package com.batteryok.evdoctor.ui.report

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.batteryok.evdoctor.R
import com.batteryok.evdoctor.databinding.ActivityReportBinding
import com.batteryok.evdoctor.model.BatteryFault
import com.batteryok.evdoctor.model.BatteryReport
import com.batteryok.evdoctor.model.FaultSeverity
import com.batteryok.evdoctor.model.SafetyStatus
import com.batteryok.evdoctor.ui.dashboard.DashboardActivity
import com.batteryok.evdoctor.ui.home.HomeActivity
import com.batteryok.evdoctor.utils.ThemeUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ReportActivity : AppCompatActivity() {

    private lateinit var binding: ActivityReportBinding
    private lateinit var report: BatteryReport

    companion object {
        private val DATE_FORMAT = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
        private val DATE_ONLY_FORMAT = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReportBinding.inflate(layoutInflater)
        setContentView(binding.root)

        @Suppress("DEPRECATION")
        report = intent.getSerializableExtra(DashboardActivity.EXTRA_REPORT) as? BatteryReport
            ?: BatteryReport()

        setupToolbar()
        populateReport()
        setupClickListeners()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)

        val dateStr = DATE_FORMAT.format(Date(report.endTime))
        binding.tvReportDate.text = dateStr

        val reportId = String.format("#%03d", (System.currentTimeMillis() % 1000).toInt())
        binding.tvReportId.text = reportId
    }

    private fun populateReport() {
        populateHealthScore()
        populateSessionInfo()
        populateSafetyStatus()
        populateFaults()
        populateEnergyParams()
        populateHealthParams()
        populateAiRecommendation()
    }

    private fun populateHealthScore() {
        val health = report.healthScore.toInt()
        binding.tvHealthScore.text = health.toString()
        binding.healthProgressBar.progress = health

        val label = when {
            health >= 90 -> "EXCELLENT"
            health >= 75 -> "GOOD"
            health >= 60 -> "FAIR"
            else -> "POOR"
        }
        binding.tvHealthLabel.text = label
    }

    private fun populateSessionInfo() {
        val session = report.session

        setInfoItem(binding.itemClientName, "Client", session.clientInfo.name.ifBlank { "—" })
        setInfoItem(binding.itemClientId, "Client ID", session.clientInfo.clientId.ifBlank { "—" })
        setInfoItem(binding.itemVehicle, "Vehicle", session.batteryInfo.vehicleModel.ifBlank { "—" })
        setInfoItem(binding.itemTestMode, "Test Mode", session.testMode)
        setInfoItem(binding.itemDuration, "Duration", ThemeUtils.formatTime(report.testDuration))
        setInfoItem(binding.itemDate, "Date", DATE_ONLY_FORMAT.format(Date(report.endTime)))
    }

    private fun setInfoItem(view: View, label: String, value: String) {
        view.findViewById<TextView>(R.id.tvLabel)?.text = label
        view.findViewById<TextView>(R.id.tvValue)?.text = value
    }

    private fun populateSafetyStatus() {
        val status = report.safetyStatus
        val final = report.finalReading

        when (status) {
            SafetyStatus.SAFE -> {
                binding.safetyBadge.background = getDrawable(R.drawable.bg_status_safe)
                binding.ivSafetyIcon.setImageResource(R.drawable.ic_check_circle)
                binding.tvSafetyStatus.text = getString(R.string.safe)
                binding.tvSafetyStatus.setTextColor(getColor(R.color.status_good))
                binding.tvSafetyDesc.text = "Battery is operating within safe parameters"
                binding.tvSafetyDesc.setTextColor(getColor(R.color.status_good))
            }
            SafetyStatus.WARNING -> {
                binding.safetyBadge.background = getDrawable(R.drawable.bg_status_warning)
                binding.ivSafetyIcon.setImageResource(R.drawable.ic_warning)
                binding.tvSafetyStatus.text = getString(R.string.warning)
                binding.tvSafetyStatus.setTextColor(getColor(R.color.status_warning))
                binding.tvSafetyDesc.text = "Some parameters approaching threshold limits"
                binding.tvSafetyDesc.setTextColor(getColor(R.color.status_warning))
            }
            SafetyStatus.CRITICAL -> {
                binding.safetyBadge.background = getDrawable(R.drawable.bg_status_critical)
                binding.ivSafetyIcon.setImageResource(R.drawable.ic_error)
                binding.tvSafetyStatus.text = getString(R.string.critical)
                binding.tvSafetyStatus.setTextColor(getColor(R.color.status_critical))
                binding.tvSafetyDesc.text = "Critical parameters detected — immediate action required"
                binding.tvSafetyDesc.setTextColor(getColor(R.color.status_critical))
            }
        }

        // Populate safety rows
        setSafetyRow(
            binding.safetyRowVoltage,
            "Voltage",
            String.format("%.1f V", final.voltage),
            if (final.voltage in 40.0..60.0) "NORMAL" else "WARNING"
        )
        setSafetyRow(
            binding.safetyRowTemp,
            "Temperature",
            String.format("%.1f °C", final.temperature),
            when {
                final.temperature > 45 -> "CRITICAL"
                final.temperature > 38 -> "WARNING"
                else -> "NORMAL"
            }
        )
        setSafetyRow(
            binding.safetyRowCurrent,
            "Current",
            String.format("%.1f A", final.current),
            if (final.current < 50) "NORMAL" else "WARNING"
        )
        setSafetyRow(
            binding.safetyRowSoc,
            "State of Charge",
            String.format("%.1f %%", final.soc),
            when {
                final.soc < 10 -> "CRITICAL"
                final.soc < 20 -> "WARNING"
                else -> "NORMAL"
            }
        )
    }

    private fun setSafetyRow(view: View, name: String, value: String, statusText: String) {
        val dotColor = when (statusText) {
            "CRITICAL" -> getColor(R.color.status_critical)
            "WARNING" -> getColor(R.color.status_warning)
            else -> getColor(R.color.status_good)
        }
        view.findViewById<View>(R.id.statusDot)?.setBackgroundColor(dotColor)
        view.findViewById<TextView>(R.id.tvParamName)?.text = name
        view.findViewById<TextView>(R.id.tvParamValue)?.text = value
        view.findViewById<TextView>(R.id.tvParamStatus)?.apply {
            text = statusText
            setTextColor(
                when (statusText) {
                    "CRITICAL" -> getColor(R.color.status_critical)
                    "WARNING" -> getColor(R.color.status_warning)
                    else -> getColor(R.color.status_good)
                }
            )
        }
    }

    private fun populateFaults() {
        val faults = report.faults
        binding.tvFaultCount.text = faults.size.toString()

        if (faults.isEmpty()) {
            binding.layoutNoFaults.visibility = View.VISIBLE
            binding.layoutFaultsList.visibility = View.GONE
        } else {
            binding.layoutNoFaults.visibility = View.GONE
            binding.layoutFaultsList.visibility = View.VISIBLE

            faults.forEach { fault ->
                val faultView = LayoutInflater.from(this)
                    .inflate(R.layout.layout_fault_item, binding.layoutFaultsList, false)
                populateFaultView(faultView, fault)
                binding.layoutFaultsList.addView(faultView)
            }
        }
    }

    private fun populateFaultView(view: View, fault: BatteryFault) {
        val color = when (fault.severity) {
            FaultSeverity.CRITICAL -> getColor(R.color.status_critical)
            FaultSeverity.HIGH -> getColor(R.color.status_critical)
            FaultSeverity.MEDIUM -> getColor(R.color.status_warning)
            FaultSeverity.LOW -> getColor(R.color.status_info)
        }

        val bg = when (fault.severity) {
            FaultSeverity.CRITICAL, FaultSeverity.HIGH -> getDrawable(R.drawable.bg_status_critical)
            FaultSeverity.MEDIUM -> getDrawable(R.drawable.bg_status_warning)
            FaultSeverity.LOW -> getDrawable(R.drawable.bg_status_safe)
        }

        view.background = bg
        view.findViewById<TextView>(R.id.tvFaultCode)?.apply {
            text = fault.code
            setTextColor(color)
        }
        view.findViewById<TextView>(R.id.tvFaultDesc)?.text = fault.description
        view.findViewById<TextView>(R.id.tvFaultSeverity)?.apply {
            text = fault.severity.name
            setTextColor(color)
        }
    }

    private fun populateEnergyParams() {
        val r = report.finalReading
        setParamCard(binding.paramVoltage, "Voltage", String.format("%.2f", r.voltage), "V", R.color.brand_blue)
        setParamCard(binding.paramCurrent, "Current", String.format("%.2f", r.current), "A", R.color.brand_purple)
        setParamCard(binding.paramPower, "Power", String.format("%.1f", r.power), "W", R.color.accent_teal)
        setParamCard(binding.paramCapacity, "Capacity", String.format("%.2f", r.capacity), "Ah", R.color.status_warning)
    }

    private fun populateHealthParams() {
        val r = report.finalReading
        setParamCard(binding.paramSoc, "State of Charge", String.format("%.1f", r.soc), "%", R.color.status_good)
        setParamCard(binding.paramTemp, "Temperature", String.format("%.1f", r.temperature), "°C",
            if (r.temperature > 40) R.color.status_warning else R.color.status_good)
        setParamCard(binding.paramResistance, "Int. Resistance", String.format("%.1f", r.internalResistance), "mΩ", R.color.brand_purple)
        setParamCard(binding.paramCycles, "Cycle Count", r.cycleCount.toString(), "cycles", R.color.brand_blue)
    }

    private fun setParamCard(view: View, label: String, value: String, unit: String, colorRes: Int) {
        view.findViewById<TextView>(R.id.tvParamLabel)?.text = label
        view.findViewById<TextView>(R.id.tvParamValue)?.apply {
            text = value
            setTextColor(getColor(colorRes))
        }
        view.findViewById<TextView>(R.id.tvParamUnit)?.text = unit
    }

    private fun populateAiRecommendation() {
        val health = report.healthScore.toInt()
        val rec = when {
            health >= 90 -> "Battery is in excellent condition. Continue regular maintenance schedule. " +
                    "Next full inspection recommended in 6 months or 500 charge cycles."
            health >= 75 -> "Battery health is good. Monitor charging patterns and avoid deep discharge cycles. " +
                    "Schedule maintenance inspection within 3 months."
            health >= 60 -> "Battery showing signs of aging. Reduce fast charging frequency. " +
                    "Consider replacement planning. Immediate inspection recommended."
            else -> "Battery health is degraded. Immediate replacement recommended. " +
                    "Do not use for critical applications until serviced."
        }
        binding.tvAiRecommendation.text = rec
    }

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener { finish() }

        binding.btnShare.setOnClickListener { shareReport() }
        binding.btnDownload.setOnClickListener { downloadPdf() }
        binding.btnDownloadPdf.setOnClickListener { downloadPdf() }
        binding.btnShareReport.setOnClickListener { shareReport() }

        binding.btnNewTest.setOnClickListener {
            val intent = Intent(this, HomeActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(intent)
            finish()
        }
    }

    private fun shareReport() {
        val shareText = buildString {
            append("EV DOCTOR Battery Report\n")
            append("━━━━━━━━━━━━━━━━━━━━━━\n")
            append("Client: ${report.session.clientInfo.name}\n")
            append("Vehicle: ${report.session.batteryInfo.vehicleModel}\n")
            append("Date: ${DATE_FORMAT.format(Date(report.endTime))}\n\n")
            append("HEALTH SCORE: ${report.healthScore.toInt()}%\n")
            append("SAFETY: ${report.safetyStatus.name}\n")
            append("FAULTS: ${report.faults.size}\n\n")
            append("Generated by EV DOCTOR™ — Battery Ok Technologies")
        }

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, shareText)
            putExtra(Intent.EXTRA_SUBJECT, "EV DOCTOR Battery Health Report")
        }
        startActivity(Intent.createChooser(intent, "Share Report"))
    }

    private fun downloadPdf() {
        // In production, generate actual PDF using PdfDocument API or iText
        android.widget.Toast.makeText(
            this,
            "PDF report generation coming soon",
            android.widget.Toast.LENGTH_SHORT
        ).show()
    }
}
