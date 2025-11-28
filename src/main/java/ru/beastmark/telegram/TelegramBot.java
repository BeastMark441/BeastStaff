package ru.beastmark.telegram;

import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;
import ru.beastmark.BeastStaff;
import ru.beastmark.integrations.TelegramIntegration;
import ru.beastmark.telegram.commands.*;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Полноценный Telegram бот с обработкой команд через long polling
 */
public class TelegramBot {
    
    private final BeastStaff plugin;
    private final TelegramIntegration telegramIntegration;
    private final String botToken;
    private BukkitTask pollingTask;
    private AtomicInteger lastUpdateId = new AtomicInteger(0);
    private boolean running = false;
    
    private final Map<String, Object> commandHandlers;
    
    public TelegramBot(BeastStaff plugin, TelegramIntegration telegramIntegration) {
        this.plugin = plugin;
        this.telegramIntegration = telegramIntegration;
        this.botToken = telegramIntegration.getBotToken();
        this.commandHandlers = new HashMap<>();
        
        // Инициализируем обработчики команд
        initializeCommandHandlers();
    }
    
    private void initializeCommandHandlers() {
        if (plugin.getLiteBansManager() != null && plugin.getLiteBansManager().isEnabled()) {
            commandHandlers.put("bs_ban_stats", new StatsCommand(plugin, plugin.getLiteBansManager()));
            commandHandlers.put("bs_recent_bans", new RecentBansCommand(plugin, plugin.getLiteBansManager()));
        }
        
        commandHandlers.put("bs_status", new StatusCommand(plugin));
        commandHandlers.put("bs_set_status", new SetStatusCommand(plugin));
        
        plugin.getLogger().info("Telegram команды зарегистрированы: " + commandHandlers.size());
    }
    
    public void start() {
        if (running) {
            plugin.getLogger().warning("Telegram бот уже запущен!");
            return;
        }
        
        if (botToken == null || botToken.isEmpty()) {
            plugin.getLogger().warning("Не удалось запустить Telegram бота: токен не настроен");
            return;
        }
        
        running = true;
        plugin.getLogger().info("Запуск Telegram бота...");
        
        // Запускаем long polling в асинхронном потоке
        pollingTask = Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            while (running && plugin.isEnabled()) {
                try {
                    processUpdates();
                    Thread.sleep(1000); // Пауза между запросами
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    plugin.getLogger().log(Level.WARNING, "Ошибка в Telegram боте", e);
                    try {
                        Thread.sleep(5000); // Пауза при ошибке
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        });
        
        plugin.getLogger().info("Telegram бот запущен и готов к работе!");
    }
    
    public void stop() {
        running = false;
        if (pollingTask != null) {
            pollingTask.cancel();
        }
        plugin.getLogger().info("Telegram бот остановлен");
    }
    
    private void processUpdates() {
        try {
            String urlString = "https://api.telegram.org/bot" + botToken + "/getUpdates?offset=" + 
                              lastUpdateId.get() + "&timeout=10";
            URL url = URI.create(urlString).toURL();
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(15000);
            
            int responseCode = connection.getResponseCode();
            if (responseCode != 200) {
                if (responseCode == 401) {
                    plugin.getLogger().severe("ОШИБКА: Неверный Bot Token! Проверьте config.yml");
                }
                return;
            }
            
            StringBuilder response = new StringBuilder();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                String line;
                while ((line = br.readLine()) != null) {
                    response.append(line);
                }
            }
            
            // Простой парсинг JSON
            String jsonResponse = response.toString();
            if (!jsonResponse.contains("\"ok\":true")) {
                return;
            }
            
            // Извлекаем updates
            Pattern updatePattern = Pattern.compile("\"update_id\":(\\d+)");
            Matcher updateMatcher = updatePattern.matcher(jsonResponse);
            
            while (updateMatcher.find()) {
                int updateId = Integer.parseInt(updateMatcher.group(1));
                lastUpdateId.set(updateId + 1);
                
                // Ищем message в этом update
                int updateStart = jsonResponse.indexOf("\"update_id\":" + updateId);
                int updateEnd = jsonResponse.indexOf("}", updateStart + 50);
                if (updateEnd == -1) continue;
                
                String updateJson = jsonResponse.substring(updateStart, updateEnd);
                if (updateJson.contains("\"message\"")) {
                    processMessage(updateJson);
                }
            }
            
        } catch (Exception e) {
            if (plugin.getConfig().getBoolean("debug.log-telegram", false)) {
                plugin.getLogger().log(Level.FINE, "Ошибка при получении обновлений Telegram", e);
            }
        }
    }
    
