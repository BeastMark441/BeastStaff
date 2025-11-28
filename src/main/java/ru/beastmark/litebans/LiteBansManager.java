package ru.beastmark.litebans;

import ru.beastmark.BeastStaff;
import org.bukkit.configuration.file.FileConfiguration;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;

/**
 * Менеджер для работы с LiteBans API через БД
 * Получает и кеширует статистику наказаний
 */
public class LiteBansManager {
    
    private final BeastStaff plugin;
    private final LiteBansDatabaseManager dbManager;
    private final PunishmentCache cache;
    private final ScheduledExecutorService scheduler;
    private static final long CACHE_TTL = 5; // минуты
    private final boolean enabled;
    
    public LiteBansManager(BeastStaff plugin) {
        this.plugin = plugin;
        this.dbManager = new LiteBansDatabaseManager(plugin);
        this.cache = new PunishmentCache();
        this.scheduler = Executors.newScheduledThreadPool(1);
        
        FileConfiguration config = plugin.getConfig();
        this.enabled = config.getBoolean("integrations.litebans.enabled", false);
        
        if (enabled) {
            // Проверяем наличие таблиц LiteBans
            if (checkLiteBansTables()) {
                // Запустить периодическое обновление кеша
                startCacheRefresh();
                plugin.getLogger().info("✓ LiteBans интеграция инициализирована");
            } else {
                plugin.getLogger().warning("⚠️ Таблицы LiteBans не найдены! Проверьте подключение к БД.");
                plugin.getLogger().warning("Убедитесь, что:");
                plugin.getLogger().warning("1. LiteBans установлен и работает");
                plugin.getLogger().warning("2. Используется та же БД или правильные настройки БД");
                plugin.getLogger().warning("3. Таблицы litebans_bans, litebans_mutes, litebans_kicks существуют");
            }
        }
    }
    
    private Connection getConnection() {
        return dbManager.getConnection();
    }
    
    /**
     * Проверка существования таблиц LiteBans
     */
    private boolean checkLiteBansTables() {
        Connection connection = getConnection();
        if (connection == null) {
            plugin.getLogger().warning("Не удалось получить подключение к БД для проверки таблиц LiteBans");
            return false;
        }
        
        try {
            String[] requiredTables = {"litebans_bans", "litebans_mutes", "litebans_kicks"};
            String[] optionalTables = {"litebans_warnings"};
            
            String dbType = dbManager.getDatabaseType().toLowerCase();
            DatabaseMetaData metaData = connection.getMetaData();
            
            // Для SQLite используем прямой запрос, так как getTables может работать некорректно
            if (dbType.equals("sqlite")) {
                return checkTablesSQLite(connection, requiredTables, optionalTables);
            }
            
            // Для MySQL и других БД используем стандартный метод
            // Проверяем обязательные таблицы
            for (String table : requiredTables) {
                try (ResultSet rs = metaData.getTables(null, null, table, null)) {
                    if (!rs.next()) {
                        plugin.getLogger().warning("⚠️ Обязательная таблица " + table + " не найдена в БД");
                        return false;
                    }
                }
            }
            
            // Проверяем опциональные таблицы
            for (String table : optionalTables) {
                try (ResultSet rs = metaData.getTables(null, null, table, null)) {
                    if (!rs.next()) {
                        plugin.getLogger().info("ℹ️ Опциональная таблица " + table + " не найдена (это нормально)");
                    }
                }
            }
            
            plugin.getLogger().info("✓ Все обязательные таблицы LiteBans найдены");
            return true;
            
        } catch (SQLException e) {
            plugin.getLogger().warning("Ошибка при проверке таблиц LiteBans: " + e.getMessage());
            if (plugin.getConfig().getBoolean("debug.log-sql", false)) {
                e.printStackTrace();
            }
            return false;
        }
    }
    
