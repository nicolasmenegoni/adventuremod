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
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
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

        event.setCancelled(false);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onAdventureLeftClick(PlayerInteractEvent event) {
        if (event.getAction() != Action.LEFT_CLICK_BLOCK || event.getClickedBlock() == null) {
            return;
        }

        Player player = event.getPlayer();
        if (player.getGameMode() != GameMode.ADVENTURE) {
            return;
        }

        Block block = event.getClickedBlock();
        if (blockedMaterials.contains(block.getType())) {
            event.setCancelled(true);
            return;
        }

        ItemStack tool = player.getInventory().getItemInMainHand();

        // In Adventure mode, vanilla does not allow breaking by default.
        // We manually break allowed blocks to enforce plugin rules.
        block.breakNaturally(tool);
        event.setCancelled(true);
    }
}
