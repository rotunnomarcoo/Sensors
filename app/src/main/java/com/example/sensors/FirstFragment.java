/**
 * FirstFragment - Main navigation fragment for the MetaWear Sensors application
 * 
 * This fragment serves as the primary user interface screen for the sensors application,
 * providing the main navigation and control interface for MetaWear device management.
 * It acts as a container for the core functionality while maintaining clean separation
 * between UI components and business logic.
 * 
 * Key Responsibilities:
 * - Provides the main UI layout and navigation structure
 * - Hosts references to shared UI components (scanner button, table rows)
 * - Manages fragment lifecycle and view binding
 * - Integrates with navigation component for screen transitions
 * - Serves as a bridge between MainActivity and navigation flow
 * 
 * Architecture Integration:
 * - Uses Android View Binding for type-safe view access
 * - Implements Fragment best practices for lifecycle management
 * - Maintains static references for cross-component communication
 * - Integrates with Navigation Component for fragment transitions
 * 
 * Static References:
 * The fragment maintains static references to UI components that need to be
 * accessed from other parts of the application, particularly MainActivity.
 * This design allows for direct control of UI elements without complex
 * callback chains or event systems.
 * 
 * @author MetaWear Sensors App
 * @version 1.0
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
 * FirstFragment extends Fragment to provide the main application interface
 * 
 * This fragment implements the primary user interface for device management,
 * containing the main layout elements and serving as the entry point for
 * user interactions with the MetaWear sensor system.
 * 
 * Design Pattern:
 * - Uses View Binding for safe and efficient view access
 * - Maintains static references for inter-component communication
 * - Follows Android Fragment lifecycle best practices
 * - Provides clean separation between UI and business logic
 */
public class FirstFragment extends Fragment {

    // View binding instance for type-safe view access
    private FragmentFirstBinding binding;

    // Static UI component references for cross-component access
    // These references allow MainActivity to directly control UI elements
    // without complex callback mechanisms or event broadcasting
    
    /**
     * Static reference to the device scanner button
     * 
     * This button is used throughout the application for initiating
     * Bluetooth device discovery. The static reference allows MainActivity
     * to enable/disable the button based on application state.
     */
    static public Button scanner;

    /**
     * Static reference to the first table row element
     * 
     * This reference may be used for dynamic layout manipulation
     * or for accessing table row elements from other components.
     */
    static public TableRow tableRowFirst;

    /**
     * Creates and returns the view hierarchy associated with this fragment
     * 
     * This method is called by the Android framework when the fragment's
     * UI needs to be created. It uses View Binding to inflate the layout
     * safely and efficiently.
     * 
     * @param inflater LayoutInflater for creating views from XML
     * @param container Parent view that will contain this fragment
     * @param savedInstanceState Previous state data (if any)
     * @return Root view of the fragment's layout hierarchy
     */
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState
    ) {
        // Inflate the layout using ViewBinding for type safety
        binding = FragmentFirstBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    /**
     * Called immediately after the fragment's view has been created
     * 
     * This method is invoked after onCreateView() returns, providing
     * an opportunity to perform additional setup that requires the
     * view hierarchy to be fully initialized.
     * 
     * @param view The root view returned by onCreateView()
     * @param savedInstanceState Previous state data (if any)
     */
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        // Additional view setup can be performed here
        // For example: setting up click listeners, configuring adapters, etc.
    }

    /**
     * Called when the fragment's view is being destroyed
     * 
     * This method is responsible for cleaning up resources and avoiding
     * memory leaks by releasing references to the view binding and any
     * other view-related resources.
     * 
     * Best Practice: Always set binding to null to prevent memory leaks
     * and ensure that the view hierarchy can be properly garbage collected.
     */
    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // Release view binding reference to prevent memory leaks
        binding = null;
    }

}