package ru.beastmark;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import ru.beastmark.commands.StaffCommand;
import ru.beastmark.commands.TimeTrackingCommand;
import ru.beastmark.listeners.StaffListener;
import ru.beastmark.listeners.TimeTrackingListener;
import ru.beastmark.managers.StaffManager;
import ru.beastmark.managers.TimeTrackingManager;
import ru.beastmark.managers.DatabaseManager;
import ru.beastmark.placeholders.TimeTrackingExpansion;
import ru.beastmark.integrations.TelegramIntegration;
import ru.beastmark.managers.TelegramBindingManager;

import java.util.ArrayList;
import java.util.List;

public class BeastStaff extends JavaPlugin {
    
    private static BeastStaff instance;
    private StaffManager staffManager;
    private TimeTrackingManager timeTrackingManager;
    private DatabaseManager databaseManager;
    private TelegramIntegration telegramIntegration;
    private TelegramBindingManager telegramBindingManager;
    
    @Override
    public void onEnable() {
        instance = this;
        
        // Сохраняем конфигурацию по умолчанию
        saveDefaultConfig();
        
        // Инициализируем менеджер базы данных
        databaseManager = new DatabaseManager(this);
        
        // Инициализируем менеджер персонала
        staffManager = new StaffManager(this);
        
        // Инициализируем менеджер учёта времени
        timeTrackingManager = new TimeTrackingManager(this);
        
        // Инициализируем Telegram интеграцию
        telegramIntegration = new TelegramIntegration(this);
        
        // Инициализируем менеджер привязок Telegram
        telegramBindingManager = new TelegramBindingManager(this);
        
        // Регистрируем команды
        StaffCommand staffCommand = new StaffCommand(this);
        TimeTrackingCommand timeCommand = new TimeTrackingCommand(this);
        
        // Создаем общий обработчик команд
        CommandExecutor mainCommandExecutor = new CommandExecutor() {
            @Override
            public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
                if (args.length == 0) {
                    return staffCommand.onCommand(sender, command, label, args);
                }
                
                // Перенаправляем команды учёта времени
                if (args[0].equalsIgnoreCase("menu") || args[0].equalsIgnoreCase("status") ||
                    args[0].equalsIgnoreCase("stats") || args[0].equalsIgnoreCase("allstats") ||
                    args[0].equalsIgnoreCase("setstatus") || args[0].equalsIgnoreCase("telegram") ||
                    args[0].equalsIgnoreCase("testtelegram")) {
                    return timeCommand.onCommand(sender, command, label, args);
                }
                
                // Остальные команды обрабатывает StaffCommand
                return staffCommand.onCommand(sender, command, label, args);
            }
        };
        
        TabCompleter mainTabCompleter = new TabCompleter() {
            @Override
            public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
                if (args.length == 0) {
                    // Показываем все доступные команды
                    List<String> allCommands = new ArrayList<>();
                    
                    // Команды управления персоналом
                    allCommands.add("add");
                    allCommands.add("remove");
                    allCommands.add("list");
                    allCommands.add("info");
                    allCommands.add("reload");
                    
                    // Команды учёта времени
                    allCommands.add("menu");
                    allCommands.add("status");
                    allCommands.add("stats");
                    allCommands.add("allstats");
                    allCommands.add("telegram");
                    
                    // Административные команды
                    allCommands.add("setstatus");
                    allCommands.add("testtelegram");
                    
                    return allCommands;
                }
                
                // Перенаправляем автодополнение команд учёта времени
                if (args[0].equalsIgnoreCase("menu") || args[0].equalsIgnoreCase("status") ||
                    args[0].equalsIgnoreCase("stats") || args[0].equalsIgnoreCase("allstats") ||
                    args[0].equalsIgnoreCase("setstatus") || args[0].equalsIgnoreCase("telegram") ||
                    args[0].equalsIgnoreCase("testtelegram")) {
                    return timeCommand.onTabComplete(sender, command, alias, args);
                }
                
                // Остальные команды обрабатывает StaffCommand
                return staffCommand.onTabComplete(sender, command, alias, args);
            }
        };
        
        // Регистрируем основную команду
        getCommand("beaststaff").setExecutor(mainCommandExecutor);
        getCommand("beaststaff").setTabCompleter(mainTabCompleter);
        
        // Регистрируем сокращенную команду как алиас
        getCommand("bs").setExecutor(mainCommandExecutor);
        getCommand("bs").setTabCompleter(mainTabCompleter);
        
        // Регистрируем слушатели событий
        getServer().getPluginManager().registerEvents(new StaffListener(this), this);
        getServer().getPluginManager().registerEvents(new TimeTrackingListener(this), this);
        
        // Регистрируем PlaceholderAPI расширение
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new TimeTrackingExpansion(this).register();
            getLogger().info("PlaceholderAPI расширение зарегистрировано!");
        }
        
        // Проверяем Telegram интеграцию
        if (telegramIntegration.isEnabled()) {
            getLogger().info("Telegram интеграция активирована!");
            // Отправляем тестовое сообщение
            telegramIntegration.sendMessage("🚀 *BeastStaff загружен!*\nПлагин успешно запущен и готов к работе.");
        }
        

        
        getLogger().info("BeastStaff успешно загружен!");
    }
    
    @Override
    public void onDisable() {
        // Сохраняем данные перед выгрузкой
        if (timeTrackingManager != null) {
            timeTrackingManager.saveAllSessions();
        }
        if (staffManager != null) {
            staffManager.saveData();
        }
        if (telegramBindingManager != null) {
            telegramBindingManager.saveBindings();
        }
        if (databaseManager != null) {
            databaseManager.closeConnection();
        }
        
        // Очищаем команды
        try {
            getCommand("beaststaff").setExecutor(null);
            getCommand("beaststaff").setTabCompleter(null);
            getCommand("bs").setExecutor(null);
            getCommand("bs").setTabCompleter(null);
        } catch (Exception e) {
            getLogger().warning("Ошибка при очистке команд: " + e.getMessage());
        }
        
        getLogger().info("BeastStaff успешно выгружен!");
    }
    
    public static BeastStaff getInstance() {
        return instance;
    }
    
    public StaffManager getStaffManager() {
        return staffManager;
    }
    
    public TimeTrackingManager getTimeTrackingManager() {
        return timeTrackingManager;
    }
    
    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }
    
    public TelegramIntegration getTelegramIntegration() {
        return telegramIntegration;
    }
    
    public TelegramBindingManager getTelegramBindingManager() {
        return telegramBindingManager;
    }
}
