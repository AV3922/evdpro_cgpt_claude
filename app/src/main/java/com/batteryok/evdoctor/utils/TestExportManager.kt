package com.batteryok.evdoctor.utils

import android.content.Context
import com.batteryok.evdoctor.BuildConfig
import com.batteryok.evdoctor.model.BatteryReading
import com.batteryok.evdoctor.model.TestSession
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Properties
import javax.mail.Message
import javax.mail.Session
import javax.mail.Transport
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeBodyPart
import javax.mail.internet.MimeMessage
import javax.mail.internet.MimeMultipart

object TestExportManager {

    private val fileNameDateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
    private val displayDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    private const val SHEET_META = "Session"
    private const val SHEET_DATA = "Telemetry"

    @Synchronized
    fun createSessionWorkbook(context: Context, session: TestSession): File {
        val exportRoot = File(context.getExternalFilesDir(null), "Android")
        if (!exportRoot.exists()) {
            exportRoot.mkdirs()
        }

        val safeName = sanitizeFilePart(session.clientInfo.name.ifBlank { "Client" })
        val safePhone = sanitizeFilePart(session.clientInfo.phone.ifBlank { "NoContact" })
        val stamped = fileNameDateFormat.format(Date(session.startTime))
        val file = File(exportRoot, "${safeName}_${safePhone}_${stamped}.xlsx")

        XSSFWorkbook().use { workbook ->
            val metaSheet = workbook.createSheet(SHEET_META)
            val telemetrySheet = workbook.createSheet(SHEET_DATA)

            listOf(
                "Client Name" to session.clientInfo.name,
                "Client Contact" to session.clientInfo.phone,
                "Battery Make" to session.batteryInfo.make,
                "Battery Model" to session.batteryInfo.model,
                "Nominal Voltage" to session.batteryInfo.nominalVoltage.toString(),
                "Nominal Capacity" to session.batteryInfo.nominalCapacity.toString(),
                "Test Mode" to session.testMode,
                "Session Started At" to displayDateFormat.format(Date(session.startTime))
            ).forEachIndexed { rowIndex, (key, value) ->
                val row = metaSheet.createRow(rowIndex)
                row.createCell(0).setCellValue(key)
                row.createCell(1).setCellValue(value)
            }

            val header = telemetrySheet.createRow(0)
            listOf(
                "Timestamp",
                "ElapsedSeconds",
                "Source",
                "Voltage(V)",
                "Current(A)",
                "Capacity(Ah)",
                "SOC(%)",
                "Temperature(C)",
                "Power(W)",
                "InternalResistance(mOhm)"
            ).forEachIndexed { i, title ->
                header.createCell(i).setCellValue(title)
            }

            FileOutputStream(file).use { workbook.write(it) }
        }

        return file
    }

    @Synchronized
    fun appendReading(filePath: String, reading: BatteryReading, elapsedMs: Long, source: String) {
        if (filePath.isBlank()) return
        val file = File(filePath)
        if (!file.exists()) return

        FileInputStream(file).use { input ->
            XSSFWorkbook(input).use { workbook ->
                val sheet = workbook.getSheet(SHEET_DATA) ?: workbook.createSheet(SHEET_DATA)
                val row = sheet.createRow(sheet.lastRowNum + 1)
                row.createCell(0).setCellValue(displayDateFormat.format(Date(reading.timestamp)))
                row.createCell(1).setCellValue(elapsedMs / 1000.0)
                row.createCell(2).setCellValue(source)
                row.createCell(3).setCellValue(reading.voltage)
                row.createCell(4).setCellValue(reading.current)
                row.createCell(5).setCellValue(reading.capacity)
                row.createCell(6).setCellValue(reading.soc)
                row.createCell(7).setCellValue(reading.temperature)
                row.createCell(8).setCellValue(reading.power)
                row.createCell(9).setCellValue(reading.internalResistance)

                FileOutputStream(file).use { workbook.write(it) }
            }
        }
    }

    fun hasSmtpConfig(): Boolean {
        return BuildConfig.SMTP_USER.isNotBlank() &&
            BuildConfig.SMTP_PASSWORD.isNotBlank() &&
            BuildConfig.SMTP_RECIPIENTS.isNotBlank()
    }

    fun sendWorkbookBySmtp(session: TestSession, filePath: String) {
        if (!hasSmtpConfig()) return

        val file = File(filePath)
        if (!file.exists()) return

        val props = Properties().apply {
            put("mail.smtp.auth", "true")
            put("mail.smtp.ssl.enable", "true")
            put("mail.smtp.host", BuildConfig.SMTP_HOST)
            put("mail.smtp.port", BuildConfig.SMTP_PORT.toString())
        }

        val mailSession = Session.getInstance(props, object : javax.mail.Authenticator() {
            override fun getPasswordAuthentication(): javax.mail.PasswordAuthentication {
                return javax.mail.PasswordAuthentication(BuildConfig.SMTP_USER, BuildConfig.SMTP_PASSWORD)
            }
        })

        val recipients = BuildConfig.SMTP_RECIPIENTS
            .split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() }

        if (recipients.isEmpty()) return

        val subject = "EV Doctor Test - ${session.clientInfo.name.ifBlank { "Client" }}"
        val textBody = "Test data attached for ${session.clientInfo.name.ifBlank { "Client" }}."

        val message = MimeMessage(mailSession).apply {
            setFrom(InternetAddress(BuildConfig.SMTP_USER, BuildConfig.SMTP_SENDER_NAME))
            setRecipients(
                Message.RecipientType.TO,
                recipients.map { InternetAddress(it) }.toTypedArray()
            )
            setSubject(subject)

            val textPart = MimeBodyPart().apply {
                setText(textBody)
            }
            val attachPart = MimeBodyPart().apply {
                attachFile(file)
            }

            setContent(MimeMultipart().apply {
                addBodyPart(textPart)
                addBodyPart(attachPart)
            })
        }

        Transport.send(message)
    }

    private fun sanitizeFilePart(raw: String): String {
        return raw
            .replace("\\s+".toRegex(), "")
            .replace("[^A-Za-z0-9_-]".toRegex(), "")
            .ifBlank { "Unknown" }
    }
}
