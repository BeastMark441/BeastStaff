package ru.beastmark.placeholders;

import org.bukkit.entity.Player;
import ru.beastmark.BeastStaff;
import ru.beastmark.managers.TimeTrackingManager;

import java.util.Map;

public class TimeTrackingExpansion {
    
    private final BeastStaff plugin;
    
    public TimeTrackingExpansion(BeastStaff plugin) {
        this.plugin = plugin;
    }
    
    public void register() {
        // Регистрация PlaceholderAPI расширения
        // Реализация будет добавлена при наличии PlaceholderAPI
        plugin.getLogger().info("PlaceholderAPI расширение зарегистрировано!");
    }
    
    public String getPlayerStatus(Player player) {
        if (player == null) return "";
        
        if (!plugin.getStaffManager().isStaffMember(player.getUniqueId())) {
            return "Не сотрудник";
        }
        
        String status = plugin.getTimeTrackingManager().getPlayerStatus(player.getUniqueId());
        return status != null ? status : "Не установлен";
    }
    
    public String getPlayerStatusColor(Player player) {
        if (player == null) return "§7";
        
        if (!plugin.getStaffManager().isStaffMember(player.getUniqueId())) {
            return "§7";
        }
        
        String status = plugin.getTimeTrackingManager().getPlayerStatus(player.getUniqueId());
        if (status == null) return "§7";
        
        switch (status) {
            case "В работе":
                return "§a";
            case "AFK":
                return "§e";
            case "Не в работе":
                return "§c";
            default:
                return "§7";
        }
    }
    
    public String getWorkTimeToday(Player player) {
        if (player == null) return "0с";
        
        if (!plugin.getStaffManager().isStaffMember(player.getUniqueId())) {
            return "0с";
        }
        
        Map<String, Long> stats = plugin.getTimeTrackingManager().getPlayerStats(player.getUniqueId(), 1);
        return formatTime(stats.getOrDefault("total", 0L));
    }
    
    public String getWorkTimeWeek(Player player) {
        if (player == null) return "0с";
        
        if (!plugin.getStaffManager().isStaffMember(player.getUniqueId())) {
            return "0с";
        }
        
        Map<String, Long> stats = plugin.getTimeTrackingManager().getPlayerStats(player.getUniqueId(), 7);
        return formatTime(stats.getOrDefault("total", 0L));
    }
    
    public String getWorkTimeMonth(Player player) {
        if (player == null) return "0с";
        
        if (!plugin.getStaffManager().isStaffMember(player.getUniqueId())) {
            return "0с";
        }
        
        Map<String, Long> stats = plugin.getTimeTrackingManager().getPlayerStats(player.getUniqueId(), 30);
        return formatTime(stats.getOrDefault("total", 0L));
    }
    
    public String getTotalWorkTime(Player player) {
        if (player == null) return "0с";
        
        if (!plugin.getStaffManager().isStaffMember(player.getUniqueId())) {
            return "0с";
        }
        
        Map<String, Long> stats = plugin.getTimeTrackingManager().getPlayerStats(player.getUniqueId(), 365);
        return formatTime(stats.getOrDefault("total", 0L));
    }
    
    public String isPlayerWorking(Player player) {
        if (player == null) return "Нет";
        
        if (!plugin.getStaffManager().isStaffMember(player.getUniqueId())) {
            return "Нет";
        }
        
        return plugin.getTimeTrackingManager().isPlayerWorking(player.getUniqueId()) ? "Да" : "Нет";
    }
    
    public String getCurrentSessionTime(Player player) {
        if (player == null) return "Нет активной сессии";
        
        if (!plugin.getStaffManager().isStaffMember(player.getUniqueId())) {
            return "Нет активной сессии";
        }
        
        TimeTrackingManager.WorkSession session = plugin.getTimeTrackingManager().getActiveSession(player.getUniqueId());
        if (session == null) {
            return "Нет активной сессии";
        }
        
        long currentTime = System.currentTimeMillis();
        long duration = currentTime - session.getStartTime();
        return formatTime(duration);
    }
    
    private String formatTime(long milliseconds) {
        long seconds = milliseconds / 1000;
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        seconds = seconds % 60;
        
        if (hours > 0) {
            return String.format("%dч %02dм", hours, minutes);
        } else if (minutes > 0) {
            return String.format("%dм %02dс", minutes, seconds);
        } else {
            return String.format("%dс", seconds);
        }
    }
}
