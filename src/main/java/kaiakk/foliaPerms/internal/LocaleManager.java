package kaiakk.foliaPerms.internal;

import kaiakk.foliaPerms.FoliaPerms;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

/**
 * Manages localized strings for the FoliaPerms plugin.
 * Loads language files from plugins/FoliaPerms/locales/<language>.yml
 * and provides convenient access with parameter substitution and color coding.
 *
 * Version: 0.1.0+26.1.2
 */
public class LocaleManager {

    private static final String LOCALES_DIR = "locales";
    private static final String DEFAULT_LANGUAGE = "en_us";

    private final FoliaPerms plugin;
    private final Map<String, String> localeCache = new HashMap<>();
    private String currentLanguage = DEFAULT_LANGUAGE;
    private boolean loaded = false;

    public LocaleManager(FoliaPerms plugin) {
        this.plugin = plugin;
    }

    /**
     * Loads the specified language file.
     * Falls back to en_us if the requested file does not exist or is invalid.
     *
     * @param language the language code (e.g. "en_us", "zh_cn"), without .yml extension
     */
    public synchronized void load(String language) {
        if (language == null || language.isBlank()) {
            language = DEFAULT_LANGUAGE;
        }

        this.currentLanguage = language;
        this.localeCache.clear();

        // Ensure locales directory exists
        File localesDir = new File(plugin.getDataFolder(), LOCALES_DIR);
        if (!localesDir.exists()) {
            localesDir.mkdirs();
        }

        // Copy default locale files from resources if they don't exist
        copyDefaultLocaleFiles(localesDir);

        // Try to load the requested language file
        File langFile = new File(localesDir, language + ".yml");
        if (!langFile.exists()) {
            plugin.getLogger().warning("Locale file '" + langFile.getName() + "' not found. Falling back to '"
                    + DEFAULT_LANGUAGE + ".yml'.");
            langFile = new File(localesDir, DEFAULT_LANGUAGE + ".yml");
            this.currentLanguage = DEFAULT_LANGUAGE;
        }

        if (!langFile.exists()) {
            plugin.getLogger().severe("Default locale file '" + DEFAULT_LANGUAGE + ".yml' not found! "
                    + "Using internal fallback keys.");
            this.loaded = true;
            return;
        }

        YamlConfiguration config = new YamlConfiguration();
        try {
            config.load(langFile);
            flattenConfiguration(config);
            this.loaded = true;
            plugin.getLogger().info("Loaded locale: " + this.currentLanguage + ".yml");
        } catch (IOException | InvalidConfigurationException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load locale file: " + langFile.getAbsolutePath(), e);
            // Try fallback
            if (!language.equals(DEFAULT_LANGUAGE)) {
                plugin.getLogger().warning("Falling back to '" + DEFAULT_LANGUAGE + ".yml'.");
                load(DEFAULT_LANGUAGE);
            }
        }
    }

    /**
     * Flattens a YAML configuration into the locale cache.
     * Uses getValues(true) to recursively retrieve all nested keys with dot-separated paths.
     */
    private void flattenConfiguration(YamlConfiguration config) {
        for (Map.Entry<String, Object> entry : config.getValues(true).entrySet()) {
            if (entry.getValue() instanceof String) {
                localeCache.put(entry.getKey(), (String) entry.getValue());
            }
        }
    }

    /**
     * Copies default locale files from the JAR resources to the data folder.
     */
    private void copyDefaultLocaleFiles(File localesDir) {
        String[] defaultLocales = {DEFAULT_LANGUAGE + ".yml", "zh_cn.yml"};
        for (String localeName : defaultLocales) {
            File targetFile = new File(localesDir, localeName);
            if (!targetFile.exists()) {
                try (InputStream in = plugin.getClass().getClassLoader()
                        .getResourceAsStream(LOCALES_DIR + "/" + localeName)) {
                    if (in != null) {
                        try (OutputStream out = new FileOutputStream(targetFile)) {
                            byte[] buffer = new byte[4096];
                            int bytesRead;
                            while ((bytesRead = in.read(buffer)) != -1) {
                                out.write(buffer, 0, bytesRead);
                            }
                        }
                        plugin.getLogger().info("Copied default locale file: " + localeName);
                    } else {
                        plugin.getLogger().warning("Default locale resource not found in JAR: " + localeName);
                    }
                } catch (IOException e) {
                    plugin.getLogger().log(Level.WARNING, "Failed to copy default locale file: " + localeName, e);
                }
            }
        }
    }

    /**
     * Reloads the current language file.
     */
    public synchronized void reload() {
        load(this.currentLanguage);
    }

    /**
     * Gets the raw (uncolored) localized string for the given key.
     * If the key is not found, the key itself is returned.
     *
     * @param key  the localization key
     * @param args optional arguments for {0}, {1}, ... placeholders
     * @return the localized string with placeholders replaced, but without color translation
     */
    public String getString(String key, Object... args) {
        if (!loaded) {
            load(currentLanguage);
        }

        String value = localeCache.get(key);
        if (value == null) {
            // Key not found, return the key itself as fallback
            return key;
        }

        return formatMessage(value, args);
    }

    /**
     * Gets the colorized localized string for the given key.
     * This translates & color codes to Minecraft section sign codes.
     *
     * @param key  the localization key
     * @param args optional arguments for {0}, {1}, ... placeholders
     * @return the localized string with color codes translated and placeholders replaced
     */
    public String getColoredString(String key, Object... args) {
        String raw = getString(key, args);
        if (raw == null) return null;
        return ColorConverter.colorize(raw);
    }

    /**
     * Gets the stripped (no color codes) localized string for the given key.
     * Useful for console output where color codes should be removed.
     *
     * @param key  the localization key
     * @param args optional arguments for {0}, {1}, ... placeholders
     * @return the localized string with all color codes stripped and placeholders replaced
     */
    public String getStrippedString(String key, Object... args) {
        String raw = getString(key, args);
        if (raw == null) return null;
        return ColorConverter.stripColor(raw);
    }

    /**
     * Returns the current language code.
     */
    public String getCurrentLanguage() {
        return currentLanguage;
    }

    /**
     * Checks if a localization key exists.
     */
    public boolean hasKey(String key) {
        return localeCache.containsKey(key);
    }

    /**
     * Replaces {0}, {1}, ... placeholders in the message with the provided arguments.
     * Uses simple string replacement to avoid MessageFormat's apostrophe handling.
     */
    private String formatMessage(String message, Object... args) {
        if (args == null || args.length == 0) {
            return message;
        }
        String result = message;
        for (int i = 0; i < args.length; i++) {
            String placeholder = "{" + i + "}";
            String replacement = args[i] != null ? args[i].toString() : "null";
            result = result.replace(placeholder, replacement);
        }
        return result;
    }
}
