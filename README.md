# OfflineSkins

在**离线模式服务器**与本地单人游戏中，为客户端显示玩家真实**皮肤与披风**的 Fabric 模组；Tab 列表头像与玩家头颅同样使用这些皮肤。

模组采用"旁路 + 返回处覆盖"的方式介入：拿不到皮肤数据时完全放行，行为与原版一致。

## 环境要求

| 项            | 版本                       |
|---------------|----------------------------|
| Minecraft     | 26.2                       |
| Fabric Loader | 0.19.5+                    |
| Fabric API    | 0.152.2+26.2               |
| Java          | 25                         |
| 安装位置      | 仅客户端（服务端无需安装） |

## 构建

```bash
./gradlew build      # 产物在 build/libs/
./gradlew runClient  # 启动开发环境客户端
```

## 实现思路

架构、数据流、并发与缓存模型、渲染挂钩点见 [Development.md](Development.md)。

## 许可证

本项目采用 CC0-1.0，见 [LICENSE](LICENSE)。
