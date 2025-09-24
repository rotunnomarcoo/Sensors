/**
 * ScannerFragment - Advanced BLE device scanner fragment
 * 
 * This fragment provides a sophisticated Bluetooth Low Energy (BLE) scanning interface
 * for discovering MetaWear sensor devices. It implements modern Android BLE scanning
 * APIs with optimized performance and user experience features.
 * 
 * Key Features:
 * - Targeted MetaWear device scanning using service UUID filters
 * - Automatic scan timeout management for battery efficiency
 * - Real-time device discovery with signal strength indicators
 * - Integration with DeviceListAdapter for smooth UI updates
 * - Permission handling for location/Bluetooth access
 * - Duplicate device filtering and RSSI-based sorting
 * 
 * Technical Implementation:
 * - Uses BluetoothLeScanner for modern BLE scanning (Android 5.0+)
 * - Implements ScanCallback for asynchronous result handling
 * - Manages scan lifecycle to prevent battery drain
 * - Handles runtime permission requirements for BLE scanning
 * 
 * @author MetaWear Sensors App
 * @version 1.0
 * @since API level 21 (Android 5.0)
 */
package com.example.sensors;

import android.app.Fragment;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.ParcelUuid;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import android.Manifest;

import androidx.core.app.ActivityCompat;

import java.util.ArrayList;
import java.util.List;

/**
 * ScannerFragment extends Fragment to provide BLE scanning functionality
 * 
 * This fragment manages the complete BLE scanning lifecycle:
 * - Initializes Bluetooth adapter and scanner components
 * - Configures scan filters for MetaWear devices
 * - Handles scan start/stop operations with timeout management
 * - Processes scan results and updates the device list UI
 * - Manages scanning state and provides user feedback
 */
public class ScannerFragment extends Fragment {
    
    // UI Components for device scanning interface
    private DeviceListAdapter deviceAdapter;    // Adapter for displaying discovered devices
    private BluetoothLeScanner bleScanner;     // Modern BLE scanner (Android 5.0+)
    private BluetoothAdapter bluetoothAdapter; // Bluetooth system service adapter
    private Handler scanHandler;               // Handler for scan timeout management
    private boolean isScanning = false;        // Current scanning state flag
    
    // UI References for fragment layout
    private TextView scanStatus;               // Status text for scan progress
    private Button scanButton;                 // Start/stop scan button
    private ListView deviceList;               // List view for discovered devices
    
    // Scanning Configuration Constants
    private static final long SCAN_PERIOD = 5000; // 5 second scan timeout for battery efficiency
    
