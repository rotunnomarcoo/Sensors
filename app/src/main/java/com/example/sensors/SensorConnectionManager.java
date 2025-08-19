/**
 * SensorConnectionManager - Background worker for MetaWear device monitoring
 * 
 * This worker class provides background monitoring and notification services for
 * MetaWear sensor devices during active logging sessions. It operates independently
 * of the main application UI to ensure continuous device monitoring and user
 * notifications even when the app is in the background.
 * 
 * Key Responsibilities:
 * - Periodic battery status monitoring for connected devices
 * - Background device connection health checks
 * - User notifications for device status updates
 * - Battery level alerts and warnings
 * - Automated device reconnection attempts
 * 
 * Technical Implementation:
 * - Extends AndroidX Worker for reliable background execution
 * - Integrates with WorkManager for scheduled periodic tasks
 * - Uses Android notification system for user alerts
 * - Operates during active logging sessions only
 * - Manages BLE service connections for device communication
 * 
 * Use Cases:
 * - Monitoring device battery levels during extended logging sessions
 * - Alerting users to device disconnections or failures
 * - Providing status updates without user interaction
 * - Ensuring data integrity during background operation
 * 
 * @author MetaWear Sensors App
 * @version 1.0
 */
package com.example.sensors;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.mbientlab.metawear.MetaWearBoard;
import com.mbientlab.metawear.android.BtleService;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.zip.CRC32;

/**
 * SensorConnectionManager extends Worker to provide background sensor monitoring
 * 
 * This class implements the Worker pattern for reliable background execution:
 * - Runs independently of UI lifecycle
 * - Survives app backgrounding and process death
 * - Integrates with Android's WorkManager for optimal scheduling
 * - Respects device battery optimization and doze mode
 * - Provides guaranteed execution for critical monitoring tasks
 */
public class SensorConnectionManager extends Worker{
    
