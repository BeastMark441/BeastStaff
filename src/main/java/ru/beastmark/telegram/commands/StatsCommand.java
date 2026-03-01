package ru.beastmark.telegram.commands;

import ru.beastmark.BeastStaff;
import ru.beastmark.litebans.LiteBansManager;
import ru.beastmark.litebans.PunishmentStats;
import ru.beastmark.utils.StatisticsFormatter;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * Команда /bs_ban_stats для получения статистики наказаний
 */
public class StatsCommand {
    
    private final BeastStaff plugin;
    private final LiteBansManager liteBansManager;
    
    public StatsCommand(BeastStaff plugin, LiteBansManager liteBansManager) {
        this.plugin = plugin;
        this.liteBansManager = liteBansManager;
    }
    
    public String handle(String[] args) {
        if (liteBansManager == null || !liteBansManager.isEnabled()) {
            return plugin.getMessageManager().getMessage("telegram-litebans-disabled");
        }
        
        // Определить период
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime from;
        String period = "сегодня";
        
        if (args.length > 0) {
            switch (args[0].toLowerCase()) {
                case "week":
                case "неделя":
                    from = now.minus(7, ChronoUnit.DAYS);
                    period = "неделю";
                    break;
                case "month":
                case "месяц":
                    from = now.minus(30, ChronoUnit.DAYS);
                    period = "месяц";
                    break;
                default:
                    from = now.truncatedTo(ChronoUnit.DAYS);
                    break;
            }
        } else {
            from = now.truncatedTo(ChronoUnit.DAYS);
        }
        
        // Получить статистику
        PunishmentStats stats = liteBansManager.getStats(from, now);
        
        // Форматировать сообщение
        return StatisticsFormatter.formatStats(plugin, stats, period);
    }
}