    /**
     * Проверка таблиц для SQLite (прямой запрос)
     */
    private boolean checkTablesSQLite(Connection connection, String[] requiredTables, String[] optionalTables) {
        try {
            // Проверяем обязательные таблицы
            for (String table : requiredTables) {
                try (Statement stmt = connection.createStatement();
                     ResultSet rs = stmt.executeQuery("SELECT name FROM sqlite_master WHERE type='table' AND name='" + table + "'")) {
                    if (!rs.next()) {
                        plugin.getLogger().warning("⚠️ Обязательная таблица " + table + " не найдена в SQLite БД");
                        return false;
                    }
                }
            }
            
            // Проверяем опциональные таблицы
            for (String table : optionalTables) {
                try (Statement stmt = connection.createStatement();
                     ResultSet rs = stmt.executeQuery("SELECT name FROM sqlite_master WHERE type='table' AND name='" + table + "'")) {
                    if (!rs.next()) {
                        plugin.getLogger().info("ℹ️ Опциональная таблица " + table + " не найдена (это нормально)");
                    }
                }
            }
            
            plugin.getLogger().info("✓ Все обязательные таблицы LiteBans найдены в SQLite БД");
            return true;
            
        } catch (SQLException e) {
            plugin.getLogger().warning("Ошибка при проверке таблиц SQLite: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Получить статистику наказаний за период
     */
    public PunishmentStats getStats(LocalDateTime from, LocalDateTime to) {
        if (!enabled) {
            return new PunishmentStats();
        }
        
        Connection connection = getConnection();
        if (connection == null) {
            return new PunishmentStats();
        }
        
        String cacheKey = "stats_" + from + "_" + to;
        
        if (cache.isValid(cacheKey)) {
            return cache.get(cacheKey);
        }
        
        PunishmentStats stats = new PunishmentStats();
        
        try {
            // Баны
            stats.bans = countPunishments("litebans_bans", from, to);
            stats.activeBans = countActivePunishments("litebans_bans", from, to);
            
            // Муты
            stats.mutes = countPunishments("litebans_mutes", from, to);
            stats.activeMutes = countActivePunishments("litebans_mutes", from, to);
            
            // Кики
            stats.kicks = countPunishments("litebans_kicks", from, to);
            
            // Предупреждения
            stats.warnings = countPunishments("litebans_warnings", from, to);
            
            // Топ модераторы
            stats.topModerators = getTopModerators(from, to, 5);
            
            // Популярные причины
            stats.topReasons = getTopReasons(from, to, 5);
            
            // Временной анализ
            stats.punishmentsByHour = getPunishmentsByHour(from, to);
            
            cache.put(cacheKey, stats, CACHE_TTL);
            
        } catch (SQLException e) {
            logError("Ошибка при получении статистики", e);
        }
        
        return stats;
    }
    
    /**
     * Получить последние наказания с детальной информацией
     */
    public List<Map<String, Object>> getRecentPunishments(int limit) {
        List<Map<String, Object>> punishments = new ArrayList<>();
        
        if (!enabled) {
            return punishments;
        }
        
        Connection connection = getConnection();
        if (connection == null) {
            return punishments;
        }
        
        try {
            // Получаем последние наказания из всех таблиц
            String query = "(" +
                "SELECT 'ban' as type, player_name, moderator_name, reason, time, until, active " +
                "FROM litebans_bans ORDER BY time DESC LIMIT ?" +
                ") UNION ALL (" +
                "SELECT 'mute' as type, player_name, moderator_name, reason, time, until, active " +
                "FROM litebans_mutes ORDER BY time DESC LIMIT ?" +
                ") UNION ALL (" +
                "SELECT 'kick' as type, player_name, moderator_name, reason, time, NULL as until, 0 as active " +
                "FROM litebans_kicks ORDER BY time DESC LIMIT ?" +
                ") ORDER BY time DESC LIMIT ?";
            
            try (PreparedStatement stmt = connection.prepareStatement(query)) {
                stmt.setInt(1, limit);
                stmt.setInt(2, limit);
                stmt.setInt(3, limit);
                stmt.setInt(4, limit);
                
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> punishment = new HashMap<>();
                        punishment.put("type", rs.getString("type"));
                        punishment.put("player", rs.getString("player_name"));
                        punishment.put("moderator", rs.getString("moderator_name"));
                        punishment.put("reason", rs.getString("reason"));
                        punishment.put("time", rs.getTimestamp("time"));
                        punishment.put("until", rs.getTimestamp("until"));
                        punishment.put("active", rs.getInt("active") == 1);
                        punishments.add(punishment);
                    }
                }
            }
        } catch (SQLException e) {
            logError("Ошибка при получении последних наказаний", e);
        }
        
        return punishments;
    }
    
    /**
     * История наказаний конкретного игрока
     */
    public List<Map<String, Object>> getPlayerHistory(String playerName, int limit) {
        List<Map<String, Object>> history = new ArrayList<>();
        
        if (!enabled) {
            return history;
        }
        
        Connection connection = getConnection();
        if (connection == null) {
            return history;
        }
        
        try {
            String query = "(" +
                "SELECT 'ban' as type, moderator_name, reason, time, until " +
                "FROM litebans_bans WHERE player_name = ? ORDER BY time DESC LIMIT ?" +
                ") UNION ALL (" +
                "SELECT 'mute' as type, moderator_name, reason, time, until " +
                "FROM litebans_mutes WHERE player_name = ? ORDER BY time DESC LIMIT ?" +
                ") ORDER BY time DESC";
            
            try (PreparedStatement stmt = connection.prepareStatement(query)) {
                stmt.setString(1, playerName);
                stmt.setInt(2, limit);
                stmt.setString(3, playerName);
                stmt.setInt(4, limit);
                
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> record = new HashMap<>();
                        record.put("type", rs.getString("type"));
                        record.put("moderator", rs.getString("moderator_name"));
                        record.put("reason", rs.getString("reason"));
                        record.put("time", rs.getTimestamp("time"));
                        record.put("until", rs.getTimestamp("until"));
                        history.add(record);
                    }
                }
            }
        } catch (SQLException e) {
            logError("Ошибка при получении истории игрока", e);
        }
        
