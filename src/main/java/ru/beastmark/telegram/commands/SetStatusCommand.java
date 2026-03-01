package ru.beastmark.telegram.commands;

import ru.beastmark.BeastStaff;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

/**
 * Команда /bs_set_status для изменения статуса
 */
public class SetStatusCommand {
    
    private final BeastStaff plugin;
    
    public SetStatusCommand(BeastStaff plugin) {
        this.plugin = plugin;
    }
    
    public String handle(String telegramId, String[] args) {
        if (args.length == 0) {
            return plugin.getMessageManager().getMessage("telegram-setstatus-usage", "statuses", getAvailableStatuses());
        }
        
        // Получить UUID игрока по Telegram ID
        UUID playerUuid = plugin.getTelegramBindingManager().getPlayerByTelegramId(telegramId);
        
        if (playerUuid == null) {
            return plugin.getMessageManager().getMessage("telegram-status-not-bound");
        }
        
        Player player = Bukkit.getPlayer(playerUuid);
        if (player == null || !player.isOnline()) {
            return plugin.getMessageManager().getMessage("telegram-setstatus-must-be-online");
        }
        
        String newStatus = String.join(" ", args);
        
        // Проверить, что статус валидный
        List<String> availableStatuses = plugin.getConfig().getStringList("time-tracking.statuses");
        if (!availableStatuses.contains(newStatus)) {
            return plugin.getMessageManager().getMessage("telegram-setstatus-invalid-status", "statuses", getAvailableStatuses());
        }
        
        // Установить статус
        plugin.getTimeTrackingManager().startSession(player, newStatus);
        
        return plugin.getMessageManager().getMessage("telegram-setstatus-success", "status", newStatus);
    }
    
    private String getAvailableStatuses() {
        List<String> statuses = plugin.getConfig().getStringList("time-tracking.statuses");
        StringBuilder sb = new StringBuilder();
        for (String status : statuses) {
            sb.append("• `").append(status).append("`\n");
        }
        return sb.toString();
    }
}

