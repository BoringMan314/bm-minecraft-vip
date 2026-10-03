package bm.minecraft.vip;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Gives configured starter bundles to players the first time they join. */
public final class BmMinecraftVipJoin {
    private final BmMinecraftVipPlugin plugin;
    private final NamespacedKey givenKey;
    private final Map<String, Pack> packs = new LinkedHashMap<>();
    private boolean enabled;

    public BmMinecraftVipJoin(BmMinecraftVipPlugin plugin) {
        this.plugin = plugin;
        this.givenKey = new NamespacedKey(plugin, "join-gift");
    }

    public void load() {
        packs.clear();
        enabled = true;
        File file = new File(plugin.getDataFolder(), "config-join.yml");
        YamlConfiguration configuration = YamlConfiguration.loadConfiguration(file);
        enabled = configuration.getBoolean("enabled", true);
        ConfigurationSection section = configuration.getConfigurationSection("packs");
        if (section == null) {
            plugin.getLogger().warning(plugin.language().format("console.missing-joins", Map.of()));
            return;
        }
        for (String id : section.getKeys(false)) {
            ConfigurationSection entry = section.getConfigurationSection(id);
            if (entry == null) {
                warn("invalid-join-contents", id, "");
                continue;
            }
            loadPack(id, entry);
        }
    }

    public int packCount() {
        return packs.size();
    }

    public List<String> packIds() {
        return List.copyOf(packs.keySet());
    }

    public ItemStack create(String id) {
        Pack pack = pack(id);
        return pack == null ? null : createPack(pack);
    }

    public String name(String id) {
        Pack pack = pack(id);
        return pack == null ? "" : pack.name();
    }

    public BmMinecraftVipTime time(String id) {
        Pack pack = pack(id);
        return pack == null ? null : pack.time();
    }

    public int contentCount(String id) {
        Pack pack = pack(id);
        return pack == null ? 0 : pack.contents().size();
    }

