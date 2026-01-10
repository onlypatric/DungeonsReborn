# Command Reference (Usage)

This is a practical, admin-facing command list for DungeonsReborn.
Some commands are player-only.

--------------------------------------------------------------------------------
## /effects

General:
- `/effects` — show help summary.
- `/effects reload` — reload effects + item bindings. Permission: `dungeonsreborn.effects.reload`.
- `/effects stats` — show engine stats.
- `/effects list` — list loaded abilities.
- `/effects info <ability>` — show ability metadata.
- `/effects cast <ability> [player]` — cast an ability (player only unless target specified).

Debug:
- `/effects debug on|off` — enable/disable engine debug logging.
- `/effects debug script on|off` — enable/disable DSL debug logs.
- `/effects debug script trace on|off` — extra DSL trace logging.
- `/effects explain left|right` — show what bindings would trigger for click.

Particles:
- `/effects particles stats` — particle stats.
- `/effects particles range <0..256>` — client render range clamp.
- `/effects particles queue <0..250000>` — max requests per tick.
- `/effects particles budget <0..50000>` — max per-player per tick.
- `/effects particles quality <0..10>` — quality multiplier.

Bindings:
- `/effects bind add left|right <ability>` — add ad-hoc binding to held item.
- `/effects bind remove left|right <ability>` — remove binding from held item.
- `/effects bind list` — list bindings on held item.
- `/effects bind clear left|right|all` — clear bindings on held item.

Mana:
- `/effects mana show` — show your mana.
- `/effects mana set <value>` — set your mana (0..1,000,000).
- `/effects mana add <delta>` — add/subtract mana (-1,000,000..1,000,000).
- `/effects mana max <value>` — set your max mana (1..1,000,000).

Timings:
- `/effects timings last [player]` — last cast timing snapshot.
- `/effects timings cast <castId>` — timings for a specific cast.

Types:
- `/effects types actions` — list action types.
- `/effects types targeters` — list targeter types.
- `/effects types conditions` — list condition types.

DSL:
- `/effects script run <file>` — run a DSL script file (for quick testing).
- `/effects script stats` — DSL runtime stats.
- `/effects lint [script]` — lint all scripts or a single script.

Minions (if installed):
- `/effects minions recall` — recall all minions.
- `/effects minions dismiss` — dismiss all minions.
- `/effects minions mode aggressive|defensive|passive` — set minion mode.
- `/effects minions list` — list active minions.
- `/effects minions stats` — minion system stats.
- `/effects minions test <mob> [count] [durationTicks] [radius]` — test summon.

Editor:
- `/effects editor` — open the effects/item editor (player only). Permission: `dungeonsreborn.editor.view`.

--------------------------------------------------------------------------------
## /mobs

- `/mobs` — show help summary.
- `/mobs reload` — reload mobs.yml. Permission: `dungeonsreborn.mobs.reload`.
- `/mobs editor` — open mob editor (player only). Permission: `dungeonsreborn.mobs.editor`.
- `/mobs list` — list active custom mobs.
- `/mobs dump <uuid>` — show snapshot of a custom mob instance.
- `/mobs spawn <id>` — spawn a custom mob at your location. Permission: `dungeonsreborn.mobs.spawn`.
- `/mobs egg <id>` — give yourself a custom egg. Permission: `dungeonsreborn.mobs.egg.give`.

--------------------------------------------------------------------------------
## /gui

- `/gui` — open the GUI showcase menu (player only).

--------------------------------------------------------------------------------
## /flyspeed

- `/flyspeed <speed> [player]` — set fly speed (0.0..10.0). Player-only unless a target is specified.
