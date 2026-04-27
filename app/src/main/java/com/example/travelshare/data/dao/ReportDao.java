package com.example.travelshare.data.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import com.example.travelshare.data.models.Report;

@Dao
public interface ReportDao {
    @Insert
    long insertReport(Report report);
}
