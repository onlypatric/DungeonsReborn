# Plugin Builder Migration Guide

This guide helps move existing YAML or older builder scripts to the new abstraction.

## From YAML to Builder
1) Identify entities: items, mobs, abilities, quests, shops.
2) Recreate each entity with builder classes and fluent methods.
3) Export to a new output folder and compare with the YAML (optional).

## From Older Builder Scripts
- Use `ContentPack` with `safe_defaults` to fill missing fields.
- If needed, apply overrides:
  - `.advanced(True).override("path.to.value", value)`

## Compatibility Notes
- Enums accept strings and provide suggestions.
- Locale keys are auto-emitted into `locales/en/builder.yml`.
- GUI previews are auto-filled with default icons and title keys.

## Validation
- `preview` shows warnings without writing files.
- `validate --strict` exits non-zero on errors.

## FAQ
- Q: Can I keep custom YAML sections?
  - A: Yes, use overrides to inject raw YAML sections.
