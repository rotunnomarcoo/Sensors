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
 * Connects to a MetaWear device in the background to read its battery status.
 * Operates asynchronously and notifies the caller when the data is ready.
 */
public class ConnectDeviceForNotification implements ServiceConnection, Runnable {

    // MAC address of the target device
    private final String macAddress;

    // Reference to the BLE service for communication
    public static BtleService.LocalBinder serviceBinder;

    // Stores the battery status as a string
    private String text;

    // Callback interface for notifying when the battery status is ready
    private OnCompleteListener onCompleteListener;

    // Synchronization mechanism to wait for battery data
    private final CountDownLatch latch = new CountDownLatch(1);

    /**
     * Initializes the class with the target device's MAC address.
     *
     * @param macAddress MAC address of the MetaWear device
     */
    public ConnectDeviceForNotification(String macAddress) {
        this.macAddress = macAddress;
    }

    /**
     * Interface for notifying when the battery reading is complete.
     */
    public interface OnCompleteListener {
        /**
         * Called when the battery status is successfully read.
         *
         * @param batteryStatus Battery level as a string (e.g., "85%")
         */
        void onComplete(String batteryStatus);
    }

    /**
     * Sets the callback listener for battery reading completion.
     *
     * @param listener The listener to notify when the operation is complete
     */
    public void setOnCompleteListener(OnCompleteListener listener) {
        this.onCompleteListener = listener;
    }

    /**
     * Called when the BLE service is connected. Required by ServiceConnection.
     */
    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        serviceBinder = (BtleService.LocalBinder) service;
    }

    /**
     * Connects to the MetaWear device, reads the battery status, and disconnects.
     */
    private void connectDevice() {
        // Get the BluetoothDevice object using the MAC address
        final BluetoothDevice remoteDevice = MainActivity.btAdapter.getRemoteDevice(macAddress);

        // Get the MetaWearBoard instance for the device
        MetaWearBoard m = MainActivity.serviceBinder.getMetaWearBoard(remoteDevice);

        // Connect to the device asynchronously
        m.connectAsync()
            .continueWithTask(task -> {
                // Retry connection if it fails
                return task.isFaulted() ? reconnect(m) : Task.forResult(null);
            })
            .continueWith(task -> {
                if (!task.isCancelled()) {
                    // Optimize BLE connection interval
                    setConnInterval(m.getModule(Settings.class));

                    // Add a route to stream battery data
                    final Settings settings = m.getModule(Settings.class);
                    settings.battery().addRouteAsync(new RouteBuilder() {
                        @Override
                        public void configure(RouteComponent source) {
                            source.stream(new Subscriber() {
                                @Override
                                public void apply(Data data, Object... env) {
                                    // Save the battery charge as a string
                                    text = String.valueOf(data.value(Settings.BatteryState.class).charge);
                                    latch.countDown(); // Notify that data is ready
                                }
                            });
                        }
                    }).continueWith(task1 -> {
                        // Trigger a battery read
                        settings.battery().read();
                        return null;
                    });
                }
                return null;
            })
            .continueWith(task -> {
                // Disconnect the device after the operation
                m.disconnectAsync();
                Log.d("End connection", "disconnectAsync");
                return null;
            });
    }

    /**
     * Called when the BLE service is disconnected. Required by ServiceConnection.
     */
    @Override
    public void onServiceDisconnected(ComponentName name) {
        // No specific action needed on service disconnection
    }

    /**
     * Checks if the battery status has been successfully read.
     *
     * @return True if the battery status is available, false otherwise
     */
    public boolean checkBattery() {
        return text != null;
    }

    /**
     * Returns the battery status as a string.
     *
     * @return Battery status (e.g., "85%") or null if not available
     */
    public String getText() {
        return text;
    }

    /**
     * Runnable implementation: Connects to the device, waits for the battery data,
     * and notifies the callback listener when the operation is complete.
     */
    @Override
    public void run() {
        connectDevice();

        try {
            // Wait for the battery data to be read
            latch.await();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        // Notify the listener if the battery data is available
        if (onCompleteListener != null && text != null) {
            onCompleteListener.onComplete(text);
        }
    }
}