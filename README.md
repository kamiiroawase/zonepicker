# ZonePicker

[![Build](https://github.com/kamiiroawase/zonepicker/actions/workflows/build.yml/badge.svg)](https://github.com/kamiiroawase/zonepicker/actions/workflows/build.yml)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.kamiiroawase/zonepicker.svg)](https://central.sonatype.com/artifact/io.github.kamiiroawase/zonepicker)
[![License: Unlicense](https://img.shields.io/badge/license-Unlicense-blue.svg)](LICENSE)

Android 时区选择器库：常用时区分组列表、全量搜索与「跟随系统」，三种入口共享同一套选择器：

- **`ZonePickerContract`** —— 类型安全的 Activity 入口，结果区分「选中 / 跟随系统 / 取消」
- **`ZonePickerDialog` / `ZonePickerBottomSheet`** —— Compose 入口，对话框或底部弹层直接嵌进宿主组合，无需 Activity 与结果接线

Jetpack Compose 渲染、Material 风格，自动适配深色模式与 edge-to-edge。强调色支持浅 / 深两套取值，色带与打勾可独立配色。界面内置英文（默认）与简繁中文、日语、韩语；时区显示名经 ICU 按运行语言生成。

[English version](README.en.md) · [API 文档](https://kamiiroawase.github.io/zonepicker/) · [版本发布说明](https://github.com/kamiiroawase/zonepicker/releases)

## 安装

```kotlin
dependencies {
    implementation("io.github.kamiiroawase:zonepicker:3.3.0")
}
```

发布于 [Maven Central](https://central.sonatype.com/artifact/io.github.kamiiroawase/zonepicker)，版本跟随 `v*` git tag。minSdk 26、无需 desugaring；`mavenCentral()` 已是 Android Studio 新建工程的默认仓库。Compose 依赖经 Gradle 自动传递，宿主无论是否使用 Compose 均可直接接入。3.0 起界面全面 Compose 化、不再依赖 AppCompat，公开 API 保持兼容。

## 快速开始

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
        selectedZoneId = currentZoneId,   // null 视为跟随系统
        title = "选择时区",                // 可选，默认「时区」
        locale = inAppLocale,             // 可选；应用内语言（见「语言」）
        defaultZoneIds = marketZones,     // 可选；自定义默认列表（见「默认列表」）
    )
)
```

## 参数

全部参数均有默认值，按需传用；`ZonePicker.createIntent(...)` 与 `ZonePickerRequest` 同名同义。

| 参数 | 作用 | 默认 |
|---|---|---|
| `selectedZoneId` | 打勾的当前时区 ID；传入旧别名（`US/Pacific`、`PRC` 等）自动映射到列表实际展示的行 | `null` = 跟随系统 |
| `accentColor` | 头部（含状态栏区域）与底部导航栏色带；标题与返回箭头按其亮度自动取黑 / 白 | 资源 `zpPrimaryColor`（#F05E1C） |
| `accentColorDark` | 夜间模式的色带替换值；**全屏打开期间切换深浅模式也会自动换到新模式的值**（Activity 重建重取） | `null` = 两模式共用 `accentColor` |
| `markColor` | 打勾与搜索光标；`null` 时由强调色推导，显式值直传——见「颜色规则」 | `null` = 推导 |
| `markColorDark` | 夜间替换 `markColor` | `null` = 两模式共用 `markColor` |
| `title` | 页面标题 | 资源 `zp_title` |
| `locale` | 显式界面语言（BCP-47）：文案、时区显示名、RTL 全跟随——见「语言」 | `null` = 系统语言 |
| `defaultZoneIds` | 自定义默认列表——见「默认列表」 | `null` = 内置 27 个常用时区 |

### 颜色规则

色带一种颜色即可，两种模式各自适配；深浅取值不同的品牌色（如浅色模式用黑、深色模式要换一个能从深色页面里跳出来的颜色）另传 `accentColorDark`：

```kotlin
accentColor = Color.parseColor("#3F51B5"),     // 浅色模式色带
accentColorDark = Color.parseColor("#5C6BC0"), // 夜间色带（更亮的靛蓝才能跳出深色页面）
markColorDark = Color.parseColor("#9FA8DA"),   // 夜间打勾与光标，与色带独立取色
```

打勾与搜索光标的颜色分两种情况：

- **不传 `markColor`（默认）**：由强调色推导——强调色对当前模式表面的对比度 ≥3:1 时打勾就是强调色，不足则回退为行文字色（浅色 `#333333`，深色 `#E0E0E0`）。例如浅色模式下强调色选琥珀（`#FFD54F`，约 1.5:1）或白，打勾会回退成深灰；深色模式下选黑同理回退成浅灰。回退是防呆保护：推导的打勾永远可读。夜间的推导跟随 `accentColorDark`（传了的话）。
- **传入 `markColor`（配套 `markColorDark`）**：直接生效，不过对比度把关（alpha 强制不透明），只换打勾与光标、不动色带——深色页面配低调色带加高亮打勾就是这个形状。低对比的显式选择（如浅色表面上的白）由宿主自己负责。

### 语言

不传 `locale` 时选择器跟随系统语言（API 33+ 自动跟随系统级 per-app 语言）；应用内语言切换在 API 26-32 上没有系统通道可读，需显式传入。Compose 入口渲染在宿主界面内，天然跟随宿主语言，无此参数。

### 默认列表

默认列表按设备当前时区环形排序（自己的偏移组开头；GMT+08 设备即东八区组开头），内置 27 个常用时区、覆盖全部 GMT 偏移，「跟随系统」置顶。传 `defaultZoneIds` 时改为宿主指定的常用时区（列表中的旧别名 ID 会像选中项一样映射到展示项），未被覆盖的 GMT 偏移仍会自动补一个代表时区，保证全偏移可达。两个 Compose 入口的同名参数含义相同。

## 基础写法

不需要区分取消与跟随系统时，直接用 Intent：

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
        selectedZoneId = currentZoneId,   // null 视为跟随系统
        title = "选择时区",
    )
)
```

`ZonePicker.createIntent` 的参数与上表相同；除 Contract 外，也可用 `EXTRA_*` 常量自行拼 Intent。

## Compose 入口：对话框与底部弹层

```kotlin
var showDialog by rememberSaveable { mutableStateOf(false) }

if (showDialog) {
    ZonePickerDialog(
        onResult = { result ->
            when (result) {
                is ZonePickerResult.Selected -> currentZoneId = result.zoneId
                ZonePickerResult.FollowSystem -> currentZoneId = null
                ZonePickerResult.Canceled -> Unit   // 对话框入口不会回调
            }
            showDialog = false
        },
        onDismissRequest = { showDialog = false },
        selectedZoneId = currentZoneId,            // null 视为跟随系统
        accentColor = Color(0xFF3F51B5),           // 即打勾与光标色（见下）
        title = "选择时区",
        defaultZoneIds = marketZones,              // 可选，含义同上表
    )
}
```

`ZonePickerBottomSheet(...)` 参数相同，以 Material 底部弹层呈现；点选行后先播收起动画，动画结束才回调 `onResult`。

要点：

- 列表、搜索与「跟随系统」与全屏入口完全一致；结果沿用 `ZonePickerResult`，只回调「选中 / 跟随系统」——取消不经过 `onResult`，由 `onDismissRequest` 单独表达；可见性由调用方持有，两个回调里都应关闭
- 没有 Activity 也就没有色带：`accentColor` 即打勾与光标色——显式传入直接生效，未传时取库默认橙并按「颜色规则」的推导回退
- 没有 `locale`（渲染在宿主窗口内，跟随宿主语言）与 `*Dark` 参数（每次组合由宿主传入当前模式的值即可）

## 功能要点

- 全量即时搜索：时区显示名 / 时区 ID / GMT 偏移（`gmt+8`、`utc-5` 均可）/ 国家中英文名（搜「中国」可带出大陆、港澳台与新加坡时区）/ 常用城市中文名（上海、纽约、東京，简繁均可）
- 按 IANA `backward` 剔除全部旧别名（`US/Pacific`、`Japan` 等）；传入的旧别名 ID 自动映射到对应展示项
- 自动适配深色模式与 edge-to-edge（状态栏 / 导航栏 / 键盘），不受宿主 targetSdk 影响；平板与横屏下内容限宽居中
- 深色模式下界面配色、强调色与打勾色自动切到各自的夜间取值；全屏入口在打开期间遇到系统切换深浅也会即时重取
- AAR 内置 baseline profile：AGP 8+ 的宿主构建自动合并，Profileinstaller 经 Compose 传递已在宿主类路径上，安装或首启即完成 AOT 编译，三个入口的首开速度都受益
- TalkBack 可感知选中状态与加载/无结果文案，返回箭头 RTL 自动镜像
- 结果经 Activity Result 回传，不接管持久化

## 资源覆盖定制

宿主可用同名资源覆盖库的全部配色与文案。配色在 `values/` 与 `values-night/` 各一份，昼夜各自生效：

| 资源 | 作用 |
|---|---|
| `zpPrimaryColor` / `zpOnAccent` | 默认强调色 / 色带上的标题与返回箭头颜色（仅资源默认路径使用） |
| `zpBackground` / `zpSurface` | 页面背景 / 卡片与行表面 |
| `zpTextStrong` / `zpTextPrimary` / `zpTextSecondary` / `zpTextHint` | 输入文字 / 行主文字 / 行副文字与分组头 / 搜索提示 |
| `zpDivider` | 行分隔线 |

文案 `zp_title` … `zp_back` 共 8 条；`zp_taipei_name` / `zp_hong_kong_name` / `zp_macau_name` 是大中华区显示名覆盖，只在中文语言桶有值，其他语言保持**为空**（名称交给 ICU）。

## 已知限制

- 城市中文别名表只收录常用城市（默认列表的城市与主要中国城市），未收录的中文城市名请用英文城市名（如 `shanghai`）或国家名代替；国家表未收录的国家同理
- 界面内置英文与简繁中文、日语、韩语；其他语言回退英文界面（时区显示名仍按该语言经 ICU 生成），如需更多语言可覆盖 `zp_*` 资源
- 解析后仍不在列表中的 `selectedZoneId`（如 `EST`、`SystemV/*` 或拼写错误）不显示选中标记
- Activity 入口在 API 26-32 上默认跟随系统语言，应用内语言需显式传入 `locale` 参数（系统级 per-app 语言自 API 33 起自动生效；Compose 入口始终跟随宿主）
- Android 15+ 上，个别系统的三键导航栏会绘制自己的背景，覆盖底部的强调色带

## 开发

```bash
git clone https://github.com/kamiiroawase/zonepicker.git
cd zonepicker
./gradlew build                            # 构建、测试、lint、Spotless 与公开 API 金标检查
./gradlew :zonepicker:testDebugUnitTest    # 数据逻辑单元测试
```

57 个单元测试（JUnit，`testDebugUnitTest`）覆盖时区分组与旧别名映射、搜索过滤（含国家与中文城市别名）、锚定排序、强调色与 Activity Result 契约、语言与布局方向解析。质量门禁全挂在 `build` 上：Spotless 格式、Android Lint 与公开 API 金标检查（见下文）。API 参考由 CI 自动发布到 GitHub Pages：<https://kamiiroawase.github.io/zonepicker/>。

演示工程为 `app` 模块：三种入口均有演示，四种颜色通道（浅 / 深 × 色带 / 打勾）可分组自选（`./gradlew :app:installDebug`）。

### 发布

推送 `v*` git tag 触发 Release 工作流：完整构建门禁通过后，库构件（aar / pom / sources / javadoc）签名发布到 Maven Central 并自动 release；同时构建**签名的演示 APK**（`zonepicker-demo-<tag>.apk`）附到对应的 GitHub Release。README 中的版本坐标必须与 tag 一致，发布门禁会校验。

### 公开 API 金标检查

`zonepicker/api/zonepicker.api` 是已发布 API 的金标快照，`apiCheck`（挂在 `build` 上）在任何漂移时失败。有意变更公开 API 时：运行 `./gradlew :zonepicker:apiDump`，审查 diff 并随代码一起提交；新增公开类型还需在 `zonepicker/build.gradle.kts` 的 `publicApiClasses` 中登记。

### 添加翻译

时区显示名无需翻译（由 ICU 按运行语言本地化），一个语言只需一个文件：

1. 把 `zonepicker/src/main/res/values/strings.xml` 复制到 `zonepicker/src/main/res/values-<locale>/strings.xml`（如 `values-ja`）；
2. 翻译 8 条界面字符串（`zp_title` … `zp_back`）；
3. `zp_taipei_name` / `zp_hong_kong_name` / `zp_macau_name` 保持**为空**——它们是大中华区显示名覆盖，只在中文变体中有意义；
4. 运行 `./gradlew build`。Android Lint 会在语言桶缺 key 时失败（`MissingTranslation`），不完整的翻译混不进来。

### 添加城市 / 国家搜索别名

别名表在 `zonepicker/src/main/kotlin/io/github/kamiiroawase/zonepicker/ZoneData.kt` 的 `COUNTRY_ZONES` 与 `CITY_ZONE_ALIASES`。中文简繁写法有差异时两个都收（东京 / 東京）；英文名已含在时区 ID 里时无需条目。请同步补充 `ZoneDataTest`——搜索行为有单元测试，测试即匹配规则的文档。

推送前运行 `./gradlew spotlessApply`——格式违规会导致构建失败。本库的注释解释「为什么这样写」而非「这行在做什么」，保持同样风格能让 PR 更快合入。

## 许可

[The Unlicense](LICENSE) —— 公共领域，随意使用。
