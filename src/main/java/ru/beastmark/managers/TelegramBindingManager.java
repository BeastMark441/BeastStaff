package ru.beastmark.managers;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import ru.beastmark.BeastStaff;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class TelegramBindingManager {
    
    private final BeastStaff plugin;
    private final Map<UUID, String> playerBindings; // UUID -> Telegram ID
    private final Map<String, UUID> telegramBindings; // Telegram ID -> UUID
    private final File bindingFile;
    private FileConfiguration bindingConfig;
    private final Object saveLock = new Object();
    private BukkitTask pendingSaveTask;
    
    public TelegramBindingManager(BeastStaff plugin) {
        this.plugin = plugin;
        this.playerBindings = new ConcurrentHashMap<>();
        this.telegramBindings = new ConcurrentHashMap<>();
        this.bindingFile = new File(plugin.getDataFolder(), "telegram_bindings.yml");
        
        loadBindings();
    }
    
    private void loadBindings() {
        if (plugin.getDatabaseManager().isConnected()) {
            loadFromDatabase();
        } else {
            loadFromFile();
        }
    }

    private void loadFromFile() {
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

    private void loadFromDatabase() {
        String query = "SELECT player_uuid, telegram_id FROM telegram_bindings";
        try (Connection conn = plugin.getDatabaseManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(query);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                UUID playerUUID = UUID.fromString(rs.getString("player_uuid"));
                String telegramId = rs.getString("telegram_id");
                if (telegramId != null && !telegramId.isEmpty()) {
                    playerBindings.put(playerUUID, telegramId);
                    telegramBindings.put(telegramId, playerUUID);
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, plugin.getMessageManager().getMessage("database-error", "error", e.getMessage()), e);
            loadFromFile();
        }
    }
    
    public void saveBindings() {
        long delayTicks = plugin.getConfig().getLong("telegram-bindings.save-delay-ticks", 40L);
        synchronized (saveLock) {
            if (pendingSaveTask != null) {
                return;
            }
            pendingSaveTask = plugin.getServer().getScheduler().runTaskLaterAsynchronously(plugin, () -> {
                synchronized (saveLock) {
                    pendingSaveTask = null;
                }
                if (plugin.getDatabaseManager().isConnected()) {
                    saveToDatabase();
                } else {
                    saveToFile();
                }
            }, delayTicks);
        }
    }

    public void flushSaveSync() {
        synchronized (saveLock) {
            if (pendingSaveTask != null) {
                pendingSaveTask.cancel();
                pendingSaveTask = null;
            }
        }
        if (plugin.getDatabaseManager().isConnected()) {
            saveToDatabase();
        } else {
            saveToFile();
        }
    }

    private void saveToFile() {
        if (bindingConfig == null) {
            bindingConfig = YamlConfiguration.loadConfiguration(bindingFile);
        }

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

    private void saveToDatabase() {
        String deleteQuery = "DELETE FROM telegram_bindings";
        String insertQuery = "INSERT INTO telegram_bindings (player_uuid, telegram_id) VALUES (?, ?)";

        try (Connection conn = plugin.getDatabaseManager().getConnection()) {
            conn.setAutoCommit(false);

            try (PreparedStatement deleteStmt = conn.prepareStatement(deleteQuery)) {
                deleteStmt.executeUpdate();
            }

            try (PreparedStatement insertStmt = conn.prepareStatement(insertQuery)) {
                for (Map.Entry<UUID, String> entry : playerBindings.entrySet()) {
                    insertStmt.setString(1, entry.getKey().toString());
                    insertStmt.setString(2, entry.getValue());
                    insertStmt.addBatch();
                }
                insertStmt.executeBatch();
            }

            conn.commit();
            conn.setAutoCommit(true);

        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, plugin.getMessageManager().getMessage("save-error", "error", e.getMessage()), e);
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
