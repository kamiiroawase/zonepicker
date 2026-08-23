package com.github.kamiiroawase.zonepicker

import android.content.Intent
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.os.Bundle
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

        binding.searchEditText.doAfterTextChanged { refresh() }

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

        refresh()
    }

    override fun onResume() {
        super.onResume()

        // Re-renders with a fresh snapshot when the previous one has crossed the DST window
        refresh()
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

    private fun refresh() {
        val query =
            binding.searchEditText.text
                .toString()
                .trim()

        binding.searchClear.isVisible = binding.searchEditText.text.isNotBlank()

        // Default shows popular zones covering all offsets; search matches against all zones
        val snapshot = viewModel.snapshot()

        val list =
            if (query.isEmpty()) {
                snapshot.defaultZones
            } else {
                ZoneData.filter(snapshot.zones, query, ZoneData.countryZoneIds(query))
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

        binding.emptyText.isVisible = rows.isEmpty()

        adapter.submitList(rows)
    }

    private companion object {
        private const val ACCENT_UNSET = Int.MIN_VALUE
    }
}
