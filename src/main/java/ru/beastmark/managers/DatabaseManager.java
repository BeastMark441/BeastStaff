package ru.beastmark.managers;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import ru.beastmark.BeastStaff;
import org.bukkit.configuration.file.FileConfiguration;

import java.io.File;
import java.sql.*;
import java.util.logging.Level;

public class DatabaseManager {
    
    private final BeastStaff plugin;
    private HikariDataSource dataSource;
    private String databaseType;
    
    public DatabaseManager(BeastStaff plugin) {
        this.plugin = plugin;
        loadDatabaseType();
        initializeDatabase();
    }
    
    private void initializeDatabase() {
        plugin.getLogger().info("Инициализация базы данных типа: " + databaseType);
        
        // Закрываем старый пул если есть
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }

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
        FileConfiguration config = plugin.getConfig();
        String host = config.getString("database.mysql.host", "localhost");
        int port = config.getInt("database.mysql.port", 3306);
        String database = config.getString("database.mysql.database", "beaststaff");
        String username = config.getString("database.mysql.username", "root");
        String password = config.getString("database.mysql.password", "password");
        
        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + database);
        hikariConfig.setUsername(username);
        hikariConfig.setPassword(password);
        hikariConfig.addDataSourceProperty("cachePrepStmts", "true");
        hikariConfig.addDataSourceProperty("prepStmtCacheSize", "250");
        hikariConfig.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        hikariConfig.addDataSourceProperty("useServerPrepStmts", "true");
        hikariConfig.addDataSourceProperty("useLocalSessionState", "true");
        hikariConfig.addDataSourceProperty("rewriteBatchedStatements", "true");
        hikariConfig.addDataSourceProperty("cacheResultSetMetadata", "true");
        hikariConfig.addDataSourceProperty("cacheServerConfiguration", "true");
        hikariConfig.addDataSourceProperty("elideSetAutoCommits", "true");
        hikariConfig.addDataSourceProperty("maintainTimeStats", "false");
        
        // SSL и кодировка
        hikariConfig.addDataSourceProperty("useSSL", "false");
        hikariConfig.addDataSourceProperty("requireSSL", "false");
        hikariConfig.addDataSourceProperty("characterEncoding", "UTF-8");
        hikariConfig.addDataSourceProperty("useUnicode", "true");
        
        // Настройки пула
        hikariConfig.setMaximumPoolSize(10);
        hikariConfig.setMinimumIdle(2);
        hikariConfig.setIdleTimeout(30000);
        hikariConfig.setMaxLifetime(1800000);
        hikariConfig.setConnectionTimeout(10000);
        hikariConfig.setPoolName("BeastStaff-MySQL-Pool");
        
        try {
            dataSource = new HikariDataSource(hikariConfig);
            createTables();
            plugin.getLogger().info("MySQL соединение установлено!");
        } catch (Exception e) {
            plugin.getLogger().severe("Ошибка подключения к MySQL: " + e.getMessage());
        }
    }
    
    private void initializeSQLite() {
        File dataFolder = plugin.getDataFolder();
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }
        
        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl("jdbc:sqlite:" + new File(dataFolder, "beaststaff.db").getAbsolutePath());
        hikariConfig.setPoolName("BeastStaff-SQLite-Pool");
        hikariConfig.setMaximumPoolSize(1); // SQLite должен быть однопоточным для записи
        
        try {
            dataSource = new HikariDataSource(hikariConfig);
            createTables();
            plugin.getLogger().info("SQLite соединение установлено!");
        } catch (Exception e) {
            plugin.getLogger().severe("Ошибка подключения к SQLite: " + e.getMessage());
        }
    }
    
    private void initializeH2() {
        File dataFolder = plugin.getDataFolder();
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }
        
        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl("jdbc:h2:" + new File(dataFolder, "beaststaff").getAbsolutePath());
        hikariConfig.setPoolName("BeastStaff-H2-Pool");
        
        try {
            dataSource = new HikariDataSource(hikariConfig);
            createTables();
            plugin.getLogger().info("H2 соединение установлено !");
        } catch (Exception e) {
            plugin.getLogger().severe("Ошибка подключения к H2: " + e.getMessage());
        }
    }
    
    private void createTables() {
        if (dataSource == null) return;
        
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            
            String createTimeTrackingTable = "CREATE TABLE IF NOT EXISTS time_tracking (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "player_uuid VARCHAR(36) NOT NULL," +
                    "player_name VARCHAR(16) NOT NULL," +
                    "status VARCHAR(50) NOT NULL," +
                    "start_time BIGINT NOT NULL," +
                    "end_time BIGINT," +
                    "duration BIGINT," +
                    "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                    ")";
            
            // Для MySQL используем специфичный синтаксис
            if (databaseType.equalsIgnoreCase("mysql")) {
                 createTimeTrackingTable = "CREATE TABLE IF NOT EXISTS time_tracking (" +
                    "id INT PRIMARY KEY AUTO_INCREMENT," +
                    "player_uuid VARCHAR(36) NOT NULL," +
                    "player_name VARCHAR(16) NOT NULL," +
                    "status VARCHAR(50) NOT NULL," +
                    "start_time BIGINT NOT NULL," +
                    "end_time BIGINT," +
                    "duration BIGINT," +
                    "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                    ") CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci";
            } else if (databaseType.equalsIgnoreCase("h2")) {
                createTimeTrackingTable = "CREATE TABLE IF NOT EXISTS time_tracking (" +
                        "id INT AUTO_INCREMENT PRIMARY KEY," +
                        "player_uuid VARCHAR(36) NOT NULL," +
                        "player_name VARCHAR(16) NOT NULL," +
                        "status VARCHAR(50) NOT NULL," +
                        "start_time BIGINT NOT NULL," +
                        "end_time BIGINT," +
                        "duration BIGINT," +
                        "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                        ")";
            } else if (databaseType.equalsIgnoreCase("sqlite")) {
                createTimeTrackingTable = "CREATE TABLE IF NOT EXISTS time_tracking (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                        "player_uuid TEXT NOT NULL," +
                        "player_name TEXT NOT NULL," +
                        "status TEXT NOT NULL," +
                        "start_time INTEGER NOT NULL," +
                        "end_time INTEGER," +
                        "duration INTEGER," +
                        "created_at INTEGER DEFAULT (strftime('%s','now'))" +
                        ")";
            }
            
            stmt.execute(createTimeTrackingTable);
            
            // Create staff_members table
            String createStaffTable = "CREATE TABLE IF NOT EXISTS staff_members (" +
                    "uuid VARCHAR(36) PRIMARY KEY," +
                    "name VARCHAR(16) NOT NULL," +
                    "rank VARCHAR(50) NOT NULL," +
                    "join_date BIGINT NOT NULL" +
                    ")";
            
            if (databaseType.equalsIgnoreCase("mysql")) {
                 createStaffTable = "CREATE TABLE IF NOT EXISTS staff_members (" +
                    "uuid VARCHAR(36) PRIMARY KEY," +
                    "name VARCHAR(16) NOT NULL," +
                    "rank VARCHAR(50) NOT NULL," +
                    "join_date BIGINT NOT NULL" +
                    ") CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci";
            }
            stmt.execute(createStaffTable);

            // Create telegram_bindings table
            String createBindingsTable = "CREATE TABLE IF NOT EXISTS telegram_bindings (" +
                    "player_uuid VARCHAR(36) PRIMARY KEY," +
                    "telegram_id VARCHAR(50) NOT NULL," +
                    "UNIQUE(telegram_id)" +
                    ")";

            if (databaseType.equalsIgnoreCase("mysql")) {
                 createBindingsTable = "CREATE TABLE IF NOT EXISTS telegram_bindings (" +
                    "player_uuid VARCHAR(36) PRIMARY KEY," +
                    "telegram_id VARCHAR(50) NOT NULL," +
                    "UNIQUE(telegram_id)" +
                    ") CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci";
            }
            stmt.execute(createBindingsTable);
            
        } catch (SQLException e) {
            plugin.getLogger().severe("Ошибка создания таблиц: " + e.getMessage());
        }
    }
    
    public Connection getConnection() throws SQLException {
        if (dataSource == null) {
            throw new SQLException("База данных не инициализирована!");
        }
        return dataSource.getConnection();
    }
    
    public HikariDataSource getDataSource() {
        return dataSource;
    }
    
    public boolean isConnected() {
        return dataSource != null && !dataSource.isClosed();
    }
    
    public String getDatabaseType() {
        return databaseType;
    }
    
    public void closeConnection() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            plugin.getLogger().info("Пул соединений с базой данных закрыт!");
        }
    }
    
    private void loadDatabaseType() {
        this.databaseType = plugin.getConfig().getString("database.type", "file");
    }
    
    public void reload() {
        plugin.getLogger().info("Перезагрузка базы данных...");
        
        closeConnection();
        plugin.reloadConfig();
        loadDatabaseType();
        initializeDatabase();
        
        plugin.getLogger().info("База данных перезагружена! Тип: " + databaseType);
    }
    
    public void executeQuery(String query) {
        if (dataSource == null) return;
        
        // ВНИМАНИЕ: Это асинхронный вызов, мы не ждем результата
        // Для SELECT запросов используйте executeQueryWithResult или getConnection() напрямую
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection conn = dataSource.getConnection();
                 Statement stmt = conn.createStatement()) {
                stmt.execute(query);
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Ошибка выполнения запроса: " + query, e);
            }
        });
    }
}
