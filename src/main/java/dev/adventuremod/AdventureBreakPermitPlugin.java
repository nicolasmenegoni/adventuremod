package dev.adventuremod;

import java.util.Set;
import java.util.stream.Collectors;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.plugin.java.JavaPlugin;

public class AdventureBreakPermitPlugin extends JavaPlugin implements Listener {

    private Set<Material> blockedMaterials;

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
        FileConfiguration config = getConfig();
        blockedMaterials = config.getStringList("blocked-blocks").stream()
                .map(String::trim)
                .map(String::toUpperCase)
                .map(name -> {
                    Material material = Material.matchMaterial(name);
                    if (material == null) {
                        getLogger().warning("Material inválido na config.yml: " + name);
                    }
                    return material;
                })
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockBreak(BlockBreakEvent event) {
        if (event.getPlayer().getGameMode() != GameMode.ADVENTURE) {
            return;
        }

        if (blockedMaterials.contains(event.getBlock().getType())) {
            event.setCancelled(true);
            return;
        }

        // No modo Adventure, o evento normalmente já chega cancelado.
        // Para materiais permitidos, removemos o cancelamento.
        event.setCancelled(false);
    }
}
