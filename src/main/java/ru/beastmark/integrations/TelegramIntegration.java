package ru.beastmark.integrations;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import ru.beastmark.BeastStaff;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.logging.Level;

public class TelegramIntegration {
    
    private final BeastStaff plugin;
    private String botToken;
    private String chatId;
    private boolean enabled;
    private boolean notifyStatusChanges;
    private boolean notifyJoinsQuits;
    private boolean adminOnly;
    private boolean personalNotifications;
    private boolean sendToChannel;
    
    public TelegramIntegration(BeastStaff plugin) {
        this.plugin = plugin;
        FileConfiguration config = plugin.getConfig();
        
        this.enabled = config.getBoolean("integrations.telegram.enabled", false);
        this.botToken = config.getString("integrations.telegram.bot-token", "");
        this.chatId = config.getString("integrations.telegram.chat-id", "");
        this.notifyStatusChanges = config.getBoolean("integrations.telegram.notify-status-changes", true);
        this.notifyJoinsQuits = config.getBoolean("integrations.telegram.notify-joins-quits", true);
        this.adminOnly = config.getBoolean("integrations.telegram.admin-only", false);
        this.personalNotifications = config.getBoolean("integrations.telegram.personal-notifications", true);
        this.sendToChannel = config.getBoolean("integrations.telegram.send-to-channel", true);
        
        // Проверка на плейсхолдеры
        if (botToken.equals("YOUR_BOT_TOKEN") || botToken.isEmpty()) {
            botToken = "";
        }
        // Chat ID теперь опциональный - убираем проверку на плейсхолдер
        if (chatId != null && chatId.equals("YOUR_CHAT_ID")) {
            chatId = "";
        }
        
        if (enabled) {
            boolean hasToken = !botToken.isEmpty();
            
            if (!hasToken) {
                plugin.getLogger().warning("═══════════════════════════════════════");
                plugin.getLogger().warning("⚠️ Telegram интеграция включена, но токен не настроен!");
                plugin.getLogger().warning("Для работы необходимо:");
                plugin.getLogger().warning("1. Получить токен бота от @BotFather");
                plugin.getLogger().warning("2. Указать его в config.yml: integrations.telegram.bot-token");
                plugin.getLogger().warning("═══════════════════════════════════════");
            }
            
            plugin.getLogger().info("Telegram интеграция инициализирована:");
            plugin.getLogger().info("- Bot Token: " + (hasToken ? "✅ НАСТРОЕН" : "❌ НЕ НАСТРОЕН"));
            plugin.getLogger().info("- Chat ID: " + (chatId == null || chatId.isEmpty() ? "ℹ️ Не указан (бот работает только для личных сообщений)" : "✅ " + chatId));
            plugin.getLogger().info("- Notify Status Changes: " + notifyStatusChanges);
            plugin.getLogger().info("- Notify Joins/Quits: " + notifyJoinsQuits);
            plugin.getLogger().info("- Personal Notifications: " + personalNotifications);
            plugin.getLogger().info("- Send to Channel: " + sendToChannel);
            
            if (hasToken && (chatId == null || chatId.isEmpty())) {
                plugin.getLogger().info("ℹ️ Chat ID не указан - бот будет работать только для личных сообщений");
                plugin.getLogger().info("ℹ️ Для отправки в канал/группу укажите chat-id в config.yml");
            }
        }
    }
    
    public boolean isEnabled() {
        return enabled && !botToken.isEmpty();
    }
    
    public boolean hasChatId() {
        return chatId != null && !chatId.isEmpty();
    }
    
    public String getBotToken() {
        return botToken;
    }
    
    public String getChatId() {
        return chatId;
    }
    
