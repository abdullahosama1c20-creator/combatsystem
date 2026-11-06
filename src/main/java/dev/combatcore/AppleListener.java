package dev.combatcore;

import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.inventory.ItemStack;

import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/** Makes apples drop far more often from tree leaves. */
public final class AppleListener implements Listener {

    private final CombatCore plugin;
    private final Set<Material> leaves = EnumSet.noneOf(Material.class);

    public AppleListener(CombatCore plugin) {
        this.plugin = plugin;
        for (String name : plugin.getConfig().getStringList("apples.leaf-types")) {
            Material m = Material.matchMaterial(name);
            if (m != null) leaves.add(m);
            else plugin.getLogger().warning("Unknown leaf type in config: " + name);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (!plugin.getConfig().getBoolean("apples.enabled", true)) return;
        Block b = e.getBlock();
        if (!leaves.contains(b.getType())) return;
        Player p = e.getPlayer();
        if (p.getGameMode() == GameMode.CREATIVE) return;
        ItemStack tool = p.getInventory().getItemInMainHand();
        if (tool.getType() == Material.SHEARS || tool.containsEnchantment(Enchantment.SILK_TOUCH)) return;
        roll(b);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDecay(LeavesDecayEvent e) {
        if (!plugin.getConfig().getBoolean("apples.enabled", true)) return;
        if (leaves.contains(e.getBlock().getType())) roll(e.getBlock());
    }

    private void roll(Block b) {
        double chance = plugin.getConfig().getDouble("apples.chance", 0.05);
        if (ThreadLocalRandom.current().nextDouble() < chance) {
            b.getWorld().dropItemNaturally(b.getLocation().add(0.5, 0.5, 0.5), new ItemStack(Material.APPLE));
        }
    }
}
