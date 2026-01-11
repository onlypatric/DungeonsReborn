# Command Reference (Usage)

This is a practical, admin-facing command list for DungeonsReborn.
Some commands are player-only.
Primary root is `/dr` (aliases: `/droam`, `/dungeonroam`).

--------------------------------------------------------------------------------
## /dr effects

General:
- `/dr effects` — show help summary.
- `/dr effects reload` — reload effects + item bindings. Permission: `dungeonsreborn.effects.reload`.
- `/dr effects logging reload` — reload logging levels from config.yml. Permission: `dungeonsreborn.effects.reload`.
- `/dr effects stats` — show engine stats.
- `/dr effects list` — list loaded abilities.
- `/dr effects info <ability>` — show ability metadata.
- `/dr effects cast <ability> [player]` — cast an ability (player only unless target specified).

Debug:
- `/dr effects debug on|off` — enable/disable engine debug logging.
- `/dr effects debug script on|off` — enable/disable DSL debug logs.
- `/dr effects debug script trace on|off` — extra DSL trace logging.
- `/dr effects explain left|right` — show what bindings would trigger for click.

Particles:
- `/dr effects particles stats` — particle stats.
- `/dr effects particles range <0..256>` — client render range clamp.
- `/dr effects particles queue <0..250000>` — max requests per tick.
- `/dr effects particles budget <0..50000>` — max per-player per tick.
- `/dr effects particles quality <0..10>` — quality multiplier.

Bindings:
- `/dr effects bind add left|right <ability>` — add ad-hoc binding to held item.
- `/dr effects bind remove left|right <ability>` — remove binding from held item.
- `/dr effects bind list` — list bindings on held item.
- `/dr effects bind clear left|right|all` — clear bindings on held item.

Mana:
- `/dr effects mana show` — show your mana.
- `/dr effects mana set <value>` — set your mana (0..1,000,000).
- `/dr effects mana add <delta>` — add/subtract mana (-1,000,000..1,000,000).
- `/dr effects mana max <value>` — set your max mana (1..1,000,000).

Timings:
- `/dr effects timings last [player]` — last cast timing snapshot.
- `/dr effects timings cast <castId>` — timings for a specific cast.

Types:
- `/dr effects types actions` — list action types.
- `/dr effects types targeters` — list targeter types.
- `/dr effects types conditions` — list condition types.

DSL:
- `/dr effects script run <file>` — run a DSL script file (for quick testing).
- `/dr effects script stats` — DSL runtime stats.
- `/dr effects lint [script]` — lint all scripts or a single script.

Minions (if installed):
- `/dr effects minions recall` — recall all minions.
- `/dr effects minions dismiss` — dismiss all minions.
- `/dr effects minions mode aggressive|defensive|passive` — set minion mode.
- `/dr effects minions list` — list active minions.
- `/dr effects minions stats` — minion system stats.
- `/dr effects minions test <mob> [count] [durationTicks] [radius]` — test summon.

Editor:
- `/dr effects editor` — open the effects/item editor (player only). Permission: `dungeonsreborn.editor.view`.

--------------------------------------------------------------------------------
## /dr mobs

- `/dr mobs` — show help summary.
- `/dr mobs reload` — reload mobs.yml. Permission: `dungeonsreborn.mobs.reload`.
- `/dr mobs editor` — open mob editor (player only). Permission: `dungeonsreborn.mobs.editor`.
- `/dr mobs list` — list active custom mobs.
- `/dr mobs dump <uuid>` — show snapshot of a custom mob instance.
- `/dr mobs spawn <id>` — spawn a custom mob at your location. Permission: `dungeonsreborn.mobs.spawn`.
- `/dr mobs egg <id>` — give yourself a custom egg. Permission: `dungeonsreborn.mobs.egg.give`.

--------------------------------------------------------------------------------
## /dr gui

- `/dr gui` — open the GUI showcase menu (player only).
