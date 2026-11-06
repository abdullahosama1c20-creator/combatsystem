package dev.combatcore;

import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Keeps track of who is "in combat" and until when. */
public final class CombatManager {

    private final CombatCore plugin;
    private final Map<UUID, Long> expiry = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> opponents = new ConcurrentHashMap<>();
    private final Set<UUID> loggedOut = ConcurrentHashMap.newKeySet();

    public CombatManager(CombatCore plugin) {
        this.plugin = plugin;
    }

    public void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 10L, 10L);
    }

    private long durationMs() {
        return Math.max(1, plugin.getConfig().getInt("combat.duration-seconds", 15)) * 1000L;
    }

    public boolean isInCombat(UUID id) {
        Long end = expiry.get(id);
        return end != null && end > System.currentTimeMillis();
    }

    public boolean isInCombat(Player p) {
        return isInCombat(p.getUniqueId());
    }

    public void tag(Player a, Player b) {
        tagOne(a, b.getUniqueId());
        tagOne(b, a.getUniqueId());
    }

    public void tagOne(Player p, UUID opponent) {
        boolean already = isInCombat(p);
        expiry.put(p.getUniqueId(), System.currentTimeMillis() + durationMs());
        if (opponent != null) opponents.put(p.getUniqueId(), opponent);
        if (!already) {
            p.sendMessage(plugin.msg("combat-start",
                    Placeholder.unparsed("seconds", String.valueOf(durationMs() / 1000))));
        }
    }

    public UUID opponentOf(UUID id) {
        return opponents.get(id);
    }

    public void untag(UUID id) {
        expiry.remove(id);
        opponents.remove(id);
    }

    public void markLoggedOut(UUID id) {
        loggedOut.add(id);
    }

    /** @return true if this player was just killed for combat logging. */
    public boolean consumeLoggedOut(UUID id) {
        return loggedOut.remove(id);
    }

    private void tick() {
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, Long> en : expiry.entrySet()) {
            UUID id = en.getKey();
            Player p = Bukkit.getPlayer(id);
            long left = en.getValue() - now;
            if (left <= 0) {
                untag(id);
                if (p != null) p.sendMessage(plugin.msg("combat-end"));
                continue;
            }
            if (p != null) {
                p.sendActionBar(plugin.msg("actionbar",
                        Placeholder.unparsed("seconds", String.valueOf((left + 999) / 1000))));
            }
        }
    }
}
