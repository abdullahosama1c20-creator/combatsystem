package dev.combatcore.recipe;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.CookingRecipe;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.SmithingRecipe;
import org.bukkit.inventory.StonecuttingRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/** The /cr GUI: recipe list (custom + vanilla) and the recipe editor. */
public final class RecipeGui implements Listener {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    // Editor layout (6-row chest)
    private static final int[] GRID = {10, 11, 12, 19, 20, 21, 28, 29, 30};
    private static final int HELP = 0, HELD = 4, PLUS = 15, ARROW = 23, RESULT = 24, MINUS = 33;
    private static final int BACK = 45, TYPE = 47, CLEAR = 48, SAVE = 49, ENABLED = 51, DELETE = 53;

    // List layout
    private static final int PAGE_SIZE = 45;
    private static final int L_PREV = 45, L_NEW = 47, L_SEARCH = 48, L_PAGE = 49, L_FILTER = 50, L_SWITCH = 51,
            L_CLOSE = 52, L_NEXT = 53;

    private final JavaPlugin plugin;
    private final RecipeManager rm;
    private final Map<UUID, Consumer<String>> awaitingChat = new ConcurrentHashMap<>();

    public RecipeGui(JavaPlugin plugin, RecipeManager rm) {
        this.plugin = plugin;
        this.rm = rm;
    }

    // ===================================================================== holders

    abstract static class Holder implements InventoryHolder {
        Inventory inv;

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    static final class ListHolder extends Holder {
        boolean vanilla;
        int page;
        String search = "";
        boolean disabledOnly;
        List<ListEntry> entries = new ArrayList<>();
    }

    static final class EditHolder extends Holder {
        String id; // null = new recipe
        boolean shaped = true;
        boolean enabled = true;
        final ItemStack[] grid = new ItemStack[9];
        ItemStack result;
        ItemStack held;
        NamespacedKey replaceVanilla;
    }

    record ListEntry(CustomRecipe custom, NamespacedKey key, Recipe recipe) {
    }

    // ===================================================================== helpers

    private static Component c(String mini) {
        return MM.deserialize(mini).decoration(TextDecoration.ITALIC, false);
    }

    private static ItemStack item(Material m, String name, String... lore) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(c(name));
        if (lore.length > 0) {
            List<Component> l = new ArrayList<>();
            for (String s : lore) l.add(c(s));
            meta.lore(l);
        }
        it.setItemMeta(meta);
        return it;
    }

    private static ItemStack pane() {
        return item(Material.GRAY_STAINED_GLASS_PANE, " ");
    }

    private static ItemStack one(ItemStack s) {
        ItemStack o = s.clone();
        o.setAmount(1);
        return o;
    }

    private static boolean empty(ItemStack s) {
        return s == null || s.getType().isAir();
    }

    private void say(Player p, String mini) {
        p.sendMessage(MM.deserialize(mini));
    }

    private void later(Runnable r) {
        Bukkit.getScheduler().runTask(plugin, r);
    }

    private static boolean allowed(Player p) {
        return p.isOp() || p.hasPermission("combatcore.recipes");
    }

    // ===================================================================== list view

    public void open(Player p, boolean vanilla) {
        ListHolder h = new ListHolder();
        h.vanilla = vanilla;
        openList(p, h);
    }

    private void openList(Player p, ListHolder h) {
        h.inv = Bukkit.createInventory(h, 54,
                c(h.vanilla ? "<dark_gray>Recipes » <white>Vanilla" : "<dark_gray>Recipes » <white>Custom"));
        populate(h);
        p.openInventory(h.inv);
    }

    private static boolean matches(String search, String hay) {
        if (search == null || search.isBlank()) return true;
        return hay.toLowerCase().contains(search.toLowerCase().trim());
    }

