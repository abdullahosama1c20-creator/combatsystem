#!/usr/bin/env python3
"""Regenerates the kill-achievements datapack in src/main/resources/datapack.
Edit the lists below, run `python3 tools/generate_advancements.py`, then rebuild."""
import json, os, shutil

ROOT = os.path.join(os.path.dirname(__file__), "..")
PACK = os.path.join(ROOT, "src", "main", "resources", "datapack")
ADV = os.path.join(PACK, "data", "combatcore", "advancement", "kills")

def ep(pred, entity="this"):
    return {"condition": "minecraft:entity_properties", "entity": entity, "predicate": pred}

def kills_at_least(n):
    return {"kills": {"trigger": "minecraft:tick", "conditions": {"player": [ep({
        "type_specific": {"type": "minecraft:player", "stats": [
            {"type": "minecraft:custom", "stat": "minecraft:player_kills", "value": {"min": n}}]}})]}}}

def player_kill(killer=None, victim=None, blow_tag=None):
    cond = {"entity": [ep({"type": "minecraft:player", **(victim or {})})]}
    if killer:
        cond["player"] = [ep(killer)]
    if blow_tag:
        cond["killing_blow"] = {"tags": [{"id": blow_tag, "expected": True}]}
    return {"kill": {"trigger": "minecraft:player_killed_entity", "conditions": cond}}

def held(item):  return {"equipment": {"mainhand": {"items": item}}}
def worn(slot, item): return {"equipment": {slot: {"items": item}}}

def write(name, icon, title, desc, frame, parent, criteria):
    d = {"display": {"icon": {"id": icon}, "title": {"text": title}, "description": {"text": desc},
                     "frame": frame, "show_toast": True, "announce_to_chat": True, "hidden": False},
         "criteria": criteria}
    if parent:
        d["parent"] = parent
    with open(os.path.join(ADV, name + ".json"), "w", encoding="utf-8") as f:
        json.dump(d, f, indent=2, ensure_ascii=False)

shutil.rmtree(ADV, ignore_errors=True)
os.makedirs(ADV)

with open(os.path.join(PACK, "pack.mcmeta"), "w", encoding="utf-8") as f:
    json.dump({"pack": {"description": "CombatCore - player kill achievements",
                        "min_format": [88, 0], "max_format": [999, 0]}}, f, indent=2)

ns = "combatcore:kills/"
root_crit = {"start": {"trigger": "minecraft:tick"}}
d = {"display": {"icon": {"id": "minecraft:netherite_sword"}, "title": {"text": "Bounty Hunter"},
                 "description": {"text": "Player-kill achievements. Hunt down other players."},
                 "frame": "task", "show_toast": False, "announce_to_chat": False, "hidden": False},
     "criteria": root_crit}
json.dump(d, open(os.path.join(ADV, "root.json"), "w", encoding="utf-8"), indent=2)

# ---- kill-count chain -----------------------------------------------------
counts = [
    (1, "first_blood", "First Blood", "Kill a player.", "minecraft:player_head", "task"),
    (2, "double_trouble", "Double Trouble", "Kill 2 players.", "minecraft:wooden_sword", "task"),
    (3, "triple_threat", "Triple Threat", "Kill 3 players.", "minecraft:stone_sword", "task"),
    (5, "killing_spree", "Killing Spree", "Kill 5 players.", "minecraft:iron_sword", "goal"),
    (10, "rampage", "Rampage", "Kill 10 players.", "minecraft:skeleton_skull", "goal"),
    (15, "dominating", "Dominating", "Kill 15 players.", "minecraft:zombie_head", "goal"),
    (25, "warlord", "Warlord", "Kill 25 players.", "minecraft:creeper_head", "goal"),
    (50, "executioner", "Executioner", "Kill 50 players.", "minecraft:wither_skeleton_skull", "challenge"),
    (75, "legendary_slayer", "Legendary Slayer", "Kill 75 players.", "minecraft:netherite_sword", "challenge"),
    (100, "centurion", "Centurion", "Kill 100 players.", "minecraft:dragon_head", "challenge"),
    (250, "bloodlord", "Bloodlord", "Kill 250 players.", "minecraft:nether_star", "challenge"),
]
parent = ns + "root"
for n, name, title, desc, icon, frame in counts:
    write(name, icon, title, desc, frame, parent, kills_at_least(n))
    parent = ns + name

