# CombatCore

A Paper plugin for **Minecraft 26.3 (Paper 26.3, Java 25)**.

| Feature | Behaviour |
|---|---|
| **Combat tag** | Hitting or being hit by another player tags both for 15 s (configurable). Action bar shows the timer. |
| **Command lock** | While in combat, **every command is blocked** except `/msg`, `/w` (+ `/tell`, `/r`, `/reply` – editable in `config.yml`). |
| **/plugins & /version** | **Always blocked**, for everyone including OPs, in or out of combat. Aliases (`/pl`, `/ver`, `/about`, `bukkit:plugins`, `/execute run plugins` …) are caught and hidden from tab-complete. |
| **Combat logging** | Leaving the game while in combat kills you (your loot drops, the opponent gets kill credit when possible). Server shutdown and admin kicks are excused (configurable). |
| **Death rules** | Out of combat: you drop **nothing** and keep items + XP. In combat: you drop **all items and all XP**. |
| **Apples** | Oak and dark-oak leaves drop apples 10× more often (5 % vs 0.5 %, configurable). Shears/Silk Touch don't roll. |
| **/cr** (OP only) | Clean chest GUI to create, edit, enable/disable and delete crafting recipes, and to disable (or copy-and-edit) vanilla recipes. |
| **Datapack** | 32 player-kill achievements in a "Bounty Hunter" tab, bundled in the jar and registered automatically. |

## Build

Requires JDK 25 and Maven 3.9+.

```bash
mvn package
```

The jar is `target/CombatCore-1.0.0.jar`. Drop it in `plugins/` and restart (a full restart is needed the first time so the datapack is discovered).

If Maven can't resolve the Paper API, change `paper.version` in `pom.xml` to a current `26.3.build.*` from <https://repo.papermc.io>.

## /cr recipe editor

* `/cr` opens **Custom recipes**. Left-click = edit, right-click = enable/disable, shift+right-click = delete.
* **+ New recipe** opens the editor: click an item in *your inventory* to select it (items are copied, never consumed), then click a grid slot or the result slot. Right-click a slot to clear it. Use the +/- buttons for the result amount, toggle shaped/shapeless, then Save.
* **View vanilla recipes**: left-click disables/enables a built-in recipe; right-click opens an editable copy (the original is disabled on Save). Has search and a "disabled only" filter.
* Data is stored in `plugins/CombatCore/recipes.yml`. Re-applied automatically after `/reload` and datapack reloads.
* Limits: tag ingredients ("any planks") are copied as their first matching item when you edit a vanilla recipe; only crafting-table recipes can be created.

Permission: `combatcore.recipes` (default: op).

## Achievements

All live in `data/combatcore/advancement/kills/`:

* **Kill counts** (11): 1, 2, 3, 5, 10, 15, 25, 50, 75, 100, 250 kills (uses the vanilla `player_kills` statistic).
* **Weapons** (9): wooden / iron / diamond / netherite sword, axe, trident, mace, bare hands, projectile.
* **Special** (12): bow from 50+ blocks, fire, explosion, in Nether, in End, wearing elytra, sneaking, sprinting, with Strength, while invisible, victim in netherite helmet, victim in elytra.

Edit `tools/generate_advancements.py` and run it to change/add achievements. A standalone copy of the pack is in `standalone-datapack/` if you'd rather install it manually in `world/datapacks/` (don't use both).

If the server complains about the pack format, adjust `min_format`/`max_format` in `pack.mcmeta` (they are set to a very wide range by default).

## Config

See `src/main/resources/config.yml` – combat time, allowed commands, always-blocked commands, apple chance/leaf types and every message.

## Permissions

| Permission | Default | Effect |
|---|---|---|
| `combatcore.recipes` | op | Use `/cr` |
| `combatcore.bypass` | false | Skip the in-combat command lock (never bypasses the /plugins and /version block) |

## License

MIT
