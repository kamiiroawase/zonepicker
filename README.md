# ZonePicker

[![Build](https://github.com/kamiiroawase/zonepicker/actions/workflows/build.yml/badge.svg)](https://github.com/kamiiroawase/zonepicker/actions)
[![License: Unlicense](https://img.shields.io/badge/License-Unlicense-blue.svg)](https://unlicense.org)

Android 时区选择器库：一个 Activity 完成时区选择，常用时区分组列表 + 全量搜索 + 跟随系统选项，Material 风格。

## 特性

- 默认列表展示 27 个常用时区，并覆盖**全部 GMT 偏移**（每个偏移至少一个代表），按偏移分组，东八区开头
- 输入关键词即时搜索全部 IANA 时区：支持**显示名 / 时区 ID / 偏移 / 国家中英文名**（搜「中国」可带出大陆、港澳台与新加坡时区），偏移搜索支持 `gmt`/`utc` 前缀（`gmt+8`、`GMT +8`、`utc-5` 均可）；已按 IANA `backward` 文件剔除全部旧别名（如 `US/Pacific`、`Japan`、`Europe/Kiev`，旧版 tzdata 设备上无规范 ID 时自动保留改名前 ID），并剔除 `SystemV/*`、`EST` 等遗留 ID 与 `GMT0`、`Greenwich` 等冗余别名
- 「跟随系统」选项置顶，当前选择打勾标识（传入旧别名 ID 如 `US/Pacific` 会自动映射到对应展示项）；选中状态对读屏（TalkBack）可见，返回箭头 RTL 自动镜像（选择页自行按系统语言解析布局方向，不依赖宿主 App 的 RTL 设置）
- 自动适配系统深色模式（内置深浅两套配色，均可覆盖定制）
- 完整适配 edge-to-edge：状态栏、导航栏与键盘 inset 自动处理，Android 15 以下系统也显式启用边到边，不受宿主 targetSdk 影响
- 支持定制强调色（头部背景、选中勾、状态栏图标自动适配深浅）与页面标题
- 结果通过 Activity Result 回传（内置类型安全的 `ZonePickerContract`，可区分选中 / 跟随系统 / 取消），不接管持久化
- minSdk 26，无需 desugaring 等额外配置
- 数据逻辑与 UI 解耦，带单元测试

## 依赖

发布于 [Maven Central](https://central.sonatype.com/artifact/io.github.kamiiroawase/zonepicker)，版本跟随 `v*` git tag 发布：

```kotlin
dependencies {
    implementation("io.github.kamiiroawase:zonepicker:2.1.0")
}
```

`mavenCentral()` 仓库已包含在 Android Studio 新建工程的默认配置中；旧坐标 `com.github.kamiiroawase:zonepicker`（JitPack）自新坐标首发版本起不再更新。

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

### 未发布

- 修复宿主应用声明 `android:supportsRtl="true"` 时引入本库导致 manifest 合并失败、无法构建的问题：库 manifest 不再声明 `supportsRtl`——库声明的任何值都会并入宿主 manifest，与宿主自身的取值冲突时即为合并错误；选择页的 RTL 布局方向本就由 Activity 运行时按 locale 解析，行为不变
- 浅色模式次要文字与图标（时区 ID 副标题、「无匹配时区」、搜索/清除图标）对比度提升至 WCAG AA（约 2.8:1 → 4.6:1）：`zpTextSecondary` 浅色值 `#9A9A9A` → `#757575`，并新增深色模式同名夜间值 `#9A9A9A`（保持原 5.9:1 对比度；单一灰值无法在深浅两种底色上同时达标）；覆盖该资源的接入方现需连同 `values-night/` 一并覆盖才会全局生效
- 首次打开时时区快照后台构建期间列表卡片显示加载指示，不再短暂空白
- 修复默认 Locale 使用本地数字（阿拉伯语、波斯语等）时 GMT 偏移标题显示本地数字、无符号偏移搜索（如「80」、输到一半的「gmt+5:3」）无法命中的问题：偏移标签格式化固定使用 `Locale.ROOT`
- 搜索匹配键（时区 ID、显示名、偏移标签）在时区快照构建时预生成，逐键过滤不再对约 600 个时区逐条重复归一化
- 浅色模式搜索框提示文字对比度提升至 WCAG AA（`zpTextHint` #B3B3B3 → #757575）
- 分组偏移标题对读屏（TalkBack）标记为标题语义
- Demo 新增自定义强调色与自定义标题演示入口
- Dependabot 新增 Gradle wrapper 版本（`gradle-version`）周更检查

### v2.1.0（2026-10-08）

- 修复自定义浅色强调色下页头内容与勾选标记不可见：运行时传入的强调色现按亮度自动为页头标题与返回箭头选黑/白前景；勾选标记与搜索光标在与背景对比度不足（WCAG 图形阈值 3:1）时回退为正文色（深浅色模式各自适配）；通过资源覆盖（`zpPrimaryColor` / `zpOnAccent`）定制的路径行为不变
- 修复带符号偏移搜索的小时前缀误报：`gmt+1` 不再命中 GMT+10~+14（`gmt-1` 同理不再命中 −10~−12），带分钟时分钟需精确匹配；非规范输入（如输入到一半的 `gmt+5:3`）与无符号写法（`80`、`gmt8`）行为不变
- 国家搜索现接受连字符/下划线写法：`united-states`、`hong-kong`、`south_korea` 与空格写法等价，与文本搜索的分隔符规则一致
- `ZonePickerViewModel` 改用 `viewModelScope` 协程构建时区快照：构建随 ViewModel 清除自动取消，语言变更即时替换进行中的构建（不再先落地一份旧语言快照）；构建调度器与时钟改为可注入
- 新增单元测试：ViewModel（快照缓存、语言变更重建、过期重建）、强调色取色，及偏移搜索精确匹配与国家搜索分隔符用例；tzdata 相关断言改为按「别名—规范 ID 存在性」判定，不再依赖本机 JDK 的 tzdb 具体版本
- 修复偏移搜索误报：`gmt+8`、`utc-5` 等带符号偏移查询只按偏移标签匹配，不再文本匹配时区 ID——`Etc/GMT+8`（实际 UTC−8）不再混入 +8 的搜索结果；`utc+2` 写法现同样支持按偏移命中（前缀统一按 GMT 标签解析），无符号写法（`gmt8` 命中 `Etc/GMT-8`）与整段 ID 搜索（`etc/gmt+8`）行为不变
- 修复传入旧别名或冗余 ID（`US/Pacific`、`Asia/Calcutta`、`Etc/UTC`、`Greenwich` 等）时选中项无勾选、无定位：比对前先解析为当前设备列表实际展示的 ID；反向同样处理——新 ID 在旧 tzdata 设备上回退到改名前 ID（`Europe/Kyiv` → `Europe/Kiev`）
- 修复横屏下侧边挖孔 / 侧边导航条遮挡内容：横向 inset 现参与头部与卡片的水平内边距
- 修复 `uk` 搜索误报乌克兰：拉丁关键词前缀匹配现要求整词相等或至少 3 字符前缀（`united`、`ame` 等行为不变，1–2 字母碎片不再命中其它国家）
- 返回键增加按压涟漪反馈；自定义强调色同步搜索光标颜色（Android 13+，更低版本沿用默认强调色光标）
- 分组最后一条与下一组标题之间不再绘制分隔线，由标题自身间距完成分组分隔
- 根目录 Gradle 脚本（`build.gradle.kts`、`settings.gradle.kts`）纳入 Spotless/ktlint 检查，随 CI 的 `gradlew build` 一并执行
- 修复含空格/连字符的英文城市名搜不到时区：文本搜索统一忽略空格、下划线与连字符（`new york` 命中 `America/New_York`，`ho chi minh` 命中 `Asia/Ho_Chi_Minh`，`port au prince` 命中 `America/Port-au-Prince`）
- `androidx.activity` 依赖改为 `api` 作用域：`ZonePickerContract` 公共签名暴露的 `ActivityResultContract` 现在进入消费者编译类路径，不再依赖宿主自带 appcompat 传递
- 补删 `US/Pacific-New` 遗留链接（tzdb 2020a 起已删除，旧 tzdata 设备上会与 `America/Los_Angeles` 重复显示）
- Release workflow 增加 concurrency 组（tag 重推时排队而非取消进行中的发布）；新增 Dependabot 配置（gradle 与 GitHub Actions 依赖周更）

### v2.0.0（2026-10-03）

- **破坏性变更**：Maven 坐标与代码包名由 `com.github.kamiiroawase` 迁移至 `io.github.kamiiroawase`（依赖坐标 `io.github.kamiiroawase:zonepicker`，包名 `io.github.kamiiroawase.zonepicker`），接入方需同步更新依赖坐标与 `import`
- 发布渠道由 JitPack 迁移至 [Maven Central](https://central.sonatype.com/artifact/io.github.kamiiroawase/zonepicker)：vanniktech maven-publish 签名上传 Central Portal 并自动发布；版本号取自 HEAD 恰好指向的 `v*` git tag（无 tag 时为 `0.0.0-SNAPSHOT`）；新增 tag 触发的 Release workflow（README 版本坐标核对、完整构建门禁、GitHub Release 附带发布产物），删除 `jitpack.yml`
- 修复宿主以 `AppCompatDelegate.setApplicationLocals` 切换应用内语言（API 33 以下）时，时区显示名仍为简体中文、界面中英混排的问题：名称语言现随选择页自身的 Context 解析，`values-<locale>` 覆盖与 AppCompat 应用内语言均可生效；应用内切换语言后（Activity 重建）快照自动重建
- 修复时区快照构建完成与并发刷新之间的竞态：`building` 标志改在新值落地后的主线程复位，期间到达的语言变更也会补一次重建
- 首次滚动定位不再依赖 `submitList` 的同步快路径：改在提交回调中按新列表定位；被搜索词过滤掉的选中项会在其首次出现时定位，无效的 `selectedZoneId` 不再无限重试
- 搜索清除按钮可见性与过滤条件统一（纯空格输入不再显示清除按钮）
- `ZoneAdapter` / `ZoneRow` 收敛为 internal，公共 API 面缩小为 `ZonePicker`、`ZonePickerContract` 及其请求/结果类型
- 发布 POM 补充 `developers` 与 `scm` 元数据；Demo 改用 `ZonePickerContract` 演示推荐用法

### v1.2.1（2026-10-03）

- 修复 Android 7.x（API 24/25）界面内边距全部丢失的问题：`paddingHorizontal/paddingVertical` 属性需 API 26 起生效，minSdk 由 24 提升至 26（**破坏性变更**：仍在支持 API 24/25 的 App 请先评估再升级）
- 搜索列表按 IANA `backward` 文件剔除全部旧别名（`US/Pacific`、`Japan`、`Europe/Kiev` 等 240+ 个与规范 ID 重复的条目），并剔除 `SystemV/*` 遗留 ID；旧版 tzdata 设备无规范 ID 时自动保留改名前 ID，不丢时区
- 选择页自行按系统语言解析 RTL 布局方向，返回箭头镜像等 RTL 行为不再依赖宿主 App 开启 RTL 支持
- 时区快照移至后台线程构建，消除首次打开时约 340 个时区显示名计算造成的主线程卡顿；加载期间不再误显示「无匹配时区」
- 首次打开自动滚动定位到当前选中的时区
- 同名同偏移时区补充 zone ID 最终排序键，列表顺序跨设备确定；Demo 旋转后保留已选时区；lint 布局警告清零（头部布局扁平化、背景移至 `windowBackground` 消除 overdraw）

### v1.2.0（2026-08-23）

- 修复 Android 15 以下系统启用边到边后状态栏 / 导航栏仍残留主题色块（如状态栏紫条、导航栏黑条）：对齐 `enableEdgeToEdge` 行为清空系统栏颜色并关闭 API 29+ 对比度遮罩，导航栏图标明暗随日夜模式切换

### v1.1.0（2026-08-23）

- 修复 Android 15 以下系统（宿主 targetSdk < 35）状态栏区域可能出现双倍留白：选择页窗口显式启用边到边，inset 处理不再依赖宿主 targetSdk 与系统默认行为
- 修复 API 29 及以下键盘可能遮挡列表底部：显式声明 `adjustResize`，键盘高度以 inset 参与列表底部避让（API 30+ 上本就是官方推荐组合，行为不变）

### v1.0.0（2026-08-23）

- 初始版本：常用时区分组列表 + 全量搜索 + 跟随系统选项，Activity Result 回传
- 类型安全的 [`ZonePickerContract`](zonepicker/src/main/kotlin/io/github/kamiiroawase/zonepicker/ZonePickerContract.kt)（`ZonePickerRequest` / `ZonePickerResult`），结果可区分「选中 / 跟随系统 / 取消」
- `ZonePickerViewModel`：时区快照跨配置变更缓存，DST 快照 30 分钟过期自动重建
- 深色模式：内置深浅两套配色，随系统切换，可整体覆盖
- 搜索：内置中英文国家关键词表（约 50 国）、GMT 偏移搜索（`gmt+8` 等免补零写法均可命中）、剔除 GMT0 / Greenwich / EST 等冗余别名与遗留 ID；搜索框带一键清除按钮
- 适配 edge-to-edge（状态栏 / 导航栏 / 键盘 inset），TalkBack 选中状态描述，返回箭头 RTL 自动镜像
- 发布链路：maven-publish 标准组件发布（含 sources），版本号取自构建时指向 HEAD 的 git tag；CI 含 Spotless、wrapper 校验、Gradle 缓存与发布产物完整性验证
- minSdk 24，JVM 目标 11；单元测试覆盖国家搜索、偏移搜索、遗留 ID 过滤、排序等

## 已知限制

- **搜索范围**：支持时区显示名、时区 ID（英文）、GMT 偏移与内置国家表中英文名。中文城市名（如「上海」）暂不可搜——请用英文城市名（如 `shanghai`）或国家名代替；国家表未收录的国家同理。已被剔除的旧别名 ID（如 `Asia/Saigon`、`Asia/Calcutta`）不可搜——请用其规范 ID 或对应城市名。
- **时区显示名语言**：默认按简体中文生成。宿主本地化 `zp_*` 字符串时时区名会跟随 App 语言（含 `AppCompatDelegate` 应用内语言切换），但需保证默认 `values/`（保持中文）与目标语言的 `values-<locale>/` 同时提供，仅覆盖默认 `values/` 为其他语言时时区名仍为中文。
- **无效入参**：传入的 `selectedZoneId` 会先解析为当前列表实际展示的 ID——IANA 旧别名（`US/Pacific`）与隐藏的 UTC/GMT 冗余别名（`Etc/UTC`）自动映射到对应展示项，新 ID 在旧 tzdata 设备上回退到改名前 ID；解析后仍不在列表中的 ID（如 `EST`、`SystemV/*` 或拼写错误）不显示任何选中标记（也不会回退为「跟随系统」）。

## 开发

```bash
./gradlew build                            # 构建、测试、lint 与 Spotless 检查
./gradlew :zonepicker:testDebugUnitTest    # 数据逻辑单元测试
./gradlew spotlessApply                    # Spotless + ktlint 自动格式化
./gradlew :zonepicker:publishToMavenLocal  # 本地发布，验证 AAR/sources/POM/module 产物
```

发布版本号取自 HEAD 恰好指向的 `v*` git tag（无 tag 时为 `0.0.0-SNAPSHOT`）。

GitHub Actions 在每次 push / PR 时自动执行构建、测试、Spotless 检查与发布产物验证（带 Gradle 依赖缓存）；推送 `v*` tag 触发 Release workflow：README 版本坐标核对与完整构建门禁通过后，签名上传 Maven Central（Central Portal 自动发布），并把产物附到 GitHub Release。

## 协议

[The Unlicense](LICENSE)——公有领域，无任何使用限制。