# ---- special kills (all children of First Blood) ---------------------------
fb = ns + "first_blood"
specials = [
    ("splinter_sting", "minecraft:wooden_sword", "Splinter Sting", "Kill a player with a wooden sword.", "task",
     player_kill(killer=held("minecraft:wooden_sword"))),
    ("cold_iron", "minecraft:iron_sword", "Cold Iron", "Kill a player with an iron sword.", "task",
     player_kill(killer=held("minecraft:iron_sword"))),
    ("diamond_edge", "minecraft:diamond_sword", "Diamond Edge", "Kill a player with a diamond sword.", "goal",
     player_kill(killer=held("minecraft:diamond_sword"))),
    ("netherite_reaper", "minecraft:netherite_sword", "Netherite Reaper", "Kill a player with a netherite sword.", "goal",
     player_kill(killer=held("minecraft:netherite_sword"))),
    ("lumberjack", "minecraft:iron_axe", "Lumberjack", "Kill a player with an axe.", "task",
     player_kill(killer=held("#minecraft:axes"))),
    ("poseidons_wrath", "minecraft:trident", "Poseidon's Wrath", "Kill a player with a trident.", "goal",
     player_kill(killer=held("minecraft:trident"))),
    ("smash_bros", "minecraft:mace", "Smash!", "Kill a player with a mace.", "goal",
     player_kill(killer=held("minecraft:mace"))),
    ("fist_of_fury", "minecraft:bone", "Fist of Fury", "Kill a player with your bare hands.", "challenge",
     player_kill(killer=held("minecraft:air"))),
    ("sniper", "minecraft:bow", "Sniper", "Kill a player with a projectile.", "task",
     player_kill(blow_tag="minecraft:is_projectile")),
    ("eagle_eye", "minecraft:spyglass", "Eagle Eye", "Kill a player with a projectile from 50+ blocks away.", "challenge",
     player_kill(victim={"distance": {"horizontal": {"min": 50}}}, blow_tag="minecraft:is_projectile")),
    ("pyromaniac", "minecraft:fire_charge", "Pyromaniac", "Kill a player with fire.", "goal",
     player_kill(blow_tag="minecraft:is_fire")),
    ("boom", "minecraft:tnt", "Boom!", "Kill a player with an explosion.", "goal",
     player_kill(blow_tag="minecraft:is_explosion")),
    ("hells_executioner", "minecraft:netherrack", "Hell's Executioner", "Kill a player in the Nether.", "goal",
     player_kill(killer={"location": {"dimension": "minecraft:the_nether"}})),
    ("end_of_the_line", "minecraft:end_stone", "End of the Line", "Kill a player in the End.", "goal",
     player_kill(killer={"location": {"dimension": "minecraft:the_end"}})),
    ("winged_warrior", "minecraft:elytra", "Winged Warrior", "Kill a player while wearing an elytra.", "goal",
     player_kill(killer=worn("chest", "minecraft:elytra"))),
    ("silent_death", "minecraft:ink_sac", "Silent Death", "Kill a player while sneaking.", "task",
     player_kill(killer={"flags": {"is_sneaking": True}})),
    ("charge", "minecraft:leather_boots", "Charge!", "Kill a player while sprinting.", "task",
     player_kill(killer={"flags": {"is_sprinting": True}})),
    ("juiced_up", "minecraft:blaze_powder", "Juiced Up", "Kill a player while you have Strength.", "goal",
     player_kill(killer={"effects": {"minecraft:strength": {}}})),
    ("ghost_in_the_machine", "minecraft:glass", "Ghost in the Machine", "Kill a player while invisible.", "goal",
     player_kill(killer={"effects": {"minecraft:invisibility": {}}})),
    ("big_game_hunter", "minecraft:netherite_helmet", "Big Game Hunter",
     "Kill a player wearing a netherite helmet.", "goal",
     player_kill(victim=worn("head", "minecraft:netherite_helmet"))),
    ("wing_clipper", "minecraft:feather", "Wing Clipper", "Kill a player who is wearing an elytra.", "goal",
     player_kill(victim=worn("chest", "minecraft:elytra"))),
]
for name, icon, title, desc, frame, crit in specials:
    write(name, icon, title, desc, frame, fb, crit)

print(f"Wrote root + {len(counts)} count + {len(specials)} special advancements = {len(counts) + len(specials)}")
