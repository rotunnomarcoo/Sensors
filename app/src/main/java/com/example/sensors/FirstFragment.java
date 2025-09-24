/**
 * FirstFragment - Main navigation fragment for the MetaWear Sensors application.
 *
 * This fragment provides the primary user interface for managing MetaWear devices.
 * It handles navigation, layout setup, and interaction with shared UI components.
 */

package com.example.sensors;

import static com.example.sensors.MainActivity.SCAN_DEVICES;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TableRow;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.example.sensors.databinding.FragmentFirstBinding;

/**
 * Extends Fragment to implement the main application interface.
 *
 * Responsibilities:
 * - Hosts the main layout and navigation structure.
 * - Provides static references to shared UI components.
 * - Manages the fragment lifecycle and view binding.
 */
public class FirstFragment extends Fragment {

    // View binding instance for accessing views safely
    private FragmentFirstBinding binding;

    // Static references for shared UI components

    /**
     * Reference to the device scanner button.
     *
     * Allows MainActivity to control the button state for initiating Bluetooth scans.
     */
    static public Button scanner;

    /**
     * Reference to the first table row element.
     *
     * Used for dynamic layout updates or accessing table rows from other components.
     */
    static public TableRow tableRowFirst;

    /**
     * Creates and returns the view hierarchy for this fragment.
     *
     * @param inflater Inflates the layout XML.
     * @param container Parent view for the fragment.
     * @param savedInstanceState Previous state data, if available.
     * @return Root view of the fragment's layout.
     */
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState
    ) {
        // Inflate the layout using View Binding
        binding = FragmentFirstBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    /**
     * Called after the fragment's view is created.
     *
     * Use this method to set up click listeners or configure adapters.
     *
     * @param view Root view returned by onCreateView().
     * @param savedInstanceState Previous state data, if available.
     */
    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        // Additional setup for views can be added here
    }

    /**
     * Cleans up resources when the fragment's view is destroyed.
     *
     * Best Practice: Release the binding reference to prevent memory leaks.
     */
    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null; // Release binding reference
    }
}