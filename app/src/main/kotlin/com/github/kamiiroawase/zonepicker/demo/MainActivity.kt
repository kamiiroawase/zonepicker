package com.github.kamiiroawase.zonepicker.demo

import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.github.kamiiroawase.zonepicker.ZonePicker
import com.github.kamiiroawase.zonepicker.demo.databinding.ActivityMainBinding
import java.util.Locale
import java.util.TimeZone

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    private var zoneId: String? = null

    private val pickerLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                zoneId = ZonePicker.getResultZoneId(result.data)

                updateText()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.pickButton.setOnClickListener {
            pickerLauncher.launch(ZonePicker.createIntent(this, zoneId))
        }

        updateText()
    }

    private fun updateText() {
        val timeZone = zoneId?.let { TimeZone.getTimeZone(it) } ?: TimeZone.getDefault()

        val name = timeZone.getDisplayName(false, TimeZone.LONG, Locale.SIMPLIFIED_CHINESE)

        val label = zoneId ?: getString(R.string.demo_follow_system)

        binding.resultText.text = getString(R.string.demo_current, "$name ($label)")
    }
}
