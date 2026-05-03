package dev.adventuremod;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

public final class AdventureBreakPermitPlugin extends JavaPlugin implements Listener {

    private final Set<Material> easyDroppedBlocks = new HashSet<>();
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
        easyDroppedBlocks.clear();
        canPlaceOnBlocks.clear();

        FileConfiguration config = getConfig();
        loadMaterialList(config.getStringList("easy-dropped-itens"), easyDroppedBlocks, "easy-dropped-itens");
        loadMaterialList(config.getStringList("canplaceon-itens"), canPlaceOnBlocks, "canplaceon-itens");

        for (Player player : getServer().getOnlinePlayers()) {
            if (player.getGameMode() == GameMode.ADVENTURE) {
                applyRulesToItem(player.getInventory().getItemInMainHand());
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
            applyRulesToItem(player.getInventory().getItemInMainHand());
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
    public void onLeftClick(PlayerInteractEvent event) {
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

        if (easyDroppedBlocks.contains(block.getType())) {
            block.breakNaturally(player.getInventory().getItemInMainHand());
            event.setCancelled(true);
            return;
        }

        ItemStack handItem = player.getInventory().getItemInMainHand();
        if (canBreakWithTool(handItem, block.getType())) {
            block.breakNaturally(handItem);
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onRightClick(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Player player = event.getPlayer();
        if (player.getGameMode() != GameMode.ADVENTURE) {
            return;
        }

        Block clicked = event.getClickedBlock();
        if (clicked == null || !canPlaceOnBlocks.contains(clicked.getType())) {
            return;
        }

        ItemStack handItem = player.getInventory().getItemInMainHand();
        if (handItem == null || handItem.getType().isAir() || !handItem.getType().isBlock()) {
            return;
        }

        Block target = clicked.getRelative(event.getBlockFace() == null ? BlockFace.UP : event.getBlockFace());
        if (!target.getType().isAir()) {
            return;
        }

        target.setType(handItem.getType(), true);
        handItem.setAmount(handItem.getAmount() - 1);
        event.setCancelled(true);
    }

    private boolean canBreakWithTool(ItemStack handItem, Material blockType) {
        if (handItem == null || handItem.getType().isAir()) {
            return false;
        }

        String tool = handItem.getType().name();
        if (tool.endsWith("_SHOVEL")) {
            return shovelBlocks.contains(blockType);
        }
        if (tool.equals("WOODEN_PICKAXE")) {
            return woodPickaxeBlocks.contains(blockType);
        }
        if (tool.equals("STONE_PICKAXE")) {
            return stonePickaxeBlocks.contains(blockType);
        }
        if (tool.endsWith("_PICKAXE")) {
            return buildGenericPickaxeBlocks().contains(blockType);
        }
        if (tool.endsWith("_AXE")) {
            return buildAxeBlocks().contains(blockType);
        }

        return false;
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
        if (tryInvoke(meta, "setCanDestroy", Set.class, materials)) {
            return;
        }

        tryInvoke(meta, "setDestroyableKeys", Collection.class, toNamespacedKeys(materials));
    }

    private void applyCanPlaceOn(ItemMeta meta, Set<Material> materials) {
        if (tryInvoke(meta, "setCanPlaceOn", Set.class, materials)) {
            return;
        }

        tryInvoke(meta, "setPlaceableKeys", Collection.class, toNamespacedKeys(materials));
    }

    private Set<NamespacedKey> toNamespacedKeys(Set<Material> materials) {
        Set<NamespacedKey> keys = new LinkedHashSet<>();
        for (Material material : materials) {
            keys.add(material.getKey());
        }
        return keys;
    }

    private boolean tryInvoke(ItemMeta meta, String methodName, Class<?> parameterType, Object value) {
        try {
            Method method = meta.getClass().getMethod(methodName, parameterType);
            method.invoke(meta, value);
            return true;
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }
}
