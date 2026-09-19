# AGENTS.md — OfflineSkins

Fabric **client-only** mod: show skins/capes on offline-mode servers (manual files + optional remote providers). Fabric port of OfflineSkins (historically rewritten from SkinPort code); hard dependency on **Fabric API** (`fabric.mod.json` `depends.fabric`). Single Gradle module. No README, no tests, no lint/typecheck tasks.

CurseForge description is user-facing marketing — when it conflicts with code (e.g. it only documents `cachedImages/skins/<name>.png`), trust the source: providers also read `cachedImages/skins/uuid/<uuid>.png`. High-res skins are supported (`ImageUtils` scales via `width/64`).

## Build (source of truth: CI + Gradle)

- Windows: `gradlew.bat build` · Unix: `./gradlew build`
- Java **21** compile target (`build.gradle` `options.release = 21`). CI uses Zulu 21. Higher JDKs that can target 21 are fine.
- Artifact: `build/libs/offlineskins-*.jar` (Loom `remapJar` output; archivesName from `gradle.properties`).
- **Verification is the build.** There is no test suite — do not invent one or expect `./gradlew test` to mean anything useful.
- CI (`.github/workflows/build.yml`) runs **only on tag push**: JDK 21 + `./gradlew --no-daemon build`, then GH Release uploads `build/libs/*.jar`. Branch pushes/PRs are not built by this workflow.
- Optional post-`remapJar` signing when env is set: `SIGNJAR_KEYSTORE` (base64 JKS), `SIGNJAR_ALIAS`, `SIGNJAR_STOREPASS`, `SIGNJAR_KEYPASS`. Locally these can be left unset.

## Version / toolchain knobs (`gradle.properties`)

- Keep these consistent when targeting a new Minecraft release:
  - `minecraft_version`, `yarn_mappings`, `loader_version`, `fabric_version`
  - `mod_version` format is currently `1.21.5-v1-fabric` (MC version + revision + loader)
- Yarn method signatures in mixins break on MC/mappings bumps — re-check inject targets after any mapping change.
- `fabric.mod.json` version is `${version}` expanded from `project.mod_version` in `processResources`.

## Architecture

Entrypoint: `lain.mods.skins.init.fabric.FabricOfflineSkins` (`ClientModInitializer` only). Mixins package: `lain.mods.skins.init.fabric.mixins` (`offlineskins.mixins.json`).

| Path | Role |
|------|------|
| `lain.mods.skins.api` / `api.interfaces` | Public API: `SkinProviderAPI.SKIN` / `CAPE`, `ISkin*` interfaces |
| `lain.mods.skins.init.fabric` | Fabric init, config load, provider registration, dynamic texture upload |
| `lain.mods.skins.init.fabric.mixins` | Client mixins: player skin/cape override, tab-list, player heads |
| `lain.mods.skins.impl` / `impl.fabric` | Offline detection, profile fill/resolve, skin data, image utils |
| `lain.mods.skins.providers` | Skin/cape sources (UserManaged, CustomServer/2, Mojang, Crafatar) |
| `lain.lib` | `SharedPool`, `SimpleDownloader`, `Retries` — keep free of Minecraft imports |

MC-facing code belongs in `init/fabric`, `impl/fabric`, mixins, and providers that touch session/proxy APIs. Prefer not to leak Minecraft types into `lain.lib` or the public API packages.

## Non-obvious runtime behavior

- **Provider order** in `FabricOfflineSkins.reloadConfig()` is intentional: always `UserManaged*` first (local cache), then optional CustomServer → CustomServer2 → Mojang → Crafatar, each gated by config flags.
- **Config file**: `./config/offlineskins.json` (Gson / `ConfigOptions`). Written with defaults on first run if missing; `validate()` only null-checks host strings.
- **Local cache layout** (UserManaged providers; CF documents the skin name path only): game-dir `./cachedImages/skins/<playerName>.png` and `./cachedImages/skins/uuid/<uuid-without-dashes>.png`; capes are **not** under `skins/` — same pattern at `./cachedImages/capes/` and `./cachedImages/capes/uuid/`. Offline profiles skip the `uuid/` dirs.
- **Offline player** check (`Shared.isOfflinePlayer`): `UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(UTF_8))` equals profile id. Incomplete profiles (null id / blank name) are treated offline and **not** cached in the offline-result cache.
- **CustomServer2 URL placeholders**: `%name%`, `%uuid%`, `%auto%` — `%auto%` resolves to name if offline, else UUID. Request only fires if the host template actually changed after substitution.
- **Skin model**: legacy skins (`width == height * 2`) are converted by `ImageUtils.legacyFilter`; `judgeSkinType` reports slim when alpha at scaled pixel `(55, 20)` is 0, else default.
- **Client-only**: `fabric.mod.json` registers only a `client` entrypoint; mixins live under `"client"`. Do not claim dedicated-server support.
- Mixin refmap name: Loom `defaultRefmapName = "${archivesBaseName}-refmap.json"` → `offlineskins-refmap.json` (must match mixins json).
- `offlineskins.mixins.json` `compatibilityLevel` is `JAVA_8` even though compile target is 21 — leave unless intentionally changing mixin compatibility.
- `PlayerListHudMixin` forces a boolean to `true` so the tab list renders with skins; `SkullBlockEntityRendererMixin` inject has `require = 0` (optional). Skin injection points return early when the mod has no ready data.
- Texture cache: dynamic `Identifier`s under `offlineskins:textures/generated/...` via `WeakHashMap`; removal listener destroys textures on the render thread.

## Working in this repo

- Sources: `src/main/java` only. Resources: `src/main/resources` (`fabric.mod.json`, `offlineskins.mixins.json`). No test source set.
- After MC/API changes, prefer compile + in-game client check over static-only review — behavior is almost entirely runtime (providers + mixins).
- This directory may not be a git worktree; verify with `Test-Path .git` before assuming commit/PR workflow.
