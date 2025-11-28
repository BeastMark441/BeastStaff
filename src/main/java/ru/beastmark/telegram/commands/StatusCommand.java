package ru.beastmark.telegram.commands;

import ru.beastmark.BeastStaff;

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
            return "❌ Ваш Telegram аккаунт не привязан к игровому аккаунту.\n" +
                   "Используйте команду в игре: /bs telegram bind";
        }
        
        // Получить текущий статус
        String currentStatus = plugin.getTimeTrackingManager().getPlayerStatus(playerUuid);
        
        if (currentStatus == null || currentStatus.isEmpty()) {
            currentStatus = "Не установлен";
        }
        
        // Получить статистику за сегодня
        var stats = plugin.getTimeTrackingManager().getPlayerStats(playerUuid, 1);
        long totalTime = stats.getOrDefault("total", 0L);
        
        StringBuilder sb = new StringBuilder();
        sb.append("📊 *Ваш статус:*\n\n");
        sb.append("Статус: `").append(currentStatus).append("`\n");
        sb.append("Время работы сегодня: `").append(formatTime(totalTime)).append("`\n");
        
        return sb.toString();
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

