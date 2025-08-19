package com.example.sensors;

import com.mbientlab.metawear.module.Led;

// Enum representing possible tracker LED colors and their index values
public enum ledEnum {
    WHITE(0),   // 0: Not assigned / disconnected
    RED(1),     // 1: Red color
    GREEN(2),   // 2: Green color
    BLUE(3),    // 3: Blue color
    YELLOW(4);  // 4: Yellow color

    // Integer index associated with each color
    final int index;

    // Constructor to assign index to each enum constant
    ledEnum(int index){
        this.index = index;
    }

    // Getter for the index value
    public int getIndex() {
        return index;
    }

    // Returns the ledEnum corresponding to a given index, or WHITE if not found
    public static ledEnum fromNumber(int number) {
        for (ledEnum color : ledEnum.values()) {
            if (color.getIndex() == number) {
                return color;
            }
        }
        return WHITE; // Default to WHITE if no match
    }
}
