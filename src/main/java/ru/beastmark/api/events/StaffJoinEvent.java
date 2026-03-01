package ru.beastmark.api.events;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.UUID;

public class StaffJoinEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID playerUuid;
    private final String playerName;
    private final String rank;
    private final long joinDate;

    public StaffJoinEvent(UUID playerUuid, String playerName, String rank, long joinDate) {
        this.playerUuid = playerUuid;
        this.playerName = playerName;
        this.rank = rank;
        this.joinDate = joinDate;
    }

    public UUID getPlayerUuid() {
        return playerUuid;
    }

    public String getPlayerName() {
        return playerName;
    }

    public String getRank() {
        return rank;
    }

    public long getJoinDate() {
        return joinDate;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
