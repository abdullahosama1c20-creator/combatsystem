package dev.combatcore;

import dev.combatcore.recipe.CrCommand;
import dev.combatcore.recipe.RecipeGui;
import dev.combatcore.recipe.RecipeManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.plugin.java.JavaPlugin;

public final class CombatCore extends JavaPlugin {

    private CombatManager combat;
    private RecipeManager recipes;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        combat = new CombatManager(this);
        recipes = new RecipeManager(this);
        RecipeGui gui = new RecipeGui(this, recipes);

        getServer().getPluginManager().registerEvents(new CombatListener(this, combat), this);
        getServer().getPluginManager().registerEvents(new CommandGuard(this, combat), this);
        getServer().getPluginManager().registerEvents(new AppleListener(this), this);
        getServer().getPluginManager().registerEvents(recipes, this);
        getServer().getPluginManager().registerEvents(gui, this);

        registerCommand("cr", "Open the recipe editor (OP only)", new CrCommand(gui));

        recipes.load();
        combat.start();
        getLogger().info("CombatCore enabled.");
    }

    public CombatManager combat() {
        return combat;
    }

    public Component msg(String key, TagResolver... resolvers) {
        return MiniMessage.miniMessage().deserialize(getConfig().getString("messages." + key, key), resolvers);
    }
}
