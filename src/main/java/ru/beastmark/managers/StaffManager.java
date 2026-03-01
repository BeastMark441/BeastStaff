package ru.beastmark.managers;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import ru.beastmark.api.events.StaffJoinEvent;
import ru.beastmark.BeastStaff;

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
import org.bukkit.scheduler.BukkitTask;

public class StaffManager {
    
    private final BeastStaff plugin;
    private final File dataFile;
    private FileConfiguration dataConfig;
    private final Map<UUID, StaffMember> staffMembers;
    private final Object saveLock = new Object();
    private BukkitTask pendingSaveTask;
    
    public StaffManager(BeastStaff plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "staff.yml");
        this.staffMembers = new ConcurrentHashMap<>();
        loadData();
    }
    
    public void loadData() {
        staffMembers.clear();
        if (plugin.getDatabaseManager().isConnected()) {
            loadFromDatabase();
        } else {
            loadFromFile();
        }
    }
    
    private void loadFromFile() {
        if (!dataFile.exists()) {
            plugin.saveResource("staff.yml", false);
        }
        
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);
        
        if (dataConfig.contains("staff")) {
            for (String uuidString : dataConfig.getConfigurationSection("staff").getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(uuidString);
                    String name = dataConfig.getString("staff." + uuidString + ".name");
                    String rank = dataConfig.getString("staff." + uuidString + ".rank");
                    long joinDate = dataConfig.getLong("staff." + uuidString + ".joinDate");
                    
                    StaffMember member = new StaffMember(uuid, name, rank, joinDate);
                    staffMembers.put(uuid, member);
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Invalid UUID in staff.yml: " + uuidString);
                }
            }
        }
    }

    private void loadFromDatabase() {
        String query = "SELECT * FROM staff_members";
        try (Connection conn = plugin.getDatabaseManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(query);
             ResultSet rs = stmt.executeQuery()) {
            
            while (rs.next()) {
                UUID uuid = UUID.fromString(rs.getString("uuid"));
                String name = rs.getString("name");
                String rank = rs.getString("rank");
                long joinDate = rs.getLong("join_date");
                
                StaffMember member = new StaffMember(uuid, name, rank, joinDate);
                staffMembers.put(uuid, member);
            }
            plugin.getLogger().info("Загружено " + staffMembers.size() + " сотрудников из базы данных.");
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, plugin.getMessageManager().getMessage("database-error", "error", e.getMessage()), e);
            // Fallback to file in case of DB failure?
            plugin.getLogger().info("Попытка загрузки из файла...");
            loadFromFile();
        }
    }
    
    public void saveData() {
        long delayTicks = plugin.getConfig().getLong("staff.file-save-delay-ticks", 40L);
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
        FileConfiguration config = YamlConfiguration.loadConfiguration(dataFile);
        config.set("staff", null);
        for (StaffMember member : staffMembers.values()) {
            String path = "staff." + member.getUuid().toString();
            config.set(path + ".name", member.getName());
            config.set(path + ".rank", member.getRank());
            config.set(path + ".joinDate", member.getJoinDate());
        }

        try {
            config.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().severe(plugin.getMessageManager().getMessage("save-error", "error", e.getMessage()));
        }
    }

    private void saveToDatabase() {
        String deleteQuery = "DELETE FROM staff_members";
        String insertQuery = "INSERT INTO staff_members (uuid, name, rank, join_date) VALUES (?, ?, ?, ?)";
        
        try (Connection conn = plugin.getDatabaseManager().getConnection()) {
            conn.setAutoCommit(false);
            
            try (PreparedStatement deleteStmt = conn.prepareStatement(deleteQuery)) {
                deleteStmt.executeUpdate();
            }

            try (PreparedStatement insertStmt = conn.prepareStatement(insertQuery)) {
                for (StaffMember member : staffMembers.values()) {
                    insertStmt.setString(1, member.getUuid().toString());
                    insertStmt.setString(2, member.getName());
                    insertStmt.setString(3, member.getRank());
                    insertStmt.setLong(4, member.getJoinDate());
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
    
    public void addStaffMember(Player player, String rank) {
        long joinDate = System.currentTimeMillis();
        StaffMember member = new StaffMember(player.getUniqueId(), player.getName(), rank, joinDate);
        staffMembers.put(player.getUniqueId(), member);
        plugin.getServer().getPluginManager().callEvent(new StaffJoinEvent(member.getUuid(), member.getName(), member.getRank(), joinDate));
        saveData(); // Async
    }
    
    public void removeStaffMember(UUID uuid) {
        staffMembers.remove(uuid);
        saveData(); // Async
    }
    
    public boolean isStaffMember(UUID uuid) {
        return staffMembers.containsKey(uuid);
    }
    
    public StaffMember getStaffMember(UUID uuid) {
        return staffMembers.get(uuid);
    }
    
    public Map<UUID, StaffMember> getAllStaffMembers() {
        return new HashMap<>(staffMembers);
    }
    
    public static class StaffMember {
        private final UUID uuid;
        private final String name;
        private final String rank;
        private final long joinDate;
        
        public StaffMember(UUID uuid, String name, String rank, long joinDate) {
            this.uuid = uuid;
            this.name = name;
            this.rank = rank;
            this.joinDate = joinDate;
        }
        
        public UUID getUuid() {
            return uuid;
        }
        
        public String getName() {
            return name;
        }
        
        public String getRank() {
            return rank;
        }
        
        public long getJoinDate() {
            return joinDate;
        }
    }
}
