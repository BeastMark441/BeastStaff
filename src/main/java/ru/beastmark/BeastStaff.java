package ru.beastmark;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
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
import ru.beastmark.litebans.LiteBansManager;
import ru.beastmark.telegram.TelegramBot;
import ru.beastmark.telegram.commands.StatsCommand;
import ru.beastmark.telegram.commands.RecentBansCommand;
import ru.beastmark.telegram.commands.StatusCommand;
import ru.beastmark.telegram.commands.SetStatusCommand;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import ru.beastmark.managers.MessageManager;

public class BeastStaff extends JavaPlugin {
    
    private static BeastStaff instance;
    private StaffManager staffManager;
    private TimeTrackingManager timeTrackingManager;
    private DatabaseManager databaseManager;
    private TelegramIntegration telegramIntegration;
    private TelegramBindingManager telegramBindingManager;
    private LiteBansManager liteBansManager;
    private MessageManager messageManager;
    private TelegramBot telegramBot;
    private Map<String, Object> telegramCommands;
    
    @Override
    public void onEnable() {
        instance = this;
        
        // Сохраняем конфигурацию по умолчанию
        saveDefaultConfig();
        
        // Инициализируем менеджер сообщений
        messageManager = new MessageManager(this);

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
        
        // Инициализируем LiteBans интеграцию
        if (getConfig().getBoolean("integrations.litebans.enabled", false)) {
            liteBansManager = new LiteBansManager(this);
            if (liteBansManager.isEnabled()) {
                getLogger().info("✓ LiteBans интеграция включена");
            }
        }
        
        // Инициализируем Telegram команды
        initializeTelegramCommands();
        
        // Запускаем Telegram бота для обработки команд
        if (telegramIntegration.isEnabled() && getConfig().getBoolean("integrations.telegram.commands.enabled", true)) {
            telegramBot = new TelegramBot(this, telegramIntegration);
            telegramBot.start();
            getLogger().info("✓ Telegram бот запущен для обработки команд");
        }
        
        // Регистрируем команды
        StaffCommand staffCommand = new StaffCommand(this);
        TimeTrackingCommand timeCommand = new TimeTrackingCommand(this);
        
        // Создаем общий обработчик команд
        CommandExecutor mainCommandExecutor = new CommandExecutor() {
            @Override
            public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
                try {
                    // Логирование для отладки
                    if (getConfig().getBoolean("debug.enabled", false)) {
                        getLogger().info("Команда выполнена: " + label + " с аргументами: " + 
                            (args.length > 0 ? String.join(" ", args) : "нет"));
                    }
                    
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
                } catch (Exception e) {
                    getLogger().severe("Ошибка при выполнении команды " + label + ": " + e.getMessage());
                    e.printStackTrace();
                    sender.sendMessage(getMessageManager().getMessage("command-exception"));
                    return true;
                }
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
        PluginCommand beaststaffCommand = getCommand("beaststaff");
        if (beaststaffCommand != null) {
            beaststaffCommand.setExecutor(mainCommandExecutor);
            beaststaffCommand.setTabCompleter(mainTabCompleter);
            getLogger().info("Команда /beaststaff зарегистрирована");
        } else {
            getLogger().severe("ОШИБКА: Команда 'beaststaff' не найдена в plugin.yml!");
        }
        
        // Регистрируем сокращенную команду как алиас
        PluginCommand bsCommand = getCommand("bs");
        if (bsCommand != null) {
            bsCommand.setExecutor(mainCommandExecutor);
            bsCommand.setTabCompleter(mainTabCompleter);
            getLogger().info("Команда /bs зарегистрирована");
        } else {
            getLogger().severe("ОШИБКА: Команда 'bs' не найдена в plugin.yml!");
        }
        
        // Регистрируем слушатели событий
        getServer().getPluginManager().registerEvents(new StaffListener(this), this);
        getServer().getPluginManager().registerEvents(new TimeTrackingListener(this), this);
        
        // Регистрируем PlaceholderAPI расширение
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new TimeTrackingExpansion(this).register();
            timeTrackingManager.startPlaceholderCache();
            getLogger().info("PlaceholderAPI расширение зарегистрировано!");
        }
        
        // Проверяем Telegram интеграцию
        if (telegramIntegration.isEnabled()) {
            getLogger().info("Telegram интеграция активирована!");
            // Отправляем тестовое сообщение только если chat-id указан
            if (telegramIntegration.hasChatId()) {
                getLogger().info("Отправка тестового сообщения в Telegram...");
                telegramIntegration.sendMessage("🚀 *BeastStaff загружен!*\nПлагин успешно запущен и готов к работе.");
            } else {
                getLogger().info("Chat ID не указан - бот работает только для личных сообщений");
            }
        }
        

        
        getLogger().info("BeastStaff успешно загружен!");
    }
    
    @Override
    public void onDisable() {
        // Сохраняем данные перед выгрузкой
        if (timeTrackingManager != null) {
            timeTrackingManager.saveAllSessions();
            timeTrackingManager.flushTimeTrackingFileSaveSync();
            timeTrackingManager.stopPlaceholderCache();
        }
        if (staffManager != null) {
            staffManager.flushSaveSync();
        }
        if (telegramBindingManager != null) {
            telegramBindingManager.flushSaveSync();
        }
        if (telegramBot != null) {
            telegramBot.stop();
        }
        if (liteBansManager != null) {
            liteBansManager.shutdown();
        }
        if (databaseManager != null) {
            databaseManager.closeConnection();
        }
        
        // Очищаем команды
        try {
            PluginCommand beaststaffCmd = getCommand("beaststaff");
            if (beaststaffCmd != null) {
                beaststaffCmd.setExecutor(null);
                beaststaffCmd.setTabCompleter(null);
            }
            PluginCommand bsCmd = getCommand("bs");
            if (bsCmd != null) {
                bsCmd.setExecutor(null);
                bsCmd.setTabCompleter(null);
            }
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

    public MessageManager getMessageManager() {
        return messageManager;
    }
    
    public TelegramIntegration getTelegramIntegration() {
        return telegramIntegration;
    }
    
    public TelegramBindingManager getTelegramBindingManager() {
        return telegramBindingManager;
    }
    
    public LiteBansManager getLiteBansManager() {
        return liteBansManager;
    }
    
    private void initializeTelegramCommands() {
        telegramCommands = new HashMap<>();
        
        if (liteBansManager != null && liteBansManager.isEnabled()) {
            telegramCommands.put("bs_ban_stats", new StatsCommand(this, liteBansManager));
            telegramCommands.put("bs_recent_bans", new RecentBansCommand(this, liteBansManager));
        }
        
        telegramCommands.put("bs_status", new StatusCommand(this));
        telegramCommands.put("bs_set_status", new SetStatusCommand(this));
        
        getLogger().info("Telegram команды инициализированы: " + telegramCommands.size());
    }
    
    public Map<String, Object> getTelegramCommands() {
        return telegramCommands;
    }
}
