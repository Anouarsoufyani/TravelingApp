package com.example.travelshare.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.example.travelshare.data.models.GroupMember;

import java.util.List;

@Dao
public interface GroupMemberDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    void joinGroup(GroupMember member);

    @Query("DELETE FROM group_members WHERE groupId = :groupId AND userId = :userId")
    void leaveGroup(long groupId, long userId);

    @Query("DELETE FROM group_members WHERE groupId = :groupId")
    void deleteMembersForGroup(long groupId);

    @Query("UPDATE group_members SET status = 'MEMBER' WHERE groupId = :groupId AND userId = :userId")
    void acceptRequest(long groupId, long userId);

    @Query("SELECT groupId FROM group_members WHERE userId = :userId AND status = 'MEMBER'")
    LiveData<List<Long>> getJoinedGroupIds(long userId);

    @Query("SELECT * FROM group_members WHERE groupId = :groupId AND status = 'PENDING'")
    LiveData<List<GroupMember>> getPendingForGroup(long groupId);

    @Query("SELECT COUNT(*) FROM group_members WHERE groupId = :groupId AND status = 'PENDING'")
    LiveData<Integer> getPendingCountForGroup(long groupId);

    @Query("SELECT * FROM group_members WHERE userId = :userId AND groupId = :groupId LIMIT 1")
    GroupMember getMembership(long userId, long groupId);

    @Query("SELECT COUNT(*) FROM group_members WHERE groupId = :groupId AND userName = :userName")
    int countByUserName(long groupId, String userName);

    @Query("SELECT * FROM group_members WHERE groupId = :groupId AND userName = :userName LIMIT 1")
    GroupMember getMemberByUserName(long groupId, String userName);

    @Query("UPDATE group_members SET status = 'MEMBER' WHERE groupId = :groupId AND userName = :userName")
    void acceptRequestByUserName(long groupId, String userName);
}
