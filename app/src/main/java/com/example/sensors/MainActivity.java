/**
 * MetaWear Sensor Management Application
 * Graduation Project - Motion Tracking System using Bluetooth LE Sensors
 */
package com.example.sensors;

// Static imports from ScannerActivity for connection utilities
import static com.example.sensors.ScannerActivity.reconnect;
import static com.example.sensors.ScannerActivity.setConnInterval;

// Android framework imports for permissions, Bluetooth, and UI components
import android.Manifest;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;

// MetaWear SDK imports for sensor data handling and board management
import com.mbientlab.metawear.Data;
import com.mbientlab.metawear.MetaWearBoard;

// Android support library imports for AppCompat and navigation
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import android.os.IBinder;
import android.util.Log;
import android.view.View;

// Core Android support imports for permissions and navigation
import androidx.core.app.ActivityCompat;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

// View binding and MetaWear SDK components
import com.example.sensors.databinding.ActivityMainBinding;
import com.mbientlab.metawear.Subscriber;
import com.mbientlab.metawear.android.BtleService;
import com.mbientlab.metawear.builder.RouteBuilder;
import com.mbientlab.metawear.builder.RouteComponent;

import com.mbientlab.metawear.module.Led;
import com.mbientlab.metawear.module.Logging;
import com.mbientlab.metawear.module.Settings;

// Additional Android UI and utility imports
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

// Java standard library imports for file operations, collections, and concurrency
import java.io.FileInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

// Third-party library imports
import bolts.Task;  // For asynchronous task handling with MetaWear
import androidx.work.PeriodicWorkRequest;  // For background work scheduling
import androidx.work.WorkManager;

/**
 * Data structure to hold sensor device information
 * Consolidates all relevant device properties for easy management
 */
class DeviceInfo {
    String macAddress;        
    String name;             
    String position;         
    ledEnum color;           
    String battery;          
    boolean isConnected;     
    String connectionStatus; 
}

/**
 * Caches UI component references for each device grid slot
 * Improves performance by avoiding repeated view lookups
 */
class DeviceGridViewHolder {
    final ImageView trackerImage;
    final ImageView colorIndicator;
    final TextView nameText;
    final TextView macText;
    final TextView positionText;
    final TextView batteryText;
    final TextView connectionStatusText;
    
    DeviceGridViewHolder(View gridItem) {
        trackerImage = gridItem.findViewById(R.id.tracker_image);
        colorIndicator = gridItem.findViewById(R.id.color_indicator);
        nameText = gridItem.findViewById(R.id.tracker_name);
        macText = gridItem.findViewById(R.id.tracker_mac);
        positionText = gridItem.findViewById(R.id.tracker_position);
        batteryText = gridItem.findViewById(R.id.tracker_battery);
        connectionStatusText = gridItem.findViewById(R.id.tracker_connection_status);
    }
}

/**
 * Main Activity for Motion Tracking System
 * 
 * Manages up to 4 MetaWear BLE sensors for body motion analysis.
 * Handles device discovery, connection management, data logging,
 * and real-time monitoring of sensor status and battery levels.
 * 
 * Key Features:
 * - Multi-device Bluetooth LE management
 * - Synchronized data collection from accelerometer and gyroscope
 * - Visual feedback through LED control
 * - Persistent configuration storage
 * - Real-time battery monitoring
 */
public class MainActivity extends AppCompatActivity implements ServiceConnection {

    // Configuration constants for timing and thresholds
    private static final int CONNECTION_DELAY_MS = 200;        // Bluetooth connection spacing
    private static final int LOGGING_DELAY_MS = 500;           // Logging startup delay  
    private static final int STOP_LOGGING_DELAY_MS = 300;      // Logging shutdown delay
    private static final int BATTERY_UPDATE_INTERVAL_MS = 300000; // 5-minute battery checks
    private static final int UI_UPDATE_INTERVAL_MS = 60000;    // 1-minute UI refresh
    private static final int BATTERY_LOW_THRESHOLD = 20;       // Low battery warning at 20%
    
    // Available body positions for sensor placement
    private static final String[] POSITIONS = {
            "Polso",               // Wrist position
            "Vita",                // Waist position  
            "Caviglia Destra",     // Right ankle
            "Caviglia Sinistra"    // Left ankle
    };

    // Data storage and tracking
    private final Map<String, Boolean> batteryRouteAdded = new HashMap<>();
    private final Map<String, Boolean> isConnected = new HashMap<>();

    // Request codes and permission constants  
    public static final int SCAN_DEVICES = 1;
    private static final int REQUEST_ENABLE_BT = 2;
    private static final int GRANTED = PackageManager.PERMISSION_GRANTED;
    private static final int PERMISSION_REQUEST = 1;

    // UI and navigation components
    private AppBarConfiguration appBarConfiguration;
    private ActivityMainBinding binding;

    // Bluetooth and MetaWear components
    public static BluetoothAdapter btAdapter = null;
    public static List<MetaWearBoard> boards = Collections.synchronizedList(new ArrayList<>());
    public static BtleService.LocalBinder serviceBinder;

    // Data storage and state management
    public static ArrayList<SensorData> sensorDataList = new ArrayList<>();
    public static SaveStruct savedMacAddresses = new SaveStruct();
    public static MainActivity instance;
    
    // UI component references
    private Button loggingButton;
    private Button resetButton;
    private Button reconnectButton;  
    private Button sensorsStatusButton;
    private View[] deviceGridItems = new View[4];
    private DeviceGridViewHolder[] deviceGridViewHolders = new DeviceGridViewHolder[4];
    private Animation pulseAnimation;
    
    // Background task handlers
    private final android.os.Handler uiUpdateHandler = new android.os.Handler();
    private final android.os.Handler batteryHandler = new android.os.Handler();
    private Runnable uiUpdater;
    private Runnable batteryUpdater;
    
    // Operation state tracking
    private boolean start = true;
    private boolean isLoggingOperationInProgress = false;
    private boolean isLEDOperationInProgress = false;
    private boolean isResetOperationInProgress = false;

    /**
     * Utility methods for common UI operations and device management
     */

    /**
     * Changes button text temporarily during operations
     */
    private void setTemporaryButtonText(Button button, String tempText, Runnable task) {
        String originalText = button.getText().toString();
        button.setText(tempText);
        button.setEnabled(false);
        
        new Thread(() -> {
            try {
                task.run();
            } finally {
                runOnUiThread(() -> {
                    button.setText(originalText);
                    button.setEnabled(true);
                });
            }
        }).start();
    }

    /**
     * Logs device actions with consistent format
     */
    private void logDeviceAction(String action, String macAddress, String status) {
        Log.d("DeviceAction", action + " - Device: " + macAddress + " - Status: " + status);
    }

