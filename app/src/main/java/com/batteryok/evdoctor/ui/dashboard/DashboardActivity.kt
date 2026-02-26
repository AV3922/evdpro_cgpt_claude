package com.batteryok.evdoctor.ui.dashboard

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MenuItem
import android.view.View
import android.view.animation.AnimationUtils
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.core.app.ActivityCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.batteryok.evdoctor.R
import com.batteryok.evdoctor.adapter.BluetoothDeviceAdapter
import com.batteryok.evdoctor.databinding.ActivityDashboardBinding
import com.batteryok.evdoctor.model.BatteryReading
import com.batteryok.evdoctor.model.BatteryReport
import com.batteryok.evdoctor.model.TestSession
import com.batteryok.evdoctor.ui.home.HomeActivity
import com.batteryok.evdoctor.ui.report.ReportActivity
import com.batteryok.evdoctor.utils.BatterySimulator
import com.batteryok.evdoctor.utils.BluetoothUtils
import com.batteryok.evdoctor.utils.ThemeUtils
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter

class DashboardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDashboardBinding
    private lateinit var session: TestSession
    private lateinit var btAdapter: BluetoothDeviceAdapter

    private val readings = mutableListOf<BatteryReading>()
    private val voltageEntries = mutableListOf<Entry>()
    private val currentEntries = mutableListOf<Entry>()

    private val handler = Handler(Looper.getMainLooper())
    private var timerRunnable: Runnable? = null
    private var dataRunnable: Runnable? = null
    private var startTime = 0L
    private var elapsedTime = 0L
    private var isPaused = false
    private var isStopped = false

    private val maxDataPoints = 30

    companion object {
        const val EXTRA_REPORT = "extra_report"
        private const val DATA_INTERVAL_MS = 2000L
        private const val TIMER_INTERVAL_MS = 1000L
        private const val REQUEST_BLUETOOTH_PERMISSIONS = 100
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        @Suppress("DEPRECATION")
        session = intent.getSerializableExtra(HomeActivity.EXTRA_SESSION) as? TestSession
            ?: TestSession()

        setupToolbar()
        setupCharts()
        setupBluetoothSheet()
        setupClickListeners()
        startTest()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)

        binding.tvClientName.text = session.clientInfo.name.ifBlank { "Test Session" }
        binding.tvTestModeTag.text = "${session.testMode} MODE"

        BatterySimulator.reset(
            session.batteryInfo.nominalVoltage.coerceAtLeast(48.0),
            85.0
        )
    }

    private fun setupCharts() {
        setupLineChart(
            chart = binding.voltageChart,
            color = Color.parseColor("#4A6CF7"),
            label = "Voltage (V)"
        )
        setupLineChart(
            chart = binding.currentChart,
            color = Color.parseColor("#7B5EA7"),
            label = "Current (A)"
        )
    }

    private fun setupLineChart(chart: LineChart, color: Int, label: String) {
        val isDark = ThemeUtils.isDarkMode(this)
        val axisTextColor = if (isDark) Color.parseColor("#9898B8") else Color.parseColor("#5A5A7A")
        val axisGridColor = if (isDark) Color.parseColor("#2A2A40") else Color.parseColor("#E8EAFF")
        val bgColor = if (isDark) Color.parseColor("#1A1A2E") else Color.WHITE

        chart.apply {
            description.isEnabled = false
            legend.isEnabled = false
            setTouchEnabled(true)
            setScaleEnabled(false)
            setPinchZoom(false)
            setBackgroundColor(bgColor)
            setNoDataText("Waiting for data…")
            setNoDataTextColor(axisTextColor)

            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                setDrawGridLines(true)
                gridColor = axisGridColor
                textColor = axisTextColor
                textSize = 10f
                setAvoidFirstLastClipping(true)
                granularity = 1f
                valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(value: Float) = "${value.toInt()}s"
                }
            }

            axisLeft.apply {
                setDrawGridLines(true)
                this.gridColor = axisGridColor
                this.textColor = axisTextColor
                textSize = 10f
            }

            axisRight.isEnabled = false

            animateX(800)
        }
    }

    private fun updateChart(chart: LineChart, entries: List<Entry>, color: Int, label: String) {
        if (entries.isEmpty()) return

        val dataSet = LineDataSet(entries, label).apply {
            this.color = color
            lineWidth = 2.5f
            setDrawCircles(false)
            setDrawValues(false)
            mode = LineDataSet.Mode.CUBIC_BEZIER
            cubicIntensity = 0.2f
            setDrawFilled(true)
            fillAlpha = 30
            fillColor = color
        }

        chart.data = LineData(dataSet)
        chart.invalidate()
    }

    private fun setupBluetoothSheet() {
        btAdapter = BluetoothDeviceAdapter { device ->
            connectToDevice(device)
        }

        binding.bluetoothSheetContainer.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.rvBluetoothDevices).apply {
            layoutManager = LinearLayoutManager(this@DashboardActivity)
            adapter = btAdapter
        }

        // Load paired devices
        if (hasBluetoothPermissions()) {
            loadPairedDevices()
        }

        binding.bluetoothSheetContainer.findViewById<View>(R.id.btnScanBluetooth).setOnClickListener {
            if (hasBluetoothPermissions()) {
                scanForDevices()
            } else {
                requestBluetoothPermissions()
            }
        }

        // Close sheet when clicking backdrop
        binding.bluetoothSheetContainer.setOnClickListener {
            binding.bluetoothSheetContainer.visibility = View.GONE
        }
    }

    private fun loadPairedDevices() {
        val paired = BluetoothUtils.getPairedDevices(this)
        if (paired.isNotEmpty()) {
            btAdapter.updateDevices(paired.toList())
            binding.bluetoothSheetContainer.findViewById<View>(R.id.layoutNoDevices).visibility = View.GONE
        }
    }

    private fun scanForDevices() {
        binding.bluetoothSheetContainer.findViewById<View>(R.id.btScanProgress).visibility = View.VISIBLE
        binding.bluetoothSheetContainer.findViewById<View>(R.id.layoutNoDevices).visibility = View.GONE

        // Simulate device scan - in production, use BluetoothLeScanner
        handler.postDelayed({
            binding.bluetoothSheetContainer.findViewById<View>(R.id.btScanProgress).visibility = View.GONE
            loadPairedDevices()
            if (btAdapter.itemCount == 0) {
                binding.bluetoothSheetContainer.findViewById<View>(R.id.layoutNoDevices).visibility = View.VISIBLE
            }
        }, 2000)
    }

    private fun connectToDevice(device: BluetoothDevice) {
        try {
            val deviceName = if (hasBluetoothPermissions()) device.name ?: "Device" else "Device"
            binding.tvBluetoothStatus.text = deviceName
            binding.tvBluetoothStatus.setTextColor(getColor(R.color.status_good))
            binding.ivBluetoothIcon.setImageResource(R.drawable.ic_bluetooth_connected)
            btAdapter.setConnectedDevice(device)
        } catch (e: SecurityException) {
            binding.tvBluetoothStatus.text = "Connected"
            binding.tvBluetoothStatus.setTextColor(getColor(R.color.status_good))
        }
        binding.bluetoothSheetContainer.visibility = View.GONE
    }

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener {
            confirmExit()
        }

        binding.btnMenu.setOnClickListener {
            showPopupMenu()
        }

        binding.bluetoothStatusCard.setOnClickListener {
            showBluetoothSheet()
        }

        binding.btnPauseResume.setOnClickListener {
            if (isPaused) resumeTest() else pauseTest()
        }

        binding.btnStopTest.setOnClickListener {
            stopTest()
        }
    }

    private fun showPopupMenu() {
        val popup = PopupMenu(this, binding.btnMenu)
        popup.menuInflater.inflate(R.menu.menu_dashboard, popup.menu)
        popup.setOnMenuItemClickListener { item: MenuItem ->
            when (item.itemId) {
                R.id.menu_bluetooth -> {
                    showBluetoothSheet()
                    true
                }
                R.id.menu_settings -> {
                    // Navigate to settings
                    true
                }
                R.id.menu_about -> {
                    // Show about
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    private fun showBluetoothSheet() {
        binding.bluetoothSheetContainer.visibility = View.VISIBLE
        val anim = AnimationUtils.loadAnimation(this, R.anim.slide_up)
        binding.bluetoothSheetContainer.startAnimation(anim)
    }

    private fun startTest() {
        startTime = System.currentTimeMillis()
        isStopped = false
        isPaused = false

        startTimer()
        startDataCollection()

        binding.liveIndicator.visibility = View.VISIBLE
        binding.btnPauseResume.text = getString(R.string.pause_test)
    }

    private fun startTimer() {
        timerRunnable = object : Runnable {
            override fun run() {
                if (!isPaused && !isStopped) {
                    elapsedTime = System.currentTimeMillis() - startTime
                    binding.tvTimer.text = ThemeUtils.formatTime(elapsedTime)
                    handler.postDelayed(this, TIMER_INTERVAL_MS)
                }
            }
        }
        handler.post(timerRunnable!!)
    }

    private fun startDataCollection() {
        dataRunnable = object : Runnable {
            override fun run() {
                if (!isPaused && !isStopped) {
                    val reading = BatterySimulator.nextReading(
                        session.batteryInfo.nominalVoltage.coerceAtLeast(48.0),
                        session.batteryInfo.nominalCapacity.coerceAtLeast(100.0)
                    )
                    readings.add(reading)
                    updateUI(reading)
                    handler.postDelayed(this, DATA_INTERVAL_MS)
                }
            }
        }
        handler.post(dataRunnable!!)
    }

    private fun updateUI(reading: BatteryReading) {
        // Update metric values
        binding.tvVoltage.text = String.format("%.1f", reading.voltage)
        binding.tvCurrent.text = String.format("%.1f", reading.current)
        binding.tvCapacity.text = String.format("%.1f", reading.capacity)
        binding.tvTemperature.text = String.format("%.1f", reading.temperature)
        binding.tvPower.text = String.format("%.0f", reading.power)
        binding.tvResistance.text = String.format("%.1f", reading.internalResistance)
        binding.tvSocValue.text = String.format("%.0f", reading.soc)
        binding.tvHealthScore.text = String.format("%.0f", reading.healthScore)

        // Update SOC progress
        binding.socProgressBar.progress = reading.soc.toInt()

        // Update temperature color
        val tempColor = when {
            reading.temperature > 45 -> getColor(R.color.status_critical)
            reading.temperature > 38 -> getColor(R.color.status_warning)
            else -> getColor(R.color.status_good)
        }
        binding.tvTemperature.setTextColor(tempColor)

        // Update charts
        val timeSeconds = ((System.currentTimeMillis() - startTime) / 1000f)

        voltageEntries.add(Entry(timeSeconds, reading.voltage.toFloat()))
        currentEntries.add(Entry(timeSeconds, reading.current.toFloat()))

        if (voltageEntries.size > maxDataPoints) voltageEntries.removeAt(0)
        if (currentEntries.size > maxDataPoints) currentEntries.removeAt(0)

        updateChart(
            binding.voltageChart, voltageEntries,
            Color.parseColor("#4A6CF7"), "Voltage (V)"
        )
        updateChart(
            binding.currentChart, currentEntries,
            Color.parseColor("#7B5EA7"), "Current (A)"
        )
    }

    private fun pauseTest() {
        isPaused = true
        binding.btnPauseResume.text = getString(R.string.resume_test)
        binding.liveIndicator.visibility = View.INVISIBLE
    }

    private fun resumeTest() {
        isPaused = false
        startTime = System.currentTimeMillis() - elapsedTime
        binding.btnPauseResume.text = getString(R.string.pause_test)
        binding.liveIndicator.visibility = View.VISIBLE
        startTimer()
        startDataCollection()
    }

    private fun stopTest() {
        isStopped = true
        handler.removeCallbacksAndMessages(null)
        binding.liveIndicator.visibility = View.GONE

        val report = BatterySimulator.generateReport(
            session = session,
            readings = readings,
            durationMs = elapsedTime
        )

        navigateToReport(report)
    }

    private fun confirmExit() {
        android.app.AlertDialog.Builder(this)
            .setTitle("Exit Test?")
            .setMessage("The current test will be stopped and unsaved data will be lost.")
            .setPositiveButton("Exit") { _, _ ->
                isStopped = true
                handler.removeCallbacksAndMessages(null)
                finish()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun navigateToReport(report: BatteryReport) {
        val intent = Intent(this, ReportActivity::class.java)
        intent.putExtra(EXTRA_REPORT, report)
        startActivity(intent)
        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        finish()
    }

    private fun hasBluetoothPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) ==
                    PackageManager.PERMISSION_GRANTED
        } else {
            ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH) ==
                    PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestBluetoothPermissions() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            )
        } else {
            arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        }
        ActivityCompat.requestPermissions(this, permissions, REQUEST_BLUETOOTH_PERMISSIONS)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_BLUETOOTH_PERMISSIONS) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                loadPairedDevices()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isStopped = true
        handler.removeCallbacksAndMessages(null)
    }

    override fun onBackPressed() {
        confirmExit()
    }
}
