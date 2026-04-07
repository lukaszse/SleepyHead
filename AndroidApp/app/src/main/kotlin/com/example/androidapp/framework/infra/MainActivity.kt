package com.example.androidapp.framework.infra

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
import com.example.androidapp.framework.bootstrap.SleepyHeadApplication
import com.example.androidapp.framework.infra.ui.AppNavigation
import com.example.androidapp.framework.infra.ui.theme.AndroidAppTheme

/**
 * Application entry point responsible for:
 * - requesting BLE runtime permissions (Android 12+),
 * - obtaining a wired [HrViewModel][com.example.androidapp.framework.adapter.input.HrViewModel]
 *   from the [SleepyHeadApplication] bootstrap,
 * - hosting the Compose [AppNavigation][com.example.androidapp.framework.infra.ui.AppNavigation].
 *
 * In Davi Vieira's hexagonal architecture this class is **framework infrastructure** —
 * an Android-specific shell that delegates DI wiring to the bootstrap layer.
 */
class MainActivity : ComponentActivity() {

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

        // Obtain a fully wired ViewModel from the Application-level composition root
        val viewModel = (application as SleepyHeadApplication).dependencies.viewModel

        setContent {
            AndroidAppTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    if (permissionsGranted.value) {
                        AppNavigation(viewModel = viewModel)
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
     * Request Bluetooth runtime permissions.
     *
     * - Android 12+ (API 31+): BLUETOOTH_SCAN + BLUETOOTH_CONNECT
     * - Android 6–11 (API 23–30): ACCESS_FINE_LOCATION (required for BLE scanning)
     * - Android < 6: permissions granted at install time.
     */
    private fun requestBlePermissions() {
        val needed = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // Android 12+ — new BLE permissions, no location needed
                add(Manifest.permission.BLUETOOTH_SCAN)
                add(Manifest.permission.BLUETOOTH_CONNECT)
            } else {
                // Android 6–11 — BLE scan requires location permission
                add(Manifest.permission.ACCESS_FINE_LOCATION)
                add(Manifest.permission.ACCESS_COARSE_LOCATION)
            }
            // Android 13+ — notification permission required for ForegroundService
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()

        if (needed.isEmpty()) {
            permissionsGranted.value = true
        } else {
            permissionLauncher.launch(needed)
        }
    }
}

