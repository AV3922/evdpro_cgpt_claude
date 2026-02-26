package com.batteryok.evdoctor.utils

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context

object BluetoothUtils {

    fun getBluetoothAdapter(context: Context): BluetoothAdapter? {
        val bluetoothManager =
            context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        return bluetoothManager?.adapter
    }

    fun isBluetoothEnabled(context: Context): Boolean {
        return getBluetoothAdapter(context)?.isEnabled == true
    }

    fun getPairedDevices(context: Context): Set<BluetoothDevice> {
        val adapter = getBluetoothAdapter(context) ?: return emptySet()
        return try {
            adapter.bondedDevices ?: emptySet()
        } catch (e: SecurityException) {
            emptySet()
        }
    }
}
