package com.batteryok.evdoctor.ui.home

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.batteryok.evdoctor.R
import com.batteryok.evdoctor.databinding.ActivityHomeBinding
import com.batteryok.evdoctor.model.BatteryInfo
import com.batteryok.evdoctor.model.ClientInfo
import com.batteryok.evdoctor.model.TestSession
import com.batteryok.evdoctor.ui.dashboard.DashboardActivity

class HomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding
    private var selectedMode: String? = null

    companion object {
        const val EXTRA_SESSION = "extra_session"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupDropdowns()
        setupModeSelection()
        setupClickListeners()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)

        binding.btnThemeToggle.setOnClickListener {
            val currentMode = AppCompatDelegate.getDefaultNightMode()
            if (currentMode == AppCompatDelegate.MODE_NIGHT_YES) {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            } else {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            }
        }
    }

    private fun setupDropdowns() {
        val chemistryTypeAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_dropdown_item_1line,
            resources.getStringArray(R.array.battery_chemistry_type_options)
        )
        binding.actvBatteryMake.setAdapter(chemistryTypeAdapter)

        val voltageAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_dropdown_item_1line,
            resources.getStringArray(R.array.battery_voltage_options)
        )
        binding.actvChemistry.setAdapter(voltageAdapter)
    }

    private fun setupModeSelection() {
        binding.cardFlashMode.setOnClickListener {
            selectMode("FLASH")
        }

        binding.cardNormalMode.setOnClickListener {
            selectMode("NORMAL")
        }
    }

    private fun selectMode(mode: String) {
        selectedMode = mode
        binding.tvModeError.visibility = View.GONE

        if (mode == "FLASH") {
            binding.cardFlashMode.background = getDrawable(R.drawable.bg_mode_card_selected)
            binding.cardNormalMode.background = getDrawable(R.drawable.bg_mode_card_normal)
            binding.ivFlashCheck.visibility = View.VISIBLE
            binding.ivNormalCheck.visibility = View.INVISIBLE
        } else {
            binding.cardNormalMode.background = getDrawable(R.drawable.bg_mode_card_selected)
            binding.cardFlashMode.background = getDrawable(R.drawable.bg_mode_card_normal)
            binding.ivNormalCheck.visibility = View.VISIBLE
            binding.ivFlashCheck.visibility = View.INVISIBLE
        }
    }

    private fun setupClickListeners() {
        binding.btnStartTest.setOnClickListener {
            if (validateForm()) {
                startTest()
            }
        }
    }

    private fun validateForm(): Boolean {
        var isValid = true

        if (binding.etClientName.text.isNullOrBlank()) {
            binding.tilClientName.error = getString(R.string.error_required)
            isValid = false
        } else {
            binding.tilClientName.error = null
        }

        if (binding.etPhone.text.isNullOrBlank()) {
            binding.tilPhone.error = getString(R.string.error_required)
            isValid = false
        } else {
            binding.tilPhone.error = null
        }

        if (binding.etBatteryModel.text.isNullOrBlank()) {
            binding.tilBatteryModel.error = getString(R.string.error_required)
            isValid = false
        } else {
            binding.tilBatteryModel.error = null
        }

        if (binding.actvBatteryMake.text.isNullOrBlank()) {
            binding.tilBatteryMake.error = getString(R.string.error_required)
            isValid = false
        } else {
            binding.tilBatteryMake.error = null
        }

        if (binding.actvChemistry.text.isNullOrBlank()) {
            binding.tilChemistry.error = getString(R.string.error_required)
            isValid = false
        } else {
            binding.tilChemistry.error = null
        }

        if (binding.etCapacity.text.isNullOrBlank()) {
            binding.tilCapacity.error = getString(R.string.error_required)
            isValid = false
        } else {
            binding.tilCapacity.error = null
        }

        if (selectedMode == null) {
            binding.tvModeError.visibility = View.VISIBLE
            isValid = false
        } else {
            binding.tvModeError.visibility = View.GONE
        }

        return isValid
    }

    private fun startTest() {
        val clientInfo = ClientInfo(
            name = binding.etClientName.text.toString().trim(),
            phone = binding.etPhone.text.toString().trim()
        )

        val batteryInfo = BatteryInfo(
            make = binding.actvBatteryMake.text.toString().trim(), // Chemistry type: LFP/LMFP/NMC/VRLA
            model = binding.etBatteryModel.text.toString().trim(), // Serial number
            chemistry = binding.etCapacity.text.toString().trim(), // Battery chemistry (text)
            nominalVoltage = binding.actvChemistry.text.toString().toDoubleOrNull() ?: 0.0
        )

        val session = TestSession(
            clientInfo = clientInfo,
            batteryInfo = batteryInfo,
            testMode = selectedMode ?: "NORMAL",
            startTime = System.currentTimeMillis()
        )

        val intent = Intent(this, DashboardActivity::class.java)
        intent.putExtra(EXTRA_SESSION, session)
        startActivity(intent)
        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
    }
}
