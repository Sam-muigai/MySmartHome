package com.samkt.mysmarthome

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.samkt.mysmarthome.ui.theme.MySmartHomeTheme

class MainActivity : ComponentActivity() {

    private lateinit var bluetoothManager: BluetoothManager
    private lateinit var bluetoothAdapter: BluetoothAdapter
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        bluetoothManager = getSystemService(BluetoothManager::class.java)
        bluetoothAdapter = bluetoothManager.adapter

        val bluetoothEnabled = bluetoothAdapter.isEnabled

        val bluetoothEnableLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult(),
        ) {}
        val permissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions(),
        ) { permissions ->
            val intent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            val bluetoothPermissionGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                permissions[Manifest.permission.BLUETOOTH_CONNECT] == true && permissions[Manifest.permission.BLUETOOTH_SCAN] == true
            } else {
                true
            }
            if (bluetoothPermissionGranted && !bluetoothEnabled) {
                bluetoothEnableLauncher.launch(intent)
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_SCAN,
                ),
            )
        }
        setContent {
            MySmartHomeTheme {
                val mainViewModel = viewModel<MainViewModel>(
                    factory = viewModelFactory {
                        MainViewModel(BluetoothController(applicationContext))
                    },
                )
                MainScreen(viewModel = mainViewModel)
            }
        }
    }
}

@Composable
fun MainScreen(
    viewModel: MainViewModel,
) {
    val state = viewModel.uiState.collectAsState().value
    MainScreenContent(
        state = state,
        onDeviceClicked = viewModel::connectToDevice,
        onSendClicked = viewModel::sendCommand,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("MissingPermission")
@Composable
fun MainScreenContent(
    modifier: Modifier = Modifier,
    state: UiState,
    onSendClicked: (message: String) -> Unit,
    onDeviceClicked: (BluetoothDevice) -> Unit,
) {
    val context = LocalContext.current
    val message = rememberSaveable {
        mutableStateOf("")
    }
    LaunchedEffect(
        key1 = state.isConnected,
        block = {
            if (state.isConnected) {
                Toast.makeText(context, "Connection successful", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(context, "Disconnected", Toast.LENGTH_LONG).show()
            }
        },
    )
    if (state.isConnecting) {
        CircularProgressIndicator()
    } else {
        Column(modifier = modifier.fillMaxSize()) {
            LazyColumn(
                content = {
                    items(state.pairedDevices) { device ->
                        DeviceCard(
                            device = device,
                            onDeviceClicked = onDeviceClicked,
                        )
                        Divider()
                    }
                },
            )
            TextField(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                value = message.value,
                onValueChange = { text ->
                    message.value = text
                },
            )

            Button(
                onClick = {
                    onSendClicked(message.value)
                },
            ) {
                Text(text = "SEND")
            }
        }
    }
}

@SuppressLint("MissingPermission")
@Composable
fun DeviceCard(
    device: BluetoothDevice,
    onDeviceClicked: (BluetoothDevice) -> Unit,
) {
    Column(
        modifier = Modifier
            .clickable {
                onDeviceClicked.invoke(device)
            }
            .padding(16.dp),
    ) {
        Text(text = device.name ?: "No name")
        Text(text = device.address ?: "No name")
    }
}
