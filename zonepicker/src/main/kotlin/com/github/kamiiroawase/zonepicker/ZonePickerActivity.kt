package com.github.kamiiroawase.zonepicker

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
import com.github.kamiiroawase.zonepicker.databinding.ActivityZonePickerBinding

class ZonePickerActivity : AppCompatActivity() {
    private lateinit var binding: ActivityZonePickerBinding

    private lateinit var adapter: ZoneAdapter

    private var selectedZoneId: String? = null

    /** Scrolls to the checked zone once the first snapshot arrives; initial launch only. */
    private var pendingSelectionScroll = false

    private var accentColor: Int = 0

    private val viewModel by lazy { ViewModelProvider(this)[ZonePickerViewModel::class.java] }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        selectedZoneId = intent.getStringExtra(ZonePicker.EXTRA_ZONE_ID)

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
        viewModel.snapshot.observe(this) { _ ->
            render()

            // After the initial render fills the list; later snapshots keep the scroll position
            scrollToSelection()
        }
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

        WindowCompat
            .getInsetsController(window, window.decorView)
            .isAppearanceLightStatusBars = ColorUtils.calculateLuminance(accentColor) > 0.5
    }

    /** Header pads below status bar/cutout; list content pads above nav bar and keyboard. */
    private fun applyWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val bars =
                insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout(),
                )

            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())

            binding.header.updatePadding(top = bars.top)
            binding.contentContainer.updatePadding(bottom = maxOf(bars.bottom, ime.bottom))

            WindowInsetsCompat.CONSUMED
        }
    }

    private fun hideKeyboard() {
        val imm = getSystemService(InputMethodManager::class.java) ?: return

        imm.hideSoftInputFromWindow(binding.searchEditText.windowToken, 0)
    }

    /** Brings the checked zone to the top once, after the initial snapshot renders. */
    private fun scrollToSelection() {
        if (!pendingSelectionScroll) return

        val index = adapter.currentList.indexOfFirst { it is ZoneRow.Item && it.selected }

        if (index < 0) return

        pendingSelectionScroll = false

        (binding.recyclerView.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(index, 0)
    }

    private fun render() {
        val query =
            binding.searchEditText.text
                .toString()
                .trim()

        binding.searchClear.isVisible = binding.searchEditText.text.isNotBlank()

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

        adapter.submitList(rows)
    }

    private companion object {
        private const val ACCENT_UNSET = Int.MIN_VALUE
    }
}
