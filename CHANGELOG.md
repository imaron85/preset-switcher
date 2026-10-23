# Changelog

## 1.1.0

Multi-version, multi-loader release from one shared codebase (branch `multiloader`, Stonecutter).

- Fabric/Quilt for Minecraft 1.20–26.3, NeoForge for 1.21–26.3, Forge for 1.20.1 (see the README for the jars).
- Early hooks: Fabric language adapter (unchanged); NeoForge and Forge use a language loader/provider in a nested
  library jar (both loaders refuse to load jars with early services as mods). The log names the hook that ran.
- Config screen from the mod list on NeoForge/Forge (Mod Menu on Fabric, unchanged).
- Chooser: keyboard control (arrow keys, Enter, Escape); widgets laid out by hand so the screen is the same on all
  versions; logs when the chooser and the Options button are shown.
- options.txt: the graphicsPreset warning only applies to Minecraft 1.21.11+; on NeoForge/Forge new options.txt files
  get the data version from a built-in table.
- Core now targets Java 17 (Minecraft 1.20–1.20.4).

## 1.0.0+26.3

First release, Minecraft 26.3, Fabric Loader 0.19.3+.

- Pack-defined presets in `config/presetswitcher/presets.json5`: `merge` (options.txt, .properties, JSON/JSON5, TOML),
  `copy` (always / only if missing), `resourcePacks` (set / enable / disable), `$firstMatch` file-name lookup,
  `extends`, `base` operations, per-language names and descriptions.
- Applied before other mods read their configs (Fabric language-adapter hook); only declared keys are written.
- First-launch chooser, Options > Presets... button, Mod Menu config screen, restart notice with Quit.
- Pack updates re-apply the chosen preset only when the definitions changed; player state in `.presetswitcher/state.json`.
