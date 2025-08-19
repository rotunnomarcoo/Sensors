package com.example.sensors;

import android.util.Log;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class SaveStruct implements Serializable {
    // List of MAC addresses for the sensors
    private ArrayList<String> macAddresses = new ArrayList<>();
    // Indicates if logging is active
    private Boolean isLogging;
    // Start time for logging (in nanoseconds)
    private long start;
    // Total recorded time (in nanoseconds)
    private long recordTime;
    // Array indicating if each device is ready (true) or not (false)
    private boolean[] checkDevices;
    // Array of user-friendly names for each device
    private String[] names;
    // Array of color assignments for each device (index matches macAddresses)
    private int[] colors;
    // Array of position assignments for each device
    private String[] positions; // New: positions for each device
    // Array of battery level assignments for each device
    private String[] batteries; // New: battery levels for each device
    // Map to count taps for each MAC address
    private final Map<String, Integer> tapCounts = new HashMap<>();

    // Constructor with parameters
    public SaveStruct(ArrayList<String> macAddresses, Boolean isLogging) {
        this.macAddresses = macAddresses;
        this.isLogging = isLogging;
        this.start = System.nanoTime();
        initializeArrays();
    }

    // Default constructor
    public SaveStruct() {
        this.macAddresses = new ArrayList<>();
        this.isLogging = false;
        // When logging starts, save the current time
        this.start = System.nanoTime();
        this.recordTime = 0;
        initializeArrays();
    }
    
    private void initializeArrays() {
        // Initialize checkDevices for up to 4 devices
        this.checkDevices = new boolean[]{false, false, false, false};
        // Initialize names for up to 4 devices
        this.names = new String[]{"", "", "", ""};
        // Initialize colors (0 means unassigned/WHITE by your enum)
        this.colors = new int[]{0, 0, 0, 0};
        // Initialize positions
        this.positions = new String[]{null, null, null, null};
        // Initialize batteries
        this.batteries = new String[]{"Unknown", "Unknown", "Unknown", "Unknown"};
    }

    // Set the user-friendly name for a device at a given index
    public void setName(String name, int index) {
        if (index >= 0 && index < 4 && index < names.length) {
            names[index] = name;
        }
    }

    // Get the user-friendly name for a device by MAC address
    public String getName(String macAddress) {
        int index = macAddresses.indexOf(macAddress);
        if (index >= 0 && index < names.length) {
            return names[index];
        }
        return null;
    }

    // Set the position for a device by MAC address
    public void setPosition(String macAddress, String position) {
        int index = macAddresses.indexOf(macAddress);
        if (index >= 0 && index < positions.length) {
            positions[index] = position;
        }
    }

    // Get the position for a device by MAC address
    public String getPosition(String macAddress) {
        int index = macAddresses.indexOf(macAddress);
        if (index >= 0 && index < positions.length) {
            return positions[index];
        }
        return null;
    }

    // Set all checkDevices to true (all devices ready)
    public void setCheckTrue() {
        checkDevices = new boolean[]{true, true, true, true};
    }

    // Get the logging state
    public Boolean getLogging() {
        return isLogging;
    }

    // Set the logging state and update timers accordingly
    public void setLogging(Boolean logging) {
        isLogging = logging;
        if (!logging) { // When stopping logging
            checkDevices = new boolean[]{false, false, false, false};
            recordTime += System.nanoTime() - start;
        } else { // When starting logging
            start = System.nanoTime();
        }
    }

    // Returns a formatted string of the total recorded time (days, hours, minutes, seconds)
    public String getRecordTimeString() {
        if (isLogging) {
            // Update the record time with the elapsed time since last start
            recordTime += System.nanoTime() - start;
            // Restart the counter
            start = System.nanoTime();
        }

        long nanosInSecond = 1_000_000_000L;
        long totalSeconds = recordTime / nanosInSecond;

        // Calculate days
        long days = totalSeconds / (24 * 60 * 60);
        long remainingSecondsAfterDays = totalSeconds % (24 * 60 * 60);

        // Calculate hours
        long hours = remainingSecondsAfterDays / (60 * 60);
        long remainingSecondsAfterHours = remainingSecondsAfterDays % (60 * 60);

        // Calculate minutes
        long minutes = remainingSecondsAfterHours / 60;

        // Calculate seconds
        long seconds = remainingSecondsAfterHours % 60;

        return days + "D " + hours + "H " + minutes + "M " + seconds + "S";
    }

    // Returns the number of days logged
    public long getDays() {
        if (isLogging) {
            recordTime += System.nanoTime() - start;
            start = System.nanoTime();
        }
        return (recordTime / 1_000_000_000L) / (24 * 60 * 60);
    }

    // Returns the total recorded time in nanoseconds
    public long getRecordTimeNanoSecond() {
        if (isLogging) {
            recordTime += System.nanoTime() - start;
            start = System.nanoTime();
        }
        return recordTime;
    }

    // Returns true if no MAC addresses are stored
    public boolean isEmpty() {
        return macAddresses.isEmpty();
    }

    // Returns true if the given MAC address is already stored
    public boolean contains(String address) {
        return macAddresses.contains(address);
    }

    // Adds a MAC address if there is room (max 4 devices)
    public boolean add(String address) {
        if (macAddresses.size() < 4) {
            return macAddresses.add(address);
        } else {
            return false;
        }
    }

    // Removes a MAC address and cleans up related data (color, name, checkDevice, position)
    public boolean remove(String macAddress) {
        int index = macAddresses.indexOf(macAddress);
        if (index != -1) {
            macAddresses.remove(index);
            
            // Shift all arrays to fill the gap
            for (int i = index; i < 3; i++) {
                if (colors != null && colors.length > i + 1) {
                    colors[i] = colors[i + 1];
                }
                if (names != null && names.length > i + 1) {
                    names[i] = names[i + 1];
                }
                if (positions != null && positions.length > i + 1) {
                    positions[i] = positions[i + 1];
                }
                if (batteries != null && batteries.length > i + 1) {
                    batteries[i] = batteries[i + 1];
                }
                if (checkDevices != null && checkDevices.length > i + 1) {
                    checkDevices[i] = checkDevices[i + 1];
                }
            }
            
            // Clear the last slot
            if (colors != null) colors[3] = 0;
            if (names != null) names[3] = "";
            if (positions != null) positions[3] = null;
            if (batteries != null) batteries[3] = "Unknown";
            if (checkDevices != null) checkDevices[3] = false;
            
            return true;
        }
        return false;
    }

    // Returns the number of stored MAC addresses
    public Integer size() {
        return macAddresses.size();
    }

    // Returns true if logging is active
    public boolean isLogging() {
        return isLogging;
    }

    // Returns true if the memory/time limit is reached (here: 3 days)
    public boolean fullMemory() {
        long maxTime = 3 * 24 * 60 * 60 * 1_000_000_000L;
        if (isLogging) {
            recordTime += System.nanoTime() - start;
            start = System.nanoTime();
        }
        return recordTime >= maxTime;
    }

    // Returns the color assigned to a MAC address, or WHITE if not assigned
    public ledEnum getColor(String macAddress) {
        int index = getIndex(macAddress);
        if (index >= 0 && index < colors.length) {
            return ledEnum.fromNumber(colors[index]);
        }
        return ledEnum.WHITE;
    }

    // Assigns a color to a MAC address
    public void setColors(String macAddress, int color) {
        int index = macAddresses.indexOf(macAddress);
        if (index == -1) {
            Log.e("SaveStruct Error", "MAC address not found: " + macAddress);
            return;
        }
        colors[index] = color;
        Log.d("SaveStruct", "Updated color for MAC address " + macAddress + " to " + color);
    }

    // Returns the index of a MAC address in the list
    public int getIndex(String macAddress) {
        return macAddresses.indexOf(macAddress);
    }

    // Sets the checkDevices state for a device at a given index
    public void setCheckDevice(boolean state, int index) {
        if (index >= 0 && index < checkDevices.length) {
            checkDevices[index] = state;
        }
    }

    // Returns true if all connected devices have assigned positions
    public boolean ready() {
        for (int i = 0; i < macAddresses.size() && i < checkDevices.length; i++) {
            if (!checkDevices[i]) {
                return false;
            }
        }
        return macAddresses.size() > 0; // At least one device must be connected
    }

    // Returns the list of MAC addresses
    public ArrayList<String> getMacAddresses() {
        return macAddresses;
    }

    // Gets the tap count for a MAC address
    public int getTapCount(String mac) {
        Integer count = tapCounts.get(mac);
        return count == null ? 0 : count;
    }

    // Sets the tap count for a MAC address
    public void setTapCount(String mac, int count) {
        tapCounts.put(mac, count);
    }

    // Gets the battery level for a MAC address
    public String getBattery(String macAddress) {
        int index = getIndex(macAddress);
        if (index >= 0 && index < batteries.length) {
            return batteries[index];
        }
        return "Unknown";
    }

    // Sets the battery level for a MAC address
    public void setBattery(String macAddress, String battery) {
        int index = getIndex(macAddress);
        if (index >= 0 && index < batteries.length) {
            batteries[index] = battery;
        }
    }

    // Get next available color in order (Red, Green, Blue, Yellow)
    public ledEnum getNextAvailableColor() {
        ledEnum[] colorOrder = {ledEnum.RED, ledEnum.GREEN, ledEnum.BLUE, ledEnum.YELLOW};
        
        for (ledEnum color : colorOrder) {
            boolean colorUsed = false;
            for (int i = 0; i < colors.length && i < macAddresses.size(); i++) {
                if (colors[i] == color.getIndex()) {
                    colorUsed = true;
                    break;
                }
            }
            if (!colorUsed) {
                return color;
            }
        }
        return ledEnum.WHITE; // No colors available
    }
}