    public void sendMessage(String message) {
        if (!isEnabled()) return;
        
        // Если chat-id не указан, не отправляем в канал
        if (!hasChatId()) {
            if (plugin.getConfig().getBoolean("debug.log-telegram", false)) {
                plugin.getLogger().info("Chat ID не указан - сообщение не отправлено в канал");
            }
            return;
        }
        
        try {
            String urlString = "https://api.telegram.org/bot" + botToken + "/sendMessage";
            URL url = URI.create(urlString).toURL();
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            connection.setDoOutput(true);
            
            // Используем chat_id напрямую
            String postData = "chat_id=" + chatId + "&text=" + URLEncoder.encode(message, StandardCharsets.UTF_8) + "&parse_mode=Markdown";
            
            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = postData.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }
            
            int responseCode = connection.getResponseCode();
            if (responseCode != 200) {
                // Читаем ответ для отладки
                String errorMessage = "";
                try (BufferedReader br = new BufferedReader(new InputStreamReader(connection.getErrorStream()))) {
                    String line;
                    StringBuilder response = new StringBuilder();
                    while ((line = br.readLine()) != null) {
                        response.append(line);
                    }
                    errorMessage = response.toString();
                } catch (Exception e) {
                    // Игнорируем ошибки чтения
                }
                
                // Парсим ошибку
                if (errorMessage.contains("chat not found")) {
                    plugin.getLogger().warning("═══════════════════════════════════════");
                    plugin.getLogger().warning("❌ ОШИБКА: Chat ID не найден!");
                    plugin.getLogger().warning("Проверьте:");
                    plugin.getLogger().warning("1. Правильность Chat ID в config.yml");
                    plugin.getLogger().warning("2. Бот добавлен в группу/канал");
                    plugin.getLogger().warning("3. Бот имеет права на отправку сообщений");
                    plugin.getLogger().warning("Текущий Chat ID: " + chatId);
                    plugin.getLogger().warning("═══════════════════════════════════════");
                } else if (errorMessage.contains("Unauthorized")) {
                    plugin.getLogger().warning("❌ ОШИБКА: Неверный Bot Token!");
                    plugin.getLogger().warning("Проверьте токен бота в config.yml");
                } else {
                    plugin.getLogger().warning("Ошибка отправки сообщения в Telegram: " + responseCode);
                    if (!errorMessage.isEmpty()) {
                        plugin.getLogger().warning("Ответ Telegram: " + errorMessage);
                    }
                }
            } else {
                if (plugin.getConfig().getBoolean("debug.log-telegram", false)) {
                    plugin.getLogger().info("Сообщение успешно отправлено в Telegram канал");
                }
            }
            
            connection.disconnect();
            
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Ошибка отправки сообщения в Telegram", e);
        }
    }
    
    public void sendPersonalMessage(String telegramId, String message) {
        if (!isEnabled()) return;
        
        try {
            String urlString = "https://api.telegram.org/bot" + botToken + "/sendMessage";
            URL url = URI.create(urlString).toURL();
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            connection.setDoOutput(true);
            
            String postData = "chat_id=" + telegramId + "&text=" + URLEncoder.encode(message, StandardCharsets.UTF_8) + "&parse_mode=Markdown";
            
            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = postData.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }
            
            int responseCode = connection.getResponseCode();
            if (responseCode != 200) {
                plugin.getLogger().warning("Ошибка отправки персонального сообщения в Telegram: " + responseCode);
                // Читаем ответ для отладки
                try (BufferedReader br = new BufferedReader(new InputStreamReader(connection.getErrorStream()))) {
                    String line;
                    StringBuilder response = new StringBuilder();
                    while ((line = br.readLine()) != null) {
                        response.append(line);
                    }
                    plugin.getLogger().warning("Ответ Telegram (персональное): " + response.toString());
                }
            } else {
                plugin.getLogger().info("Персональное сообщение успешно отправлено в Telegram");
            }
            
            connection.disconnect();
            
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Ошибка отправки персонального сообщения в Telegram", e);
        }
    }
    
    public void notifyStatusChange(Player player, String oldStatus, String newStatus) {
        if (!isEnabled() || !notifyStatusChanges) return;
        
        plugin.getLogger().info("Отправка уведомления об изменении статуса для " + player.getName());
        
        String message = String.format("🔄 *Изменение статуса*\n" +
                "Игрок: %s\n" +
                "Старый статус: %s\n" +
                "Новый статус: %s", 
                player.getName(), 
                oldStatus != null ? oldStatus : "Не установлен", 
                newStatus);
        
        // Отправляем в общий канал
        if (sendToChannel && hasChatId()) {
            if (plugin.getConfig().getBoolean("debug.log-telegram", false)) {
                plugin.getLogger().info("Отправка в общий канал: " + sendToChannel);
            }
            sendMessage(message);
        }
        
        // Отправляем персональное уведомление игроку
        if (personalNotifications && plugin.getTelegramBindingManager() != null) {
            String playerTelegramId = plugin.getTelegramBindingManager().getPlayerTelegramId(player.getUniqueId());
            plugin.getLogger().info("Telegram ID игрока " + player.getName() + ": " + playerTelegramId);
            if (playerTelegramId != null) {
                sendPersonalMessage(playerTelegramId, message);
            }
        }
    }
    
    public void notifyPlayerJoin(Player player) {
        if (!isEnabled() || !notifyJoinsQuits) return;
        
        String message = String.format("✅ *Вход в игру*\n" +
                "Игрок: %s\n" +
                "Ранг: %s", 
                player.getName(), 
                getPlayerRank(player));
        
        // Отправляем в общий канал
        if (sendToChannel && hasChatId()) {
            sendMessage(message);
        }
        
        // Отправляем персональное уведомление игроку
        if (personalNotifications && plugin.getTelegramBindingManager() != null) {
            String playerTelegramId = plugin.getTelegramBindingManager().getPlayerTelegramId(player.getUniqueId());
            if (playerTelegramId != null) {
                sendPersonalMessage(playerTelegramId, message);
            }
        }
    }
    
    public void notifyPlayerQuit(Player player) {
        if (!isEnabled() || !notifyJoinsQuits) return;
        
        String message = String.format("❌ *Выход из игры*\n" +
                "Игрок: %s\n" +
                "Ранг: %s", 
                player.getName(), 
                getPlayerRank(player));
        
        // Отправляем в общий канал
        if (sendToChannel && hasChatId()) {
            sendMessage(message);
        }
        
        // Отправляем персональное уведомление игроку
        if (personalNotifications && plugin.getTelegramBindingManager() != null) {
            String playerTelegramId = plugin.getTelegramBindingManager().getPlayerTelegramId(player.getUniqueId());
            if (playerTelegramId != null) {
                sendPersonalMessage(playerTelegramId, message);
            }
        }
    }
    
    private String getPlayerRank(Player player) {
        if (plugin.getStaffManager().isStaffMember(player.getUniqueId())) {
            var member = plugin.getStaffManager().getStaffMember(player.getUniqueId());
            return member != null ? member.getRank() : "Неизвестно";
        }
        return "Не персонал";
    }
    
    public void sendStatusCommand(Player player, String status) {
        if (!isEnabled()) return;
        
        String message = String.format("📊 *Команда статуса*\n" +
                "Игрок: %s\n" +
                "Статус: %s", 
                player.getName(), 
                status);
        
        sendMessage(message);
    }
    
    public void sendStatsCommand(Player player, int days) {
        if (!isEnabled()) return;
        
        var stats = plugin.getTimeTrackingManager().getPlayerStats(player.getUniqueId(), days);
        long totalTime = stats.getOrDefault("total", 0L);
        
        String message = String.format("📈 *Статистика за %d дней*\n" +
                "Игрок: %s\n" +
                "Общее время: %s", 
                days, 
                player.getName(), 
                formatTime(totalTime));
        
        sendMessage(message);
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
    
    /**
     * Обработать команду Telegram
     * @param telegramId ID пользователя Telegram
     * @param command Команда (например, "bs_ban_stats")
     * @param args Аргументы команды
     * @return Результат выполнения команды
     */
    public String handleCommand(String telegramId, String command, String[] args) {
        if (!isEnabled()) {
            return "❌ Telegram интеграция не включена";
        }
        
        Map<String, Object> commands = plugin.getTelegramCommands();
        if (commands == null || commands.isEmpty()) {
            return "❌ Команды не инициализированы";
        }
        
        Object commandHandler = commands.get(command);
        if (commandHandler == null) {
            return "❌ Неизвестная команда: " + command;
        }
        
        try {
            if (commandHandler instanceof ru.beastmark.telegram.commands.StatsCommand) {
                return ((ru.beastmark.telegram.commands.StatsCommand) commandHandler).handle(args);
            } else if (commandHandler instanceof ru.beastmark.telegram.commands.RecentBansCommand) {
                return ((ru.beastmark.telegram.commands.RecentBansCommand) commandHandler).handle(args);
            } else if (commandHandler instanceof ru.beastmark.telegram.commands.StatusCommand) {
                return ((ru.beastmark.telegram.commands.StatusCommand) commandHandler).handle(telegramId);
            } else if (commandHandler instanceof ru.beastmark.telegram.commands.SetStatusCommand) {
                return ((ru.beastmark.telegram.commands.SetStatusCommand) commandHandler).handle(telegramId, args);
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Ошибка выполнения команды " + command + ": " + e.getMessage());
            e.printStackTrace();
            return "❌ Ошибка выполнения команды: " + e.getMessage();
        }
        
        return "❌ Команда не поддерживается";
    }
    
    /**
     * Отправить результат выполнения команды пользователю
     */
    public void sendCommandResult(String telegramId, String result) {
        sendPersonalMessage(telegramId, result);
    }
    
    public void reload() {
        FileConfiguration config = plugin.getConfig();
        
        // Перезагружаем настройки
        this.enabled = config.getBoolean("integrations.telegram.enabled", false);
        this.botToken = config.getString("integrations.telegram.bot-token", "");
        this.chatId = config.getString("integrations.telegram.chat-id", "");
        this.notifyStatusChanges = config.getBoolean("integrations.telegram.notify-status-changes", true);
        this.notifyJoinsQuits = config.getBoolean("integrations.telegram.notify-joins-quits", true);
        this.adminOnly = config.getBoolean("integrations.telegram.admin-only", false);
        this.personalNotifications = config.getBoolean("integrations.telegram.personal-notifications", true);
        this.sendToChannel = config.getBoolean("integrations.telegram.send-to-channel", true);
        
        plugin.getLogger().info("Telegram интеграция перезагружена!");
    }
}
