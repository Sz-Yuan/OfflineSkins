> **English** | [中文](README_ZH.md)

# OfflineSkins

Shows players' real **skins and capes** on **offline-mode servers** and in **singleplayer** — the tab list heads, player heads and mannequins use them too. (The **mannequin** is a vanilla decorative entity: it looks like a player and displays the skin of the profile it is given — spawn one with `/summon minecraft:mannequin ~ ~ ~ {profile:"<player name>"}`.)

Client-side only: **nothing has to be installed or configured on the server**. When no skin data is available the mod does not interfere at all and everything behaves exactly like vanilla.

## Before you start

- Minecraft **26.2** with **Fabric Loader**
- **Fabric API** and **Cloth Config** are required as well
- **Mod Menu is optional** — it only adds an in-game configuration screen; without it you can use `/offlineskins reload` or edit the config file directly

## Installation

Drop the mod jar into your client's `mods/` folder.

## Adding skins and capes

Put the files inside `config/offlineskins/` in your game directory (for a normal client that is `.minecraft/config/offlineskins/`). The folders are created on first launch:

```
config/offlineskins/
├── config.json
├── skins/
│   ├── <player name>.png
│   └── uuid/<32-hex-lowercase-uuid>.png
└── capes/
    ├── <player name>.png
    └── uuid/<32-hex-lowercase-uuid>.png
```

- **By player name**: `<player name>.png`. **Offline names work too**; the name must match what the game shows (case-sensitive on Linux).
- **By UUID**: `uuid/<32-hex-lowercase-uuid>.png` (for example `0123456789abcdef0123456789abcdef.png`). It is only checked first when the profile already carries a premium UUID.
- **Image rules**: 64×64 is used as-is; 64×32 is completed into 64×64 automatically (legacy skins); any other size is ignored. PNG only.

## Configuration

`config/offlineskins/config.json` (created on first launch):

| Option (JSON key)                                           | Default | Effect                                                                                     |
|-------------------------------------------------------------|---------|--------------------------------------------------------------------------------------------|
| Use the Mojang skin source (`mojangSource`)                 | on      | Fetch skins and capes for premium players from Mojang; when off, only local files are used |
| Disable skull / mannequin override (`disableSkullOverride`) | off     | When on, every skull, head item and mannequin is rendered by vanilla again                 |
| Worker threads (`workerThreads`)                            | 8       | Range 1–32; raise it if players load slowly on a busy server                               |

- With **Mod Menu** installed you can change these in **Mod Menu → OfflineSkins → Configuration**; saving applies them immediately and writes the file back.
- You can also edit the JSON and run `/offlineskins reload` (or restart the game).
- Missing options are added to the file automatically; your existing values and any extra keys you added are preserved.

## Commands

| Command                | Effect                                                                                                   |
|------------------------|----------------------------------------------------------------------------------------------------------|
| `/offlineskins status` | Shows the current config, thread count, skin/cape service statistics and whether all 5 hooks are applied |
| `/offlineskins reload` | Re-reads the config and applies it immediately                                                           |
| `/offlineskins clear`  | Clears the skin caches and textures (they are rebuilt on the next render)                                |

## Priority and refresh timing

- **Mojang's data wins over local files**: a local file usually shows first, then the official skin replaces it once it arrives. If Mojang cannot be reached (offline name, no connection) the local file stays.
- Skins are cached in memory only — **official skins are never written to disk** and are fetched again on the next launch.
- **Failures are retried automatically**: starting at ~5 seconds and backing off up to 160 seconds, **without leaving the world**. A local file added later is picked up too.

## Why is my skin not showing up?

1. Is the file in `<game directory>/config/offlineskins/skins/`?
2. Does the file name match the in-game player name exactly? (case-sensitive on Linux)
3. Is it a 64×64 or 64×32 PNG?
4. Is that name a premium account? If so, the official skin overrides your local file — put it in `skins/uuid/<premium uuid>.png` instead, or turn the Mojang source off temporarily to check.
5. Only heads or mannequins wrong? Check whether "disable skull / mannequin override" is on.
6. Still stuck: run `/offlineskins status` and send the lines containing `OfflineSkins` from `logs/latest.log` to the maintainer. For more detail, add `-Dfabric.log.level=debug` to the launch arguments.

## Some players' skins do not come from this mod

Only players shown as **ready** in `/offlineskins status` get their skin from this mod. Players shown as **no data** are completely untouched by it — their skin (or the default skin) comes from **vanilla**, i.e. from the profile the server sent. Typical cases: a server plugin or proxy supplied a real profile; or **Carpet fake players** (when a fake player is created for a premium name, the profile the server resolves carries the skin information, so vanilla renders the real skin).

The most conclusive check: move this mod's jar out of `mods/` and join the same server again — **if the skin does not change, this mod is not the source**.

## Compatibility with other mods

This mod changes where skins are obtained and how they are displayed. If another skin-related mod is installed as well (for example CustomSkinLoader, Ears, Figura), they may override each other: some skins stop working, get replaced by another one, or flicker. This mod does **no detection and no conflict handling**; to narrow it down:

1. Keep only this mod and confirm it works on its own;
2. Add the other mods back one at a time to find the conflicting one;
3. If you need both, disable the overlapping parts through each mod's own settings (for example turn off this mod's head/mannequin override).

## License

Core logic of this mod was developed with reference to [zlainsama/OfflineSkins](https://github.com/zlainsama/OfflineSkins). This project is licensed under the MIT license, see [LICENSE](LICENSE).
