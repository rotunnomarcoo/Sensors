/**
 * DeviceListAdapter - Custom adapter for displaying Bluetooth device scan results
 * 
 * This adapter manages the display of discovered Bluetooth Low Energy (BLE) devices
 * in a ListView during the device scanning process. It handles:
 * - Device information display (name, MAC address, signal strength)
 * - Selection state management for multiple device selection
 * - Visual feedback for selected/unselected devices
 * - Integration with ScannerActivity for device selection callbacks
 * 
 * The adapter uses a custom layout to show device details and provides
 * click handling for device selection/deselection.
 * 
 * @author MetaWear Sensors App
 * @version 1.0
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
 * DeviceListAdapter extends BaseAdapter to provide custom ListView functionality
 * for displaying and managing Bluetooth device scan results.
 * 
 * Key responsibilities:
 * - Maintain list of discovered BLE devices with their metadata
 * - Handle device selection state and visual indicators
 * - Provide smooth scrolling performance through ViewHolder pattern
 * - Interface with ScannerActivity for selection callbacks
 */
public class DeviceListAdapter extends BaseAdapter {
    
    // Core adapter components
    private Context context;                    // Application context for resource access
    private List<DeviceItem> devices;          // List of discovered devices
    private LayoutInflater inflater;           // Layout inflater for view creation
    private ScannerActivity scannerActivity;   // Parent activity for selection callbacks

    /**
     * DeviceItem - Data container for Bluetooth device information
     * 
     * Encapsulates all relevant information about a discovered BLE device:
     * - BluetoothDevice object for system-level device operations
     * - Display name (may be null for unnamed devices)
     * - MAC address for unique device identification
     * - RSSI value for signal strength indication
     */
    public static class DeviceItem {
        public BluetoothDevice device;  // System Bluetooth device object
        public String name;             // Human-readable device name (may be null)
        public String address;          // MAC address (unique identifier)
        public int rssi;               // Received Signal Strength Indicator

        /**
         * Constructor for creating a new DeviceItem
         * 
         * @param device Bluetooth device object from system scan
         * @param name Device name (may be null if not advertised)
         * @param address MAC address of the device
         * @param rssi Signal strength in dBm (typically negative value)
         */
        public DeviceItem(BluetoothDevice device, String name, String address, int rssi) {
            this.device = device;
            this.name = name;
            this.address = address;
            this.rssi = rssi;
        }
    }

    /**
     * Constructor for DeviceListAdapter
     * 
     * Initializes the adapter with required dependencies and empty device list.
     * Sets up the LayoutInflater for efficient view creation.
     * 
     * @param context Application context for accessing resources
     * @param scannerActivity Parent activity for device selection callbacks
     */
    public DeviceListAdapter(Context context, ScannerActivity scannerActivity) {
        this.context = context;
        this.scannerActivity = scannerActivity;
        this.devices = new ArrayList<>();
        this.inflater = LayoutInflater.from(context);
    }

    /**
     * Returns the total number of devices in the adapter
     * Required by BaseAdapter interface
     * 
     * @return Number of devices currently in the list
     */
    @Override
    public int getCount() {
        return devices.size();
    }

    /**
     * Returns the device item at the specified position
     * Required by BaseAdapter interface
     * 
     * @param position Index of the item to retrieve
     * @return DeviceItem at the specified position
     */
    @Override
    public Object getItem(int position) {
        return devices.get(position);
    }

    /**
     * Returns the unique identifier for the item at the specified position
     * Required by BaseAdapter interface
     * 
     * @param position Index of the item
     * @return Unique identifier (using position as ID)
     */
    @Override
    public long getItemId(int position) {
        return position;
    }

    /**
     * Creates and returns a view for the item at the specified position
     * This method implements the ViewHolder pattern for optimal performance
     * 
     * Key features:
     * - Recycles views for smooth scrolling performance
     * - Displays device name, MAC address, and signal strength
     * - Shows selection state with visual indicators
     * - Handles click events for device selection/deselection
     * 
     * @param position Position of the item within the adapter
     * @param convertView Recycled view (may be null for new views)
     * @param parent Parent ViewGroup that will contain the returned view
     * @return Configured view displaying the device information
     */
    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;

