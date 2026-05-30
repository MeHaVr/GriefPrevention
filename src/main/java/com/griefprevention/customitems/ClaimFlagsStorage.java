package com.griefprevention.customitems;

import me.ryanhamshire.GriefPrevention.GriefPrevention;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class ClaimFlagsStorage
{
    public static final String FLAG_PVP          = "pvp";
    public static final String FLAG_EXPLOSIONS   = "explosions";
    public static final String FLAG_MOB_SPAWNING = "mobSpawning";
    public static final String FLAG_HOLOGRAM     = "hologram";
    public static final String FLAG_PARTICLES    = "particles";

    public static final String DEFAULT_PARTICLE_TYPE = "FLAME";
    private static final String KEY_PARTICLE_TYPE = "particleType";

    private static final Map<String, Boolean> DEFAULTS = Map.of(
        FLAG_PVP,          false,
        FLAG_EXPLOSIONS,   false,
        FLAG_MOB_SPAWNING, true,
        FLAG_HOLOGRAM,     true,
        FLAG_PARTICLES,    false
    );

    private static final int DEBOUNCE_TICKS = 40; // 2 Sekunden

    private final GriefPrevention plugin;
    private final File             file;
    private final YamlConfiguration config;
    private final ConcurrentHashMap<Long, Map<String, Boolean>> cache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, String> particleTypeCache   = new ConcurrentHashMap<>();
    private final AtomicInteger pendingSaveTask = new AtomicInteger(-1);

    public ClaimFlagsStorage(@NotNull GriefPrevention plugin)
    {
        this.plugin  = plugin;
        this.file    = new File(plugin.getDataFolder(), "claimFlags.yml");
        this.config  = YamlConfiguration.loadConfiguration(file);
    }

    public boolean getFlag(long claimId, @NotNull String flag)
    {
        return cache.computeIfAbsent(claimId, this::loadFromDisk)
                    .getOrDefault(flag, DEFAULTS.getOrDefault(flag, false));
    }

    public void setFlag(long claimId, @NotNull String flag, boolean value)
    {
        cache.computeIfAbsent(claimId, this::loadFromDisk).put(flag, value);
        config.set(claimId + "." + flag, value);
        scheduleSave();
    }

    public @NotNull String getParticleType(long claimId)
    {
        return particleTypeCache.computeIfAbsent(claimId, id ->
            config.getString(id + "." + KEY_PARTICLE_TYPE, DEFAULT_PARTICLE_TYPE));
    }

    public void setParticleType(long claimId, @NotNull String type)
    {
        particleTypeCache.put(claimId, type);
        config.set(claimId + "." + KEY_PARTICLE_TYPE, type);
        scheduleSave();
    }

    public void removeClaim(long claimId)
    {
        cache.remove(claimId);
        particleTypeCache.remove(claimId);
        config.set(String.valueOf(claimId), null);
        scheduleSave();
    }

    private @NotNull Map<String, Boolean> loadFromDisk(long claimId)
    {
        Map<String, Boolean> flags = new HashMap<>(DEFAULTS);
        for (String key : DEFAULTS.keySet())
        {
            String path = claimId + "." + key;
            if (config.contains(path)) flags.put(key, config.getBoolean(path));
        }
        return flags;
    }

    /** Debounced save – mehrere schnelle Änderungen lösen nur einen einzigen Schreibzugriff aus. */
    private void scheduleSave()
    {
        int old = pendingSaveTask.getAndSet(-1);
        if (old != -1) plugin.getServer().getScheduler().cancelTask(old);

        // Snapshot auf dem Main Thread erstellen – YamlConfiguration ist nicht thread-safe
        String snapshot = config.saveToString();

        int newTask = plugin.getServer().getScheduler()
            .runTaskLaterAsynchronously(plugin, () ->
            {
                pendingSaveTask.set(-1);
                try { java.nio.file.Files.writeString(file.toPath(), snapshot); }
                catch (IOException e)
                {
                    plugin.getLogger().warning("claimFlags.yml konnte nicht gespeichert werden: " + e.getMessage());
                }
            }, DEBOUNCE_TICKS).getTaskId();
        pendingSaveTask.set(newTask);
    }
}
