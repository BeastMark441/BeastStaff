package ru.beastmark.placeholders;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import ru.beastmark.BeastStaff;
import ru.beastmark.managers.TimeTrackingManager;

public class TimeTrackingExpansion extends PlaceholderExpansion {
    
    private final BeastStaff plugin;
    
    public TimeTrackingExpansion(BeastStaff plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public String getIdentifier() {
        return "beaststaff";
    }

    @Override
    public String getAuthor() {
        return "BeastMark";
    }

    @Override
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onPlaceholderRequest(Player player, String identifier) {
        if (player == null || identifier == null) {
            return "";
        }

        if (!plugin.getStaffManager().isStaffMember(player.getUniqueId())) {
            if (identifier.equalsIgnoreCase("status")) {
                return plugin.getMessageManager().getMessage("status-not-staff");
            }
            if (identifier.equalsIgnoreCase("status_color")) {
                return "§7";
            }
            if (identifier.equalsIgnoreCase("is_working")) {
                return plugin.getMessageManager().getMessage("placeholder-no");
            }
            if (identifier.startsWith("work_time_") || identifier.equalsIgnoreCase("total_work_time") || identifier.equalsIgnoreCase("current_session_time")) {
                return "0с";
            }
            return "";
        }

        if (identifier.equalsIgnoreCase("status")) {
            String status = plugin.getTimeTrackingManager().getPlayerStatus(player.getUniqueId());
            return status != null ? status : plugin.getMessageManager().getMessage("status-not-set");
        }

        if (identifier.equalsIgnoreCase("status_color")) {
            String status = plugin.getTimeTrackingManager().getPlayerStatus(player.getUniqueId());
            if (status == null) {
                return "§7";
            }
            if (status.equals("В работе")) {
                return "§a";
            }
            if (status.equals("AFK")) {
                return "§e";
            }
            if (status.equals("Не в работе")) {
                return "§c";
            }
            return "§7";
        }

        if (identifier.equalsIgnoreCase("is_working")) {
            return plugin.getTimeTrackingManager().isPlayerWorking(player.getUniqueId()) ? plugin.getMessageManager().getMessage("placeholder-yes") : plugin.getMessageManager().getMessage("placeholder-no");
        }

        if (identifier.equalsIgnoreCase("current_session_time")) {
            TimeTrackingManager.WorkSession session = plugin.getTimeTrackingManager().getActiveSession(player.getUniqueId());
            if (session == null) {
                return plugin.getMessageManager().getMessage("placeholder-no-active-session");
            }
            long duration = System.currentTimeMillis() - session.getStartTime();
            return formatTime(duration);
        }

        if (identifier.equalsIgnoreCase("work_time_today")) {
            return formatWorkTimeWithActiveSession(player, 1);
        }
        if (identifier.equalsIgnoreCase("work_time_week")) {
            return formatWorkTimeWithActiveSession(player, 7);
        }
        if (identifier.equalsIgnoreCase("work_time_month")) {
            return formatWorkTimeWithActiveSession(player, 30);
        }
        if (identifier.equalsIgnoreCase("total_work_time")) {
            return formatWorkTimeWithActiveSession(player, 365);
        }

        return "";
    }

    private String formatWorkTimeWithActiveSession(Player player, int days) {
        long cached = plugin.getTimeTrackingManager().getCachedTotalWorkTimeMillis(player.getUniqueId(), days);
        long total = cached >= 0 ? cached : 0L;

        TimeTrackingManager.WorkSession session = plugin.getTimeTrackingManager().getActiveSession(player.getUniqueId());
        if (session != null && session.getStatus() != null && !session.getStatus().equals("Не в работе")) {
            total += (System.currentTimeMillis() - session.getStartTime());
        }

        return formatTime(total);
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
