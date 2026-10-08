package io.github.kamiiroawase.zonepicker.demo

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import io.github.kamiiroawase.zonepicker.ZonePickerContract
import io.github.kamiiroawase.zonepicker.ZonePickerRequest
import io.github.kamiiroawase.zonepicker.ZonePickerResult
import io.github.kamiiroawase.zonepicker.demo.databinding.ActivityMainBinding
import java.util.Locale
import java.util.TimeZone

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    private var zoneId: String? = null

    private val pickerLauncher =
        registerForActivityResult(ZonePickerContract()) { result ->
            when (result) {
                is ZonePickerResult.Selected -> zoneId = result.zoneId
                ZonePickerResult.FollowSystem -> zoneId = null
                ZonePickerResult.Canceled -> return@registerForActivityResult
            }

            updateText()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        zoneId = savedInstanceState?.getString(STATE_ZONE_ID)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.pickButton.setOnClickListener {
            pickerLauncher.launch(
                ZonePickerRequest(
                    selectedZoneId = zoneId,
                    accentColor = if (binding.accentToggle.isChecked) CUSTOM_ACCENT_COLOR else null,
                    title =
                        binding.titleInput.text
                            .toString()
                            .trim()
                            .takeIf { it.isNotEmpty() },
                ),
            )
        }

        updateText()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        zoneId?.let { outState.putString(STATE_ZONE_ID, it) }
    }

    private fun updateText() {
        val timeZone = zoneId?.let { TimeZone.getTimeZone(it) } ?: TimeZone.getDefault()

        val name = timeZone.getDisplayName(false, TimeZone.LONG, Locale.SIMPLIFIED_CHINESE)

        val label = zoneId ?: getString(R.string.demo_follow_system)

        binding.resultText.text = getString(R.string.demo_current, "$name ($label)")
    }

    private companion object {
        private const val STATE_ZONE_ID = "zoneId"

        /** The light accent the demo toggle passes — also exercises the runtime-accent
         *  black header chrome the library picks for it. */
        private const val CUSTOM_ACCENT_COLOR = 0xFF3F51B5.toInt()
    }
}
