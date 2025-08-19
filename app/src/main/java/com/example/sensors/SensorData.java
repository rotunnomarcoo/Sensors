package com.example.sensors;

import androidx.annotation.Nullable;

// Class representing the data for a single sensor/tracker
public class SensorData {
    // MAC address of the sensor
    private final String macAddress;
    // Position or label for the sensor (e.g., "left arm", "right leg")
    private final String position;
    // Battery level as a string (could be percentage or voltage)
    private final String batteria;
    // Status of the sensor (e.g., "connected", "disconnected")
    private final String stato;

    // Constructor to initialize all fields
    public SensorData(String macAddress, String position, String batteria, String stato) {
        this.macAddress = macAddress;
        this.position = position;
        this.batteria = batteria;
        this.stato = stato;
    }

    // Override equals to compare SensorData objects by MAC address,
    // or to compare directly with a String MAC address
    @Override
    public boolean equals(@Nullable Object obj) {
        if(obj instanceof SensorData){
            return ((SensorData) obj).macAddress.equals(this.macAddress);
        }else {
            if(obj instanceof String){
                return ((String) obj).equals(this.macAddress);
            }else
                return false;
        }
    }

    // Getter for MAC address
    public String getMacAddress() { return macAddress; }
    // Getter for position
    public String getPosition() { return position; }
    // Getter for battery level
    public String getBatteria() { return batteria; }
    // Getter for status
    public String getStato() { return stato; }
}
