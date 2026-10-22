# Changelog

## 1.0.0+26.3

First release, Minecraft 26.3, Fabric Loader 0.19.3+.

- Pack-defined presets in `config/presetswitcher/presets.json5`: `merge` (options.txt, .properties, JSON/JSON5, TOML),
  `copy` (always / only if missing), `resourcePacks` (set / enable / disable), `$firstMatch` file-name lookup,
  `extends`, `base` operations, per-language names and descriptions.
- Applied before other mods read their configs (Fabric language-adapter hook); only declared keys are written.
- First-launch chooser, Options > Presets... button, Mod Menu config screen, restart notice with Quit.
- Pack updates re-apply the chosen preset only when the definitions changed; player state in `.presetswitcher/state.json`.
