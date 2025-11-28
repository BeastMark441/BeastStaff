package ru.beastmark.litebans;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Кеш для статистики с поддержкой TTL
 */
public class PunishmentCache {
    
    private static class CacheEntry<T> {
        private final T value;
        private final long expiresAt;
        
        CacheEntry(T value, long ttlMinutes) {
            this.value = value;
            this.expiresAt = System.currentTimeMillis() + (ttlMinutes * 60 * 1000);
        }
        
        boolean isExpired() {
            return System.currentTimeMillis() > expiresAt;
        }
    }
    
    private final Map<String, CacheEntry<?>> cache = new ConcurrentHashMap<>();
    
    public <T> void put(String key, T value, long ttlMinutes) {
        cache.put(key, new CacheEntry<>(value, ttlMinutes));
    }
    
    @SuppressWarnings("unchecked")
    public <T> T get(String key) {
        CacheEntry<?> entry = cache.get(key);
        if (entry != null && !entry.isExpired()) {
            return (T) entry.value;
        }
        cache.remove(key);
        return null;
    }
    
    public boolean isValid(String key) {
        CacheEntry<?> entry = cache.get(key);
        if (entry == null) return false;
        if (entry.isExpired()) {
            cache.remove(key);
            return false;
        }
        return true;
    }
    
    public void invalidateExpired() {
        cache.entrySet().removeIf(e -> e.getValue().isExpired());
    }
    
    public void clear() {
        cache.clear();
    }
}

