package com.samkt.mysmarthome

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.IOException
import java.io.OutputStream
import java.util.UUID

const val SSP_UUID = "00001101-0000-1000-8000-00805F9B34FB"
const val BT = "blue_tooth"

/*
* This class is responsible for
* - connecting to the arduino
* - sending bytes to the arduino
* - listening to bytes from the arduino
*
* In order to use this class one need to pair with the bluetooth device
* */
@SuppressLint("MissingPermission")
class BluetoothController(
    private val context: Context,
) {
    private val bluetoothManager by lazy {
        context.getSystemService(BluetoothManager::class.java)
    }
    private val bluetoothAdapter by lazy {
        bluetoothManager.adapter
    }

    private var currentSocket: BluetoothSocket? = null

    private var communicationSocket: BluetoothSocket? = null

    init {
        getPairedDevices()
    }

    fun connectToDevice(device: BluetoothDevice): Flow<Result> {
        return flow {
            if (!hasConnectionPermission()) {
                throw SecurityException("Permission not granted")
            }
            currentSocket = bluetoothAdapter
                ?.getRemoteDevice(device.address)
                ?.createRfcommSocketToServiceRecord(UUID.fromString(SSP_UUID))
            currentSocket?.let { bluetoothSocket ->
                try {
                    bluetoothSocket.connect()
                    communicationSocket = bluetoothSocket
                } catch (e: Exception) {
                    bluetoothSocket.close()
                    currentSocket = null
                    e.printStackTrace()
                    emit(Result.Error("Could not connect to device!!"))
                }
                emit(Result.IsConnectionSuccessful(bluetoothSocket.isConnected))
                Timber.d("Bluetooth socket on conn: " + bluetoothSocket.isConnected)
            }
        }.flowOn(Dispatchers.IO)
    }

    suspend fun sendMessages(
        message: String,
    ): Boolean {
        return withContext(Dispatchers.IO) {
            val outputStream: OutputStream? = communicationSocket?.outputStream
            try {
                Timber.d("Bluetooth outputStream: $outputStream")
                Timber.d("CommunicationSocket: $communicationSocket")
                outputStream?.write(message.toByteArray())
                Timber.d("Sent successfully")
            } catch (e: Exception) {
                e.printStackTrace()
                Timber.d("Error : " + e.message)
                return@withContext false
            }
            true
        }
    }

    fun getPairedDevices(): Flow<List<BluetoothDevice>?> {
        return flow {
            while (true) {
                val devices = bluetoothAdapter?.bondedDevices?.toList()
                emit(devices)
                delay(2_000)
            }
        }
    }

    private fun hasConnectionPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }
}

class TransferData(
    private val socket: BluetoothSocket,
) {
    private fun listenToIncomingCommands(): Flow<Result> {
        return flow {
            val buffer = ByteArray(1024)
            val inputStream = socket.inputStream
            while (true) {
                val byteCount = try {
                    inputStream.read(buffer)
                } catch (e: IOException) {
                    e.printStackTrace()
                    emit(Result.Error("Error occurred"))
                    return@flow
                }
                val message = buffer
                    .decodeToString(endIndex = byteCount)
                emit(Result.IncomingCommands(message))
            }
        }.flowOn(Dispatchers.IO)
    }
}

sealed interface Result {
    data class IsConnectionSuccessful(val isConnectionSuccessful: Boolean) : Result
    data class Error(val message: String) : Result
    data class IncomingCommands(val command: String) : Result
}
