package com.example.travelshare.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import com.example.travelshare.data.models.Group;
import java.util.List;

@Dao
public interface GroupDao {

    @Insert
    long insertGroup(Group group);

    @Query("SELECT * FROM `groups` ORDER BY id DESC")
    LiveData<List<Group>> getAllGroups();

    @Query("SELECT * FROM `groups` WHERE id = :groupId LIMIT 1")
    Group getGroupById(long groupId);

    @Query("SELECT * FROM `groups` WHERE name LIKE '%' || :query || '%' ORDER BY id DESC")
    LiveData<List<Group>> searchGroups(String query);

    /** Groupes où l'utilisateur est créateur OU membre accepté */
    @Query("SELECT DISTINCT g.* FROM `groups` g " +
           "LEFT JOIN group_members gm ON g.id = gm.groupId " +
           "WHERE g.creatorId = :userId OR (gm.userId = :userId AND gm.status = 'MEMBER') " +
           "ORDER BY g.id DESC")
    LiveData<List<Group>> getGroupsForUser(long userId);

    @Query("SELECT DISTINCT g.* FROM `groups` g " +
           "LEFT JOIN group_members gm ON g.id = gm.groupId " +
           "WHERE g.creatorId = :userId OR (gm.userId = :userId AND gm.status = 'MEMBER') " +
           "ORDER BY g.id DESC")
    List<Group> getGroupsForUserSync(long userId);
}
