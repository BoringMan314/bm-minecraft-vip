package bm.minecraft.vip;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.block.TileState;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.BundleMeta;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Builds VIP tools and boxes and keeps their timer, durability, and lore on the item. */
public final class BmMinecraftVipItems {
    private static final String KIND_TOOL = "item";
    private static final String KIND_BOX = "box";

    private final BmMinecraftVipPlugin plugin;
    private final NamespacedKey kindKey;
    private final NamespacedKey idKey;
    private final NamespacedKey activatedKey;
    private final NamespacedKey expireKey;
    private final NamespacedKey indexKey;
    private final NamespacedKey viewKey;

    public BmMinecraftVipItems(BmMinecraftVipPlugin plugin) {
        this.plugin = plugin;
        this.kindKey = new NamespacedKey(plugin, "kind");
        this.idKey = new NamespacedKey(plugin, "id");
        this.activatedKey = new NamespacedKey(plugin, "activated");
        this.expireKey = new NamespacedKey(plugin, "expire");
        this.indexKey = new NamespacedKey(plugin, "index");
        this.viewKey = new NamespacedKey(plugin, "view");
    }

    public record ItemUpdate(ItemStack stack, boolean changed, String messageKey, String subject) {
        public static ItemUpdate same(ItemStack stack) {
            return new ItemUpdate(stack, false, null, "");
        }

        public static ItemUpdate remove(String messageKey, String subject) {
            return new ItemUpdate(null, true, messageKey, subject == null ? "" : subject);
        }

        public static ItemUpdate replace(ItemStack stack) {
            return new ItemUpdate(stack, true, null, "");
        }
    }

    public record TakeResult(ItemStack given, ItemStack box, String messageKey, String subject, String itemName) {
    }

    public boolean isTool(ItemStack stack) {
        return KIND_TOOL.equals(kind(stack));
    }

    public boolean isBox(ItemStack stack) {
        return KIND_BOX.equals(kind(stack));
    }

    public boolean isBundle(ItemStack stack) {
        return isBox(stack) && stack.getType().name().endsWith("BUNDLE");
    }

    public boolean isShulker(ItemStack stack) {
        return isBox(stack) && stack.getType().name().endsWith("SHULKER_BOX");
    }

    public boolean isPlacedBox(Block block) {
        if (block == null || !block.getType().name().endsWith("SHULKER_BOX")) {
            return false;
        }
        if (!(block.getState() instanceof TileState tile)) {
            return false;
        }
        return KIND_BOX.equals(tile.getPersistentDataContainer().get(kindKey, PersistentDataType.STRING));
    }

    public long expireOf(ItemStack stack) {
        return readExpire(stack);
    }

    public long expiryAfterUse(ItemStack stack, long now) {
        BmMinecraftVipTime time = boxTime(id(stack));
        return time == null ? 0L : time.expiryAfterUse(now);
    }

