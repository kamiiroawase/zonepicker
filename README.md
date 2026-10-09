# ZonePicker

[![Build](https://github.com/kamiiroawase/zonepicker/actions/workflows/build.yml/badge.svg)](https://github.com/kamiiroawase/zonepicker/actions/workflows/build.yml)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.kamiiroawase/zonepicker.svg)](https://central.sonatype.com/artifact/io.github.kamiiroawase/zonepicker)
[![License: Unlicense](https://img.shields.io/badge/license-Unlicense-blue.svg)](LICENSE)

Android 时区选择器库：**一个 Activity 完成时区选择**——常用时区分组列表、全量搜索与「跟随系统」选项，Material 风格，自动适配深色模式与 edge-to-edge。

[English version](README.en.md)

## 使用

发布于 [Maven Central](https://central.sonatype.com/artifact/io.github.kamiiroawase/zonepicker)，版本跟随 `v*` git tag。Android 库（minSdk 26，无需 desugaring）；`mavenCentral()` 已是 Android Studio 新建工程的默认仓库，无需额外配置。

```kotlin
dependencies {
    implementation("io.github.kamiiroawase:zonepicker:2.3.1")
}
```

推荐用类型安全的 `ZonePickerContract` 启动，结果可区分「选中 / 跟随系统 / 取消」：

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

不需要区分取消与跟随系统时，用基础写法即可：

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
        selectedZoneId = currentZoneId,              // null 视为跟随系统
        accentColor = Color.parseColor("#3F51B5"),   // 可选，默认 #F05E1C
        title = "选择时区"                            // 可选，默认「时区」
    )
)
```

要点：

- 列表默认 27 个常用时区、覆盖全部 GMT 偏移，东八区开头；「跟随系统」置顶，选中项打勾
- 全量即时搜索：时区显示名 / 时区 ID / GMT 偏移（`gmt+8`、`utc-5` 均可）/ 国家中英文名（搜「中国」可带出大陆、港澳台与新加坡时区）
- 按 IANA `backward` 剔除全部旧别名（`US/Pacific`、`Japan` 等）；传入旧别名 ID 会自动映射到对应展示项
- 自动适配深色模式与 edge-to-edge（状态栏 / 导航栏 / 键盘），不受宿主 targetSdk 影响
- 深浅两套配色与 `zp_*` 字符串可用同名资源覆盖定制
- TalkBack 可感知选中状态，返回箭头 RTL 自动镜像
- 结果经 Activity Result 回传，不接管持久化

## 已知限制

- 中文城市名（如「上海」）暂不可搜，请用英文城市名（如 `shanghai`）或国家名代替；国家表未收录的国家同理
- 时区显示名默认简体中文。本地化需同时提供默认 `values/`（保持中文）与目标语言的 `values-<locale>/`，仅覆盖默认 `values/` 时时区名仍为中文
- 解析后仍不在列表中的 `selectedZoneId`（如 `EST`、`SystemV/*` 或拼写错误）不显示选中标记

## 开发

```bash
git clone https://github.com/kamiiroawase/zonepicker.git
cd zonepicker
./gradlew build                            # 构建、测试、lint 与 Spotless 检查
./gradlew :zonepicker:testDebugUnitTest    # 数据逻辑单元测试
```

45 个单元测试（JUnit，`testDebugUnitTest`）覆盖时区分组与旧别名映射、搜索过滤、强调色与 Activity Result 契约。质量门禁全挂在 `build` 上：Spotless 格式与 Android Lint。演示工程为 `app` 模块（`./gradlew :app:installDebug`）；推送 `v*` tag 触发 Release 工作流发布到 Maven Central。

## 许可

[The Unlicense](LICENSE) —— 公共领域，随意使用。
