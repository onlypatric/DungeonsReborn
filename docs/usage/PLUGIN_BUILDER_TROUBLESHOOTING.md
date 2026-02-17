# Plugin Builder Troubleshooting

## Build fails with validation errors
- Run `python -m dungeonsreborn_builder validate path/to/pack.py --strict`.
- Fix missing IDs or invalid enum names (see suggestions in output).

## Export runs but nothing appears in-game
- Ensure output path is `./plugins/DungeonsReborn` (server folder).
- Run `/dr effects reload` or the relevant reload command.

## Locale keys show as missing
- Check `locales/en/builder.yml` was generated.
- Ensure `ContentPack.emit_locales` is enabled (default true).

## GUI previews look blank
- Icons are auto-filled only when `emit_gui_previews` is enabled.
- Ensure `heads_gui_<theme>.yml` exists in output.

## Overrides not applied
- Make sure `.advanced(True)` was called before overrides.
- Confirm override paths match output schema.

## Pack too strong or weak
- Use `scale_pack(pack, "easy"/"hard"/"elite")`.
