package com.firsttest.bluetoothletest3

import android.bluetooth.le.ScanResult
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.nio.charset.Charset

class ScanResultAdapter  (
    private val items: List<ScanResult>,
    private val onClickListener: ((device: ScanResult) -> Unit)
) : RecyclerView.Adapter<ScanResultAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        var inflater : LayoutInflater = parent.context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val view = inflater.inflate(
            R.layout.row_scan_result,
            parent,
            false
        )
        return ViewHolder(view, onClickListener)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.bind(item)
    }

    class ViewHolder(
        private val view: View,
        private val onClickListener: (device: ScanResult) -> Unit
    ) : RecyclerView.ViewHolder(view) {
        fun bind(result: ScanResult) {
            var deviceName = view.findViewById<TextView>(R.id.device_name)
            var macAddress = view.findViewById<TextView>(R.id.mac_address)
            var signalStrength = view.findViewById<TextView>(R.id.signal_strength)
            var data = view.findViewById<TextView>(R.id.data)



            var dataString : String? = null
            try{

                var map = result.scanRecord?.serviceData
                var entry = map?.entries?.iterator()?.next()

                dataString =
                    entry?.let { String(it.value, Charset.forName("UTF-8")) }
                //dataString = entry?.key.toString();
            } catch (e: Exception) {

            }




            deviceName.text = result.device.name ?: "Unnamed"
            macAddress.text = result.device.address
            signalStrength.text = "${result.rssi} dBm"
            data.text = "${dataString ?: "Unknown"}";

            view.setOnClickListener{onClickListener.invoke(result)}
        }
    }
}