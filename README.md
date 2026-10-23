# Preset Switcher

A small, client-only Fabric mod that lets **modpacks** offer selectable settings presets such as *Potato*,
*Default* and *Beautiful*.

- On the **first launch** players pick a preset from a vanilla-style screen (names and descriptions come from the pack).
- Later they can switch from **Options > Presets...** or from the mod's config button in **Mod Menu**.
- The chosen preset is applied at the **next start**, before any other mod reads its config. The game says a restart
  is needed and offers to quit.
- A preset only sets the keys it declares. **Key binds, volumes, language and every other personal setting are
  kept** when switching.
- **Pack updates** never undo the player's choice: if the pack's preset definitions changed, the chosen preset's
  declared keys are applied again on the next start; otherwise live files are never touched.
- Everything is data-driven (`config/presetswitcher/presets.json5`); there is no pack-specific code.

Client-only mod for **Fabric** (also runs on **Quilt**), **NeoForge** and **Forge**, Minecraft **1.20 to 26.3**. Mod Menu
(Fabric) is optional; the config button is in the mod list on NeoForge/Forge. Fabric API is optional (without it
the UI is English only on Fabric, because Fabric API loads mod language files). On a dedicated server installed from
the same pack the mod does nothing (Fabric: `"environment": "client"`; NeoForge: client-only mod; Forge: ignored on
servers and not required by clients).

### Supported versions

| Minecraft | Fabric / Quilt jar | NeoForge jar | Forge jar |
|---|---|---|---|
| 1.20 – 1.20.1 | `fabric-1.20-1.20.1` | – (use the Forge jar on NeoForge 47.1) | `forge-1.20.1` |
| 1.20.2 | `fabric-1.20.2` | – | – |
| 1.20.3 – 1.20.4 | `fabric-1.20.3-1.20.4` | – | – |
| 1.20.5 – 1.20.6 | `fabric-1.20.5-1.20.6` | – | – |
| 1.21 – 1.21.5 | `fabric-1.21-1.21.5` | `neoforge-1.21-1.21.5` | – |
| 1.21.6 – 1.21.8 | `fabric-1.21.6-1.21.8` | `neoforge-1.21.6-1.21.8` | – |
| 1.21.9 – 1.21.11 | `fabric-1.21.9-1.21.11` | `neoforge-1.21.9-1.21.11` | – |
| 26.1 – 26.1.2 | `fabric-26.1-26.1.2` | `neoforge-26.1-26.1.2` | – |
| 26.2 | `fabric-26.2` | `neoforge-26.2` | – |
| 26.3 | `fabric-26.3` | `neoforge-26.3` | – |

File names are `preset-switcher-<version>+<jar>.jar`. Fabric Loader 0.15+ (26.1/26.2: 0.18.4+, 26.3: 0.19.3+), Java 17
(1.20–1.20.4), 21 (1.20.5–1.21.11) or 25 (26.x). Not built: NeoForge for 1.20.2–1.20.6 (very few NeoForge modpacks
use these versions) and Forge after 1.20.1 (very few Forge modpacks after 1.20.1).

![First-launch chooser](docs/chooser.png)

![Options > Presets...](docs/options-button.png)

## For players

- First start: choose a preset, or keep the pack's default.
- Switch later: *Options > Presets...* (or Mod Menu > Preset Switcher > config). Choose *Quit Game* or restart later;
  the preset is applied when the game starts next time.
- Your choice is stored in `.presetswitcher/state.json` in the instance folder. Deleting that file makes the game
  apply the pack's default preset again and show the chooser on the next start.

## For pack authors

### Files

| Path | Owner | Purpose |
|---|---|---|
| `config/presetswitcher/presets.json5` | pack | Preset list and operations (JSON5: comments, trailing commas, unquoted keys allowed). |
| `config/presetswitcher/**` | pack | Any files the operations copy or merge from (e.g. one folder per preset). |
| `.presetswitcher/state.json` | player | Chosen / applied / pending preset, hash of the definitions. **Never ship this file.** |

Rules for a pack that uses presets:

