package bm.minecraft.vip;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Loads editable VIP item and box definitions. */
public final class BmMinecraftVipCatalog {
    public enum FunctionType {
        FACE,
        FORWARD,
        NONE
    }

    public record ItemDefinition(
            String id,
            String name,
            Material base,
            Material appearance,
            int durability,
            BmMinecraftVipTime time,
            FunctionType function,
            int size,
            int width,
            int depth,
            Map<Enchantment, Integer> enchants,
            double attackDamage,
            boolean attackNoCooldown,
            int maxAreaBlocks) {
    }

    public record BoxDefinition(
            String id,
            String name,
            Material appearance,
            BmMinecraftVipTime time,
            List<String> contents) {
    }

    private final BmMinecraftVipPlugin plugin;
    private final Map<String, ItemDefinition> items = new LinkedHashMap<>();
    private final Map<String, BoxDefinition> boxes = new LinkedHashMap<>();

    public BmMinecraftVipCatalog(BmMinecraftVipPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        items.clear();
        boxes.clear();
        loadItems(YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "config-item.yml")));
        loadItems(YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "config-op-item.yml")));
        loadBoxes(YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "config-box.yml")));
    }

    public ItemDefinition item(String id) {
        return find(items, id);
    }

    public BoxDefinition box(String id) {
        return find(boxes, id);
    }

    public List<String> itemIds() {
        return List.copyOf(items.keySet());
    }

    public List<String> boxIds() {
        return List.copyOf(boxes.keySet());
    }

    public int itemCount() {
        return items.size();
    }

    public int boxCount() {
        return boxes.size();
    }

    private void loadItems(YamlConfiguration configuration) {
        ConfigurationSection section = configuration.getConfigurationSection("items");
        if (section == null) {
            plugin.getLogger().warning(plugin.language().format("console.missing-items", Map.of()));
            return;
        }
        for (String id : section.getKeys(false)) {
            ConfigurationSection entry = section.getConfigurationSection(id);
            if (entry == null) {
                warn("invalid-item-function", id, text(section, id));
                continue;
            }
            loadItem(id, entry);
        }
    }

    private void loadItem(String id, ConfigurationSection entry) {
        String name = entry.getString("name", "").trim();
        if (name.isEmpty()) {
            warn("invalid-item-name", id, "");
            return;
        }
        Material base = material(entry.getString("base"));
        if (!isUsableMaterial(base) || !isDamageable(base)) {
            warn("invalid-item-base", id, text(entry, "base"));
            return;
        }
        Material appearance = material(entry.getString("appearance"));
        if (!isUsableMaterial(appearance)) {
            warn("invalid-item-appearance", id, text(entry, "appearance"));
            return;
        }
        int durability = entry.getInt("durability", -1);
        if (durability < 1 || durability > 1_000_000_000) {
            warn("invalid-item-durability", id, text(entry, "durability"));
            return;
        }
        BmMinecraftVipTime time = BmMinecraftVipTime.parse(entry.getString("time"));
        if (time == null) {
            warn("invalid-item-time", id, text(entry, "time"));
            return;
        }
        ConfigurationSection function = entry.getConfigurationSection("function");
        if (function == null) {
            warn("invalid-item-function", id, text(entry, "function"));
            return;
        }
        double attackDamage = entry.getDouble("attack-damage", 0);
        boolean attackNoCooldown = entry.getBoolean("attack-no-cooldown", false);
        int maxAreaBlocks = entry.getInt("max-area-blocks", plugin.maxAreaBlocks());
        if (!Double.isFinite(attackDamage) || attackDamage < 0 || attackDamage > 2048
                || maxAreaBlocks < 1 || maxAreaBlocks > 4096) {
            warn("invalid-item-function", id, "attack-damage / max-area-blocks");
            return;
        }
        String type = function.getString("type", "").trim().toLowerCase(Locale.ROOT);
        Map<Enchantment, Integer> enchants = enchants(id, entry.getConfigurationSection("enchants"));
        if (type.equals("none")) {
            items.put(id, new ItemDefinition(
                    id, name, base, appearance, durability, time, FunctionType.NONE, 0, 0, 0, enchants, attackDamage, attackNoCooldown, maxAreaBlocks));
            return;
        }
        if (type.equals("face")) {
            Integer size = oddSize(function);
            int depth = function.getInt("depth", 1);
            if (size == null || depth < 1 || depth > 100) {
                warn("invalid-item-size", id, sizeText(function));
                return;
            }
            items.put(id, new ItemDefinition(
                    id, name, base, appearance, durability, time, FunctionType.FACE, size, 0, depth, enchants, attackDamage, attackNoCooldown, maxAreaBlocks));
            return;
        }
        if (type.equals("forward")) {
            Integer width = oddSize(function, "width");
            Integer depth = oddSize(function, "depth");
            if (width == null || depth == null) {
                warn("invalid-item-size", id, text(function, "width") + "x" + text(function, "depth"));
                return;
            }
            items.put(id, new ItemDefinition(
                    id, name, base, appearance, durability, time, FunctionType.FORWARD, 0, width, depth, enchants, attackDamage, attackNoCooldown, maxAreaBlocks));
            return;
        }
        warn("invalid-item-function", id, text(function, "type"));
    }

    private void loadBoxes(YamlConfiguration configuration) {
        ConfigurationSection section = configuration.getConfigurationSection("boxes");
        if (section == null) {
            plugin.getLogger().warning(plugin.language().format("console.missing-boxes", Map.of()));
            return;
        }
        for (String id : section.getKeys(false)) {
            ConfigurationSection entry = section.getConfigurationSection(id);
            if (entry == null) {
                warn("invalid-box-contents", id, text(section, id));
                continue;
            }
            loadBox(id, entry);
        }
    }

    private void loadBox(String id, ConfigurationSection entry) {
        String name = entry.getString("name", "").trim();
        if (name.isEmpty()) {
            warn("invalid-box-name", id, "");
            return;
        }
        Material appearance = material(entry.getString("appearance"));
        if (!isUsableMaterial(appearance)) {
            warn("invalid-box-appearance", id, text(entry, "appearance"));
            return;
        }
        BmMinecraftVipTime time = BmMinecraftVipTime.parse(entry.getString("time"));
        if (time == null) {
            warn("invalid-box-time", id, text(entry, "time"));
            return;
        }
        List<String> contents = new ArrayList<>();
        for (String content : entry.getStringList("contents")) {
            if (content == null || content.trim().isEmpty()) {
                continue;
            }
            String contentId = content.trim();
            contents.add(contentId);
            if (item(contentId) == null) {
                warn("invalid-box-content", id, contentId);
            }
        }
        if (contents.isEmpty()) {
            warn("invalid-box-contents", id, "");
            return;
        }
        boxes.put(id, new BoxDefinition(id, name, appearance, time, List.copyOf(contents)));
    }

    private Map<Enchantment, Integer> enchants(String id, ConfigurationSection section) {
        Map<Enchantment, Integer> enchants = new LinkedHashMap<>();
        if (section == null) {
            return enchants;
        }
        for (String key : section.getKeys(false)) {
            Enchantment enchantment = enchantment(key);
            int level = section.getInt(key, -1);
            if (enchantment == null || level < 1) {
                warn("invalid-item-enchant", id, key);
                continue;
            }
            enchants.put(enchantment, level);
        }
        return enchants;
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

    private Integer oddSize(ConfigurationSection function) {
        if (function.contains("size")) {
            return oddSize(function, "size");
        }
        if (!function.contains("range")) {
            return null;
        }
        int range = function.getInt("range", -1);
        if (range < 0 || range > 7) {
            return null;
        }
        return range * 2 + 1;
    }

    private Integer oddSize(ConfigurationSection section, String key) {
        if (!section.contains(key)) {
            return null;
        }
        int value = section.getInt(key, -1);
        if (value < 1 || value > 15 || value % 2 == 0) {
            return null;
        }
        return value;
    }

    private String sizeText(ConfigurationSection function) {
        if (function.contains("size")) {
            return text(function, "size");
        }
        return text(function, "range");
    }

    private boolean isDamageable(Material material) {
        ItemStack sample = new ItemStack(material);
        return sample.getItemMeta() instanceof Damageable;
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

    private String text(ConfigurationSection section, String key) {
        Object value = section.get(key);
        return value == null ? "" : String.valueOf(value);
    }

    private <T> T find(Map<String, T> source, String id) {
        if (id == null) {
            return null;
        }
        String key = id.trim();
        T found = source.get(key);
        if (found != null) {
            return found;
        }
        if (!key.matches("\\d+")) {
            return null;
        }
        try {
            int value = Integer.parseInt(key);
            for (Map.Entry<String, T> entry : source.entrySet()) {
                if (entry.getKey().matches("\\d+") && Integer.parseInt(entry.getKey()) == value) {
                    return entry.getValue();
                }
            }
        } catch (NumberFormatException exception) {
            return null;
        }
        return null;
    }
}
