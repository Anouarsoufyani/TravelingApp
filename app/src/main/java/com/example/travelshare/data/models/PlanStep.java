package com.example.travelshare.data.models;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "plan_steps")
public class PlanStep {
    @PrimaryKey(autoGenerate = true)
    public long id;
    public long planId;
    public int stepOrder;
    public String name;
    public String type;
    public String timeSlot;
    public int durationMin;
    public int costEur;
    public String description;
    public double lat;
    public double lng;
    public String openingHours;
    public String address;
}
