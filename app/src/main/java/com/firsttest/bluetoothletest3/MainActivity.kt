package com.firsttest.bluetoothletest3

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.ParcelUuid
import android.util.Log
import android.widget.Button
import android.widget.EditText
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import java.nio.charset.Charset
import java.util.*


private const val ENABLE_BLUETOOTH_REQUEST_CODE = 1
private const val LOCATION_PERMISSION_REQUEST_CODE = 2

class MainActivity : AppCompatActivity() {

    //UI
    private lateinit var btnScan: Button
    private lateinit var btnAdvertise: Button
    private lateinit var txtBeacon : EditText
    private lateinit var scanResultRecycler: RecyclerView

    //unique id to mark the service
    private val pUuid by lazy {
        ParcelUuid(UUID.fromString(getString(R.string.ble_uuid)))
    }

    //bluetooth adapter for advertising and scanning
    private val bluetoothAdapter: BluetoothAdapter by lazy {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter
    }

    //low energy scanning component
    private val bleScanner by lazy {
        bluetoothAdapter.bluetoothLeScanner
    }

    //low energy advertising component
    private val bleAdvertiser by lazy {
        bluetoothAdapter.bluetoothLeAdvertiser
    }

    private val isLocationPermissionGranted
        get() = hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)

    //used to check whether the app is scanning or not
    //also used to change the text on the scan button
    private var isScanning = false
    set(value) {
        field = value
        runOnUiThread {
            btnScan.text = if(value) "Stop Scan" else "Start Scan"
        }
    }

    //same as isScanning, but for the advertise button
    private var isAdvertising = false
    set(value) {
        field = value
        runOnUiThread {
            btnAdvertise.text = if(value) "Stop Advertising" else "Start Advertising"
        }
    }

    //contains all found bluetooth devices
    private val scanResults = mutableListOf<ScanResult>()
    //used to manage the display of the devices in the recycler view
    private val scanResultAdapter: ScanResultAdapter by lazy {
        ScanResultAdapter(scanResults) {}
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        btnScan = findViewById(R.id.button_discover)
        btnAdvertise = findViewById(R.id.button_advertise)
        txtBeacon = findViewById(R.id.text_beacon)
        scanResultRecycler = findViewById<RecyclerView>(R.id.scan_results_recycler)

        btnScan.setOnClickListener{
            if(!isScanning) {
                startBleScan()
            } else {
                stopBleScan()
            }
        }

        btnAdvertise.setOnClickListener{
            if(!isAdvertising) {
                startAdvertising()
            } else {
                stopAdvertising()
            }
        }

        setupRecyclerView()
    }

    private fun setupRecyclerView() {
        scanResultRecycler.apply {
            adapter = scanResultAdapter
            layoutManager = LinearLayoutManager (
                this@MainActivity,
                RecyclerView.VERTICAL,
                false
            )
            isNestedScrollingEnabled = false
        }

        val animator = scanResultRecycler.itemAnimator
        if(animator is SimpleItemAnimator) {
            animator.supportsChangeAnimations = false
        }
    }

    //#region BLE Scanning
    private fun startBleScan() {
        //check if the android version needs location permission in order to use BLE
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !isLocationPermissionGranted) {
            requestLocationPermission()
        } else {
            //reset the list of all bluetooth devices
            scanResults.clear()
            scanResultAdapter.notifyDataSetChanged()

            //filters found devices by the given criteria
            //only devices with the given uuid - devices that run this application will be discovered
            var filter = ScanFilter.Builder().setServiceData(pUuid!!, null).build()
            var filters :MutableList<ScanFilter> = mutableListOf()
            filters.add(filter)

            //starts the scanning
            //should not be running for too long - can cause heavy battery drain
            bleScanner.startScan(filters, scanSettings, scanCallback)
            isScanning = true
        }
    }

    private fun stopBleScan() {
        bleScanner.stopScan(scanCallback)
        isScanning = false
    }



    private val scanSettings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
    private val scanCallback = object: ScanCallback() {
        //called whenever a new matching ble device was found
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            var resultUuid : String? = null;
            try{

                var map = result.scanRecord?.serviceData
                var entry = map?.entries?.iterator()?.next()
                resultUuid = entry?.key.toString();
            } catch (e: Exception) {
                return;
            }

            //check if the found result has a service data uuid mask set, if not don't add it to the list
            if(resultUuid == null || pUuid.toString().lowercase() != resultUuid.lowercase()) {
                return;
            }
            Log.i("resultUuid", "${resultUuid?:"Unknown"}");

            val indexQuery = scanResults.indexOfFirst { it.device.address == result.device.address }

            //check if the list already contains an item with the found address
            //if the result is already in the list, update it
            if(indexQuery != -1) {
                scanResults[indexQuery] = result
                scanResultAdapter.notifyItemChanged(indexQuery)
            } else {
                with(result.device) {
                    Log.i("ScanCallback", "${result.scanRecord}")
                    Log.i("ScanCallback", "Found BLE Device! Name: ${name ?: "Unnamed"}, address $address")
                }
                //add the device to the list and view, if it is new
                scanResults.add(result)
                scanResultAdapter.notifyItemInserted(scanResults.size-1)
            }
        }

        //called, whenever the scan fails
        override fun onScanFailed(errorCode: Int) {
            Log.e("ScanCallback", "onScanFailed: code $errorCode")
            super.onScanFailed(errorCode)
        }
    }
    //#endregion

    //#region BLE Advertising
    private fun startAdvertising() {
        var settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(false)
            .build()


        var dataString = txtBeacon.text.trim()

        //can only contain about 23 Bytes of data
        //make sure to only use service uuid or service data
        //data can be used to broadcast information but the pUuid seems to be changed on older devices
        var advertiseData = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            //.addServiceUuid(pUuid)
            .addServiceData(pUuid, dataString.toString().toByteArray(Charset.forName("UTF-8")))
            .build()

        Log.i("pUuid", pUuid.toString())

        //starts the bluetooth advertiser
        bleAdvertiser.startAdvertising(settings, advertiseData, advertisingCallback)
        isAdvertising = true
    }

    private fun stopAdvertising() {
        bleAdvertiser.stopAdvertising(advertisingCallback)
        isAdvertising = false
    }

    private var advertisingCallback: AdvertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            Log.i("advertisingCallback", "Advertising started")
            super.onStartSuccess(settingsInEffect)
        }

        override fun onStartFailure(errorCode: Int) {
            Log.e("advertisingCallback", "AdvertisingFailure: $errorCode")
            isAdvertising = false
            super.onStartFailure(errorCode)
        }
    }

    //#endregion

    //#region location permission
    private fun requestLocationPermission() {
        if(isLocationPermissionGranted) {
            return
        }

        runOnUiThread{
            val builder = AlertDialog.Builder(this)
            builder.setTitle("Location permission required")
            builder.setMessage("Starting from Android M (6.0), the system requires apps to be granted " +
                    "location access in order to scan for BLE devices.")
            builder.setCancelable(false)
            builder.setPositiveButton("Ok") {
                    dialog, which -> {
                requestPermission(Manifest.permission.ACCESS_FINE_LOCATION,
                    LOCATION_PERMISSION_REQUEST_CODE)
            }
            }
            builder.show()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when(requestCode) {
            LOCATION_PERMISSION_REQUEST_CODE -> {
                if(grantResults.firstOrNull() == PackageManager.PERMISSION_DENIED) {
                    requestLocationPermission()
                } else {
                    startBleScan()
                }
            }
        }
    }

    private fun Activity.requestPermission(permission: String, requestCode: Int) {
        ActivityCompat.requestPermissions(this, arrayOf(permission), requestCode)
    }
    //#endregion
    override fun onResume() {
        super.onResume()
        if(!bluetoothAdapter.isEnabled) {
            promptEnableBluetooth()
        }
    }

    private fun promptEnableBluetooth() {
        if(!bluetoothAdapter.isEnabled) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(enableBtIntent, ENABLE_BLUETOOTH_REQUEST_CODE)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when(requestCode) {
            ENABLE_BLUETOOTH_REQUEST_CODE -> {
                if(resultCode != Activity.RESULT_OK) {
                    promptEnableBluetooth()
                }
            }
        }
    }

    fun Context.hasPermission(permissionType: String) : Boolean {
        return ContextCompat.checkSelfPermission(this, permissionType) == PackageManager.PERMISSION_GRANTED
    }
}