package com.example.travelshare.data.models;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "plannings")
public class Planning {
    @PrimaryKey(autoGenerate = true)
    public int id;
    
    public long userId;
    public String creneau8_10;
    public String creneau10_12;
    public String creneau14_16;
    public String creneau16_18;
}
