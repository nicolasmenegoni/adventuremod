package dev.adventuremod;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.Arrays;
import java.util.stream.Collectors;
import java.lang.reflect.Method;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

public final class AdventureBreakPermitPlugin extends JavaPlugin implements Listener {

    private final Set<Material> blockedMaterials = new HashSet<>();
    private final Set<Material> allowedDestroyMaterials = new HashSet<>();

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
        allowedDestroyMaterials.clear();

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

        allowedDestroyMaterials.addAll(Arrays.stream(Material.values())
            .filter(Material::isBlock)
            .filter(material -> !blockedMaterials.contains(material))
            .collect(Collectors.toSet()));

        for (Player onlinePlayer : getServer().getOnlinePlayers()) {
            if (onlinePlayer.getGameMode() == GameMode.ADVENTURE) {
                applyCanDestroy(playerMainHand(onlinePlayer));
            }
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.ADVENTURE) {
            applyCanDestroy(playerMainHand(player));
        }
    }

    @EventHandler
    public void onItemHeld(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        if (player.getGameMode() != GameMode.ADVENTURE) {
            return;
        }

        applyCanDestroy(player.getInventory().getItem(event.getNewSlot()));
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        if (player.getGameMode() != GameMode.ADVENTURE) {
            return;
        }

        applyCanDestroy(playerMainHand(player));
    }

    private void applyCanDestroy(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }

        if (!applyCanDestroyMeta(meta)) {
            return;
        }

        item.setItemMeta(meta);
    }

    private boolean applyCanDestroyMeta(ItemMeta meta) {
        try {
            Method setCanDestroy = meta.getClass().getMethod("setCanDestroy", Set.class);
            setCanDestroy.invoke(meta, allowedDestroyMaterials);
            return true;
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    private ItemStack playerMainHand(Player player) {
        return player.getInventory().getItemInMainHand();
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
