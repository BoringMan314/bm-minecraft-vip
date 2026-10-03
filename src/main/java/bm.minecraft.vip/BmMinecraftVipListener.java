package bm.minecraft.vip;

import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import org.bukkit.Nameable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import com.destroystokyo.paper.event.player.PlayerArmorChangeEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Applies area mining, box withdrawal, and expiry to VIP items. */
public final class BmMinecraftVipListener implements Listener {
    private final BmMinecraftVipPlugin plugin;
    private final Set<String> warnedArea = new HashSet<>();
    private boolean areaBreakActive;
    private int sweepPhase;

    public BmMinecraftVipListener(BmMinecraftVipPlugin plugin) {
        this.plugin = plugin;
    }

    public void restoreLoadedItems() {
        rewriteLoaded(true);
    }

    public void revertLoadedItems() {
        rewriteLoaded(false);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        plugin.joins().giveIfNew(event.getPlayer());
    }

    public void tick() {
        long now = System.currentTimeMillis();
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            sweepPlayer(player, now);
        }
        sweepPhase++;
        if (sweepPhase % 5 != 0) {
            return;
        }
        for (World world : plugin.getServer().getWorlds()) {
            List<Item> expiredItems = new ArrayList<>();
            for (Item entity : world.getEntitiesByClass(Item.class)) {
                BmMinecraftVipItems.ItemUpdate update = plugin.items().update(entity.getItemStack(), now, false);
                if (update.changed() && update.stack() == null) {
                    expiredItems.add(entity);
                }
            }
            for (Item entity : expiredItems) {
                entity.remove();
            }
            for (Chunk chunk : world.getLoadedChunks()) {
                List<Block> finishedBoxes = new ArrayList<>();
                for (BlockState state : chunk.getTileEntities(false)) {
                    if (plugin.items().isPlacedBox(state.getBlock()) && placedShouldRemove(state.getBlock(), now)) {
                        finishedBoxes.add(state.getBlock());
                        continue;
                    }
                    if (state instanceof InventoryHolder holder && holder.getInventory() != null) {
                        sweepInventory(null, holder.getInventory(), now);
                    }
                }
                for (Block block : finishedBoxes) {
                    removePlacedBox(block, placedRemovalKey(block, now));
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onShulkerBreak(BlockBreakEvent event) {
        if (plugin.items().isPlacedBox(event.getBlock())) {
            event.setDropItems(false);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onShulkerDrop(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (!plugin.items().isPlacedBox(block)) {
            return;
        }
        Player player = event.getPlayer();
        long now = System.currentTimeMillis();
        if (placedShouldRemove(block, now)) {
            plugin.language().send(player, placedRemovalKey(block, now), Map.of("name", plugin.items().placedName(block)));
            return;
        }
        ItemStack drop = plugin.items().shulkerFromBlock(block);
        if (drop != null) {
            block.getWorld().dropItemNaturally(block.getLocation().add(0.5D, 0.5D, 0.5D), drop);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!plugin.isFeatureEnabled() || areaBreakActive) {
            return;
        }
        Player player = event.getPlayer();
        if (!player.hasPermission(BmMinecraftVipPlugin.USE_PERMISSION)) {
            return;
        }
        ItemStack tool = player.getInventory().getItemInMainHand();
        if (!plugin.items().isTool(tool)) {
            return;
        }
        long now = System.currentTimeMillis();
        if (plugin.items().isActivated(tool) && plugin.items().isExpired(tool, now)) {
            event.setCancelled(true);
            player.getInventory().setItemInMainHand(null);
            plugin.language().send(player, "item-expired", Map.of("name", plugin.items().subjectName(tool)));
            return;
        }
        BmMinecraftVipCatalog.ItemDefinition definition = plugin.catalog().item(plugin.items().id(tool));
        List<Block> extras = List.of();
        if (definition != null) {
            extras = extras(player, event.getBlock(), definition);
            int total = extras.size() + 1;
            int max = plugin.maxAreaBlocks();
            if (total > max) {
                if (warnedArea.add(definition.id())) {
                    plugin.getLogger().warning(plugin.language().format("console.area-too-large", Map.of(
                            "id", definition.id(),
                            "count", Integer.toString(total),
                            "max", Integer.toString(max))));
                }
                extras = List.of();
            }
        }
        List<Block> scheduled = List.copyOf(extras);
        String toolId = plugin.items().id(tool);
        UUID playerId = player.getUniqueId();
        // Wait until this break finishes so vanilla durability is applied before the area break.
        plugin.getServer().getScheduler().runTask(plugin, () -> finishUse(playerId, toolId, scheduled));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        ItemStack held = event.getItem();
        if (!plugin.items().isBox(held)) {
            return;
        }
        Player player = event.getPlayer();
        if (plugin.items().isShulker(held)) {
            if (action == Action.RIGHT_CLICK_AIR) {
                event.setCancelled(true);
                event.setUseItemInHand(Event.Result.DENY);
            }
            return;
        }
        if (plugin.items().isBundle(held)) {
            if (!plugin.isFeatureEnabled()) {
                denyUse(event);
                plugin.language().send(player, "feature-disabled");
                return;
            }
            if (!player.hasPermission(BmMinecraftVipPlugin.USE_PERMISSION)) {
                denyUse(event);
                plugin.language().send(player, "no-use-permission");
                return;
            }
            UUID playerId = player.getUniqueId();
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                Player online = plugin.getServer().getPlayer(playerId);
                if (online != null) {
                    sweepPlayer(online, System.currentTimeMillis());
                }
            });
            return;
        }
        denyUse(event);
        if (!plugin.isFeatureEnabled()) {
            plugin.language().send(player, "feature-disabled");
            return;
        }
        if (!player.hasPermission(BmMinecraftVipPlugin.USE_PERMISSION)) {
            plugin.language().send(player, "no-use-permission");
            return;
        }
        EquipmentSlot hand = event.getHand();
        UUID playerId = player.getUniqueId();
        plugin.getServer().getScheduler().runTask(plugin, () -> takeFromHand(playerId, hand));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        ItemStack hand = event.getItemInHand();
        if (!plugin.items().isBox(hand)) {
            return;
        }
        if (!plugin.items().isShulker(hand)) {
            event.setCancelled(true);
            return;
        }
        Player player = event.getPlayer();
        if (!plugin.isFeatureEnabled()) {
            event.setCancelled(true);
            plugin.language().send(player, "feature-disabled");
            return;
        }
        if (!player.hasPermission(BmMinecraftVipPlugin.USE_PERMISSION)) {
            event.setCancelled(true);
            plugin.language().send(player, "no-use-permission");
            return;
        }
        long now = System.currentTimeMillis();
        if (plugin.items().isActivated(hand) && plugin.items().isExpired(hand, now)) {
            event.setCancelled(true);
            setHand(player, event.getHand(), null);
            plugin.language().send(player, "box-expired", Map.of("name", plugin.items().subjectName(hand)));
            return;
        }
        long expire = plugin.items().isActivated(hand)
                ? plugin.items().expireOf(hand)
                : plugin.items().expiryAfterUse(hand, now);
        String boxId = plugin.items().id(hand);
        Block placed = event.getBlockPlaced();
        plugin.items().markPlaced(placed, boxId, expire);
        if (expire > 0L && now >= expire) {
            plugin.getServer().getScheduler().runTask(plugin, () -> removePlacedBox(placed, "box-expired"));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onShulkerClick(InventoryClickEvent event) {
        Inventory shulker = vipShulkerInventory(event.getView().getTopInventory());
        if (shulker == null || !(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!targetsShulker(event, shulker)) {
            return;
        }
        if (!plugin.isFeatureEnabled()) {
            event.setCancelled(true);
            plugin.language().send(player, "feature-disabled");
            return;
        }
        if (!player.hasPermission(BmMinecraftVipPlugin.USE_PERMISSION)) {
            event.setCancelled(true);
            plugin.language().send(player, "no-use-permission");
            return;
        }
        if (insertsIntoShulker(event, shulker)) {
            event.setCancelled(true);
            return;
        }
        if (!(shulker.getHolder() instanceof Container container)) {
            return;
        }
        Block block = container.getBlock();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (placedShouldRemove(block, System.currentTimeMillis())) {
                removePlacedBox(block, placedRemovalKey(block, System.currentTimeMillis()));
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onShulkerDrag(InventoryDragEvent event) {
        Inventory shulker = vipShulkerInventory(event.getView().getTopInventory());
        if (shulker == null) {
            return;
        }
        int topSize = shulker.getSize();
        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot >= 0 && rawSlot < topSize) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onShulkerMove(InventoryMoveItemEvent event) {
        if (vipShulkerInventory(event.getDestination()) != null) {
            event.setCancelled(true);
            return;
        }
        Inventory source = vipShulkerInventory(event.getSource());
        if (source == null) {
            return;
        }
        if (!plugin.isFeatureEnabled()) {
            event.setCancelled(true);
            return;
        }
        if (source.getHolder() instanceof Container container) {
            Block block = container.getBlock();
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (placedShouldRemove(block, System.currentTimeMillis())) {
                    removePlacedBox(block, placedRemovalKey(block, System.currentTimeMillis()));
                }
            });
        }
    }

    private void finishUse(UUID playerId, String toolId, List<Block> extras) {
        Player player = plugin.getServer().getPlayer(playerId);
        if (player == null || !player.isOnline()) {
            return;
        }
        ItemStack tool = player.getInventory().getItemInMainHand();
        if (!plugin.items().isTool(tool) || !toolId.equals(plugin.items().id(tool))) {
            return;
        }
        long now = System.currentTimeMillis();
        ItemStack prepared = plugin.items().prepareUse(tool, now);
        if (plugin.items().isExpired(prepared, now)) {
            player.getInventory().setItemInMainHand(null);
            plugin.language().send(player, "item-expired", Map.of("name", plugin.items().subjectName(prepared)));
            return;
        }
        player.getInventory().setItemInMainHand(prepared);
        if (!extras.isEmpty()) {
            areaBreakActive = true;
            try {
                for (Block block : extras) {
                    ItemStack hand = player.getInventory().getItemInMainHand();
                    if (!plugin.items().isTool(hand) || !toolId.equals(plugin.items().id(hand))) {
                        break;
                    }
                    if (!player.getWorld().equals(block.getWorld()) || !canAreaBreak(block)) {
                        continue;
                    }
                    player.breakBlock(block);
                }
            } finally {
                areaBreakActive = false;
            }
        }
        ItemStack hand = player.getInventory().getItemInMainHand();
        BmMinecraftVipItems.ItemUpdate update = plugin.items().update(hand, System.currentTimeMillis(), true);
        if (update.changed()) {
            player.getInventory().setItemInMainHand(update.stack());
            if (update.messageKey() != null) {
                plugin.language().send(player, update.messageKey(), Map.of("name", update.subject()));
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onArmor(PlayerArmorChangeEvent event) {
        ItemStack worn = event.getNewItem();
        if (worn == null || worn.getType().isAir() || !plugin.items().isTool(worn) || plugin.items().isActivated(worn)) {
            return;
        }
        BmMinecraftVipCatalog.ItemDefinition definition = plugin.catalog().item(plugin.items().id(worn));
        if (definition == null || definition.function() != BmMinecraftVipCatalog.FunctionType.NONE) {
            return;
        }
        Player player = event.getPlayer();
        if (!plugin.isFeatureEnabled() || !player.hasPermission(BmMinecraftVipPlugin.USE_PERMISSION)) {
            return;
        }
        long now = System.currentTimeMillis();
        ItemStack prepared = plugin.items().prepareUse(worn, now);
        if (plugin.items().isExpired(prepared, now)) {
            player.getInventory().setItem(event.getSlot(), null);
            plugin.language().send(player, "item-expired", Map.of("name", plugin.items().subjectName(prepared)));
            return;
        }
        player.getInventory().setItem(event.getSlot(), prepared);
    }

    private List<Block> extras(Player player, Block origin, BmMinecraftVipCatalog.ItemDefinition definition) {
        if (definition.function() == BmMinecraftVipCatalog.FunctionType.NONE) {
            return List.of();
        }
        if (definition.function() == BmMinecraftVipCatalog.FunctionType.FACE) {
            return faceLayer(origin, minedFace(player, origin), definition.size());
        }
        return forwardLayer(player, origin, definition.width(), definition.depth());
    }

    /** One layer on the mined face. The mined block stays the center, so 3 is 3×3×1. */
    private List<Block> faceLayer(Block origin, BlockFace face, int size) {
        List<Block> blocks = new ArrayList<>();
        int radius = size / 2;
        int lockX = Math.abs(face.getModX());
        int lockY = Math.abs(face.getModY());
        int lockZ = Math.abs(face.getModZ());
        World world = origin.getWorld();
        for (int offsetX = -radius; offsetX <= radius; offsetX++) {
            if (lockX == 1 && offsetX != 0) {
                continue;
            }
            for (int offsetY = -radius; offsetY <= radius; offsetY++) {
                if (lockY == 1 && offsetY != 0) {
                    continue;
                }
                for (int offsetZ = -radius; offsetZ <= radius; offsetZ++) {
                    if (lockZ == 1 && offsetZ != 0) {
                        continue;
                    }
                    if (offsetX == 0 && offsetY == 0 && offsetZ == 0) {
                        continue;
                    }
                    blocks.add(world.getBlockAt(
                            origin.getX() + offsetX, origin.getY() + offsetY, origin.getZ() + offsetZ));
                }
            }
        }
        return blocks;
    }

    /** One horizontal layer. The mined block is the center of the near edge and the area extends forward. */
    private List<Block> forwardLayer(Player player, Block origin, int width, int depth) {
        List<Block> blocks = new ArrayList<>();
        BlockFace facing = horizontalFacing(player);
        int forwardX = facing.getModX();
        int forwardZ = facing.getModZ();
        int sideX = -forwardZ;
        int sideZ = forwardX;
        int sideRadius = width / 2;
        World world = origin.getWorld();
        for (int forward = 0; forward < depth; forward++) {
            for (int side = -sideRadius; side <= sideRadius; side++) {
                if (forward == 0 && side == 0) {
                    continue;
                }
                blocks.add(world.getBlockAt(
                        origin.getX() + forwardX * forward + sideX * side,
                        origin.getY(),
                        origin.getZ() + forwardZ * forward + sideZ * side));
            }
        }
        return blocks;
    }

    private BlockFace minedFace(Player player, Block origin) {
        RayTraceResult trace = player.rayTraceBlocks(8.0D);
        if (trace != null && origin.equals(trace.getHitBlock()) && trace.getHitBlockFace() != null) {
            return trace.getHitBlockFace();
        }
        Vector direction = player.getEyeLocation().getDirection();
        double absoluteX = Math.abs(direction.getX());
        double absoluteY = Math.abs(direction.getY());
        double absoluteZ = Math.abs(direction.getZ());
        if (absoluteY >= absoluteX && absoluteY >= absoluteZ) {
            return direction.getY() >= 0.0D ? BlockFace.UP : BlockFace.DOWN;
        }
        if (absoluteX >= absoluteZ) {
            return direction.getX() >= 0.0D ? BlockFace.EAST : BlockFace.WEST;
        }
        return direction.getZ() >= 0.0D ? BlockFace.SOUTH : BlockFace.NORTH;
    }

    private BlockFace horizontalFacing(Player player) {
        float yaw = player.getLocation().getYaw() % 360.0F;
        if (yaw < 0.0F) {
            yaw += 360.0F;
        }
        if (yaw < 45.0F || yaw >= 315.0F) {
            return BlockFace.SOUTH;
        }
        if (yaw < 135.0F) {
            return BlockFace.WEST;
        }
        if (yaw < 225.0F) {
            return BlockFace.NORTH;
        }
        return BlockFace.EAST;
    }

    private boolean canAreaBreak(Block block) {
        Material type = block.getType();
        if (type.isAir() || type.getHardness() < 0.0F || block.isLiquid()) {
            return false;
        }
        World world = block.getWorld();
        int y = block.getY();
        if (y < world.getMinHeight() || y >= world.getMaxHeight()) {
            return false;
        }
        if (!world.isChunkLoaded(block.getX() >> 4, block.getZ() >> 4)) {
            return false;
        }
        return !(block.getState() instanceof InventoryHolder);
    }

    private void rewriteLoaded(boolean restore) {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            rewriteInventory(player.getInventory(), restore);
            rewriteInventory(player.getEnderChest(), restore);
            ItemStack cursor = player.getItemOnCursor();
            ItemStack mapped = restore ? plugin.items().withPlugin(cursor) : plugin.items().withoutPlugin(cursor);
            if (mapped != cursor) {
                player.setItemOnCursor(mapped == null ? new ItemStack(Material.AIR) : mapped);
            }
        }
        for (World world : plugin.getServer().getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity instanceof Player) {
                    continue;
                }
                if (entity instanceof Item item) {
                    ItemStack mapped = restore
                            ? plugin.items().withPlugin(item.getItemStack())
                            : plugin.items().withoutPlugin(item.getItemStack());
                    if (mapped != item.getItemStack()) {
                        item.setItemStack(mapped);
                    }
                    continue;
                }
                if (entity instanceof ItemFrame frame) {
                    ItemStack mapped = restore
                            ? plugin.items().withPlugin(frame.getItem())
                            : plugin.items().withoutPlugin(frame.getItem());
                    if (mapped != frame.getItem()) {
                        frame.setItem(mapped);
                    }
                    continue;
                }
                if (entity instanceof InventoryHolder holder && holder.getInventory() != null) {
                    rewriteInventory(holder.getInventory(), restore);
                }
            }
            for (Chunk chunk : world.getLoadedChunks()) {
                for (BlockState state : chunk.getTileEntities(false)) {
                    if (state instanceof InventoryHolder holder && holder.getInventory() != null) {
                        rewriteInventory(holder.getInventory(), restore);
                    }
                    if (!plugin.items().isPlacedBox(state.getBlock())) {
                        continue;
                    }
                    BlockState live = state.getBlock().getState();
                    if (!(live instanceof Nameable nameable)) {
                        continue;
                    }
                    if (restore) {
                        nameable.setCustomName(plugin.language().format("lore.box-name", Map.of(
                                "name", plugin.items().placedName(state.getBlock()))));
                    } else {
                        nameable.setCustomName(null);
                    }
                    live.update(true, false);
                }
            }
        }
    }

    private void rewriteInventory(Inventory inventory, boolean restore) {
        if (inventory == null) {
            return;
        }
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            ItemStack updated = restore ? plugin.items().withPlugin(stack) : plugin.items().withoutPlugin(stack);
            if (updated != stack) {
                inventory.setItem(slot, updated);
            }
        }
    }

    private void sweepPlayer(Player player, long now) {
        sweepInventory(player, player.getInventory(), now);
        sweepInventory(player, player.getEnderChest(), now);
        ItemStack cursor = player.getItemOnCursor();
        BmMinecraftVipItems.ItemUpdate cursorUpdate = plugin.items().update(cursor, now, true);
        if (cursorUpdate.changed()) {
            player.setItemOnCursor(airIfNull(cursorUpdate.stack()));
            notify(player, cursorUpdate);
        }
        Inventory top = player.getOpenInventory().getTopInventory();
        if (top != null && top != player.getInventory() && top != player.getEnderChest()) {
            sweepInventory(player, top, now);
            if (vipShulkerInventory(top) != null && top.getHolder() instanceof Container container
                    && placedShouldRemove(container.getBlock(), now)) {
                removePlacedBox(container.getBlock(), placedRemovalKey(container.getBlock(), now));
            }
        }
    }

    private void takeFromHand(UUID playerId, EquipmentSlot hand) {
        Player player = plugin.getServer().getPlayer(playerId);
        if (player == null || !player.isOnline()) {
            return;
        }
        ItemStack held = hand == EquipmentSlot.OFF_HAND
                ? player.getInventory().getItemInOffHand()
                : player.getInventory().getItemInMainHand();
        if (!plugin.items().isBox(held) || plugin.items().isBundle(held) || plugin.items().isShulker(held)) {
            return;
        }
        BmMinecraftVipItems.TakeResult result = plugin.items().take(held, System.currentTimeMillis());
        if (result.given() != null) {
            giveOrDrop(player, result.given(), result.itemName());
            plugin.language().send(player, "box-took", Map.of(
                    "box", result.subject(),
                    "item", result.itemName()));
        }
        setHand(player, hand, result.box());
        if (result.messageKey() != null) {
            plugin.language().send(player, result.messageKey(), Map.of("name", result.subject()));
        }
    }

    private void denyUse(PlayerInteractEvent event) {
        event.setCancelled(true);
        event.setUseItemInHand(Event.Result.DENY);
        event.setUseInteractedBlock(Event.Result.DENY);
    }

    private Inventory vipShulkerInventory(Inventory inventory) {
        if (inventory == null || !(inventory.getHolder() instanceof Container container)) {
            return null;
        }
        if (!plugin.items().isPlacedBox(container.getBlock())) {
            return null;
        }
        return inventory;
    }

    private boolean targetsShulker(InventoryClickEvent event, Inventory shulker) {
        int rawSlot = event.getRawSlot();
        if (event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY && rawSlot >= shulker.getSize()) {
            return true;
        }
        return rawSlot >= 0 && rawSlot < shulker.getSize();
    }

    private boolean insertsIntoShulker(InventoryClickEvent event, Inventory shulker) {
        if (event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY && event.getRawSlot() >= shulker.getSize()) {
            return true;
        }
        if (event.getRawSlot() < 0 || event.getRawSlot() >= shulker.getSize()) {
            return false;
        }
        return switch (event.getAction()) {
            case PLACE_ALL, PLACE_SOME, PLACE_ONE, SWAP_WITH_CURSOR -> true;
            case HOTBAR_SWAP, HOTBAR_MOVE_AND_READD -> hotbarHasItem(event);
            case PICKUP_ALL, PICKUP_HALF, PICKUP_SOME, PICKUP_ONE, MOVE_TO_OTHER_INVENTORY,
                    DROP_ALL_SLOT, DROP_ONE_SLOT, DROP_ALL_CURSOR, DROP_ONE_CURSOR,
                    CLONE_STACK, COLLECT_TO_CURSOR -> false;
            default -> event.getCursor() != null && !event.getCursor().getType().isAir();
        };
    }

    private boolean hotbarHasItem(InventoryClickEvent event) {
        int button = event.getHotbarButton();
        if (button < 0) {
            return true;
        }
        ItemStack stack = event.getWhoClicked().getInventory().getItem(button);
        return stack != null && !stack.getType().isAir();
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBundleClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || !bundleClick(event)) {
            return;
        }
        if (!plugin.items().isBundle(event.getCurrentItem()) && !plugin.items().isBundle(event.getCursor())) {
            return;
        }
        if (!plugin.isFeatureEnabled()) {
            event.setCancelled(true);
            plugin.language().send(player, "feature-disabled");
            return;
        }
        if (!player.hasPermission(BmMinecraftVipPlugin.USE_PERMISSION)) {
            event.setCancelled(true);
            plugin.language().send(player, "no-use-permission");
            return;
        }
        if (bundleInsert(event.getAction())) {
            event.setCancelled(true);
            return;
        }
        UUID playerId = player.getUniqueId();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            Player online = plugin.getServer().getPlayer(playerId);
            if (online != null) {
                sweepPlayer(online, System.currentTimeMillis());
            }
        });
    }

    private boolean bundleClick(InventoryClickEvent event) {
        return switch (event.getAction()) {
            case PICKUP_FROM_BUNDLE, PICKUP_ALL_INTO_BUNDLE, PICKUP_SOME_INTO_BUNDLE,
                    PLACE_FROM_BUNDLE, PLACE_ALL_INTO_BUNDLE, PLACE_SOME_INTO_BUNDLE -> true;
            default -> false;
        };
    }

    private boolean bundleInsert(InventoryAction action) {
        return action == InventoryAction.PICKUP_ALL_INTO_BUNDLE
                || action == InventoryAction.PICKUP_SOME_INTO_BUNDLE
                || action == InventoryAction.PLACE_ALL_INTO_BUNDLE
                || action == InventoryAction.PLACE_SOME_INTO_BUNDLE;
    }

    private boolean placedShouldRemove(Block block, long now) {
        if (!plugin.items().isPlacedBox(block)) {
            return false;
        }
        long expire = plugin.items().placedExpire(block);
        if (expire > 0L && now >= expire) {
            return true;
        }
        return placedEmpty(block);
    }

    private String placedRemovalKey(Block block, long now) {
        long expire = plugin.items().placedExpire(block);
        if (expire > 0L && now >= expire) {
            return "box-expired";
        }
        return "box-finished";
    }

    private boolean placedEmpty(Block block) {
        if (!(block.getState() instanceof Container container)) {
            return false;
        }
        for (ItemStack item : container.getInventory().getContents()) {
            if (item != null && !item.getType().isAir()) {
                return false;
            }
        }
        return true;
    }

    private void removePlacedBox(Block block, String messageKey) {
        if (!plugin.items().isPlacedBox(block)) {
            return;
        }
        String name = plugin.items().placedName(block);
        for (Player viewer : block.getWorld().getPlayers()) {
            Inventory top = viewer.getOpenInventory().getTopInventory();
            if (top.getHolder() instanceof Container container && block.equals(container.getBlock())) {
                viewer.closeInventory();
                plugin.language().send(viewer, messageKey, Map.of("name", name));
            }
        }
        block.setType(Material.AIR);
    }

    private void sweepInventory(Player player, Inventory inventory, long now) {
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            BmMinecraftVipItems.ItemUpdate update = plugin.items().update(stack, now, true);
            if (!update.changed()) {
                continue;
            }
            inventory.setItem(slot, update.stack());
            notify(player, update);
        }
    }

    private void notify(Player player, BmMinecraftVipItems.ItemUpdate update) {
        if (player == null || update.messageKey() == null) {
            return;
        }
        plugin.language().send(player, update.messageKey(), Map.of("name", update.subject()));
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

    private void setHand(Player player, EquipmentSlot hand, ItemStack stack) {
        if (hand == EquipmentSlot.OFF_HAND) {
            player.getInventory().setItemInOffHand(stack);
            return;
        }
        player.getInventory().setItemInMainHand(stack);
    }

    private ItemStack airIfNull(ItemStack stack) {
        return stack == null ? new ItemStack(Material.AIR) : stack;
    }
}
