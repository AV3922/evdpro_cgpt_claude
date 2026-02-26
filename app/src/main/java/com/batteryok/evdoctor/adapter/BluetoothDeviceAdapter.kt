package com.batteryok.evdoctor.adapter

import android.bluetooth.BluetoothDevice
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.batteryok.evdoctor.R

class BluetoothDeviceAdapter(
    private val devices: MutableList<BluetoothDevice> = mutableListOf(),
    private val onDeviceClick: (BluetoothDevice) -> Unit
) : RecyclerView.Adapter<BluetoothDeviceAdapter.DeviceViewHolder>() {

    private var connectedDevice: BluetoothDevice? = null

    inner class DeviceViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvDeviceName: TextView = itemView.findViewById(R.id.tvDeviceName)
        val tvDeviceAddress: TextView = itemView.findViewById(R.id.tvDeviceAddress)
        val ivConnectStatus: ImageView = itemView.findViewById(R.id.ivConnectStatus)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_bluetooth_device, parent, false)
        return DeviceViewHolder(view)
    }

    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
        val device = devices[position]
        try {
            val name = device.name ?: "Unknown Device"
            holder.tvDeviceName.text = name
            holder.tvDeviceAddress.text = device.address
            holder.ivConnectStatus.visibility =
                if (device.address == connectedDevice?.address) View.VISIBLE else View.GONE
        } catch (e: SecurityException) {
            holder.tvDeviceName.text = "Device $position"
            holder.tvDeviceAddress.text = "Permission required"
        }

        holder.itemView.setOnClickListener {
            onDeviceClick(device)
        }
    }

    override fun getItemCount() = devices.size

    fun updateDevices(newDevices: List<BluetoothDevice>) {
        devices.clear()
        devices.addAll(newDevices)
        notifyDataSetChanged()
    }

    fun addDevice(device: BluetoothDevice) {
        if (devices.none { it.address == device.address }) {
            devices.add(device)
            notifyItemInserted(devices.size - 1)
        }
    }

    fun setConnectedDevice(device: BluetoothDevice?) {
        connectedDevice = device
        notifyDataSetChanged()
    }
}
