package io.github.kamiiroawase.zonepicker

import android.content.Intent
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.ColorUtils
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import io.github.kamiiroawase.zonepicker.databinding.ActivityZonePickerBinding
import java.util.Locale

class ZonePickerActivity : AppCompatActivity() {
    private lateinit var binding: ActivityZonePickerBinding

    private lateinit var adapter: ZoneAdapter

    private var selectedZoneId: String? = null

    /** Scrolls the checked zone to the top once, on its first appearance in the rendered
     *  list; initial launch only. */
    private var pendingSelectionScroll = false

    private var accentColor: Int = 0

    private val viewModel by lazy { ViewModelProvider(this)[ZonePickerViewModel::class.java] }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Zone names must follow the picker's own UI language. They resolve against this
        // activity's context — AppCompat per-app locales are applied there, never to the
        // application context — and are re-pushed on every recreation, so an in-app language
        // switch surviving recreation rebuilds the snapshot.
        viewModel.setZoneNames(resolveZoneNames())

        // Stored legacy aliases (US/Pacific, Etc/UTC) and canonical IDs this old tzdata never
        // heard of resolve to the ID the list actually shows, so the checkmark lands on a
        // visible row instead of silently disappearing.
        selectedZoneId =
            intent
                .getStringExtra(ZonePicker.EXTRA_ZONE_ID)
                ?.let { ZoneData.resolveDisplayZoneId(it) }

        accentColor = intent
            .getIntExtra(ZonePicker.EXTRA_ACCENT_COLOR, ACCENT_UNSET)
            .takeIf { it != ACCENT_UNSET } ?: getColor(R.color.zpPrimaryColor)

        binding = ActivityZonePickerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // The picker mirrors in RTL locales on its own: resolving the layout direction from the
        // locale here works even when the host app has not enabled RTL support.
        binding.root.layoutDirection = View.LAYOUT_DIRECTION_LOCALE

        // Android 15 enforces edge-to-edge; older systems need the explicit opt-in so the
        // inset listener below owns the system bar padding on every host targetSdk, instead
        // of the system also reserving the bar areas and double-padding the status bar.
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // The pre-35 DecorView still paints the theme's bar colors (Material default
        // colorPrimaryDark status bar, black nav bar) over that fullscreen layout, so clear
        // them the way androidx enableEdgeToEdge does, leaving only the padded content.
        if (Build.VERSION.SDK_INT < 35) {
            @Suppress("DEPRECATION")
            window.statusBarColor = Color.TRANSPARENT
            @Suppress("DEPRECATION")
            window.navigationBarColor = Color.TRANSPARENT

            if (Build.VERSION.SDK_INT >= 29) {
                window.isStatusBarContrastEnforced = false
                window.isNavigationBarContrastEnforced = false
            }
        }

        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightNavigationBars =
            (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) !=
            Configuration.UI_MODE_NIGHT_YES

        applyWindowInsets()

        applyAccent()

        intent.getCharSequenceExtra(ZonePicker.EXTRA_TITLE)?.let { binding.headerTitle.text = it }

        binding.buttonBack.setOnClickListener { finish() }

        binding.followSystemRow.setOnClickListener { select(null) }

        binding.followSystemCheck.isVisible = selectedZoneId == null

        ViewCompat.setStateDescription(
            binding.followSystemRow,
            if (selectedZoneId == null) getString(R.string.zp_selected) else null,
        )

        binding.searchEditText.doAfterTextChanged { render() }

