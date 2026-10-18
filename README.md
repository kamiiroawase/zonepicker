# ZonePicker

[![Build](https://github.com/kamiiroawase/zonepicker/actions/workflows/build.yml/badge.svg)](https://github.com/kamiiroawase/zonepicker/actions/workflows/build.yml)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.kamiiroawase/zonepicker.svg)](https://central.sonatype.com/artifact/io.github.kamiiroawase/zonepicker)
[![License: Unlicense](https://img.shields.io/badge/license-Unlicense-blue.svg)](LICENSE)

Android 时区选择器库：**一个 Activity 完成时区选择**——常用时区分组列表、全量搜索与「跟随系统」选项，Jetpack Compose 构建，Material 风格，自动适配深色模式与 edge-to-edge。Compose 宿主还可以用 **`ZonePickerDialog` / `ZonePickerBottomSheet`** 把同一套选择器以对话框或底部弹层直接嵌入自己的界面。

[English version](README.en.md)

## 使用

发布于 [Maven Central](https://central.sonatype.com/artifact/io.github.kamiiroawase/zonepicker)，版本跟随 `v*` git tag。Android 库（minSdk 26，无需 desugaring）；`mavenCentral()` 已是 Android Studio 新建工程的默认仓库，无需额外配置。界面由 Jetpack Compose 渲染，Compose 依赖经 Gradle 自动传递，宿主无论是否使用 Compose 均可直接接入。3.0 起界面全面迁移 Compose：公开 API 不变，不再依赖 AppCompat。

```kotlin
dependencies {
    implementation("io.github.kamiiroawase:zonepicker:3.3.0")
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
        accentColor = Color.parseColor("#3F51B5"),     // 可选，默认 #F05E1C；作用于头部与底部色带，打勾默认由它推导
        accentColorDark = Color.parseColor("#3949AB"), // 可选；夜间模式下的色带替换值（null 则两模式同色）
        markColorDark = Color.parseColor("#9FA8DA"),   // 可选；夜间模式下的打勾与光标，与色带独立取色（见下文）
        title = "选择时区",                           // 可选，默认「时区」
        locale = inAppLocale,                        // 可选；应用内语言，文字/时区名/RTL 均跟随
        defaultZoneIds = marketZones,                // 可选；自定义默认列表（如应用主要市场的时区）
    )
)
```

不传 `locale` 时选择器跟随系统语言（API 33+ 跟随系统级 per-app 语言）；应用内语言切换在 API 26-32 上没有系统通道可读，需像上面这样显式传入。Compose 入口（对话框 / 底部弹层）渲染在宿主界面内，天然跟随宿主语言，无需此参数。

默认列表按设备当前时区环形排序（自己的偏移组开头）；传 `defaultZoneIds` 时改为宿主指定的常用时区（列表中的旧别名 ID 如 `US/Pacific`、`PRC` 会像选中项一样映射到对应展示项），未被覆盖的 GMT 偏移仍会自动补一个代表时区，保证全偏移可达。两个 Compose 入口的同名参数含义相同。

`accentColor` 传一个颜色即可，两种模式各自适配：头部标题与返回箭头按该色亮度自动取黑/白，选中标记与搜索光标按当前模式的表面对比度判定，不足时回退行文字色。深浅取值不同的品牌色（如浅色模式用黑、深色模式要换一个能从深色页面里跳出来的颜色）另传 `accentColorDark`：选择器渲染在夜间模式时替换 `accentColor`——包括全屏打开期间系统切换深浅模式，Activity 重建后按新模式重新取值；null 时两种模式共用 `accentColor`。Compose 入口没有色带，`accentColor` 只作用于打勾与光标，由宿主按当前组合取值传入。

打勾需要与色带独立取色时另传 `markColor`（配套 `markColorDark`）：显式值同样按当前模式的表面对比度把关（不足 3:1 回退行文字色，alpha 强制不透明），只换打勾与光标、不动色带——深色页面配低调色带加高亮打勾就是这个形状。Compose 入口无需此参数。

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
        accentColor = Color.parseColor("#3F51B5"),   // 可选，默认 #F05E1C；作用于头部与底部色带，打勾默认由它推导
        title = "选择时区"                            // 可选，默认「时区」
    )
)
```

### Compose 入口：对话框与底部弹层

宿主本身用 Compose 时，可以不启动 Activity，直接在组合里弹出同一个选择器（3.1.0 起）。列表、搜索与「跟随系统」与全屏入口完全一致；结果沿用 `ZonePickerResult`，只回调「选中 / 跟随系统」——取消不经过 `onResult`，由 `onDismissRequest` 单独表达。可见性由调用方持有，两个回调里都应关闭：

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
        accentColor = Color(0xFF3F51B5),           // 可选，默认 #F05E1C；作用于选中标记与搜索光标
        title = "选择时区",                          // 可选，默认「时区」
    )
}
```

`ZonePickerBottomSheet(...)` 参数相同，以 Material 底部弹层呈现；点选行后先播收起动画，动画结束才回调 `onResult`。

要点：

- 界面由 Jetpack Compose 渲染，宿主无需任何 Compose 配置
- 除全屏 Activity 外提供 `ZonePickerDialog` / `ZonePickerBottomSheet` 两个 Compose 入口，可直接嵌入任意 Compose 界面，无需 Activity 结果接线
- 内置英文（默认）与简繁中文、日语、韩语界面，按系统或应用内语言自动选择；时区显示名经 ICU 以同一语言生成，其他语言回退英文界面而时区名仍随系统语言
- 列表默认 27 个常用时区、覆盖全部 GMT 偏移，从设备当前时区所在的偏移组环形排起（中文设备即东八区开头）；可传 `defaultZoneIds` 自定义常用列表，未覆盖的偏移仍自动补代表时区；「跟随系统」置顶，选中项打勾
- 全量即时搜索：时区显示名 / 时区 ID / GMT 偏移（`gmt+8`、`utc-5` 均可）/ 国家中英文名（搜「中国」可带出大陆、港澳台与新加坡时区）/ 常用城市中文名（上海、纽约、東京，简繁均可）
- 按 IANA `backward` 剔除全部旧别名（`US/Pacific`、`Japan` 等）；传入旧别名 ID 会自动映射到对应展示项
- 自动适配深色模式与 edge-to-edge（状态栏 / 导航栏 / 键盘），不受宿主 targetSdk 影响；平板与横屏下内容限宽居中
- 强调色贯穿头部与底部导航栏区域；标题与图标按强调色亮度取黑/白，选中标记与搜索光标在对比度不足时回退行文字色
- 深浅两套配色与 `zp_*` 字符串可用同名资源覆盖定制
- AAR 内置 baseline profile：AGP 8+ 的宿主构建自动合并，Profileinstaller 经 Compose 传递已在宿主类路径上，安装或首启即完成 AOT 编译，三个入口的首开速度都受益
- TalkBack 可感知选中状态，返回箭头 RTL 自动镜像
- 结果经 Activity Result 回传，不接管持久化

## 已知限制

- 城市中文别名表只收录常用城市（默认列表的城市与主要中国城市），未收录的中文城市名请用英文城市名（如 `shanghai`）或国家名代替；国家表未收录的国家同理
- 界面内置英文与简繁中文、日语、韩语；其他语言回退英文界面（时区显示名仍按该语言经 ICU 生成），如需更多语言可按需覆盖 `zp_*` 资源
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

57 个单元测试（JUnit，`testDebugUnitTest`）覆盖时区分组与旧别名映射、搜索过滤（含国家与中文城市别名）、锚定排序、强调色与 Activity Result 契约、语言与布局方向解析。质量门禁全挂在 `build` 上：Spotless 格式、Android Lint 与公开 API 金标检查（见下文）。API 参考由 CI 自动发布到 GitHub Pages：<https://kamiiroawase.github.io/zonepicker/>。演示工程为 `app` 模块（三种入口均有演示，`./gradlew :app:installDebug`）；推送 `v*` tag 触发 Release 工作流发布到 Maven Central。

### 公开 API 金标检查

`zonepicker/api/zonepicker.api` 是已发布 API 的金标快照，`apiCheck`（挂在 `build` 上）在任何漂移时失败。有意变更公开 API 时：运行 `./gradlew :zonepicker:apiDump`，审查 diff 并随代码一起提交；新增公开类型还需在 `zonepicker/build.gradle.kts` 的 `publicApiClasses` 中登记。

### 添加翻译

时区显示名无需翻译（由 ICU 按运行语言本地化），一个语言只需一个文件：

1. 把 `zonepicker/src/main/res/values/strings.xml` 复制到 `zonepicker/src/main/res/values-<locale>/strings.xml`（如 `values-ja`）；
2. 翻译 8 条界面字符串（`zp_title` … `zp_back`）；
3. `zp_taipei_name` / `zp_hong_kong_name` / `zp_macau_name` 保持**为空**——它们是大中华区显示名覆盖，只在中文变体中有意义，其他语言的名称由 ICU 提供；
4. 运行 `./gradlew build`。Android Lint 会在语言桶缺 key 时失败（`MissingTranslation`），不完整的翻译混不进来。

### 添加城市 / 国家搜索别名

别名表在 `zonepicker/src/main/kotlin/io/github/kamiiroawase/zonepicker/ZoneData.kt` 的 `COUNTRY_ZONES` 与 `CITY_ZONE_ALIASES`。中文简繁写法有差异时两个都收（东京 / 東京）；英文名已含在时区 ID 里时无需条目。请同步补充 `ZoneDataTest`——搜索行为有单元测试，测试即匹配规则的文档。

推送前运行 `./gradlew spotlessApply`——格式违规会导致构建失败。本库的注释解释「为什么这样写」而非「这行在做什么」，保持同样风格能让 PR 更快合入。

## 许可

[The Unlicense](LICENSE) —— 公共领域，随意使用。
