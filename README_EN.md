# OfflineSkins

**Language:** [中文](./README.md) | English

Show premium Minecraft skins and capes on **offline-mode** servers (and in local worlds).

Client-side mod: install it only on your client. No matching server-side mod is required.

## Features

### Skins on offline servers

- On an offline-mode server, the client looks up the **username** to find that account’s official skin.
- If the username is a premium Minecraft name, the official skin (and cape) are used. You do **not** need to be logged in with a premium account.
- Offline UUID, premium login, or a dev launch all behave the same: what matters is whether the **username** maps to a premium account.

### Skin sources (priority order)

1. **Mojang official** (when available)  
   Premium usernames use the official skin/cape first.
2. **Local files**  
   If Mojang has no result, the request fails, or the name is not premium, a local PNG for that username is used when present.
3. **Vanilla**  
   If neither official nor local data exists, the mod does not intervene and the default vanilla skin is shown.

### Custom local skins

Place images in the game directory named after the username (premium status is not required):

| File                                                | Purpose                                                |
|-----------------------------------------------------|--------------------------------------------------------|
| `cachedImages/skins/<username>.png`                 | Skin                                                   |
| `cachedImages/skins/uuid/<uuid-without-dashes>.png` | Skin by UUID (takes priority over the name-based file) |
| `cachedImages/capes/<username>.png`                 | Cape                                                   |
| `cachedImages/capes/uuid/<uuid-without-dashes>.png` | Cape by UUID                                           |

Notes:

- The file name must match the in-game username (e.g. username `Steve` → `Steve.png`).
- With a dev client (`runClient`), paths are relative to the `run/` working directory.
- Use a game-decodable skin PNG (typically 64×64; some higher-resolution skins are supported).

### Other display

- **Tab list**: player heads are shown even in offline mode (easier to match skins).
- **Player heads**: skull rendering can use the mod skin for that player (can be turned off in config).

## Configuration

On first launch the game creates `config/offlineskins.json`:

```json
{
  "useMojang": true,
  "disablePlayerHeads": false
}
```

| Field                | Meaning                                            |
|----------------------|----------------------------------------------------|
| `useMojang`          | Whether to use the official Mojang skin source     |
| `disablePlayerHeads` | When `true`, do not replace player skull rendering |

## Requirements

- Minecraft **Java Edition 26.2**
- **Fabric Loader**
- **Fabric API**
- Install on the **client** only

## Privacy & network

- The username is used to look up public Mojang skin data; local custom skins are only read from your game directory.
- Your game profile is not uploaded. Other players’ skins appear only if that username can be resolved officially, or a matching local file exists.