        // Implement ViewHolder pattern for performance optimization
        if (convertView == null) {
            // Create new view from layout
            convertView = inflater.inflate(R.layout.device_list_item, parent, false);
            holder = new ViewHolder();
            
            // Initialize ViewHolder with view references
            holder.deviceName = convertView.findViewById(R.id.device_name);
            holder.deviceAddress = convertView.findViewById(R.id.device_address);
            holder.deviceRssi = convertView.findViewById(R.id.device_rssi);
            holder.selectedCheck = convertView.findViewById(R.id.device_selected_check);
            holder.connectedCheck = convertView.findViewById(R.id.device_connected_check);
            
            // Store ViewHolder in view tag for reuse
            convertView.setTag(holder);
        } else {
            // Reuse existing view and retrieve ViewHolder
            holder = (ViewHolder) convertView.getTag();
        }

        // Get device data for this position
        DeviceItem deviceItem = devices.get(position);
        
        // Populate device information in the UI elements
        // Handle null device names gracefully
        holder.deviceName.setText(deviceItem.name != null ? deviceItem.name : "Unknown Device");
        holder.deviceAddress.setText(deviceItem.address);
        holder.deviceRssi.setText("RSSI: " + deviceItem.rssi + " dBm");

        // Configure selection and connection state indicators
        // Check if this device is currently selected by the user
        boolean isSelected = scannerActivity.isDeviceSelected(deviceItem.address);
        
        // Hide connected indicator since connected devices are filtered out during scanning
        holder.connectedCheck.setVisibility(View.GONE);
        
        // Show/hide selection checkmark based on current selection state
        holder.selectedCheck.setVisibility(isSelected ? View.VISIBLE : View.GONE);

        // Set up click listener for device selection/deselection
        convertView.setOnClickListener(v -> {
            if (scannerActivity != null) {
                // Delegate click handling to the parent ScannerActivity
                scannerActivity.onDeviceSelected(deviceItem.device);
            }
        });

        return convertView;
    }

    /**
     * Adds a new device to the adapter or updates existing device information
     * 
     * This method handles device discovery during BLE scanning:
     * - Filters out already connected devices to avoid duplicates
     * - Updates RSSI for existing devices (signal strength changes)
     * - Adds new devices to the scan results list
     * - Notifies the ListView of data changes for UI updates
     * 
     * @param device BluetoothDevice object from the system scan
     * @param name Advertised device name (may be null)
     * @param address MAC address of the discovered device
     * @param rssi Signal strength in dBm
     */
    public void addDevice(BluetoothDevice device, String name, String address, int rssi) {
        // Skip devices that are already connected to avoid confusion
        if (scannerActivity != null && scannerActivity.isDeviceConnected(address)) {
            return; // Don't add already connected devices to the scan list
        }
        
        // Check if device already exists in the scan list
        for (DeviceItem existingDevice : devices) {
            if (existingDevice.address.equals(address)) {
                // Update RSSI if device already exists (signal strength may change)
                existingDevice.rssi = rssi;
                notifyDataSetChanged(); // Refresh the ListView
                return;
            }
        }
        
        // Add new device to the list
        devices.add(new DeviceItem(device, name, address, rssi));
        notifyDataSetChanged(); // Notify ListView of data change
    }

    /**
     * Clears all devices from the adapter
     * 
     * This method removes all discovered devices from the list and updates the UI.
     * Typically called when starting a new scan or resetting the scanner.
     */
    public void clear() {
        devices.clear();
        notifyDataSetChanged();
    }

    /**
     * Refreshes the device list by removing connected devices
     * 
     * This method filters out devices that have been connected since the scan started.
     * This prevents users from seeing devices in the scan list that are already
     * connected to the application, reducing confusion.
     */
    public void refreshDeviceList() {
        // Remove any devices that are now connected from the scan list
        if (scannerActivity != null) {
            devices.removeIf(deviceItem -> scannerActivity.isDeviceConnected(deviceItem.address));
            notifyDataSetChanged();
        }
    }

    /**
     * ViewHolder class for efficient view recycling
     * 
     * This static inner class implements the ViewHolder pattern to improve
     * ListView performance by caching view references. This eliminates the need
     * to call findViewById() repeatedly during scrolling.
     * 
     * Contains references to all UI elements in the device list item layout:
     * - Device name TextView
     * - MAC address TextView  
     * - Signal strength (RSSI) TextView
     * - Selection state indicator ImageView
     * - Connection state indicator ImageView
     */
    private static class ViewHolder {
        TextView deviceName;        // Displays device name or "Unknown Device"
        TextView deviceAddress;     // Shows MAC address for identification
        TextView deviceRssi;        // Shows signal strength in dBm
        ImageView selectedCheck;    // Visual indicator for device selection state
        ImageView connectedCheck;   // Visual indicator for device connection state
    }
}
