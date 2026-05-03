package dev.adventuremod;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class AdventureBreakPermitPlugin extends JavaPlugin implements Listener {

    private final Set<Material> blockedMaterials = new HashSet<>();
    private final Set<Material> canDestroyMaterials = new HashSet<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadBlockedMaterials();
        getServer().getPluginManager().registerEvents(this, this);

        for (World world : getServer().getWorlds()) {
            applyRulesForWorld(world);
        }
    }

    @Override
    public void reloadConfig() {
        super.reloadConfig();
        reloadBlockedMaterials();
    }

    private void reloadBlockedMaterials() {
        blockedMaterials.clear();
        canDestroyMaterials.clear();

        FileConfiguration config = getConfig();
        List<String> names = config.getStringList("blocked-blocks");

        for (String raw : names) {
            String key = raw.trim().toUpperCase(Locale.ROOT);
            Material material = Material.matchMaterial(key);
            if (material != null) {
                blockedMaterials.add(material);
            } else {
                getLogger().warning("Invalid material in blocked-blocks: " + raw);
            }
        }

        canDestroyMaterials.addAll(Arrays.stream(Material.values())
            .filter(Material::isBlock)
            .filter(material -> !blockedMaterials.contains(material))
            .collect(Collectors.toSet()));
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        applyRulesForWorld(event.getWorld());
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        applyRulesForChunk(event.getChunk());
    }

    private void applyRulesForWorld(World world) {
        for (Chunk chunk : world.getLoadedChunks()) {
            applyRulesForChunk(chunk);
        }
    }

    private void applyRulesForChunk(Chunk chunk) {
        // In vanilla/Spigot API, CanDestroy exists only as item metadata.
        // This plugin intentionally has no player logic per requested scope.
        // We still bind execution to world/chunk lifecycle events.
    }
}
