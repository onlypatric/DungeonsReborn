# Plugin Builder Migration Guide

This guide helps move existing YAML or older builder scripts to the new abstraction.

## From YAML to Builder
1) Identify entities: items, mobs, abilities, quests, shops.
2) Recreate each entity with builder classes and fluent methods.
3) Export to a new output folder and compare with the YAML (optional).

## From Older Builder Scripts
- Migrate imports to `from dungeonsreborn_builder.v2 import ...`.
- Replace legacy helper calls with first-class v2 APIs (`ability`, `item`, `mob`, `recipe`, `shop`, `quest`, `upgrade`).
- For mob AI:
  - Keep `.ai_quick(...)` for legacy/non-override setup.
  - Use `.ai_v4(...)` + `.ai_selector(...)` / `.ai_selector_cast(...)` for full override mode.
  - Use `.ai_v4_raw({...})` for schema escape-hatch fields not covered by typed helpers.
- Use strict typed mob setters instead of path injection:
  - `.scale_range(0.92, 1.08)` for scale variance bands
  - `.equip_head(...)`, `.equip_armor(...)` for equipment
  - `.collidable(...)`, `.invulnerable(...)`, `.tier(...)` for base flags/tier
  - `.unsafe_raw_patch({...})` only as explicit unsafe escape hatch.

## Compatibility Notes
- Enums accept strings and provide suggestions.
- Locale keys are auto-emitted into `locales/en/builder.yml`.
- GUI previews are auto-filled with default icons and title keys.

## Validation
- `preview` shows warnings without writing files.
- `validate --strict` exits non-zero on errors.

## FAQ
- Q: Can I keep custom YAML sections?
  - A: Yes, but strict typed mode blocks `.override(...)`. Use typed APIs first, then `.unsafe_raw_patch({...})`
    and `.ai_v4_raw({...})` only for unsupported edge fields.
