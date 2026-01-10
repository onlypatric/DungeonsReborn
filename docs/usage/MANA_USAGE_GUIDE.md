# Mana System - Usage Guide

This guide explains how mana works in DungeonsReborn and how admins can configure
mana costs, bonuses, and drops. No coding required.

--------------------------------------------------------------------------------
## Chapter 1 - How Mana Works

- Mana is **session-based**. It resets when a player leaves and rejoins.
- Default max mana is handled by the plugin (no YAML setting currently).
- Mana regen is enabled by default:
  - **5 mana per 20 ticks** (1 second).
- Mana regen bonuses from items are **per second** and are scaled by the regen period.

--------------------------------------------------------------------------------
## Chapter 2 - Mana Costs on Abilities

Add a mana cost in any ability:

```yaml
costs:
  - type: mana
    amount: 12
```

Notes:
- `amount` must be > 0.
- If the player lacks mana, the cast is blocked with an error message.

--------------------------------------------------------------------------------
## Chapter 3 - Item Mana Bonuses

Item files can grant temporary mana bonuses while held/equipped:

```yaml
mana:
  maxBonus: 20
  regenBonus: 1.5
```

Notes:
- `maxBonus` adds to the player’s max mana while the item is active.
- `regenBonus` adds mana **per second** (scaled by the regen period).

--------------------------------------------------------------------------------
## Chapter 4 - Mana Drops from Mobs

Custom mobs can drop mana to the killer and nearby players:

```yaml
manaDrops:
  killer:
    min: 8
    max: 16
  nearby:
    radius: 6
    min: 2
    max: 5
```

Notes:
- `killer` and `nearby` can be a fixed number or a min/max range.
- `nearby.radius` controls how far from the kill to share mana.

--------------------------------------------------------------------------------
## Chapter 5 - Mana Placeholders

You can show mana values in messages/action bars:

- `{mana}` — current mana (player only)
- `{mana_max}` — max mana (player only)

Example:

```yaml
action:
  type: action_bar
  text: "<gray>Mana: {mana}/{mana_max}</gray>"
```

--------------------------------------------------------------------------------
## Chapter 6 - Mana Commands

See the command reference in:

`docs/usage/COMMAND_REFERENCE.md`

--------------------------------------------------------------------------------
## Chapter 7 - Troubleshooting

- No mana bar? Use `{mana}` in messages or action bars.
- Costs not applying? Make sure the ability includes a mana cost.
- Regen too slow? Check item bonuses and the base regen settings.