1. **Do not ship files that presets manage as plain overrides.** A pack update would overwrite the player's live
   file with your copy while the player's chosen preset is not re-applied (the definitions didn't change). Ship
   them only inside `config/presetswitcher/` and let the presets write them. For first-launch defaults of files that
   are *not* preset-specific, use a `copy` with `when: "missing"` in `base` (this replaces YOSBR's job;
   don't ship the same file through YOSBR too).
2. Let every preset declare the **same set of keys**. A preset only writes what it declares, so a key that only
   *Beautiful* sets keeps Beautiful's value after switching to *Potato*. Put shared operations in `base` or in a
   preset that the others `extends`.
3. Any change inside `config/presetswitcher/` (definitions or source files) counts as a pack update and re-applies
   the chosen preset's declared keys on the next start. Keep the folder stable between releases unless you want
   that.
4. Never export `.presetswitcher/` with your instance.
5. **Minecraft 1.21.11+ options.txt:** vanilla re-applies `graphicsPreset` (`fast`, `fancy`, `fabulous`) at every
   start and overwrites render distance, simulation distance, particles, clouds, mipmaps, entity shadows and more. A
   preset that sets any of these must also set `graphicsPreset: "custom"`. The log warns when it doesn't. See
   "Version notes" for other option keys that differ between versions.

### When presets are applied

| Situation at game start | What happens |
|---|---|
| No state file (first start of a new install) | `base` + default preset are applied. The chooser is shown once (after vanilla's onboarding screens). |
| The player chose another preset in game | `base` + that preset are applied. |
| Definitions folder changed since the last apply (pack update) | `base` + the chosen preset are applied again. |
| The chosen preset no longer exists | `base` + the default preset are applied; the chooser is shown again. |
| Nothing changed | Nothing is written. Steps that were *deferred* earlier (see `optional`/`createIfMissing`) are retried. |

Presets are applied by an early hook that runs right after mod discovery, before any mod reads its configuration
(mixin configuration plugins such as `sodium-mixins.properties`, mod config files, options.txt):

| Loader | Hook | Runs |
|---|---|---|
| Fabric | language adapter declared in `fabric.mod.json` (never used to create entrypoints) | when Fabric Loader sets up language adapters, before the mixin subsystem starts and before `preLaunch` |
| Quilt | same jar; Quilt creates the adapter, `preLaunch` is the fallback | as above |
| NeoForge | `IModLanguageLoader` in a library jar nested in the mod jar (jar-in-jar) | after mod discovery, before the game layer / game content class loader is built, i.e. before mixin configs are applied and before mods are constructed and load their configs |
| Forge 1.20.1 | `IModLanguageProvider` in a language-provider jar nested in the mod jar | after mod discovery, before the game layer is built (same as NeoForge) |

Why nested jars on NeoForge/Forge: both loaders (ModLauncher and FancyModLoader 10+) refuse to load a jar that
declares early services (locators, transformation services, graphics bootstrappers) as a mod, so the hook lives in a
small library jar inside the mod jar; it contains the loader-independent core. The log line
`[PresetSwitcher] starting from <hook>` shows which hook ran. If no hook ran (unknown loader), the presets are applied
from the mod's constructor / first screen and the log warns.

### Version notes

- **options.txt keys differ between Minecraft versions.** Presets merge the keys exactly as written, so use the keys of
  the target version (checked in the game's `Options` class): `graphicsPreset`, `cutoutLeaves`,
  `improvedTransparency`, `textureFiltering`, `maxAnisotropyBit`, `weatherRadius`, `chunkSectionFadeInTime` exist from
  1.21.11, which removed `graphicsMode` (0/1/2 = fast/fancy/fabulous, up to 1.21.10); `cloudRange` from 1.21.6,
  `inactivityFpsLimit` from 1.21.2, `menuBackgroundBlurriness` from 1.20.5. Keys a version doesn't know are dropped
  by the game the next time it saves options.txt.
- options.txt written by the mod starts with the game's data version (`version:` line) so vanilla does not run its
  option data fixers from version 0; on NeoForge/Forge it comes from a built-in table (1.20–26.3).
- First-launch chooser: on 1.20–1.20.1 it is shown before vanilla's accessibility onboarding, on 1.20.2+ after it (it
  is part of vanilla's start-up screen chain). In both cases its buttons continue where vanilla would have gone (title
  screen, or the server/world of a quick-play launch).
- The chooser can be used with the keyboard: arrow keys pick a preset, Enter uses it, Escape keeps the current one.

### presets.json5 reference

```json5
{
  formatVersion: 1,                     // optional; this mod reads version 1
  title: "Choose your settings",        // optional, chooser title (string or { en_us: "...", de_de: "..." })
  subtitle: "...",                      // optional, text under the title
  showChooserOnFirstLaunch: true,       // optional, default true (needs at least two presets)
  defaultPreset: "default",             // optional, default = first preset; applied on a new install
  base: [ /* operations run before every preset */ ],
  presets: [
    {
      id: "potato",                     // letters, digits, _ - . ; stored in the player's state
      name: "Potato",                   // string or { en_us: "...", de_de: "..." }
      description: "For weak PCs.",     // shown under the name (wrapped); § formatting codes work, e.g. "§cWarning§r"
      extends: "default",               // optional, one id or a list; parents' operations run first
      operations: [ /* see below */ ],
    },
  ],
}
```

Every operation accepts `optional: true` (skip silently when its target is missing instead of retrying on the next
start) and `comment: "..."`. Unknown keys are rejected, so typos are reported. Paths are relative and `/`-separated;
`from` is relative to `config/presetswitcher/`, `file`/`to` to the game folder; `..` is not allowed.

#### `merge`: set keys in a config file

```json5
{ type: "merge", file: "options.txt", values: { graphicsPreset: "custom", renderDistance: 8, particles: 1 } }
{ type: "merge", file: "config/sodium-options.json", values: { "quality.weather_quality": "FAST", performance: { use_entity_culling: true } } }
{ type: "merge", file: "config/example.toml", values: { "general.enabled": false } }
{ type: "merge", file: "config/iris.properties", from: "potato/iris.properties" }     // every key of a partial file
{ type: "merge", file: "shaderpacks/MyShader*.zip.txt", format: "properties", values: { SHADOW_QUALITY: "1" } }  // every match
```

| Key | Meaning |
|---|---|
| `file` | Target file. May contain glob wildcards (`*`, `?`, `[ab]`, `{a,b}`) per path segment; every match is edited. |
| `format` | `options` (`key:value`, options.txt), `properties`, `json` (also JSON5), `toml`. Inferred from `options*.txt`, `.properties`, `.json`, `.json5`, `.toml`; required for other names. |
| `values` | Keys to set. For `json`/`toml`, dots separate path segments (`a.b.c`, `list.0` for an array element; `\\.` is a literal dot) and nested objects are flattened, so `{a: {b: 1}}` sets only `a.b`. Arrays are set as a whole. For `options`/`properties`, keys are literal (key binds contain dots). |
| `from` | A file in the definitions folder in the same format; all of its leaf keys are merged (values in `values` win). |
| `createIfMissing` | Default `true`: a missing target is created with just these keys (options.txt gets the current `version:` line so vanilla doesn't run its data fixers). `false`: the step is deferred and retried at each start until the file exists (e.g. a config a mod writes on its first run). |

What is preserved: `options` and `properties` files keep all other lines, comments and order; `toml` keeps comments
and layout (values in `[[array tables]]` cannot be addressed); `json` keeps key order and the indent unit but drops
comments (the file is rewritten as standard JSON, which is valid JSON5).

Value types: strings, numbers, booleans, arrays and objects. In `options.txt`, a string is written as-is, unless
the existing value in the file is quoted (vanilla stores enum options such as `graphicsPreset:"fancy"` as JSON
strings), then it is quoted too. Arrays and objects are written as compact JSON.

Value directive `$firstMatch` (for files whose names contain versions, e.g. shader packs):

```json5
{ type: "merge", file: "config/iris.properties", values: {
    enableShaders: true,
    shaderPack: { $firstMatch: "shaderpacks/ComplementaryUnbound*", fallback: "ComplementaryUnbound_r5.6.zip" },
} }
```

It resolves to the file name of the first match (sorted by name), with an optional `prefix`. Without a match and
without `fallback`, the step is deferred and retried at the next start.

#### `copy`: copy a file or folder

```json5
{ type: "copy", from: "base/options.txt", to: "options.txt", when: "missing" }   // first-launch defaults
{ type: "copy", from: "beautiful/config/voxy", to: "config/voxy", when: "always" }
```

`when`: `always` (default; overwrites the whole file, so the player's own edits of that file are lost on switch) or
`missing` (only if the target doesn't exist). Folders are copied file by file; other files in the target folder stay.

#### `resourcePacks`: change the enabled resource packs (options.txt)

```json5
{ type: "resourcePacks", disable: ["file/Heavy Pack.zip"] }
{ type: "resourcePacks", enable: ["file/Fresh Animations.zip", { $firstMatch: "resourcepacks/Better Clouds*", prefix: "file/" }] }
{ type: "resourcePacks", enable: [{ pack: "file/Fresh Animations.zip", after: "file/Eating Animation.zip" }] }
{ type: "resourcePacks", set: ["vanilla", "fabric", "file/Pack A.zip"] }
```

`set` replaces the whole list; otherwise `disable` removes and `enable` adds packs, keeping the ones the player added.
An enabled pack is appended (on top, i.e. highest priority) unless it is given as `{ pack, after }`: then it is
inserted directly after (= above) the `after` pack if that is enabled, so a preset can restore the pack's original
position after another preset disabled it. `file/` prefixes are optional when comparing. Packs that are incompatible with
the game version are dropped by vanilla.

### Complete example

`config/presetswitcher/presets.json5`:

```json5
{
  formatVersion: 1,
  title: "Choose your settings",
  defaultPreset: "default",

  base: [
    // First-launch defaults for everything that is not preset-specific (replaces YOSBR's options.txt).
    { type: "copy", from: "base/options.txt", to: "options.txt", when: "missing" },
  ],

  presets: [
    {
      id: "potato",
      name: "Potato",
      description: "Lowest settings for weak PCs and laptops. No shaders, fewer particles.",
      operations: [
        { type: "merge", file: "options.txt", values: {
            graphicsPreset: "custom", renderDistance: 6, simulationDistance: 5, particles: 2, renderClouds: "false",
        } },
        { type: "merge", file: "config/sodium-options.json", from: "potato/sodium-options.json" },
        { type: "merge", file: "config/iris.properties", values: { enableShaders: false } },
        { type: "resourcePacks", disable: ["file/Fresh Animations.zip"] },
      ],
    },
    {
      id: "default",
      name: "Default",
      description: "The pack's tuned settings.",
      operations: [
        { type: "merge", file: "options.txt", values: {
            graphicsPreset: "custom", renderDistance: 12, simulationDistance: 8, particles: 0, renderClouds: "true",
        } },
        { type: "merge", file: "config/sodium-options.json", from: "default/sodium-options.json" },
        { type: "merge", file: "config/iris.properties", values: { enableShaders: false } },
        { type: "resourcePacks", enable: ["file/Fresh Animations.zip"] },
      ],
    },
    {
      id: "beautiful",
      name: "Beautiful",
      extends: "default",
      description: "Everything maxed, shaders on. Needs a strong GPU.",
      operations: [
        { type: "merge", file: "options.txt", values: { renderDistance: 24, simulationDistance: 12 } },
        { type: "merge", file: "config/iris.properties", values: {
            enableShaders: true,
            shaderPack: { $firstMatch: "shaderpacks/ComplementaryReimagined*", fallback: "ComplementaryReimagined_r5.6.zip" },
        } },
      ],
    },
  ],
}
```

with `config/presetswitcher/potato/sodium-options.json` containing only the keys that preset sets, e.g.

```json
{ "quality": { "weather_quality": "FAST", "leaves_quality": "FAST" }, "performance": { "use_entity_culling": true } }
```

### Disabling mods per preset (not supported)

Disabling heavy mods (renaming jars) is deliberately not offered:

- Fabric Loader discovers mods before any mod code runs, so a jar can only be disabled for the *next* start, by
  renaming it while the game runs. On Windows, jars that the running game has open cannot be renamed or deleted.
- Launchers re-install missing jars on pack updates (the jar is listed in `modrinth.index.json`), and a left-over
  `.jar.disabled` file is not tracked by the pack, so the result differs between launchers.

Use the mods' own config switches (e.g. Voxy/Iris enable flags) in presets instead.

## Building

One branch (`multiloader`) builds every jar with [Stonecutter](https://stonecutter.kikugie.dev/): Loom
(fabric-loom-remap before 26.1, fabric-loom from 26.1) for Fabric, ModDevGradle for NeoForge and ModDevGradle
legacyforge for Forge 1.20.1. Version-specific code is marked with `//? if` comments; per-version settings are in
`stonecutter.properties.toml`. Sources: `src/core` (loader-independent engine, pure Java), `src/main` (screens, mixins,
loader entry points), `src/early` (NeoForge/Forge early hooks, nested jar), `src/stubs` (Mod Menu API for compiling).

```
./gradlew build collectJar   # JDK 17, 21 and 25 as toolchains; all jars in build/libs (reproducible),
                             # unit tests of the core in the 26.3-fabric node
```

## Example folder

[`example/config/presetswitcher/`](example/config/presetswitcher) is the two-preset definition used to test the mod
(options.txt keys, a JSON config, a .properties file from preset folders, a resource pack and first-launch defaults).

## License

MIT
