package com.example.androidapp.framework.adapter.input.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.example.androidapp.application.usecase.ConnectDeviceService
import com.example.androidapp.application.usecase.GetHeartRateStreamService
import com.example.androidapp.framework.adapter.output.polar.PolarBleAdapter
import com.example.androidapp.ui.theme.AndroidAppTheme

/**
 * Application entry point responsible for:
 * - requesting BLE runtime permissions (Android 12+),
 * - wiring dependency injection (manual),
 * - hosting the Compose [HrScreen].
 */
class MainActivity : ComponentActivity() {

    companion object {
        /** Default Polar H10 device ID — replace with your own. */
        private const val DEVICE_ID = "C0680226"
    }

    private val permissionsGranted = mutableStateOf(false)

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        permissionsGranted.value = grants.values.all { it }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestBlePermissions()

        // --- Manual Dependency Injection (framework layer) ---
        val polarAdapter = PolarBleAdapter(applicationContext)
        val connectService = ConnectDeviceService(polarAdapter)
        val streamService = GetHeartRateStreamService(polarAdapter)
        val viewModel = HrViewModel(connectService, streamService)

        setContent {
            AndroidAppTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    if (permissionsGranted.value) {
                        HrScreen(
                            viewModel = viewModel,
                            deviceId = DEVICE_ID,
                        )
                    } else {
                        Text(
                            text = "Bluetooth permissions are required to use this app.",
                            modifier = Modifier.padding(innerPadding)
                        )
                    }
                }
            }
        }
    }

    /**
     * Request Bluetooth runtime permissions required on Android 12+ (API 31+).
     * On older versions the permissions are granted at install time.
     */
    private fun requestBlePermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val needed = arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            ).filter {
                ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
            }.toTypedArray()

            if (needed.isEmpty()) {
                permissionsGranted.value = true
            } else {
                permissionLauncher.launch(needed)
            }
        } else {
            // Pre-Android 12 — permissions declared in manifest are enough.
            permissionsGranted.value = true
        }
    }
}