    public ItemStack createJoinBundle(String id, String name, Material appearance, List<ItemStack> contents) {
        ItemStack stack = new ItemStack(appearance);
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return null;
        }
        meta.setDisplayName(plugin.language().format("lore.box-name", Map.of("name", name)));
        writeIdentity(meta, KIND_BOX, id);
        if (meta instanceof BundleMeta bundleMeta) {
            List<ItemStack> copies = new ArrayList<>();
            for (ItemStack content : contents) {
                if (content != null && !content.getType().isAir()) {
                    copies.add(content.clone());
                }
            }
            bundleMeta.setItems(copies);
            bundleMeta.getPersistentDataContainer().set(indexKey, PersistentDataType.INTEGER, copies.size());
        }
        stack.setItemMeta(meta);
        return present(stack, System.currentTimeMillis());
    }

    public void markPlaced(Block block, String boxId, long expire) {
        if (!(block.getState() instanceof TileState tile)) {
            return;
        }
        tile.getPersistentDataContainer().set(kindKey, PersistentDataType.STRING, KIND_BOX);
        tile.getPersistentDataContainer().set(idKey, PersistentDataType.STRING, boxId);
        tile.getPersistentDataContainer().set(activatedKey, PersistentDataType.BYTE, (byte) 1);
        tile.getPersistentDataContainer().set(expireKey, PersistentDataType.LONG, expire);
        tile.update(true, false);
    }

    public long placedExpire(Block block) {
        if (!(block.getState() instanceof TileState tile)) {
            return 0L;
        }
        Long expire = tile.getPersistentDataContainer().get(expireKey, PersistentDataType.LONG);
        return expire == null ? 0L : expire;
    }

    public String placedId(Block block) {
        if (!(block.getState() instanceof TileState tile)) {
            return "";
        }
        String value = tile.getPersistentDataContainer().get(idKey, PersistentDataType.STRING);
        return value == null ? "" : value;
    }

    public String placedName(Block block) {
        BmMinecraftVipCatalog.BoxDefinition box = plugin.catalog().box(placedId(block));
        return box == null ? placedId(block) : box.name();
    }

    public String id(ItemStack stack) {
        String value = read(stack, idKey, PersistentDataType.STRING);
        return value == null ? "" : value;
    }

    public boolean isActivated(ItemStack stack) {
        Byte value = read(stack, activatedKey, PersistentDataType.BYTE);
        return value != null && value == (byte) 1;
    }

    public boolean isExpired(ItemStack stack, long now) {
        long expire = readExpire(stack);
        return expire > 0L && now >= expire;
    }

    public String subjectName(ItemStack stack) {
        if (isBox(stack)) {
            String name = boxDisplayName(id(stack));
            return name.isEmpty() ? id(stack) : name;
        }
        BmMinecraftVipCatalog.ItemDefinition item = plugin.catalog().item(id(stack));
        return item == null ? id(stack) : item.name();
    }

    public ItemStack createTool(BmMinecraftVipCatalog.ItemDefinition definition) {
        ItemStack stack = new ItemStack(definition.base());
        ItemMeta meta = stack.getItemMeta();
        if (!(meta instanceof Damageable damageable)) {
            return null;
        }
        meta.setDisplayName(plugin.language().format("lore.item-name", Map.of("name", definition.name())));
        meta.setItemModel(definition.appearance().getKey());
        damageable.setMaxDamage(definition.durability());
        damageable.setDamage(0);
        for (Map.Entry<org.bukkit.enchantments.Enchantment, Integer> enchant : definition.enchants().entrySet()) {
            meta.addEnchant(enchant.getKey(), enchant.getValue(), true);
        }
        writeIdentity(meta, KIND_TOOL, definition.id());
        stack.setItemMeta(meta);
        return present(stack, System.currentTimeMillis());
    }

    public ItemStack createBox(BmMinecraftVipCatalog.BoxDefinition definition) {
        ItemStack stack = new ItemStack(definition.appearance());
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return null;
        }
        meta.setDisplayName(plugin.language().format("lore.box-name", Map.of("name", definition.name())));
        writeIdentity(meta, KIND_BOX, definition.id());
        List<ItemStack> contents = contentItems(definition);
        if (meta instanceof BundleMeta bundleMeta) {
            bundleMeta.setItems(contents);
            bundleMeta.getPersistentDataContainer().set(indexKey, PersistentDataType.INTEGER, contents.size());
        } else if (meta instanceof BlockStateMeta blockStateMeta
                && blockStateMeta.getBlockState() instanceof Container container) {
            for (int slot = 0; slot < contents.size() && slot < container.getInventory().getSize(); slot++) {
                container.getInventory().setItem(slot, contents.get(slot));
            }
            blockStateMeta.setBlockState(container);
        }
        stack.setItemMeta(meta);
        return present(stack, System.currentTimeMillis());
    }

    public ItemStack shulkerFromBlock(Block block) {
        if (!isPlacedBox(block) || !(block.getState() instanceof Container container)) {
            return null;
        }
        ItemStack stack = new ItemStack(block.getType());
        ItemMeta meta = stack.getItemMeta();
        if (!(meta instanceof BlockStateMeta blockStateMeta)) {
            return null;
        }
        blockStateMeta.setDisplayName(plugin.language().format("lore.box-name", Map.of("name", placedName(block))));
        blockStateMeta.setBlockState(container);
        writeIdentity(blockStateMeta, KIND_BOX, placedId(block));
        blockStateMeta.getPersistentDataContainer().set(activatedKey, PersistentDataType.BYTE, (byte) 1);
        blockStateMeta.getPersistentDataContainer().set(expireKey, PersistentDataType.LONG, placedExpire(block));
        stack.setItemMeta(blockStateMeta);
        return present(stack, System.currentTimeMillis());
    }

    /** Starts the countdown on the first real use without resetting a clock that already started. */
    public ItemStack prepareUse(ItemStack stack, long now) {
        if (!isTool(stack) || isActivated(stack)) {
            return stack;
        }
        BmMinecraftVipCatalog.ItemDefinition item = plugin.catalog().item(id(stack));
        if (item == null) {
            return stack;
        }
        return present(writeClock(stack, item.time().expiryAfterUse(now)), now);
    }

    public TakeResult take(ItemStack stack, long now) {
        if (!isBox(stack)) {
            return new TakeResult(null, stack, null, "", "");
        }
        String boxId = id(stack);
        BmMinecraftVipCatalog.BoxDefinition box = plugin.catalog().box(boxId);
        String boxName = box == null ? boxId : box.name();
        if (isActivated(stack) && isExpired(stack, now)) {
            return new TakeResult(null, null, "box-expired", boxName, "");
        }
        ItemStack fromBundle = takeBundleItem(stack);
        if (fromBundle != null || isBundle(stack)) {
            if (fromBundle == null) {
                return new TakeResult(null, stack, "box-missing-content", boxId, boxId);
            }
            ItemStack advanced = writeClock(withoutFirstBundleItem(stack), isActivated(stack)
                    ? readExpire(stack)
                    : expiryAfterUse(stack, now));
            String itemName = subjectName(fromBundle);
            if (bundleCount(advanced) <= 0) {
                return new TakeResult(fromBundle, null, "box-finished", boxName, itemName);
            }
            if (isExpired(advanced, now)) {
                return new TakeResult(fromBundle, null, "box-expired", boxName, itemName);
            }
            return new TakeResult(fromBundle, present(advanced, now), null, boxName, itemName);
        }
        if (box == null) {
            return new TakeResult(null, stack, "box-missing-content", boxId, boxId);
        }
        int index = readIndex(stack);
        if (index < 0 || index >= box.contents().size()) {
            return new TakeResult(null, null, "box-finished", boxName, "");
        }
        String contentId = box.contents().get(index);
        BmMinecraftVipCatalog.ItemDefinition content = plugin.catalog().item(contentId);
        if (content == null) {
            return new TakeResult(null, stack, "box-missing-content", contentId, contentId);
        }
        ItemStack given = createTool(content);
        long expire = isActivated(stack) ? readExpire(stack) : box.time().expiryAfterUse(now);
        ItemStack advanced = writeIndex(writeClock(stack, expire), index + 1);
        if (index + 1 >= box.contents().size()) {
            return new TakeResult(given, null, "box-finished", boxName, content.name());
        }
        if (isExpired(advanced, now)) {
            return new TakeResult(given, null, "box-expired", boxName, content.name());
        }
        return new TakeResult(given, present(advanced, now), null, boxName, content.name());
    }

    public ItemUpdate update(ItemStack stack, long now, boolean refreshPresentation) {
        if (!isTool(stack) && !isBox(stack)) {
            return ItemUpdate.same(stack);
        }
        String subject = subjectName(stack);
        if (isBox(stack)) {
            BmMinecraftVipCatalog.BoxDefinition box = plugin.catalog().box(id(stack));
            if (isBundle(stack)) {
                ItemUpdate bundleUpdate = updateBundle(stack, box, subject, now);
                if (bundleUpdate != null) {
                    return bundleUpdate;
                }
            } else if (isShulker(stack)) {
                ItemUpdate shulkerUpdate = updateShulkerItem(stack, box, subject, now);
                if (shulkerUpdate != null) {
                    return shulkerUpdate;
                }
            } else if (box != null && readIndex(stack) >= box.contents().size()) {
                return ItemUpdate.remove("box-finished", subject);
            } else if (isExpired(stack, now)) {
                return ItemUpdate.remove("box-expired", subject);
            }
        } else if (isExpired(stack, now)) {
            return ItemUpdate.remove("item-expired", subject);
        }
        if (!refreshPresentation) {
            return ItemUpdate.same(stack);
        }
        if (viewSignature(stack, now).equals(readView(stack))) {
            return ItemUpdate.same(stack);
        }
        return ItemUpdate.replace(present(stack, now));
    }

    /** Restores the plugin-only model, lore, name, and durability cap. */
    public ItemStack withPlugin(ItemStack stack) {
        return rewritePresence(stack, true);
    }

    /** Leaves the base item after the plugin is removed. */
    public ItemStack withoutPlugin(ItemStack stack) {
        return rewritePresence(stack, false);
    }

    private ItemStack rewritePresence(ItemStack stack, boolean restore) {
        if (stack == null || stack.getType().isAir() || !stack.hasItemMeta()) {
            return stack;
        }
        ItemStack result = stack.clone();
        ItemMeta meta = result.getItemMeta();
        if (meta == null) {
            return stack;
        }
        boolean changed = rewriteStored(meta, restore);
        if (isTool(result)) {
            changed = applyToolPresence(meta, result, restore) || changed;
        } else if (isBox(result)) {
            changed = applyBoxPresence(meta, result, restore) || changed;
        }
        if (!changed) {
            return stack;
        }
        result.setItemMeta(meta);
        if (restore && (isTool(result) || isBox(result))) {
            return present(result, System.currentTimeMillis());
        }
        return result;
    }

    private boolean rewriteStored(ItemMeta meta, boolean restore) {
        boolean changed = false;
        if (meta instanceof BundleMeta bundleMeta && bundleMeta.hasItems()) {
            List<ItemStack> contents = rewriteContents(bundleMeta.getItems(), restore);
            if (contents != null) {
                bundleMeta.setItems(contents);
                changed = true;
            }
        }
        if (meta instanceof BlockStateMeta blockStateMeta
                && blockStateMeta.hasBlockState()
                && blockStateMeta.getBlockState() instanceof Container container
                && rewriteInventory(container.getInventory(), restore)) {
            blockStateMeta.setBlockState(container);
            changed = true;
        }
        return changed;
    }

    private List<ItemStack> rewriteContents(List<ItemStack> contents, boolean restore) {
        List<ItemStack> rewritten = new ArrayList<>();
        boolean changed = false;
        for (ItemStack item : contents) {
            if (item == null || item.getType().isAir()) {
                continue;
            }
            ItemStack updated = rewritePresence(item, restore);
            if (updated != item) {
                changed = true;
            }
            if (updated != null && !updated.getType().isAir()) {
                rewritten.add(updated);
            }
        }
        return changed ? rewritten : null;
    }

    private boolean rewriteInventory(Inventory inventory, boolean restore) {
        boolean changed = false;
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack item = inventory.getItem(slot);
            if (item == null || item.getType().isAir()) {
                continue;
            }
            ItemStack updated = rewritePresence(item, restore);
            if (updated == item) {
                continue;
            }
            inventory.setItem(slot, updated);
            changed = true;
        }
        return changed;
    }

    private boolean applyToolPresence(ItemMeta meta, ItemStack stack, boolean restore) {
        if (!(meta instanceof Damageable damageable)) {
            return false;
        }
        if (!restore) {
            meta.setDisplayName(null);
            meta.setLore(null);
            meta.setItemModel(null);
            int spent = damageable.hasDamage() ? Math.max(0, damageable.getDamage()) : 0;
            int vanillaMax = stack.getType().getMaxDurability();
            damageable.setMaxDamage(null);
            if (vanillaMax > 1) {
                damageable.setDamage(Math.min(spent, vanillaMax - 1));
            }
            return true;
        }
        BmMinecraftVipCatalog.ItemDefinition item = plugin.catalog().item(id(stack));
        if (item == null) {
            return false;
        }
        int spent = damageable.hasDamage() ? Math.max(0, damageable.getDamage()) : 0;
        damageable.setMaxDamage(item.durability());
        damageable.setDamage(Math.min(spent, Math.max(0, item.durability() - 1)));
        meta.setItemModel(item.appearance().getKey());
        meta.setDisplayName(plugin.language().format("lore.item-name", Map.of("name", item.name())));
        return true;
    }

    private boolean applyBoxPresence(ItemMeta meta, ItemStack stack, boolean restore) {
        if (!restore) {
            meta.setDisplayName(null);
            meta.setLore(null);
            meta.setItemModel(null);
            return true;
        }
        String name = boxDisplayName(id(stack));
        if (name.isEmpty()) {
            return false;
        }
        meta.setDisplayName(plugin.language().format("lore.box-name", Map.of("name", name)));
        return true;
    }

    private ItemStack present(ItemStack stack, long now) {
        ItemStack result = stack.clone();
        ItemMeta meta = result.getItemMeta();
        if (meta == null) {
            return stack;
        }
        List<String> lore = isBox(result) ? boxLore(result, now) : toolLore(result, now);
        if (lore != null) {
            applyLore(meta, lore, unnamedNextContent(result));
        }
        if (isTool(result)) {
            BmMinecraftVipCatalog.ItemDefinition item = plugin.catalog().item(id(result));
            if (item != null) {
                meta.setItemModel(item.appearance().getKey());
            }
        }
        meta.getPersistentDataContainer().set(
                viewKey, PersistentDataType.STRING, viewSignature(result, now));
        result.setItemMeta(meta);
        return result;
    }

    private List<String> toolLore(ItemStack stack, long now) {
        BmMinecraftVipCatalog.ItemDefinition item = plugin.catalog().item(id(stack));
        if (item == null) {
            return null;
        }
        List<String> lore = new ArrayList<>();
        lore.add(timeLine(stack, item.time(), now));
        lore.add(durabilityLine(stack, item.durability()));
        String function = functionLine(item);
        if (!function.isEmpty()) {
            lore.add(function);
        }
        return lore;
    }

    private List<String> boxLore(ItemStack stack, long now) {
        BmMinecraftVipTime time = boxTime(id(stack));
        if (time == null) {
            return null;
        }
        BmMinecraftVipCatalog.BoxDefinition box = plugin.catalog().box(id(stack));
        List<String> lore = new ArrayList<>();
        lore.add(timeLine(stack, time, now, isShulker(stack)
                ? "lore.time-pending-place"
                : "lore.time-pending"));
        lore.add(plugin.language().format(isShulker(stack) ? "lore.box-use-shulker" : "lore.box-use-bundle", Map.of()));
        int total = box != null ? box.contents().size() : plugin.joins().contentCount(id(stack));
        int stored = isBundle(stack) ? bundleCount(stack) : isShulker(stack) ? shulkerCount(stack) : -1;
        int index = stored >= 0
                ? Math.max(0, total - stored)
                : Math.min(Math.max(0, readIndex(stack)), total);
        int remaining = stored >= 0 ? stored : total - index;
        lore.add(plugin.language().format("lore.box-contents", Map.of(
                "remaining", Integer.toString(remaining),
                "total", Integer.toString(total))));
        String nextName = nextContentName(stack, box, index);
        if (!nextName.isEmpty()) {
            lore.add(plugin.language().format("lore.box-next", Map.of("name", nextName)));
        }
        return lore;
    }

    private void applyLore(ItemMeta meta, List<String> lore, ItemStack unnamedNext) {
        if (unnamedNext == null) {
            meta.setLore(lore);
            return;
        }
        LegacyComponentSerializer legacy = LegacyComponentSerializer.legacySection();
        List<Component> lines = new ArrayList<>();
        for (String line : lore) {
            lines.add(legacy.deserialize(line));
        }
        String prefix = plugin.language().format("lore.box-next", Map.of("name", ""));
        lines.add(legacy.deserialize(prefix).append(unnamedNext.displayName().color(NamedTextColor.YELLOW)));
        meta.lore(lines);
    }

    private ItemStack unnamedNextContent(ItemStack stack) {
        if (!isBox(stack)) {
            return null;
        }
        ItemStack stored = isBundle(stack) ? takeBundleItem(stack) : isShulker(stack) ? firstShulkerItem(stack) : null;
        if (stored == null || isTool(stored) || isBox(stored)) {
            return null;
        }
        ItemMeta meta = stored.getItemMeta();
        if (meta != null && meta.hasDisplayName()) {
            return null;
        }
        return stored;
    }

    private boolean joinBundle(ItemStack stack) {
        return plugin.catalog().box(id(stack)) == null && plugin.joins().time(id(stack)) != null;
    }

    private BmMinecraftVipTime boxTime(String id) {
        BmMinecraftVipCatalog.BoxDefinition box = plugin.catalog().box(id);
        if (box != null) {
            return box.time();
        }
        return plugin.joins().time(id);
    }

    private String boxDisplayName(String id) {
        BmMinecraftVipCatalog.BoxDefinition box = plugin.catalog().box(id);
        if (box != null) {
            return box.name();
        }
        return plugin.joins().name(id);
    }

    private ItemUpdate updateBundle(
            ItemStack stack,
            BmMinecraftVipCatalog.BoxDefinition box,
            String subject,
            long now) {
        ItemStack current = joinBundle(stack) ? stack : keepBundleTools(stack);
        int count = bundleCount(current);
        if (!isActivated(current) && count == 0 && box != null) {
            ItemStack filled = fillBundle(current, box);
            int filledCount = bundleCount(filled);
            if (filledCount > 0) {
                current = writeIndex(filled, filledCount);
                count = filledCount;
            }
        }
        int seen = readIndex(current);
        if (!isActivated(current) && count > seen) {
            current = writeIndex(current, count);
            seen = count;
        }
        if (!isActivated(current) && seen > 0 && count < seen) {
            BmMinecraftVipTime time = box == null ? plugin.joins().time(id(current)) : box.time();
            long expire = time == null ? 0L : time.expiryAfterUse(now);
            current = writeIndex(writeClock(current, expire), count);
            if (count <= 0) {
                return ItemUpdate.remove("box-finished", subject);
            }
            if (isExpired(current, now)) {
                return ItemUpdate.remove("box-expired", subject);
            }
            return ItemUpdate.replace(present(current, now));
        }
        if (isActivated(current) && count <= 0) {
            return ItemUpdate.remove("box-finished", subject);
        }
        if (isExpired(current, now)) {
            return ItemUpdate.remove("box-expired", subject);
        }
        if (current != stack) {
            return ItemUpdate.replace(present(current, now));
        }
        return null;
    }

    private ItemUpdate updateShulkerItem(
            ItemStack stack,
            BmMinecraftVipCatalog.BoxDefinition box,
            String subject,
            long now) {
        ItemStack current = stack;
        int count = shulkerCount(current);
        if (!isActivated(current) && count <= 0 && box != null) {
            current = fillShulker(current, box);
            count = shulkerCount(current);
        }
        if (isActivated(current) && count <= 0) {
            return ItemUpdate.remove("box-finished", subject);
        }
        if (isExpired(current, now)) {
            return ItemUpdate.remove("box-expired", subject);
        }
        if (current != stack) {
            return ItemUpdate.replace(present(current, now));
        }
        return null;
    }

    private List<ItemStack> contentItems(BmMinecraftVipCatalog.BoxDefinition definition) {
        List<ItemStack> contents = new ArrayList<>();
        for (String contentId : definition.contents()) {
            BmMinecraftVipCatalog.ItemDefinition item = plugin.catalog().item(contentId);
            if (item == null) {
                continue;
            }
            ItemStack tool = createTool(item);
            if (tool != null) {
                contents.add(tool);
            }
        }
        return contents;
    }

    private ItemStack fillBundle(ItemStack stack, BmMinecraftVipCatalog.BoxDefinition box) {
        ItemStack result = stack.clone();
        if (!(result.getItemMeta() instanceof BundleMeta bundleMeta)) {
            return stack;
        }
        bundleMeta.setItems(contentItems(box));
        result.setItemMeta(bundleMeta);
        return result;
    }

    private ItemStack fillShulker(ItemStack stack, BmMinecraftVipCatalog.BoxDefinition box) {
        ItemStack result = stack.clone();
        if (!(result.getItemMeta() instanceof BlockStateMeta blockStateMeta)
                || !(blockStateMeta.getBlockState() instanceof Container container)) {
            return stack;
        }
        List<ItemStack> contents = contentItems(box);
        for (int slot = 0; slot < contents.size() && slot < container.getInventory().getSize(); slot++) {
            container.getInventory().setItem(slot, contents.get(slot));
        }
        blockStateMeta.setBlockState(container);
        result.setItemMeta(blockStateMeta);
        return result;
    }

    private ItemStack keepBundleTools(ItemStack stack) {
        if (!(stack.getItemMeta() instanceof BundleMeta bundleMeta) || !bundleMeta.hasItems()) {
            return stack;
        }
        List<ItemStack> kept = new ArrayList<>();
        boolean changed = false;
        for (ItemStack item : bundleMeta.getItems()) {
            if (item != null && !item.getType().isAir() && isTool(item)) {
                kept.add(item);
            } else if (item != null && !item.getType().isAir()) {
                changed = true;
            }
        }
        if (!changed) {
            return stack;
        }
        ItemStack result = stack.clone();
        if (!(result.getItemMeta() instanceof BundleMeta copy)) {
            return stack;
        }
        copy.setItems(kept);
        result.setItemMeta(copy);
        return result;
    }

    private String nextContentName(ItemStack stack, BmMinecraftVipCatalog.BoxDefinition box, int index) {
        ItemStack stored = isBundle(stack) ? takeBundleItem(stack) : isShulker(stack) ? firstShulkerItem(stack) : null;
        if (stored != null) {
            if (isTool(stored) || isBox(stored)) {
                return subjectName(stored);
            }
            ItemMeta meta = stored.getItemMeta();
            if (meta != null && meta.hasDisplayName()) {
                return ChatColor.stripColor(meta.getDisplayName());
            }
            return "";
        }
        if (box == null || index < 0 || index >= box.contents().size()) {
            return "";
        }
        String contentId = box.contents().get(index);
        BmMinecraftVipCatalog.ItemDefinition next = plugin.catalog().item(contentId);
        return next == null ? contentId : next.name();
    }

    private ItemStack takeBundleItem(ItemStack stack) {
        if (!(stack.getItemMeta() instanceof BundleMeta bundleMeta) || !bundleMeta.hasItems()) {
            return null;
        }
        for (ItemStack item : bundleMeta.getItems()) {
            if (item != null && !item.getType().isAir()) {
                return item.clone();
            }
        }
        return null;
    }

    private ItemStack withoutFirstBundleItem(ItemStack stack) {
        ItemStack result = stack.clone();
        if (!(result.getItemMeta() instanceof BundleMeta bundleMeta) || !bundleMeta.hasItems()) {
            return result;
        }
        List<ItemStack> items = new ArrayList<>();
        boolean removed = false;
        for (ItemStack item : bundleMeta.getItems()) {
            if (!removed && item != null && !item.getType().isAir()) {
                removed = true;
                continue;
            }
            if (item != null && !item.getType().isAir()) {
                items.add(item);
            }
        }
        bundleMeta.setItems(items);
        result.setItemMeta(bundleMeta);
        return result;
    }

    private ItemStack firstShulkerItem(ItemStack stack) {
        if (!(stack.getItemMeta() instanceof BlockStateMeta blockStateMeta) || !blockStateMeta.hasBlockState()) {
            return null;
        }
        if (!(blockStateMeta.getBlockState() instanceof Container container)) {
            return null;
        }
        for (ItemStack item : container.getInventory().getContents()) {
            if (item != null && !item.getType().isAir()) {
                return item;
            }
        }
        return null;
    }

    private int shulkerCount(ItemStack stack) {
        if (!(stack.getItemMeta() instanceof BlockStateMeta blockStateMeta) || !blockStateMeta.hasBlockState()) {
            return 0;
        }
        if (!(blockStateMeta.getBlockState() instanceof Container container)) {
            return 0;
        }
        int count = 0;
        for (ItemStack item : container.getInventory().getContents()) {
            if (item != null && !item.getType().isAir()) {
                count++;
            }
        }
        return count;
    }

    private int bundleCount(ItemStack stack) {
        if (!(stack.getItemMeta() instanceof BundleMeta bundleMeta) || !bundleMeta.hasItems()) {
            return 0;
        }
        int count = 0;
        for (ItemStack item : bundleMeta.getItems()) {
            if (item != null && !item.getType().isAir()) {
                count++;
            }
        }
        return count;
    }

    private String timeLine(ItemStack stack, BmMinecraftVipTime time, long now) {
        return timeLine(stack, time, now, "lore.time-pending");
    }

    private String timeLine(ItemStack stack, BmMinecraftVipTime time, long now, String pendingKey) {
        if (time.isPermanent()) {
            return plugin.language().format("lore.time-permanent", Map.of());
        }
        if (!isActivated(stack)) {
            String pending = time.kind() == BmMinecraftVipTime.Kind.DATE
                    ? time.dateText()
                    : plugin.language().format("lore.duration-value", Map.of("days", Long.toString(time.days())));
            return plugin.language().format(pendingKey, Map.of("time", pending));
        }
        long remaining = readExpire(stack) - now;
        if (remaining < 60_000L) {
            return plugin.language().format("lore.time-remaining-soon", Map.of());
        }
        long minutesTotal = remaining / 60_000L;
        return plugin.language().format("lore.time-remaining", Map.of(
                "days", Long.toString(minutesTotal / (60L * 24L)),
                "hours", Long.toString((minutesTotal / 60L) % 24L),
                "minutes", Long.toString(minutesTotal % 60L)));
    }

    private String durabilityLine(ItemStack stack, int fallbackMax) {
        int max = fallbackMax;
        int damage = 0;
        if (stack.getItemMeta() instanceof Damageable damageable) {
            if (damageable.hasMaxDamage()) {
                max = damageable.getMaxDamage();
            }
            damage = Math.max(0, damageable.getDamage());
        }
        int current = Math.max(0, max - damage);
        return plugin.language().format("lore.durability", Map.of(
                "current", Integer.toString(current),
                "max", Integer.toString(max)));
    }

    private String functionLine(BmMinecraftVipCatalog.ItemDefinition item) {
        if (item.function() == BmMinecraftVipCatalog.FunctionType.NONE) {
            return "";
        }
        if (item.function() == BmMinecraftVipCatalog.FunctionType.FACE) {
            return plugin.language().format("lore.function-face", Map.of(
                    "size", Integer.toString(item.size())));
        }
        return plugin.language().format("lore.function-forward", Map.of(
                "width", Integer.toString(item.width()),
                "depth", Integer.toString(item.depth())));
    }

    private String viewSignature(ItemStack stack, long now) {
        long minutesLeft = 0L;
        long expire = readExpire(stack);
        if (isActivated(stack) && expire > now) {
            minutesLeft = (expire - now) / 60_000L;
        }
        int damage = 0;
        if (stack.getItemMeta() instanceof Damageable damageable) {
            damage = Math.max(0, damageable.getDamage());
        }
        String look = "";
        if (isTool(stack)) {
            BmMinecraftVipCatalog.ItemDefinition item = plugin.catalog().item(id(stack));
            if (item != null) {
                look = item.appearance().getKey().toString();
            }
        }
        int stored = isBundle(stack) ? bundleCount(stack) : isShulker(stack) ? shulkerCount(stack) : 0;
        return plugin.language().reloadCount()
                + ":" + (isActivated(stack) ? 1 : 0)
                + ":" + minutesLeft
                + ":" + damage
                + ":" + readIndex(stack)
                + ":" + stored
                + ":" + look;
    }

    private void writeIdentity(ItemMeta meta, String kind, String id) {
        PersistentDataContainer data = meta.getPersistentDataContainer();
        data.set(kindKey, PersistentDataType.STRING, kind);
        data.set(idKey, PersistentDataType.STRING, id);
        data.set(activatedKey, PersistentDataType.BYTE, (byte) 0);
        data.set(expireKey, PersistentDataType.LONG, 0L);
        data.set(indexKey, PersistentDataType.INTEGER, 0);
    }

    private ItemStack writeClock(ItemStack stack, long expire) {
        ItemStack result = stack.clone();
        ItemMeta meta = result.getItemMeta();
        if (meta == null) {
            return stack;
        }
        PersistentDataContainer data = meta.getPersistentDataContainer();
        data.set(activatedKey, PersistentDataType.BYTE, (byte) 1);
        data.set(expireKey, PersistentDataType.LONG, expire);
        result.setItemMeta(meta);
        return result;
    }

    private ItemStack writeIndex(ItemStack stack, int index) {
        ItemStack result = stack.clone();
        ItemMeta meta = result.getItemMeta();
        if (meta == null) {
            return stack;
        }
        meta.getPersistentDataContainer().set(indexKey, PersistentDataType.INTEGER, index);
        result.setItemMeta(meta);
        return result;
    }

    private String kind(ItemStack stack) {
        return read(stack, kindKey, PersistentDataType.STRING);
    }

    private long readExpire(ItemStack stack) {
        Long expire = read(stack, expireKey, PersistentDataType.LONG);
        return expire == null ? 0L : expire;
    }

    private int readIndex(ItemStack stack) {
        Integer index = read(stack, indexKey, PersistentDataType.INTEGER);
        return index == null ? 0 : index;
    }

    private String readView(ItemStack stack) {
        String view = read(stack, viewKey, PersistentDataType.STRING);
        return view == null ? "" : view;
    }

    private <T, Z> Z read(ItemStack stack, NamespacedKey key, PersistentDataType<T, Z> type) {
        if (stack == null || stack.getType().isAir() || !stack.hasItemMeta()) {
            return null;
        }
        return stack.getItemMeta().getPersistentDataContainer().get(key, type);
    }
}
