package com.batteryok.evdoctor.ui.home

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.batteryok.evdoctor.R
import com.batteryok.evdoctor.databinding.ActivityHomeBinding
import com.batteryok.evdoctor.model.BatteryInfo
import com.batteryok.evdoctor.model.ClientInfo
import com.batteryok.evdoctor.model.TestSession
import com.batteryok.evdoctor.ui.dashboard.DashboardActivity
import com.batteryok.evdoctor.utils.NotificationUtils

class HomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding
    private var selectedMode: String? = null

    companion object {
        const val EXTRA_SESSION = "extra_session"
        private const val REQUEST_POST_NOTIFICATIONS = 201
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupDropdowns()
        setupDurationTags()
        setupModeSelection()
        setupClickListeners()
        ensureBackgroundAndNotificationAccess()
    }

    private fun ensureBackgroundAndNotificationAccess() {
        NotificationUtils.ensureChannel(this)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                REQUEST_POST_NOTIFICATIONS
            )
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = getSystemService(PowerManager::class.java)
            val packageName = packageName
            if (pm != null && !pm.isIgnoringBatteryOptimizations(packageName)) {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            }
        }
    }

    private fun setupDurationTags() {
        binding.tvFlashDurationTag.text = getString(R.string.flash_mode_fixed_duration)
        updateNormalModeDurationTag()

        binding.etCapacity.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) { updateNormalModeDurationTag() }
        })
        binding.actvChemistry.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) { updateNormalModeDurationTag() }
        })
    }

    private fun updateNormalModeDurationTag() {
        val voltage = binding.actvChemistry.text?.toString()?.toDoubleOrNull() ?: 0.0
        val capacity = binding.etCapacity.text?.toString()?.toDoubleOrNull() ?: 0.0

        if (voltage <= 0.0 || capacity <= 0.0) {
            binding.tvNormalDurationTag.text = getString(R.string.normal_mode_duration_placeholder)
            return
        }

        val wattHours = voltage * capacity
        val assumedChargerPowerW = 500.0
        val efficiencyFactor = 1.15
        val hours = (wattHours / assumedChargerPowerW) * efficiencyFactor
        val totalMinutes = (hours * 60).toInt().coerceAtLeast(1)
        val h = totalMinutes / 60
        val m = totalMinutes % 60

        binding.tvNormalDurationTag.text = if (h > 0) {
            getString(R.string.normal_mode_duration_h_m, h, m)
        } else {
            getString(R.string.normal_mode_duration_m, m)
        }
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
            chemistry = binding.actvBatteryMake.text.toString().trim(),
            nominalVoltage = binding.actvChemistry.text.toString().toDoubleOrNull() ?: 0.0,
            nominalCapacity = binding.etCapacity.text.toString().toDoubleOrNull() ?: 0.0
        )

        val startTime = System.currentTimeMillis()
        val draftSession = TestSession(
            clientInfo = clientInfo,
            batteryInfo = batteryInfo,
            testMode = selectedMode ?: "NORMAL",
            startTime = startTime
        )

        val intent = Intent(this, DashboardActivity::class.java)
        intent.putExtra(EXTRA_SESSION, draftSession)
        startActivity(intent)
        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
    }
}
