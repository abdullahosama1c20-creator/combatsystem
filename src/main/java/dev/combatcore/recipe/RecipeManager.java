package dev.combatcore.recipe;

import org.bukkit.Bukkit;
import org.bukkit.Keyed;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.server.ServerLoadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.*;

/** Stores custom recipes, registers them with the server and tracks disabled vanilla recipes. */
public final class RecipeManager implements Listener {

    private final JavaPlugin plugin;
    private final File file;
    private final Map<String, CustomRecipe> custom = new LinkedHashMap<>();
    private final Set<String> disabledVanilla = new LinkedHashSet<>();
    private final Map<NamespacedKey, Recipe> removedCache = new HashMap<>();

    public RecipeManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "recipes.yml");
    }

    // ------------------------------------------------------------------ load/save

    public void load() {
        custom.clear();
        disabledVanilla.clear();
        if (file.exists()) {
            YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
            ConfigurationSection sec = y.getConfigurationSection("custom");
            if (sec != null) {
                for (String id : sec.getKeys(false)) {
                    ConfigurationSection s = sec.getConfigurationSection(id);
                    if (s == null) continue;
                    CustomRecipe c = new CustomRecipe(id);
                    c.shaped = !"shapeless".equalsIgnoreCase(s.getString("type", "shaped"));
                    c.enabled = s.getBoolean("enabled", true);
                    c.result = s.getItemStack("result");
                    for (int i = 0; i < 9; i++) c.grid[i] = s.getItemStack("grid." + i);
                    if (c.result != null) custom.put(id, c);
                }
            }
            disabledVanilla.addAll(y.getStringList("disabled-vanilla"));
        }
        applyAll();
    }

    private void save() {
        YamlConfiguration y = new YamlConfiguration();
        for (CustomRecipe c : custom.values()) {
            String base = "custom." + c.id;
            y.set(base + ".type", c.shaped ? "shaped" : "shapeless");
            y.set(base + ".enabled", c.enabled);
            y.set(base + ".result", c.result);
            for (int i = 0; i < 9; i++) {
                if (c.grid[i] != null) y.set(base + ".grid." + i, c.grid[i]);
            }
        }
        y.set("disabled-vanilla", new ArrayList<>(disabledVanilla));
        try {
            plugin.getDataFolder().mkdirs();
            y.save(file);
        } catch (IOException ex) {
            plugin.getLogger().severe("Could not save recipes.yml: " + ex.getMessage());
        }
    }

    /** (Re)apply everything to the server. Also runs after /reload and datapack reloads. */
    public void applyAll() {
        for (CustomRecipe c : custom.values()) {
            if (c.enabled) {
                String err = register(c);
                if (err != null) plugin.getLogger().warning("Recipe " + c.id + " not registered: " + err);
            } else {
                unregister(c);
            }
        }
        for (String k : disabledVanilla) {
            NamespacedKey nk = NamespacedKey.fromString(k);
            if (nk != null) removeVanilla(nk);
        }
    }

    @EventHandler
    public void onServerLoad(ServerLoadEvent e) {
        applyAll();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        for (CustomRecipe c : custom.values()) {
            if (c.enabled) e.getPlayer().discoverRecipe(key(c.id));
        }
    }

    // ------------------------------------------------------------ custom recipes

    public List<CustomRecipe> customs() {
        return new ArrayList<>(custom.values());
    }

    public String nextId() {
        int i = 1;
        while (custom.containsKey("recipe_" + i)) i++;
        return "recipe_" + i;
    }

    private NamespacedKey key(String id) {
        return new NamespacedKey(plugin, id);
    }

    /** Adds or replaces a recipe. @return error message, or null on success. */
    public String put(CustomRecipe c) {
        CustomRecipe prev = custom.put(c.id, c);
        String err = c.enabled ? register(c) : unregister(c);
        if (err != null) {
            if (prev == null) custom.remove(c.id);
            else {
                custom.put(c.id, prev);
                if (prev.enabled) register(prev);
            }
            return err;
        }
        save();
        return null;
    }

    public String setEnabled(CustomRecipe c, boolean enabled) {
        c.enabled = enabled;
        String err = enabled ? register(c) : unregister(c);
        if (err != null) {
            c.enabled = !enabled;
            return err;
        }
        save();
        return null;
    }

    public void delete(String id) {
        CustomRecipe c = custom.remove(id);
        if (c != null) unregister(c);
        save();
    }

    private String unregister(CustomRecipe c) {
        Bukkit.removeRecipe(key(c.id));
        return null;
    }

    private String register(CustomRecipe c) {
        NamespacedKey key = key(c.id);
        Bukkit.removeRecipe(key);
        try {
            Bukkit.addRecipe(build(c, key));
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return String.valueOf(ex.getMessage());
        }
        for (Player p : Bukkit.getOnlinePlayers()) p.discoverRecipe(key);
        return null;
    }

    private static RecipeChoice choice(ItemStack it) {
        ItemStack one = it.clone();
        one.setAmount(1);
        return one.hasItemMeta()
                ? new RecipeChoice.ExactChoice(one)
                : new RecipeChoice.MaterialChoice(one.getType());
    }

    private Recipe build(CustomRecipe c, NamespacedKey key) {
        if (c.result == null || c.result.getType().isAir()) {
            throw new IllegalArgumentException("The recipe has no result item.");
        }
        ItemStack result = c.result.clone();

        if (!c.shaped) {
            ShapelessRecipe sr = new ShapelessRecipe(key, result);
            int count = 0;
            for (ItemStack it : c.grid) {
                if (it == null) continue;
                sr.addIngredient(choice(it));
                count++;
            }
            if (count == 0) throw new IllegalArgumentException("The recipe has no ingredients.");
            return sr;
        }

        int minR = 3, maxR = -1, minC = 3, maxC = -1;
        for (int i = 0; i < 9; i++) {
            if (c.grid[i] == null) continue;
            int r = i / 3, col = i % 3;
            minR = Math.min(minR, r);
            maxR = Math.max(maxR, r);
            minC = Math.min(minC, col);
            maxC = Math.max(maxC, col);
        }
        if (maxR < 0) throw new IllegalArgumentException("The recipe has no ingredients.");

        List<ItemStack> kinds = new ArrayList<>();
        List<String> rows = new ArrayList<>();
        for (int r = minR; r <= maxR; r++) {
            StringBuilder sb = new StringBuilder();
            for (int col = minC; col <= maxC; col++) {
                ItemStack it = c.grid[r * 3 + col];
                if (it == null) {
                    sb.append(' ');
                    continue;
                }
                int idx = -1;
                for (int k = 0; k < kinds.size(); k++) {
                    if (kinds.get(k).isSimilar(it)) idx = k;
                }
                if (idx < 0) {
                    kinds.add(it);
                    idx = kinds.size() - 1;
                }
                sb.append((char) ('A' + idx));
            }
            rows.add(sb.toString());
        }
        ShapedRecipe sr = new ShapedRecipe(key, result);
        sr.shape(rows.toArray(new String[0]));
        for (int k = 0; k < kinds.size(); k++) {
            sr.setIngredient((char) ('A' + k), choice(kinds.get(k)));
        }
        return sr;
    }

    // ------------------------------------------------------------ vanilla recipes

    /** All vanilla recipes (including currently disabled ones), sorted by key. */
    public List<Map.Entry<NamespacedKey, Recipe>> vanilla() {
        Map<NamespacedKey, Recipe> map = new TreeMap<>(Comparator.comparing(NamespacedKey::toString));
        Iterator<Recipe> it = Bukkit.recipeIterator();
        while (it.hasNext()) {
            Recipe r = it.next();
            if (r instanceof Keyed k && k.getKey().getNamespace().equals(NamespacedKey.MINECRAFT)) {
                map.put(k.getKey(), r);
            }
        }
        for (Map.Entry<NamespacedKey, Recipe> en : removedCache.entrySet()) map.putIfAbsent(en.getKey(), en.getValue());

        List<Map.Entry<NamespacedKey, Recipe>> out = new ArrayList<>();
        for (Map.Entry<NamespacedKey, Recipe> en : map.entrySet()) {
            ItemStack res;
            try {
                res = en.getValue().getResult();
            } catch (RuntimeException ex) {
                continue;
            }
            if (res == null || res.getType().isAir()) continue;
            out.add(new AbstractMap.SimpleEntry<>(en.getKey(), en.getValue()));
        }
        return out;
    }

    public boolean isDisabled(NamespacedKey key) {
        return disabledVanilla.contains(key.toString());
    }

    private void removeVanilla(NamespacedKey key) {
        Recipe r = Bukkit.getRecipe(key);
        if (r != null) removedCache.put(key, r);
        Bukkit.removeRecipe(key);
    }

    public void disableVanilla(NamespacedKey key) {
        removeVanilla(key);
        disabledVanilla.add(key.toString());
        save();
    }

    /** @return true if the recipe is usable again right now (false = it returns after a restart). */
    public boolean enableVanilla(NamespacedKey key) {
        disabledVanilla.remove(key.toString());
        save();
        Recipe r = removedCache.remove(key);
        if (r == null) return false;
        try {
            Bukkit.addRecipe(r);
            return true;
        } catch (RuntimeException ex) {
            return false;
        }
    }
}
