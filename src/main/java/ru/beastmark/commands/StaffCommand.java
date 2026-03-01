package ru.beastmark.commands;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import ru.beastmark.BeastStaff;
import ru.beastmark.managers.StaffManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public class StaffCommand implements CommandExecutor, TabCompleter {
    
    private final BeastStaff plugin;
    
    public StaffCommand(BeastStaff plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelpMessage(sender);
            return true;
        }
        
        switch (args[0].toLowerCase()) {
            case "add":
                if (!sender.hasPermission("beaststaff.command.add")) {
                    sender.sendMessage(plugin.getMessageManager().getMessage("no-permission"));
                    return true;
                }
                if (args.length < 3) {
                    sender.sendMessage(plugin.getMessageManager().getMessage("usage-add"));
                    return true;
                }
                // Объединяем все аргументы после имени игрока в один ранг
                String rank = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
                handleAddCommand(sender, args[1], rank);
                break;
                
            case "remove":
                if (!sender.hasPermission("beaststaff.command.remove")) {
                    sender.sendMessage(plugin.getMessageManager().getMessage("no-permission"));
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(plugin.getMessageManager().getMessage("usage-remove"));
                    return true;
                }
                handleRemoveCommand(sender, args[1]);
                break;
                
            case "list":
                if (!sender.hasPermission("beaststaff.command.list")) {
                    sender.sendMessage(plugin.getMessageManager().getMessage("no-permission"));
                    return true;
                }
                handleListCommand(sender);
                break;
                
            case "info":
                if (!sender.hasPermission("beaststaff.command.info")) {
                    sender.sendMessage(plugin.getMessageManager().getMessage("no-permission"));
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(plugin.getMessageManager().getMessage("usage-info"));
                    return true;
                }
                handleInfoCommand(sender, args[1]);
                break;
                
            case "reload":
                if (!sender.hasPermission("beaststaff.command.reload")) {
                    sender.sendMessage(plugin.getMessageManager().getMessage("no-permission"));
                    return true;
                }
                handleReloadCommand(sender);
                break;
                
            default:
                sendHelpMessage(sender);
                break;
        }
        
        return true;
    }
    
    private void handleAddCommand(CommandSender sender, String playerName, String rank) {
        Player target = Bukkit.getPlayer(playerName);
        if (target == null) {
            // Пытаемся найти игрока по частичному совпадению
            target = Bukkit.getOnlinePlayers().stream()
                    .filter(player -> player.getName().toLowerCase().startsWith(playerName.toLowerCase()))
                    .findFirst()
                    .orElse(null);
            
            if (target == null) {
                sender.sendMessage(plugin.getMessageManager().getMessage("player-not-found", "player", playerName));
                return;
            }
        }
        
        // Проверяем, что ранг существует в конфигурации
        List<String> availableRanks = plugin.getConfig().getStringList("ranks.available");
        if (!availableRanks.contains(rank)) {
            sender.sendMessage(plugin.getMessageManager().getMessage("unknown-rank", "rank", rank));
            sender.sendMessage(plugin.getMessageManager().getMessage("available-ranks", "ranks", String.join(", ", availableRanks)));
            return;
        }
        
        StaffManager staffManager = plugin.getStaffManager();
        if (staffManager.isStaffMember(target.getUniqueId())) {
            sender.sendMessage(plugin.getMessageManager().getMessage("already-staff", "player", playerName));
            return;
        }
        
        staffManager.addStaffMember(target, rank);
        sender.sendMessage(plugin.getMessageManager().getMessage("staff-added", "player", playerName, "rank", rank));
        target.sendMessage(plugin.getMessageManager().getMessage("staff-added-target", "rank", rank));
    }
    
    private void handleRemoveCommand(CommandSender sender, String playerName) {
        Player target = Bukkit.getPlayer(playerName);
        if (target == null) {
            // Пытаемся найти игрока по частичному совпадению
            target = Bukkit.getOnlinePlayers().stream()
                    .filter(player -> player.getName().toLowerCase().startsWith(playerName.toLowerCase()))
                    .findFirst()
                    .orElse(null);
            
            if (target == null) {
                sender.sendMessage(plugin.getMessageManager().getMessage("player-not-found", "player", playerName));
                return;
            }
        }
        
        StaffManager staffManager = plugin.getStaffManager();
        if (!staffManager.isStaffMember(target.getUniqueId())) {
            sender.sendMessage(plugin.getMessageManager().getMessage("not-staff", "player", playerName));
            return;
        }
        
        staffManager.removeStaffMember(target.getUniqueId());
        sender.sendMessage(plugin.getMessageManager().getMessage("staff-removed", "player", playerName));
        target.sendMessage(plugin.getMessageManager().getMessage("staff-removed-target"));
    }
    
    private void handleListCommand(CommandSender sender) {
        StaffManager staffManager = plugin.getStaffManager();
        Map<UUID, StaffManager.StaffMember> staffMembers = staffManager.getAllStaffMembers();
        
        if (staffMembers.isEmpty()) {
            sender.sendMessage(plugin.getMessageManager().getMessage("staff-list-empty"));
            return;
        }
        
        sender.sendMessage(plugin.getMessageManager().getMessage("staff-list-header"));
        for (StaffManager.StaffMember member : staffMembers.values()) {
            sender.sendMessage(plugin.getMessageManager().getMessage("staff-list-item", "player", member.getName(), "rank", member.getRank()));
        }
    }
    
    private void handleInfoCommand(CommandSender sender, String playerName) {
        Player target = Bukkit.getPlayer(playerName);
        if (target == null) {
            // Пытаемся найти игрока по частичному совпадению
            target = Bukkit.getOnlinePlayers().stream()
                    .filter(player -> player.getName().toLowerCase().startsWith(playerName.toLowerCase()))
                    .findFirst()
                    .orElse(null);
            
            if (target == null) {
                sender.sendMessage(plugin.getMessageManager().getMessage("player-not-found", "player", playerName));
                return;
            }
        }
        
        StaffManager staffManager = plugin.getStaffManager();
        if (!staffManager.isStaffMember(target.getUniqueId())) {
            sender.sendMessage(plugin.getMessageManager().getMessage("not-staff", "player", playerName));
            return;
        }
        
        StaffManager.StaffMember member = staffManager.getStaffMember(target.getUniqueId());
        sender.sendMessage(plugin.getMessageManager().getMessage("staff-info-header", "player", member.getName()));
        sender.sendMessage(plugin.getMessageManager().getMessage("staff-info-rank", "rank", member.getRank()));
        sender.sendMessage(plugin.getMessageManager().getMessage("staff-info-date", "date", new java.text.SimpleDateFormat("dd.MM.yyyy HH:mm:ss")
                .format(new java.util.Date(member.getJoinDate()))));
    }
    
    private void handleReloadCommand(CommandSender sender) {
        plugin.reloadConfig();
        plugin.getStaffManager().loadData();
        plugin.getTimeTrackingManager().reloadAvailableStatuses();
        plugin.getTelegramIntegration().reload();
        plugin.getTelegramBindingManager().reloadBindings();
        plugin.getDatabaseManager().reload();
        plugin.getMessageManager().loadMessages();
        sender.sendMessage(plugin.getMessageManager().getMessage("config-reloaded"));
    }
    
    private void sendHelpMessage(CommandSender sender) {
        sender.sendMessage(plugin.getMessageManager().getMessage("staff-help-header"));
        sender.sendMessage(plugin.getMessageManager().getMessage("staff-help-add"));
        sender.sendMessage(plugin.getMessageManager().getMessage("staff-help-remove"));
        sender.sendMessage(plugin.getMessageManager().getMessage("staff-help-list"));
        sender.sendMessage(plugin.getMessageManager().getMessage("staff-help-info"));
        sender.sendMessage(plugin.getMessageManager().getMessage("staff-help-reload"));
        sender.sendMessage(plugin.getMessageManager().getMessage("staff-help-menu"));
        sender.sendMessage(plugin.getMessageManager().getMessage("staff-help-status"));
        sender.sendMessage(plugin.getMessageManager().getMessage("staff-help-stats"));
        sender.sendMessage(plugin.getMessageManager().getMessage("staff-help-allstats"));
        sender.sendMessage(plugin.getMessageManager().getMessage("staff-help-telegram"));
        if (sender.hasPermission("beaststaff.admin")) {
            sender.sendMessage(plugin.getMessageManager().getMessage("staff-help-setstatus"));
            sender.sendMessage(plugin.getMessageManager().getMessage("staff-help-testtelegram"));
        }
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        
        if (args.length == 1) {
            completions.add("add");
            completions.add("remove");
            completions.add("list");
            completions.add("info");
            completions.add("reload");
            completions.add("menu");
            completions.add("status");
            completions.add("stats");
            completions.add("allstats");
            completions.add("telegram");
            completions.add("setstatus");
            completions.add("testtelegram");
        } else if (args.length == 2) {
            if (args[0].equalsIgnoreCase("add") || args[0].equalsIgnoreCase("remove") || args[0].equalsIgnoreCase("info")) {
                return Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
            }
        } else if (args.length >= 3) {
            if (args[0].equalsIgnoreCase("add") && args.length == 3) {
                // Показываем доступные ранги для команды add
                return plugin.getConfig().getStringList("ranks.available").stream()
                        .filter(rank -> rank.toLowerCase().startsWith(args[2].toLowerCase()))
                        .collect(Collectors.toList());
            }
        }
        
        return completions.stream()
                .filter(s -> s.toLowerCase().startsWith(args[args.length - 1].toLowerCase()))
                .collect(Collectors.toList());
    }
}
