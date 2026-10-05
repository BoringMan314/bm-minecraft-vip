package bm.minecraft.vip;

import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.util.Map;

/** Gives timed VIP tools and boxes, then removes them when their time runs out. */
public final class BmMinecraftVipPlugin extends JavaPlugin {
    public static final String USE_PERMISSION = "bm-minecraft-vip.use";
    private static final String ADMIN_PERMISSION = "bm-minecraft-vip.admin";
    private static final String COMMAND_NAME = "bm-minecraft-vip";

    private BmMinecraftVipLanguageManager languageManager;
    private BmMinecraftVipCatalog catalog;
    private BmMinecraftVipJoin joins;
    private BmMinecraftVipItems items;
    private BmMinecraftVipListener listener;
    private BukkitTask expiryTask;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        languageManager = new BmMinecraftVipLanguageManager(this);
        languageManager.reload();
        ensureResource("config-item.yml");
        ensureResource("config-op-item.yml");
        ensureResource("config-box.yml");
        ensureResource("config-join.yml");
        catalog = new BmMinecraftVipCatalog(this);
        catalog.load();
        items = new BmMinecraftVipItems(this);
        joins = new BmMinecraftVipJoin(this);
        joins.load();
        registerCommand();
        listener = new BmMinecraftVipListener(this);
        getServer().getPluginManager().registerEvents(listener, this);
        listener.restoreLoadedItems();
        expiryTask = getServer().getScheduler().runTaskTimer(this, listener::tick, 20L, 20L);
        getLogger().info(languageManager.format("console.enabled", Map.of(
                "version", getPluginMeta().getVersion())));
        getLogger().info(languageManager.format("console.loaded", Map.of(
                "items", Integer.toString(catalog.itemCount()),
                "boxes", Integer.toString(catalog.boxCount()),
                "joins", Integer.toString(joins.packCount()))));
    }

    @Override
    public void onDisable() {
        if (expiryTask != null) {
            expiryTask.cancel();
            expiryTask = null;
        }
        // Disabling also happens during normal shutdown. Persist VIP metadata intact.
        if (languageManager != null) {
            getLogger().info(languageManager.getConsole("disabled"));
        }
    }

    public BmMinecraftVipLanguageManager language() {
        return languageManager;
    }

    public BmMinecraftVipCatalog catalog() {
        return catalog;
    }

    public BmMinecraftVipJoin joins() {
        return joins;
    }

    public BmMinecraftVipItems items() {
        return items;
    }

    public boolean isFeatureEnabled() {
        return getConfig().getBoolean("enabled", true);
    }

    public int maxAreaBlocks() {
        return Math.clamp(getConfig().getInt("max-area-blocks", 125), 1, 4096);
    }

    public boolean canManage(CommandSender sender) {
        return !(sender instanceof Player)
                || !getConfig().getBoolean("admin-require-op", true)
                || sender.isOp()
                || sender.hasPermission(ADMIN_PERMISSION);
    }

    public void setFeatureEnabled(boolean enabled) {
        getConfig().set("enabled", enabled);
        saveConfig();
    }

    public void reloadAll() {
        reloadConfig();
        languageManager.reload();
        ensureResource("config-item.yml");
        ensureResource("config-op-item.yml");
        ensureResource("config-box.yml");
        ensureResource("config-join.yml");
        catalog.load();
        joins.load();
    }

    private void registerCommand() {
        PluginCommand command = getCommand(COMMAND_NAME);
        if (command == null) {
            throw new IllegalStateException(languageManager.getConsole("missing-command")
                    .replace("{command}", COMMAND_NAME));
        }
        BmMinecraftVipCommand executor = new BmMinecraftVipCommand(this);
        command.setExecutor(executor);
        command.setTabCompleter(executor);
    }

    private void ensureResource(String name) {
        File file = new File(getDataFolder(), name);
        if (file.isFile()) {
            return;
        }
        try {
            saveResource(name, false);
        } catch (IllegalArgumentException exception) {
            getLogger().warning(languageManager.format("console.config-copy-failed", Map.of("file", name)));
        }
    }
}
