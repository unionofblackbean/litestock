# LiteStock
[English](#english) | [中文](#中文)
---
<a name="english"></a>
## English
Litematica addon for logistics restocking. Automatically scans and highlights containers that contain items needed by your projection's material list.
### Features
- **HUD Auto-Scan**: Automatically scans containers when the Litematica material list HUD is displayed, and clears highlights when the HUD is closed
- **F3+i Container Reading**: Reads container contents using the in-game F3+i mechanism (ServerboundBlockEntityTagQueryPacket), no need to open containers
- **Container Selection**: Manually select which containers to scan using two-point selection mode
- **Preset System**: Save and share container position configurations via preset files
- **Local Cache**: Container data is cached locally for fast repeated access
- **Highlight Customization**: Customizable highlight color and line width
- **Works with Hidden Schematics**: Can read material lists even when the projection is hidden
### Dependencies
- Minecraft 26.1.2+
- Fabric Loader 0.19.2+
- Fabric API
- [MaLiLib](https://github.com/maruohon/malilib)
- [Litematica](https://github.com/maruohon/litematica)
### Installation
1. Download the latest `.jar` file from the [Releases](https://github.com/unionofblackbean/litestock/releases) page
2. Place the `.jar` file into your Minecraft `mods` folder
3. Launch the game with the Fabric profile
### Hotkeys
| Key | Function |
|-----|----------|
| `H` | Toggle scan |
| `K` | Add container (two-point selection mode) |
| `L` | Clear current selection |
| `L + O` | Open config GUI |
### Commands
- `/litestock scan` - Normal scan
- `/litestock scan show` - Show cached container data
- `/litestock scan clear` - Clear cache and selection
- `/litestock scan save_cache <1/2/3>` - Save to cache slot
- `/litestock scan cache <1/2/3>` - Load from cache slot
- `/litestock preset save <name>` - Save preset
- `/litestock pre# LiteStock

[English](#english) | [中文](#中文)

---

<a name="english"></a>
## English

Litematica addon for logistics restocking. Automatically scans and highlights containers that contain items needed by your projection's material list.

### Features

- **HUD Auto-Scan**: Automatically scans containers when the Litematica material list HUD is displayed, and clears highlights when the HUD is closed
- **F3+i Container Reading**: Reads container contents using the in-game F3+i mechanism (ServerboundBlockEntityTagQueryPacket), no need to open containers
- **Container Selection**: Manually select which containers to scan using two-point selection mode
- **Preset System**: Save and share container position configurations via preset files
- **Local Cache**: Container data is cached locally for fast repeated access
- **Highlight Customization**: Customizable highlight color and line width
- **Works with Hidden Schematics**: Can read material lists even when the projection is hidden

### Dependencies

- Minecraft 26.1.2+
- Fabric Loader 0.19.2+
- Fabric API
- [MaLiLib](https://github.com/maruohon/malilib)
- [Litematica](https://github.com/maruohon/litematica)

### Installation

1. Download the latest `.jar` file from the [Releases](https://github.com/unionofblackbean/litestock/releases) page
2. Place the `.jar` file into your Minecraft `mods` folder
3. Launch the game with the Fabric profile

### Hotkeys

| Key | Function |
|-----|----------|
| `H` | Toggle scan |
| `K` | Add container (two-point selection mode) |
| `L` | Clear current selection |
| `L + O` | Open config GUI |

### Commands

- `/litestock scan` - Normal scan
- `/litestock scan show` - Show cached container data
- `/litestock scan clear` - Clear cache and selection
- `/litestock scan save_cache <1/2/3>` - Save to cache slot
- `/litestock scan cache <1/2/3>` - Load from cache slot
- `/litestock preset save <name>` - Save preset
- `/litestock preset load <name>` - Load preset
- `/litestock preset list` - List presets
- `/litestock preset delete <name>` - Delete preset

### Configuration

Access the config GUI with `L + O`, or via Mod Menu.

Available options:
- **HUD Auto-Scan**: Automatically scan containers when material list HUD is displayed (default: on)
- **Cache Expiry (seconds)**: How long cached container data remains valid (default: 300)
- **Highlight Color**: Color of the container highlight box (default: yellow)
- **Highlight Line Width**: Thickness of the highlight box lines (default: 2.5)

### Building

```bash
./gradlew build
```

Built jars will be in `build/libs/`.

### License

MIT License

---

<a name="中文"></a>
## 中文

Litematica 投影备货清单联动 mod。自动扫描并高亮显示包含投影所需物品的容器。

### 功能特性

- **HUD 自动扫描**：显示 Litematica 材料清单 HUD 时自动扫描容器，关闭 HUD 时清除高亮
- **F3+i 容器读取**：使用游戏内 F3+i 机制（ServerboundBlockEntityTagQueryPacket）读取容器内容，无需打开容器
- **容器选择**：通过两点框选模式手动选择要扫描的容器
- **预设系统**：通过预设文件保存和分享容器位置配置
- **本地缓存**：容器数据缓存在本地，支持快速重复访问
- **高亮自定义**：可自定义高亮颜色和线宽
- **隐藏投影也能读取**：即使投影被隐藏也能读取材料列表

### 依赖

- Minecraft 26.1.2+
- Fabric Loader 0.19.2+
- Fabric API
- [MaLiLib](https://github.com/maruohon/malilib)
- [Litematica](https://github.com/maruohon/litematica)

### 安装

1. 从 [Releases](https://github.com/unionofblackbean/litestock/releases) 页面下载最新的 `.jar` 文件
2. 将 `.jar` 文件放入 Minecraft 的 `mods` 文件夹
3. 使用 Fabric 配置启动游戏

### 热键

| 按键 | 功能 |
|------|------|
| `H` | 切换扫描 |
| `K` | 框选添加容器（两点选择模式） |
| `L` | 清除当前选区 |
| `L + O` | 打开配置界面 |

### 命令

- `/litestock scan` - 普通扫描
- `/litestock scan show` - 显示缓存的容器数据
- `/litestock scan clear` - 清除缓存和选区
- `/litestock scan save_cache <1/2/3>` - 保存到缓存槽
- `/litestock scan cache <1/2/3>` - 从缓存槽加载
- `/litestock preset save <名称>` - 保存预设
- `/litestock preset load <名称>` - 加载预设
- `/litestock preset list` - 列出预设
- `/litestock preset delete <名称>` - 删除预设

### 配置

使用 `L + O` 打开配置界面，或通过 Mod Menu 打开。

可用选项：
- **HUD 自动扫描**：显示材料清单 HUD 时自动扫描容器（默认：开启）
- **缓存过期时间（秒）**：缓存的容器数据有效期（默认：300）
- **高亮颜色**：容器高亮框的颜色（默认：黄色）
- **高亮框线宽**：高亮框线条粗细（默认：2.5）

### 构建

```bash
./gradlew build
```

构建完成的 jar 文件位于 `build/libs/` 目录。

### 许可证

MIT License
set load <name>` - Load preset
- `/litestock preset list` - List presets
- `/litestock preset delete <name>` - Delete preset
### Configuration
Access the config GUI with `L + O`, or via Mod Menu.
Available options:
- **HUD Auto-Scan**: Automatically scan containers when material list HUD is displayed (default: on)
- **Cache Expiry (seconds)**: How long cached container data r
