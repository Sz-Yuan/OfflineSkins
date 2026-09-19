# AGENTS.md — OfflineSkins

## 项目是什么

Minecraft **Java 版 Fabric 客户端模组**：在离线模式服务器（或本地单人）为玩家显示皮肤与披风。

- **仅客户端**：`fabric.mod.json` 只有 `client` 入口；不需要在服务端安装。
- **依赖**：Fabric Loader、**Fabric API**（见 `fabric.mod.json` 的 `depends`）。
- **单 Gradle 模块**，源码在 `src/main/java`，资源在 `src/main/resources`。
- **无测试套件、无 lint/typecheck 任务**；验证以客户端能否编译并进游戏为准。

## 运行环境

| 项            | 值                                                       |
|---------------|----------------------------------------------------------|
| Minecraft     | 26.2（无混淆，官方名称）                                 |
| Loader        | 0.19.5                                                   |
| Loom          | 1.17-SNAPSHOT                                            |
| Java          | **25**（`build.gradle` / CI / mixin `JAVA_25`）          |
| 模组版本      | `26.2-v1-fabric`（`gradle.properties` 的 `mod_version`） |
| 包名 / 构件名 | `lain.mods.skins` / `offlineskins`                       |

`fabric.mod.json` 中 `version`、`fabricloader`、`minecraft`、`fabric-api` 由 `processResources` 从 `gradle.properties` 展开。

## 构建与运行

```text
# Windows
gradlew.bat build
# 产物: build/libs/offlineskins-*.jar

# 开发客户端（例：离线用户名）
gradlew.bat runClient --args="--username <用户名>"
# 工作目录通常为项目下 run/（配置、cachedImages、日志）
```

- CI（`.github/workflows/build.yml`）：**仅在 tag push 时**构建（JDK 25 + `gradlew build`）并上传 jar。
- 可选 jar 签名：环境变量 `SIGNJAR_KEYSTORE`（base64 JKS）、`SIGNJAR_ALIAS`、`SIGNJAR_STOREPASS`、`SIGNJAR_KEYPASS`；本地开发可不设。

## 皮肤显示策略

对客户端里出现的每个玩家档案（含其他玩家）：

1. **正版用户名**（通过 Mojang 用户名→UUID 查询判定，与是否离线登录无关）  
   → 优先 **Mojang 官方源**（sessionserver 拉 textures URL 后下载）
2. **Mojang 失败**，或 **非正版名**，但本地存在 `username` 对应 png  
   → 使用 **本地皮肤**
3. **无官方皮肤且无本地文件**  
   → 模组 **不介入**，走原版默认皮肤

说明：

- 正版名在离线服上往往仍是离线 UUID：`MojangProvider` 会先 `resolveBlocking(username)` 再按正版 UUID 查 sessionserver。
- Provider 注册顺序：`MojangProvider` → `UserManagedProvider`（`SkinBundle` 取第一个 data ready 的结果）。
- 配置 `useMojang=false` 时只保留本地源。

## 运行时路径（相对游戏工作目录）

| 路径                                              | 用途                                                  |
|---------------------------------------------------|-------------------------------------------------------|
| `config/offlineskins.json`                        | 配置（缺失时写入默认值）                              |
| `cachedImages/skins/<username>.png`               | 本地皮肤（任意用户名，不要求正版）                    |
| `cachedImages/skins/uuid/<uuid去横线>.png`        | 在线/正版 UUID 对应的本地皮肤（优先于用户名文件名）   |
| `cachedImages/capes/`、`cachedImages/capes/uuid/` | 披风，规则同皮肤                                      |
| `run/`                                            | Loom `runClient` 工作目录（开发时配置与缓存常在这里） |

高分辨率皮肤：`ImageUtils` 按 `width/64` 缩放处理。

## 代码结构

入口：`lain.mods.skins.init.fabric.FabricOfflineSkins`（`ClientModInitializer`）。

