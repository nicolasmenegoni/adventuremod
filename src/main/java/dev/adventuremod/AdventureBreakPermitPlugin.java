package dev.adventuremod;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.LinkedHashSet;
import java.util.UUID;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

public final class AdventureBreakPermitPlugin extends JavaPlugin implements Listener {

    private final Set<Material> canPlaceOnBlocks = new HashSet<>();
    private final Set<Material> easyDroppedItems = new HashSet<>();
    private final Map<String, BlockDamageState> blockDamageStates = new HashMap<>();
    private final Set<Material> temporaryTorchBlocks = Set.of(
        Material.TORCH,
        Material.SOUL_TORCH,
        Material.REDSTONE_TORCH
    );

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
        getServer().getScheduler().runTaskTimer(this, () -> {
            for (Player player : getServer().getOnlinePlayers()) {
                if (player.getGameMode() == GameMode.ADVENTURE) {
                    unstackPlayerInventory(player);
                }
            }
        }, 40L, 40L);
    }

    @Override
    public void reloadConfig() {
        super.reloadConfig();
        reloadPluginConfig();
    }

    private void reloadPluginConfig() {
        canPlaceOnBlocks.clear();
        easyDroppedItems.clear();

        FileConfiguration config = getConfig();
        loadMaterialList(config.getStringList("canplaceon-itens"), canPlaceOnBlocks, "canplaceon-itens");
        loadMaterialList(config.getStringList("easy-dropped-itens"), easyDroppedItems, "easy-dropped-itens");

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
        getServer().getScheduler().runTaskLater(this, () -> unstackPlayerInventory(player), 1L);
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
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getEntity();
        if (player.getGameMode() != GameMode.ADVENTURE) {
            return;
        }

        ItemStack picked = event.getItem().getItemStack();
        if (picked.getAmount() > 1) {
            event.setCancelled(true);
            int amount = picked.getAmount();
            ItemStack base = picked.clone();
            base.setAmount(1);
            for (int i = 0; i < amount; i++) {
                ItemStack single = base.clone();
                applyRulesToItem(single);
                player.getInventory().addItem(single);
            }
            event.getItem().remove();
            getServer().getScheduler().runTaskLater(this, () -> unstackPlayerInventory(player), 1L);
            player.updateInventory();
            return;
        }

        // Apply only when item is picked; avoid scanning entire inventory.
        applyRulesToItem(picked);
        getServer().getScheduler().runTaskLater(this, () -> unstackPlayerInventory(player), 1L);
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            getServer().getScheduler().runTaskLater(this, () -> unstackPlayerInventory(player), 1L);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            getServer().getScheduler().runTaskLater(this, () -> unstackPlayerInventory(player), 1L);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEasyDropBreak(PlayerInteractEvent event) {
        if (event.getAction() != Action.LEFT_CLICK_BLOCK) {
            return;
        }

        Player player = event.getPlayer();
        if (player.getGameMode() != GameMode.ADVENTURE || event.getClickedBlock() == null) {
            return;
        }

        Material type = event.getClickedBlock().getType();

        ItemStack hand = player.getInventory().getItemInMainHand();
        if (!hasProperTool(hand, type)) {
            handleCustomBlockDamage(player, event.getClickedBlock());
            event.setCancelled(true);
            return;
        }

        if (!easyDroppedItems.contains(type)) {
            return;
        }

        Block block = event.getClickedBlock();
        Sound breakSound = block.getBlockData().getSoundGroup().getBreakSound();
        block.breakNaturally(player.getInventory().getItemInMainHand());
        player.playSound(block.getLocation(), breakSound, 1.0f, 1.0f);
        event.setCancelled(true);
    }

    private void handleCustomBlockDamage(Player player, Block block) {
        String key = buildDamageKey(player.getUniqueId(), block);
        BlockDamageState state = blockDamageStates.get(key);
        if (state == null || state.remaining <= 0) {
            state = createDamageState(block);
            blockDamageStates.put(key, state);
        }

        state.remaining--;
        updateHologram(block, state);
        player.playSound(block.getLocation(), block.getBlockData().getSoundGroup().getHitSound(), 1.0f, 1.0f);

        if (state.remaining <= 0) {
            removeHologram(block.getWorld(), state);
            blockDamageStates.remove(key);
            block.breakNaturally(player.getInventory().getItemInMainHand());
        }
    }

    private BlockDamageState createDamageState(Block block) {
        ArmorStand hologram = block.getWorld().spawn(block.getLocation().add(0.5, 1.2, 0.5), ArmorStand.class, stand -> {
            stand.setInvisible(true);
            stand.setGravity(false);
            stand.setMarker(true);
            stand.setCustomNameVisible(true);
        });
        return new BlockDamageState(64, hologram.getUniqueId());
    }

    private void updateHologram(Block block, BlockDamageState state) {
        Entity entity = block.getWorld().getEntity(state.hologramId);
        if (entity instanceof ArmorStand stand) {
            stand.setCustomName("§f" + state.remaining + "/64");
        }
    }

    private void removeHologram(World world, BlockDamageState state) {
        Entity entity = world.getEntity(state.hologramId);
        if (entity != null) {
            entity.remove();
        }
    }

    private String buildDamageKey(UUID playerId, Block block) {
        return playerId + ":" + block.getWorld().getName() + ":" + block.getX() + ":" + block.getY() + ":" + block.getZ();
    }

    private boolean hasProperTool(ItemStack item, Material blockType) {
        if (item == null || item.getType().isAir()) {
            return false;
        }

        String name = item.getType().name();
        if (name.endsWith("_SHOVEL")) {
            return shovelBlocks.contains(blockType);
        }
        if (name.equals("WOODEN_PICKAXE")) {
            return woodPickaxeBlocks.contains(blockType);
        }
        if (name.equals("STONE_PICKAXE")) {
            return stonePickaxeBlocks.contains(blockType);
        }
        if (name.endsWith("_PICKAXE")) {
            return buildGenericPickaxeBlocks().contains(blockType);
        }
        if (name.endsWith("_AXE")) {
            return buildAxeBlocks().contains(blockType);
        }

        return false;
    }

    private static final class BlockDamageState {
        private int remaining;
        private final UUID hologramId;

        private BlockDamageState(int remaining, UUID hologramId) {
            this.remaining = remaining;
            this.hologramId = hologramId;
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onChunkLoad(ChunkLoadEvent event) {
        World world = event.getWorld();
        int minY = world.getMinHeight();
        int maxY = world.getMaxHeight();
        int baseX = event.getChunk().getX() << 4;
        int baseZ = event.getChunk().getZ() << 4;

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = minY; y < maxY; y++) {
                    Block block = world.getBlockAt(baseX + x, y, baseZ + z);
                    if (block.getType() == Material.LAVA) {
                        block.setType(Material.AIR, false);
                    }
                }
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Block placed = event.getBlockPlaced();
        if (!temporaryTorchBlocks.contains(placed.getType())) {
            return;
        }

        getServer().getScheduler().runTaskLater(this, () -> {
            if (placed.getType() == Material.TORCH
                || placed.getType() == Material.SOUL_TORCH
                || placed.getType() == Material.REDSTONE_TORCH) {
                placed.setType(Material.AIR, false);
                placed.getWorld().dropItemNaturally(placed.getLocation(), new ItemStack(Material.STICK, 1));
            }
        }, 20L * 30L);
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
            Set<Material> placeOn = new HashSet<>(canPlaceOnBlocks);
            placeOn.add(type);
            applyCanPlaceOn(meta, placeOn);
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

        meta.addItemFlags(ItemFlag.HIDE_DESTROYS, ItemFlag.HIDE_PLACED_ON);
        meta.setMaxStackSize(1);
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
        Set<NamespacedKey> keys = new LinkedHashSet<>();
        for (Material material : materials) {
            keys.add(material.getKey());
        }
        meta.setDestroyableKeys((Collection) keys);
    }

    private void applyCanPlaceOn(ItemMeta meta, Set<Material> materials) {
        Set<NamespacedKey> keys = new LinkedHashSet<>();
        for (Material material : materials) {
            keys.add(material.getKey());
        }
        meta.setPlaceableKeys((Collection) keys);
    }

    private void unstackPlayerInventory(Player player) {
        var inventory = player.getInventory();
        ItemStack[] contents = inventory.getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack stack = contents[slot];
            if (stack == null || stack.getType().isAir() || stack.getAmount() <= 1) {
                continue;
            }

            int amount = stack.getAmount();
            ItemStack base = stack.clone();
            base.setAmount(1);
            inventory.setItem(slot, base);
            int remaining = amount - 1;

            while (remaining > 0) {
                ItemStack single = base.clone();
                single.setAmount(1);
                int empty = inventory.firstEmpty();
                if (empty == -1) {
                    player.getWorld().dropItemNaturally(player.getLocation(), single);
                } else {
                    inventory.setItem(empty, single);
                }
                remaining--;
            }
        }
        player.updateInventory();
    }

}