    // Background task execution and scheduling
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1); // For future async operations
    private final Context context = getApplicationContext();  // Application context for notifications
    
    // BLE service integration for device communication
    public static BtleService.LocalBinder serviceBinder;     // Static reference to BLE service
    
    /**
     * Constructor for SensorConnectionManager Worker
     * 
     * Required by the Worker class architecture. WorkManager uses this constructor
     * to instantiate the worker with necessary context and parameters.
     * 
     * @param context Application context for the worker
     * @param workerParams Parameters and configuration for the work execution
     */
    public SensorConnectionManager(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    /**
     * Main work execution method called by WorkManager
     * 
     * This method performs the core background monitoring operations:
     * - Checks if logging is currently active
     * - Iterates through connected devices
     * - Monitors battery levels and connection status
     * - Sends notifications for important status updates
     * - Returns work result status to WorkManager
     * 
     * @return Result indicating success, failure, or retry needed
     */
    @NonNull
    @Override
    public Result doWork() {
        Log.d("Worker","doWorkFunction - Starting battery update cycle");
        
        // Only run monitoring during active logging sessions to conserve battery
        if (!MainActivity.savedMacAddresses.isLogging()) {
            Log.d("Worker", "Skipping battery updates - logging is not active");
            return Result.success();
        }
        
        // Get the list of configured devices from MainActivity's SaveStruct
        ArrayList<String> addresses = MainActivity.savedMacAddresses.getMacAddresses();

        if(addresses.isEmpty()){
            Log.d("Worker", "No devices to check");
            return Result.success();
        }
        
        Log.d("Worker", "Logging is active - updating battery for " + addresses.size() + " devices");
        
        // Update battery information for all devices using the main battery update method
        if (MainActivity.instance != null && MainActivity.serviceBinder != null && MainActivity.serviceBinder.isBinderAlive()) {
            for (String address : addresses) {
                // Find the corresponding MetaWearBoard
                MetaWearBoard targetBoard = null;
                for (MetaWearBoard board : MainActivity.boards) {
                    if (board.getMacAddress().equals(address)) {
                        targetBoard = board;
                        break;
                    }
                }
                
                if (targetBoard != null) {
                    Log.d("Worker", "Updating battery for device: " + address);
                    final String deviceAddress = address;
                    final MetaWearBoard finalBoard = targetBoard;
                    
                    // Call the main battery update method
                    MainActivity.instance.runOnUiThread(() -> {
                        // Use the existing updateBoardBattery method from MainActivity
                        MainActivity.instance.updateBoardBattery(finalBoard).continueWith(task -> {
                            // Check battery level after update and send notification/toast if needed
                            String updatedBattery = MainActivity.savedMacAddresses.getBattery(deviceAddress);
                            if (!"Unknown".equals(updatedBattery)) {
                                try {
                                    int batteryLevel = Integer.parseInt(updatedBattery.replace("%", ""));
                                    String name = MainActivity.savedMacAddresses.getName(deviceAddress);
                                    // Send notification and toast if battery is below 30%
                                    if (batteryLevel < 30) {
                                        String text = name + " Low Battery: " + updatedBattery;
                                        sendNotification(deviceAddress, text, "my_channel_id");
                                        
                                        // Also show toast on main thread
                                        /* MainActivity.instance.runOnUiThread(() -> {
                                            Toast.makeText(MainActivity.instance, 
                                                text + " - Consider charging device", 
                                                Toast.LENGTH_LONG).show();
                                        }); */
                                        
                                        Log.w("Worker", "LOW BATTERY ALERT: " + name + " (" + deviceAddress + ") - " + updatedBattery);
                                    } else {
                                        Log.d("Worker", "Battery OK: " + name + " (" + deviceAddress + ") - " + updatedBattery);
                                    }
                                    
                                    // Also check memory status
                                    if (MainActivity.savedMacAddresses.fullMemory()) {
                                        String memoryText = "Memory Full - " + MainActivity.savedMacAddresses.getRecordTimeString();
                                        sendNotification("memory_alert", memoryText, "my_channel_id");
                                    }
                                    
                                } catch (NumberFormatException e) {
                                    Log.w("Worker", "Invalid battery format for " + deviceAddress + ": " + updatedBattery);
                                }
                            }
                            return null;
                        });
                    });
                } else {
                    Log.w("Worker", "Board not found for address: " + address);
                }
            }
        } else {
            Log.e("Worker", "MainActivity instance or service binder not available");
        }

        // Indicate that the work finished successfully
        return Result.success();
    }

    // Generates a unique notification ID based on the MAC address using CRC32
    private int generateNotificationId(String macAddress){
        CRC32 crc = new CRC32();
        crc.update(macAddress.getBytes(StandardCharsets.UTF_8));
        long checksum = crc.getValue();
        return (int) (checksum & 0x7FFFFFFF); // Ensure positive int
    }

    // Sends a notification with the given text and channel
    public void sendNotification(String macAddress, String text, String notificationChannelId){
        Log.d("Notification",text);
        int id = generateNotificationId(macAddress);

        // Build the notification
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, notificationChannelId)
                .setSmallIcon(R.drawable.sensor_icon) // Icon for the notification
                .setContentTitle("Sensors check") // Title
                .setContentText(text) // Message
                .setPriority(NotificationCompat.PRIORITY_DEFAULT) // Priority
                .setAutoCancel(true); // Automatically removes the notification when clicked

        // Get the NotificationManager system service
        NotificationManager notificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        if (notificationManager != null) {
            // Create the notification channel if necessary (Android O+)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                NotificationChannel channel = new NotificationChannel(
                        notificationChannelId,
                        "Periodic Notifications",
                        NotificationManager.IMPORTANCE_DEFAULT);
                notificationManager.createNotificationChannel(channel);
            }
            // Show the notification
            notificationManager.notify(id, builder.build());
        }
    }
}