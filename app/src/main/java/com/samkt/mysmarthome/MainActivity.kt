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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.samkt.mysmarthome.ui.theme.MySmartHomeTheme

class MainActivity : ComponentActivity() {

    private lateinit var bluetoothManager: BluetoothManager
    private lateinit var bluetoothAdapter: BluetoothAdapter

    @SuppressLint("InlinedApi")
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
                if (!bluetoothEnabled) {
                    bluetoothEnableLauncher.launch(intent)
                }
                true
            }
            if (bluetoothPermissionGranted && !bluetoothEnabled) {
                bluetoothEnableLauncher.launch(intent)
            }
        }

        permissionLauncher.launch(
            arrayOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN,
            ),
        )
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
    val context = LocalContext.current
    LaunchedEffect(
        key1 = state.errorMessage,
        block = {
            state.errorMessage?.let { error ->
                Toast.makeText(context, error, Toast.LENGTH_SHORT).show()
            }
        },
    )

    when {
        state.isConnecting -> {
            Loading()
        }

        state.isConnected -> {
            SendCommandScreen(
                onSendClicked = viewModel::sendCommand,
                onValueChange = viewModel::onCommandChange,
                value = viewModel.command,
            )
        }

        else -> {
            AvailableDevices(
                state = state,
                onDeviceClicked = viewModel::connectToDevice,
            )
        }
    }
}

@SuppressLint("MissingPermission")
@Composable
fun AvailableDevices(
    modifier: Modifier = Modifier,
    state: UiState,
    onDeviceClicked: (BluetoothDevice) -> Unit,
) {
    Column(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            content = {
                item {
                    Text(
                        text = "PAIRED DEVICES",
                        modifier = Modifier.padding(start = 8.dp),
                        fontWeight = FontWeight.Bold,
                        textDecoration = TextDecoration.Underline,
                        fontSize = 18.sp,
                    )
                }
                items(state.pairedDevices) { device ->
                    DeviceCard(
                        device = device,
                        onDeviceClicked = onDeviceClicked,
                    )
                    Divider()
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SendCommandScreen(
    modifier: Modifier = Modifier,
    onSendClicked: () -> Unit,
    onValueChange: (String) -> Unit,
    value: String,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(20.dp),
    ) {
        TextField(
            modifier = Modifier.fillMaxWidth(),
            value = value,
            onValueChange = onValueChange,
        )
        Spacer(modifier = Modifier.height(15.dp))
        Button(onClick = onSendClicked) {
            Text(text = "SEND")
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
            .padding(8.dp),
    ) {
        Text(text = device.name ?: "No name")
        Text(text = "MAC : ${device.address}")
    }
}

@Composable
fun Loading(
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}