    /**
     * Creates and shows a status dialog with consistent styling
     */
    private void showStatusDialog(String title, String message) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("OK", null)
                .show();
    }

    /**
     * Centralized handler management for periodic updates
     * Starts both UI and battery update handlers
     */
    private void startPeriodicUpdates() {
        if (uiUpdater != null) {
            uiUpdateHandler.post(uiUpdater);
        }
        if (batteryUpdater != null) {
            batteryHandler.post(batteryUpdater);
        }
    }

    /**
     * Centralized handler management for stopping updates
     * Stops both UI and battery update handlers
     */
    private void stopPeriodicUpdates() {
        uiUpdateHandler.removeCallbacks(uiUpdater);
        batteryHandler.removeCallbacks(batteryUpdater);
    }

    /**
     * Initializes the main activity and sets up the sensor management interface
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        instance = this;

        // Initialize visual feedback animation
        try {
            pulseAnimation = AnimationUtils.loadAnimation(this, R.anim.pulse);
            pulseAnimation.setRepeatCount(Animation.INFINITE);
            pulseAnimation.setRepeatMode(Animation.REVERSE);
        } catch (Exception e) {
            pulseAnimation = AnimationUtils.loadAnimation(this, android.R.anim.fade_in);
            pulseAnimation.setRepeatCount(Animation.INFINITE);
            pulseAnimation.setRepeatMode(Animation.REVERSE);
        }

        // Connect to MetaWear Bluetooth service
        getApplicationContext().bindService(new Intent(this, BtleService.class),
                this, Context.BIND_AUTO_CREATE);

        // Check for first run and load saved configuration
        SharedPreferences prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE);
        boolean isFirstRun = prefs.getBoolean("first_run", true);

        if (isFirstRun) {
            savedMacAddresses = new SaveStruct();
            prefs.edit().putBoolean("first_run", false).apply();
            Log.d("MainActivity", "First run - clean state");
        } else {
            savedMacAddresses = readState();
            Log.d("MainActivity", "Loading saved configuration");
        }

        if (savedMacAddresses.isEmpty())
            Toast.makeText(this, "No devices configured", Toast.LENGTH_SHORT).show();

        // Set up user interface
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        setSupportActionBar(binding.toolbar);

        // Configure navigation
        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_content_main);
        appBarConfiguration = new AppBarConfiguration.Builder(navController.getGraph()).build();
        NavigationUI.setupActionBarWithNavController(this, navController, appBarConfiguration);

        // Initialize components
        setupButtons();
        setupDeviceGrid();
        setupDeviceListButton();
        setupHandlers();

        // Restore UI state based on saved configuration
        if (savedMacAddresses.isLogging()) {
            loggingButton.setText("Stop");
            if (FirstFragment.scanner != null) {
                FirstFragment.scanner.setEnabled(false);
            }
        } else {
            loggingButton.setText("Start");
            if (FirstFragment.scanner != null) {
                FirstFragment.scanner.setEnabled(true);
            }
        }

        updateDeviceGrid();
        checkPermission();
        startPeriodicUpdates();
    }

    /**
     * Configures button click listeners and initial states
     */
    private void setupButtons() {
        // Scanner button for device discovery
        FirstFragment.scanner = findViewById(R.id.scannerButton);
        if (FirstFragment.scanner != null) {
            FirstFragment.scanner.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (savedMacAddresses.getMacAddresses().size() >= 4) {
                        Toast.makeText(MainActivity.this, "Maximum 4 devices supported", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    
                    if (savedMacAddresses.isLogging()) {
                        Toast.makeText(MainActivity.this, "Stop logging before adding devices", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    
                    if (isLoggingOperationInProgress || isLEDOperationInProgress || isResetOperationInProgress) {
                        Toast.makeText(MainActivity.this, "Operation in progress", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    
                    Intent intent = new Intent(MainActivity.this, ScannerActivity.class);
                    startActivityForResult(intent, SCAN_DEVICES);
                }
            });
        }

        // Logging control button
        loggingButton = findViewById(R.id.loggingButton);
        loggingButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Verify that exactly 4 devices are connected before starting logging
                // Verify that exactly 4 devices are connected before starting logging
                int connectedDevices = getConnectedDeviceCount();
                if (connectedDevices != 4) {
                    Toast.makeText(MainActivity.this, "Connect exactly 4 devices to start (" + connectedDevices + "/4)", Toast.LENGTH_SHORT)
                            .show();
                } else if (allDevicesHavePositions()) {
                    // Toggle logging state
                    if (savedMacAddresses.isLogging()) {
                        stopLogging();
                    } else {
                        startLogging();
                    }
                } else {
                    // Position assignment required before logging
                    Toast.makeText(MainActivity.this, "Please assign positions to sensors first", Toast.LENGTH_SHORT)
                            .show();
                }
            }
        });

        // Reset button setup
        resetButton = findViewById(R.id.resetButton);
        
        // Disable reset during active logging to prevent data corruption
        if (savedMacAddresses.isLogging()) {
            resetButton.setEnabled(false);
        } else {
            resetButton.setEnabled(true);
        }

        resetButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                performReset();
            }
        });

        // Reconnect button setup
        reconnectButton = findViewById(R.id.reconnectButton);
        reconnectButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                performReconnectToDevices(); // Attempt to reconnect all configured devices
            }
        });

        // Device status button setup
        sensorsStatusButton = findViewById(R.id.sensorsStatusButton);
        sensorsStatusButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Visual feedback during status retrieval
                Button button = (Button) v;
                String originalText = button.getText().toString();
                button.setText("Retrieving device information...");
                button.setEnabled(false);
                
                // Update device information before displaying status
                updateAllDeviceInformationBeforeStatus(button, originalText);
            }
        });
    }

    /**
     * Sets up the device information button
     */
    private void setupDeviceListButton() {
        ImageView deviceListButton = findViewById(R.id.device_list_info_button);
        if (deviceListButton != null) {
            deviceListButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showConnectedDevicesList();
                }
            });
        }
    }

    /**
     * Displays a dialog with information about all configured devices
     */
    private void showConnectedDevicesList() {
        if (savedMacAddresses.isEmpty()) {
            Toast.makeText(this, "No devices configured", Toast.LENGTH_SHORT).show();
            return;
        }

        StringBuilder deviceInfo = new StringBuilder();
        ArrayList<String> macAddresses = savedMacAddresses.getMacAddresses();

        for (int i = 0; i < macAddresses.size(); i++) {
            String macAddress = macAddresses.get(i);
            String name = savedMacAddresses.getName(macAddress);
            String position = savedMacAddresses.getPosition(macAddress);
            ledEnum color = savedMacAddresses.getColor(macAddress);

            deviceInfo.append("Device ").append(i + 1).append(":\n");
            deviceInfo.append("Name: ").append(name != null && !name.isEmpty() ? name : "Unknown").append("\n");
            deviceInfo.append("MAC: ").append(macAddress).append("\n");
            deviceInfo.append("Position: ").append(position != null && !position.isEmpty() ? position : "Not assigned").append("\n");
            deviceInfo.append("Color: ").append(color.toString()).append("\n");
            
            if (i < macAddresses.size() - 1) {
                deviceInfo.append("\n");
            }
        }

        // Display the device information in a dialog
        showStatusDialog("Connected Devices (" + macAddresses.size() + ")", deviceInfo.toString());
    }

    /**
     * allDevicesHavePositions - Verify that all devices have assigned positions
     * 
     * Checks whether all connected devices have been assigned body positions.
     * This is required before logging can begin to ensure proper data attribution.
     * 
     * @return true if all devices have positions assigned, false otherwise
     */
    private boolean allDevicesHavePositions() {
        ArrayList<String> macAddresses = savedMacAddresses.getMacAddresses();
        
        // Check each device for position assignment
        for (String mac : macAddresses) {
            String position = savedMacAddresses.getPosition(mac);
            if (position == null || position.isEmpty()) {
                return false; // Found a device without position
            }
        }
        return !macAddresses.isEmpty(); // Return true only if we have devices AND all have positions
    }

    /**
     * Initializes the 4-device display grid with ViewHolders and click listeners
     */
    private void setupDeviceGrid() {
        deviceGridItems[0] = findViewById(R.id.device_slot_0);
        deviceGridItems[1] = findViewById(R.id.device_slot_1);
        deviceGridItems[2] = findViewById(R.id.device_slot_2);
        deviceGridItems[3] = findViewById(R.id.device_slot_3);

        // Cache view references for better performance
        for (int i = 0; i < deviceGridItems.length; i++) {
            deviceGridViewHolders[i] = new DeviceGridViewHolder(deviceGridItems[i]);
        }

        // Set up click listeners for position assignment
        for (int i = 0; i < deviceGridItems.length; i++) {
            final int slotIndex = i;
            deviceGridItems[i].setOnClickListener(v -> showPositionAssignmentDialog(slotIndex));
        }
    }

    /**
     * Sets up background handlers for UI updates and battery monitoring
     */
    private void setupHandlers() {
        // UI refresh handler
        uiUpdater = new Runnable() {
            @Override
            public void run() {
                // Update sensor data timestamps
                for (int i = 0; i < sensorDataList.size(); i++) {
                    SensorData oldData = sensorDataList.get(i);
                    SensorData updated = new SensorData(
                            oldData.getMacAddress(),
                            oldData.getPosition(),
                            oldData.getBatteria(),
                            savedMacAddresses.getRecordTimeString());
                    sensorDataList.set(i, updated);
                }

                updateDeviceGrid();
                uiUpdateHandler.postDelayed(this, UI_UPDATE_INTERVAL_MS);
            }
        };

        // Battery monitoring handler
        batteryUpdater = new Runnable() {
            @Override
            public void run() {
                // Update batteries only during logging to reduce BLE traffic
                if (!savedMacAddresses.isLogging()) {
                    return;
                }

                // Update battery level for each connected board
                for (MetaWearBoard board : boards) {
                    updateBoardBattery(board).continueWith(task -> {
                        if (task.isFaulted()) {
                            Log.e("BatteryUpdater", "Battery update failed for: " + board.getMacAddress());
                        }
                        return null;
                    });
                }
                
                // Schedule next battery update
                batteryHandler.postDelayed(this, BATTERY_UPDATE_INTERVAL_MS);
            }
        };
    }

    /**
     * Updates battery level for the specified board
     */
    public Task<Void> updateBoardBattery(MetaWearBoard board) {
        return board.connectWithRetryAsync(2).continueWithTask(task -> {
            if (task.isFaulted() || task.isCancelled()) {
                Log.e("BatteryUpdate", "Connection failed for: " + board.getMacAddress());
                return Task.forResult(null);
            }

            // Get the Settings module to access battery information
            Settings settings = board.getModule(Settings.class);
            if (settings != null) {
                return setupBatteryRoute(board, savedMacAddresses.getName(board.getMacAddress()))
                        .continueWith(routeTask -> {
                            // Trigger battery reading after route setup
                            settings.battery().read();
                            return null;
                        });
            }
            return Task.forResult(null);
        }).continueWithTask(task -> Task.delay(CONNECTION_DELAY_MS))
                .continueWithTask(task -> board.disconnectAsync())
                .continueWith(task -> {
                    if (task.isFaulted()) {
                        Log.e("BatteryUpdate", "Error in battery update for: " + board.getMacAddress(),
                                task.getError());
                    }
                    return null;
                });
    }

    private Task<Void> setupBatteryRoute(MetaWearBoard board, String alias) {
        String mac = board.getMacAddress();

        if (batteryRouteAdded.getOrDefault(mac, false)) {
            return Task.forResult(null);
        }

        Settings settings = board.getModule(Settings.class);
        if (settings == null) {
            return Task.forResult(null);
        }

        return settings.battery().addRouteAsync(new RouteBuilder() {
            @Override
            public void configure(RouteComponent source) {
                source.stream(new Subscriber() {
                    @Override
                    public void apply(Data data, Object... env) {
                        Settings.BatteryState batteryState = data.value(Settings.BatteryState.class);
                        String battery = batteryState.charge + "%";
                        int batteryValue = batteryState.charge;

                        updateSensorDataBattery(board.getMacAddress(), battery, batteryValue, board);
                        runOnUiThread(() -> {
                            // Update grid instead of RecyclerView
                            updateDeviceGrid();
                        });
                    }
                });
            }
        }).continueWith(task -> {
            if (task.isCompleted() && !task.isFaulted()) {
                batteryRouteAdded.put(mac, true);
            }
            return null;
        });
    }

    private void updateSensorDataBattery(String macAddress, String battery, int batteryValue, MetaWearBoard board) {
        // Update SensorData list
        for (int i = 0; i < sensorDataList.size(); i++) {
            SensorData oldData = sensorDataList.get(i);
            if (oldData.getMacAddress().equals(macAddress)) {
                SensorData updated = new SensorData(
                        oldData.getMacAddress(),
                        oldData.getPosition(),
                        battery,
                        oldData.getStato());
                sensorDataList.set(i, updated);
                break;
            }
        }

        // Also update SaveStruct
        savedMacAddresses.setBattery(macAddress, battery);

        // Handle low battery LED indication
        if (savedMacAddresses.isLogging() && batteryValue < BATTERY_LOW_THRESHOLD) {
            showLowBatteryIndication(board);
        }
    }

    private void showLowBatteryIndication(MetaWearBoard board) {
        Led led = board.getModule(Led.class);
        ledEnum color = savedMacAddresses.getColor(board.getMacAddress());

        if (led != null && color != null) {
            led.stop(true);
            switch (color) {
                case BLUE:
                    led.editPattern(Led.Color.BLUE, Led.PatternPreset.PULSE).repeatCount((byte) 15).commit();
                    break;
                case RED:
                    led.editPattern(Led.Color.RED, Led.PatternPreset.PULSE).repeatCount((byte) 15).commit();
                    break;
                case GREEN:
                    led.editPattern(Led.Color.GREEN, Led.PatternPreset.PULSE).repeatCount((byte) 15).commit();
                    break;
                case YELLOW:
                    led.editPattern(Led.Color.RED, Led.PatternPreset.PULSE).repeatCount((byte) 15).commit();
                    led.editPattern(Led.Color.GREEN, Led.PatternPreset.PULSE).repeatCount((byte) 15).commit();
                    break;
            }
            led.play();
        }
    }

    /**
     * performReset - Execute comprehensive device reset operation
     * 
     * Performs a complete reset of all connected devices including:
     * - LED state reset (turn off all LEDs)
     * - Resource cleanup and teardown
     * - UI state restoration
     * Includes comprehensive state protection and operation coordination.
     */
    private void performReset() {
        // Operation state protection
        if (isLoggingOperationInProgress || isLEDOperationInProgress || isResetOperationInProgress) {
            Toast.makeText(this, "Operation in progress, please wait...", Toast.LENGTH_SHORT).show();
            return;
        }

        // Prevent reset during logging to avoid data corruption
        if (savedMacAddresses.isLogging()) {
            Toast.makeText(this, "Stop logging before resetting", Toast.LENGTH_SHORT).show();
            return;
        }

        // Operation initialization
        isResetOperationInProgress = true;
        isLEDOperationInProgress = true;
        resetButton.setEnabled(false);
        resetButton.setText("Resetting...");
        loggingButton.setEnabled(false);

        savedMacAddresses = new SaveStruct();
        sensorDataList.clear();

        // LED shutdown with proper synchronization
        if (!boards.isEmpty()) {
            AtomicInteger completedOperations = new AtomicInteger(0);
            int totalBoards = boards.size();
            
            for (MetaWearBoard board : boards) {
                board.connectWithRetryAsync(3).continueWithTask(task -> {
                    if (task.isFaulted() || task.isCancelled()) {
                        logDeviceAction("Reset", board.getMacAddress(), "Connection failed");
                        return Task.forResult(null);
                    }
                    
                    // Tear down board resources before LED cleanup
                    logDeviceAction("Reset", board.getMacAddress(), "Tearing down resources");
                    //board.tearDown();
                    
                    Led led = board.getModule(Led.class);
                    if (led != null) {
                        led.stop(true);
                        logDeviceAction("Reset", board.getMacAddress(), "LED stopped");
                    }
                    return Task.delay(1500); // Increased delay to ensure LED turns off
                }).continueWithTask(task -> {
                    return board.disconnectAsync();
                }).continueWith(task -> {
                    if (task.isFaulted()) {
                        logDeviceAction("Reset", board.getMacAddress(), "Error during LED shutdown: " + task.getError().getMessage());
                    } else {
                        logDeviceAction("Reset", board.getMacAddress(), "LED reset successful");
                    }
                    
                    int completed = completedOperations.incrementAndGet();
                    if (completed >= totalBoards) {
                        // All LED operations completed, now safe to clear boards
                        runOnUiThread(() -> {
                            boards.clear();
                            batteryRouteAdded.clear();
                            isConnected.clear(); // Clear connection tracking state
                            saveState();
                            updateDeviceGrid();
                            updateButtonState(); // Update button state after reset
                            Log.d("Reset", "All devices reset successfully");
                        });
                    }
                    return null;
                });
            }
        } else {
            // No boards to reset, proceed immediately
            boards.clear();
            batteryRouteAdded.clear();
            isConnected.clear(); // Clear connection tracking state
            saveState();
            updateDeviceGrid();
            updateButtonState(); // Update button state after reset
        }

        WorkManager.getInstance(getApplicationContext()).cancelAllWork();
        batteryHandler.removeCallbacks(batteryUpdater);

        // Re-enable buttons after reset is complete (increased timeout for LED operations)
        new android.os.Handler().postDelayed(() -> {
            runOnUiThread(() -> {
                isResetOperationInProgress = false;
                isLEDOperationInProgress = false;
                resetButton.setEnabled(true);
                resetButton.setText("Reset");
                loggingButton.setEnabled(true);
                Log.d("Reset", "Reset operation completed");
            });
        }, 4000); // Increased from 2000 to 4000ms
    }

    private void performReconnectToDevices() {
        // Prevent reconnect during other operations
        if (isLoggingOperationInProgress || isLEDOperationInProgress || isResetOperationInProgress) {
            Toast.makeText(this, "Operation in progress, please wait...", Toast.LENGTH_SHORT).show();
            return;
        }

        ArrayList<String> macAddresses = savedMacAddresses.getMacAddresses();
        if (macAddresses.isEmpty()) {
            Toast.makeText(this, "No devices to reconnect to", Toast.LENGTH_SHORT).show();
            return;
        }

        reconnectButton.setEnabled(false);
        reconnectButton.setText("Reconnecting...");

        int connectedCount = getConnectedDeviceCount();
        boolean isRefresh = (connectedCount == macAddresses.size());
        
        String toastMessage = isRefresh ? 
            "Refreshing connections for " + macAddresses.size() + " devices" :
            "Attempting to reconnect to " + macAddresses.size() + " devices";
        Toast.makeText(this, toastMessage, Toast.LENGTH_SHORT).show();

        final int[] completedReconnects = {0};
        int totalDevices = macAddresses.size();

        for (String macAddress : macAddresses) {
            // Find the board for this MAC address
            MetaWearBoard board = null;
            for (MetaWearBoard b : boards) {
                if (b.getMacAddress().equals(macAddress)) {
                    board = b;
                    break;
                }
            }

            if (board != null) {
                final MetaWearBoard finalBoard = board;
                final String address = macAddress;
                
                Log.d("Reconnect", "Force disconnecting and reconnecting to: " + address);
                
                // Always disconnect first (even if already connected) to refresh the connection
                finalBoard.disconnectAsync().continueWithTask(task -> {
                    // Mark as disconnected during the process
                    isConnected.put(address, false);
                    runOnUiThread(() -> updateDeviceGrid()); // Update UI to show disconnected state
                    
                    // Wait a moment before reconnecting
                    return Task.delay(1500);
                }).continueWithTask(task -> {
                    Log.d("Reconnect", "Attempting to reconnect to: " + address);
                    return finalBoard.connectAsync();
                }).continueWithTask(task -> {
                    if (task.isCancelled()) {
                        isConnected.put(address, false);
                        Log.e("Reconnect", "Connection cancelled for: " + address);
                        return task;
                    } else if (task.isFaulted()) {
                        isConnected.put(address, false);
                        Log.e("Reconnect", "Connection failed for: " + address, task.getError());
                        return reconnect(finalBoard);
                    } else {
                        Log.d("Reconnect", "Reconnected successfully to: " + address);
                        isConnected.put(address, true);
                        
                        // Update battery and LED after successful reconnection
                        updateBoardBattery(finalBoard);
                        monitorConnection(finalBoard, address);
                        
                        // Update LED if device has position and not logging
                        if (!savedMacAddresses.isLogging()) {
                            updateDeviceLED(address);
                        }
                        
                        runOnUiThread(() -> updateDeviceGrid());
                        return task;
                    }
                }).continueWith(task -> {
                    // Update UI after each device attempt
                    runOnUiThread(() -> {
                        completedReconnects[0]++;
                        
                        if (completedReconnects[0] >= totalDevices) {
                            // All reconnection attempts completed
                            reconnectButton.setEnabled(true);
                            updateButtonState();
                            
                            int finalConnectedCount = getConnectedDeviceCount();
                            String message = isRefresh ? 
                                "Connection refresh complete. " + finalConnectedCount + "/" + totalDevices + " devices connected" :
                                "Reconnection complete. " + finalConnectedCount + "/" + totalDevices + " devices connected";
                            Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show();
                        }
                    });
                    return null;
                });
            } else {
                // Board not found, increment counter
                runOnUiThread(() -> {
                    completedReconnects[0]++;
                    if (completedReconnects[0] >= totalDevices) {
                        reconnectButton.setEnabled(true);
                        updateButtonState();
                        
                        int finalConnectedCount = getConnectedDeviceCount();
                        String message = "Reconnection complete. " + finalConnectedCount + "/" + totalDevices + " devices connected";
                        Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show();
                    }
                });
            }
        }

        // Re-enable button after timeout as safety measure (increased from 15 to 20 seconds)
        new android.os.Handler().postDelayed(() -> {
            runOnUiThread(() -> {
                reconnectButton.setEnabled(true);
                updateButtonState();
            });
        }, 20000); // 20 second timeout
    }

    /**
     * Updates the visual grid showing device status, positions, and battery levels
     */
    private void updateDeviceGrid() {
        runOnUiThread(() -> {
            // Get current list of configured devices
            ArrayList<String> macAddresses = savedMacAddresses.getMacAddresses();

            // Update each of the 4 device slots in the grid
            for (int i = 0; i < 4; i++) {
                View gridItem = deviceGridItems[i];
                DeviceGridViewHolder holder = deviceGridViewHolders[i];
                if (gridItem == null || holder == null)
                    continue;

                // Check if this slot has an assigned device
                if (i < macAddresses.size()) {
                    String macAddress = macAddresses.get(i);
                    ledEnum color = savedMacAddresses.getColor(macAddress);
                    String position = savedMacAddresses.getPosition(macAddress);
                    String name = savedMacAddresses.getName(macAddress);

                    // Show connected tracker
                    holder.trackerImage.setImageResource(R.drawable.tracker_connected);

                    // Set color indicator
                    setColorIndicator(holder.colorIndicator, color);

                    // Set texts
                    holder.nameText.setText(name != null && !name.isEmpty() ? name : "Device " + (i + 1));
                    holder.nameText.setTextColor(getResources().getColor(android.R.color.black));
                    
                    holder.macText.setText(macAddress);
                    holder.macText.setTextColor(getResources().getColor(android.R.color.black));
                    
                    // Set position text with color coding
                    if (position != null && !position.isEmpty()) {
                        holder.positionText.setText(position);
                        holder.positionText.setTextColor(getResources().getColor(android.R.color.black));
                    } else {
                        holder.positionText.setText("Tap to assign");
                        holder.positionText.setTextColor(getResources().getColor(android.R.color.holo_red_dark));
                    }

                    // Get battery from SaveStruct or SensorData
                    String battery = savedMacAddresses.getBattery(macAddress);
                    if (battery.equals("Unknown")) {
                        for (SensorData data : sensorDataList) {
                            if (data.getMacAddress().equals(macAddress)) {
                                battery = data.getBatteria();
                                break;
                            }
                        }
                    }
                    
                    // Set battery text with color coding
                    holder.batteryText.setText("Battery: " + battery);
                    if (!battery.equals("Unknown") && battery.contains("%")) {
                        try {
                            int batteryLevel = Integer.parseInt(battery.replace("%", ""));
                            if (batteryLevel >= 66) {
                                holder.batteryText.setTextColor(getResources().getColor(android.R.color.holo_green_dark));
                            } else if (batteryLevel >= 33) {
                                holder.batteryText.setTextColor(getResources().getColor(android.R.color.holo_orange_dark));
                            } else {
                                holder.batteryText.setTextColor(getResources().getColor(android.R.color.holo_red_dark));
                            }
                        } catch (NumberFormatException e) {
                            // If parsing fails, use default color
                            holder.batteryText.setTextColor(getResources().getColor(android.R.color.white));
                        }
                    } else {
                        holder.batteryText.setTextColor(getResources().getColor(android.R.color.white)); // Default color for "Unknown"
                    }

                    // Set connection status
                    Boolean connected = isConnected.get(macAddress);
                    if (connected != null && connected) {
                        holder.connectionStatusText.setText("CONNECTED");
                        holder.connectionStatusText.setTextColor(getResources().getColor(android.R.color.holo_green_dark));
                    } else {
                        holder.connectionStatusText.setText("DISCONNECTED");
                        holder.connectionStatusText.setTextColor(getResources().getColor(android.R.color.holo_red_dark));
                    }

                    // Start pulsing animation if position not assigned
                    if (position == null || position.isEmpty()) {
                        // Check if animation is already running
                        if (holder.trackerImage.getAnimation() == null || holder.trackerImage.getAnimation().hasEnded()) {
                            // Create a programmatic alpha animation that definitely repeats
                            android.view.animation.AlphaAnimation alphaAnimation = new android.view.animation.AlphaAnimation(1.0f, 0.3f);
                            alphaAnimation.setDuration(800);
                            alphaAnimation.setRepeatCount(android.view.animation.Animation.INFINITE);
                            alphaAnimation.setRepeatMode(android.view.animation.Animation.REVERSE);
                            holder.trackerImage.startAnimation(alphaAnimation);
                        }
                    } else {
                        holder.trackerImage.clearAnimation();
                    }

                    gridItem.setVisibility(View.VISIBLE);
                } else {
                    // Show disconnected slot
                    holder.trackerImage.setImageResource(R.drawable.tracker_disconnected);
                    holder.colorIndicator.setImageResource(R.drawable.baseline_lightbulb_24);
                    holder.nameText.setText("Empty Slot");
                    holder.macText.setText("");
                    holder.positionText.setText("");
                    holder.batteryText.setText("");
                    holder.connectionStatusText.setText("");
                    holder.trackerImage.clearAnimation();
                    gridItem.setVisibility(View.VISIBLE);
                }
            }
        });
        
        // Update button state after updating device grid
        updateButtonState();
    }

    private void updateButtonState() {
        int connectedDevices = getConnectedDeviceCount();
        boolean hasRequiredDevices = (connectedDevices == 4);
        boolean hasPositionsAssigned = allDevicesHavePositions();
        boolean shouldEnable = hasRequiredDevices;
        boolean shouldFullAlpha = hasRequiredDevices && hasPositionsAssigned;
        
        // Debug logging to troubleshoot the 3-device issue
        Log.d("ButtonState", "Connected devices: " + connectedDevices);
        Log.d("ButtonState", "Has required devices (4): " + hasRequiredDevices);
        Log.d("ButtonState", "Has positions assigned: " + hasPositionsAssigned);
        Log.d("ButtonState", "Should enable: " + shouldEnable);
        Log.d("ButtonState", "Should full alpha: " + shouldFullAlpha);
        
        loggingButton.setEnabled(shouldEnable);
        
        // Change button appearance based on state
        if (shouldFullAlpha) {
            loggingButton.setAlpha(1.0f);  // Fully visible when devices connected AND positioned
            Log.d("ButtonState", "Setting alpha to 1.0f");
        } else {
            loggingButton.setAlpha(0.5f);  // Dimmed when devices not connected or not positioned
            Log.d("ButtonState", "Setting alpha to 0.5f");
        }
        
        // Update reconnect button state - always enabled if there are devices
        if (reconnectButton != null) {
            ArrayList<String> macAddresses = savedMacAddresses.getMacAddresses();
            boolean hasDevicesToReconnect = !macAddresses.isEmpty();
            
            // Always enable reconnect button if devices exist, regardless of logging state
            reconnectButton.setEnabled(hasDevicesToReconnect);
            if (hasDevicesToReconnect) {
                reconnectButton.setAlpha(1.0f);
                // Update button text based on current connection status
                int connectedCount = getConnectedDeviceCount();
                if (connectedCount == macAddresses.size()) {
                    reconnectButton.setText("Refresh Connections");
                } else {
                    reconnectButton.setText("Reconnect Devices");
                }
            } else {
                reconnectButton.setAlpha(0.5f);
                reconnectButton.setText("Reconnect Devices");
            }
        }
        
        // Update scanner button state
        if (FirstFragment.scanner != null) {
            ArrayList<String> macAddresses = savedMacAddresses.getMacAddresses();
            boolean hasMaxDevices = macAddresses.size() >= 4;
            boolean isCurrentlyLogging = savedMacAddresses.isLogging();
            
            // Disable scanner if max devices reached or logging is active
            boolean shouldDisableScanner = hasMaxDevices || isCurrentlyLogging;
            
            FirstFragment.scanner.setEnabled(!shouldDisableScanner);
            if (shouldDisableScanner) {
                FirstFragment.scanner.setAlpha(0.5f);
            } else {
                FirstFragment.scanner.setAlpha(1.0f);
            }
        }
        
        // Update device information button state
        if (sensorsStatusButton != null) {
            ArrayList<String> macAddresses = savedMacAddresses.getMacAddresses();
            boolean hasDevices = !macAddresses.isEmpty();
            
            sensorsStatusButton.setEnabled(hasDevices);
            if (hasDevices) {
                sensorsStatusButton.setAlpha(1.0f);
            } else {
                sensorsStatusButton.setAlpha(0.5f);
            }
        }
    }

    private int getConnectedDeviceCount() {
        int count = 0;
        ArrayList<String> savedDevices = savedMacAddresses.getMacAddresses();
        
        Log.d("DeviceCount", "Total saved devices: " + savedDevices.size());
        Log.d("DeviceCount", "isConnected map size: " + isConnected.size());
        
        for (Boolean connected : isConnected.values()) {
            if (connected != null && connected) {
                count++;
            }
        }
        
        Log.d("DeviceCount", "Connected count from isConnected map: " + count);
        
        // Additional debug: check each saved device's connection status
        for (String macAddress : savedDevices) {
            Boolean connected = isConnected.get(macAddress);
            Log.d("DeviceCount", "Device " + macAddress + " connected: " + connected);
        }
        
        return count;
    }

    private void setColorIndicator(ImageView colorIndicator, ledEnum color) {
        switch (color) {
            case RED:
                colorIndicator.setImageResource(R.drawable.baseline_lightbulb_red_24);
                break;
            case GREEN:
                colorIndicator.setImageResource(R.drawable.baseline_lightbulb_green_24);
                break;
            case BLUE:
                colorIndicator.setImageResource(R.drawable.baseline_lightbulb_blue_24);
                break;
            case YELLOW:
                colorIndicator.setImageResource(R.drawable.baseline_lightbulb_yellow_24);
                break;
            default:
                colorIndicator.setImageResource(R.drawable.baseline_lightbulb_24);
                break;
        }
    }

    // Connection monitoring method
    private void monitorConnection(MetaWearBoard board, String deviceMac) {
        // Initial connection status check
        isConnected.put(deviceMac, board.isConnected());
        
        board.onUnexpectedDisconnect(status -> runOnUiThread(() -> {
            isConnected.put(deviceMac, false);
            Log.d("Connection Monitor", "Device disconnected: " + deviceMac);
            updateDeviceGrid(); // Update UI to show disconnected status
        }));
    }

    // Helper method to add new sensor data when device is connected
    private void addSensorData(String macAddress) {
        String position = savedMacAddresses.getPosition(macAddress);
        String battery = savedMacAddresses.getBattery(macAddress);
        String name = savedMacAddresses.getName(macAddress);

        SensorData newData = new SensorData(
                macAddress,
                position,
                battery,
                savedMacAddresses.getRecordTimeString());

        // Check if sensor data already exists
        boolean exists = false;
        for (int i = 0; i < sensorDataList.size(); i++) {
            if (sensorDataList.get(i).getMacAddress().equals(macAddress)) {
                // Update existing data
                sensorDataList.set(i, newData);
                exists = true;
                break;
            }
        }

        if (!exists) {
            sensorDataList.add(newData);
        }
    }

    /**
     * checkPermission - Verify and request necessary runtime permissions
     * 
     * Checks for all required permissions for BLE operations, file access,
     * and notifications. Requests any missing permissions from the user.
     * This is critical for Android 6+ where permissions must be granted at runtime.
     * 
     * Required permissions include:
     * - BLUETOOTH_SCAN and BLUETOOTH_CONNECT for BLE operations (Android 12+)
     * - READ/WRITE_EXTERNAL_STORAGE for data persistence
     * - POST_NOTIFICATIONS for user alerts
     */
    public void checkPermission() {
        // Check if all required permissions are already granted
        if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == GRANTED &&
                checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == GRANTED &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == GRANTED &&
                checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == GRANTED &&
                checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == GRANTED) {
            Log.d("Permission", "All permissions already granted");
        } else {
            // Request any missing permissions from the user
            ActivityCompat.requestPermissions(this, new String[] {
                    Manifest.permission.POST_NOTIFICATIONS,      // For user notifications
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,  // For data file writing
                    Manifest.permission.READ_EXTERNAL_STORAGE,   // For data file reading
                    Manifest.permission.BLUETOOTH_SCAN,          // For BLE device discovery
                    Manifest.permission.BLUETOOTH_CONNECT        // For BLE device connection
            },
                    PERMISSION_REQUEST);
        }

        // Initialize Bluetooth adapter for BLE operations
        btAdapter = ((BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE)).getAdapter();
        if (btAdapter == null) {
            new AlertDialog.Builder(this).setTitle("Bluetooth not available")
                    .setMessage("Bluetooth not available")
                    .setCancelable(false)
                    .setPositiveButton("Ok", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            MainActivity.this.finish();
                        }
                    })
                    .create()
                    .show();
        } else if (!btAdapter.isEnabled()) {
            final Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        return true;
    }

    public void scheduleInfoNotification() {
        Log.d("Schedule notification worker", "Work");
        PeriodicWorkRequest notification =
                new PeriodicWorkRequest.Builder(
                        SensorConnectionManager.class,
                        15,
                        TimeUnit.MINUTES
                )
                        .setInitialDelay(15, TimeUnit.MINUTES)
                        .build();
        WorkManager.getInstance(this).enqueue(notification);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        return super.onOptionsItemSelected(item);
    }

    /**
     * onSupportNavigateUp - Handle navigation up button press
     * 
     * Provides standard navigation behavior for the app bar's up button
     * using the configured navigation controller.
     */
    @Override
    public boolean onSupportNavigateUp() {
        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_content_main);
        return NavigationUI.navigateUp(navController, appBarConfiguration)
                || super.onSupportNavigateUp();
    }

    /**
     * onActivityResult - Handle results from child activities
     * 
     * Processes results from the device scanner activity, including:
     * - Adding newly selected devices to the managed device list
     * - Assigning colors and positions to new devices
     * - Validating device limits and preventing duplicates
     * - Initiating connections to newly added devices
     * 
     * @param requestCode The request code used when starting the activity
     * @param resultCode The result code returned by the child activity
     * @param data Intent data containing the activity results
     */

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        // Handle results from the device scanner activity
        if (requestCode == SCAN_DEVICES) {
            if (resultCode == RESULT_OK && data != null) {
                // Extract the list of newly selected device MAC addresses
                ArrayList<String> newMacAddresses = data.getStringArrayListExtra(ScannerActivity.SCAN_DEVICES);

                if (newMacAddresses != null && !newMacAddresses.isEmpty()) {
                    // Process each newly selected device
                    for (String newMacAddress : newMacAddresses) {
                        // Check for duplicate devices to prevent re-adding
                        if (savedMacAddresses.contains(newMacAddress)) {
                            Toast.makeText(getApplicationContext(), "Device already connected: " + newMacAddress,
                                    Toast.LENGTH_SHORT).show();
                        } else {
                            // Assign the next available color to the new device
                            ledEnum assignedColor = savedMacAddresses.getNextAvailableColor();
                            
                            // Verify we haven't exceeded the 4-device limit and have available colors
                            if (assignedColor != ledEnum.WHITE && savedMacAddresses.size() < 4) {
                                savedMacAddresses.add(newMacAddress);
                                savedMacAddresses.setColors(newMacAddress, assignedColor.getIndex());

                                // Add sensor data for the new device
                                addSensorData(newMacAddress);

                                Toast.makeText(getApplicationContext(),
                                        "Connected device: " + newMacAddress + " (Color: " + assignedColor + ")",
                                        Toast.LENGTH_SHORT).show();
                                connectionDevice(newMacAddress);
                            } else {
                                Toast.makeText(getApplicationContext(), "Maximum 4 devices supported", Toast.LENGTH_SHORT)
                                        .show();
                            }
                        }
                    }
                    scheduleInfoNotification();
                    updateDeviceGrid();
                    Log.d("onActivityResult", "Added " + newMacAddresses.size() + " new devices. Total devices: " + savedMacAddresses.size());
                } else {
                    Log.d("onActivityResult", "No devices selected or empty device list");
                }
            } else if (resultCode == RESULT_CANCELED) {
                // User cancelled the scanner activity - this is normal behavior
                Log.d("onActivityResult", "Scanner activity cancelled by user");
                // No action needed, just log for debugging
            } else {
                // Handle other result codes or null data
                Log.w("onActivityResult", "Unexpected result from scanner activity. ResultCode: " + resultCode + ", Data: " + (data != null ? "not null" : "null"));
            }
        }

        // Always call super to ensure proper activity result handling
        super.onActivityResult(requestCode, resultCode, data);
    }

    /**
     * onServiceConnected - Handle BLE service connection establishment
     * 
     * Called when the MetaWear BLE service is successfully bound to this activity.
     * Initializes device connections for any previously configured devices and
     * sets up the initial application state.
     * 
     * @param name The component name of the service that was connected
     * @param service The IBinder interface for communicating with the service
     */
    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        // Obtain the service binder for MetaWear BLE operations
        serviceBinder = (BtleService.LocalBinder) service;

        // On first startup, attempt to reconnect to previously configured devices
        if (start) {
            // Iterate through all saved MAC addresses and attempt connections
            for (String m : savedMacAddresses.getMacAddresses()) {
                connectionDevice(m);            // Establish BLE connection
                addSensorData(m);              // Initialize sensor data entry
            }
        }
        // Clear the startup flag to prevent re-connection on subsequent service bindings
        start = false;

        Log.d("On Service connected", "Size boards = " + boards.size());
        // Persist current state after service connection
        saveState();
    }

    private void connectionDevice(String address) {
        MetaWearBoard board;
        final BluetoothManager btManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);

        final BluetoothDevice remoteDevice = btManager.getAdapter().getRemoteDevice(address);

        if (ActivityCompat.checkSelfPermission(this,
                Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            Log.e("Permission Error", "Bluetooth connect permission not granted");
            return;
        }

        String alias = remoteDevice.getAlias();
        Log.d("Connection device", "Name: " + alias + ", Address: " + address);

        int index = savedMacAddresses.getIndex(address);
        if (index >= 0) {
            savedMacAddresses.setName(alias != null ? alias : "Device " + (index + 1), index);
        }
        saveState();

        if (serviceBinder.isBinderAlive()) {
            board = serviceBinder.getMetaWearBoard(remoteDevice);

            board.connectWithRetryAsync(3).continueWithTask(task -> {
                if (task.isCancelled()) {
                    Log.e("Connection Error", "Connection cancelled for: " + address);
                    isConnected.put(address, false);
                    return task;
                }
                if (task.isFaulted()) {
                    Log.e("Connection Error", "Connection failed for: " + address, task.getError());
                    isConnected.put(address, false); // Mark as disconnected on failure
                    return reconnect(board);
                }

                Log.d("Connection Success", "Connected to board: " + address);
                
                // Mark device as connected
                isConnected.put(address, true);

                boolean boardExists = boards.stream()
                        .anyMatch(b -> b.getMacAddress().equals(board.getMacAddress()));
                if (!boardExists) {
                    boards.add(board);
                }

                // Set up connection monitoring
                monitorConnection(board, address);

                setConnInterval(board.getModule(Settings.class));

                if (!savedMacAddresses.isLogging()) {
                    showDeviceColor(board);
                }

                return setupBatteryRoute(board, alias);
            }).continueWithTask(task -> {
                if (task.isFaulted()) {
                    Log.e("Connection Error", "Setup failed for board: " + address, task.getError());
                }
                
                // Read battery status during initial connection
                Settings settings = board.getModule(Settings.class);
                if (settings != null) {
                    settings.battery().read();
                    return Task.delay(1000); // Wait for battery reading to complete
                } else {
                    return Task.delay(CONNECTION_DELAY_MS);
                }
            }).continueWithTask(task -> {
                return board.disconnectAsync();
            }).continueWith(task -> {
                if (task.isFaulted()) {
                    Log.e("Connection Error", "Disconnect failed for board: " + address, task.getError());
                }
                runOnUiThread(() -> {
                    updateDeviceGrid();
                    updateButtonState(); // Update button state after device connection changes
                });
                return null;
            });
        }
    }

    private void showDeviceColor(MetaWearBoard board) {
        String macAddress = board.getMacAddress();
        String position = savedMacAddresses.getPosition(macAddress);
        ledEnum color = savedMacAddresses.getColor(macAddress);
        Led led = board.getModule(Led.class);

        if (led != null && color != ledEnum.WHITE) {
            led.stop(true);

            // Check if position is assigned
            if (position == null || position.isEmpty()) {
                // Position NOT assigned - BLINK
                switch (color) {
                    case RED:
                        led.editPattern(Led.Color.RED, Led.PatternPreset.BLINK).commit();
                        break;
                    case GREEN:
                        led.editPattern(Led.Color.GREEN, Led.PatternPreset.BLINK).commit();
                        break;
                    case BLUE:
                        led.editPattern(Led.Color.BLUE, Led.PatternPreset.BLINK).commit();
                        break;
                    case YELLOW:
                        led.editPattern(Led.Color.RED, Led.PatternPreset.BLINK).commit();
                        led.editPattern(Led.Color.GREEN, Led.PatternPreset.BLINK).commit();
                        break;
                }
            } else {
                // Position IS assigned - SOLID
                switch (color) {
                    case RED:
                        led.editPattern(Led.Color.RED, Led.PatternPreset.SOLID).commit();
                        break;
                    case GREEN:
                        led.editPattern(Led.Color.GREEN, Led.PatternPreset.SOLID).commit();
                        break;
                    case BLUE:
                        led.editPattern(Led.Color.BLUE, Led.PatternPreset.SOLID).commit();
                        break;
                    case YELLOW:
                        led.editPattern(Led.Color.RED, Led.PatternPreset.SOLID).commit();
                        led.editPattern(Led.Color.GREEN, Led.PatternPreset.SOLID).commit();
                        break;
                }
            }
            led.play();
        }
    }

    // Turn off all LEDs with error handling
    private void turnOffAllLEDs() {
        if (boards.isEmpty()) {
            Log.d("LED Off", "No boards to turn off LEDs");
            return;
        }
        
        Log.d("LED Off", "Turning off LEDs for " + boards.size() + " devices");
        
        for (MetaWearBoard board : boards) {
            board.connectWithRetryAsync(2).continueWithTask(task -> {
                if (task.isFaulted() || task.isCancelled()) {
                    Log.e("LED Off", "Failed to connect to device for LED off: " + board.getMacAddress());
                    return Task.forResult(null);
                }

                Led led = board.getModule(Led.class);
                if (led != null) {
                    led.stop(true);
                    Log.d("LED Off", "LED stopped for device: " + board.getMacAddress());
                } else {
                    Log.w("LED Off", "No LED module found for device: " + board.getMacAddress());
                }
                return Task.delay(1200); // Slightly increased delay for LED to turn off
            }).continueWithTask(task -> {
                return board.disconnectAsync();
            }).continueWith(task -> {
                if (task.isFaulted()) {
                    Log.e("LED Off", "Error turning off LED for: " + board.getMacAddress(), task.getError());
                } else {
                    Log.d("LED Off", "Successfully turned off LED for: " + board.getMacAddress());
                }
                return null;
            });
        }
    }

    // Add method to restore LEDs after logging stops
    private void restoreAllLEDs() {
        for (MetaWearBoard board : boards) {
            board.connectAsync().continueWithTask(task -> {
                if (task.isFaulted() || task.isCancelled()) {
                    return Task.forResult(null);
                }

                showDeviceColor(board);
                return Task.delay(1000); // Delay for LED setting
            }).continueWithTask(task -> {
                return board.disconnectAsync();
            }).continueWith(task -> {
                if (task.isFaulted()) {
                    Log.e("LED Restore", "Error restoring LED for: " + board.getMacAddress(), task.getError());
                }
                return null;
            });
        }
    }

    private void removeDeviceFromPosition(String macAddress, int slotIndex) {
        savedMacAddresses.setPosition(macAddress, "");
        savedMacAddresses.setCheckDevice(false, slotIndex);
        saveState();
        updateDeviceGrid();

        // Update LED to blinking if not logging
        if (!savedMacAddresses.isLogging()) {
            updateDeviceLED(macAddress);
        }

        Toast.makeText(this, "Device removed from position", Toast.LENGTH_SHORT).show();
    }

    // Updated position assignment dialog with logging state check
    private void showPositionAssignmentDialog(int slotIndex) {
        // Don't allow position assignment during operations OR when logging is active
        if (isLoggingOperationInProgress || isLEDOperationInProgress || isResetOperationInProgress) {
            Toast.makeText(this, "Operation in progress, please wait...", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Block grid interaction when logging is active
        if (savedMacAddresses.isLogging()) {
            Toast.makeText(this, "Stop logging before managing devices", Toast.LENGTH_SHORT).show();
            return;
        }

        ArrayList<String> macAddresses = savedMacAddresses.getMacAddresses();
        
        // Check if slot has a device
        if (slotIndex >= macAddresses.size() || macAddresses.isEmpty()) {
            Toast.makeText(this, "No device in this slot", Toast.LENGTH_SHORT).show();
            return;
        }

        String macAddress = macAddresses.get(slotIndex);
        
        // Check if the device is connected
        Boolean connected = isConnected.get(macAddress);
        if (connected == null || !connected) {
            Toast.makeText(this, "Device is not connected. Try using 'Reconnect Devices' button", Toast.LENGTH_SHORT).show();
            return;
        }

        String deviceName = savedMacAddresses.getName(macAddress);
        String currentPosition = savedMacAddresses.getPosition(macAddress);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Manage: " + (deviceName != null ? deviceName : macAddress))
                .setItems(POSITIONS, (dialog, which) -> {
                    String selectedPosition = POSITIONS[which];

                    // Check if position is already taken by another device
                    for (int i = 0; i < macAddresses.size(); i++) {
                        String otherDevicePosition = savedMacAddresses.getPosition(macAddresses.get(i));
                        if (i != slotIndex
                                && selectedPosition.equals(otherDevicePosition)
                                && otherDevicePosition != null && !otherDevicePosition.isEmpty()) {
                            // Position is taken, ask if user wants to swap
                            int finalI = i;
                            new AlertDialog.Builder(this)
                                    .setTitle("Position Already Taken")
                                    .setMessage("This position is already assigned to another device. Swap positions?")
                                    .setPositiveButton("Swap", (swapDialog, swapWhich) -> {
                                        String deviceCurrentPosition = savedMacAddresses.getPosition(macAddress);
                                        savedMacAddresses.setPosition(macAddresses.get(finalI), deviceCurrentPosition);
                                        savedMacAddresses.setPosition(macAddress, selectedPosition);
                                        savedMacAddresses.setCheckDevice(true, slotIndex);
                                        saveState();
                                        updateDeviceGrid();

                                        // Update LEDs for both devices if not logging
                                        if (!savedMacAddresses.isLogging()) {
                                            updateDeviceLED(macAddress);
                                            updateDeviceLED(macAddresses.get(finalI));
                                        }

                                        Toast.makeText(this, "Positions swapped", Toast.LENGTH_SHORT).show();
                                    })
                                    .setNegativeButton("Cancel", null)
                                    .show();
                            return;
                        }
                    }

                    // Position is free
                    savedMacAddresses.setPosition(macAddress, selectedPosition);
                    savedMacAddresses.setCheckDevice(true, slotIndex);
                    Toast.makeText(this, "Assigned " + selectedPosition + " to " + (deviceName != null ? deviceName : macAddress), Toast.LENGTH_SHORT).show();
                    saveState();
                    updateDeviceGrid();

                    // Update LED for this device if not logging (blink -> solid)
                    if (!savedMacAddresses.isLogging()) {
                        updateDeviceLED(macAddress);
                    }
                })
                .setNegativeButton("Cancel", null);

        // Add "Remove from Position" button if device has a position assigned
        if (currentPosition != null && !currentPosition.isEmpty()) {
            builder.setNeutralButton("Remove from Position", (dialog, which) -> {
                removeDeviceFromPosition(macAddress, slotIndex);
            });
        }

        // Add "Remove Device" button
        builder.setPositiveButton("Remove Device", (dialog, which) -> {
            showRemoveDeviceConfirmation(macAddress, deviceName);
        });

        builder.create().show();
    }

    // Also add the missing showRemoveDeviceConfirmation method
    private void showRemoveDeviceConfirmation(String macAddress, String deviceName) {
        String displayName = deviceName != null ? deviceName : macAddress;
        
        new AlertDialog.Builder(this)
                .setTitle("Remove Device")
                .setMessage("Are you sure you want to remove \"" + displayName + "\" from the system?")
                .setPositiveButton("Remove", (dialog, which) -> {
                    removeDeviceAndCleanup(macAddress);
                    saveState();
                    Toast.makeText(this, "Device \"" + displayName + "\" removed", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // Add helper method to update individual device LED
    private void updateDeviceLED(String macAddress) {
        // Don't update LED if operations are in progress
        if (isLEDOperationInProgress || isLoggingOperationInProgress) {
            Log.d("LED Update", "Skipping LED update for " + macAddress + " - operation in progress");
            return;
        }

        MetaWearBoard board = null;
        for (MetaWearBoard b : boards) {
            if (b.getMacAddress().equals(macAddress)) {
                board = b;
                break;
            }
        }

        if (board == null)
            return;

        final MetaWearBoard finalBoard = board;
        board.connectWithRetryAsync(2).continueWithTask(task -> {
            if (task.isFaulted() || task.isCancelled()) {
                return Task.forResult(null);
            }

            showDeviceColor(finalBoard);
            return Task.delay(1000); // Wait for LED to be set
        }).continueWithTask(task -> {
            return finalBoard.disconnectAsync();
        }).continueWith(task -> {
            if (task.isFaulted()) {
                Log.e("LED Update", "Error updating LED for: " + macAddress, task.getError());
            }
            return null;
        });
    }

    /**
     * Begins sensor data logging for all connected devices
     */
    public void startLogging() {
        Log.d("StartLogging", "Starting logging operation");

        if (isLoggingOperationInProgress || isLEDOperationInProgress) {
            Toast.makeText(this, "Operation in progress", Toast.LENGTH_SHORT).show();
            return;
        }

        if (boards.isEmpty() || !serviceBinder.isBinderAlive()) {
            Toast.makeText(this, "No devices connected", Toast.LENGTH_LONG).show();
            return;
        }

        proceedWithLogging();
    }



    private void proceedWithLogging() {

        // Set operation in progress and disable button
        isLoggingOperationInProgress = true;
        isLEDOperationInProgress = true;
        loggingButton.setEnabled(false);
        loggingButton.setText("Starting...");
        if (FirstFragment.scanner != null) {
            FirstFragment.scanner.setEnabled(false);
        }

        // Turn off all LEDs when logging starts
        turnOffAllLEDs();

        for (MetaWearBoard board : boards) {
            Log.d("Start Logging", "Starting logging for: " + board.getMacAddress());

            board.connectWithRetryAsync(3).continueWithTask(task -> {
                if (task.isCancelled()) {
                    Log.e("StartLogging", "Connection cancelled for: " + board.getMacAddress());
                    return task;
                }
                if (task.isFaulted()) {
                    Log.e("StartLogging", "Connection failed for: " + board.getMacAddress(), task.getError());
                    return reconnect(board);
                }

                setConnInterval(board.getModule(Settings.class));

                Logging logging = board.getModule(Logging.class);
                if (logging != null) {
                    logging.start(true);
                    Log.d("Start Logging", "Logging started for: " + board.getMacAddress());
                } else {
                    Log.e("StartLogging", "Logging module not available for: " + board.getMacAddress());
                }

                return Task.delay(LOGGING_DELAY_MS);
            }).continueWithTask(task -> {
                return board.disconnectAsync();
            }).continueWith(task -> {
                if (task.isFaulted()) {
                    Log.e("StartLogging", "Error in start logging chain for: " + board.getMacAddress(),
                            task.getError());
                }
                return null;
            });
        }

        savedMacAddresses.setLogging(true);
        startPeriodicUpdates();

        resetButton.setEnabled(false);

        // Persist the current state to storage
        saveState();

        // Wait for LED operations to complete before re-enabling UI controls
        new android.os.Handler().postDelayed(() -> {
            runOnUiThread(() -> {
                // Clear operation flags and restore UI state
                isLoggingOperationInProgress = false;
                isLEDOperationInProgress = false;
                loggingButton.setEnabled(true);
                // Keep scanner disabled during logging to prevent conflicts
                loggingButton.setText("Stop");
            });
        }, 2000); // Wait for LED operations to complete
    }

    /**
     * Stops sensor data logging and cleans up resources
     */
    public void stopLogging() {
        Log.d("StopLogging", "Stopping logging operation");

        if (isLoggingOperationInProgress || isLEDOperationInProgress) {
            Toast.makeText(this, "Operation in progress", Toast.LENGTH_SHORT).show();
            return;
        }

        if (boards.isEmpty() || !serviceBinder.isBinderAlive()) {
            Toast.makeText(this, "No devices connected", Toast.LENGTH_LONG).show();
            return;
        }

        isLoggingOperationInProgress = true;
        isLEDOperationInProgress = true;
        loggingButton.setEnabled(false);
        loggingButton.setText("Stopping...");

        for (MetaWearBoard board : boards) {
            Log.d("Stop Logging", "Stopping logging for: " + board.getMacAddress());

            board.connectAsync().continueWithTask(task -> {
                if (task.isCancelled()) {
                    Log.e("StopLogging", "Connection cancelled for: " + board.getMacAddress());
                    return task;
                }
                if (task.isFaulted()) {
                    Log.e("StopLogging", "Connection failed for: " + board.getMacAddress(), task.getError());
                    return reconnect(board);
                }

                setConnInterval(board.getModule(Settings.class));

                Logging logging = board.getModule(Logging.class);
                if (logging != null) {
                    logging.stop();
                    Log.d("Stop Logging", "Logging stopped for: " + board.getMacAddress());
                } else {
                    Log.e("StopLogging", "Logging module not available for: " + board.getMacAddress());
                }

                return Task.delay(STOP_LOGGING_DELAY_MS);
            }).continueWithTask(task -> {
                return board.disconnectAsync();
            }).continueWith(task -> {
                if (task.isFaulted()) {
                    Log.e("StopLogging", "Error in stop logging chain for: " + board.getMacAddress(), task.getError());
                }
                return null;
            });
        }

        savedMacAddresses.setLogging(false);
        stopPeriodicUpdates();

        resetButton.setEnabled(true);

        // Restore LEDs after logging stops
        restoreAllLEDs();

        saveState();

        // Wait for LED operations to complete before re-enabling buttons
        new android.os.Handler().postDelayed(() -> {
            runOnUiThread(() -> {
                isLoggingOperationInProgress = false;
                isLEDOperationInProgress = false;
                loggingButton.setEnabled(true);
                loggingButton.setText("Start");
                // Re-enable scanner when logging stops
                if (FirstFragment.scanner != null) {
                    FirstFragment.scanner.setEnabled(true);
                }
            });
        }, 2000); // Wait for LED restore operations to complete
    }

    public static void removeDevice(String address) {
        for (MetaWearBoard m : boards) {
            if (m.getMacAddress().equals(address))
                m.disconnectAsync();
        }
    }

    public static void removeDeviceAndCleanup(String macAddress) {
        savedMacAddresses.remove(macAddress);

        MetaWearBoard boardToRemove = null;
        for (MetaWearBoard board : boards) {
            if (board.getMacAddress().equals(macAddress)) {
                board.connectAsync().continueWithTask(task -> {
                    if (task.isCancelled() || task.isFaulted()) {
                        return board.disconnectAsync();
                    }
                    
                    // Tear down board resources before LED cleanup
                    Log.d("RemoveDevice", "Tearing down board resources: " + macAddress);
                    //board.tearDown();
                    
                    Led led = board.getModule(Led.class);
                    if (led != null)
                        led.stop(true);
                    return Task.delay(100);
                }).continueWithTask(task -> {
                    return board.disconnectAsync();
                }).continueWith(task -> {
                    if (task.isFaulted()) {
                        Log.e("RemoveDevice", "Error during cleanup for: " + macAddress, task.getError());
                    }
                    return null;
                });

                boardToRemove = board;
                break;
            }
        }

        if (boardToRemove != null) {
            boards.remove(boardToRemove);
        }

        // Remove from sensorDataList (no RecyclerView notification needed)
        sensorDataList.removeIf(data -> data.getMacAddress().equals(macAddress));

        if (instance != null) {
            instance.runOnUiThread(() -> {
                instance.updateDeviceGrid();
                instance.updateButtonState(); // Update button state after device removal
            });

            instance.batteryRouteAdded.remove(macAddress);
            instance.isConnected.remove(macAddress); // Remove connection tracking for removed device
        }
    }

    /**
     * onServiceDisconnected - Handle BLE service disconnection
     * 
     * Called when the MetaWear BLE service is unexpectedly disconnected.
     * Currently no specific cleanup is required as the service binding
     * will automatically attempt to reconnect.
     */
    @Override
    public void onServiceDisconnected(ComponentName name) {
        // No specific cleanup required - service will automatically rebind
    }

    /**
     * onDestroy - Clean up resources when activity is destroyed
     * 
     * Performs essential cleanup operations including:
     * - Stopping background handlers and periodic tasks
     * - Persisting current application state
     * - Calling superclass cleanup
     */
    @Override
    protected void onDestroy() {
        Log.d("On destroy", "Cleaning up activity resources");
        
        // Stop all background handlers to prevent memory leaks
        uiUpdateHandler.removeCallbacks(uiUpdater);
        batteryHandler.removeCallbacks(batteryUpdater);
        
        // Persist current state before destruction
        saveState();
        
        super.onDestroy();
    }

    /**
     * saveState - Persist application state to internal storage
     * 
     * Serializes the current SaveStruct object containing all device
     * configurations, logging state, and user preferences to a binary file.
     * This ensures data persistence across app restarts and device reboots.
     */
    private void saveState() {
        Log.d("Save state", "Persisting state - Logging: " + savedMacAddresses.getLogging().toString());
        try (ObjectOutputStream oos = new ObjectOutputStream(openFileOutput("struct.bin", Context.MODE_PRIVATE))) {
            oos.writeObject(savedMacAddresses);
            Log.d("Save state", "Application state saved successfully");
        } catch (IOException e) {
            Log.e("Save state", "Failed to save application state", e);
            e.printStackTrace();
        }
    }

    /**
     * readState - Load application state from internal storage
     * 
     * Attempts to deserialize the SaveStruct object from the binary file
     * created by saveState(). If the file doesn't exist or is corrupted,
     * returns a new empty SaveStruct object for clean initialization.
     * 
     * @return SaveStruct object containing loaded state, or new empty state if loading fails
     */
    private SaveStruct readState() {
        try (FileInputStream fis = openFileInput("struct.bin");
                ObjectInputStream ois = new ObjectInputStream(fis)) {
            SaveStruct loadedState = (SaveStruct) ois.readObject();
            Log.d("Read state", "Application state loaded successfully");
            return loadedState;
        } catch (IOException | ClassNotFoundException e) {
            Log.w("Read state", "Could not load saved state, using fresh state", e);
            e.printStackTrace();
        }
        // Return clean state if loading fails
        return new SaveStruct();
    }

    /**
     * updateAllDeviceInformationBeforeStatus - Refresh device data before status display
     * 
     * Updates battery levels and device information for all connected devices
     * before displaying the device status dialog. Provides visual feedback
     * during the information gathering process.
     * 
     * @param button The button that triggered this update (for restoring state)
     * @param originalText The original button text to restore after completion
     */
    private void updateAllDeviceInformationBeforeStatus(Button button, String originalText) {
        // Handle case where no devices are connected
        if (boards.isEmpty()) {
            runOnUiThread(() -> {
                button.setText(originalText);
                button.setEnabled(true);
                displayDeviceInformation(); // Show "no devices" message
            });
            return;
        }

        // Create a map to store updated device information during collection
        Map<String, DeviceInfo> deviceInfoMap = new HashMap<>();
        AtomicInteger completedUpdates = new AtomicInteger(0);
        int totalBoards = boards.size();
        AtomicBoolean timeoutOccurred = new AtomicBoolean(false);
        
        // Set up 10-second timeout
        android.os.Handler timeoutHandler = new android.os.Handler();
        Runnable timeoutRunnable = () -> {
            if (!timeoutOccurred.getAndSet(true)) {
                Log.w("DeviceInfo", "Timeout occurred while retrieving device information");
                runOnUiThread(() -> {
                    button.setText(originalText);
                    button.setEnabled(true);
                    
                    // Show partial results or timeout message
                    if (!deviceInfoMap.isEmpty()) {
                        Toast.makeText(this, "Timeout occurred - showing partial information", Toast.LENGTH_SHORT).show();
                        displayDeviceInformation(deviceInfoMap);
                    } else {
                        Toast.makeText(this, "Timeout - failed to retrieve device information", Toast.LENGTH_SHORT).show();
                        displayDeviceInformation(); // Show empty status
                    }
                });
            }
        };
        timeoutHandler.postDelayed(timeoutRunnable, 10000); // 10 second timeout
        
        for (MetaWearBoard board : boards) {
            String macAddress = board.getMacAddress();
            DeviceInfo info = new DeviceInfo();
            info.macAddress = macAddress;
            info.name = savedMacAddresses.getName(macAddress);
            info.position = savedMacAddresses.getPosition(macAddress);
            info.color = savedMacAddresses.getColor(macAddress);
            info.battery = "Reading..."; // Show reading status initially
            info.isConnected = board.isConnected();
            
            deviceInfoMap.put(macAddress, info);
            
            // Check if device is connected first, if not attempt reconnection
            Task<Void> connectionTask;
            if (!board.isConnected()) {
                Log.d("DeviceInfo", "Device " + macAddress + " not connected, attempting reconnection");
                info.connectionStatus = "Reconnecting...";
                connectionTask = board.connectWithRetryAsync(3);
            } else {
                Log.d("DeviceInfo", "Device " + macAddress + " already connected");
                info.connectionStatus = "Connected";
                connectionTask = Task.forResult(null);
            }
            
            // Always read fresh battery status
            connectionTask.continueWithTask(task -> {
                if (task.isFaulted() || task.isCancelled()) {
                    Log.e("DeviceInfo", "Connection failed for: " + macAddress);
                    info.connectionStatus = "Failed to connect";
                    info.battery = "Connection failed";
                    return Task.forResult(null);
                }
                
                info.connectionStatus = "Connected";
                isConnected.put(macAddress, true); // Update connection status
                
                // Set up a temporary battery route just for this reading
                Settings settings = board.getModule(Settings.class);
                if (settings != null) {
                    return settings.battery().addRouteAsync(source -> {
                        source.stream((data, env) -> {
                            Settings.BatteryState batteryState = data.value(Settings.BatteryState.class);
                            String battery = batteryState.charge + "%";
                            // Update both the info object and saved data
                            info.battery = battery;
                            savedMacAddresses.setBattery(macAddress, battery);
                            Log.d("BatteryRead", "Updated battery for " + macAddress + ": " + battery);
                        });
                    }).continueWithTask(routeTask -> {
                        if (!routeTask.isFaulted()) {
                            settings.battery().read();
                            return Task.delay(500); // Wait for battery response
                        }
                        return Task.forResult(null);
                    });
                } else {
                    info.battery = "Settings module unavailable";
                    return Task.forResult(null);
                }
            }).continueWithTask(task -> board.disconnectAsync())
            .continueWith(task -> {
                if (task.isFaulted()) {
                    Log.e("DeviceInfo", "Error in battery reading chain for: " + macAddress, task.getError());
                    info.battery = "Error reading battery";
                }
                
                int completed = completedUpdates.incrementAndGet();
                Log.d("DeviceInfo", "Completed " + completed + " of " + totalBoards + " devices");
                
                if (completed >= totalBoards && !timeoutOccurred.get()) {
                    // Cancel timeout since we completed successfully
                    timeoutHandler.removeCallbacks(timeoutRunnable);
                    
                    // Save state with updated battery info
                    saveState();
                    runOnUiThread(() -> {
                        button.setText(originalText);
                        button.setEnabled(true);
                        updateDeviceGrid(); // Update the device grid with new connection statuses
                        displayDeviceInformation(deviceInfoMap);
                    });
                }
                return null;
            });
        }
    }

    private void displayDeviceInformation() {
        // Fallback method that shows logging status and basic device info
        if (boards.isEmpty()) {
            Toast.makeText(this, "No devices connected", Toast.LENGTH_SHORT).show();
            return;
        }
        
        StringBuilder statusMessage = new StringBuilder();
        
        // Add logging status at the top
        boolean isLogging = savedMacAddresses.isLogging();
        statusMessage.append("LOGGING STATUS: ").append(isLogging ? "ACTIVE" : "STOPPED")
                .append("\n")
                .append("=====================================")
                .append("\n\n");
        
        for (MetaWearBoard board : boards) {
            String macAddress = board.getMacAddress();
            String name = savedMacAddresses.getName(macAddress);
            String position = savedMacAddresses.getPosition(macAddress);
            ledEnum color = savedMacAddresses.getColor(macAddress);
            String battery = savedMacAddresses.getBattery(macAddress);

            statusMessage.append("Device: ").append(name != null ? name : "Unknown")
                    .append("\nMAC: ").append(macAddress)
                    .append("\nColor: ").append(color)
                    .append("\nPosition: ").append(position != null ? position : "Not assigned")
                    .append("\nBattery: ").append(battery)
                    .append("\nSensor Config: Unable to verify - use full check")
                    .append("\n\n");
        }

        showStatusDialog("Device Information & Logging Status", statusMessage.toString());
    }

    private void displayDeviceInformation(Map<String, DeviceInfo> deviceInfoMap) {
        if (deviceInfoMap.isEmpty()) {
            Toast.makeText(this, "No device information available", Toast.LENGTH_SHORT).show();
            return;
        }

        StringBuilder infoMessage = new StringBuilder();
        
        // Add logging status at the top
        boolean isLogging = savedMacAddresses.isLogging();
        infoMessage.append("LOGGING STATUS: ").append(isLogging ? "ACTIVE" : "STOPPED")
                .append("\n")
                .append("=====================================")
                .append("\n\n");
        
        for (DeviceInfo info : deviceInfoMap.values()) {
            infoMessage.append("Device: ").append(info.name != null ? info.name : "Unknown")
                    .append("\nMAC: ").append(info.macAddress)
                    .append("\nColor: ").append(info.color)
                    .append("\nPosition: ").append(info.position != null ? info.position : "Not assigned")
                    .append("\nBattery: ").append(info.battery)
                    .append("\nConnection: ").append(info.connectionStatus != null ? info.connectionStatus : (info.isConnected ? "Connected" : "Disconnected"))
                    .append("\n\n");
        }

        showStatusDialog("Device Information & Logging Status", infoMessage.toString());
    }
}