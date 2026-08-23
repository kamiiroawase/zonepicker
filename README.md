# ZonePicker

[![Build](https://github.com/kamiiroawase/zonepicker/actions/workflows/build.yml/badge.svg)](https://github.com/kamiiroawase/zonepicker/actions)
[![License: Unlicense](https://img.shields.io/badge/License-Unlicense-blue.svg)](https://unlicense.org)

Android 时区选择器库：一个 Activity 完成时区选择，常用时区分组列表 + 全量搜索 + 跟随系统选项，Material 风格。

## 特性

- 默认列表展示 27 个常用时区，并覆盖**全部 GMT 偏移**（每个偏移至少一个代表），按偏移分组，东八区开头
- 输入关键词即时搜索全部 IANA 时区：支持**显示名 / 时区 ID / 偏移 / 国家中英文名**（搜「中国」可带出大陆、港澳台与新加坡时区）；已剔除 GMT0、Greenwich 等冗余别名与 EST 等遗留 ID
- 「跟随系统」选项置顶，当前选择打勾标识；选中状态对读屏（TalkBack）可见，返回箭头 RTL 自动镜像
- 自动适配系统深色模式（内置深浅两套配色，均可覆盖定制）
- 完整适配 edge-to-edge：状态栏、导航栏与键盘 inset 自动处理，Android 15 以下系统也显式启用边到边，不受宿主 targetSdk 影响
- 支持定制强调色（头部背景、选中勾、状态栏图标自动适配深浅）与页面标题
- 结果通过 Activity Result 回传（内置类型安全的 `ZonePickerContract`，可区分选中 / 跟随系统 / 取消），不接管持久化
- minSdk 24，无需 desugaring 等额外配置
- 数据逻辑与 UI 解耦，带单元测试

## 依赖

通过 [JitPack](https://jitpack.io) 引入（先在 GitHub 发布 Release 或打 tag，如 `v1.1.0`）：

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        maven { url = uri("https://jitpack.io") }
    }
}

// build.gradle.kts
dependencies {
    implementation("com.github.kamiiroawase:zonepicker:v1.1.0")
}
```

## 用法

```kotlin
private val pickerLauncher =
    registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val zoneId = ZonePicker.getResultZoneId(result.data)
            // 如 "Asia/Shanghai"；null 表示「跟随系统」
        }
    }

pickerLauncher.launch(
    ZonePicker.createIntent(
        context = this,
        selectedZoneId = currentZoneId,              // 当前选中项打勾，null 视为跟随系统
        accentColor = Color.parseColor("#3F51B5"),   // 可选，默认 #F05E1C
        title = "选择时区"                            // 可选，默认「时区」
    )
)
```

取消返回（`RESULT_CANCELED`）不携带数据，直接忽略即可。

也可以使用类型安全的 `ZonePickerContract`（推荐），结果能区分「选中 / 跟随系统 / 取消」三种状态：

```kotlin
private val pickerLauncher =
    registerForActivityResult(ZonePickerContract()) { result ->
        when (result) {
            is ZonePickerResult.Selected -> { /* result.zoneId，如 "Asia/Shanghai" */ }
            ZonePickerResult.FollowSystem -> { /* 用户选择跟随系统 */ }
            ZonePickerResult.Canceled -> { /* 用户取消，无需处理 */ }
        }
    }

pickerLauncher.launch(
    ZonePickerRequest(
        selectedZoneId = currentZoneId,              // 当前选中项打勾，null 视为跟随系统
        accentColor = Color.parseColor("#3F51B5"),   // 可选，默认 #F05E1C
        title = "选择时区",                           // 可选，默认「时区」
    )
)
```

## 定制

- **强调色 / 标题**：通过 `createIntent` 参数传入，如上。
- **配色**：库的颜色集中在 `zonepicker/res/values/colors.xml`（浅色）与 `zonepicker/res/values-night/colors.xml`（深色），`zp` 前缀，如 `zpBackground`、`zpSurface`、`zpDivider`；接入方以同名资源覆盖即可全局换肤（深色两份都要覆盖才会同时生效）。
- **字符串**：库内置字符串默认为中文；接入方覆盖同名 `zp_*` 字符串即可本地化。时区显示名默认按**简体中文**生成（与界面语言一致，不受系统语言影响）。

## Demo

`app` 模块为演示工程：`./gradlew :app:installDebug`。

## 更新日志

### v1.1.0（2026-08-23）

- 修复 Android 15 以下系统（宿主 targetSdk < 35）状态栏区域可能出现双倍留白：选择页窗口显式启用边到边，inset 处理不再依赖宿主 targetSdk 与系统默认行为
- 修复 API 29 及以下键盘可能遮挡列表底部：显式声明 `adjustResize`，键盘高度以 inset 参与列表底部避让（API 30+ 上本就是官方推荐组合，行为不变）

### v1.0.0（2026-08-23）

- 初始版本：常用时区分组列表 + 全量搜索 + 跟随系统选项，Activity Result 回传
- 类型安全的 [`ZonePickerContract`](zonepicker/src/main/kotlin/com/github/kamiiroawase/zonepicker/ZonePickerContract.kt)（`ZonePickerRequest` / `ZonePickerResult`），结果可区分「选中 / 跟随系统 / 取消」
- `ZonePickerViewModel`：时区快照跨配置变更缓存，DST 快照 30 分钟过期自动重建
- 深色模式：内置深浅两套配色，随系统切换，可整体覆盖
- 搜索：内置中英文国家关键词表（约 50 国）、GMT 偏移搜索（`gmt+8` 等免补零写法均可命中）、剔除 GMT0 / Greenwich / EST 等冗余别名与遗留 ID；搜索框带一键清除按钮
- 适配 edge-to-edge（状态栏 / 导航栏 / 键盘 inset），TalkBack 选中状态描述，返回箭头 RTL 自动镜像
- 发布链路：maven-publish 标准组件发布（含 sources），版本号取自构建时指向 HEAD 的 git tag；CI 含 Spotless、wrapper 校验、Gradle 缓存与发布产物完整性验证
- minSdk 24，JVM 目标 11；单元测试 20 个（覆盖国家搜索、偏移搜索、遗留 ID 过滤、排序等）

## 已知限制

- **搜索范围**：支持时区显示名、时区 ID（英文）、GMT 偏移与内置国家表中英文名。中文城市名（如「上海」）暂不可搜——请用英文城市名（如 `shanghai`）或国家名代替；国家表未收录的国家同理。
- **时区显示名语言**：默认按简体中文生成。宿主本地化 `zp_*` 字符串时时区名会跟随 App 语言，但需保证默认 `values/`（保持中文）与目标语言的 `values-<locale>/` 同时提供，仅覆盖默认 `values/` 为其他语言时时区名仍为中文。
- **无效入参**：传入不存在的 `selectedZoneId` 时，列表中不会有任何选中标记（也不会回退为「跟随系统」）。

## 开发

```bash
./gradlew build                                              # 构建、测试、lint 与 Spotless 检查
./gradlew :zonepicker:testDebugUnitTest                      # 数据逻辑单元测试
./gradlew spotlessApply                                      # Spotless + ktlint 自动格式化
./gradlew :zonepicker:publishReleasePublicationToMavenLocal  # 本地发布，验证 AAR/sources/POM/module 产物
```

发布版本号取自构建时指向 HEAD 的 git tag（无 tag 时为 `dev`）；JitPack 会以自己的坐标（`com.github.User:Repo:Tag`）重新发布，不受此影响。

GitHub Actions 在每次 push / PR 时自动执行构建、测试、Spotless 检查与发布产物验证（带 Gradle 依赖缓存）。

## 协议

[The Unlicense](LICENSE)——公有领域，无任何使用限制。
