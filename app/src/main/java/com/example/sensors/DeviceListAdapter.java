/**
 * DeviceListAdapter - Custom adapter for displaying Bluetooth device scan results.
 *
 * This adapter manages the display of discovered Bluetooth Low Energy (BLE) devices
 * in a ListView during the scanning process. It handles device information display,
 * selection state, and integration with the parent activity for callbacks.
 */

package com.example.sensors;

import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * Extends BaseAdapter to provide custom ListView functionality for BLE devices.
 *
 * Responsibilities:
 * - Maintain a list of discovered devices.
 * - Handle device selection and visual indicators.
 * - Optimize performance using the ViewHolder pattern.
 */
public class DeviceListAdapter extends BaseAdapter {

    // Core adapter components
    private Context context;                    // Application context for resources
    private List<DeviceItem> devices;          // List of discovered devices
    private LayoutInflater inflater;           // Inflates views for the ListView
    private ScannerActivity scannerActivity;   // Parent activity for callbacks

    /**
     * Represents a discovered Bluetooth device.
     *
     * Contains device metadata such as name, MAC address, and signal strength.
     */
    public static class DeviceItem {
        public BluetoothDevice device;  // System Bluetooth device object
        public String name;             // Device name (may be null)
        public String address;          // MAC address (unique identifier)
        public int rssi;                // Signal strength in dBm

        /**
         * Initializes a new DeviceItem.
         *
         * @param device Bluetooth device object.
         * @param name Device name (may be null).
         * @param address MAC address of the device.
         * @param rssi Signal strength in dBm.
         */
        public DeviceItem(BluetoothDevice device, String name, String address, int rssi) {
            this.device = device;
            this.name = name;
            this.address = address;
            this.rssi = rssi;
        }
    }

    /**
     * Initializes the adapter with the application context and parent activity.
     *
     * @param context Application context for accessing resources.
     * @param scannerActivity Parent activity for device selection callbacks.
     */
    public DeviceListAdapter(Context context, ScannerActivity scannerActivity) {
        this.context = context;
        this.scannerActivity = scannerActivity;
        this.devices = new ArrayList<>();
        this.inflater = LayoutInflater.from(context);
    }

    /**
     * Returns the total number of devices in the list.
     *
     * @return Number of devices.
     */
    @Override
    public int getCount() {
        return devices.size();
    }

    /**
     * Retrieves the device at the specified position.
     *
     * @param position Index of the device.
     * @return DeviceItem at the specified position.
     */
    @Override
    public Object getItem(int position) {
        return devices.get(position);
    }

    /**
     * Returns the unique identifier for the device at the specified position.
     *
     * @param position Index of the device.
     * @return Position as the unique identifier.
     */
    @Override
    public long getItemId(int position) {
        return position;
    }

    /**
     * Creates and returns a view for the device at the specified position.
     *
     * Implements the ViewHolder pattern for efficient view recycling.
     *
     * @param position Position of the device in the list.
     * @param convertView Recycled view (if available).
     * @param parent Parent ViewGroup.
     * @return Configured view displaying device information.
     */
    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;

        if (convertView == null) {
            convertView = inflater.inflate(R.layout.device_list_item, parent, false);
            holder = new ViewHolder();
            holder.deviceName = convertView.findViewById(R.id.device_name);
            holder.deviceAddress = convertView.findViewById(R.id.device_address);
            holder.deviceRssi = convertView.findViewById(R.id.device_rssi);
            holder.selectedCheck = convertView.findViewById(R.id.device_selected_check);
            holder.connectedCheck = convertView.findViewById(R.id.device_connected_check);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        DeviceItem deviceItem = devices.get(position);
        holder.deviceName.setText(deviceItem.name != null ? deviceItem.name : "Unknown Device");
        holder.deviceAddress.setText(deviceItem.address);
        holder.deviceRssi.setText("RSSI: " + deviceItem.rssi + " dBm");

        boolean isSelected = scannerActivity.isDeviceSelected(deviceItem.address);
        holder.connectedCheck.setVisibility(View.GONE);
        holder.selectedCheck.setVisibility(isSelected ? View.VISIBLE : View.GONE);

        convertView.setOnClickListener(v -> {
            if (scannerActivity != null) {
                scannerActivity.onDeviceSelected(deviceItem.device);
            }
        });

        return convertView;
    }

    /**
     * Adds or updates a device in the list.
     *
     * Filters out connected devices and updates RSSI for existing devices.
     *
     * @param device Bluetooth device object.
     * @param name Device name (may be null).
     * @param address MAC address of the device.
     * @param rssi Signal strength in dBm.
     */
    public void addDevice(BluetoothDevice device, String name, String address, int rssi) {
        if (scannerActivity != null && scannerActivity.isDeviceConnected(address)) {
            return;
        }

        for (DeviceItem existingDevice : devices) {
            if (existingDevice.address.equals(address)) {
                existingDevice.rssi = rssi;
                notifyDataSetChanged();
                return;
            }
        }

        devices.add(new DeviceItem(device, name, address, rssi));
        notifyDataSetChanged();
    }

    /**
     * Clears all devices from the list.
     */
    public void clear() {
        devices.clear();
        notifyDataSetChanged();
    }

    /**
     * Removes connected devices from the list.
     */
    public void refreshDeviceList() {
        if (scannerActivity != null) {
            devices.removeIf(deviceItem -> scannerActivity.isDeviceConnected(deviceItem.address));
            notifyDataSetChanged();
        }
    }

    /**
     * ViewHolder for caching view references.
     */
    private static class ViewHolder {
        TextView deviceName;
        TextView deviceAddress;
        TextView deviceRssi;
        ImageView selectedCheck;
        ImageView connectedCheck;
    }
}
