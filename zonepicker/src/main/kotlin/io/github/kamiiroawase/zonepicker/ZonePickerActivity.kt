package io.github.kamiiroawase.zonepicker

import android.content.Intent
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.app.AppCompatActivity
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

    /** True when the accent came from the launch intent rather than the zpPrimaryColor
     *  resource: only a runtime accent owns the header's black/white foreground pairing. */
    private var runtimeAccent = false

    /** Checkmark and cursor color: the accent itself while it contrasts with the surface. */
    private var markColor: Int = 0

    /** Runs the list filter one typing-pause beat behind the keystrokes; see [onCreate]. */
    private val renderHandler = Handler(Looper.getMainLooper())

    /** True while [debouncedRender] sits in the handler queue; [onStart] flushes the beat
     *  view-state restoration schedules, before its delay can flash the unfiltered list. */
    private var renderPending = false

    private val debouncedRender =
        Runnable {
            renderPending = false
            render()
        }

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

        val passedAccent =
            intent
                .getIntExtra(ZonePicker.EXTRA_ACCENT_COLOR, ACCENT_UNSET)
                .takeIf { it != ACCENT_UNSET }
                ?.let(AccentColors::opaqueColor)

        runtimeAccent = passedAccent != null

        accentColor = passedAccent ?: getColor(R.color.zpPrimaryColor)

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

        binding.searchEditText.doAfterTextChanged { text ->
            // The clear affordance tracks the text synchronously; the whole-list filter runs
            // one debounce beat behind so a burst of keystrokes filters once, not per key.
            // render() recomputes the visibility too — both paths stay consistent.
            binding.searchClear.isVisible = !text?.toString()?.trim().isNullOrEmpty()
            renderHandler.removeCallbacks(debouncedRender)
            renderPending = true
            renderHandler.postDelayed(debouncedRender, SEARCH_RENDER_DEBOUNCE_MILLIS)
        }

        binding.searchEditText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                // The search action is the query's final word — flush the pending beat now
                renderHandler.removeCallbacks(debouncedRender)
                renderPending = false
                render()
                hideKeyboard()
                true
            } else {
                false
            }
        }

        binding.searchClear.setOnClickListener { binding.searchEditText.setText("") }

        adapter = ZoneAdapter(markColor) { zoneId -> select(zoneId) }

        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter
        binding.recyclerView.itemAnimator = null

        pendingSelectionScroll = savedInstanceState == null && selectedZoneId != null

        // Renders whenever a snapshot lands: the first build runs in the background, and a
        // stale one crossing the DST window is rebuilt in onResume.
        viewModel.snapshot.observe(this) { render() }
    }

    override fun onStart() {
        super.onStart()

        // View-state restoration runs between onCreate and onStart, re-firing the text
        // watcher with the saved query; flush that beat before the first frame so a
        // restored search never flashes the unfiltered default list through the delay
        if (renderPending) {
            renderHandler.removeCallbacks(debouncedRender)
            renderPending = false
            render()
        }
    }

    override fun onResume() {
        super.onResume()

        // Triggers a background rebuild when the cached snapshot has crossed the DST window
        viewModel.requestFreshSnapshot()
    }

    override fun onDestroy() {
        // A debounced render scheduled by the last keystroke must not fire past the activity
        renderHandler.removeCallbacks(debouncedRender)
        super.onDestroy()
    }

    private fun select(zoneId: String?) {
        val data = Intent()

        zoneId?.let { data.putExtra(ZonePicker.EXTRA_ZONE_ID, it) }

        setResult(RESULT_OK, data)

        finish()
    }

    private fun applyAccent() {
        binding.header.setBackgroundColor(accentColor)

        // A runtime accent repaints the whole header, so its title and back arrow take the
        // black/white side that reads over it; the resource path keeps the host's zpOnAccent
        // pairing untouched.
        if (runtimeAccent) {
            val onAccent = AccentColors.onAccentColor(accentColor)

            binding.headerTitle.setTextColor(onAccent)
            binding.buttonBack.imageTintList = ColorStateList.valueOf(onAccent)
        }

        // Selection marks drawn on zpSurface (checkmark, cursor) keep the accent while it
        // contrasts with that surface; a too-light accent falls back to the row's own text
        // color, which reads in both day and night modes.
        markColor =
            AccentColors.markColor(accentColor, getColor(R.color.zpSurface), getColor(R.color.zpTextPrimary))

        binding.followSystemCheck.imageTintList = ColorStateList.valueOf(markColor)

        // The theme's zp_cursor drawable carries the default zpPrimaryColor; retint it to the
        // mark color where the platform exposes the cursor drawable (API 33+). Earlier
        // versions keep the default-colored cursor.
        if (Build.VERSION.SDK_INT >= 33) {
            binding.searchEditText.textCursorDrawable
                ?.mutate()
                ?.setTint(markColor)
        }

        // Status-bar icons flip on the same light/dark edge as the header's own chrome
        WindowCompat
            .getInsetsController(window, window.decorView)
            .isAppearanceLightStatusBars = AccentColors.onAccentColor(accentColor) == Color.BLACK
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

        // The spinner stands in for the list until the first snapshot lands; a stale-triggered
        // rebuild never resurrects it, as the current snapshot stays rendered meanwhile
        binding.loadingIndicator.isVisible = snapshot == null

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

    /** Chinese while the picker's own strings are the untranslated defaults; otherwise the
     *  activity locale, which reflects both system and AppCompat per-app language. The
     *  baseline resolves the title under Locale.ROOT — the default values/ bucket, which not
     *  even a host overriding values-zh can shift — so only a real localization (values-<locale>
     *  resources or a per-app locale) flips the decision. */
    private fun resolveNameLocale(): Locale {
        val appContext = applicationContext

        val defaults =
            Configuration(appContext.resources.configuration).apply { setLocale(Locale.ROOT) }

        return if (appContext.createConfigurationContext(defaults).getString(R.string.zp_title) == getString(R.string.zp_title)) {
            Locale.SIMPLIFIED_CHINESE
        } else {
            resources.configuration.locales[0]
        }
    }

    private companion object {
        private const val ACCENT_UNSET = Int.MIN_VALUE

        /** Typing pause the list filter waits out; the immediate query UI (the clear button)
         *  never waits. */
        private const val SEARCH_RENDER_DEBOUNCE_MILLIS = 120L
    }
}
