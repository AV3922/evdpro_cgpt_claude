package com.batteryok.evdoctor.ui.dashboard

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.batteryok.evdoctor.model.BatterySample
import com.batteryok.evdoctor.model.TestSession
import com.batteryok.evdoctor.utils.FirebaseCutoffRepository
import com.batteryok.evdoctor.utils.SocCalculator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

class TestViewModel : ViewModel() {

    private val cutoffRepository = FirebaseCutoffRepository()
    private var socCalculator: SocCalculator? = null
    private val workbookLock = Any()

    private var sheet2Path: String? = null
    private var initialized = false
    private var workbookSupported = true

    private val _latestSample = MutableLiveData<BatterySample>()
    val latestSample: LiveData<BatterySample> = _latestSample

    fun initialize(session: TestSession) {
        if (initialized) return
        initialized = true

        val basePath = session.exportFilePath.ifBlank {
            ""
        }

        sheet2Path = if (basePath.endsWith(".xlsx", ignoreCase = true)) {
            basePath
        } else if (basePath.endsWith(".csv", ignoreCase = true)) {
            basePath.removeSuffix(".csv") + ".xlsx"
        } else {
            "$basePath.xlsx"
        }

        viewModelScope.launch(Dispatchers.IO) {
            val chemistry = session.batteryInfo.chemistry.ifBlank { session.batteryInfo.make }
            val cutoff = cutoffRepository.getCutoff(chemistry, session.batteryInfo.nominalVoltage)
            socCalculator = SocCalculator.fromSession(session, cutoff?.lower, cutoff?.upper)
            ensureSheet2WorkbookSafely()
        }
    }

    fun processTelemetry(
        session: TestSession,
        voltage: Double,
        current: Double,
        capacity: Double,
        hour: Int,
        minute: Int,
        second: Int
    ): BatterySample {
        if (socCalculator == null) {
            val chemistry = session.batteryInfo.chemistry.ifBlank { session.batteryInfo.make }
            val cutoff = cutoffRepository.getCutoff(chemistry, session.batteryInfo.nominalVoltage)
            socCalculator = SocCalculator.fromSession(session, cutoff?.lower, cutoff?.upper)
            ensureSheet2WorkbookSafely()
        }

        val sample = socCalculator!!.calculate(voltage, current, capacity, hour, minute, second)
        _latestSample.postValue(sample)

        viewModelScope.launch(Dispatchers.IO) {
            appendSheet2RowSafely(sample)
        }

        return sample
    }


    private fun ensureSheet2WorkbookSafely() {
        if (!workbookSupported) return
        runCatching {
            ensureSheet2Workbook()
        }.onFailure {
            workbookSupported = false
            Log.e("EVDoctorSoc", "Disabling Sheet2 workbook export due to runtime error", it)
        }
    }

    private fun appendSheet2RowSafely(sample: BatterySample) {
        if (!workbookSupported) return
        runCatching {
            appendSheet2Row(sample)
        }.onFailure {
            workbookSupported = false
            Log.e("EVDoctorSoc", "Disabling Sheet2 workbook row append due to runtime error", it)
        }
    }

    private fun ensureSheet2Workbook() {
        val path = sheet2Path ?: return
        if (path.isBlank()) return

        synchronized(workbookLock) {
            val file = File(path)
            file.parentFile?.mkdirs()

            if (!file.exists()) {
                XSSFWorkbook().use { workbook ->
                    val sheet = workbook.createSheet("Sheet2")
                    val header = sheet.createRow(0)
                    listOf("soc", "voltage", "current", "capacity", "Hour", "min", "second")
                        .forEachIndexed { index, label ->
                            header.createCell(index).setCellValue(label)
                        }
                    FileOutputStream(file).use { output ->
                        workbook.write(output)
                    }
                }
                return
            }

            FileInputStream(file).use { input ->
                XSSFWorkbook(input).use { workbook ->
                    if (workbook.getSheet("Sheet2") == null) {
                        val sheet = workbook.createSheet("Sheet2")
                        val header = sheet.createRow(0)
                        listOf("soc", "voltage", "current", "capacity", "Hour", "min", "second")
                            .forEachIndexed { index, label ->
                                header.createCell(index).setCellValue(label)
                            }
                        FileOutputStream(file).use { output ->
                            workbook.write(output)
                        }
                    }
                }
            }
        }
    }

    private fun appendSheet2Row(sample: BatterySample) {
        val path = sheet2Path ?: return
        if (path.isBlank()) return

        synchronized(workbookLock) {
            val file = File(path)
            if (!file.exists()) {
                ensureSheet2WorkbookSafely()
            }

            FileInputStream(file).use { input ->
                XSSFWorkbook(input).use { workbook ->
                    val sheet = workbook.getSheet("Sheet2") ?: workbook.createSheet("Sheet2")
                    val nextRow = sheet.lastRowNum + 1
                    val row = sheet.createRow(nextRow)

                    row.createCell(0).setCellValue(sample.soc.toDouble())
                    row.createCell(1).setCellValue(sample.voltage)
                    row.createCell(2).setCellValue(sample.current)
                    row.createCell(3).setCellValue(sample.capacity)
                    row.createCell(4).setCellValue(sample.hour.toDouble())
                    row.createCell(5).setCellValue(sample.minute.toDouble())
                    row.createCell(6).setCellValue(sample.second.toDouble())

                    FileOutputStream(file).use { output ->
                        workbook.write(output)
                    }
                }
            }
        }
    }
}
