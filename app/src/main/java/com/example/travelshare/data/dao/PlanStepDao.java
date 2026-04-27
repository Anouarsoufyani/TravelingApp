package com.example.travelshare.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import com.example.travelshare.data.models.PlanStep;

import java.util.List;

@Dao
public interface PlanStepDao {
    @Insert
    void insertStep(PlanStep step);

    @Query("SELECT * FROM plan_steps WHERE planId = :planId ORDER BY stepOrder ASC")
    LiveData<List<PlanStep>> getStepsForPlan(long planId);

    @Query("SELECT * FROM plan_steps WHERE planId = :planId ORDER BY stepOrder ASC")
    List<PlanStep> getStepsForPlanSync(long planId);

    @Query("DELETE FROM plan_steps WHERE planId = :planId")
    void deleteStepsForPlan(long planId);
}
