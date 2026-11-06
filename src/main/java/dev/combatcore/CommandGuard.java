package dev.combatcore;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerCommandSendEvent;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * 1) /plugins and /version (and aliases) are ALWAYS blocked and hidden, for everyone.
 * 2) While in combat only the configured allow-list (msg, w, ...) works.
 */
public final class CommandGuard implements Listener {

    private final CombatCore plugin;
    private final CombatManager combat;
    private final Set<String> always = new HashSet<>();
    private final Set<String> allowed = new HashSet<>();

    public CommandGuard(CombatCore plugin, CombatManager combat) {
        this.plugin = plugin;
        this.combat = combat;
        for (String s : plugin.getConfig().getStringList("always-blocked-commands")) always.add(norm(s));
        for (String s : plugin.getConfig().getStringList("combat.allowed-commands")) allowed.add(norm(s));
        // Hard defaults so a broken config can never re-enable them.
        always.addAll(Set.of("plugins", "pl", "version", "ver", "about", "icanhasbukkit"));
        allowed.add("msg");
        allowed.add("w");
    }

    private static String norm(String s) {
        s = s.toLowerCase(Locale.ROOT).trim();
        int i = s.lastIndexOf(':');
        return i >= 0 ? s.substring(i + 1) : s;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        Player p = e.getPlayer();
        String body = e.getMessage().trim();
        if (body.startsWith("/")) body = body.substring(1).trim();
        if (body.isEmpty()) return;
        String[] tokens = body.split("\\s+");
        String label = norm(tokens[0]);

        if (isAlwaysBlocked(label, tokens)) {
            e.setCancelled(true);
            p.sendMessage(plugin.msg("command-blocked-always"));
            return;
        }

        if (combat.isInCombat(p) && !p.hasPermission("combatcore.bypass") && !allowed.contains(label)) {
            e.setCancelled(true);
            p.sendMessage(plugin.msg("command-blocked-combat"));
        }
    }

    private boolean isAlwaysBlocked(String label, String[] tokens) {
        if (always.contains(label)) return true;

        Command cmd = Bukkit.getCommandMap().getCommand(label);
        if (cmd != null) {
            if (always.contains(norm(cmd.getName()))) return true;
            for (String alias : cmd.getAliases()) {
                if (always.contains(norm(alias))) return true;
            }
        }

        // /execute ... run plugins
        for (int i = 0; i + 1 < tokens.length; i++) {
            if (tokens[i].equalsIgnoreCase("run") && always.contains(norm(tokens[i + 1]))) return true;
        }
        return false;
    }

    /** Hide the blocked commands from tab-complete / the client command tree. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSend(PlayerCommandSendEvent e) {
        e.getCommands().removeIf(c -> always.contains(norm(c)));
    }
}
