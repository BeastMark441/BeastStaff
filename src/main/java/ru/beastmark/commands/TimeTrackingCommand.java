package ru.beastmark.commands;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import ru.beastmark.BeastStaff;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class TimeTrackingCommand implements CommandExecutor, TabCompleter {
    
    private final BeastStaff plugin;
    
    public TimeTrackingCommand(BeastStaff plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelpMessage(sender);
            return true;
        }
        
        switch (args[0].toLowerCase()) {
            case "menu":
            case "bsm":
                if (!sender.hasPermission("beaststaff.command.menu")) {
                    sender.sendMessage(plugin.getMessageManager().getMessage("no-permission"));
                    return true;
                }
                if (!(sender instanceof Player)) {
                    sender.sendMessage(plugin.getMessageManager().getMessage("command-player-only"));
                    return true;
                }
                handleMenuCommand((Player) sender);
                break;
                
            case "status":
                if (!sender.hasPermission("beaststaff.command.status")) {
                    sender.sendMessage(plugin.getMessageManager().getMessage("no-permission"));
                    return true;
                }
                if (!(sender instanceof Player)) {
                    sender.sendMessage(plugin.getMessageManager().getMessage("command-player-only"));
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(plugin.getMessageManager().getMessage("usage-status"));
                    return true;
                }
                // Объединяем все аргументы после "status" в один статус
                String status = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
                handleStatusCommand((Player) sender, status);
                break;
                
            case "stats":
                handleStats(sender, args);
                break;
                
            case "allstats":
                if (!sender.hasPermission("beaststaff.command.allstats")) {
                    sender.sendMessage(plugin.getMessageManager().getMessage("no-permission"));
                    return true;
                }
                int days = args.length > 1 ? parseDaysOrDefault(sender, args[1], 7) : 7;
                if (days <= 0) {
                    return true;
                }
                handleAllStatsCommand(sender, Math.min(days, 365));
                break;
                
            case "setstatus":
                if (!sender.hasPermission("beaststaff.command.setstatus")) {
                    sender.sendMessage(plugin.getMessageManager().getMessage("no-permission"));
                    return true;
                }
                if (args.length < 3) {
                    sender.sendMessage(plugin.getMessageManager().getMessage("usage-setstatus"));
                    return true;
                }
                // Объединяем все аргументы после имени игрока в один статус
                String setStatus = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
                handleSetStatusCommand(sender, args[1], setStatus);
                break;
                
            case "telegram":
                if (!sender.hasPermission("beaststaff.command.telegram")) {
                    sender.sendMessage(plugin.getMessageManager().getMessage("no-permission"));
                    return true;
                }
                if (!(sender instanceof Player)) {
                    sender.sendMessage(plugin.getMessageManager().getMessage("command-player-only"));
                    return true;
                }
                handleTelegramCommand((Player) sender, args);
                break;
                
            case "testtelegram":
                if (!sender.hasPermission("beaststaff.command.testtelegram")) {
                    sender.sendMessage(plugin.getMessageManager().getMessage("no-permission"));
                    return true;
                }
                handleTestTelegramCommand(sender);
                break;
                
            default:
                sendHelpMessage(sender);
                break;
        }
        
        return true;
    }
    
    private void handleMenuCommand(Player player) {
        if (!plugin.getStaffManager().isStaffMember(player.getUniqueId())) {
            player.sendMessage(plugin.getMessageManager().getMessage("not-staff-self"));
            return;
        }
        
        List<String> statuses = plugin.getTimeTrackingManager().getAvailableStatuses();
        String currentStatus = plugin.getTimeTrackingManager().getPlayerStatus(player.getUniqueId());
        
        player.sendMessage(plugin.getMessageManager().getMessage("time-menu-header"));
        player.sendMessage(plugin.getMessageManager().getMessage("time-menu-current-status", "status", currentStatus != null ? currentStatus : plugin.getMessageManager().getMessage("status-not-set")));
        player.sendMessage(plugin.getMessageManager().getMessage("time-menu-separator"));
        
        for (int i = 0; i < statuses.size(); i++) {
            String status = statuses.get(i);
            String color = currentStatus != null && currentStatus.equals(status) ? "§a" : "§7";
            player.sendMessage(color + (i + 1) + ". " + status);
        }
        
        player.sendMessage(plugin.getMessageManager().getMessage("time-menu-separator"));
        player.sendMessage(plugin.getMessageManager().getMessage("time-menu-usage"));
    }
    
    private void handleStatusCommand(Player player, String status) {
        if (!plugin.getStaffManager().isStaffMember(player.getUniqueId())) {
            player.sendMessage(plugin.getMessageManager().getMessage("not-staff-self"));
            return;
        }
        
        List<String> availableStatuses = plugin.getTimeTrackingManager().getAvailableStatuses();
        if (!availableStatuses.contains(status)) {
            player.sendMessage(plugin.getMessageManager().getMessage("unknown-status", "status", status));
            player.sendMessage(plugin.getMessageManager().getMessage("available-statuses", "statuses", String.join(", ", availableStatuses)));
            return;
        }
        
        plugin.getTimeTrackingManager().startSession(player, status);
    }
    
    private void handleStats(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            if (args.length < 2) {
                sender.sendMessage(plugin.getMessageManager().getMessage("usage-stats"));
                return;
            }
            if (!sender.hasPermission("beaststaff.command.stats.other")) {
                sender.sendMessage(plugin.getMessageManager().getMessage("no-permission"));
                return;
            }
            int days = args.length > 2 ? parseDaysOrDefault(sender, args[2], 7) : 7;
            if (days <= 0) {
                return;
            }
            handleStatsCommand(sender, args[1], Math.min(days, 365));
            return;
        }

        Player player = (Player) sender;

        if (args.length == 1) {
            if (!sender.hasPermission("beaststaff.command.stats")) {
                sender.sendMessage(plugin.getMessageManager().getMessage("no-permission"));
                return;
            }
            handleStatsCommand(sender, player.getName(), 7);
            return;
        }

        if (args.length == 2) {
            Integer maybeDays = tryParseInt(args[1]);
            if (maybeDays != null) {
                if (!sender.hasPermission("beaststaff.command.stats")) {
                    sender.sendMessage(plugin.getMessageManager().getMessage("no-permission"));
                    return;
                }
                int days = parseDaysOrDefault(sender, args[1], 7);
                if (days <= 0) {
                    return;
                }
                handleStatsCommand(sender, player.getName(), Math.min(days, 365));
                return;
            }

            if (!sender.hasPermission("beaststaff.command.stats.other")) {
                sender.sendMessage(plugin.getMessageManager().getMessage("no-permission"));
                return;
            }
            handleStatsCommand(sender, args[1], 7);
            return;
        }

        if (!sender.hasPermission("beaststaff.command.stats.other")) {
            sender.sendMessage(plugin.getMessageManager().getMessage("no-permission"));
            return;
        }
        int days = parseDaysOrDefault(sender, args[2], 7);
        if (days <= 0) {
            return;
        }
        handleStatsCommand(sender, args[1], Math.min(days, 365));
    }

    private void handleStatsCommand(CommandSender sender, String playerName, int days) {
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
        
        if (!plugin.getStaffManager().isStaffMember(target.getUniqueId())) {
            sender.sendMessage(plugin.getMessageManager().getMessage("not-staff", "player", playerName));
            return;
        }
        
        Map<String, Long> stats = plugin.getTimeTrackingManager().getPlayerStats(target.getUniqueId(), days);
        String currentStatus = plugin.getTimeTrackingManager().getPlayerStatus(target.getUniqueId());
        
        sender.sendMessage(plugin.getMessageManager().getMessage("time-stats-header", "player", playerName, "days", String.valueOf(days)));
        sender.sendMessage(plugin.getMessageManager().getMessage("time-stats-current-status", "status", currentStatus != null ? currentStatus : plugin.getMessageManager().getMessage("status-not-set")));
        sender.sendMessage(plugin.getMessageManager().getMessage("time-stats-total", "time", formatTime(stats.getOrDefault("total", 0L))));
        sender.sendMessage(plugin.getMessageManager().getMessage("time-stats-separator"));
        
        for (Map.Entry<String, Long> entry : stats.entrySet()) {
            if (!entry.getKey().equals("total")) {
                sender.sendMessage("§7" + entry.getKey() + ": §f" + formatTime(entry.getValue()));
            }
        }
    }
    
    private void handleAllStatsCommand(CommandSender sender, int days) {
        Map<String, Long> stats = plugin.getTimeTrackingManager().getAllStaffStats(days);
        
        sender.sendMessage(plugin.getMessageManager().getMessage("time-allstats-header", "days", String.valueOf(days)));
        sender.sendMessage(plugin.getMessageManager().getMessage("time-allstats-total", "time", formatTime(stats.getOrDefault("total", 0L))));
        sender.sendMessage(plugin.getMessageManager().getMessage("time-stats-separator"));
        
        for (Map.Entry<String, Long> entry : stats.entrySet()) {
            if (!entry.getKey().equals("total")) {
                sender.sendMessage("§7" + entry.getKey() + ": §f" + formatTime(entry.getValue()));
            }
        }
    }
    
    private void handleSetStatusCommand(CommandSender sender, String playerName, String status) {
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
        
        if (!plugin.getStaffManager().isStaffMember(target.getUniqueId())) {
            sender.sendMessage(plugin.getMessageManager().getMessage("not-staff", "player", playerName));
            return;
        }
        
        List<String> availableStatuses = plugin.getTimeTrackingManager().getAvailableStatuses();
        if (!availableStatuses.contains(status)) {
            sender.sendMessage(plugin.getMessageManager().getMessage("unknown-status", "status", status));
            sender.sendMessage(plugin.getMessageManager().getMessage("available-statuses", "statuses", String.join(", ", availableStatuses)));
            return;
        }
        
        plugin.getTimeTrackingManager().startSession(target, status);
        sender.sendMessage(plugin.getMessageManager().getMessage("time-setstatus-success", "player", playerName, "status", status));
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
    
    private void handleTelegramCommand(Player player, String[] args) {
        if (!plugin.getStaffManager().isStaffMember(player.getUniqueId())) {
            player.sendMessage(plugin.getMessageManager().getMessage("not-staff-self"));
            return;
        }
        
        if (args.length < 2) {
            player.sendMessage(plugin.getMessageManager().getMessage("telegram-help-header"));
            player.sendMessage(plugin.getMessageManager().getMessage("telegram-help-bind"));
            player.sendMessage(plugin.getMessageManager().getMessage("telegram-help-unbind"));
            player.sendMessage(plugin.getMessageManager().getMessage("telegram-help-status"));
            return;
        }
        
        switch (args[1].toLowerCase()) {
            case "bind":
                if (args.length < 3) {
                    player.sendMessage(plugin.getMessageManager().getMessage("telegram-bind-usage"));
                    player.sendMessage(plugin.getMessageManager().getMessage("telegram-bind-hint"));
                    return;
                }
                handleTelegramBind(player, args[2]);
                break;
                
            case "unbind":
                handleTelegramUnbind(player);
                break;
                
            case "status":
                handleTelegramStatus(player);
                break;
                
            default:
                player.sendMessage(plugin.getMessageManager().getMessage("telegram-unknown-subcommand"));
                break;
        }
    }
    
    private void handleTelegramBind(Player player, String telegramId) {
        if (plugin.getTelegramBindingManager().bindPlayer(player.getUniqueId(), telegramId)) {
            player.sendMessage(plugin.getMessageManager().getMessage("telegram-bind-success"));
            player.sendMessage(plugin.getMessageManager().getMessage("telegram-bind-success-hint"));
        } else {
            player.sendMessage(plugin.getMessageManager().getMessage("telegram-bind-error"));
        }
    }
    
    private void handleTelegramUnbind(Player player) {
        if (plugin.getTelegramBindingManager().unbindPlayer(player.getUniqueId())) {
            player.sendMessage(plugin.getMessageManager().getMessage("telegram-unbind-success"));
        } else {
            player.sendMessage(plugin.getMessageManager().getMessage("telegram-unbind-not-bound"));
        }
    }
    
    private void handleTelegramStatus(Player player) {
        if (plugin.getTelegramBindingManager().isPlayerBound(player.getUniqueId())) {
            String telegramId = plugin.getTelegramBindingManager().getPlayerTelegramId(player.getUniqueId());
            player.sendMessage(plugin.getMessageManager().getMessage("telegram-status-bound"));
            player.sendMessage(plugin.getMessageManager().getMessage("telegram-status-id", "id", telegramId));
        } else {
            player.sendMessage(plugin.getMessageManager().getMessage("telegram-status-not-bound"));
            player.sendMessage(plugin.getMessageManager().getMessage("telegram-status-usage"));
        }
    }
    
    private void handleTestTelegramCommand(CommandSender sender) {
        sender.sendMessage(plugin.getMessageManager().getMessage("telegram-test-header"));
        
        if (!plugin.getTelegramIntegration().isEnabled()) {
            sender.sendMessage(plugin.getMessageManager().getMessage("telegram-test-disabled"));
            return;
        }
        
        sender.sendMessage(plugin.getMessageManager().getMessage("telegram-test-enabled"));
        
        // Отправляем тестовое сообщение
        plugin.getTelegramIntegration().sendMessage(plugin.getMessageManager().getMessage("telegram-test-message",
                "time", new java.text.SimpleDateFormat("dd.MM.yyyy HH:mm:ss").format(new java.util.Date()),
                "sender", sender.getName()));
        
        sender.sendMessage(plugin.getMessageManager().getMessage("telegram-test-sent"));
    }
    
    private void sendHelpMessage(CommandSender sender) {
        sender.sendMessage(plugin.getMessageManager().getMessage("time-help-header"));
        sender.sendMessage(plugin.getMessageManager().getMessage("time-help-menu"));
        sender.sendMessage(plugin.getMessageManager().getMessage("time-help-status"));
        sender.sendMessage(plugin.getMessageManager().getMessage("time-help-stats"));
        sender.sendMessage(plugin.getMessageManager().getMessage("time-help-allstats"));
        sender.sendMessage(plugin.getMessageManager().getMessage("time-help-telegram"));
        if (sender.hasPermission("beaststaff.admin")) {
            sender.sendMessage(plugin.getMessageManager().getMessage("time-help-setstatus"));
            sender.sendMessage(plugin.getMessageManager().getMessage("time-help-testtelegram"));
        }
    }

    private int parseDaysOrDefault(CommandSender sender, String raw, int defaultValue) {
        Integer value = tryParseInt(raw);
        if (value == null) {
            sender.sendMessage(plugin.getMessageManager().getMessage("time-days-invalid", "value", raw));
            return -1;
        }
        if (value <= 0) {
            sender.sendMessage(plugin.getMessageManager().getMessage("time-days-invalid", "value", raw));
            return -1;
        }
        return value;
    }

    private Integer tryParseInt(String raw) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            return null;
        }
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        
        if (args.length == 1) {
            completions.add("menu");
            completions.add("status");
            completions.add("stats");
            completions.add("allstats");
            completions.add("telegram");
            if (sender.hasPermission("beaststaff.admin")) {
                completions.add("setstatus");
                completions.add("testtelegram");
            }
        } else if (args.length == 2) {
            if (args[0].equalsIgnoreCase("status")) {
                completions.addAll(plugin.getTimeTrackingManager().getAvailableStatuses());
            } else if (args[0].equalsIgnoreCase("stats") || args[0].equalsIgnoreCase("setstatus")) {
                List<String> players = new ArrayList<>();
                for (Player online : Bukkit.getOnlinePlayers()) {
                    String name = online.getName();
                    if (name.toLowerCase().startsWith(args[1].toLowerCase())) {
                        players.add(name);
                    }
                }
                return players;
            } else if (args[0].equalsIgnoreCase("allstats")) {
                completions.add("7");
                completions.add("30");
                completions.add("90");
            } else if (args[0].equalsIgnoreCase("telegram")) {
                completions.add("bind");
                completions.add("unbind");
                completions.add("status");
            }
        } else if (args.length >= 3) {
            if (args[0].equalsIgnoreCase("stats") && args.length == 3) {
                completions.add("7");
                completions.add("30");
                completions.add("90");
            } else if (args[0].equalsIgnoreCase("setstatus") && args.length == 3) {
                completions.addAll(plugin.getTimeTrackingManager().getAvailableStatuses());
            }
        }
        
        return completions.stream()
                .filter(s -> s.toLowerCase().startsWith(args[args.length - 1].toLowerCase()))
                .collect(Collectors.toList());
    }
}
