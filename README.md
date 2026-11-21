# LiteStock

[English](#english) | [中文](#中文)

---

<a name="english"></a>
## English

Litematica addon for logistics restocking. Automatically scans and highlights containers that contain items needed by your projection's material list.

### Features

- **HUD Auto-Scan**: Automatically scans containers when the Litematica material list HUD is displayed, and clears highlights when the HUD is closed
- **Fangkuai-Material HUD Support**: Also supports the fangkuai-material mod's task HUD. When both HUDs are active, fangkuai-material takes priority
- **F3+i Container Reading**: Reads container contents using the in-game F3+i mechanism (ServerboundBlockEntityTagQueryPacket), no need to open containers
- **Container Selection**: Manually select which containers to scan using two-point selection mode
- **Preset System**: Save and share container position configurations via preset files
- **Local Cache**: Container data is cached locally for fast repeated access
- **Highlight Customization**: Customizable highlight color and line width
- **Works with Hidden Schematics**: Can read material lists even when the projection is hidden

### Dependencies

- Minecraft Java Edition 26.1, 26.1.1, 26.1.2, 26.2, or 26.3
- Java 25 and Fabric Loader 0.19.3 or newer
- [Fabric API](https://modrinth.com/mod/fabric-api), [Litematica](https://modrinth.com/mod/litematica), and [MaLiLib](https://modrinth.com/mod/malilib) for your Minecraft version

| Minecraft | Fabric API tested | Litematica | MaLiLib |
|---|---|---|---|
| 26.1, 26.1.1, 26.1.2 | 0.155.3+26.1.2 | 0.27.14 | 0.28.12 |
| 26.2 | 0.161.0+26.2 | 0.28.8 | 0.29.6 |
| 26.3 | 0.166.2+26.3 | 0.29.1 | 0.30.2 |

### Installation

1. Choose the LiteStock JAR whose `mc` version matches your game exactly. For example, use `litestock-mc26.3-1.5.1.jar` with Minecraft 26.3. [Releases](https://github.com/unionofblackbean/litestock/releases)
2. Place it and the matching dependencies in your Minecraft `mods` folder, replacing any previous LiteStock JAR.
3. Launch the game with the Fabric profile.

### Hotkeys

| Key | Function |
|-----|----------|
| `H` | Toggle scan |
| `K` | Add container (two-point selection mode) |
| `L` | Clear current selection |
| `L + O` | Open config GUI |

### Commands

- `/litestock scan` - Normal scan
- `/litestock scan start` - Start scan with progress display
- `/litestock scan stop` - Stop scan
- `/litestock scan show` - Show cached container data
- `/litestock scan clear` - Clear cache and selection
- `/litestock clear` - Clear highlights
- `/litestock preset save <name>` - Save preset
- `/litestock preset load <name>` - Load preset
- `/litestock preset list` - List presets
- `/litestock preset delete <name>` - Delete preset

### Configuration

Open the LiteStock settings with `L + O`, or select **LiteStock** from the upper-right config switcher in a MaLiLib-based config screen such as Litematica's (`M + C`).

Available options:
- **HUD Auto-Scan**: Automatically scan containers when material list HUD is displayed (default: on)
- **Cache Expiry (seconds)**: How long cached container data remains valid (default: 300)
- **Highlight Color**: Color of the container highlight box (default: yellow)
- **Highlight Line Width**: Thickness of the highlight box lines (default: 2.5)

### Building

```bash
./gradlew build                           # Minecraft 26.3
./gradlew build -Pminecraft_version=26.1.1 # Select a different supported version
```

Build with JDK 25. Gradle downloads the dependencies and writes the JARs to `build/<minecraft-version>/libs/`. On Windows, use `gradlew.bat` and quote the version argument as `"-Pminecraft_version=26.1.1"`.

### License

MIT License

---

<a name="中文"></a>
## 中文

Litematica 投影备货清单联动 mod。自动扫描并高亮显示包含投影所需物品的容器。

### 功能特性

- **HUD 自动扫描**：显示 Litematica 材料清单 HUD 时自动扫描容器，关闭 HUD 时清除高亮
- **方块清单 HUD 支持**：同时支持方块清单（fangkuai-material）mod 的备货任务 HUD。当两个 HUD 同时激活时，方块清单优先
- **F3+i 容器读取**：使用游戏内 F3+i 机制（ServerboundBlockEntityTagQueryPacket）读取容器内容，无需打开容器
- **容器选择**：通过两点框选模式手动选择要扫描的容器
- **预设系统**：通过预设文件保存和分享容器位置配置
- **本地缓存**：容器数据缓存在本地，支持快速重复访问
- **高亮自定义**：可自定义高亮颜色和线宽
- **隐藏投影也能读取**：即使投影被隐藏也能读取材料列表

### 依赖

- Minecraft Java 版 26.1、26.1.1、26.1.2、26.2 或 26.3
- Java 25 和 Fabric Loader 0.19.3 或更新版本
- 对应游戏版本的 [Fabric API](https://modrinth.com/mod/fabric-api)、[Litematica](https://modrinth.com/mod/litematica) 和 [MaLiLib](https://modrinth.com/mod/malilib)

| Minecraft | 已验证的 Fabric API | Litematica | MaLiLib |
|---|---|---|---|
| 26.1、26.1.1、26.1.2 | 0.155.3+26.1.2 | 0.27.14 | 0.28.12 |
| 26.2 | 0.161.0+26.2 | 0.28.8 | 0.29.6 |
| 26.3 | 0.166.2+26.3 | 0.29.1 | 0.30.2 |

### 安装

1. 选择文件名中的 `mc` 版本与游戏完全一致的 LiteStock JAR。例如 Minecraft 26.3 使用 `litestock-mc26.3-1.5.1.jar`。[发布页](https://github.com/unionofblackbean/litestock/releases)
2. 将 LiteStock 和对应的依赖放入游戏的 `mods` 文件夹，替换旧的 LiteStock JAR。
3. 使用 Fabric 配置启动游戏。

### 热键

| 按键 | 功能 |
|------|------|
| `H` | 切换扫描 |
| `K` | 框选添加容器（两点选择模式） |
| `L` | 清除当前选区 |
| `L + O` | 打开配置界面 |

### 命令

- `/litestock scan` - 普通扫描
- `/litestock scan start` - 开始扫描（显示进度）
- `/litestock scan stop` - 停止扫描
- `/litestock scan show` - 显示缓存的容器数据
- `/litestock scan clear` - 清除缓存和选区
- `/litestock clear` - 清除高亮
- `/litestock preset save <名称>` - 保存预设
- `/litestock preset load <名称>` - 加载预设
- `/litestock preset list` - 列出预设
- `/litestock preset delete <名称>` - 删除预设

### 配置

使用 `L + O` 打开 LiteStock 设置，也可以在 Litematica（`M + C`）等 MaLiLib 模组的配置页中，从右上角下拉菜单选择 **LiteStock**。

可用选项：
- **HUD 自动扫描**：显示材料清单 HUD 时自动扫描容器（默认：开启）
- **缓存过期时间（秒）**：缓存的容器数据有效期（默认：300）
- **高亮颜色**：容器高亮框的颜色（默认：黄色）
- **高亮框线宽**：高亮框线条粗细（默认：2.5）

### 构建

```bash
./gradlew build                           # 构建 Minecraft 26.3 版
./gradlew build -Pminecraft_version=26.1.1 # 构建指定的支持版本
```

构建需要 JDK 25。Gradle 会自动下载依赖，把 JAR 输出到 `build/<Minecraft 版本>/libs/`。Windows 下使用 `gradlew.bat`，并将版本参数写成 `"-Pminecraft_version=26.1.1"`。

### 备注

该mod为纯添加无自然的纯vibe coding的AI编写mod，如有不足还望体谅

### 许可证

MIT License
