package com.example.travelshare.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.example.travelshare.data.models.TravelPlan;

import java.util.List;

@Dao
public interface TravelPlanDao {
    @Insert
    long insertPlan(TravelPlan plan);

    @Update
    void updatePlan(TravelPlan plan);

    @Query("SELECT * FROM travel_plans WHERE userId = :userId ORDER BY id DESC")
    LiveData<List<TravelPlan>> getPlansForUser(long userId);

    @Query("SELECT * FROM travel_plans WHERE userId = :userId AND saved = 1 ORDER BY id DESC")
    LiveData<List<TravelPlan>> getSavedPlansForUser(long userId);

    @Query("SELECT * FROM travel_plans WHERE id = :planId LIMIT 1")
    TravelPlan getPlanById(long planId);

    @Query("DELETE FROM travel_plans WHERE id = :planId")
    void deletePlan(long planId);

    @Query("DELETE FROM travel_plans WHERE userId = :userId AND saved = 0")
    void deleteAllUnsavedForUser(long userId);
}