| 包                                   | 职责                                                                                        |
|--------------------------------------|---------------------------------------------------------------------------------------------|
| `lain.mods.skins.init.fabric`        | 初始化、读配置、注册 Provider、动态贴图注册/释放                                            |
| `lain.mods.skins.init.fabric.mixins` | 客户端 mixin（见下）                                                                        |
| `lain.mods.skins.providers`          | `MojangProvider`（官方）、`UserManagedProvider`（本地）                                     |
| `lain.mods.skins.impl`               | `MojangService`（正版解析、纹理源）、`PlayerProfile`、`SkinData`、`Shared`、`ConfigOptions` |
| `lain.mods.skins.impl.fabric`        | `SkinUtils`、`ImageUtils`、`MinecraftUtils`（代理）                                         |
| `lain.mods.skins.api`                | `SkinProviderAPI`（`SKIN`/`CAPE`）、`SkinBundle` 及 `api.interfaces`                        |
| `lain.lib`                           | `SharedPool`、`SimpleDownloader`、`Retries`（尽量不依赖 Minecraft）                         |

显示链路（简图）：

```text
Provider (Mojang / 本地)
  → SkinProviderAPI → SkinBundle
  → FabricOfflineSkins 动态贴图 Identifier
  → SkinUtils 构造 PlayerSkin（无数据则 null）
  → PlayerListEntryMixin 覆盖 PlayerInfo#getSkin
```

## Mixins

配置：`src/main/resources/offlineskins.mixins.json`  
`fabric.mod.json` → `"mixins": ["offlineskins.mixins.json"]`  
包名：`lain.mods.skins.init.fabric.mixins`，`compatibilityLevel: JAVA_25`。

| Mixin                           | 目标                 | 作用                                                      |
|---------------------------------|----------------------|-----------------------------------------------------------|
| `PlayerListEntryMixin`          | `PlayerInfo`         | 有模组皮肤时覆盖 `getSkin`；无数据则不改                  |
| `PlayerListHudMixin`            | `PlayerTabOverlay`   | 离线时也强制 Tab 列表显示头像（改 `onlineMode()` 表达式） |
| `SkullBlockEntityRendererMixin` | `SkullBlockRenderer` | 可选：玩家头颅使用模组皮肤（`require = 0`）               |

IDE 可能对 mixin 类报 “未使用 / not found”，以 **运行时游戏表现** 为准。

## 配置项

`config/offlineskins.json`（`ConfigOptions`）：

```json
{
  "useMojang": true,
  "disablePlayerHeads": false
}
```

- `useMojang`：是否启用官方 Mojang 源  
- `disablePlayerHeads`：`true` 时不覆盖玩家头颅渲染  

json 中其它历史字段会被忽略。

## 实现要点（读代码时容易误解）

- **离线判定**（`Shared.isOfflinePlayer`）：`UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(UTF_8))` 与档案 UUID 相同，或 id/name 不完整。  
- **Mojang HTTP**：用户名查询用 `api.mojang.com/users/profiles/minecraft/<名>`；纹理用 `sessionserver.mojang.com/session/minecraft/profile/<uuid>`。`GameProfile` 为 authlib record，**查名 JSON 需手动解析**，不要对 `GameProfile` 做 Gson 反射反序列化。  
- **Minecraft 侧**：无 yarn；Minecraft API 使用官方名称（如 `Minecraft`、`PlayerInfo`、`Identifier`、`DynamicTexture`）。  
- **未介入时**：`getLocationSkin` / `getLocationCape` 返回 null → 不注册动态贴图、mixin 不覆盖 → 原版皮肤。  
- **动态贴图**：`offlineskins:textures/generated/<uuid>`，`WeakHashMap` 按 `ByteBuffer` 缓存；移除时在渲染线程 `release`。  
- **旧版皮肤**：`width == 2×height` 由 `ImageUtils.legacyFilter` 转为现代布局；`judgeSkinType` 用像素 `(55,20)`（按分辨率缩放）的 alpha 判断 slim。

## 约定

- 源码注释：`//`，中文。  
- 网络/IO 在 `SharedPool` 异步执行；`ISkinProvider.getSkin` 本身不应长时间阻塞主线程。  
- Minecraft 相关代码放在 `init/fabric`、`impl/fabric`、mixins、以及用到会话/代理的 Provider；`lain.lib` 与纯 API 层避免引入 Minecraft 类型。  
- 修改 MC 版本时同步 `gradle.properties`（`minecraft_version`、`loader_version`、`loom_version`、`fabric_api_version`、`mod_version`）并核对 mixin 注入目标签名。

## 工作目录备忘

- 开发用 `run/`：`config/`、`cachedImages/`、`logs/` 常在此目录下。  
- `.gitignore` 忽略 `run/`、`build/`、`.gradle/` 等；本地测试皮肤不会进版本库。  
- 本说明只描述项目现状，不含移植/修复过程记录。
