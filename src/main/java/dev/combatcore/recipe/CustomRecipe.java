package dev.combatcore.recipe;

import org.bukkit.inventory.ItemStack;

/** A recipe created in the /cr editor. Grid slots are 0-8, left-to-right, top-to-bottom. */
public final class CustomRecipe {
    public final String id;
    public boolean shaped = true;
    public boolean enabled = true;
    public ItemStack result;
    public final ItemStack[] grid = new ItemStack[9];

    public CustomRecipe(String id) {
        this.id = id;
    }
}
