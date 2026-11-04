package com.example.beiwang.data.dao

import androidx.room.*
import com.example.beiwang.data.entity.PurgedIdEntity

@Dao
interface PurgedIdDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(p: PurgedIdEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(list: List<PurgedIdEntity>)

    @Query("SELECT id FROM purged_ids WHERE type = :type")
    suspend fun getIdsByType(type: String): List<String>

    /**
     * 全量快照。用于「登录清库」场景：purged_ids 必须在清库后原样保留
     * （见 AppDatabase.clearAll 的注释），否则永久删除的记录会被服务端墓碑拉回。
     */
    @Query("SELECT * FROM purged_ids")
    suspend fun getAll(): List<PurgedIdEntity>

    /** 恢复备份时，被主动恢复的 id 须移出永久删除记录，避免推送时又被服务端真删。 */
    @Query("DELETE FROM purged_ids WHERE type = :type AND id IN (:ids)")
    suspend fun deleteByIds(type: String, ids: List<String>)
}
