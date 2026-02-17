# Enum Update Workflow + Deprecation Policy

This builder mirrors Paper/vanilla identifiers as enums in `plugin_builder/dungeonsreborn_builder/vanilla.py`.

## Update Workflow

1) Update the enum lists in `vanilla.py` (Material, EntityType, Sound, etc.).
2) Keep values as strings matching vanilla IDs.
3) Regenerate any schema snapshots if you rely on them for docs:
   - `scripts/builder_schema_dump.py`
4) If any IDs were renamed, add entries to the migration map once Phase 8.5 is implemented.

## Deprecation Policy

- Do not delete enum values immediately.
- Mark deprecated values in docs and add a migration note in Phase 8.5.
- Provide one release cycle where legacy values are still emitted (compat mode).

## Normalization Notes

- `normalize_enum_name` strips the `GENERIC_` prefix for attributes.
- Head IDs are normalized by lowercasing and allowing `[a-z0-9_.:-]`.