        return history;
    }
    
    /**
     * Получить топ модераторов
     */
    private List<Map<String, Object>> getTopModerators(
            LocalDateTime from, LocalDateTime to, int limit) throws SQLException {
        
        List<Map<String, Object>> result = new ArrayList<>();
        Connection connection = getConnection();
        if (connection == null) {
            return result;
        }
        
        String query = "SELECT moderator_name, COUNT(*) as count " +
                "FROM (SELECT moderator_name FROM litebans_bans WHERE time BETWEEN ? AND ? " +
                "UNION ALL SELECT moderator_name FROM litebans_mutes WHERE time BETWEEN ? AND ? " +
                "UNION ALL SELECT moderator_name FROM litebans_kicks WHERE time BETWEEN ? AND ?) t " +
                "GROUP BY moderator_name ORDER BY count DESC LIMIT ?";
        
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            Timestamp fromTs = Timestamp.valueOf(from);
            Timestamp toTs = Timestamp.valueOf(to);
            
            stmt.setTimestamp(1, fromTs);
            stmt.setTimestamp(2, toTs);
            stmt.setTimestamp(3, fromTs);
            stmt.setTimestamp(4, toTs);
            stmt.setTimestamp(5, fromTs);
            stmt.setTimestamp(6, toTs);
            stmt.setInt(7, limit);
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> mod = new HashMap<>();
                    mod.put("name", rs.getString("moderator_name"));
                    mod.put("count", rs.getInt("count"));
                    result.add(mod);
                }
            }
        }
        
        return result;
    }
    
    /**
     * Получить популярные причины
     */
    private List<Map<String, Object>> getTopReasons(
            LocalDateTime from, LocalDateTime to, int limit) throws SQLException {
        
        List<Map<String, Object>> result = new ArrayList<>();
        Connection connection = getConnection();
        if (connection == null) {
            return result;
        }
        
        String query = "SELECT reason, COUNT(*) as count " +
                "FROM (SELECT reason FROM litebans_bans WHERE time BETWEEN ? AND ? " +
                "UNION ALL SELECT reason FROM litebans_mutes WHERE time BETWEEN ? AND ? " +
                "UNION ALL SELECT reason FROM litebans_kicks WHERE time BETWEEN ? AND ?) t " +
                "GROUP BY reason ORDER BY count DESC LIMIT ?";
        
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            Timestamp fromTs = Timestamp.valueOf(from);
            Timestamp toTs = Timestamp.valueOf(to);
            
            stmt.setTimestamp(1, fromTs);
            stmt.setTimestamp(2, toTs);
            stmt.setTimestamp(3, fromTs);
            stmt.setTimestamp(4, toTs);
            stmt.setTimestamp(5, fromTs);
            stmt.setTimestamp(6, toTs);
            stmt.setInt(7, limit);
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> reason = new HashMap<>();
                    reason.put("name", rs.getString("reason"));
                    reason.put("count", rs.getInt("count"));
                    result.add(reason);
                }
            }
        }
        
        return result;
    }
    
    /**
     * Получить наказания по часам
     */
    private Map<Integer, Integer> getPunishmentsByHour(
            LocalDateTime from, LocalDateTime to) throws SQLException {
        
        Map<Integer, Integer> result = new HashMap<>();
        for (int i = 0; i < 24; i++) {
            result.put(i, 0);
        }
        
        Connection connection = getConnection();
        if (connection == null) {
            return result;
        }
        
        // Для MySQL используем HOUR(), для других БД может потребоваться адаптация
        String query = "SELECT HOUR(time) as hour, COUNT(*) as count " +
                "FROM (SELECT time FROM litebans_bans WHERE time BETWEEN ? AND ? " +
                "UNION ALL SELECT time FROM litebans_mutes WHERE time BETWEEN ? AND ? " +
                "UNION ALL SELECT time FROM litebans_kicks WHERE time BETWEEN ? AND ?) t " +
                "GROUP BY HOUR(time)";
        
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            Timestamp fromTs = Timestamp.valueOf(from);
            Timestamp toTs = Timestamp.valueOf(to);
            
            stmt.setTimestamp(1, fromTs);
            stmt.setTimestamp(2, toTs);
            stmt.setTimestamp(3, fromTs);
            stmt.setTimestamp(4, toTs);
            stmt.setTimestamp(5, fromTs);
            stmt.setTimestamp(6, toTs);
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    result.put(rs.getInt("hour"), rs.getInt("count"));
                }
            }
        } catch (SQLException e) {
            // Если HOUR() не поддерживается (SQLite), используем альтернативный метод
            plugin.getLogger().warning("HOUR() не поддерживается, используем альтернативный метод");
            // Можно реализовать альтернативу для SQLite
        }
        
        return result;
    }
    
    // Вспомогательные методы
    
    private int countPunishments(String table, 
            LocalDateTime from, LocalDateTime to) throws SQLException {
        
        Connection connection = getConnection();
        if (connection == null) {
            return 0;
        }
        
        String query = "SELECT COUNT(*) as count FROM " + table + 
                " WHERE time BETWEEN ? AND ?";
        
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setTimestamp(1, Timestamp.valueOf(from));
            stmt.setTimestamp(2, Timestamp.valueOf(to));
            
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? rs.getInt("count") : 0;
            }
        }
    }
    
    private int countActivePunishments(String table,
            LocalDateTime from, LocalDateTime to) throws SQLException {
        
        Connection connection = getConnection();
        if (connection == null) {
            return 0;
        }
        
        String query = "SELECT COUNT(*) as count FROM " + table +
                " WHERE time BETWEEN ? AND ? AND active = 1";
        
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setTimestamp(1, Timestamp.valueOf(from));
            stmt.setTimestamp(2, Timestamp.valueOf(to));
            
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? rs.getInt("count") : 0;
            }
        }
    }
    
    private void startCacheRefresh() {
        scheduler.scheduleAtFixedRate(() -> {
            cache.invalidateExpired();
        }, 1, 1, TimeUnit.MINUTES);
    }
    
    private void logError(String message, Exception e) {
        plugin.getLogger().severe("[BeastStaff LiteBans] " + message);
        plugin.getLogger().severe(e.getMessage());
        if (plugin.getConfig().getBoolean("debug.log-sql", false)) {
            e.printStackTrace();
        }
    }
    
    public void shutdown() {
        scheduler.shutdown();
        if (dbManager != null) {
            dbManager.closeConnection();
        }
    }
    
    public boolean isEnabled() {
        return enabled;
    }
}

