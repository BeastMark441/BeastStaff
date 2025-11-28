package ru.beastmark.litebans;

import ru.beastmark.BeastStaff;
import org.bukkit.configuration.file.FileConfiguration;

import java.io.File;
import java.sql.*;

/**
 * Менеджер для подключения к БД LiteBans
 * Поддерживает использование той же БД, что и BeastStaff, или отдельную БД (MySQL/SQLite)
 */
public class LiteBansDatabaseManager {
    
    private final BeastStaff plugin;
    private Connection connection;
    private boolean useSameDatabase;
    private String databaseType;
    
    public LiteBansDatabaseManager(BeastStaff plugin) {
        this.plugin = plugin;
        FileConfiguration config = plugin.getConfig();
        this.useSameDatabase = config.getBoolean("integrations.litebans.use-same-database", true);
        
        if (!useSameDatabase) {
            // Определяем тип БД из конфига
            this.databaseType = config.getString("integrations.litebans.database.type", "mysql").toLowerCase();
        }
    }
    
    /**
     * Получить подключение к БД LiteBans
     */
    public Connection getConnection() {
        // Проверяем существующее подключение
        if (connection != null) {
            try {
                if (connection.isClosed() || !connection.isValid(2)) {
                    connection = null;
                } else {
                    return connection;
                }
            } catch (SQLException e) {
                connection = null;
            }
        }
        
        // Создаём новое подключение
        if (useSameDatabase) {
            // Используем ту же БД, что и BeastStaff
            connection = plugin.getDatabaseManager().getConnection();
            return connection;
        } else {
            // Подключаемся к отдельной БД LiteBans
            return connectToSeparateDatabase();
        }
    }
    
    /**
     * Подключение к отдельной БД LiteBans
     */
    private Connection connectToSeparateDatabase() {
        FileConfiguration config = plugin.getConfig();
        
        try {
            if (databaseType.equals("sqlite")) {
                return connectToSQLite(config);
            } else {
                // По умолчанию MySQL
                return connectToMySQL(config);
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Ошибка подключения к БД LiteBans: " + e.getMessage());
            if (plugin.getConfig().getBoolean("debug.log-sql", false)) {
                e.printStackTrace();
            }
            return null;
        }
    }
    
    /**
     * Подключение к MySQL БД LiteBans
     */
    private Connection connectToMySQL(FileConfiguration config) throws SQLException {
        String host = config.getString("integrations.litebans.database.host", "localhost");
        int port = config.getInt("integrations.litebans.database.port", 3306);
        String database = config.getString("integrations.litebans.database.database", "litebans");
        String username = config.getString("integrations.litebans.database.username", "");
        String password = config.getString("integrations.litebans.database.password", "");
        
        String url = "jdbc:mysql://" + host + ":" + port + "/" + database + 
                    "?useSSL=false&autoReconnect=true&useUnicode=true&characterEncoding=UTF-8";
        
        connection = DriverManager.getConnection(url, username, password);
        plugin.getLogger().info("✓ Подключение к MySQL БД LiteBans установлено: " + host + ":" + port + "/" + database);
        return connection;
    }
    
    /**
     * Подключение к SQLite БД LiteBans
     */
    private Connection connectToSQLite(FileConfiguration config) throws SQLException {
        // Путь к файлу БД LiteBans
        String dbPath = config.getString("integrations.litebans.database.path", "");
        
        if (dbPath.isEmpty()) {
            // Пытаемся найти БД в стандартных местах
            File pluginsFolder = plugin.getDataFolder().getParentFile();
            File litebansFolder = new File(pluginsFolder, "LiteBans");
            
            // Проверяем стандартные пути
            File[] possiblePaths = {
                new File(litebansFolder, "litebans.db"),
                new File(litebansFolder, "database.db"),
                new File(pluginsFolder.getParentFile(), "litebans.db"),
                new File(plugin.getDataFolder(), "litebans.db")
            };
            
            for (File dbFile : possiblePaths) {
                if (dbFile.exists() && dbFile.isFile()) {
                    dbPath = dbFile.getAbsolutePath();
                    plugin.getLogger().info("Найдена SQLite БД LiteBans: " + dbPath);
                    break;
                }
            }
            
            if (dbPath.isEmpty()) {
                throw new SQLException("SQLite БД LiteBans не найдена! Укажите путь в config.yml: integrations.litebans.database.path");
            }
        } else {
            // Используем указанный путь
            File dbFile = new File(dbPath);
            if (!dbFile.exists()) {
                throw new SQLException("SQLite БД не найдена по указанному пути: " + dbPath);
            }
        }
        
        String url = "jdbc:sqlite:" + dbPath;
        connection = DriverManager.getConnection(url);
        plugin.getLogger().info("✓ Подключение к SQLite БД LiteBans установлено: " + dbPath);
        return connection;
    }
    
    /**
     * Проверить, подключены ли к БД
     */
    public boolean isConnected() {
        if (connection == null) return false;
        try {
            return !connection.isClosed() && connection.isValid(2);
        } catch (SQLException e) {
            return false;
        }
    }
    
    /**
     * Закрыть подключение
     */
    public void closeConnection() {
        if (connection != null) {
            try {
                // Закрываем только если это отдельное подключение
                if (!useSameDatabase && connection != plugin.getDatabaseManager().getConnection()) {
                    connection.close();
                    plugin.getLogger().info("Подключение к БД LiteBans закрыто");
                }
                connection = null;
            } catch (SQLException e) {
                plugin.getLogger().warning("Ошибка закрытия соединения LiteBans: " + e.getMessage());
            }
        }
    }
    
    /**
     * Получить тип БД
     */
    public String getDatabaseType() {
        if (useSameDatabase) {
            return plugin.getDatabaseManager().getDatabaseType();
        }
        return databaseType;
    }
}
