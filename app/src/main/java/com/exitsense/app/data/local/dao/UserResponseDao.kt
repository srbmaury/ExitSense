package com.exitsense.app.data.local.dao

import androidx.room.*
import com.exitsense.app.data.local.entities.UserResponseEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UserResponseDao {

    @Insert
    suspend fun insertResponse(response: UserResponseEntity): Long

    @Query("SELECT * FROM user_responses WHERE itemId = :itemId ORDER BY respondedAt DESC")
    fun getResponsesForItem(itemId: Long): Flow<List<UserResponseEntity>>

    @Query("""
        SELECT * FROM user_responses
        WHERE itemId = :itemId AND profileId = :profileId
        ORDER BY respondedAt DESC LIMIT 30
    """)
    suspend fun getRecentResponsesForItem(itemId: Long, profileId: Long): List<UserResponseEntity>

    @Query("""
        SELECT CAST(SUM(CASE WHEN wasConfirmed = 1 THEN 1 ELSE 0 END) AS REAL) / COUNT(*)
        FROM user_responses
        WHERE itemId = :itemId AND profileId = :profileId
    """)
    suspend fun getConfirmationRate(itemId: Long, profileId: Long): Float?

    @Query("SELECT DISTINCT itemId FROM user_responses WHERE profileId = :profileId")
    suspend fun getDistinctItemIds(profileId: Long): List<Long>
}