    /**
     * ScanCallback implementation for handling BLE scan results
     * 
     * This callback receives asynchronous notifications when BLE devices are discovered
     * during scanning. It processes scan results, extracts device information, and
     * updates the UI adapter with new discoveries.
     * 
     * Key responsibilities:
     * - Filter scan results for relevant devices
     * - Extract device metadata (name, address, RSSI)
     * - Update the device list adapter with new discoveries
     * - Handle scan errors and batch results
     */
    private ScanCallback scanCallback = new ScanCallback() {
        /**
         * Called when a BLE advertisement is found during scanning
         * 
         * @param callbackType Type of callback (e.g., CALLBACK_TYPE_ALL_MATCHES)
         * @param result ScanResult containing device information and advertisement data
         */
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            // Extract device information from scan result
            BluetoothDevice device = result.getDevice();
            if (device != null) {
                String deviceName = device.getName();    // May be null for unnamed devices
                String deviceAddress = device.getAddress(); // MAC address (always available)
                int rssi = result.getRssi();             // Signal strength in dBm
                
                Log.d("Scanner", "Found device: " + deviceName + " (" + deviceAddress + ") RSSI: " + rssi);
                
                // Update UI on main thread (scan callback runs on background thread)
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        // Add device to the adapter (handles duplicates and filtering)
                        deviceAdapter.addDevice(device, deviceName, deviceAddress, rssi);
                    });
                }
            }
        }
        
        /**
         * Called when the BLE scan encounters an error
         * 
         * Common error codes:
         * - SCAN_FAILED_ALREADY_STARTED (1): Scan already in progress
         * - SCAN_FAILED_APPLICATION_REGISTRATION_FAILED (2): App registration failed
         * - SCAN_FAILED_INTERNAL_ERROR (3): Internal BLE stack error
         * - SCAN_FAILED_FEATURE_UNSUPPORTED (4): BLE not supported
         * 
         * @param errorCode Integer error code indicating the type of failure
         */
        @Override
        public void onScanFailed(int errorCode) {
            Log.e("Scanner", "Scan failed with error code: " + errorCode);
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    Toast.makeText(getActivity(), "Scan failed with error: " + errorCode, Toast.LENGTH_SHORT).show();
                    stopBleScan(); // Stop scanning and reset UI state
                });
            }
        }
    };

    /**
     * Creates and initializes the fragment's view hierarchy
     * 
     * This method sets up the complete scanning interface:
     * - Inflates the custom scanner layout
     * - Initializes UI component references
     * - Configures Bluetooth adapter and scanner
     * - Sets up device list adapter
     * - Configures scan button click handlers
     * 
     * @param inflater LayoutInflater for creating views
     * @param container Parent ViewGroup for the fragment
     * @param savedInstanceState Saved state bundle (may be null)
     * @return Configured View hierarchy for the scanner interface
     */
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        Log.d("Scanner", "ScannerFragment onCreateView");
        
        // Inflate the fragment layout
        View view = inflater.inflate(R.layout.custom_scanner_fragment, container, false);
        
        // Initialize UI component references
        scanStatus = view.findViewById(R.id.scan_status);
        scanButton = view.findViewById(R.id.scan_button);
        deviceList = view.findViewById(R.id.device_list);
        
        // Initialize Bluetooth system services
        if (getActivity() != null) {
            BluetoothManager bluetoothManager = (BluetoothManager) getActivity().getSystemService(Context.BLUETOOTH_SERVICE);
            bluetoothAdapter = bluetoothManager.getAdapter();
            
            if (bluetoothAdapter != null) {
                bleScanner = bluetoothAdapter.getBluetoothLeScanner();
                Log.d("Scanner", "Bluetooth adapter and scanner initialized");
            } else {
                Log.e("Scanner", "Bluetooth adapter is null");
            }
        } else {
            Log.e("Scanner", "Activity is null, cannot initialize Bluetooth");
        }
        
        // Initialize adapter
        if (getActivity() instanceof ScannerActivity) {
            deviceAdapter = new DeviceListAdapter(getActivity(), (ScannerActivity) getActivity());
            deviceList.setAdapter(deviceAdapter);
            Log.d("Scanner", "Device adapter initialized");
        } else {
            Log.e("Scanner", "Activity is not ScannerActivity or is null");
        }
        
        scanHandler = new Handler();
        
        // Set up scan button
        scanButton.setOnClickListener(v -> {
            Log.d("Scanner", "Scan button clicked, isScanning: " + isScanning);
            if (isScanning) {
                stopBleScan();
            } else {
                startBleScan();
            }
        });
        
        return view;
    }
    
    public void startBleScan() {
        Log.d("Scanner", "Attempting to start BLE scan");
        
        // Check if fragment is properly attached to activity
        if (getActivity() == null || !isAdded()) {
            Log.w("Scanner", "Fragment not attached to activity, cannot start scan");
            return;
        }
        
        if (!checkPermissions()) {
            Log.e("Scanner", "Permissions not granted");
            Toast.makeText(getActivity(), "Bluetooth permissions required", Toast.LENGTH_SHORT).show();
            return;
        }
        
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            Log.e("Scanner", "Bluetooth not enabled");
            Toast.makeText(getActivity(), "Bluetooth is not enabled", Toast.LENGTH_SHORT).show();
            return;
        }
        
        if (bleScanner == null) {
            Log.e("Scanner", "BLE scanner is null");
            Toast.makeText(getActivity(), "BLE scanner not available", Toast.LENGTH_SHORT).show();
            return;
        }
        
        if (isScanning) {
            Log.d("Scanner", "Already scanning");
            return;
        }
        
        Log.d("Scanner", "Starting BLE scan");
        deviceAdapter.clear();
        isScanning = true;
        updateUI();
        
        // Create scan settings for better discovery
        ScanSettings scanSettings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
                .build();
        
        // Create scan filters for MetaWear devices
        List<ScanFilter> filters = new ArrayList<>();
        
        // Add MetaWear service UUID filter
        ScanFilter.Builder filterBuilder = new ScanFilter.Builder();
        filterBuilder.setServiceUuid(ParcelUuid.fromString("326a9000-85cb-9195-d9dd-464cfbbae75a"));
        filters.add(filterBuilder.build());
        
        // Add MetaBoot service UUID filter
        ScanFilter.Builder filterBuilder2 = new ScanFilter.Builder();
        filterBuilder2.setServiceUuid(ParcelUuid.fromString("00001530-1212-efde-1523-785feabcd123"));
        filters.add(filterBuilder2.build());
        
        // Start scan with filters and settings
        bleScanner.startScan(filters, scanSettings, scanCallback);
        
        // Also start a scan without filters as backup
        new Handler().postDelayed(() -> {
            if (isScanning && deviceAdapter.getCount() == 0) {
                Log.d("Scanner", "No devices found with filters, starting unfiltered scan");
                bleScanner.stopScan(scanCallback);
                bleScanner.startScan(scanCallback);
            }
        }, 3000);
        
        // Stop scan after SCAN_PERIOD
        scanHandler.postDelayed(this::stopBleScan, SCAN_PERIOD);
    }
    
    public void stopBleScan() {
        if (!isScanning) {
            return;
        }
        
        Log.d("Scanner", "Stopping BLE scan");
        isScanning = false;
        updateUI();
        
        if (bleScanner != null) {
            bleScanner.stopScan(scanCallback);
        }
        
        scanHandler.removeCallbacks(this::stopBleScan);
        
        Log.d("Scanner", "Found " + deviceAdapter.getCount() + " devices");
    }
    
    private void updateUI() {
        if (getActivity() != null) {
            getActivity().runOnUiThread(() -> {
                if (isScanning) {
                    scanStatus.setText("Scanning for devices...");
                    scanButton.setText("Stop Scan");
                } else {
                    scanStatus.setText("Scan stopped");
                    scanButton.setText("Start Scan");
                }
            });
        }
    }
    
    private boolean checkPermissions() {
        if (getActivity() == null) return false;
        
        // Check different permissions based on Android version
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            // Android 12+ permissions
            return ActivityCompat.checkSelfPermission(getActivity(), Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                   ActivityCompat.checkSelfPermission(getActivity(), Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED &&
                   ActivityCompat.checkSelfPermission(getActivity(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        } else {
            // Pre-Android 12 permissions
            return ActivityCompat.checkSelfPermission(getActivity(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
                   ActivityCompat.checkSelfPermission(getActivity(), Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        }
    }
    
    public void refreshDeviceList() {
        if (deviceAdapter != null) {
            deviceAdapter.notifyDataSetChanged();
        }
    }
    
    // Getter method to access the device adapter
    public DeviceListAdapter getDeviceAdapter() {
        return deviceAdapter;
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        stopBleScan();
    }
}
