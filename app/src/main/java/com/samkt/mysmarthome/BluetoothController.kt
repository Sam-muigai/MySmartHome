package com.samkt.mysmarthome

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.OutputStream

const val SSP_UUID = "00001101-0000-1000-8000-00805F9B34FB"
const val BT = "blue_tootb"

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

    private var dataTransferService: TransferData? = null

    private var currentSocket: BluetoothSocket? = null

    var isBtConnected = MutableStateFlow(false)
        private set

    private val stateChangedReceiver = BluetoothStateReceiver { isConnected, device ->
        isBtConnected.update {
            isConnected
        }
    }

    init {
        getPairedDevices()
        val intentFilter = IntentFilter().also {
            it.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            it.addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
        }
        context.registerReceiver(stateChangedReceiver, intentFilter)
    }

    fun connectToDevice(device: BluetoothDevice): Flow<Result> {
        return flow {
            if (!hasConnectionPermission()) {
                throw SecurityException("Permission not granted")
            }
            // not always work
            val APP_UUID = device.uuids[0].uuid
            currentSocket = bluetoothAdapter
                ?.getRemoteDevice(device.address)
                ?.createRfcommSocketToServiceRecord(APP_UUID)
            currentSocket?.let { bluetoothSocket ->
                try {
                    // Just because it is open does not mean it is not null
                    bluetoothSocket.connect()
                    val transferService = TransferData(bluetoothSocket)
                    dataTransferService = transferService
                } catch (e: Exception) {
                    bluetoothSocket.close()
                    currentSocket = null
                    e.printStackTrace()
                    emit(Result.Error("Could not connect to device!!"))
                }
                emit(Result.ConnectionSuccessful)
                Log.d(BT, "UUID : $APP_UUID")
                Log.d(BT, "Transfer services on conn: $dataTransferService")
                Log.d(BT, "Bluetooth socket on conn: ${bluetoothSocket.isConnected}")
            }
        }.flowOn(Dispatchers.IO)
    }

    suspend fun sendMessage(message: String): Boolean {
        Log.d(BT, "Transfer services: $dataTransferService")
        return dataTransferService?.sendCommands(message) ?: false
    }

    fun getPairedDevices(): List<BluetoothDevice>? {
        return bluetoothAdapter?.bondedDevices?.toList()
    }

    private fun hasConnectionPermission(): Boolean {
        return context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
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

    suspend fun sendCommands(
        message: String,
    ): Boolean {
        return withContext(Dispatchers.IO) {
            val outputStream: OutputStream = socket.outputStream
            try {
                Log.d(BT, "Bluetooth outputStream: $socket")
                outputStream.write(message.toByteArray())
                Log.d(BT, "Sent sdeviuccessfully")
            } catch (e: Exception) {
                e.printStackTrace()
                Log.d(BT, "Error : ${e.message}")
                return@withContext false
            }
            true
        }
    }
}

sealed interface Result {
    object ConnectionSuccessful : Result
    data class Error(val message: String) : Result
    data class IncomingCommands(val command: String) : Result
}

@RequiresApi(Build.VERSION_CODES.S)
private fun hasScanPermission(context: Context): Boolean {
    return ActivityCompat.checkSelfPermission(
        context,
        Manifest.permission.BLUETOOTH_SCAN,
    ) == PackageManager.PERMISSION_GRANTED
}

class BluetoothStateReceiver(
    private val onStateChanged: (isConnected: Boolean, BluetoothDevice) -> Unit,
) : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent?.getParcelableExtra(
                BluetoothDevice.EXTRA_DEVICE,
                BluetoothDevice::class.java,
            )
        } else {
            intent?.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        }
        when (intent?.action) {
            BluetoothDevice.ACTION_ACL_CONNECTED -> {
                onStateChanged(true, device ?: return)
            }

            BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                onStateChanged(false, device ?: return)
            }
        }
    }
}
