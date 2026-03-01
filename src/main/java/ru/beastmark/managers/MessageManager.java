package ru.beastmark.managers;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import ru.beastmark.BeastStaff;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

public class MessageManager {

    private final BeastStaff plugin;
    private FileConfiguration messagesConfig;
    private File messagesFile;
    private final Map<String, String> messageCache = new HashMap<>();

    public MessageManager(BeastStaff plugin) {
        this.plugin = plugin;
        loadMessages();
    }

    public void loadMessages() {
        messagesFile = new File(plugin.getDataFolder(), "messages.yml");
        if (!messagesFile.exists()) {
            plugin.saveResource("messages.yml", false);
        }

        messagesConfig = YamlConfiguration.loadConfiguration(messagesFile);
        
        // Load default messages from jar to add missing keys
        InputStream defConfigStream = plugin.getResource("messages.yml");
        if (defConfigStream != null) {
            messagesConfig.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(defConfigStream, StandardCharsets.UTF_8)));
            messagesConfig.options().copyDefaults(true);
            try {
                messagesConfig.save(messagesFile);
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not save messages.yml", e);
            }
        }
        
        messageCache.clear();
        for (String key : messagesConfig.getKeys(true)) {
            if (messagesConfig.isString(key)) {
                messageCache.put(key, ChatColor.translateAlternateColorCodes('&', messagesConfig.getString(key)));
            }
        }
    }

    public String getMessage(String key) {
        return messageCache.getOrDefault(key, key);
    }
    
    public String getMessage(String key, String... placeholders) {
        String message = getMessage(key);
        for (int i = 0; i < placeholders.length; i += 2) {
            if (i + 1 < placeholders.length) {
                message = message.replace("{" + placeholders[i] + "}", placeholders[i + 1]);
            }
        }
        return message;
    }
}
