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
import java.util.stream.Collectors;

public class StaffCommand implements CommandExecutor, TabCompleter {
    
    private final BeastStaff plugin;
    
    public StaffCommand(BeastStaff plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("beaststaff.admin")) {
            sender.sendMessage("§c[BeastStaff] У вас нет прав для использования этой команды!");
            return true;
        }
        
        if (args.length == 0) {
            sendHelpMessage(sender);
            return true;
        }
        
        switch (args[0].toLowerCase()) {
            case "add":
                if (args.length < 3) {
                    sender.sendMessage("§c[BeastStaff] Использование: /beaststaff add <игрок> <ранг>");
                    return true;
                }
                // Объединяем все аргументы после имени игрока в один ранг
                String rank = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
                handleAddCommand(sender, args[1], rank);
                break;
                
            case "remove":
                if (args.length < 2) {
                    sender.sendMessage("§c[BeastStaff] Использование: /beaststaff remove <игрок>");
                    return true;
                }
                handleRemoveCommand(sender, args[1]);
                break;
                
            case "list":
                handleListCommand(sender);
                break;
                
            case "info":
                if (args.length < 2) {
                    sender.sendMessage("§c[BeastStaff] Использование: /beaststaff info <игрок>");
                    return true;
                }
                handleInfoCommand(sender, args[1]);
                break;
                
            case "reload":
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
                sender.sendMessage("§c[BeastStaff] Игрок " + playerName + " не найден!");
                return;
            }
        }
        
        // Проверяем, что ранг существует в конфигурации
        List<String> availableRanks = plugin.getConfig().getStringList("ranks.available");
        if (!availableRanks.contains(rank)) {
            sender.sendMessage("§c[BeastStaff] Неизвестный ранг: " + rank);
            sender.sendMessage("§7Доступные ранги: " + String.join(", ", availableRanks));
            return;
        }
        
        StaffManager staffManager = plugin.getStaffManager();
        if (staffManager.isStaffMember(target.getUniqueId())) {
            sender.sendMessage("§c[BeastStaff] Игрок " + playerName + " уже является персоналом!");
            return;
        }
        
        staffManager.addStaffMember(target, rank);
        sender.sendMessage("§a[BeastStaff] Игрок " + playerName + " добавлен в персонал с рангом " + rank + "!");
        target.sendMessage("§a[BeastStaff] Вы были добавлены в персонал с рангом " + rank + "!");
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
                sender.sendMessage("§c[BeastStaff] Игрок " + playerName + " не найден!");
                return;
            }
        }
        
        StaffManager staffManager = plugin.getStaffManager();
        if (!staffManager.isStaffMember(target.getUniqueId())) {
            sender.sendMessage("§c[BeastStaff] Игрок " + playerName + " не является персоналом!");
            return;
        }
        
        staffManager.removeStaffMember(target.getUniqueId());
        sender.sendMessage("§a[BeastStaff] Игрок " + playerName + " удален из персонала!");
        target.sendMessage("§c[BeastStaff] Вы были удалены из персонала!");
    }
    
    private void handleListCommand(CommandSender sender) {
        StaffManager staffManager = plugin.getStaffManager();
        var staffMembers = staffManager.getAllStaffMembers();
        
        if (staffMembers.isEmpty()) {
            sender.sendMessage("§e[BeastStaff] Список персонала пуст!");
            return;
        }
        
        sender.sendMessage("§6[BeastStaff] Список персонала:");
        for (StaffManager.StaffMember member : staffMembers.values()) {
            sender.sendMessage("§7- " + member.getName() + " §8(" + member.getRank() + ")");
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
                sender.sendMessage("§c[BeastStaff] Игрок " + playerName + " не найден!");
                return;
            }
        }
        
        StaffManager staffManager = plugin.getStaffManager();
        if (!staffManager.isStaffMember(target.getUniqueId())) {
            sender.sendMessage("§c[BeastStaff] Игрок " + playerName + " не является персоналом!");
            return;
        }
        
        StaffManager.StaffMember member = staffManager.getStaffMember(target.getUniqueId());
        sender.sendMessage("§6[BeastStaff] Информация о " + member.getName() + ":");
        sender.sendMessage("§7Ранг: §f" + member.getRank());
        sender.sendMessage("§7Дата добавления: §f" + new java.text.SimpleDateFormat("dd.MM.yyyy HH:mm:ss")
                .format(new java.util.Date(member.getJoinDate())));
    }
    
    private void handleReloadCommand(CommandSender sender) {
        plugin.reloadConfig();
        plugin.getStaffManager().loadData();
        plugin.getTimeTrackingManager().reloadAvailableStatuses();
        plugin.getTelegramIntegration().reload();
        plugin.getTelegramBindingManager().reloadBindings();
        plugin.getDatabaseManager().reload();
        sender.sendMessage("§a[BeastStaff] Конфигурация перезагружена!");
    }
    
    private void sendHelpMessage(CommandSender sender) {
        sender.sendMessage("§6[BeastStaff] Доступные команды:");
        sender.sendMessage("§7/beaststaff add <игрок> <ранг> §8- Добавить игрока в персонал");
        sender.sendMessage("§7/beaststaff remove <игрок> §8- Удалить игрока из персонала");
        sender.sendMessage("§7/beaststaff list §8- Показать список персонала");
        sender.sendMessage("§7/beaststaff info <игрок> §8- Информация об игроке");
        sender.sendMessage("§7/beaststaff reload §8- Перезагрузить конфигурацию");
        sender.sendMessage("§7/beaststaff menu §8- Открыть меню статусов");
        sender.sendMessage("§7/beaststaff status <статус> §8- Изменить статус");
        sender.sendMessage("§7/beaststaff stats [игрок] [дни] §8- Просмотр статистики");
        sender.sendMessage("§7/beaststaff allstats [дни] §8- Общая статистика персонала");
        sender.sendMessage("§7/beaststaff telegram §8- Управление Telegram привязками");
        if (sender.hasPermission("beaststaff.admin")) {
            sender.sendMessage("§7/beaststaff setstatus <игрок> <статус> §8- Установить статус игрока");
            sender.sendMessage("§7/beaststaff testtelegram §8- Тестирование Telegram интеграции");
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
