package com.example.travelshare.data.models;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "reports")
public class Report {
    @PrimaryKey(autoGenerate = true)
    public long id;
    public long photoId;
    public long userId;
    public String date;
}
