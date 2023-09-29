package com.samkt.mysmarthome

import android.bluetooth.BluetoothDevice
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MainViewModel(
    private val bluetoothController: BluetoothController,
) : ViewModel() {
    private val _uiState = MutableStateFlow(UiState())
    val uiState = _uiState.asStateFlow()

    private val _uiEvents = MutableSharedFlow<UiEvents>()
    val uiEvents: SharedFlow<UiEvents>
        get() = _uiEvents

    init {
        bluetoothController.getPairedDevices().onEach { pairedDevices ->
            _uiState.update { state ->
                state.copy(
                    pairedDevices = pairedDevices ?: emptyList(),
                )
            }
        }.launchIn(viewModelScope)
    }

    var command by mutableStateOf("")
    fun onCommandChange(value: String) {
        command = value
    }

    fun sendCommand() {
        viewModelScope.launch {
            bluetoothController.sendMessages(command)
        }
    }

    fun connectToDevice(device: BluetoothDevice) {
        _uiState.update {
            it.copy(isConnecting = true)
        }
        viewModelScope.launch {
            bluetoothController.connectToDevice(device).collectLatest { result ->
                when (result) {
                    is Result.Error -> {
                        _uiEvents.emit(UiEvents.ShowSnackBar(result.message))
                        _uiState.update { state ->
                            state.copy(
                                isConnected = false,
                                isConnecting = false,
                                errorMessage = result.message,
                            )
                        }
                    }

                    is Result.IncomingCommands -> {
                        _uiState.update { state ->
                            state.copy(
                                incomingCommands = state.incomingCommands + result.command,
                            )
                        }
                    }

                    is Result.IsConnectionSuccessful -> {
                        if (result.isConnectionSuccessful) {
                            _uiState.update { state ->
                                state.copy(
                                    isConnected = true,
                                    isConnecting = false,
                                    errorMessage = null,
                                )
                            }
                        } else {
                            _uiEvents.emit(UiEvents.ShowSnackBar("Could not connect to the bluetooth device.."))
                            _uiState.update { state ->
                                state.copy(
                                    isConnected = false,
                                    isConnecting = false,
                                    errorMessage = "Could not connect to the bluetooth device..",
                                )
                            }
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

sealed class UiEvents {
    data class ShowSnackBar(val message: String) : UiEvents()
}

fun <VM : ViewModel> viewModelFactory(initializer: () -> VM): ViewModelProvider.Factory {
    return object : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return initializer() as T
        }
    }
}
