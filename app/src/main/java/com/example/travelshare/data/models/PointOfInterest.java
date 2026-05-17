package com.example.travelshare.data.models;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "points_of_interest")
public class PointOfInterest {
    @PrimaryKey(autoGenerate = true)
    public long id;

    public String name;
    public String category;
    public String description;

    public double latitude;
    public double longitude;

    public int estimatedCost;
    public int estimatedDuration;
    public int effortLevel;

    public boolean isIndoor;
    public boolean needsAC;
}