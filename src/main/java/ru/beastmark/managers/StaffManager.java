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

public class StaffManager {
    
    private final BeastStaff plugin;
    private final File dataFile;
    private FileConfiguration dataConfig;
    private final Map<UUID, StaffMember> staffMembers;
    
    public StaffManager(BeastStaff plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "staff.yml");
        this.staffMembers = new HashMap<>();
        loadData();
    }
    
    public void loadData() {
        if (!dataFile.exists()) {
            plugin.saveResource("staff.yml", false);
        }
        
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);
        
        if (dataConfig.contains("staff")) {
            for (String uuidString : dataConfig.getConfigurationSection("staff").getKeys(false)) {
                UUID uuid = UUID.fromString(uuidString);
                String name = dataConfig.getString("staff." + uuidString + ".name");
                String rank = dataConfig.getString("staff." + uuidString + ".rank");
                long joinDate = dataConfig.getLong("staff." + uuidString + ".joinDate");
                
                StaffMember member = new StaffMember(uuid, name, rank, joinDate);
                staffMembers.put(uuid, member);
            }
        }
    }
    
    public void saveData() {
        for (Map.Entry<UUID, StaffMember> entry : staffMembers.entrySet()) {
            StaffMember member = entry.getValue();
            String path = "staff." + member.getUuid().toString();
            dataConfig.set(path + ".name", member.getName());
            dataConfig.set(path + ".rank", member.getRank());
            dataConfig.set(path + ".joinDate", member.getJoinDate());
        }
        
        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Не удалось сохранить данные персонала: " + e.getMessage());
        }
    }
    
    public void addStaffMember(Player player, String rank) {
        StaffMember member = new StaffMember(player.getUniqueId(), player.getName(), rank, System.currentTimeMillis());
        staffMembers.put(player.getUniqueId(), member);
        saveData();
    }
    
    public void removeStaffMember(UUID uuid) {
        staffMembers.remove(uuid);
        dataConfig.set("staff." + uuid.toString(), null);
        saveData();
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
