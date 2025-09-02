package ru.beastmark.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerKickEvent;
import ru.beastmark.BeastStaff;

public class TimeTrackingListener implements Listener {
    
    private final BeastStaff plugin;
    
    public TimeTrackingListener(BeastStaff plugin) {
        this.plugin = plugin;
    }
    
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        
        if (plugin.getStaffManager().isStaffMember(player.getUniqueId())) {
            // Если у игрока был активный статус, восстанавливаем его
            String lastStatus = plugin.getTimeTrackingManager().getPlayerStatus(player.getUniqueId());
            if (lastStatus != null && !lastStatus.equals("Не в работе")) {
                plugin.getTimeTrackingManager().startSession(player, lastStatus);
            }
        }
    }
    
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        
        if (plugin.getStaffManager().isStaffMember(player.getUniqueId())) {
            // При выходе игрока автоматически переводим в статус "Не в работе"
            plugin.getTimeTrackingManager().endSession(player.getUniqueId(), "Не в работе");
        }
    }
    
    @EventHandler
    public void onPlayerKick(PlayerKickEvent event) {
        Player player = event.getPlayer();
        
        if (plugin.getStaffManager().isStaffMember(player.getUniqueId())) {
            // При кике игрока автоматически переводим в статус "Не в работе"
            plugin.getTimeTrackingManager().endSession(player.getUniqueId(), "Не в работе");
        }
    }
}
