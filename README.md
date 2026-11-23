# carpet-YHaddition

[中文](#中文) | [English](#english)

A small [Carpet](https://github.com/gnembon/fabric-carpet) extension that adds a few
quality-of-life commands and rules for technical Minecraft, plus an update-skip preview
and a TNT-equivalence command that pairs with Pearl Calculator RS.

## 中文

### 功能

1. **批量召唤假人** —— `/bot spawn range <范围>`（如 `1-10`，单次最多 100 个），
   等价于逐个 `/player <名字> spawn`；`/bot clear` 清除本模组召唤的假人。
   权限规则：`/carpet commandBot true|false|ops|0-4`。
2. **旧版放水逻辑** —— `/carpet oldwaterblockplacement true` 恢复 1.21.5 之前的行为：
   手持水桶对准可含水方块潜行时，把水放进点击的方块而不是相邻方块。
3. **更新跳略显示** —— `/carpet showSkippedUpdates true`：连锁更新超过
   `max-chained-neighbor-updates` 上限后，被丢弃更新的方块用彩色粒子标出
   （青色 = 下界传送门方块，红色 = 其它方块）。
4. **更新跳略预览** —— `/carpet updateSkipPreview true`：准星对准方块后按预览键（默认 `V`），
   真实试运行一次破坏/放置并回滚，然后用客户端着色显示「红 = 会被破坏、青 = 被跳略保住」的
   下界传送门方块；功能关闭时按 V 完全静默。
5. **当量分解** —— `/yhtnt <红> <蓝> [垂直] [values <档位列表>] [repeat]`：
   用给定档位（默认 360FTL 12 档）把红/蓝 TNT 拆成具体组合，点击结果可复制。
   权限规则：`/carpet commandTntDecompose`。

### 安装

1. Fabric Loader + Fabric API + [Carpet](https://modrinth.com/mod/carpet)。
2. 把对应版本的 jar 放进 `mods`：
   - 26.2 → `carpet-YHaddition-1.6.1-mc26.2.jar`
   - 26.1.2 → `carpet-YHaddition-1.5.3-mc26.1.2.jar`
   - 1.21.11 → `carpet-YHaddition-1.7.1-mc1.21.11.jar`
3. 单人不开作弊时，可配合 carpet-org-addition 的 `/carpet openCarpetPermission true`
   让 `/bot` 等指令可用（模组会读取该规则）。

### 规则一览

| 规则 | 默认 | 说明 |
| --- | --- | --- |
| `commandBot` | `ops` | `/bot` 指令权限 |
| `commandTntDecompose` | `ops` | `/yhtnt` 指令权限 |
| `oldwaterblockplacement` | `false` | 旧版放水逻辑 |
| `showSkippedUpdates` | `false` | 粒子显示被跳略的更新 |
| `updateSkipPreview` | `false` | 按 V 试运行预览 + 传送门着色 |

预览功能需要客户端也安装本模组（着色在客户端渲染）；服务端单独安装时仍会试运行并在
聊天栏报数字。预览依赖 1,000,000 次连锁更新上限（单人固定，专用服务端可在
`server.properties` 的 `max-chained-neighbor-updates` 调小以方便测试）。

### 构建

```bash
cd versions/26.2      # 或 versions/26.1.2 / versions/1.21.11
./gradlew build
```

`libs/` 内含 Carpet 与 Fabric API 的本地 jar（各自许可证见其 jar 内），离线可构建。

## English

Carpet extension adding: batched fake-player spawning (`/bot spawn range 1-10`, `/bot clear`),
pre-1.21.5 water bucket behaviour (`oldwaterblockplacement`), particle display of updates
dropped by the chained-update cap (`showSkippedUpdates`), a trial-run preview that tints
nether portal blocks red/cyan (`updateSkipPreview`, key `V`), and TNT-equivalence
decomposition (`/yhtnt`).

## License

MIT — see [LICENSE](LICENSE). Carpet itself is MIT.
