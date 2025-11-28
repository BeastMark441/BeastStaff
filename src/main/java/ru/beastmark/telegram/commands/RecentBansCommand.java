package ru.beastmark.telegram.commands;

import ru.beastmark.BeastStaff;
import ru.beastmark.litebans.LiteBansManager;
import ru.beastmark.utils.StatisticsFormatter;

import java.util.List;
import java.util.Map;

/**
 * Команда /bs_recent_bans для получения последних наказаний
 */
public class RecentBansCommand {
    
    private final BeastStaff plugin;
    private final LiteBansManager liteBansManager;
    
    public RecentBansCommand(BeastStaff plugin, LiteBansManager liteBansManager) {
        this.plugin = plugin;
        this.liteBansManager = liteBansManager;
    }
    
    public String handle(String[] args) {
        if (liteBansManager == null || !liteBansManager.isEnabled()) {
            return "❌ Интеграция с LiteBans не включена или недоступна";
        }
        
        int limit = 10;
        if (args.length > 0) {
            try {
                limit = Integer.parseInt(args[0]);
                if (limit > 50) limit = 50; // Максимум 50
                if (limit < 1) limit = 10;
            } catch (NumberFormatException e) {
                limit = 10;
            }
        }
        
        List<Map<String, Object>> punishments = liteBansManager.getRecentPunishments(limit);
        
        if (punishments.isEmpty()) {
            return "📭 Нет последних наказаний";
        }
        
        return StatisticsFormatter.formatRecentBans(punishments);
    }
}

