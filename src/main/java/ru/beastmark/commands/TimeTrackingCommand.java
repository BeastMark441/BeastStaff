package ru.beastmark.commands;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import ru.beastmark.BeastStaff;
import ru.beastmark.managers.TimeTrackingManager;

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
                if (!(sender instanceof Player)) {
                    sender.sendMessage("§c[BeastStaff] Эта команда только для игроков!");
                    return true;
                }
                handleMenuCommand((Player) sender);
                break;
                
            case "status":
                if (!(sender instanceof Player)) {
                    sender.sendMessage("§c[BeastStaff] Эта команда только для игроков!");
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage("§c[BeastStaff] Использование: /beaststaff status <статус>");
                    return true;
                }
                // Объединяем все аргументы после "status" в один статус
                String status = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
                handleStatusCommand((Player) sender, status);
                break;
                
            case "stats":
                if (args.length < 2) {
                    if (!(sender instanceof Player)) {
                        sender.sendMessage("§c[BeastStaff] Использование: /beaststaff stats <игрок> [дни]");
                        return true;
                    }
                    handleStatsCommand(sender, ((Player) sender).getName(), args.length > 2 ? Integer.parseInt(args[2]) : 7);
                } else {
                    if (!sender.hasPermission("beaststaff.admin")) {
                        sender.sendMessage("§c[BeastStaff] У вас нет прав для просмотра статистики других игроков!");
                        return true;
                    }
                    int days = args.length > 2 ? Integer.parseInt(args[2]) : 7;
                    handleStatsCommand(sender, args[1], days);
                }
                break;
                
            case "allstats":
                if (!sender.hasPermission("beaststaff.admin")) {
                    sender.sendMessage("§c[BeastStaff] У вас нет прав для использования этой команды!");
                    return true;
                }
                int days = args.length > 1 ? Integer.parseInt(args[1]) : 7;
                handleAllStatsCommand(sender, days);
                break;
                
            case "setstatus":
                if (!sender.hasPermission("beaststaff.admin")) {
                    sender.sendMessage("§c[BeastStaff] У вас нет прав для использования этой команды!");
                    return true;
                }
                if (args.length < 3) {
                    sender.sendMessage("§c[BeastStaff] Использование: /beaststaff setstatus <игрок> <статус>");
                    return true;
                }
                // Объединяем все аргументы после имени игрока в один статус
                String setStatus = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
                handleSetStatusCommand(sender, args[1], setStatus);
                break;
                
            case "telegram":
                if (!(sender instanceof Player)) {
                    sender.sendMessage("§c[BeastStaff] Эта команда только для игроков!");
                    return true;
                }
                handleTelegramCommand((Player) sender, args);
                break;
                
            case "testtelegram":
                if (!sender.hasPermission("beaststaff.admin")) {
                    sender.sendMessage("§c[BeastStaff] У вас нет прав для использования этой команды!");
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
            player.sendMessage("§c[BeastStaff] Вы не являетесь сотрудником!");
            return;
        }
        
        List<String> statuses = plugin.getTimeTrackingManager().getAvailableStatuses();
        String currentStatus = plugin.getTimeTrackingManager().getPlayerStatus(player.getUniqueId());
        
        player.sendMessage("§6[BeastStaff] §fМеню статусов:");
        player.sendMessage("§7Текущий статус: §f" + (currentStatus != null ? currentStatus : "Не установлен"));
        player.sendMessage("");
        
        for (int i = 0; i < statuses.size(); i++) {
            String status = statuses.get(i);
            String color = currentStatus != null && currentStatus.equals(status) ? "§a" : "§7";
            player.sendMessage(color + (i + 1) + ". " + status);
        }
        
        player.sendMessage("");
        player.sendMessage("§7Используйте: §f/beaststaff status <статус>");
    }
    
    private void handleStatusCommand(Player player, String status) {
        if (!plugin.getStaffManager().isStaffMember(player.getUniqueId())) {
            player.sendMessage("§c[BeastStaff] Вы не являетесь сотрудником!");
            return;
        }
        
        List<String> availableStatuses = plugin.getTimeTrackingManager().getAvailableStatuses();
        if (!availableStatuses.contains(status)) {
            player.sendMessage("§c[BeastStaff] Неизвестный статус: " + status);
            player.sendMessage("§7Доступные статусы: " + String.join(", ", availableStatuses));
            return;
        }
        
        plugin.getTimeTrackingManager().startSession(player, status);
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
                sender.sendMessage("§c[BeastStaff] Игрок " + playerName + " не найден!");
                return;
            }
        }
        
        if (!plugin.getStaffManager().isStaffMember(target.getUniqueId())) {
            sender.sendMessage("§c[BeastStaff] Игрок " + playerName + " не является сотрудником!");
            return;
        }
        
        Map<String, Long> stats = plugin.getTimeTrackingManager().getPlayerStats(target.getUniqueId(), days);
        String currentStatus = plugin.getTimeTrackingManager().getPlayerStatus(target.getUniqueId());
        
        sender.sendMessage("§6[BeastStaff] §fСтатистика " + playerName + " за " + days + " дней:");
        sender.sendMessage("§7Текущий статус: §f" + (currentStatus != null ? currentStatus : "Не установлен"));
        sender.sendMessage("§7Общее время: §f" + formatTime(stats.getOrDefault("total", 0L)));
        sender.sendMessage("");
        
        for (Map.Entry<String, Long> entry : stats.entrySet()) {
            if (!entry.getKey().equals("total")) {
                sender.sendMessage("§7" + entry.getKey() + ": §f" + formatTime(entry.getValue()));
            }
        }
    }
    
    private void handleAllStatsCommand(CommandSender sender, int days) {
        Map<String, Long> stats = plugin.getTimeTrackingManager().getAllStaffStats(days);
        
        sender.sendMessage("§6[BeastStaff] §fОбщая статистика персонала за " + days + " дней:");
        sender.sendMessage("§7Общее время: §f" + formatTime(stats.getOrDefault("total", 0L)));
        sender.sendMessage("");
        
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
                sender.sendMessage("§c[BeastStaff] Игрок " + playerName + " не найден!");
                return;
            }
        }
        
        if (!plugin.getStaffManager().isStaffMember(target.getUniqueId())) {
            sender.sendMessage("§c[BeastStaff] Игрок " + playerName + " не является сотрудником!");
            return;
        }
        
        List<String> availableStatuses = plugin.getTimeTrackingManager().getAvailableStatuses();
        if (!availableStatuses.contains(status)) {
            sender.sendMessage("§c[BeastStaff] Неизвестный статус: " + status);
            sender.sendMessage("§7Доступные статусы: " + String.join(", ", availableStatuses));
            return;
        }
        
        plugin.getTimeTrackingManager().startSession(target, status);
        sender.sendMessage("§a[BeastStaff] Статус игрока " + playerName + " изменен на: " + status);
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
            player.sendMessage("§c[BeastStaff] Вы не являетесь сотрудником!");
            return;
        }
        
        if (args.length < 2) {
            player.sendMessage("§6[BeastStaff] §fКоманды Telegram:");
            player.sendMessage("§7/beaststaff telegram bind <ID> §8- Привязать Telegram аккаунт");
            player.sendMessage("§7/beaststaff telegram unbind §8- Отвязать Telegram аккаунт");
            player.sendMessage("§7/beaststaff telegram status §8- Статус привязки");
            return;
        }
        
        switch (args[1].toLowerCase()) {
            case "bind":
                if (args.length < 3) {
                    player.sendMessage("§c[BeastStaff] Использование: /beaststaff telegram bind <Telegram ID>");
                    player.sendMessage("§7Для получения ID отправьте боту @userinfobot команду /start");
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
                player.sendMessage("§c[BeastStaff] Неизвестная команда. Используйте: bind, unbind, status");
                break;
        }
    }
    
    private void handleTelegramBind(Player player, String telegramId) {
        if (plugin.getTelegramBindingManager().bindPlayer(player.getUniqueId(), telegramId)) {
            player.sendMessage("§a[BeastStaff] Ваш Telegram аккаунт успешно привязан!");
            player.sendMessage("§7Теперь вы будете получать персональные уведомления.");
        } else {
            player.sendMessage("§c[BeastStaff] Ошибка привязки Telegram аккаунта!");
        }
    }
    
    private void handleTelegramUnbind(Player player) {
        if (plugin.getTelegramBindingManager().unbindPlayer(player.getUniqueId())) {
            player.sendMessage("§a[BeastStaff] Ваш Telegram аккаунт отвязан!");
        } else {
            player.sendMessage("§c[BeastStaff] У вас нет привязанного Telegram аккаунта!");
        }
    }
    
    private void handleTelegramStatus(Player player) {
        if (plugin.getTelegramBindingManager().isPlayerBound(player.getUniqueId())) {
            String telegramId = plugin.getTelegramBindingManager().getPlayerTelegramId(player.getUniqueId());
            player.sendMessage("§a[BeastStaff] Ваш Telegram аккаунт привязан!");
            player.sendMessage("§7ID: " + telegramId);
        } else {
            player.sendMessage("§e[BeastStaff] У вас нет привязанного Telegram аккаунта.");
            player.sendMessage("§7Используйте: /beaststaff telegram bind <ID>");
        }
    }
    
    private void handleTestTelegramCommand(CommandSender sender) {
        sender.sendMessage("§6[BeastStaff] §fТестирование Telegram интеграции:");
        
        if (!plugin.getTelegramIntegration().isEnabled()) {
            sender.sendMessage("§cTelegram интеграция отключена!");
            return;
        }
        
        sender.sendMessage("§aTelegram интеграция включена!");
        
        // Отправляем тестовое сообщение
        plugin.getTelegramIntegration().sendMessage("🧪 *Тестовое сообщение*\n" +
                "Отправлено: " + new java.text.SimpleDateFormat("dd.MM.yyyy HH:mm:ss").format(new java.util.Date()) + "\n" +
                "Отправитель: " + sender.getName());
        
        sender.sendMessage("§aТестовое сообщение отправлено в Telegram канал!");
    }
    
    private void sendHelpMessage(CommandSender sender) {
        sender.sendMessage("§6[BeastStaff] §fКоманды учёта времени:");
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
                return Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
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
