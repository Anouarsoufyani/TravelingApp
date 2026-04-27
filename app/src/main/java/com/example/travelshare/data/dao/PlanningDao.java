package com.example.travelshare.data.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;
import com.example.travelshare.data.models.Planning;

@Dao
public interface PlanningDao {
    @Insert
    long insertPlanning(Planning planning);

    @Query("SELECT * FROM plannings WHERE userId = :userId ORDER BY id DESC LIMIT 1")
    Planning getDernierPlanning(long userId);

    @Update
    int updatePlanning(Planning planning);
}
