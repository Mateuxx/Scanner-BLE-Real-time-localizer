package com.example.airyblzappclient

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import androidx.core.app.ActivityCompat
import com.example.airyblzappclient.databinding.ActivityMainBinding
import java.util.UUID

// Request code for permissions
private const val PERMISSION_REQUEST_CODE = 1

class MainActivity : AppCompatActivity(), OnMapReadyCallback {

    val connectionManager = ConnectionManager
    private lateinit var mMap: GoogleMap
    private lateinit var binding: ActivityMainBinding

    // Lazy initialization of BluetoothAdapter
    private val bluetoothAdapter: BluetoothAdapter by lazy {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter
    }

    // Activity result launcher for enabling Bluetooth
    private val bluetoothEnablingResult = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            // Bluetooth is enabled, good to go
        } else {
            // User dismissed or denied Bluetooth prompt
            promptEnableBluetooth()
        }
    }

    // Lazy initialization of BLE scanner
    private val bleScanner by lazy {
        bluetoothAdapter.bluetoothLeScanner
    }

    // Scan settings for BLE
    private val scanSettings = ScanSettings.Builder()
        .setScanMode(ScanSettings.SCAN_MODE_LOW_POWER)
        .build()

    // Boolean to track scanning state
    private var isScanning = false
        set(value) {
            field = value
            runOnUiThread { binding.scanButton.text = if (value) "Stop Scan" else "Start Scan" }
        }

    // List to store scan results
    private val scanResults = mutableListOf<ScanResult>()

    // BluetoothGatt instance for BLE operations
    private var bluetoothGatt: BluetoothGatt? = null

    // Byte array to hold data read from characteristics
    private var data: ByteArray? = null

    // Contains all the latitude values from GPS module
    private var latitude: MutableList<String?> = mutableListOf()

    //Contains all the longitude values from GPS module
    private var longitude: MutableList<String?> = mutableListOf()

    /**
     * ONLY FOR THE TEST ( REMOVE LATER)
     * store only one value for use in the api
     */
    private  var latitudeAPI: Double = latitude[0]?.toDouble()!!

    private  var longitudeAPI: Double = longitude[0]?.toDouble()!!


    // UUID of the target BLE service (ESP32 Service UUID)
    private val targetServiceUUID = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e")

    //Counts to receive the data in a dynamic way
    private var readCounter = 0

    private val maxReads = 10

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root) // Only call setContentView once

        // Set up scan button click listener
        binding.scanButton.setOnClickListener {
            if (isScanning) {
                stopBleScan()
                Log.d("TAG", "Lista Longitude: $longitude")
                Log.d("TAG", "Lista latitude: $latitude")
            } else {
                startBleScan()
            }
        }


        //Ver se eh pego o primeiro valor da lista de valores
        Log.d("TAG", "Latitude Value : $latitudeAPI")
        Log.d("TAG", "longitude Value : $longitudeAPI")

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)
    }

    /**
     * GOOGLE MAP API
     */
    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap


        // Add a marker in Sydney and move the camera -> Rename sydneyy stuff
        val sydney = LatLng(-latitudeAPI, longitudeAPI)
        mMap.addMarker(MarkerOptions().position(sydney).title("Marker in Sydney"))
        mMap.moveCamera(CameraUpdateFactory.newLatLng(sydney))
    }

    override fun onResume() {
        super.onResume()
        if (!bluetoothAdapter.isEnabled) {
            promptEnableBluetooth()
        }
    }

    /**
     * Method to request some android permissions
     */
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != PERMISSION_REQUEST_CODE) return
        val containsPermanentDenial = permissions.zip(grantResults.toTypedArray()).any {
            it.second == PackageManager.PERMISSION_DENIED &&
                    !ActivityCompat.shouldShowRequestPermissionRationale(this, it.first)
        }
        val containsDenial = grantResults.any { it == PackageManager.PERMISSION_DENIED }
        val allGranted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }
        when {
            containsPermanentDenial -> {
                // TODO: Handle permanent denial (e.g., show AlertDialog with justification)
                // Note: The user will need to navigate to App Settings and manually grant
                // permissions that were permanently denied
            }

            containsDenial -> {
                requestRelevantRuntimePermissions()
            }

            allGranted && hasRequiredBluetoothPermissions() -> {
                startBleScan()
            }

            else -> {
                // Unexpected scenario encountered when handling permissions
                recreate()
            }
        }
    }

    // Callback for BLE scan results
    @SuppressLint("MissingPermission")
    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val indexQuery = scanResults.indexOfFirst { it.device.address == result.device.address }
            if (indexQuery != -1) { // A scan result already exists with the same address
                scanResults[indexQuery] = result
            } else {
                with(result.device) {
                    Log.i(
                        "ScanCallback",
                        "Found BLE device! Name: ${name ?: "Unnamed"}," +
                                " address: $address,  rssi: ${result.rssi}," +
                                " services: ${result.scanRecord?.serviceUuids}"
                    )
                }
                scanResults.add(result)
                val serviceUuids = result.scanRecord?.serviceUuids?.map { it.uuid }
                if (serviceUuids?.contains(targetServiceUUID) == true) {
                    result.device.connectGatt(this@MainActivity, false, gattCallback)
                    Log.d("TAG", "DATA TO STRING:  ${data?.toString(Charsets.UTF_8)}")
                }
            }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e("ScanCallback", "onScanFailed: code $errorCode")
        }
    }

    // Callback for GATT events
    @SuppressLint("MissingPermission")
    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            Log.d("onConnectionStateChange", "")
            Log.d("onConnectionStateChange", "onConnectionStateChange: Estou nesse método!")
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.i("GattCallback", "Connected to a GATT server")
                bluetoothGatt = gatt
                gatt?.discoverServices() // Discover services offered by the remote device
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.i("GattCallback", "Disconnected from GATT server.")
                bluetoothGatt = null
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            Log.d("onServicesDiscovered", "Cheguei aqui")
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val service = gatt?.getService(targetServiceUUID)
                Log.d("onServicesDiscovered", "VALOR DO SERVICE  == $service")
                Log.d("onServicesDiscovered", "UUID TO FOUND: $service")
                if (service != null) {
                    Log.i("GattCallback", "Service found: $service")
                    for (characteristic in service.characteristics) {
                        Log.i("GattCallback", "Characteristic found: ${characteristic.uuid}")
                        // Reset the read counter
                        readCounter = 0
                        // Start the first read
                        readCharacteristic(gatt, characteristic)
                    }
                }
            }
        }

        /**
         * Le as caracteristicas até uma determinado quantidade de tempo
         */
        private fun readCharacteristic(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic
        ) {
            if (readCounter < maxReads) {
                Log.d("TAG", "Reading characteristic: ${readCounter + 1}")
                gatt?.readCharacteristic(characteristic)
            }
        }


        /**
         *  Reads the data from the BLE device
         */
        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int
        ) {
            val uuid = characteristic.uuid
            when (status) {
                BluetoothGatt.GATT_SUCCESS -> {
                    data = value //Refactor later with the method that stores the values
                    Log.d(
                        "onCharacteristicRead:",
                        " DATA VALUE: ${value.toString(Charsets.UTF_8)} "
                    )
                    // Increment the read counter and initiate the next read
                    readCounter++
                    readCharacteristic(gatt, characteristic)
                    saveDataGPS(value.toString(Charsets.UTF_8)) //saves the gps Module values
                }

                BluetoothGatt.GATT_READ_NOT_PERMITTED -> {
                    Log.e("BluetoothGattCallback", "Read not permitted for $uuid!")
                }

                else -> {
                    Log.e(
                        "BluetoothGattCallback",
                        "Characteristic read failed for $uuid, error: $status"
                    )
                }
            }
        }

    }

    /**
     * Parser dos dados recebidos do GPS
     */
    private fun saveDataGPS(dataToString: String?) {
        val parse = dataToString?.split("|")
        longitude.add(parse?.get(1))
        latitude.add(parse?.get(0))
    }

    // Prompt user to enable Bluetooth if disabled
    private fun promptEnableBluetooth() {
        if (hasRequiredBluetoothPermissions() && !bluetoothAdapter.isEnabled) {
            Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE).apply {
                bluetoothEnablingResult.launch(this)
            }
        }
    }

    // Start BLE scan
    @SuppressLint("MissingPermission")
    private fun startBleScan() {
        if (!hasRequiredBluetoothPermissions()) {
            requestRelevantRuntimePermissions()
        } else {
            scanResults.clear()
            bleScanner.startScan(null, scanSettings, scanCallback)
            isScanning = true
        }
    }

    // Stop BLE scan
    @SuppressLint("MissingPermission")
    private fun stopBleScan() {
        bleScanner.stopScan(scanCallback)
        isScanning = false
    }

    // Request relevant runtime permissions
    private fun Activity.requestRelevantRuntimePermissions() {
        if (hasRequiredBluetoothPermissions()) {
            return
        }
        when {
            Build.VERSION.SDK_INT < Build.VERSION_CODES.S -> {
                requestLocationPermission()
            }

            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                requestBluetoothPermissions()
            }
        }
    }

    // Request location permission
    private fun requestLocationPermission() = runOnUiThread {
        AlertDialog.Builder(this)
            .setTitle("Location permission required")
            .setMessage(
                "Starting from Android M (6.0), the system requires apps to be granted " +
                        "location access in order to scan for BLE devices."
            )
            .setCancelable(false)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                    PERMISSION_REQUEST_CODE
                )
            }
            .show()
    }

    // Request Bluetooth permissions for Android 12 and above
    @RequiresApi(Build.VERSION_CODES.S)
    private fun requestBluetoothPermissions() = runOnUiThread {
        AlertDialog.Builder(this)
            .setTitle("Bluetooth permission required")
            .setMessage(
                "Starting from Android 12, the system requires apps to be granted " +
                        "Bluetooth access in order to scan for and connect to BLE devices."
            )
            .setCancelable(false)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(
                        Manifest.permission.BLUETOOTH_SCAN,
                        Manifest.permission.BLUETOOTH_CONNECT
                    ),
                    PERMISSION_REQUEST_CODE
                )
            }
            .show()
    }
}
