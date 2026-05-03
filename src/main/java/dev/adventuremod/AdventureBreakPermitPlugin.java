package dev.adventuremod;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

public final class AdventureBreakPermitPlugin extends JavaPlugin implements Listener {

    private final Set<Material> canPlaceOnBlocks = new HashSet<>();

    private final Set<Material> shovelBlocks = EnumSet.of(
        Material.DIRT, Material.GRASS_BLOCK, Material.COARSE_DIRT, Material.PODZOL,
        Material.ROOTED_DIRT, Material.MUD, Material.SAND, Material.RED_SAND,
        Material.GRAVEL, Material.CLAY, Material.SNOW, Material.SNOW_BLOCK,
        Material.SOUL_SAND, Material.SOUL_SOIL, Material.MYCELIUM
    );

    private final Set<Material> woodPickaxeBlocks = EnumSet.of(
        Material.STONE, Material.COBBLESTONE, Material.COAL_ORE, Material.DEEPSLATE_COAL_ORE
    );

    private final Set<Material> stonePickaxeBlocks = EnumSet.of(
        Material.STONE, Material.COBBLESTONE, Material.COAL_ORE,
        Material.DEEPSLATE_COAL_ORE, Material.IRON_ORE, Material.DEEPSLATE_IRON_ORE
    );

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadPluginConfig();
        getServer().getPluginManager().registerEvents(this, this);
    }

    @Override
    public void reloadConfig() {
        super.reloadConfig();
        reloadPluginConfig();
    }

    private void reloadPluginConfig() {
        canPlaceOnBlocks.clear();

        FileConfiguration config = getConfig();
        loadMaterialList(config.getStringList("canplaceon-itens"), canPlaceOnBlocks, "canplaceon-itens");

        for (Player player : getServer().getOnlinePlayers()) {
            if (player.getGameMode() == GameMode.ADVENTURE) {
                applyRulesToInventory(player);
            }
        }
    }

    private void loadMaterialList(List<String> names, Set<Material> target, String path) {
        for (String raw : names) {
            String key = raw.trim().toUpperCase(Locale.ROOT);
            Material material = Material.matchMaterial(key);
            if (material == null) {
                getLogger().warning("Material inválido em " + path + ": " + raw);
                continue;
            }
            target.add(material);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.ADVENTURE) {
            applyRulesToInventory(player);
        }
    }

    @EventHandler
    public void onHeldItem(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        if (player.getGameMode() != GameMode.ADVENTURE) {
            return;
        }
        applyRulesToItem(player.getInventory().getItem(event.getNewSlot()));
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || player.getGameMode() != GameMode.ADVENTURE) {
            return;
        }

        applyRulesToInventory(player);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player) || player.getGameMode() != GameMode.ADVENTURE) {
            return;
        }

        applyRulesToItem(event.getItem().getItemStack());
    }

    private void applyRulesToInventory(Player player) {
        PlayerInventory inventory = player.getInventory();
        for (ItemStack content : inventory.getContents()) {
            applyRulesToItem(content);
        }
        applyRulesToItem(inventory.getItemInOffHand());
        player.updateInventory();
    }

    private void applyRulesToItem(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }

        Material type = item.getType();
        String name = type.name();

        if (type.isBlock()) {
            applyCanPlaceOn(meta, canPlaceOnBlocks);
        }

        if (name.endsWith("_SHOVEL")) {
            applyCanDestroy(meta, shovelBlocks);
        } else if (name.equals("WOODEN_PICKAXE")) {
            applyCanDestroy(meta, woodPickaxeBlocks);
        } else if (name.equals("STONE_PICKAXE")) {
            applyCanDestroy(meta, stonePickaxeBlocks);
        } else if (name.endsWith("_PICKAXE")) {
            applyCanDestroy(meta, buildGenericPickaxeBlocks());
        } else if (name.endsWith("_AXE")) {
            applyCanDestroy(meta, buildAxeBlocks());
        }

        item.setItemMeta(meta);
    }

    private Set<Material> buildGenericPickaxeBlocks() {
        Set<Material> blocks = new HashSet<>(stonePickaxeBlocks);
        for (Material material : Material.values()) {
            if (!material.isBlock()) {
                continue;
            }
            String n = material.name();
            if (n.endsWith("_ORE") || n.contains("DEEPSLATE") || n.contains("STONE") || n.contains("NETHERRACK")) {
                blocks.add(material);
            }
        }
        return blocks;
    }

    private Set<Material> buildAxeBlocks() {
        Set<Material> blocks = new HashSet<>();
        for (Material material : Material.values()) {
            if (!material.isBlock()) {
                continue;
            }
            String n = material.name();
            if (n.contains("LOG") || n.contains("WOOD") || n.contains("STEM") || n.contains("HYPHAE") || n.contains("PLANKS")) {
                blocks.add(material);
            }
        }
        return blocks;
    }

    private void applyCanDestroy(ItemMeta meta, Set<Material> materials) {
        meta.setDestroyableKeys(materials.stream().map(Material::getKey).collect(Collectors.toSet()));
    }

    private void applyCanPlaceOn(ItemMeta meta, Set<Material> materials) {
        meta.setPlaceableKeys(materials.stream().map(Material::getKey).collect(Collectors.toSet()));
    }

}