    private void populate(ListHolder h) {
        Inventory inv = h.inv;
        inv.clear();

        List<ListEntry> list = new ArrayList<>();
        if (!h.vanilla) {
            for (CustomRecipe cr : rm.customs()) {
                if (matches(h.search, cr.id + " " + cr.result.getType().name())) {
                    list.add(new ListEntry(cr, null, null));
                }
            }
        } else {
            for (Map.Entry<NamespacedKey, Recipe> en : rm.vanilla()) {
                boolean dis = rm.isDisabled(en.getKey());
                if (h.disabledOnly && !dis) continue;
                if (!matches(h.search, en.getKey().getKey() + " " + en.getValue().getResult().getType().name())) continue;
                list.add(new ListEntry(null, en.getKey(), en.getValue()));
            }
        }
        h.entries = list;

        int pages = Math.max(1, (list.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        h.page = Math.max(0, Math.min(h.page, pages - 1));
        for (int i = 0; i < PAGE_SIZE; i++) {
            int idx = h.page * PAGE_SIZE + i;
            if (idx >= list.size()) break;
            inv.setItem(i, render(list.get(idx)));
        }

        for (int i = 45; i < 54; i++) inv.setItem(i, pane());
        if (h.page > 0) inv.setItem(L_PREV, item(Material.ARROW, "<yellow>◀ Previous page"));
        if (h.page < pages - 1) inv.setItem(L_NEXT, item(Material.ARROW, "<yellow>Next page ▶"));
        inv.setItem(L_PAGE, item(Material.PAPER, "<white>Page " + (h.page + 1) + " / " + pages,
                "<gray>" + list.size() + " recipes shown"));
        if (!h.vanilla) {
            inv.setItem(L_NEW, item(Material.CRAFTING_TABLE, "<green>+ New recipe",
                    "<gray>Open the recipe editor."));
        }
        inv.setItem(L_SEARCH, item(Material.OAK_SIGN, "<yellow>Search",
                h.search.isBlank() ? "<gray>Current: <white>(none)" : "<gray>Current: <white>" + h.search,
                "<gray>Left-click: type a search in chat",
                "<gray>Right-click: clear search"));
        if (h.vanilla) {
            inv.setItem(L_FILTER, item(Material.HOPPER, "<yellow>Show: " + (h.disabledOnly ? "<red>disabled only" : "<white>all"),
                    "<gray>Click to toggle"));
        }
        inv.setItem(L_SWITCH, item(Material.BOOKSHELF,
                h.vanilla ? "<aqua>View custom recipes" : "<aqua>View vanilla recipes",
                h.vanilla ? "<gray>Switch to your own recipes." : "<gray>Disable or copy built-in recipes."));
        inv.setItem(L_CLOSE, item(Material.BARRIER, "<red>Close"));
    }

    private ItemStack render(ListEntry en) {
        boolean custom = en.custom() != null;
        ItemStack base = custom ? en.custom().result : en.recipe().getResult();
        ItemStack it = base.clone();
        it.setAmount(Math.max(1, Math.min(base.getAmount(), it.getMaxStackSize())));
        ItemMeta m = it.getItemMeta();

        boolean enabled = custom ? en.custom().enabled : !rm.isDisabled(en.key());
        if (!enabled) {
            m.displayName(c("<red>✘ ").append(Component.translatable(it.getType().translationKey())
                    .color(NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false)));
        }

        List<Component> lore = new ArrayList<>();
        if (m.lore() != null) lore.addAll(m.lore());
        lore.add(Component.empty());
        if (custom) {
            CustomRecipe cr = en.custom();
            lore.add(c("<gray>ID: <white>" + cr.id));
            lore.add(c("<gray>Type: <white>" + (cr.shaped ? "Shaped" : "Shapeless")));
            lore.add(c(cr.enabled ? "<green>● Enabled" : "<red>● Disabled"));
            lore.add(Component.empty());
            lore.add(c("<yellow>Left-click: <white>edit"));
            lore.add(c("<yellow>Right-click: <white>" + (cr.enabled ? "disable" : "enable")));
            lore.add(c("<red>Shift + right-click: <white>delete"));
        } else {
            Recipe r = en.recipe();
            lore.add(c("<gray>" + en.key()));
            lore.add(c("<gray>Type: <white>" + typeName(r)));
            lore.add(c(enabled ? "<green>● Enabled" : "<red>● Disabled"));
            lore.add(Component.empty());
            lore.add(c("<yellow>Left-click: <white>" + (enabled ? "disable" : "enable")));
            if (editable(r)) lore.add(c("<yellow>Right-click: <white>edit a copy"));
        }
        m.lore(lore);
        it.setItemMeta(m);
        return it;
    }

    private static boolean editable(Recipe r) {
        return r instanceof ShapedRecipe || r instanceof ShapelessRecipe;
    }

    private static String typeName(Recipe r) {
        if (r instanceof ShapedRecipe) return "Shaped crafting";
        if (r instanceof ShapelessRecipe) return "Shapeless crafting";
        if (r instanceof CookingRecipe<?>) return "Cooking";
        if (r instanceof StonecuttingRecipe) return "Stonecutting";
        if (r instanceof SmithingRecipe) return "Smithing";
        return "Special";
    }

    private void onListClick(InventoryClickEvent e, Player p, ListHolder h) {
        int s = e.getRawSlot();
        if (s < 0) return;

        if (s < PAGE_SIZE) {
            int idx = h.page * PAGE_SIZE + s;
            if (idx >= h.entries.size()) return;
            ListEntry en = h.entries.get(idx);

            if (en.custom() != null) {
                CustomRecipe cr = en.custom();
                if (e.isRightClick() && e.isShiftClick()) {
                    rm.delete(cr.id);
                    populate(h);
                } else if (e.isRightClick()) {
                    String err = rm.setEnabled(cr, !cr.enabled);
                    if (err != null) say(p, "<red>" + err);
                    populate(h);
                } else {
                    EditHolder eh = fromCustom(cr);
                    later(() -> openEditor(p, eh));
                }
            } else {
                NamespacedKey k = en.key();
                if (e.isRightClick()) {
                    if (!editable(en.recipe())) {
                        say(p, "<red>Only shaped and shapeless crafting recipes can be edited.");
                        return;
                    }
                    EditHolder eh = fromVanilla(en.recipe(), k);
                    later(() -> {
                        openEditor(p, eh);
                        say(p, "<yellow>Editing a copy of <white>" + k + "</white>. The original is disabled when you press Save. "
                                + "Tag ingredients (e.g. \"any planks\") are copied as their first matching item.");
                    });
                } else {
                    if (rm.isDisabled(k)) {
                        if (!rm.enableVanilla(k)) say(p, "<yellow>Re-enabled. It will be fully restored after a server restart.");
                    } else {
                        rm.disableVanilla(k);
                    }
                    populate(h);
                }
            }
            return;
        }

        switch (s) {
            case L_PREV -> {
                if (h.page > 0) {
                    h.page--;
                    populate(h);
                }
            }
            case L_NEXT -> {
                h.page++;
                populate(h);
            }
            case L_NEW -> {
                if (!h.vanilla) {
                    EditHolder eh = new EditHolder();
                    later(() -> openEditor(p, eh));
                }
            }
            case L_SEARCH -> {
                if (e.isRightClick()) {
                    h.search = "";
                    h.page = 0;
                    populate(h);
                } else {
                    awaitingChat.put(p.getUniqueId(), text -> {
                        h.search = text.equalsIgnoreCase("cancel") ? h.search : (text.equalsIgnoreCase("clear") ? "" : text);
                        h.page = 0;
                        openList(p, h);
                    });
                    later(p::closeInventory);
                    say(p, "<yellow>Type a search in chat. <gray>(<white>cancel</white> to abort, <white>clear</white> to reset)");
                }
            }
            case L_FILTER -> {
                if (h.vanilla) {
                    h.disabledOnly = !h.disabledOnly;
                    h.page = 0;
                    populate(h);
                }
            }
            case L_SWITCH -> {
                boolean v = !h.vanilla;
                later(() -> open(p, v));
            }
            case L_CLOSE -> later(p::closeInventory);
            default -> {
            }
        }
    }

    // ===================================================================== editor

    private static EditHolder fromCustom(CustomRecipe cr) {
        EditHolder h = new EditHolder();
        h.id = cr.id;
        h.shaped = cr.shaped;
        h.enabled = cr.enabled;
        h.result = cr.result == null ? null : cr.result.clone();
        for (int i = 0; i < 9; i++) h.grid[i] = cr.grid[i] == null ? null : cr.grid[i].clone();
        return h;
    }

    private static EditHolder fromVanilla(Recipe r, NamespacedKey key) {
        EditHolder h = new EditHolder();
        h.replaceVanilla = key;
        h.result = r.getResult().clone();
        if (r instanceof ShapedRecipe sr) {
            h.shaped = true;
            String[] shape = sr.getShape();
            Map<Character, RecipeChoice> map = sr.getChoiceMap();
            for (int row = 0; row < shape.length && row < 3; row++) {
                for (int col = 0; col < shape[row].length() && col < 3; col++) {
                    char ch = shape[row].charAt(col);
                    if (ch == ' ') continue;
                    h.grid[row * 3 + col] = firstItem(map.get(ch));
                }
            }
        } else if (r instanceof ShapelessRecipe sl) {
            h.shaped = false;
            int i = 0;
            for (RecipeChoice rc : sl.getChoiceList()) {
                if (i >= 9) break;
                h.grid[i++] = firstItem(rc);
            }
        }
        return h;
    }

    private static ItemStack firstItem(RecipeChoice rc) {
        if (rc instanceof RecipeChoice.MaterialChoice mc && !mc.getChoices().isEmpty()) {
            return new ItemStack(mc.getChoices().get(0));
        }
        if (rc instanceof RecipeChoice.ExactChoice ec && !ec.getChoices().isEmpty()) {
            return one(ec.getChoices().get(0));
        }
        return null;
    }

    private void openEditor(Player p, EditHolder h) {
        h.inv = Bukkit.createInventory(h, 54,
                c("<dark_gray>Recipe Editor » <white>" + (h.id == null ? "New" : h.id)));
        renderEditor(h);
        p.openInventory(h.inv);
    }

    private void renderEditor(EditHolder h) {
        Inventory inv = h.inv;
        for (int i = 0; i < 54; i++) inv.setItem(i, pane());

        for (int i = 0; i < 9; i++) inv.setItem(GRID[i], h.grid[i] == null ? null : h.grid[i].clone());

        inv.setItem(HELP, item(Material.BOOK, "<yellow>How to use",
                "<gray>1. Click an item in <white>your inventory</white> to select it.",
                "<gray>2. Click a grid slot or the result slot to place it.",
                "<gray>3. Right-click a slot to clear it.",
                "<gray>Items are copied, never consumed."));

        if (h.held != null) {
            ItemStack held = h.held.clone();
            ItemMeta hm = held.getItemMeta();
            List<Component> l = new ArrayList<>();
            if (hm.lore() != null) l.addAll(hm.lore());
            l.add(Component.empty());
            l.add(c("<yellow>Selected — click a slot to place it"));
            hm.lore(l);
            held.setItemMeta(hm);
            inv.setItem(HELD, held);
        } else {
            inv.setItem(HELD, item(Material.LIGHT_GRAY_STAINED_GLASS_PANE, "<gray>Nothing selected",
                    "<dark_gray>Click an item in your inventory."));
        }

        inv.setItem(ARROW, item(Material.ARROW, "<gray>makes"));
        if (h.result == null) {
            inv.setItem(RESULT, item(Material.LIME_STAINED_GLASS_PANE, "<green>Result slot",
                    "<gray>Select an item, then click here."));
        } else {
            inv.setItem(RESULT, h.result.clone());
        }
        int amt = h.result == null ? 0 : h.result.getAmount();
        inv.setItem(PLUS, item(Material.LIME_DYE, "<green>+ Amount", "<gray>Result amount: <white>" + amt,
                "<gray>Click: +1   Shift-click: +8"));
        inv.setItem(MINUS, item(Material.RED_DYE, "<red>- Amount", "<gray>Result amount: <white>" + amt,
                "<gray>Click: -1   Shift-click: -8"));

        inv.setItem(BACK, item(Material.ARROW, "<yellow>◀ Back", "<gray>Discard changes."));
        inv.setItem(TYPE, h.shaped
                ? item(Material.CRAFTING_TABLE, "<yellow>Type: <white>Shaped", "<gray>Position matters.", "<gray>Click to switch to shapeless.")
                : item(Material.ENDER_PEARL, "<yellow>Type: <white>Shapeless", "<gray>Position doesn't matter.", "<gray>Click to switch to shaped."));
        inv.setItem(CLEAR, item(Material.LAVA_BUCKET, "<red>Clear grid"));
        inv.setItem(SAVE, item(Material.EMERALD_BLOCK, "<green>✔ Save recipe"));
        inv.setItem(ENABLED, h.enabled
                ? item(Material.LIME_DYE, "<green>● Enabled", "<gray>Click to disable.")
                : item(Material.GRAY_DYE, "<red>● Disabled", "<gray>Click to enable."));
        if (h.id != null) inv.setItem(DELETE, item(Material.TNT, "<red>Delete recipe", "<gray>Permanently removes it."));
    }

    private static int gridIndex(int slot) {
        for (int i = 0; i < GRID.length; i++) if (GRID[i] == slot) return i;
        return -1;
    }

    private void onEditClick(InventoryClickEvent e, Player p, EditHolder h) {
        int raw = e.getRawSlot();
        if (raw < 0) return;

        // Clicked in the player's own inventory -> select that item.
        if (raw >= h.inv.getSize()) {
            ItemStack clicked = e.getCurrentItem();
            if (!empty(clicked)) {
                h.held = one(clicked);
                renderEditor(h);
            }
            return;
        }

        ItemStack cursor = e.getCursor();
        ItemStack source = h.held != null ? h.held : (!empty(cursor) ? cursor : null);

        int gi = gridIndex(raw);
        if (gi >= 0) {
            h.grid[gi] = (e.isRightClick() || source == null) ? null : one(source);
            renderEditor(h);
            return;
        }

        switch (raw) {
            case RESULT -> {
                if (e.isRightClick() || source == null) {
                    h.result = null;
                } else {
                    ItemStack r = source.clone();
                    int amt = h.result == null ? 1 : h.result.getAmount();
                    r.setAmount(Math.max(1, Math.min(amt, r.getMaxStackSize())));
                    h.result = r;
                }
                renderEditor(h);
            }
            case PLUS, MINUS -> {
                if (h.result == null) return;
                int d = (e.isShiftClick() ? 8 : 1) * (raw == PLUS ? 1 : -1);
                h.result.setAmount(Math.max(1, Math.min(h.result.getAmount() + d, h.result.getMaxStackSize())));
                renderEditor(h);
            }
            case TYPE -> {
                h.shaped = !h.shaped;
                renderEditor(h);
            }
            case CLEAR -> {
                for (int i = 0; i < 9; i++) h.grid[i] = null;
                renderEditor(h);
            }
            case ENABLED -> {
                h.enabled = !h.enabled;
                renderEditor(h);
            }
            case BACK -> later(() -> open(p, false));
            case DELETE -> {
                if (h.id != null) {
                    rm.delete(h.id);
                    say(p, "<red>Deleted recipe <white>" + h.id);
                    later(() -> open(p, false));
                }
            }
            case SAVE -> save(p, h);
            default -> {
            }
        }
    }

    private void save(Player p, EditHolder h) {
        if (h.result == null) {
            say(p, "<red>Set a result item first.");
            return;
        }
        boolean any = false;
        for (ItemStack it : h.grid) if (it != null) any = true;
        if (!any) {
            say(p, "<red>Put at least one ingredient in the grid.");
            return;
        }

        String id = h.id != null ? h.id : rm.nextId();
        CustomRecipe cr = new CustomRecipe(id);
        cr.shaped = h.shaped;
        cr.enabled = h.enabled;
        cr.result = h.result.clone();
        for (int i = 0; i < 9; i++) cr.grid[i] = h.grid[i] == null ? null : h.grid[i].clone();

        String err = rm.put(cr);
        if (err != null) {
            say(p, "<red>Could not save: " + err);
            return;
        }
        if (h.replaceVanilla != null) rm.disableVanilla(h.replaceVanilla);
        say(p, "<green>Saved recipe <white>" + id + "</white>.");
        later(() -> open(p, false));
    }

    // ===================================================================== events

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof Holder holder)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (!allowed(p)) {
            later(p::closeInventory);
            return;
        }
        if (holder instanceof ListHolder lh) onListClick(e, p, lh);
        else if (holder instanceof EditHolder eh) onEditClick(e, p, eh);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Holder) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent e) {
        Consumer<String> cb = awaitingChat.remove(e.getPlayer().getUniqueId());
        if (cb == null) return;
        e.setCancelled(true);
        String text = PlainTextComponentSerializer.plainText().serialize(e.message()).trim();
        Bukkit.getScheduler().runTask(plugin, () -> cb.accept(text));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        awaitingChat.remove(e.getPlayer().getUniqueId());
    }
}
