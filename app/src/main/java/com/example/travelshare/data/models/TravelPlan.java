package com.example.travelshare.data.models;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "travel_plans")
public class TravelPlan {
    @PrimaryKey(autoGenerate = true)
    public long id;
    public long userId;
    public String city;
    public String type;
    public String activities;
    public int budgetEur;
    public int durationHours;
    public String effort;
    public boolean liked;
    public boolean saved;
    public String date;
    public String requiredPlaces;
    public String weatherTolerances;
}