    private void processMessage(String messageJson) {
        try {
            // Извлекаем text из сообщения
            Pattern textPattern = Pattern.compile("\"text\":\"([^\"]+)\"");
            Matcher textMatcher = textPattern.matcher(messageJson);
            if (!textMatcher.find()) {
                return;
            }
            String text = textMatcher.group(1).replace("\\/", "/");
            
            // Извлекаем chat_id
            Pattern chatIdPattern = Pattern.compile("\"chat\":\\{[^}]*\"id\":(-?\\d+)");
            Matcher chatIdMatcher = chatIdPattern.matcher(messageJson);
            if (!chatIdMatcher.find()) {
                return;
            }
            String chatId = chatIdMatcher.group(1);
            
            // Обрабатываем только команды
            if (!text.startsWith("/")) {
                return;
            }
            
            // Извлекаем команду и аргументы
            String[] parts = text.substring(1).split("\\s+", 2);
            String command = parts[0];
            String[] args = parts.length > 1 ? parts[1].split("\\s+") : new String[0];
            
            // Обрабатываем команду
            String response = handleCommand(chatId, command, args);
            
            // Отправляем ответ
            if (response != null && !response.isEmpty()) {
                sendMessage(chatId, response);
            }
            
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Ошибка при обработке сообщения Telegram", e);
        }
    }
    
    private String handleCommand(String chatId, String command, String[] args) {
        Object handler = commandHandlers.get(command);
        
        if (handler == null) {
            return "❌ Неизвестная команда: /" + command + "\n\n" +
                   "Доступные команды:\n" +
                   "/bs_ban_stats - Статистика наказаний\n" +
                   "/bs_recent_bans - Последние наказания\n" +
                   "/bs_status - Мой статус\n" +
                   "/bs_set_status - Изменить статус";
        }
        
        try {
            if (handler instanceof StatsCommand) {
                return ((StatsCommand) handler).handle(args);
            } else if (handler instanceof RecentBansCommand) {
                return ((RecentBansCommand) handler).handle(args);
            } else if (handler instanceof StatusCommand) {
                return ((StatusCommand) handler).handle(chatId);
            } else if (handler instanceof SetStatusCommand) {
                return ((SetStatusCommand) handler).handle(chatId, args);
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Ошибка выполнения команды " + command, e);
            return "❌ Ошибка выполнения команды: " + e.getMessage();
        }
        
        return "❌ Команда не поддерживается";
    }
    
    private void sendMessage(String chatId, String text) {
        try {
            String urlString = "https://api.telegram.org/bot" + botToken + "/sendMessage";
            URL url = URI.create(urlString).toURL();
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            connection.setDoOutput(true);
            
            String postData = "chat_id=" + chatId + "&text=" + 
                            URLEncoder.encode(text, StandardCharsets.UTF_8) + 
                            "&parse_mode=Markdown";
            
            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = postData.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }
            
            int responseCode = connection.getResponseCode();
            if (responseCode != 200 && plugin.getConfig().getBoolean("debug.log-telegram", false)) {
                plugin.getLogger().warning("Ошибка отправки ответа в Telegram: " + responseCode);
            }
            
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Ошибка отправки сообщения в Telegram", e);
        }
    }
    
    public boolean isRunning() {
        return running;
    }
}

