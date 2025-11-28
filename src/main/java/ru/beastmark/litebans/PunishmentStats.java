package ru.beastmark.litebans;

import java.util.*;

/**
 * Объект для хранения статистики наказаний
 */
public class PunishmentStats {
    
    public int bans;              // Всего банов
    public int activeBans;        // Активных банов
    public int mutes;             // Всего мутов
    public int activeMutes;       // Активных мутов
    public int kicks;             // Всего киков
    public int warnings;          // Всего предупреждений
    
    public List<Map<String, Object>> topModerators;    // Топ модераторы
    public List<Map<String, Object>> topReasons;       // Популярные причины
    public Map<Integer, Integer> punishmentsByHour;    // По часам
    
    public PunishmentStats() {
        this.topModerators = new ArrayList<>();
        this.topReasons = new ArrayList<>();
        this.punishmentsByHour = new HashMap<>();
    }
    
    public int getTotalPunishments() {
        return bans + mutes + kicks + warnings;
    }
    
    public int getTotalActivePunishments() {
        return activeBans + activeMutes;
    }
    
    @Override
    public String toString() {
        return "PunishmentStats{" +
                "bans=" + bans +
                ", activeBans=" + activeBans +
                ", mutes=" + mutes +
                ", activeMutes=" + activeMutes +
                ", kicks=" + kicks +
                ", warnings=" + warnings +
                ", topModerators=" + topModerators +
                ", topReasons=" + topReasons +
                '}';
    }
}

