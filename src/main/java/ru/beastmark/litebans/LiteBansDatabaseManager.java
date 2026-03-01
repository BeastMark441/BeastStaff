package ru.beastmark.litebans;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
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
    private HikariDataSource dataSource;
    private boolean useSameDatabase;
    private String databaseType;
    
    public LiteBansDatabaseManager(BeastStaff plugin) {
        this.plugin = plugin;
        FileConfiguration config = plugin.getConfig();
        this.useSameDatabase = config.getBoolean("integrations.litebans.use-same-database", true);
        
        if (!useSameDatabase) {
            // Определяем тип БД из конфига
            this.databaseType = config.getString("integrations.litebans.database.type", "mysql").toLowerCase();
            initializeDataSource();
        }
    }
    
    private void initializeDataSource() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
        
        try {
            if (databaseType.equals("sqlite")) {
                initializeSQLite();
            } else {
                initializeMySQL();
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Ошибка инициализации БД LiteBans: " + e.getMessage());
        }
    }
    
    private void initializeMySQL() {
        FileConfiguration config = plugin.getConfig();
        String host = config.getString("integrations.litebans.database.host", "localhost");
        int port = config.getInt("integrations.litebans.database.port", 3306);
        String database = config.getString("integrations.litebans.database.database", "litebans");
        String username = config.getString("integrations.litebans.database.username", "");
        String password = config.getString("integrations.litebans.database.password", "");
        
        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + database);
        hikariConfig.setUsername(username);
        hikariConfig.setPassword(password);
        hikariConfig.setPoolName("BeastStaff-LiteBans-MySQL-Pool");
        
        // Оптимизации
        hikariConfig.addDataSourceProperty("cachePrepStmts", "true");
        hikariConfig.addDataSourceProperty("prepStmtCacheSize", "250");
        hikariConfig.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        
        dataSource = new HikariDataSource(hikariConfig);
        plugin.getLogger().info("✓ Подключение к MySQL БД LiteBans установлено (HikariCP)");
    }
    
    private void initializeSQLite() throws SQLException {
        FileConfiguration config = plugin.getConfig();
        String dbPath = config.getString("integrations.litebans.database.path", "");
        
        if (dbPath.isEmpty()) {
            // Поиск БД (как в оригинальном коде)
            File pluginsFolder = plugin.getDataFolder().getParentFile();
            File litebansFolder = new File(pluginsFolder, "LiteBans");
            File[] possiblePaths = {
                new File(litebansFolder, "litebans.db"),
                new File(litebansFolder, "database.db"),
                new File(pluginsFolder.getParentFile(), "litebans.db"),
                new File(plugin.getDataFolder(), "litebans.db")
            };
            
            for (File dbFile : possiblePaths) {
                if (dbFile.exists() && dbFile.isFile()) {
                    dbPath = dbFile.getAbsolutePath();
                    break;
                }
            }
            
            if (dbPath.isEmpty()) {
                throw new SQLException("SQLite БД LiteBans не найдена!");
            }
        }
        
        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl("jdbc:sqlite:" + dbPath);
        hikariConfig.setPoolName("BeastStaff-LiteBans-SQLite-Pool");
        hikariConfig.setMaximumPoolSize(1);
        
        dataSource = new HikariDataSource(hikariConfig);
        plugin.getLogger().info("✓ Подключение к SQLite БД LiteBans установлено (HikariCP): " + dbPath);
    }

    /**
     * Получить подключение к БД LiteBans
     */
    public Connection getConnection() throws SQLException {
        if (useSameDatabase) {
            return plugin.getDatabaseManager().getConnection();
        } else {
            if (dataSource == null) {
                throw new SQLException("БД LiteBans не инициализирована!");
            }
            return dataSource.getConnection();
        }
    }
    
    /**
     * Проверить, подключены ли к БД
     */
    public boolean isConnected() {
        if (useSameDatabase) {
            return plugin.getDatabaseManager().isConnected();
        }
        return dataSource != null && !dataSource.isClosed();
    }
    
    /**
     * Закрыть подключение
     */
    public void closeConnection() {
        if (!useSameDatabase && dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            plugin.getLogger().info("Подключение к БД LiteBans закрыто");
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
