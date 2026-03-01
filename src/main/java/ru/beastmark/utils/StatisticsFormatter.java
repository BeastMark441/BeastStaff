package ru.beastmark.utils;

import ru.beastmark.BeastStaff;
import ru.beastmark.litebans.PunishmentStats;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Форматирование статистики наказаний для отображения в Telegram
 */
public class StatisticsFormatter {
    
    private static final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    
    /**
     * Форматировать статистику наказаний
     */
    public static String formatStats(BeastStaff plugin, PunishmentStats stats, String period) {
        StringBuilder sb = new StringBuilder();
        
        sb.append(plugin.getMessageManager().getMessage("telegram-punishment-stats-header", "period", period));
        
        // Общая статистика
        sb.append(plugin.getMessageManager().getMessage("telegram-punishment-stats-bans",
                "total", String.valueOf(stats.bans),
                "active", String.valueOf(stats.activeBans)));
        
        sb.append(plugin.getMessageManager().getMessage("telegram-punishment-stats-mutes",
                "total", String.valueOf(stats.mutes),
                "active", String.valueOf(stats.activeMutes)));
        
        sb.append(plugin.getMessageManager().getMessage("telegram-punishment-stats-kicks", "count", String.valueOf(stats.kicks)));
        
        if (stats.warnings > 0) {
            sb.append(plugin.getMessageManager().getMessage("telegram-punishment-stats-warnings", "count", String.valueOf(stats.warnings)));
        }
        
        // Топ модераторы
        if (!stats.topModerators.isEmpty()) {
            sb.append(plugin.getMessageManager().getMessage("telegram-punishment-stats-top-moderators-header"));
            int rank = 1;
            for (Map<String, Object> mod : stats.topModerators) {
                String name = (String) mod.get("name");
                Integer count = (Integer) mod.get("count");
                sb.append(plugin.getMessageManager().getMessage("telegram-punishment-stats-top-item",
                        "rank", String.valueOf(rank++),
                        "name", String.valueOf(name),
                        "count", String.valueOf(count)));
            }
            sb.append("\n");
        }
        
        // Популярные причины
        if (!stats.topReasons.isEmpty()) {
            sb.append(plugin.getMessageManager().getMessage("telegram-punishment-stats-top-reasons-header"));
            int rank = 1;
            for (Map<String, Object> reasonMap : stats.topReasons) {
                String reason = (String) reasonMap.get("name");
                Integer count = (Integer) reasonMap.get("count");
                if (reason != null && reason.length() > 30) {
                    reason = reason.substring(0, 27) + "...";
                }
                sb.append(plugin.getMessageManager().getMessage("telegram-punishment-stats-reason-item",
                        "rank", String.valueOf(rank++),
                        "reason", reason != null ? reason : plugin.getMessageManager().getMessage("telegram-punishment-stats-reason-not-specified"),
                        "count", String.valueOf(count)));
            }
            sb.append("\n");
        }
        
        return sb.toString();
    }
    
    /**
     * Форматировать список последних наказаний
     */
    public static String formatRecentBans(BeastStaff plugin, List<Map<String, Object>> punishments) {
        StringBuilder sb = new StringBuilder();
        
        sb.append(plugin.getMessageManager().getMessage("telegram-recent-bans-header"));
        
        if (punishments.isEmpty()) {
            sb.append(plugin.getMessageManager().getMessage("telegram-recent-bans-empty"));
            return sb.toString();
        }
        
        for (int i = 0; i < Math.min(10, punishments.size()); i++) {
            Map<String, Object> p = punishments.get(i);
            String type = getEmojiByType((String) p.get("type"));
            String typeName = ((String) p.get("type")).toUpperCase();
            
            sb.append(plugin.getMessageManager().getMessage("telegram-recent-bans-item-line1",
                    "emoji", type,
                    "player", String.valueOf(p.get("player")),
                    "type", typeName));
            sb.append(plugin.getMessageManager().getMessage("telegram-recent-bans-item-moderator", "moderator", String.valueOf(p.get("moderator"))));
            sb.append(plugin.getMessageManager().getMessage("telegram-recent-bans-item-reason", "reason", String.valueOf(p.get("reason"))));
            
            Timestamp time = (Timestamp) p.get("time");
            if (time != null) {
                sb.append(plugin.getMessageManager().getMessage("telegram-recent-bans-item-when", "time", formatRelativeTime(plugin, time)));
            }
            
            Timestamp until = (Timestamp) p.get("until");
            Boolean active = (Boolean) p.get("active");
            if (until != null && until.getTime() > 0) {
                long untilTime = until.getTime();
                long maxLong = 9223372036854775807L / 1000; // Максимальное значение для постоянных банов
                if (untilTime < maxLong) {
                    sb.append(plugin.getMessageManager().getMessage("telegram-recent-bans-item-until", "until", formatRelativeTime(plugin, until)));
                } else {
                    sb.append(plugin.getMessageManager().getMessage("telegram-recent-bans-item-until", "until", plugin.getMessageManager().getMessage("telegram-recent-bans-until-forever")));
                }
            }
            if (active != null) {
                sb.append(plugin.getMessageManager().getMessage("telegram-recent-bans-status-line",
                        "status", active ? plugin.getMessageManager().getMessage("telegram-recent-bans-status-active") : plugin.getMessageManager().getMessage("telegram-recent-bans-status-expired")));
            }
            
            sb.append("\n");
        }
        
        return sb.toString();
    }
    
    /**
     * Получить эмодзи по типу наказания
     */
    private static String getEmojiByType(String type) {
        if (type == null) return "❓";
        
        switch (type.toLowerCase()) {
            case "ban":
                return "🚫";
            case "mute":
                return "🔇";
            case "kick":
                return "👢";
            case "warning":
                return "⚠️";
            default:
                return "❓";
        }
    }
    
    /**
     * Форматировать время относительно текущего момента
     */
    private static String formatRelativeTime(BeastStaff plugin, Timestamp timestamp) {
        if (timestamp == null) return plugin.getMessageManager().getMessage("telegram-time-not-specified");
        
        long time = timestamp.getTime();
        long now = System.currentTimeMillis();
        long diff = Math.abs(now - time);
        
        // Для будущих дат (until)
        boolean isFuture = time > now;
        
        // Если меньше минуты
        if (diff < 60000) {
            return isFuture ? plugin.getMessageManager().getMessage("telegram-time-few-seconds-future") : plugin.getMessageManager().getMessage("telegram-time-just-now");
        }
        // Если меньше часа
        if (diff < 3600000) {
            long minutes = diff / 60000;
            return isFuture ? plugin.getMessageManager().getMessage("telegram-time-minutes-future", "minutes", String.valueOf(minutes)) : plugin.getMessageManager().getMessage("telegram-time-minutes-ago", "minutes", String.valueOf(minutes));
        }
        // Если меньше дня
        if (diff < 86400000) {
            long hours = diff / 3600000;
            return isFuture ? plugin.getMessageManager().getMessage("telegram-time-hours-future", "hours", String.valueOf(hours)) : plugin.getMessageManager().getMessage("telegram-time-hours-ago", "hours", String.valueOf(hours));
        }
        // Если меньше недели
        if (diff < 604800000) {
            long days = diff / 86400000;
            return isFuture ? plugin.getMessageManager().getMessage("telegram-time-days-future", "days", String.valueOf(days)) : plugin.getMessageManager().getMessage("telegram-time-days-ago", "days", String.valueOf(days));
        }
        
        // Иначе показываем полную дату
        return dateFormat.format(timestamp);
    }
}

