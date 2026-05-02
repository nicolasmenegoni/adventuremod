package dev.adventuremod;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class AdventureBreakPermitPlugin extends JavaPlugin implements Listener {

    private final Set<Material> blockedMaterials = new HashSet<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadBlockedMaterials();
        getServer().getPluginManager().registerEvents(this, this);
    }

    @Override
    public void reloadConfig() {
        super.reloadConfig();
        reloadBlockedMaterials();
    }

    private void reloadBlockedMaterials() {
        blockedMaterials.clear();

        FileConfiguration config = getConfig();
        List<String> configured = config.getStringList("blocked-blocks");

        for (String rawName : configured) {
            String normalized = rawName.trim().toUpperCase(Locale.ROOT);
            Material material = Material.matchMaterial(normalized);

            if (material == null) {
                getLogger().warning("Invalid material in config.yml: " + rawName);
                continue;
            }

            blockedMaterials.add(material);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockBreak(BlockBreakEvent event) {
        if (event.getPlayer().getGameMode() != GameMode.ADVENTURE) {
            return;
        }

        Material blockType = event.getBlock().getType();
        if (blockedMaterials.contains(blockType)) {
            event.setCancelled(true);
            return;
        }

        // Adventure mode normally blocks breaking. Allow it for non-blocked materials.
        event.setCancelled(false);
    }
}
