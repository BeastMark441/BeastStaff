package ru.beastmark.managers;

import ru.beastmark.BeastStaff;
import org.bukkit.configuration.file.FileConfiguration;

import java.io.File;
import java.sql.*;
import java.util.logging.Level;

public class DatabaseManager {
    
    private final BeastStaff plugin;
    private Connection connection;
    private String databaseType;
    
    public DatabaseManager(BeastStaff plugin) {
        this.plugin = plugin;
        loadDatabaseType();
        initializeDatabase();
    }
    
    private void initializeDatabase() {
        plugin.getLogger().info("Инициализация базы данных типа: " + databaseType);
        
        switch (databaseType.toLowerCase()) {
            case "mysql":
            case "mariadb":
                initializeMySQL();
                break;
            case "sqlite":
                initializeSQLite();
                break;
            case "h2":
                initializeH2();
                break;
            case "file":
            default:
                plugin.getLogger().info("Используется файловое хранение (YAML)");
                break;
        }
    }
    
    private void initializeMySQL() {
        try {
            FileConfiguration config = plugin.getConfig();
            String host = config.getString("database.mysql.host", "localhost");
            int port = config.getInt("database.mysql.port", 3306);
            String database = config.getString("database.mysql.database", "beaststaff");
            String username = config.getString("database.mysql.username", "root");
            String password = config.getString("database.mysql.password", "password");
            
            String url = "jdbc:mysql://" + host + ":" + port + "/" + database + 
                        "?useSSL=false&autoReconnect=true&useUnicode=true&characterEncoding=UTF-8";
            
            connection = DriverManager.getConnection(url, username, password);
            createTables();
            plugin.getLogger().info("MySQL соединение установлено!");
            
        } catch (SQLException e) {
            plugin.getLogger().severe("Ошибка подключения к MySQL: " + e.getMessage());
        }
    }
    
    private void initializeSQLite() {
        try {
            File dataFolder = plugin.getDataFolder();
            if (!dataFolder.exists()) {
                dataFolder.mkdirs();
            }
            
            String url = "jdbc:sqlite:" + new File(dataFolder, "beaststaff.db");
            connection = DriverManager.getConnection(url);
            createTables();
            plugin.getLogger().info("SQLite соединение установлено!");
            
        } catch (SQLException e) {
            plugin.getLogger().severe("Ошибка подключения к SQLite: " + e.getMessage());
        }
    }
    
    private void initializeH2() {
        try {
            File dataFolder = plugin.getDataFolder();
            if (!dataFolder.exists()) {
                dataFolder.mkdirs();
            }
            
            String url = "jdbc:h2:" + new File(dataFolder, "beaststaff").getAbsolutePath();
            connection = DriverManager.getConnection(url);
            createTables();
            plugin.getLogger().info("H2 соединение установлено!");
            
        } catch (SQLException e) {
            plugin.getLogger().severe("Ошибка подключения к H2: " + e.getMessage());
        }
    }
    
    private void createTables() {
        if (connection == null) return;
        
        try {
            String createTimeTrackingTable = "CREATE TABLE IF NOT EXISTS time_tracking (" +
                    "id INTEGER PRIMARY KEY AUTO_INCREMENT," +
                    "player_uuid VARCHAR(36) NOT NULL," +
                    "player_name VARCHAR(16) NOT NULL," +
                    "status VARCHAR(50) NOT NULL," +
                    "start_time BIGINT NOT NULL," +
                    "end_time BIGINT," +
                    "duration BIGINT," +
                    "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                    ")";
            
            try (Statement stmt = connection.createStatement()) {
                stmt.execute(createTimeTrackingTable);
            }
            
        } catch (SQLException e) {
            plugin.getLogger().severe("Ошибка создания таблиц: " + e.getMessage());
        }
    }
    
    public Connection getConnection() {
        return connection;
    }
    
    public boolean isConnected() {
        return connection != null;
    }
    
    public String getDatabaseType() {
        return databaseType;
    }
    
    public void closeConnection() {
        if (connection != null) {
            try {
                connection.close();
                plugin.getLogger().info("Соединение с базой данных закрыто!");
            } catch (SQLException e) {
                plugin.getLogger().severe("Ошибка закрытия соединения: " + e.getMessage());
            }
        }
    }
    
    private void loadDatabaseType() {
        this.databaseType = plugin.getConfig().getString("database.type", "file");
    }
    
    public void reload() {
        plugin.getLogger().info("Перезагрузка базы данных...");
        
        // Закрываем текущее соединение
        closeConnection();
        
        // Перезагружаем конфигурацию
        plugin.reloadConfig();
        
        // Загружаем новый тип базы данных
        loadDatabaseType();
        
        // Инициализируем новое соединение
        initializeDatabase();
        
        plugin.getLogger().info("База данных перезагружена! Тип: " + databaseType);
    }
    
    public void executeQuery(String query) {
        if (connection == null) return;
        
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(query);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Ошибка выполнения запроса: " + query, e);
        }
    }
    
    public ResultSet executeQueryWithResult(String query) {
        if (connection == null) return null;
        
        try {
            Statement stmt = connection.createStatement();
            return stmt.executeQuery(query);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Ошибка выполнения запроса: " + query, e);
            return null;
        }
    }
}
