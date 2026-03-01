package ru.beastmark.telegram.commands;

import ru.beastmark.BeastStaff;

import java.util.Map;
import java.util.UUID;

/**
 * Команда /bs_status для просмотра статуса
 */
public class StatusCommand {
    
    private final BeastStaff plugin;
    
    public StatusCommand(BeastStaff plugin) {
        this.plugin = plugin;
    }
    
    public String handle(String telegramId) {
        // Получить UUID игрока по Telegram ID
        UUID playerUuid = plugin.getTelegramBindingManager().getPlayerByTelegramId(telegramId);
        
        if (playerUuid == null) {
            return plugin.getMessageManager().getMessage("telegram-status-not-bound");
        }
        
        // Получить текущий статус
        String currentStatus = plugin.getTimeTrackingManager().getPlayerStatus(playerUuid);
        
        if (currentStatus == null || currentStatus.isEmpty()) {
            currentStatus = plugin.getMessageManager().getMessage("status-not-set");
        }
        
        // Получить статистику за сегодня
        Map<String, Long> stats = plugin.getTimeTrackingManager().getPlayerStats(playerUuid, 1);
        long totalTime = stats.getOrDefault("total", 0L);
        
        return plugin.getMessageManager().getMessage("telegram-status-response",
                "status", currentStatus,
                "time", formatTime(totalTime));
    }
    
    private String formatTime(long milliseconds) {
        long seconds = milliseconds / 1000;
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        seconds = seconds % 60;
        
        if (hours > 0) {
            return String.format("%dч %02dм %02dс", hours, minutes, seconds);
        } else if (minutes > 0) {
            return String.format("%dм %02dс", minutes, seconds);
        } else {
            return String.format("%dс", seconds);
        }
    }
}

