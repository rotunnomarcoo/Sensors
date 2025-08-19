package com.example.sensors;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.mbientlab.metawear.MetaWearBoard;
import com.mbientlab.metawear.module.Led;

import java.util.List;

// Adapter for displaying sensor data in a RecyclerView
public class RecyclerViewAdapter extends RecyclerView.Adapter<RecyclerViewAdapter.ViewHolder> {

    // List holding all sensor data objects
    private final List<SensorData> sensorDataList;

    // Constructor: receives the list of sensor data
    public RecyclerViewAdapter(List<SensorData> sensorDataList) {
        this.sensorDataList = sensorDataList;
    }

    // Inflates the item layout and creates the ViewHolder
    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_row, parent, false);
        return new ViewHolder(view);
    }

    // Binds the sensor data to the ViewHolder's views
    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        SensorData data = sensorDataList.get(position);
        holder.textSensore.setText(data.getMacAddress());
        holder.textPosition.setText(data.getPosition());
        holder.textBatteria.setText(data.getBatteria());
        holder.textStato.setText(data.getStato());
    }

    // Returns the total number of items in the list
    @Override
    public int getItemCount() {
        return sensorDataList.size();
    }

    // ViewHolder class that holds references to the views for each item
    public static class ViewHolder extends RecyclerView.ViewHolder {
        private final TextView textSensore;   // MAC address
        private final TextView textPosition;  // Position/label
        private final TextView textBatteria;  // Battery level
        private final TextView textStato;     // Status

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            // Find views by their IDs in the item layout
            textSensore = itemView.findViewById(R.id.text_sensore);
            textPosition = itemView.findViewById(R.id.text_colore);
            textBatteria = itemView.findViewById(R.id.text_batteria);
            textStato = itemView.findViewById(R.id.text_stato);

            // Set a click listener for the entire item view
            itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    // Check if logging is active
                    if (MainActivity.savedMacAddresses.isLogging()) {
                        Toast.makeText(itemView.getContext(), "Cannot remove device while logging is active. Stop logging first.", Toast.LENGTH_SHORT).show();
                        return; // Prevent further action
                    }

                    String macAddress = (String) textSensore.getText();
                    MainActivity.removeDeviceAndCleanup(macAddress);
                    Toast.makeText(itemView.getContext(), macAddress, Toast.LENGTH_SHORT).show();
                }
            });
        }
    }
}
