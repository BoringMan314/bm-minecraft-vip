package bm.minecraft.vip;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Map;

/** Loads player-facing text for the language selected by this build. */
public final class BmMinecraftVipLanguageManager {
    private static final String FALLBACK_LANGUAGE = "zh_TW";

    private final BmMinecraftVipPlugin plugin;
    private YamlConfiguration languageConfig = new YamlConfiguration();
    private int reloadCount;

    public BmMinecraftVipLanguageManager(BmMinecraftVipPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        // Bump first so items refresh their lore even if loading falls back to defaults.
        reloadCount++;
        plugin.saveResource("active-language.yml", true);
        File activeLanguageFile = new File(plugin.getDataFolder(), "active-language.yml");
        String language = YamlConfiguration.loadConfiguration(activeLanguageFile)
                .getString("language", FALLBACK_LANGUAGE);
        String resourcePath = "lang/" + language + ".yml";
        if (plugin.getResource(resourcePath) == null) {
            YamlConfiguration fallback = loadBundledConfiguration("lang/" + FALLBACK_LANGUAGE + ".yml");
            plugin.getLogger().warning(console(fallback, "unknown-language", Map.of(
                    "language", language,
                    "fallback", FALLBACK_LANGUAGE)));
            resourcePath = "lang/" + FALLBACK_LANGUAGE + ".yml";
        }
        YamlConfiguration bundled = loadBundledConfiguration(resourcePath);
        File languageFile = new File(plugin.getDataFolder(), resourcePath);
        if (!languageFile.isFile()) {
            plugin.saveResource(resourcePath, false);
        } else if (!upgradeOutdatedLanguageFile(languageFile, resourcePath, bundled)) {
            languageConfig = bundled;
            return;
        }
        languageConfig = YamlConfiguration.loadConfiguration(languageFile);
        languageConfig.setDefaults(bundled);
        saveMissingDefaults(languageFile);
        languageConfig = YamlConfiguration.loadConfiguration(languageFile);
    }

    public int reloadCount() {
        return reloadCount;
    }

    public void send(CommandSender sender, String key) {
        sender.sendMessage(format("messages." + key, Map.of()));
    }

    public void send(CommandSender sender, String key, Map<String, String> replacements) {
        sender.sendMessage(format("messages." + key, replacements));
    }

    public String format(String path, Map<String, String> replacements) {
        return colour(replace(languageConfig.getString(path, path), replacements));
    }

    public String getConsole(String key) {
        return languageConfig.getString("console." + key, key);
    }

    private YamlConfiguration loadBundledConfiguration(String resourcePath) {
        try (InputStream input = plugin.getResource(resourcePath)) {
            if (input != null) {
                return YamlConfiguration.loadConfiguration(new InputStreamReader(input, StandardCharsets.UTF_8));
            }
        } catch (Exception exception) {
            plugin.getLogger().warning(getConsole("bundled-defaults-load-failed")
                    .replace("{resource}", resourcePath));
        }
        return new YamlConfiguration();
    }

    private boolean upgradeOutdatedLanguageFile(
            File languageFile, String resourcePath, YamlConfiguration bundled) {
        int localVersion = YamlConfiguration.loadConfiguration(languageFile)
                .getInt("language-format-version", 0);
        int bundledVersion = bundled.getInt("language-format-version", 0);
        if (localVersion >= bundledVersion) {
            return true;
        }

        File backupFile = new File(
                languageFile.getParentFile(),
                languageFile.getName() + ".pre-v" + bundledVersion + ".bak");
        File temporaryFile = new File(languageFile.getParentFile(), languageFile.getName() + ".upgrade.tmp");
        try (InputStream input = plugin.getResource(resourcePath)) {
            if (input == null) {
                throw new IOException("Bundled resource is unavailable");
            }
            Files.copy(input, temporaryFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            Files.copy(languageFile.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            try {
                Files.move(temporaryFile.toPath(), languageFile.toPath(),
                        StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporaryFile.toPath(), languageFile.toPath(),
                        StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (Exception exception) {
            try {
                Files.deleteIfExists(temporaryFile.toPath());
            } catch (IOException ignored) {
                // The original language file is still intact.
            }
            plugin.getLogger().warning(console(bundled, "language-upgrade-failed", Map.of(
                    "file", languageFile.getName(),
                    "error", exception.getMessage() == null
                            ? exception.getClass().getSimpleName()
                            : exception.getMessage())));
            return false;
        }
    }

    private void saveMissingDefaults(File languageFile) {
        languageConfig.options().copyDefaults(true);
        try {
            languageConfig.save(languageFile);
        } catch (Exception exception) {
            plugin.getLogger().warning(getConsole("language-update-failed")
                    .replace("{file}", languageFile.getName()));
        }
    }

    private String console(YamlConfiguration configuration, String key, Map<String, String> replacements) {
        return replace(configuration.getString("console." + key, key), replacements);
    }

    private String replace(String message, Map<String, String> replacements) {
        if (message == null) {
            return "";
        }
        if (replacements == null || replacements.isEmpty()) {
            return message;
        }
        for (Map.Entry<String, String> replacement : replacements.entrySet()) {
            message = message.replace("{" + replacement.getKey() + "}", replacement.getValue());
        }
        return message;
    }

    private String colour(String message) {
        return ChatColor.translateAlternateColorCodes('&', message == null ? "" : message);
    }
}
