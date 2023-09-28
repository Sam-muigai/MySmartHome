package com.samkt.mysmarthome

import android.bluetooth.BluetoothDevice
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MainViewModel(
    private val bluetoothController: BluetoothController,
) : ViewModel() {
    private val _uiState = MutableStateFlow(UiState())
    val uiState = _uiState.asStateFlow()

    init {
        _uiState.update {
            it.copy(
                pairedDevices = bluetoothController.getPairedDevices() ?: emptyList(),
            )
        }
        bluetoothController.isBtConnected.onEach {isConnected ->
            _uiState.update {
                it.copy(
                    isConnected = isConnected
                )
            }
        }
    }

    fun sendCommand(message: String) {
        viewModelScope.launch {
            bluetoothController.sendMessages(message)
        }
    }

    fun connectToDevice(device: BluetoothDevice) {
        _uiState.update {
            it.copy(isConnecting = true)
        }
        viewModelScope.launch {
            bluetoothController.connectToDevice(device).collectLatest {
                when (it) {
                    Result.ConnectionSuccessful -> {
                        _uiState.update { state ->
                            state.copy(
                                isConnected = true,
                                isConnecting = false,
                                errorMessage = null,
                            )
                        }
                    }

                    is Result.Error -> {
                        _uiState.update { state ->
                            state.copy(
                                isConnected = true,
                                isConnecting = false,
                                errorMessage = it.message,
                            )
                        }
                    }

                    is Result.IncomingCommands -> {
                        _uiState.update { state ->
                            state.copy(
                                incomingCommands = state.incomingCommands + it.command,
                            )
                        }
                    }
                }
            }
        }
    }
}

data class UiState(
    val isConnecting: Boolean = false,
    val isConnected: Boolean = false,
    val message: String = "",
    val pairedDevices: List<BluetoothDevice> = emptyList(),
    val errorMessage: String? = null,
    val incomingCommands: List<String> = emptyList(),
)

fun <VM : ViewModel> viewModelFactory(initializer: () -> VM): ViewModelProvider.Factory {
    return object : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return initializer() as T
        }
    }
}
