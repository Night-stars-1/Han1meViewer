package com.yenaly.han1meviewer.logic.dao.download

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.yenaly.han1meviewer.logic.entity.download.HUpdateEntity
import com.yenaly.han1meviewer.logic.state.DownloadState
import kotlinx.coroutines.flow.Flow

@Dao
abstract class HUpdateDao {

    @Query("SELECT * FROM HUpdateEntity WHERE state != ${DownloadState.Mask.FINISHED} AND id = 1")
    abstract fun loadUpdating(): Flow<HUpdateEntity>

    @Query("DELETE FROM HUpdateEntity WHERE id = 1")
    abstract suspend fun delete()

    @Insert(onConflict = OnConflictStrategy.Companion.REPLACE)
    abstract suspend fun insert(entity: HUpdateEntity)

    @Update(onConflict = OnConflictStrategy.Companion.REPLACE)
    abstract suspend fun update(entity: HUpdateEntity): Int

    @Query("SELECT * FROM HUpdateEntity WHERE id = 1")
    abstract suspend fun get(): HUpdateEntity?

    @Query("UPDATE HUpdateEntity SET state = :state WHERE id = 1")
    abstract suspend fun updateState(state: DownloadState)
}