/**
 * SensorConnectionManager - Background worker for monitoring MetaWear devices.
 *
 * This class ensures continuous monitoring of MetaWear sensors during active logging sessions.
 * It operates in the background to check battery levels, connection status, and memory usage,
 * and sends notifications for critical updates.
 */

package com.example.sensors;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.mbientlab.metawear.MetaWearBoard;
import com.mbientlab.metawear.android.BtleService;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.zip.CRC32;

/**
 * Extends Worker to provide background monitoring for MetaWear devices.
 *
 * Responsibilities:
 * - Periodic battery level checks for connected devices.
 * - Notifications for low battery or memory alerts.
 * - Ensures data integrity during background operation.
 */
public class SensorConnectionManager extends Worker {

    // Scheduler for background tasks
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    // Application context for notifications
    private final Context context = getApplicationContext();

    // BLE service for device communication
    public static BtleService.LocalBinder serviceBinder;

    /**
     * Constructor for SensorConnectionManager.
     *
     * @param context Application context for the worker.
     * @param workerParams Parameters for work execution.
     */
    public SensorConnectionManager(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    /**
     * Executes the background monitoring tasks.
     *
     * - Checks if logging is active.
     * - Updates battery levels for connected devices.
     * - Sends notifications for low battery or memory alerts.
     *
     * @return Result indicating success, failure, or retry.
     */
    @NonNull
    @Override
    public Result doWork() {
        Log.d("Worker", "Starting battery update cycle");

        // Skip monitoring if logging is inactive
        if (!MainActivity.savedMacAddresses.isLogging()) {
            Log.d("Worker", "Logging is not active. Skipping updates.");
            return Result.success();
        }

        // Retrieve the list of connected devices
        ArrayList<String> addresses = MainActivity.savedMacAddresses.getMacAddresses();
        if (addresses.isEmpty()) {
            Log.d("Worker", "No devices to monitor.");
            return Result.success();
        }

        Log.d("Worker", "Updating battery for " + addresses.size() + " devices.");

        // Iterate through devices and update their battery levels
        if (MainActivity.instance != null && MainActivity.serviceBinder != null && MainActivity.serviceBinder.isBinderAlive()) {
            for (String address : addresses) {
                MetaWearBoard targetBoard = findBoardByAddress(address);

                if (targetBoard != null) {
                    updateDeviceBattery(targetBoard, address);
                } else {
                    Log.w("Worker", "Device not found: " + address);
                }
            }
        } else {
            Log.e("Worker", "MainActivity or service binder unavailable.");
        }

        return Result.success();
    }

    /**
     * Finds the MetaWearBoard corresponding to the given MAC address.
     *
     * @param address MAC address of the device.
     * @return The MetaWearBoard instance, or null if not found.
     */
    private MetaWearBoard findBoardByAddress(String address) {
        for (MetaWearBoard board : MainActivity.boards) {
            if (board.getMacAddress().equals(address)) {
                return board;
            }
        }
        return null;
    }

    /**
     * Updates the battery level for a specific device and sends notifications if needed.
     *
     * @param board The MetaWearBoard instance.
     * @param address MAC address of the device.
     */
    private void updateDeviceBattery(MetaWearBoard board, String address) {
        MainActivity.instance.runOnUiThread(() -> {
            MainActivity.instance.updateBoardBattery(board).continueWith(task -> {
                String batteryStatus = MainActivity.savedMacAddresses.getBattery(address);
                if (!"Unknown".equals(batteryStatus)) {
                    handleBatteryStatus(batteryStatus, address);
                }
                return null;
            });
        });
    }

    /**
     * Handles the battery status and sends notifications for low battery levels.
     *
     * @param batteryStatus Battery level as a string.
     * @param address MAC address of the device.
     */
    private void handleBatteryStatus(String batteryStatus, String address) {
        try {
            int batteryLevel = Integer.parseInt(batteryStatus.replace("%", ""));
            String deviceName = MainActivity.savedMacAddresses.getName(address);

            if (batteryLevel < 30) {
                String notificationText = deviceName + " Low Battery: " + batteryStatus;
                sendNotification(address, notificationText, "my_channel_id");
                Log.w("Worker", "Low battery alert: " + deviceName + " - " + batteryStatus);
            } else {
                Log.d("Worker", "Battery OK: " + deviceName + " - " + batteryStatus);
            }

            if (MainActivity.savedMacAddresses.fullMemory()) {
                sendNotification("memory_alert", "Memory Full", "my_channel_id");
            }
        } catch (NumberFormatException e) {
            Log.w("Worker", "Invalid battery format for " + address + ": " + batteryStatus);
        }
    }

    /**
     * Generates a unique notification ID based on the MAC address.
     *
     * @param macAddress MAC address of the device.
     * @return A unique notification ID.
     */
    private int generateNotificationId(String macAddress) {
        CRC32 crc = new CRC32();
        crc.update(macAddress.getBytes(StandardCharsets.UTF_8));
        return (int) (crc.getValue() & 0x7FFFFFFF);
    }

    /**
     * Sends a notification with the given text and channel.
     *
     * @param macAddress MAC address of the device.
     * @param text Notification text.
     * @param notificationChannelId Notification channel ID.
     */
    public void sendNotification(String macAddress, String text, String notificationChannelId) {
        Log.d("Notification", text);
        int id = generateNotificationId(macAddress);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, notificationChannelId)
                .setSmallIcon(R.drawable.sensor_icon)
                .setContentTitle("Sensors Check")
                .setContentText(text)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true);

        NotificationManager notificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        if (notificationManager != null) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                NotificationChannel channel = new NotificationChannel(
                        notificationChannelId,
                        "Periodic Notifications",
                        NotificationManager.IMPORTANCE_DEFAULT);
                notificationManager.createNotificationChannel(channel);
            }
            notificationManager.notify(id, builder.build());
        }
    }
}