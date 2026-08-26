# KineticFTB

[简体中文](#简体中文) | [English](#english)

## 简体中文

### 模组定位

**KineticFTB** 是 Kinetic 系列专门面向 **FTB Quests** 的任务物品联动模块。它把物品、标签和 NBT 变种与 FTB 任务绑定起来，让玩家从物品 Tooltip 直接跳转到对应任务，同时扩展可重复物品任务的提交体验。

### 主要功能

- **物品直达任务**：鼠标悬停在已绑定物品上时显示任务跳转提示。
- **默认快捷键 G**：在支持的 GUI 中按 `G` 直接打开该物品默认绑定的 FTB 任务。
- **Alt + G 多任务列表**：一个物品关联多个任务时，可以打开任务选择界面。
- **任务收藏**：可把某个任务设为该物品的默认星标任务，以后普通打开键优先跳转到它。
- **物品 / 标签绑定**：既能绑定具体物品，也能按物品标签批量关联任务。
- **NBT 模糊匹配**：支持针对带 NBT 的物品变种进行任务绑定，适合附魔书、枪械、饰品等动态物品。
- **黑名单系统**：可排除不希望出现任务跳转提示的物品、标签或 NBT 变种。
- **可视化绑定编辑器**：通过 F6 配置中心打开绑定管理界面，不需要手写完整 JSON。
- **可重复 ItemTask 多次提交**：对允许批量提交的 FTB 物品任务提供次数输入与重复提交逻辑。
- **服务端提交校验**：多次提交最终仍由服务端 FTB Quest 数据和 TeamData 规则决定，不会只靠客户端修改任务进度。
- **独立客户端总开关**：可仅关闭本机的 Tooltip 跳转提示和快捷键跳转，不影响服务器任务数据。
- **JEI 可选兼容**：安装 JEI 时继续提供相关客户端联动。

### 配置与数据文件

```text
config/kineticcore/ftb_item_client.toml
config/kineticcore/ftb_item_quest_bindings.json
config/kineticcore/ftb_quest_favorites.json
config/kineticcore/ftb_item_blacklist.json
```

- `ftb_item_client.toml`：纯客户端任务跳转开关。
- `ftb_item_quest_bindings.json`：物品与任务绑定数据。
- `ftb_quest_favorites.json`：多任务物品的默认收藏记录。
- `ftb_item_blacklist.json`：任务跳转黑名单。

### 使用建议

先通过 F6 打开 KineticFTB 页面并进入绑定编辑器。普通玩家只需要记住 `G` 和 `Alt + G`；整合包作者则可以使用绑定、收藏和黑名单把物品说明与任务书直接连接起来。

### 运行环境

- Minecraft 1.20.1
- Minecraft Forge 47.4.2
- Java 17
- KineticCore：必须
- FTB Library：必须
- FTB Quests：必须
- FTB Teams：必须
- JEI：可选

## English

### Overview

**KineticFTB** integrates Kinetic with **FTB Quests**. It binds items, item tags and NBT variants to quest pages, provides direct quest navigation from item tooltips, and improves repeated submission for supported FTB item tasks.

### Key Features

- Quest-jump tooltip for bound items.
- `G` opens the default linked quest.
- `Alt + G` opens the multi-quest selector.
- Per-item favorite/default quest selection.
- Item, tag and fuzzy-NBT binding rules.
- Item/tag/NBT blacklist support.
- Visual binding editor in the F6 configuration center.
- Multi-submit support for eligible repeatable FTB ItemTasks.
- Server-side task submission remains authoritative.
- Local client switch for tooltip and hotkey navigation.
- Optional JEI integration.

### Configuration

```text
config/kineticcore/ftb_item_client.toml
config/kineticcore/ftb_item_quest_bindings.json
config/kineticcore/ftb_quest_favorites.json
config/kineticcore/ftb_item_blacklist.json
```

### Requirements

- Minecraft 1.20.1
- Minecraft Forge 47.4.2
- Java 17
- KineticCore: required
- FTB Library: required
- FTB Quests: required
- FTB Teams: required
- JEI: optional

## 开源协议与版权 (License)

Copyright (C) 2024-2026 XYAT.

本项目基于 **GNU Lesser General Public License v3.0 (LGPLv3)** 协议开源。

This project is open-sourced under the **GNU Lesser General Public License v3.0 (LGPLv3)**.
