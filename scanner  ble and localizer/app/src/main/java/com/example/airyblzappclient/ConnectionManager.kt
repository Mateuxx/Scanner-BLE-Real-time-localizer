package com.example.airyblzappclient

import android.annotation.SuppressLint
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothProfile
import android.util.Log

object ConnectionManager {

     val gattCallback = object : BluetoothGattCallback() {
        /**
         * Callback indicating when GATT client has connected/disconnected to/from a remote
         * GATT server.
         *
         * @param gatt GATT client
         * @param status Status of the connect or disconnect operation. {@link
         * BluetoothGatt#GATT_SUCCESS} if the operation succeeds.
         * @param newState Returns the new connection state. Can be one of {@link
         * BluetoothProfile#STATE_DISCONNECTED} or {@link BluetoothProfile#STATE_CONNECTED}
         */
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            val deviceAddress = gatt?.device?.address
            if (status == BluetoothGatt.GATT_SUCCESS) {
                when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        Log.w(
                            "BluetoothGattCallback",
                            "Successfully connected to $deviceAddress "
                        )
                        // TODO: Store a reference to BluetoothGatt
                    }

                    BluetoothProfile.STATE_DISCONNECTED -> {
                        Log.w(
                            "BluetoothGattCallback",
                            "Successfully disconnected from $deviceAddress"
                        )
                        gatt?.close()
                    }

                    else -> {
                        Log.w(
                            "BluetoothGattCallback",
                            "Error $status encountered for $deviceAddress! Disconnecting..."
                        )
                        gatt?.close()
                    }
                }

            }
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int
        ) {
            val uuid = characteristic.uuid
            when (status) {
                BluetoothGatt.GATT_SUCCESS -> {
                    Log.i("BluetoothGattCallback", "Read characteristic $uuid:\n${value.toHexString()}")
                }
                BluetoothGatt.GATT_READ_NOT_PERMITTED -> {
                    Log.e("BluetoothGattCallback", "Read not permitted for $uuid!")
                }
                else -> {
                    Log.e("BluetoothGattCallback", "Characteristic read failed for $uuid, error: $status")
                }
            }
        }

    }

    // ... somewhere outside BluetoothGattCallback
    fun ByteArray.toHexString(): String =
        joinToString(separator = " ", prefix = "0x") { String.format("%02X", it) }
}