/**
 * ConnectDeviceForNotification - Background battery status reader for MetaWear devices
 * 
 * This utility class provides a specialized service for connecting to MetaWear devices
 * in the background to read battery status without disrupting the main application flow.
 * It's designed for scenarios where battery information is needed for notifications,
 * status updates, or background monitoring.
 * 
 * Key Features:
 * - Asynchronous device connection and battery reading
 * - Callback-based completion notification system
 * - Thread-safe operation using CountDownLatch synchronization
 * - Automatic cleanup and resource management
 * - Integration with MetaWear BLE service infrastructure
 * 
 * Use Cases:
 * - Reading battery status for notification services
 * - Background health monitoring of connected devices
 * - Periodic battery level checks without UI interaction
 * - Quick connection status verification
 * 
 * Technical Implementation:
 * - Implements ServiceConnection for BLE service binding
 * - Implements Runnable for background thread execution
 * - Uses MetaWear SDK for device communication
 * - Employs async/await pattern with Task continuation
 * 
 * @author MetaWear Sensors App
 * @version 1.0
 */
package com.example.sensors;

import static com.example.sensors.ScannerActivity.reconnect;
import static com.example.sensors.ScannerActivity.setConnInterval;

import android.bluetooth.BluetoothDevice;
import android.content.ComponentName;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.util.Log;

import com.mbientlab.metawear.Data;
import com.mbientlab.metawear.MetaWearBoard;
import com.mbientlab.metawear.Route;
import com.mbientlab.metawear.Subscriber;
import com.mbientlab.metawear.android.BtleService;
import com.mbientlab.metawear.builder.RouteBuilder;
import com.mbientlab.metawear.builder.RouteComponent;
import com.mbientlab.metawear.module.Led;
import com.mbientlab.metawear.module.Settings;

import java.util.concurrent.CountDownLatch;

import bolts.Continuation;
import bolts.Task;

/**
 * ConnectDeviceForNotification implements ServiceConnection and Runnable
 * to provide background battery status reading capabilities.
 * 
 * This class manages the complete lifecycle of:
 * - BLE service connection establishment
 * - MetaWear device connection and authentication
 * - Battery route setup and data reading
 * - Async callback notification with results
 * - Resource cleanup and disconnection
 * 
 * Thread Safety:
 * Uses CountDownLatch for synchronization between the connection thread
 * and the battery reading completion, ensuring proper async operation.
 */
public class ConnectDeviceForNotification implements ServiceConnection, Runnable {
    
    // Device identification and connection
    private final String macAddress;                    // Target device MAC address
    public static BtleService.LocalBinder serviceBinder; // BLE service reference
    
    // Battery reading state management
    private String text;                               // Battery status result storage
    private OnCompleteListener onCompleteListener;    // Completion callback interface
    private final CountDownLatch latch = new CountDownLatch(1); // Async synchronization
    
    /**
     * Constructor for ConnectDeviceForNotification
     * 
     * @param macAddress MAC address of the MetaWear device to read battery from
     */
    public ConnectDeviceForNotification(String macAddress){
        this.macAddress = macAddress;
    }

    /**
     * Callback interface for battery reading completion notification
     * 
     * Implementers of this interface will receive the battery status
     * when the background reading operation completes successfully.
     */
    public interface OnCompleteListener {
        /**
         * Called when battery reading operation completes
         * 
         * @param batteryStatus String representation of battery level (e.g., "85%")
         */
        void onComplete(String batteryStatus);
    }

    /**
     * Sets the completion callback listener
     * 
     * @param listener OnCompleteListener implementation to receive results
     */
    public void setOnCompleteListener(OnCompleteListener listener) {
        this.onCompleteListener = listener;
    }

    /**
     * ServiceConnection callback - called when BLE service connects
     * Note: This implementation doesn't use this callback directly as the
     * service binding is managed by the main application context.
     */
    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        serviceBinder = (BtleService.LocalBinder) service;
    }

    // Connects to the device, reads battery, and triggers the latch when done
    private void connectDevice(){
        // Get the BluetoothDevice object from the MAC address
        final BluetoothDevice remoteDevice =
                MainActivity.btAdapter.getRemoteDevice(macAddress);

        // Get the MetaWearBoard instance for the device
        MetaWearBoard m = MainActivity.serviceBinder.getMetaWearBoard(remoteDevice);

        // Connect asynchronously to the board
        m.connectAsync().continueWithTask(task -> {
            if (task.isCancelled()) {
                return task;
            }
            // If connection failed, try to reconnect
            return task.isFaulted() ? reconnect(m) : Task.forResult(null);
        })
        .continueWith(task -> {
            if (!task.isCancelled()) {
                // Set BLE connection interval for better performance
                setConnInterval(m.getModule(Settings.class));
            }
            // DON'T interfere with LED - device may have position-based configuration
            // Skip LED manipulation to preserve device state

            final Settings settings = m.getModule(Settings.class);

            // Add a route to stream battery state data
            settings.battery().addRouteAsync(new RouteBuilder() {
                @Override
                public void configure(RouteComponent source) {
                    source.stream(new Subscriber() {
                        @Override
                        public void apply(Data data, Object... env) {
                            // Save the battery charge as string
                            text = String.valueOf(data.value(Settings.BatteryState.class).charge);
                            latch.countDown(); // Notify that battery state is ready
                        }
                    });
                }
            }).continueWith(new Continuation<Route, Void>() {
                @Override
                public Void then(Task<Route> task) throws Exception {
                    // Trigger a battery read
                    settings.battery().read();
                    return null;
                }
            });
            return null;
        }).continueWith(task -> {
            // DON'T stop LED - preserve device's position-based LED state
            // Just disconnect cleanly without interfering with device configuration
            m.disconnectAsync();
            Log.d("End connection","disconnectAsync");
            return null;
        });
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        // Not used, but required by ServiceConnection
    }

    // Returns true if battery status has been read
    public boolean checkBattery(){
        return text != null;
    }

    // Returns the battery status string
    public String getText(){
        return text;
    }

    // Runnable implementation: connects, waits for battery read, then calls callback
    @Override
    public void run() {
        connectDevice();

        try {
            latch.await(); // Wait until battery status is read
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        // Call the callback if set and battery status is available
        if (onCompleteListener != null && text != null) {
            onCompleteListener.onComplete(text);
        }
    }
}