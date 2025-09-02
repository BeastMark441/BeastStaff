package ru.beastmark.managers;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import ru.beastmark.BeastStaff;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

public class TelegramBindingManager {
    
    private final BeastStaff plugin;
    private final Map<UUID, String> playerBindings; // UUID -> Telegram ID
    private final Map<String, UUID> telegramBindings; // Telegram ID -> UUID
    private final File bindingFile;
    private FileConfiguration bindingConfig;
    
    public TelegramBindingManager(BeastStaff plugin) {
        this.plugin = plugin;
        this.playerBindings = new HashMap<>();
        this.telegramBindings = new HashMap<>();
        this.bindingFile = new File(plugin.getDataFolder(), "telegram_bindings.yml");
        
        loadBindings();
    }
    
    private void loadBindings() {
        if (!bindingFile.exists()) {
            plugin.saveResource("telegram_bindings.yml", false);
        }
        
        bindingConfig = YamlConfiguration.loadConfiguration(bindingFile);
        
        if (bindingConfig.contains("bindings")) {
            for (String uuidString : bindingConfig.getConfigurationSection("bindings").getKeys(false)) {
                try {
                    UUID playerUUID = UUID.fromString(uuidString);
                    String telegramId = bindingConfig.getString("bindings." + uuidString);
                    
                    if (telegramId != null && !telegramId.isEmpty()) {
                        playerBindings.put(playerUUID, telegramId);
                        telegramBindings.put(telegramId, playerUUID);
                    }
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Неверный UUID в telegram_bindings.yml: " + uuidString);
                }
            }
        }
    }
    
    public void saveBindings() {
        bindingConfig.set("bindings", null);
        
        for (Map.Entry<UUID, String> entry : playerBindings.entrySet()) {
            bindingConfig.set("bindings." + entry.getKey().toString(), entry.getValue());
        }
        
        try {
            bindingConfig.save(bindingFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Ошибка сохранения привязок Telegram", e);
        }
    }
    
    public boolean bindPlayer(UUID playerUUID, String telegramId) {
        // Проверяем, что игрок является сотрудником
        if (!plugin.getStaffManager().isStaffMember(playerUUID)) {
            return false;
        }
        
        // Удаляем старую привязку, если есть
        String oldTelegramId = playerBindings.get(playerUUID);
        if (oldTelegramId != null) {
            telegramBindings.remove(oldTelegramId);
        }
        
        // Удаляем привязку другого игрока, если этот Telegram ID уже используется
        UUID oldPlayerUUID = telegramBindings.get(telegramId);
        if (oldPlayerUUID != null) {
            playerBindings.remove(oldPlayerUUID);
        }
        
        // Создаем новую привязку
        playerBindings.put(playerUUID, telegramId);
        telegramBindings.put(telegramId, playerUUID);
        
        saveBindings();
        return true;
    }
    
    public boolean unbindPlayer(UUID playerUUID) {
        String telegramId = playerBindings.remove(playerUUID);
        if (telegramId != null) {
            telegramBindings.remove(telegramId);
            saveBindings();
            return true;
        }
        return false;
    }
    
    public String getPlayerTelegramId(UUID playerUUID) {
        return playerBindings.get(playerUUID);
    }
    
    public UUID getPlayerByTelegramId(String telegramId) {
        return telegramBindings.get(telegramId);
    }
    
    public boolean isPlayerBound(UUID playerUUID) {
        return playerBindings.containsKey(playerUUID);
    }
    
    public boolean isTelegramIdBound(String telegramId) {
        return telegramBindings.containsKey(telegramId);
    }
    
    public Map<UUID, String> getAllBindings() {
        return new HashMap<>(playerBindings);
    }
    
    public void reloadBindings() {
        playerBindings.clear();
        telegramBindings.clear();
        loadBindings();
    }
}
