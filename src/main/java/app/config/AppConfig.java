package app.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * アプリケーション設定を管理するクラス
 */
public class AppConfig {
    private static final Logger logger = LoggerFactory.getLogger(AppConfig.class);
    private static final String CONFIG_FILE = "app.properties";
    private static AppConfig instance;
    private final Properties properties;

    private AppConfig() {
        this.properties = new Properties();
        loadProperties();
    }

    public static synchronized AppConfig getInstance() {
        if (instance == null) {
            instance = new AppConfig();
        }
        return instance;
    }

    private void loadProperties() {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(CONFIG_FILE)) {
            if (input != null) {
                properties.load(input);
                logger.info("Configuration loaded from {}", CONFIG_FILE);
            } else {
                logger.warn("Configuration file {} not found, using default values", CONFIG_FILE);
                setDefaultProperties();
            }
        } catch (IOException e) {
            logger.error("Failed to load configuration file", e);
            setDefaultProperties();
        }
    }

    private void setDefaultProperties() {
        // UI設定
        properties.setProperty("ui.window.width", "1200");
        properties.setProperty("ui.window.height", "800");
        properties.setProperty("ui.notes.list.width", "280");
        properties.setProperty("ui.notes.list.max.items", "500");
        properties.setProperty("ui.notes.list.min.height", "44");
        
        // 自動保存設定
        properties.setProperty("autosave.debounce.ms", "600");
        properties.setProperty("search.debounce.ms", "300");
        
        // データベース設定
        properties.setProperty("database.path", "data/app.db");
        properties.setProperty("database.connection.timeout.ms", "30000");
        
        // 検索設定
        properties.setProperty("search.notes.limit", "300");
        properties.setProperty("search.events.limit", "200");
        
        // 予定設定
        properties.setProperty("event.default.duration.minutes", "90");
        properties.setProperty("event.min.duration.minutes", "5");
        properties.setProperty("event.snap.minutes", "15");
        
        // ログ設定
        properties.setProperty("log.level", "INFO");
        properties.setProperty("log.file.max.size", "10MB");
        properties.setProperty("log.file.max.history", "30");
    }

    // UI設定
    public int getWindowWidth() {
        return getIntProperty("ui.window.width", 1200);
    }

    public int getWindowHeight() {
        return getIntProperty("ui.window.height", 800);
    }

    public int getNotesListWidth() {
        return getIntProperty("ui.notes.list.width", 280);
    }

    public int getNotesListMaxItems() {
        return getIntProperty("ui.notes.list.max.items", 500);
    }

    public int getNotesListMinHeight() {
        return getIntProperty("ui.notes.list.min.height", 44);
    }

    // 自動保存設定
    public double getAutosaveDebounceMs() {
        return getDoubleProperty("autosave.debounce.ms", 600.0);
    }

    public double getSearchDebounceMs() {
        return getDoubleProperty("search.debounce.ms", 300.0);
    }

    // データベース設定
    public String getDatabasePath() {
        return getStringProperty("database.path", "data/app.db");
    }

    public int getDatabaseConnectionTimeoutMs() {
        return getIntProperty("database.connection.timeout.ms", 30000);
    }

    // 検索設定
    public int getSearchNotesLimit() {
        return getIntProperty("search.notes.limit", 300);
    }

    public int getSearchEventsLimit() {
        return getIntProperty("search.events.limit", 200);
    }

    // 予定設定
    public int getEventDefaultDurationMinutes() {
        return getIntProperty("event.default.duration.minutes", 90);
    }

    public int getEventMinDurationMinutes() {
        return getIntProperty("event.min.duration.minutes", 5);
    }

    public int getEventSnapMinutes() {
        return getIntProperty("event.snap.minutes", 15);
    }

    // ログ設定
    public String getLogLevel() {
        return getStringProperty("log.level", "INFO");
    }

    public String getLogFileMaxSize() {
        return getStringProperty("log.file.max.size", "10MB");
    }

    public int getLogFileMaxHistory() {
        return getIntProperty("log.file.max.history", 30);
    }

    // ヘルパーメソッド
    private String getStringProperty(String key, String defaultValue) {
        return properties.getProperty(key, defaultValue);
    }

    private int getIntProperty(String key, int defaultValue) {
        try {
            return Integer.parseInt(properties.getProperty(key, String.valueOf(defaultValue)));
        } catch (NumberFormatException e) {
            logger.warn("Invalid integer value for property {}: {}, using default: {}", 
                       key, properties.getProperty(key), defaultValue);
            return defaultValue;
        }
    }

    private double getDoubleProperty(String key, double defaultValue) {
        try {
            return Double.parseDouble(properties.getProperty(key, String.valueOf(defaultValue)));
        } catch (NumberFormatException e) {
            logger.warn("Invalid double value for property {}: {}, using default: {}", 
                       key, properties.getProperty(key), defaultValue);
            return defaultValue;
        }
    }

    // 設定の動的更新（必要に応じて）
    public void setProperty(String key, String value) {
        properties.setProperty(key, value);
        logger.debug("Property updated: {} = {}", key, value);
    }

    public void reload() {
        loadProperties();
        logger.info("Configuration reloaded");
    }
}