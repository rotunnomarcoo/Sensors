package com.example.sensors;

import static com.github.mikephil.charting.charts.Chart.LOG_TAG;

import android.app.ProgressDialog;
import android.bluetooth.BluetoothDevice;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.content.DialogInterface;
import android.util.Log;
import android.widget.Toast;
import android.widget.Button;
import android.Manifest;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.github.mikephil.charting.utils.Utils;
import com.mbientlab.metawear.MetaWearBoard;
import com.mbientlab.metawear.UnsupportedModuleException;
import com.mbientlab.metawear.android.BtleService;
import com.mbientlab.metawear.data.Acceleration;
import com.mbientlab.metawear.module.Accelerometer;
import com.mbientlab.metawear.module.BarometerBosch;
import com.mbientlab.metawear.module.Gyro;
import com.mbientlab.metawear.module.Led;
import com.mbientlab.metawear.module.Logging;
import com.mbientlab.metawear.module.Settings;
import com.mbientlab.metawear.module.GyroBmi160;
import com.mbientlab.metawear.module.AmbientLightLtr329;

import java.io.IOException;
import java.util.ArrayList;
import java.util.UUID;

import bolts.Task;

// Activity for scanning and selecting Bluetooth devices
public class ScannerActivity extends AppCompatActivity implements ServiceConnection {
    private final static UUID[] serviceUuids; // UUIDs to filter BLE devices
    public static final int REQUEST_START_APP = 1; // Request code for starting app
    public static String SCAN_DEVICES = "return_devices"; // Intent key for returning devices
    private boolean isServiceBind; // Tracks if the BLE service is bound
    private BtleService.LocalBinder serviceBinder; // Binder for BLE service
    private ArrayList<String> selectedDevices = new ArrayList<>(); // List to store selected MAC addresses
    private Button confirmButton; // Reference to the confirm button
    private Button backButton; // Reference to the back button
    private static final int MAX_DEVICES = 4; // Maximum number of devices that can be selected
    private static final int PERMISSION_REQUEST_CODE = 1001; // Permission request code
    private ArrayList<String> connectedDevices = new ArrayList<>(); // List of already connected devices

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.fragment_second);

        Utils.init(this); // Initialize charting utils (if used)
        
        // Request permissions first
        requestNecessaryPermissions();
        
        Intent intent = new Intent(this, BtleService.class);
        bindService(intent, this, BIND_AUTO_CREATE); // Bind BLE service

        // Get connected devices list from intent
        connectedDevices = getIntent().getStringArrayListExtra("connected_devices");
        if (connectedDevices == null) {
            connectedDevices = new ArrayList<>();
        }

        // Initialize the confirm button
        confirmButton = findViewById(R.id.confirmButton);
        updateConfirmButton(); // Update button state initially
        
        // Initialize the back button
        backButton = findViewById(R.id.backButton);
        backButton.setOnClickListener(v -> {
            setResult(RESULT_CANCELED);
            finish();
        });
        
        // Add a "Confirm" button to finalize the selection
        confirmButton.setOnClickListener(v -> {
            if (selectedDevices.isEmpty()) {
                Toast.makeText(this, "Please select at least one device", Toast.LENGTH_SHORT).show();
                return;
            }
            
            Intent resultIntent = new Intent();
            resultIntent.putStringArrayListExtra(SCAN_DEVICES, selectedDevices);
            setResult(RESULT_OK, resultIntent);
            finish(); // Close the activity and return to MainActivity
        });
    }
    
    private void requestNecessaryPermissions() {
        ArrayList<String> permissionsNeeded = new ArrayList<>();
        
        // Check for location permission (required for BLE scanning)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) 
                != PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(Manifest.permission.ACCESS_FINE_LOCATION);
        }
        
        // Check for Bluetooth permissions based on Android version
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) 
                    != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.BLUETOOTH_SCAN);
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) 
                    != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.BLUETOOTH_CONNECT);
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) 
                    != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.ACCESS_COARSE_LOCATION);
            }
        }
        
        if (!permissionsNeeded.isEmpty()) {
            ActivityCompat.requestPermissions(this, 
                    permissionsNeeded.toArray(new String[0]), 
                    PERMISSION_REQUEST_CODE);
        }
    }
    
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean allPermissionsGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allPermissionsGranted = false;
                    break;
                }
            }
            
            if (allPermissionsGranted) {
                Toast.makeText(this, "Permissions granted. You can now scan for devices.", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Some permissions were denied. Scanning may not work properly.", Toast.LENGTH_LONG).show();
            }
        }
    }

    // Static block to initialize service UUIDs for BLE scanning
    static {
        serviceUuids = new UUID[]{
                MetaWearBoard.METAWEAR_GATT_SERVICE,
                MetaWearBoard.METABOOT_SERVICE
        };
    }

    // Set BLE connection interval for better performance
    static void setConnInterval(Settings settings) {
        if (settings != null) {
            Settings.BleConnectionParametersEditor editor = settings.editBleConnParams();
            if (editor != null) {
                editor.maxConnectionInterval(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? 11.25f : 7.5f)
                        .commit();
            }
        }
    }

    // Helper method to reconnect to a MetaWearBoard with retry logic
    public static Task<Void> reconnect(final MetaWearBoard board) {
        return board.connectAsync()
                .continueWithTask(task -> {
                    if (task.isFaulted()) {
                        return reconnect(board); // Retry on failure
                    } else if (task.isCancelled()) {
                        return task;
                    }
                    return Task.forResult(null);
                });
    }

    @Override
    protected void onStart() {
        super.onStart();
        Log.d("Scanner", "ScannerActivity onStart");
        // Bind the service in onStart() to ensure it's ready when needed
        Intent intent = new Intent(this, BtleService.class);
        isServiceBind = bindService(intent, this, BIND_AUTO_CREATE);
        
        // Start scanning automatically when activity starts
        CustomScannerFragment scannerFragment = (CustomScannerFragment) getFragmentManager().findFragmentById(R.id.scanner_fragment);
        if (scannerFragment != null) {
            // Delay the scan start to ensure fragment is fully initialized and attached
            new android.os.Handler().postDelayed(() -> {
                // Double-check that the fragment is still attached
                if (scannerFragment.isAdded() && !isFinishing()) {
                    Log.d("Scanner", "Starting automatic scan");
                    scannerFragment.startBleScan();
                } else {
                    Log.w("Scanner", "Fragment not attached or activity finishing, skipping auto-scan");
                }
            }, 2000); // Increased delay to 2 seconds for better reliability
        } else {
            Log.e("Scanner", "Scanner fragment not found");
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // Unbind service only if it was bound
        if (isServiceBind) {
            unbindService(this);
            isServiceBind = false;
        }
    }

    // Handle results from child activities (e.g., BLE scan fragment)
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch(requestCode) {
            case REQUEST_START_APP:
                // Restart BLE scan if needed
                CustomScannerFragment scannerFragment = (CustomScannerFragment) getFragmentManager().findFragmentById(R.id.scanner_fragment);
                if (scannerFragment != null) {
                    scannerFragment.startBleScan();
                }
                break;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    // Called when a device is selected from our custom scanner
    public void onDeviceSelected(final BluetoothDevice btDevice) {
        // Get the MAC address of the selected device
        String newMacAddress = serviceBinder.getMetaWearBoard(btDevice).getMacAddress();

        // Check if device is already connected
        if (connectedDevices.contains(newMacAddress)) {
            // Device is connected - show removal confirmation
            new androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("Remove Connected Device")
                    .setMessage("This device is currently connected. Do you want to remove it?")
                    .setPositiveButton("Remove", (dialog, which) -> {
                        // Remove from connected devices and send removal intent
                        Intent resultIntent = new Intent();
                        resultIntent.putExtra("action", "remove_device");
                        resultIntent.putExtra("device_address", newMacAddress);
                        setResult(RESULT_OK, resultIntent);
                        finish();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
            return;
        }

        // Check if device is already selected (toggle selection)
        if (selectedDevices.contains(newMacAddress)) {
            // Remove device from selection
            selectedDevices.remove(newMacAddress);
            Toast.makeText(this, "Removed device: " + newMacAddress, Toast.LENGTH_SHORT).show();
        } else {
            // Check if we've reached the maximum number of devices (including connected ones)
            int totalDevices = connectedDevices.size() + selectedDevices.size();
            if (totalDevices >= MAX_DEVICES) {
                Toast.makeText(this, "You can only have " + MAX_DEVICES + " devices maximum", Toast.LENGTH_LONG).show();
                return;
            }
            
            // Add the MAC address to the list
            selectedDevices.add(newMacAddress);
            Toast.makeText(this, "Added device: " + newMacAddress + " (" + (connectedDevices.size() + selectedDevices.size()) + "/" + MAX_DEVICES + ")", Toast.LENGTH_SHORT).show();
        }
        
        // Update confirm button state
        updateConfirmButton();
        
        // Refresh the scanner fragment to update checkmarks
        CustomScannerFragment scannerFragment = (CustomScannerFragment) getFragmentManager().findFragmentById(R.id.scanner_fragment);
        if (scannerFragment != null) {
            scannerFragment.refreshDeviceList();
        }
    }
    
    // Helper method to update confirm button state
    private void updateConfirmButton() {
        if (confirmButton != null) {
            int totalDevices = connectedDevices.size() + selectedDevices.size();
            confirmButton.setText("Confirm Selection (" + totalDevices + "/" + MAX_DEVICES + ")");
            confirmButton.setEnabled(!selectedDevices.isEmpty());
        }
    }
    
    // Method to check if a device is selected (for use by scanner fragment)
    public boolean isDeviceSelected(String macAddress) {
        return selectedDevices.contains(macAddress);
    }
    
    // Method to check if a device is connected (for use by scanner fragment)
    public boolean isDeviceConnected(String macAddress) {
        return connectedDevices.contains(macAddress);
    }

    // Method to refresh the device list (remove connected devices from scan results)
    public void refreshDeviceList() {
        CustomScannerFragment scannerFragment = (CustomScannerFragment) getFragmentManager().findFragmentById(R.id.scanner_fragment);
        if (scannerFragment != null && scannerFragment.getDeviceAdapter() != null) {
            scannerFragment.getDeviceAdapter().refreshDeviceList();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Refresh the device list when activity resumes to remove any newly connected devices
        refreshDeviceList();
    }

    // Called when the BLE service is connected
    @Override
    public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
        serviceBinder = (BtleService.LocalBinder) iBinder;
    }

    // Called when the BLE service is disconnected
    @Override
    public void onServiceDisconnected(ComponentName componentName) {
        serviceBinder = null;
    }
}
