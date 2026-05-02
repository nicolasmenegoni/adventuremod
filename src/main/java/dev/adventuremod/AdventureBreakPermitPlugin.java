package dev.adventuremod;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
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
    }

    @EventHandler(ignoreCancelled = true)
    public void onLeftClickBlock(PlayerInteractEvent event) {
        if (event.getAction() != Action.LEFT_CLICK_BLOCK) {
            return;
        }

        Player player = event.getPlayer();
        if (player.getGameMode() != GameMode.ADVENTURE) {
            return;
        }

        Block block = event.getClickedBlock();
        if (block == null) {
            return;
        }

        if (blockedMaterials.contains(block.getType())) {
            event.setCancelled(true);
            return;
        }

        // For blocks that are not configured as blocked, let vanilla Minecraft
        // handle the interaction/break behavior normally.
    }
}
