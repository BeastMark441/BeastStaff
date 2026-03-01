package ru.beastmark.api.events;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.UUID;

public class StaffWorkEndEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID playerUuid;
    private final String playerName;
    private final String oldStatus;
    private final String newStatus;
    private final long startTime;
    private final long endTime;
    private final long duration;

    public StaffWorkEndEvent(UUID playerUuid, String playerName, String oldStatus, String newStatus, long startTime, long endTime, long duration) {
        this.playerUuid = playerUuid;
        this.playerName = playerName;
        this.oldStatus = oldStatus;
        this.newStatus = newStatus;
        this.startTime = startTime;
        this.endTime = endTime;
        this.duration = duration;
    }

    public UUID getPlayerUuid() {
        return playerUuid;
    }

    public String getPlayerName() {
        return playerName;
    }

    public String getOldStatus() {
        return oldStatus;
    }

    public String getNewStatus() {
        return newStatus;
    }

    public long getStartTime() {
        return startTime;
    }

    public long getEndTime() {
        return endTime;
    }

    public long getDuration() {
        return duration;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
