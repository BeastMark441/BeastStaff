package ru.beastmark.utils;

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
    public static String formatStats(PunishmentStats stats, String period) {
        StringBuilder sb = new StringBuilder();
        
        sb.append("📊 *Статистика наказаний за ").append(period).append("*\n\n");
        
        // Общая статистика
        sb.append("🔴 *Баны:*\n");
        sb.append("Всего: `").append(stats.bans).append("`\n");
        sb.append("Активных: `").append(stats.activeBans).append("`\n\n");
        
        sb.append("🔇 *Муты:*\n");
        sb.append("Всего: `").append(stats.mutes).append("`\n");
        sb.append("Активных: `").append(stats.activeMutes).append("`\n\n");
        
        sb.append("👢 *Кики:* `").append(stats.kicks).append("`\n\n");
        
        if (stats.warnings > 0) {
            sb.append("⚠️ *Предупреждения:* `").append(stats.warnings).append("`\n\n");
        }
        
        // Топ модераторы
        if (!stats.topModerators.isEmpty()) {
            sb.append("👑 *Топ модераторы:*\n");
            int rank = 1;
            for (Map<String, Object> mod : stats.topModerators) {
                String name = (String) mod.get("name");
                Integer count = (Integer) mod.get("count");
                sb.append(String.format("%d. `%s` - %d\n", rank++, name, count));
            }
            sb.append("\n");
        }
        
        // Популярные причины
        if (!stats.topReasons.isEmpty()) {
            sb.append("📝 *Популярные причины:*\n");
            int rank = 1;
            for (Map<String, Object> reasonMap : stats.topReasons) {
                String reason = (String) reasonMap.get("name");
                Integer count = (Integer) reasonMap.get("count");
                if (reason != null && reason.length() > 30) {
                    reason = reason.substring(0, 27) + "...";
                }
                sb.append(String.format("%d. `%s` - %d\n", rank++, reason != null ? reason : "Не указано", count));
            }
            sb.append("\n");
        }
        
        return sb.toString();
    }
    
    /**
     * Форматировать список последних наказаний
     */
    public static String formatRecentBans(List<Map<String, Object>> punishments) {
        StringBuilder sb = new StringBuilder();
        
        sb.append("🚨 *Последние наказания:*\n\n");
        
        if (punishments.isEmpty()) {
            sb.append("📭 Нет последних наказаний");
            return sb.toString();
        }
        
        for (int i = 0; i < Math.min(10, punishments.size()); i++) {
            Map<String, Object> p = punishments.get(i);
            String type = getEmojiByType((String) p.get("type"));
            String typeName = ((String) p.get("type")).toUpperCase();
            
            sb.append(String.format("%s *%s* — `%s`\n", type, p.get("player"), typeName));
            sb.append(String.format("👨 Модератор: `%s`\n", p.get("moderator")));
            sb.append(String.format("📝 Причина: `%s`\n", p.get("reason")));
            
            Timestamp time = (Timestamp) p.get("time");
            if (time != null) {
                sb.append(String.format("⏰ Когда: `%s`\n", formatRelativeTime(time)));
            }
            
            Timestamp until = (Timestamp) p.get("until");
            Boolean active = (Boolean) p.get("active");
            if (until != null && until.getTime() > 0) {
                long untilTime = until.getTime();
                long maxLong = 9223372036854775807L / 1000; // Максимальное значение для постоянных банов
                if (untilTime < maxLong) {
                    sb.append(String.format("📅 До: `%s`\n", formatRelativeTime(until)));
                } else {
                    sb.append("📅 До: `Навсегда`\n");
                }
            }
            if (active != null) {
                sb.append(String.format("Статус: %s\n", active ? "🔴 АКТИВНО" : "🟢 ИСТЕКЛО"));
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
    private static String formatRelativeTime(Timestamp timestamp) {
        if (timestamp == null) return "Не указано";
        
        long time = timestamp.getTime();
        long now = System.currentTimeMillis();
        long diff = Math.abs(now - time);
        
        // Для будущих дат (until)
        boolean isFuture = time > now;
        
        // Если меньше минуты
        if (diff < 60000) {
            return isFuture ? "через несколько секунд" : "только что";
        }
        // Если меньше часа
        if (diff < 3600000) {
            long minutes = diff / 60000;
            return isFuture ? "через " + minutes + " мин." : minutes + " мин. назад";
        }
        // Если меньше дня
        if (diff < 86400000) {
            long hours = diff / 3600000;
            return isFuture ? "через " + hours + " ч." : hours + " ч. назад";
        }
        // Если меньше недели
        if (diff < 604800000) {
            long days = diff / 86400000;
            return isFuture ? "через " + days + " дн." : days + " дн. назад";
        }
        
        // Иначе показываем полную дату
        return dateFormat.format(timestamp);
    }
}

