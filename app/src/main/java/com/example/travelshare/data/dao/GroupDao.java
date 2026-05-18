package com.example.travelshare.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import com.example.travelshare.data.models.Group;
import java.util.List;

@Dao
public interface GroupDao {

    @Insert
    long insertGroup(Group group);

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    long insertGroupOrIgnore(Group group);

    @Query("SELECT COUNT(*) FROM `groups` WHERE name = :name")
    int countByName(String name);

    @Query("SELECT * FROM `groups` WHERE name = :name LIMIT 1")
    Group getGroupByName(String name);

    @Query("SELECT * FROM `groups` ORDER BY id DESC")
    LiveData<List<Group>> getAllGroups();

    @Query("SELECT * FROM `groups` WHERE id = :groupId LIMIT 1")
    Group getGroupById(long groupId);

    @Query("SELECT * FROM `groups` WHERE name LIKE '%' || :query || '%' ORDER BY id DESC")
    LiveData<List<Group>> searchGroups(String query);

    @Query("UPDATE `groups` SET name = :newName WHERE id = :groupId")
    void updateGroupName(long groupId, String newName);

    @Query("DELETE FROM `groups` WHERE id = :groupId")
    void deleteGroupById(long groupId);

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
