package ru.beastmark.managers;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import ru.beastmark.BeastStaff;
import ru.beastmark.api.events.StaffWorkEndEvent;
import ru.beastmark.api.events.StaffWorkStartEvent;

import java.io.File;
import java.io.IOException;
import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.Date;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class TimeTrackingManager {
    
    private final BeastStaff plugin;
    private final Map<UUID, WorkSession> activeSessions;
    private final Map<UUID, String> playerStatuses;
    private final List<String> availableStatuses;
    private final File timeTrackingFile;
    private FileConfiguration timeTrackingConfig;
    private final Object timeTrackingFileLock = new Object();
    private BukkitTask pendingTimeTrackingSaveTask;
    private final Map<UUID, Map<Integer, Long>> cachedTotalsByDays = new ConcurrentHashMap<>();
    private BukkitTask placeholderCacheTask;
    
    public TimeTrackingManager(BeastStaff plugin) {
        this.plugin = plugin;
        this.activeSessions = new ConcurrentHashMap<>();
        this.playerStatuses = new ConcurrentHashMap<>();
        this.availableStatuses = new ArrayList<>();
        this.timeTrackingFile = new File(plugin.getDataFolder(), "time_tracking.yml");
        
        loadAvailableStatuses();
        loadTimeTrackingData();
    }
    
    private void loadAvailableStatuses() {
        availableStatuses.clear();
        availableStatuses.addAll(plugin.getConfig().getStringList("time-tracking.statuses"));
        
        // Добавляем стандартные статусы, если их нет
        if (availableStatuses.isEmpty()) {
            availableStatuses.add("В работе");
            availableStatuses.add("AFK");
            availableStatuses.add("Не в работе");
        }
    }
    
    private void loadTimeTrackingData() {
        if (!timeTrackingFile.exists()) {
            plugin.saveResource("time_tracking.yml", false);
        }
        
        timeTrackingConfig = YamlConfiguration.loadConfiguration(timeTrackingFile);
    }

    private void scheduleTimeTrackingFileSave() {
        long delayTicks = plugin.getConfig().getLong("time-tracking.file-save-delay-ticks", 40L);
        synchronized (timeTrackingFileLock) {
            if (pendingTimeTrackingSaveTask != null) {
                return;
            }
            pendingTimeTrackingSaveTask = plugin.getServer().getScheduler().runTaskLaterAsynchronously(plugin, () -> {
                synchronized (timeTrackingFileLock) {
                    pendingTimeTrackingSaveTask = null;
                    try {
                        timeTrackingConfig.save(timeTrackingFile);
                    } catch (IOException e) {
                        plugin.getLogger().log(Level.SEVERE, "Ошибка сохранения сессии в файл", e);
                    }
                }
            }, delayTicks);
        }
    }

    public void flushTimeTrackingFileSaveSync() {
        synchronized (timeTrackingFileLock) {
            if (pendingTimeTrackingSaveTask != null) {
                pendingTimeTrackingSaveTask.cancel();
                pendingTimeTrackingSaveTask = null;
            }
            try {
                timeTrackingConfig.save(timeTrackingFile);
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Ошибка сохранения сессии в файл", e);
            }
        }
    }
    
    public void startSession(Player player, String status) {
        if (!plugin.getStaffManager().isStaffMember(player.getUniqueId())) {
            return;
        }
        
        UUID playerUUID = player.getUniqueId();
        String currentStatus = playerStatuses.get(playerUUID);
        boolean wasWorking = currentStatus != null && !currentStatus.equals("Не в работе");
        boolean willBeWorking = status != null && !status.equals("Не в работе");
        
        // Если статус не изменился, не создаём новую сессию
        if (status.equals(currentStatus)) {
            // Обновляем только время последней активности для активной сессии
            WorkSession activeSession = activeSessions.get(playerUUID);
            if (activeSession != null && activeSession.getStatus().equals(status)) {
                // Сессия уже активна с этим статусом, ничего не делаем
                return;
            }
        }
        
        // Завершаем предыдущую сессию, если есть и статус изменился
        if (currentStatus != null && !currentStatus.equals(status)) {
            endSession(playerUUID, status);
        } else if (currentStatus == null) {
            // Первая сессия для игрока
            endSession(playerUUID);
        }
        
        // Создаём новую сессию только если статус изменился или это первая сессия
        WorkSession session = new WorkSession(
                playerUUID,
                player.getName(),
                status,
                System.currentTimeMillis()
        );
        
        activeSessions.put(playerUUID, session);
        playerStatuses.put(playerUUID, status);
        
        saveSessionToDatabase(session);
        refreshPlayerPlaceholderCache(playerUUID);

        if (willBeWorking && !wasWorking) {
            plugin.getServer().getPluginManager().callEvent(new StaffWorkStartEvent(playerUUID, player.getName(), currentStatus, status, session.getStartTime()));
        }
        
        // Отправляем сообщение только если статус действительно изменился
        if (currentStatus == null || !currentStatus.equals(status)) {
            player.sendMessage(plugin.getMessageManager().getMessage("status-changed", "status", status));
            
            // Отправляем уведомление в Telegram
            if (plugin.getTelegramIntegration() != null) {
                plugin.getTelegramIntegration().notifyStatusChange(player, currentStatus, status);
            }
        }
    }
    
    public void endSession(UUID playerUUID) {
        WorkSession session = activeSessions.get(playerUUID);
        if (session != null) {
            String oldStatus = session.getStatus();
            session.setEndTime(System.currentTimeMillis());
            session.calculateDuration();
            
            updateSessionInDatabase(session);
            activeSessions.remove(playerUUID);
            playerStatuses.remove(playerUUID);
            refreshPlayerPlaceholderCache(playerUUID);

            if (oldStatus != null && !oldStatus.equals("Не в работе")) {
                plugin.getServer().getPluginManager().callEvent(new StaffWorkEndEvent(playerUUID, session.getPlayerName(), oldStatus, null, session.getStartTime(), session.getEndTime(), session.getDuration()));
            }
        }
    }
    
    public void endSession(UUID playerUUID, String newStatus) {
        WorkSession session = activeSessions.get(playerUUID);
        if (session != null) {
            String oldStatus = session.getStatus();
            session.setEndTime(System.currentTimeMillis());
            session.calculateDuration();
            
            updateSessionInDatabase(session);
            activeSessions.remove(playerUUID);
            
            if (newStatus != null) {
                playerStatuses.put(playerUUID, newStatus);
            } else {
                playerStatuses.remove(playerUUID);
            }
            refreshPlayerPlaceholderCache(playerUUID);

            if (oldStatus != null && !oldStatus.equals("Не в работе")) {
                boolean nowNotWorking = newStatus == null || newStatus.equals("Не в работе");
                if (nowNotWorking) {
                    plugin.getServer().getPluginManager().callEvent(new StaffWorkEndEvent(playerUUID, session.getPlayerName(), oldStatus, newStatus, session.getStartTime(), session.getEndTime(), session.getDuration()));
                }
            }
        }
    }
    
    public String getPlayerStatus(UUID playerUUID) {
        return playerStatuses.get(playerUUID);
    }
    
    public boolean isPlayerWorking(UUID playerUUID) {
        String status = getPlayerStatus(playerUUID);
        return status != null && !status.equals("Не в работе");
    }
    
    public List<String> getAvailableStatuses() {
        return new ArrayList<>(availableStatuses);
    }
    
    public void reloadAvailableStatuses() {
        loadAvailableStatuses();
    }
    
    public WorkSession getActiveSession(UUID playerUUID) {
        return activeSessions.get(playerUUID);
    }

    public void startPlaceholderCache() {
        long refreshTicks = plugin.getConfig().getLong("placeholders.cache-refresh-ticks", 20L * 60L);
        if (refreshTicks <= 0) {
            return;
        }
        if (placeholderCacheTask != null) {
            placeholderCacheTask.cancel();
        }
        placeholderCacheTask = plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            for (Player player : plugin.getServer().getOnlinePlayers()) {
                if (plugin.getStaffManager().isStaffMember(player.getUniqueId())) {
                    refreshPlayerPlaceholderCache(player.getUniqueId());
                }
            }
        }, 1L, refreshTicks);
    }

    public void stopPlaceholderCache() {
        if (placeholderCacheTask != null) {
            placeholderCacheTask.cancel();
            placeholderCacheTask = null;
        }
        cachedTotalsByDays.clear();
    }

    private void refreshPlayerPlaceholderCache(UUID playerUUID) {
        if (!plugin.getServer().isPrimaryThread()) {
            refreshPlayerPlaceholderCacheAsync(playerUUID);
            return;
        }
        refreshPlayerPlaceholderCacheAsync(playerUUID);
    }

    private void refreshPlayerPlaceholderCacheAsync(UUID playerUUID) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            int[] daysList = new int[] {1, 7, 30, 365};
            Map<Integer, Long> totals = new HashMap<>();
            for (int days : daysList) {
                totals.put(days, calculateTotalWorkTimeMillis(playerUUID, days));
            }
            cachedTotalsByDays.put(playerUUID, totals);
        });
    }

    public long getCachedTotalWorkTimeMillis(UUID playerUUID, int days) {
        Map<Integer, Long> totals = cachedTotalsByDays.get(playerUUID);
        if (totals == null) {
            return -1L;
        }
        Long value = totals.get(days);
        return value != null ? value : -1L;
    }

    private long calculateTotalWorkTimeMillis(UUID playerUUID, int days) {
        long startTime = System.currentTimeMillis() - (days * 24L * 60L * 60L * 1000L);
        String notWorkingStatus = "Не в работе";
        if (plugin.getDatabaseManager().isConnected()) {
            String query = "SELECT COALESCE(SUM(duration), 0) AS total FROM time_tracking WHERE player_uuid = ? AND start_time >= ? AND status <> ?";
            try (Connection conn = plugin.getDatabaseManager().getConnection();
                 PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.setString(1, playerUUID.toString());
                stmt.setLong(2, startTime);
                stmt.setString(3, notWorkingStatus);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getLong("total");
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Ошибка получения статистики времени из БД", e);
            }
            return 0L;
        }

        long total = 0L;
        String path = "sessions." + playerUUID.toString();
        synchronized (timeTrackingFileLock) {
            if (timeTrackingConfig.contains(path)) {
                for (String sessionId : timeTrackingConfig.getConfigurationSection(path).getKeys(false)) {
                    String sessionPath = path + "." + sessionId;
                    long sessionStart = timeTrackingConfig.getLong(sessionPath + ".start_time");
                    if (sessionStart < startTime) {
                        continue;
                    }
                    String status = timeTrackingConfig.getString(sessionPath + ".status", "");
                    if (notWorkingStatus.equals(status)) {
                        continue;
                    }
                    long duration = timeTrackingConfig.getLong(sessionPath + ".duration");
                    total += duration;
                }
            }
        }
        return total;
    }
    
    public List<WorkSession> getPlayerSessions(UUID playerUUID, int days) {
        List<WorkSession> sessions = new ArrayList<>();
        
        if (plugin.getDatabaseManager().isConnected()) {
            plugin.getLogger().info("Получение сессий из базы данных: " + plugin.getDatabaseManager().getDatabaseType());
            // Получаем из базы данных
            long startTime = System.currentTimeMillis() - (days * 24 * 60 * 60 * 1000L);
            
            String query = "SELECT * FROM time_tracking WHERE player_uuid = ? AND start_time >= ? ORDER BY start_time DESC";
            
            try (Connection conn = plugin.getDatabaseManager().getConnection();
                 PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.setString(1, playerUUID.toString());
                stmt.setLong(2, startTime);
                
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        WorkSession session = new WorkSession(
                                UUID.fromString(rs.getString("player_uuid")),
                                rs.getString("player_name"),
                                rs.getString("status"),
                                rs.getLong("start_time")
                        );
                        session.setEndTime(rs.getLong("end_time"));
                        session.setDuration(rs.getLong("duration"));
                        sessions.add(session);
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Ошибка получения сессий игрока", e);
            }
        } else {
            plugin.getLogger().info("Получение сессий из файла (YAML) - база данных не подключена");
            // Получаем из файла
            String path = "sessions." + playerUUID.toString();
            synchronized (timeTrackingFileLock) {
                if (timeTrackingConfig.contains(path)) {
                    for (String sessionId : timeTrackingConfig.getConfigurationSection(path).getKeys(false)) {
                        String sessionPath = path + "." + sessionId;
                        WorkSession session = new WorkSession(
                                playerUUID,
                                timeTrackingConfig.getString(sessionPath + ".player_name"),
                                timeTrackingConfig.getString(sessionPath + ".status"),
                                timeTrackingConfig.getLong(sessionPath + ".start_time")
                        );
                        session.setEndTime(timeTrackingConfig.getLong(sessionPath + ".end_time"));
                        session.setDuration(timeTrackingConfig.getLong(sessionPath + ".duration"));
                        sessions.add(session);
                    }
                }
            }
        }
        
        return sessions;
    }
    
    public Map<String, Long> getPlayerStats(UUID playerUUID, int days) {
        Map<String, Long> stats = new HashMap<>();
        List<WorkSession> sessions = getPlayerSessions(playerUUID, days);
        
        long totalTime = 0;
        Map<String, Long> statusTime = new HashMap<>();
        
        for (WorkSession session : sessions) {
            long duration = session.getDuration();
            String status = session.getStatus();
            
            // Исключаем время "Не в работе" из общей статистики
            if (!status.equals("Не в работе")) {
                totalTime += duration;
            }
            
            statusTime.put(status, statusTime.getOrDefault(status, 0L) + duration);
        }
        
        stats.put("total", totalTime);
        stats.putAll(statusTime);
        
        return stats;
    }
    
    public Map<String, Long> getAllStaffStats(int days) {
        Map<String, Long> totalStats = new HashMap<>();
        
        for (StaffManager.StaffMember member : plugin.getStaffManager().getAllStaffMembers().values()) {
            Map<String, Long> playerStats = getPlayerStats(member.getUuid(), days);
            
            for (Map.Entry<String, Long> entry : playerStats.entrySet()) {
                String key = entry.getKey();
                Long value = entry.getValue();
                totalStats.put(key, totalStats.getOrDefault(key, 0L) + value);
            }
        }
        
        return totalStats;
    }
    
    private void saveSessionToDatabase(WorkSession session) {
        if (plugin.getDatabaseManager().isConnected()) {
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                if (plugin.getConfig().getBoolean("debug.log-sql", false)) {
                    plugin.getLogger().info("Сохранение сессии в базу данных: " + plugin.getDatabaseManager().getDatabaseType());
                }
                String query = "INSERT INTO time_tracking (player_uuid, player_name, status, start_time) VALUES (?, ?, ?, ?)";
                
                try (Connection conn = plugin.getDatabaseManager().getConnection();
                     PreparedStatement stmt = conn.prepareStatement(query)) {
                    stmt.setString(1, session.getPlayerUUID().toString());
                    stmt.setString(2, session.getPlayerName());
                    stmt.setString(3, session.getStatus());
                    stmt.setLong(4, session.getStartTime());
                    stmt.executeUpdate();
                } catch (SQLException e) {
                    plugin.getLogger().log(Level.SEVERE, "Ошибка сохранения сессии в БД", e);
                }
            });
        } else {
            if (plugin.getConfig().getBoolean("debug.log-sql", false)) {
                plugin.getLogger().info("Сохранение сессии в файл (YAML) - база данных не подключена");
            }
            // Сохраняем в файл - используем start_time как уникальный ключ для избежания дубликатов
            String path = "sessions." + session.getPlayerUUID().toString() + "." + session.getStartTime();
            
            synchronized (timeTrackingFileLock) {
                if (!timeTrackingConfig.contains(path)) {
                    timeTrackingConfig.set(path + ".player_name", session.getPlayerName());
                    timeTrackingConfig.set(path + ".status", session.getStatus());
                    timeTrackingConfig.set(path + ".start_time", session.getStartTime());
                    timeTrackingConfig.set(path + ".end_time", 0L);
                    timeTrackingConfig.set(path + ".duration", 0L);
                }
            }
            scheduleTimeTrackingFileSave();
        }
    }
    
    private void updateSessionInDatabase(WorkSession session) {
        if (plugin.getDatabaseManager().isConnected()) {
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                String query = "UPDATE time_tracking SET end_time = ?, duration = ? WHERE player_uuid = ? AND start_time = ?";
                
                try (Connection conn = plugin.getDatabaseManager().getConnection();
                     PreparedStatement stmt = conn.prepareStatement(query)) {
                    stmt.setLong(1, session.getEndTime());
                    stmt.setLong(2, session.getDuration());
                    stmt.setString(3, session.getPlayerUUID().toString());
                    stmt.setLong(4, session.getStartTime());
                    stmt.executeUpdate();
                } catch (SQLException e) {
                    plugin.getLogger().log(Level.SEVERE, "Ошибка обновления сессии в БД", e);
                }
            });
        } else {
            // Обновляем в файле
            String path = "sessions." + session.getPlayerUUID().toString() + "." + session.getStartTime();
            synchronized (timeTrackingFileLock) {
                if (timeTrackingConfig.contains(path)) {
                    timeTrackingConfig.set(path + ".end_time", session.getEndTime());
                    timeTrackingConfig.set(path + ".duration", session.getDuration());
                }
            }
            scheduleTimeTrackingFileSave();
        }
    }
    
    public void saveAllSessions() {
        for (Map.Entry<UUID, WorkSession> entry : activeSessions.entrySet()) {
            endSession(entry.getKey());
        }
    }
    
    public static class WorkSession {
        private final UUID playerUUID;
        private final String playerName;
        private final String status;
        private final long startTime;
        private long endTime;
        private long duration;
        
        public WorkSession(UUID playerUUID, String playerName, String status, long startTime) {
            this.playerUUID = playerUUID;
            this.playerName = playerName;
            this.status = status;
            this.startTime = startTime;
        }
        
        public void setEndTime(long endTime) {
            this.endTime = endTime;
        }
        
        public void setDuration(long duration) {
            this.duration = duration;
        }
        
        public void calculateDuration() {
            if (endTime > 0) {
                this.duration = endTime - startTime;
            }
        }
        
        public UUID getPlayerUUID() {
            return playerUUID;
        }
        
        public String getPlayerName() {
            return playerName;
        }
        
        public String getStatus() {
            return status;
        }
        
        public long getStartTime() {
            return startTime;
        }
        
        public long getEndTime() {
            return endTime;
        }
        
        public long getDuration() {
            return duration;
        }
        
        public String getFormattedDuration() {
            long seconds = duration / 1000;
            long hours = seconds / 3600;
            long minutes = (seconds % 3600) / 60;
            seconds = seconds % 60;
            
            return String.format("%02d:%02d:%02d", hours, minutes, seconds);
        }
        
        public String getFormattedStartTime() {
            SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss");
            return sdf.format(new Date(startTime));
        }
        
        public String getFormattedEndTime() {
            if (endTime == 0) return "Активна";
            SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss");
            return sdf.format(new Date(endTime));
        }
    }
}
