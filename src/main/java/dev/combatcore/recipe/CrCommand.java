package dev.combatcore.recipe;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class CrCommand implements BasicCommand {

    private final RecipeGui gui;

    public CrCommand(RecipeGui gui) {
        this.gui = gui;
    }

    @Override
    public void execute(CommandSourceStack stack, String[] args) {
        CommandSender sender = stack.getSender();
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Only players can use /cr.");
            return;
        }
        if (!canUse(p)) {
            p.sendMessage(Component.text("You don't have permission to do that.", NamedTextColor.RED));
            return;
        }
        gui.open(p, false);
    }

    @Override
    public boolean canUse(CommandSender sender) {
        return sender.isOp() || sender.hasPermission("combatcore.recipes");
    }
}
