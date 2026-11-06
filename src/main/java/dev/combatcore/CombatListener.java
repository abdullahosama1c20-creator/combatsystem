package dev.combatcore;

import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Combat tagging, combat-log punishment and the death/drop rules. */
public final class CombatListener implements Listener {

    private final CombatCore plugin;
    private final CombatManager combat;
    private final Set<UUID> kicked = new HashSet<>();

    public CombatListener(CombatCore plugin, CombatManager combat) {
        this.plugin = plugin;
        this.combat = combat;
    }

    // ---------------------------------------------------------------- tagging

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player victim)) return;
        if (victim.hasMetadata("NPC") || exempt(victim)) return;

        Player attacker = resolveAttacker(e.getDamager());
        if (attacker != null) {
            if (attacker.equals(victim) || attacker.hasMetadata("NPC") || exempt(attacker)) return;
            combat.tag(victim, attacker);
        } else if (plugin.getConfig().getBoolean("combat.tag-on-mob-damage", false)
                && e.getDamager() instanceof LivingEntity) {
            combat.tagOne(victim, null);
        }
    }

    private boolean exempt(Player p) {
        if (!plugin.getConfig().getBoolean("combat.ignore-creative", true)) return false;
        GameMode gm = p.getGameMode();
        return gm == GameMode.CREATIVE || gm == GameMode.SPECTATOR;
    }

    private Player resolveAttacker(Entity damager) {
        if (damager instanceof Player p) return p;
        if (damager instanceof Projectile proj && proj.getShooter() instanceof Player shooter) return shooter;
        return null;
    }

    // ---------------------------------------------------------- combat logging

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onKick(PlayerKickEvent e) {
        if (!plugin.getConfig().getBoolean("combat.punish-on-kick", false)) {
            kicked.add(e.getPlayer().getUniqueId());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        UUID id = p.getUniqueId();
        boolean excused = kicked.remove(id) || Bukkit.isStopping();

        if (excused || !combat.isInCombat(id) || p.isDead()) {
            combat.untag(id);
            return;
        }

        combat.markLoggedOut(id);
        UUID oppId = combat.opponentOf(id);
        Player opponent = oppId == null ? null : Bukkit.getPlayer(oppId);
        try {
            // Best effort: register the opponent as the damager so they get kill credit.
            if (opponent != null && opponent.isOnline()) p.damage(0.001, opponent);
            p.setHealth(0.0);
        } finally {
            combat.consumeLoggedOut(id);
            combat.untag(id);
        }
    }

    // ------------------------------------------------------------------ deaths

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(PlayerDeathEvent e) {
        Player p = e.getPlayer();
        boolean logged = combat.consumeLoggedOut(p.getUniqueId());
        boolean inCombat = logged || combat.isInCombat(p);

        if (logged) {
            e.deathMessage(plugin.msg("combat-log-death", Placeholder.unparsed("player", p.getName())));
        }

        if (inCombat) {
            // Drop EVERYTHING: items + XP.
            e.setKeepInventory(false);
            e.setKeepLevel(false);
            List<ItemStack> drops = e.getDrops();
            drops.clear();
            for (ItemStack it : p.getInventory().getContents()) {
                if (it == null || it.getType().isAir()) continue;
                if (it.containsEnchantment(Enchantment.VANISHING_CURSE)) continue;
                drops.add(it.clone());
            }
            if (plugin.getConfig().getBoolean("combat.drop-full-xp", true)) {
                e.setDroppedExp(Math.max(0, p.getTotalExperience()));
            }
            e.setNewExp(0);
            e.setNewLevel(0);
            e.setNewTotalExp(0);
        } else {
            // Out of combat: drop NOTHING, keep everything.
            e.setKeepInventory(true);
            e.setKeepLevel(true);
            e.getDrops().clear();
            e.setDroppedExp(0);
        }
        combat.untag(p.getUniqueId());
    }
}