    private Pack pack(String id) {
        if (id == null) {
            return null;
        }
        String key = id.trim();
        Pack found = packs.get(key);
        if (found != null) {
            return found;
        }
        for (Map.Entry<String, Pack> entry : packs.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(key)) {
                return entry.getValue();
            }
        }
        return null;
    }

    public void giveIfNew(Player player) {
        if (!enabled || player == null || player.hasPlayedBefore() || hasReceived(player)) {
            return;
        }
        plugin.getServer().getScheduler().runTask(plugin, () -> deliver(player));
    }

    private void deliver(Player player) {
        if (!player.isOnline() || hasReceived(player)) {
            return;
        }
        List<ItemStack> gifts = new ArrayList<>();
        List<String> names = new ArrayList<>();
        for (Pack pack : packs.values()) {
            ItemStack gift = createPack(pack);
            if (gift == null) {
                continue;
            }
            gifts.add(gift);
            names.add(pack.name());
        }
        if (gifts.isEmpty()) {
            return;
        }
        markReceived(player);
        for (int index = 0; index < gifts.size(); index++) {
            giveOrDrop(player, gifts.get(index), names.get(index));
            plugin.language().send(player, "join-given", Map.of("name", names.get(index)));
        }
    }

    private ItemStack createPack(Pack pack) {
        return plugin.items().createJoinBundle(pack.id(), pack.name(), pack.appearance(), pack.contents());
    }

    private void loadPack(String id, ConfigurationSection entry) {
        String name = entry.getString("name", "").trim();
        if (name.isEmpty()) {
            warn("invalid-join-name", id, "");
            return;
        }
        Material appearance = material(entry.getString("appearance", "BUNDLE"));
        if (!isUsableMaterial(appearance)) {
            warn("invalid-join-appearance", id, entry.getString("appearance", ""));
            return;
        }
        List<ItemStack> contents = new ArrayList<>();
        List<Map<?, ?>> rows = entry.getMapList("contents");
        if (rows.isEmpty()) {
            warn("invalid-join-contents", id, "");
            return;
        }
        for (Map<?, ?> row : rows) {
            contents.addAll(contentStacks(id, row));
        }
        if (contents.isEmpty()) {
            warn("invalid-join-contents", id, "");
            return;
        }
        packs.put(id, new Pack(id, name, appearance, time(entry), List.copyOf(contents)));
    }

    private BmMinecraftVipTime time(ConfigurationSection entry) {
        String raw = entry.getString("time", "30d");
        BmMinecraftVipTime parsed = BmMinecraftVipTime.parse(raw);
        if (parsed != null) {
            return parsed;
        }
        warn("invalid-join-time", entry.getName(), raw);
        return BmMinecraftVipTime.parse("30d");
    }

    private List<ItemStack> contentStacks(String packId, Map<?, ?> row) {
        Object itemId = row.get("item");
        if (itemId != null && !String.valueOf(itemId).isBlank()) {
            return vipTool(packId, String.valueOf(itemId));
        }
        Object materialValue = row.get("material");
        Material material = material(materialValue == null ? "" : String.valueOf(materialValue));
        if (!isUsableMaterial(material)) {
            warn("invalid-join-material", packId, materialValue == null ? "" : String.valueOf(materialValue));
            return List.of();
        }
        int amount = positiveInt(row.get("amount"), 1);
        if (amount < 1) {
            warn("invalid-join-amount", packId, String.valueOf(row.get("amount")));
            return List.of();
        }
        ItemStack sample = new ItemStack(material);
        applyEnchants(packId, sample, row.get("enchants"));
        int maxStack = Math.max(1, material.getMaxStackSize());
        List<ItemStack> stacks = new ArrayList<>();
        int remaining = amount;
        while (remaining > 0) {
            ItemStack stack = sample.clone();
            stack.setAmount(Math.min(maxStack, remaining));
            stacks.add(stack);
            remaining -= stack.getAmount();
        }
        return stacks;
    }

    private List<ItemStack> vipTool(String packId, String rawId) {
        String id = rawId.trim();
        if (id.endsWith(".0")) {
            id = id.substring(0, id.length() - 2);
        }
        BmMinecraftVipCatalog.ItemDefinition definition = plugin.catalog().item(id);
        if (definition == null) {
            warn("invalid-join-item", packId, rawId.trim());
            return List.of();
        }
        ItemStack tool = plugin.items().createTool(definition);
        if (tool == null) {
            warn("invalid-join-item", packId, definition.id());
            return List.of();
        }
        return List.of(tool);
    }

    private void applyEnchants(String packId, ItemStack stack, Object rawEnchants) {
        if (!(rawEnchants instanceof Map<?, ?> enchants)) {
            return;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return;
        }
        for (Map.Entry<?, ?> enchant : enchants.entrySet()) {
            String enchantName = String.valueOf(enchant.getKey()).trim();
            Enchantment type = enchantment(enchantName);
            int level = positiveInt(enchant.getValue(), -1);
            if (type == null || level < 1) {
                warn("invalid-join-enchant", packId, enchantName);
                continue;
            }
            meta.addEnchant(type, level, true);
        }
        stack.setItemMeta(meta);
    }

    private Enchantment enchantment(String name) {
        String token = name.trim();
        if (token.isEmpty()) {
            return null;
        }
        Enchantment byName = Enchantment.getByName(token.toUpperCase(Locale.ROOT).replace(' ', '_').replace('-', '_'));
        if (byName != null) {
            return byName;
        }
        return Enchantment.getByKey(NamespacedKey.minecraft(token.toLowerCase(Locale.ROOT).replace(' ', '_')));
    }

    private int positiveInt(Object raw, int fallback) {
        if (raw == null) {
            return fallback;
        }
        if (raw instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(raw).trim());
        } catch (NumberFormatException exception) {
            return -1;
        }
    }

    private boolean hasReceived(Player player) {
        return player.getPersistentDataContainer().has(givenKey, PersistentDataType.BYTE);
    }

    private void markReceived(Player player) {
        player.getPersistentDataContainer().set(givenKey, PersistentDataType.BYTE, (byte) 1);
    }

    private void giveOrDrop(Player player, ItemStack stack, String name) {
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(stack);
        if (leftover.isEmpty()) {
            return;
        }
        for (ItemStack remain : leftover.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), remain);
        }
        plugin.language().send(player, "dropped", Map.of("name", name));
    }

    private boolean isUsableMaterial(Material material) {
        return material != null && !material.isAir() && material.isItem() && !material.isLegacy();
    }

    private Material material(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return null;
        }
        return Material.matchMaterial(raw.trim());
    }

    private void warn(String key, String id, String value) {
        plugin.getLogger().warning(plugin.language().format("console." + key, Map.of(
                "id", id,
                "value", value == null ? "" : value)));
    }

    private record Pack(
            String id,
            String name,
            Material appearance,
            BmMinecraftVipTime time,
            List<ItemStack> contents) {
    }
}
