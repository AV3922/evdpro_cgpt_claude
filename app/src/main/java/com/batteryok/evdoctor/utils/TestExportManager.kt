package com.batteryok.evdoctor.utils

import android.content.Context
import com.batteryok.evdoctor.BuildConfig
import com.batteryok.evdoctor.model.BatteryReading
import com.batteryok.evdoctor.model.TestSession
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
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

    @Synchronized
    fun createSessionWorkbook(context: Context, session: TestSession): File {
        val exportRoot = File(context.getExternalFilesDir(null), "Android")
        if (!exportRoot.exists()) {
            exportRoot.mkdirs()
        }

        val safeName = sanitizeFilePart(session.clientInfo.name.ifBlank { "Client" })
        val safePhone = sanitizeFilePart(session.clientInfo.phone.ifBlank { "NoContact" })
        val stamped = fileNameDateFormat.format(Date(session.startTime))
        val file = File(exportRoot, "${safeName}_${safePhone}_${stamped}.csv")

        BufferedWriter(FileWriter(file, false)).use { writer ->
            writer.appendLine("Section,Key,Value")
            writer.appendLine(csvLine("Session", "Client Name", session.clientInfo.name))
            writer.appendLine(csvLine("Session", "Client Contact", session.clientInfo.phone))
            writer.appendLine(csvLine("Session", "Battery Serial Number", session.batteryInfo.make))
            writer.appendLine(csvLine("Session", "Battery Chemistry", session.batteryInfo.model))
            writer.appendLine(csvLine("Session", "Battery Voltage", session.batteryInfo.nominalVoltage.toString()))
            writer.appendLine(csvLine("Session", "Battery Capacity", session.batteryInfo.nominalCapacity.toString()))
            writer.appendLine(csvLine("Session", "Test Mode", session.testMode))
            writer.appendLine(csvLine("Session", "Device MAX ID", session.deviceMaxId.ifBlank { "Unknown" }))
            writer.appendLine(csvLine("Session", "Session Started At", displayDateFormat.format(Date(session.startTime))))
            writer.appendLine()
            writer.appendLine(
                "Timestamp,ElapsedSeconds,Source,Voltage(V),Current(A),Capacity(Ah),SOC(%),Temperature(C),Power(W),InternalResistance(mOhm)"
            )
        }

        return file
    }

    @Synchronized
    fun appendReading(filePath: String, reading: BatteryReading, elapsedMs: Long, source: String) {
        if (filePath.isBlank()) return
        val file = File(filePath)
        if (!file.exists()) return

        BufferedWriter(FileWriter(file, true)).use { writer ->
            writer.appendLine(
                csvLine(
                    displayDateFormat.format(Date(reading.timestamp)),
                    String.format(Locale.US, "%.1f", elapsedMs / 1000.0),
                    source,
                    String.format(Locale.US, "%.3f", reading.voltage),
                    String.format(Locale.US, "%.3f", reading.current),
                    String.format(Locale.US, "%.3f", reading.capacity),
                    String.format(Locale.US, "%.1f", reading.soc),
                    String.format(Locale.US, "%.2f", reading.temperature),
                    String.format(Locale.US, "%.2f", reading.power),
                    String.format(Locale.US, "%.3f", reading.internalResistance)
                )
            )
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
            val useSsl = BuildConfig.SMTP_PROTOCOL.equals("SSL", ignoreCase = true)
            if (useSsl) {
                put("mail.smtp.ssl.enable", "true")
            } else {
                put("mail.smtp.starttls.enable", "true")
            }
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

        val durationMin = ((System.currentTimeMillis() - session.startTime) / 60000L).coerceAtLeast(0)
        val subject = "EV Doctor Report – Device MAX ID: ${session.deviceMaxId.ifBlank { "Unknown" }}"
        val textBody = buildString {
            appendLine("Device MAX ID: ${session.deviceMaxId.ifBlank { "Unknown" }}")
            appendLine("Battery ID: ${session.batteryInfo.model.ifBlank { "Unknown" }}")
            appendLine("Test Type: ${session.testMode}")
            appendLine("Test Duration: ${durationMin} minutes")
            appendLine("Test Date: ${displayDateFormat.format(Date())}")
            appendLine()
            append("CSV report is attached.")
        }

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

    private fun csvLine(vararg values: String): String {
        return values.joinToString(",") { value ->
            val escaped = value.replace("\"", "\"\"")
            "\"$escaped\""
        }
    }
}
