package bm.minecraft.vip;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Handles administrative commands for VIP items and boxes. */
public final class BmMinecraftVipCommand implements CommandExecutor, TabCompleter {
    private final BmMinecraftVipPlugin plugin;

    public BmMinecraftVipCommand(BmMinecraftVipPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            help(sender);
            return true;
        }
        if (!plugin.canManage(sender)) {
            plugin.language().send(sender, "no-permission");
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "0", "1" -> {
                boolean enabled = args[0].equals("1");
                plugin.setFeatureEnabled(enabled);
                plugin.language().send(sender, enabled ? "enabled" : "disabled");
            }
            case "reload" -> {
                plugin.reloadAll();
                plugin.language().send(sender, "reloaded");
            }
            case "info" -> plugin.language().send(sender, "info", Map.of(
                    "version", plugin.getPluginMeta().getVersion()));
            case "status" -> plugin.language().send(sender, "status", Map.of(
                    "enabled", plugin.language().format(
                            plugin.isFeatureEnabled() ? "values.status-on" : "values.status-off", Map.of()),
                    "items", Integer.toString(plugin.catalog().itemCount()),
                    "boxes", Integer.toString(plugin.catalog().boxCount()),
                    "joins", Integer.toString(plugin.joins().packCount())));
            case "give" -> give(sender, args);
            default -> help(sender);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!plugin.canManage(sender)) {
            return List.of();
        }
        if (args.length == 1) {
            return filter(List.of("0", "1", "info", "status", "reload", "give"), args[0]);
        }
        if (!args[0].equalsIgnoreCase("give")) {
            return List.of();
        }
        if (args.length == 2) {
            return filter(List.of("item", "box"), args[1]);
        }
        if (args.length == 3) {
            String kind = args[1].toLowerCase(Locale.ROOT);
            if (kind.equals("item")) {
                return filter(plugin.catalog().itemIds(), args[2]);
            }
            if (kind.equals("box")) {
                List<String> ids = new ArrayList<>(plugin.catalog().boxIds());
                for (String packId : plugin.joins().packIds()) {
                    if (!ids.contains(packId)) {
                        ids.add(packId);
                    }
                }
                return filter(ids, args[2]);
            }
            return List.of();
        }
        if (args.length == 4) {
            List<String> names = new ArrayList<>();
            for (Player player : plugin.getServer().getOnlinePlayers()) {
                names.add(player.getName());
            }
            return filter(names, args[3]);
        }
        return List.of();
    }

    private void give(CommandSender sender, String[] args) {
        if (args.length < 3 || args.length > 4) {
            plugin.language().send(sender, "usage-give");
            return;
        }
        Player target = target(sender, args);
        if (target == null) {
            return;
        }
        String kind = args[1].toLowerCase(Locale.ROOT);
        String id = args[2].trim();
        ItemStack stack;
        String messageKey;
        String name;
        if (kind.equals("item")) {
            BmMinecraftVipCatalog.ItemDefinition definition = plugin.catalog().item(id);
            if (definition == null) {
                plugin.language().send(sender, "unknown-item", Map.of("id", id));
                return;
            }
            stack = plugin.items().createTool(definition);
            messageKey = "gave-item";
            name = definition.name();
        } else if (kind.equals("box")) {
            BmMinecraftVipCatalog.BoxDefinition definition = plugin.catalog().box(id);
            if (definition != null) {
                stack = plugin.items().createBox(definition);
                name = definition.name();
            } else {
                stack = plugin.joins().create(id);
                name = plugin.joins().name(id);
            }
            if (stack == null) {
                plugin.language().send(sender, "unknown-box", Map.of("id", id));
                return;
            }
            messageKey = "gave-box";
        } else {
            plugin.language().send(sender, "usage-give");
            return;
        }
        if (stack == null) {
            plugin.language().send(sender, kind.equals("box") ? "unknown-box" : "unknown-item", Map.of("id", id));
            return;
        }
        Map<Integer, ItemStack> leftover = target.getInventory().addItem(stack);
        Map<String, String> values = Map.of("name", name, "player", target.getName());
        tell(sender, target, messageKey, values);
        if (leftover.isEmpty()) {
            return;
        }
        for (ItemStack remain : leftover.values()) {
            target.getWorld().dropItemNaturally(target.getLocation(), remain);
        }
        tell(sender, target, "dropped", values);
    }

    private Player target(CommandSender sender, String[] args) {
        if (args.length == 3) {
            if (sender instanceof Player player) {
                return player;
            }
            plugin.language().send(sender, "player-only");
            return null;
        }
        Player player = plugin.getServer().getPlayerExact(args[3]);
        if (player == null) {
            plugin.language().send(sender, "player-not-found", Map.of("player", args[3]));
        }
        return player;
    }

    private void tell(CommandSender sender, Player target, String key, Map<String, String> values) {
        plugin.language().send(sender, key, values);
        if (target != sender) {
            plugin.language().send(target, key, values);
        }
    }

    private void help(CommandSender sender) {
        for (String key : List.of(
                "help-header", "help-toggle", "help-reload", "help-info", "help-status",
                "help-give-item", "help-give-box", "help-footer")) {
            plugin.language().send(sender, key);
        }
    }

    private List<String> filter(List<String> options, String input) {
        String prefix = input.toLowerCase(Locale.ROOT);
        return options.stream()
                .filter(option -> option.toLowerCase(Locale.ROOT).startsWith(prefix))
                .toList();
    }
}
