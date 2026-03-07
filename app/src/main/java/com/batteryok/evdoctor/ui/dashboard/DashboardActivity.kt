package com.batteryok.evdoctor.ui.dashboard

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.media.AudioManager
import android.media.ToneGenerator
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.Toast
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
import com.batteryok.evdoctor.utils.NotificationUtils
import com.batteryok.evdoctor.utils.TestExportManager
import com.batteryok.evdoctor.utils.ThemeUtils
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.UUID
import java.util.concurrent.Executors
import kotlin.concurrent.thread

class DashboardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDashboardBinding
    private lateinit var session: TestSession
    private lateinit var btAdapter: BluetoothDeviceAdapter

    private val readings = mutableListOf<BatteryReading>()
    private val voltageEntries = mutableListOf<Entry>()
    private val currentEntries = mutableListOf<Entry>()

    private val handler = Handler(Looper.getMainLooper())
    private var timerRunnable: Runnable? = null
    private var fallbackDataRunnable: Runnable? = null
    private var startTime = 0L
    private var elapsedTime = 0L
    private var isStopped = false
    private var isTestRunning = false
    private var isBluetoothConnected = false

    private var bluetoothSocket: BluetoothSocket? = null
    private var readerThread: Thread? = null
    private var isReaderActive = false
    private val exportExecutor = Executors.newSingleThreadExecutor()

    private val maxDataPoints = 30
    private val flashModeDurationMs = 15 * 60 * 1000L
    private val isFlashMode: Boolean by lazy { session.testMode.equals("FLASH", ignoreCase = true) }
    private var isFlashFinishUnlocked = false

    companion object {
        const val EXTRA_REPORT = "extra_report"
        private const val TIMER_INTERVAL_MS = 1000L
        private const val REQUEST_BLUETOOTH_PERMISSIONS = 100
        private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        @Suppress("DEPRECATION")
        session = intent.getSerializableExtra(HomeActivity.EXTRA_SESSION) as? TestSession
            ?: TestSession()

        initializeSessionExportFile()

        setupToolbar()
        setupCharts()
        setupBluetoothSheet()
        setupClickListeners()
        binding.btnPauseResume.text = "START"
        binding.liveIndicator.visibility = View.GONE
        binding.btnStopTest.text = getString(R.string.finish_test)
        NotificationUtils.ensureChannel(this)
    }

    private fun initializeSessionExportFile() {
        if (session.exportFilePath.isNotBlank()) return

        runCatching {
            TestExportManager.createSessionWorkbook(this, session)
        }.onSuccess { exportFile ->
            session = session.copy(exportFilePath = exportFile.absolutePath)
        }.onFailure {
            Toast.makeText(this, "Unable to create test data file", Toast.LENGTH_LONG).show()
        }
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
        setupLineChart(binding.voltageChart)
        setupLineChart(binding.currentChart)
    }

    private fun setupLineChart(chart: LineChart) {
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
            mode = LineDataSet.Mode.LINEAR
            cubicIntensity = 0.2f
            setDrawFilled(true)
            fillAlpha = 30
            fillColor = color
        }

        chart.data = LineData(dataSet)
        chart.invalidate()
    }

    private fun setupBluetoothSheet() {
        btAdapter = BluetoothDeviceAdapter { device -> connectToDevice(device) }

        binding.bluetoothSheetContainer.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.rvBluetoothDevices).apply {
            layoutManager = LinearLayoutManager(this@DashboardActivity)
            adapter = btAdapter
        }

        if (hasBluetoothPermissions()) loadPairedDevices()

        binding.bluetoothSheetContainer.findViewById<View>(R.id.btnScanBluetooth).setOnClickListener {
            if (hasBluetoothPermissions()) {
                loadPairedDevices()
            } else {
                requestBluetoothPermissions()
            }
        }

        binding.bluetoothSheetContainer.setOnClickListener {
            binding.bluetoothSheetContainer.visibility = View.GONE
        }
    }

    private fun loadPairedDevices() {
        val paired = BluetoothUtils.getPairedDevices(this)
        if (paired.isNotEmpty()) {
            btAdapter.updateDevices(paired.toList())
            binding.bluetoothSheetContainer.findViewById<View>(R.id.layoutNoDevices).visibility = View.GONE
        } else {
            binding.bluetoothSheetContainer.findViewById<View>(R.id.layoutNoDevices).visibility = View.VISIBLE
        }
    }

    private fun connectToDevice(device: BluetoothDevice) {
        thread {
            try {
                if (!hasBluetoothPermissions()) {
                    runOnUiThread { requestBluetoothPermissions() }
                    return@thread
                }

                val socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
                bluetoothSocket?.close()
                bluetoothSocket = socket
                socket.connect()

                runOnUiThread {
                    binding.tvBluetoothStatus.text = getString(R.string.bluetooth_connected)
                    binding.tvBluetoothStatus.setTextColor(getColor(R.color.status_good))
                    binding.ivBluetoothIcon.setImageResource(R.drawable.ic_bluetooth_connected)
                    btAdapter.setConnectedDevice(device)
                    isBluetoothConnected = true
                    binding.bluetoothSheetContainer.visibility = View.GONE
                }
            } catch (_: Exception) {
                runOnUiThread {
                    isBluetoothConnected = false
                    binding.tvBluetoothStatus.text = getString(R.string.bluetooth_disconnected)
                    binding.tvBluetoothStatus.setTextColor(getColor(R.color.status_critical))
                }
            }
        }
    }

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener { confirmExit() }
        binding.btnMenu.setOnClickListener { showPopupMenu() }
        binding.bluetoothStatusCard.setOnClickListener { showBluetoothSheet() }

        binding.btnPauseResume.setOnClickListener {
            if (!isTestRunning) {
                if (isBluetoothConnected) {
                    startTest()
                } else {
                    binding.tvBluetoothStatus.text = getString(R.string.bluetooth_disconnected)
                    binding.tvBluetoothStatus.setTextColor(getColor(R.color.status_critical))
                    showBluetoothSheet()
                }
            }
        }

        binding.btnStopTest.setOnClickListener {
            if (isFlashMode && !isFlashFinishUnlocked) {
                Toast.makeText(this, getString(R.string.flash_mode_wait_finish), Toast.LENGTH_SHORT).show()
            } else {
                stopTest()
            }
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
                R.id.menu_pair_device -> {
                    startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                    true
                }
                R.id.menu_settings -> true
                R.id.menu_about -> true
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
        isTestRunning = true
        elapsedTime = 0L
        readings.clear()
        voltageEntries.clear()
        currentEntries.clear()

        val commandSent = sendBluetoothCommand(1)
        startTimer()
        startBluetoothReader()

        if (isFlashMode) {
            isFlashFinishUnlocked = false
            binding.btnStopTest.isEnabled = false
            binding.btnStopTest.alpha = 0.5f
        } else {
            isFlashFinishUnlocked = true
            binding.btnStopTest.isEnabled = true
            binding.btnStopTest.alpha = 1f
        }

        if (!commandSent) {
            startFallbackDataFeed()
        } else {
            handler.postDelayed({
                if (!isStopped && readings.isEmpty()) {
                    startFallbackDataFeed()
                }
            }, 3000)
        }

        binding.liveIndicator.visibility = View.VISIBLE
        binding.btnPauseResume.text = "START"
        binding.btnPauseResume.isEnabled = false
    }

    private fun sendBluetoothCommand(command: Int): Boolean {
        return try {
            bluetoothSocket?.outputStream?.write(command)
            bluetoothSocket?.outputStream?.flush()
            bluetoothSocket != null
        } catch (_: Exception) {
            runOnUiThread {
                binding.tvBluetoothStatus.text = getString(R.string.bluetooth_disconnected)
                binding.tvBluetoothStatus.setTextColor(getColor(R.color.status_critical))
                isBluetoothConnected = false
            }
            false
        }
    }

    private fun startTimer() {
        timerRunnable = object : Runnable {
            override fun run() {
                if (!isStopped) {
                    if (elapsedTime <= 0L) {
                        elapsedTime = System.currentTimeMillis() - startTime
                    }
                    binding.tvTimer.text = ThemeUtils.formatTime(elapsedTime)
                    evaluateFlashModeCompletion(elapsedTime)
                    handler.postDelayed(this, TIMER_INTERVAL_MS)
                }
            }
        }
        handler.post(timerRunnable!!)
    }

    private fun startFallbackDataFeed() {
        if (fallbackDataRunnable != null) return

        fallbackDataRunnable = object : Runnable {
            override fun run() {
                if (isStopped) return

                val reading = BatterySimulator.nextReading(
                    session.batteryInfo.nominalVoltage.coerceAtLeast(48.0),
                    session.batteryInfo.nominalCapacity.coerceAtLeast(100.0)
                )
                elapsedTime = System.currentTimeMillis() - startTime
                val secs = elapsedTime / 1000f

                readings.add(reading)
                updateUI(reading, secs)
                appendReadingToWorkbook(reading, elapsedTime, "simulator")
                evaluateFlashModeCompletion(elapsedTime)

                handler.postDelayed(this, 1000)
            }
        }
        handler.post(fallbackDataRunnable!!)
    }

    private fun startBluetoothReader() {
        if (isReaderActive) return
        val socket = bluetoothSocket ?: return

        isReaderActive = true
        readerThread = thread {
            try {
                val reader = BufferedReader(InputStreamReader(socket.inputStream))
                while (isReaderActive && !isStopped) {
                    val line = reader.readLine() ?: break
                    handleHardwareLine(line)
                }
            } catch (_: Exception) {
                runOnUiThread {
                    isBluetoothConnected = false
                    binding.tvBluetoothStatus.text = getString(R.string.bluetooth_disconnected)
                    binding.tvBluetoothStatus.setTextColor(getColor(R.color.status_critical))
                }
            } finally {
                isReaderActive = false
            }
        }
    }

    private fun handleHardwareLine(line: String) {
        val parts = line.split(',').map { it.trim() }
        if (parts.size < 6) return

        val voltage = parts.getOrNull(0)?.toDoubleOrNull() ?: return
        val current = parts.getOrNull(1)?.toDoubleOrNull() ?: 0.0
        val capacity = parts.getOrNull(2)?.toDoubleOrNull() ?: 0.0
        val hour = parts.getOrNull(3)?.toIntOrNull() ?: 0
        val min = parts.getOrNull(4)?.toIntOrNull() ?: 0
        val sec = parts.getOrNull(5)?.toIntOrNull() ?: 0

        val totalSeconds = (hour * 3600 + min * 60 + sec).toFloat()
        elapsedTime = (hour * 3600L + min * 60L + sec) * 1000L

        val reading = BatteryReading(
            voltage = voltage,
            current = current,
            capacity = capacity,
            soc = session.batteryInfo.nominalCapacity.takeIf { it > 0.0 }?.let {
                (capacity / it * 100.0).coerceIn(0.0, 100.0)
            } ?: 0.0,
            temperature = 0.0,
            power = voltage * current,
            internalResistance = 0.0,
            healthScore = 0.0
        )

        runOnUiThread {
            readings.add(reading)
            updateUI(reading, totalSeconds)
            binding.tvTimer.text = String.format("%02d : %02d : %02d", hour, min, sec)
        }
        appendReadingToWorkbook(reading, elapsedTime, "bluetooth")
        evaluateFlashModeCompletion(elapsedTime)
    }

    private fun evaluateFlashModeCompletion(currentElapsedMs: Long) {
        if (!isFlashMode || isFlashFinishUnlocked || currentElapsedMs < flashModeDurationMs) return

        isFlashFinishUnlocked = true
        runOnUiThread {
            binding.btnStopTest.isEnabled = true
            binding.btnStopTest.alpha = 1f
            triggerFlashFinishAlert()
        }
    }

    private fun triggerFlashFinishAlert() {
        runCatching {
            val toneGenerator = ToneGenerator(AudioManager.STREAM_ALARM, 100)
            toneGenerator.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 1200)
        }

        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(VibratorManager::class.java)
                vibratorManager?.defaultVibrator?.let { vibrate(it) }
            } else {
                @Suppress("DEPRECATION")
                val vibrator = getSystemService(VIBRATOR_SERVICE) as? Vibrator
                vibrator?.let { vibrate(it) }
            }
        }

        NotificationUtils.showNotification(
            this,
            getString(R.string.flash_mode_finish_ready_title),
            getString(R.string.flash_mode_finish_ready_body)
        )
    }

    private fun vibrate(vibrator: Vibrator) {
        if (!vibrator.hasVibrator()) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(1200, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(1200)
        }
    }

    private fun updateUI(reading: BatteryReading, elapsedSeconds: Float) {
        binding.tvVoltage.text = String.format("%.1f", reading.voltage)
        binding.tvCurrent.text = String.format("%.1f", reading.current)
        binding.tvCapacity.text = String.format("%.1f", reading.capacity)
        binding.tvTemperature.text = String.format("%.1f", reading.temperature)
        binding.tvPower.text = String.format("%.0f", reading.power)
        binding.tvResistance.text = String.format("%.1f", reading.internalResistance)
        binding.tvSocValue.text = String.format("%.0f", reading.soc)
        binding.socProgressBar.progress = reading.soc.toInt()

        val tempColor = when {
            reading.temperature > 45 -> getColor(R.color.status_critical)
            reading.temperature > 38 -> getColor(R.color.status_warning)
            else -> getColor(R.color.status_good)
        }
        binding.tvTemperature.setTextColor(tempColor)

        voltageEntries.add(Entry(elapsedSeconds, reading.voltage.toFloat()))
        currentEntries.add(Entry(elapsedSeconds, reading.current.toFloat()))

        if (voltageEntries.size > maxDataPoints) voltageEntries.removeAt(0)
        if (currentEntries.size > maxDataPoints) currentEntries.removeAt(0)

        updateChart(binding.voltageChart, voltageEntries, Color.parseColor("#4A6CF7"), "Voltage (V)")
        updateChart(binding.currentChart, currentEntries, Color.parseColor("#7B5EA7"), "Current (A)")
    }

    private fun appendReadingToWorkbook(reading: BatteryReading, elapsedMs: Long, source: String) {
        if (session.exportFilePath.isBlank()) return
        exportExecutor.execute {
            runCatching {
                TestExportManager.appendReading(session.exportFilePath, reading, elapsedMs, source)
            }
        }
    }

    private fun finalizeExportAndNavigate(report: BatteryReport) {
        binding.btnPauseResume.isEnabled = false
        binding.btnStopTest.isEnabled = false

        exportExecutor.execute {
            var finalMessage: String? = null

            if (session.exportFilePath.isBlank()) {
                runCatching { TestExportManager.createSessionWorkbook(this, session) }
                    .onSuccess { exportFile ->
                        session = session.copy(exportFilePath = exportFile.absolutePath)
                        Log.d("EVDoctorExport", "Created export file at stop: ${exportFile.absolutePath}")
                    }
                    .onFailure {
                        Log.e("EVDoctorExport", "Unable to create test data file", it)
                        finalMessage = "Unable to create test data file"
                    }
            }

            if (session.exportFilePath.isNotBlank() && TestExportManager.hasSmtpConfig()) {
                runCatching {
                    TestExportManager.sendWorkbookBySmtp(session, session.exportFilePath)
                }.onSuccess {
                    Log.d("EVDoctorExport", "Test data email sent during finalization")
                    finalMessage = "Test data file sent via email"
                }.onFailure {
                    Log.e("EVDoctorExport", "Finalization email failed", it)
                    finalMessage = "Failed to email test data file: ${it.message ?: "Unknown error"}"
                }
            } else if (session.exportFilePath.isNotBlank()) {
                finalMessage = "SMTP config missing. File saved locally."
            }

            runOnUiThread {
                finalMessage?.let {
                    Toast.makeText(this, it, Toast.LENGTH_LONG).show()
                }
                navigateToReport(report)
            }
        }
    }

    private fun stopTest() {
        isStopped = true
        isTestRunning = false
        isReaderActive = false
        fallbackDataRunnable = null
        sendBluetoothCommand(3)

        handler.removeCallbacksAndMessages(null)
        binding.liveIndicator.visibility = View.GONE

        val report = BatterySimulator.generateReport(
            session = session,
            readings = readings,
            durationMs = elapsedTime
        )

        finalizeExportAndNavigate(report)
    }

    private fun confirmExit() {
        android.app.AlertDialog.Builder(this)
            .setTitle("Exit Test?")
            .setMessage("The current test will be stopped and unsaved data will be lost.")
            .setPositiveButton("Exit") { _, _ ->
                isStopped = true
                isTestRunning = false
                isReaderActive = false
                fallbackDataRunnable = null
                sendBluetoothCommand(3)
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
        isTestRunning = false
        isReaderActive = false
        fallbackDataRunnable = null
        handler.removeCallbacksAndMessages(null)
        exportExecutor.shutdown()
        try { bluetoothSocket?.close() } catch (_: Exception) { }
    }

    override fun onBackPressed() {
        confirmExit()
    }
}
