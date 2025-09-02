package ru.beastmark.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import ru.beastmark.BeastStaff;
import ru.beastmark.managers.StaffManager;

public class StaffListener implements Listener {
    
    private final BeastStaff plugin;
    
    public StaffListener(BeastStaff plugin) {
        this.plugin = plugin;
    }
    
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        StaffManager staffManager = plugin.getStaffManager();
        
        if (staffManager.isStaffMember(player.getUniqueId())) {
            StaffManager.StaffMember member = staffManager.getStaffMember(player.getUniqueId());
            player.sendMessage("§a[BeastStaff] Добро пожаловать! Ваш ранг: " + member.getRank());
            
            // Уведомляем других игроков о входе персонала
            for (Player onlinePlayer : plugin.getServer().getOnlinePlayers()) {
                if (onlinePlayer.hasPermission("beaststaff.notify") && !onlinePlayer.equals(player)) {
                    onlinePlayer.sendMessage("§6[BeastStaff] " + member.getName() + " (" + member.getRank() + ") зашел на сервер!");
                }
            }
            
            // Отправляем уведомление в Telegram
            if (plugin.getTelegramIntegration() != null) {
                plugin.getTelegramIntegration().notifyPlayerJoin(player);
            }
        }
    }
    
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        StaffManager staffManager = plugin.getStaffManager();
        
        if (staffManager.isStaffMember(player.getUniqueId())) {
            StaffManager.StaffMember member = staffManager.getStaffMember(player.getUniqueId());
            
            // Уведомляем других игроков о выходе персонала
            for (Player onlinePlayer : plugin.getServer().getOnlinePlayers()) {
                if (onlinePlayer.hasPermission("beaststaff.notify")) {
                    onlinePlayer.sendMessage("§6[BeastStaff] " + member.getName() + " (" + member.getRank() + ") покинул сервер!");
                }
            }
            
            // Отправляем уведомление в Telegram
            if (plugin.getTelegramIntegration() != null) {
                plugin.getTelegramIntegration().notifyPlayerQuit(player);
            }
        }
    }
}