        binding.searchEditText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                hideKeyboard()
                true
            } else {
                false
            }
        }

        binding.searchClear.setOnClickListener { binding.searchEditText.setText("") }

        adapter = ZoneAdapter(accentColor) { zoneId -> select(zoneId) }

        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter
        binding.recyclerView.itemAnimator = null

        pendingSelectionScroll = savedInstanceState == null && selectedZoneId != null

        // Renders whenever a snapshot lands: the first build runs in the background, and a
        // stale one crossing the DST window is rebuilt in onResume.
        viewModel.snapshot.observe(this) { render() }
    }

    override fun onResume() {
        super.onResume()

        // Triggers a background rebuild when the cached snapshot has crossed the DST window
        viewModel.requestFreshSnapshot()
    }

    private fun select(zoneId: String?) {
        val data = Intent()

        zoneId?.let { data.putExtra(ZonePicker.EXTRA_ZONE_ID, it) }

        setResult(RESULT_OK, data)

        finish()
    }

    private fun applyAccent() {
        binding.header.setBackgroundColor(accentColor)

        binding.followSystemCheck.imageTintList = ColorStateList.valueOf(accentColor)

        // The theme's zp_cursor drawable carries the default zpPrimaryColor; retint it to the
        // caller's accent where the platform exposes the cursor drawable (API 33+). Earlier
        // versions keep the default-colored cursor.
        if (Build.VERSION.SDK_INT >= 33) {
            binding.searchEditText.textCursorDrawable
                ?.mutate()
                ?.setTint(accentColor)
        }

        WindowCompat
            .getInsetsController(window, window.decorView)
            .isAppearanceLightStatusBars = ColorUtils.calculateLuminance(accentColor) > 0.5
    }

    /** Header pads below the status bar; list content pads above the nav bar and keyboard.
     *  Horizontal insets — landscape display cutouts, side-placed navigation bars — pad both
     *  columns so nothing sits under them. */
    private fun applyWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val bars =
                insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout(),
                )

            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())

            binding.header.updatePadding(top = bars.top, left = bars.left, right = bars.right)

            binding.contentContainer.updatePadding(
                left = bars.left,
                right = bars.right,
                bottom = maxOf(bars.bottom, ime.bottom),
            )

            WindowInsetsCompat.CONSUMED
        }
    }

    private fun hideKeyboard() {
        val imm = getSystemService(InputMethodManager::class.java) ?: return

        imm.hideSoftInputFromWindow(binding.searchEditText.windowToken, 0)
    }

    /** Brings the checked zone to the top the first time the rendered list contains it. Runs
     *  from the submitList commit callback, so the position targets the committed list rather
     *  than whatever the async diff still holds; a selection filtered out by the current query
     *  waits for the query to clear, and an unknown zone ID gives up — it never shows a
     *  checkmark anyway. */
    private fun scrollToSelection(
        rows: List<ZoneRow>,
        snapshot: ZonePickerViewModel.Snapshot?,
    ) {
        if (!pendingSelectionScroll || snapshot == null) return

        if (snapshot.zones.none { it.zoneId == selectedZoneId }) {
            pendingSelectionScroll = false

            return
        }

        val index = rows.indexOfFirst { it is ZoneRow.Item && it.selected }

        if (index < 0) return

        pendingSelectionScroll = false

        (binding.recyclerView.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(index, 0)
    }

    private fun render() {
        val query =
            binding.searchEditText.text
                .toString()
                .trim()

        binding.searchClear.isVisible = query.isNotEmpty()

        // Default shows popular zones covering all offsets; search matches against all zones
        val snapshot = viewModel.snapshot.value

        val list =
            when {
                snapshot == null -> emptyList()
                query.isEmpty() -> snapshot.defaultZones
                else -> ZoneData.filter(snapshot.zones, query, ZoneData.countryZoneIds(query))
            }

        val rows = mutableListOf<ZoneRow>()

        list.groupBy { it.offsetSeconds }.forEach { (offset, group) ->
            rows += ZoneRow.Header(ZoneData.offsetLabel(offset))

            group.forEach {
                rows +=
                    ZoneRow.Item(
                        zoneId = it.zoneId,
                        title = it.displayName,
                        subtitle = it.zoneId,
                        selected = selectedZoneId == it.zoneId,
                    )
            }
        }

        // An empty list while the snapshot is still loading is not a "no result" state
        binding.emptyText.isVisible = snapshot != null && rows.isEmpty()

        adapter.submitList(rows) { scrollToSelection(rows, snapshot) }
    }

    /** Zone names follow the picker UI language: the default Chinese strings yield Chinese
     *  names, while a host localizing them — values-<locale> overrides or AppCompat per-app
     *  locales — gets names in that language. */
    private fun resolveZoneNames(): ZonePickerViewModel.ZoneNames =
        ZonePickerViewModel.ZoneNames(
            nameLocale = resolveNameLocale(),
            overrides =
                mapOf(
                    "Asia/Taipei" to getString(R.string.zp_taipei_name),
                    "Asia/Hong_Kong" to getString(R.string.zp_hong_kong_name),
                    "Asia/Macau" to getString(R.string.zp_macau_name),
                ),
        )

    /** Chinese while the picker's own strings are the default Chinese ones; otherwise the
     *  activity locale, which reflects both system and AppCompat per-app language. */
    private fun resolveNameLocale(): Locale {
        val appContext = applicationContext

        val chinese =
            Configuration(appContext.resources.configuration).apply { setLocale(Locale.SIMPLIFIED_CHINESE) }

        return if (appContext.createConfigurationContext(chinese).getString(R.string.zp_title) == getString(R.string.zp_title)) {
            Locale.SIMPLIFIED_CHINESE
        } else {
            resources.configuration.locales[0] ?: Locale.getDefault()
        }
    }

    private companion object {
        private const val ACCENT_UNSET = Int.MIN_VALUE
    }
}
