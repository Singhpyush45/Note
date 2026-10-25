package com.appname.notes.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.appname.notes.core.database.entity.CustomTemplateEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TemplateDao {
    @Query("SELECT * FROM custom_templates ORDER BY createdAt, id")
    fun observeAll(): Flow<List<CustomTemplateEntity>>

    @Query("SELECT * FROM custom_templates WHERE id = :id")
    suspend fun get(id: String): CustomTemplateEntity?

    @Insert
    suspend fun insert(template: CustomTemplateEntity)

    @Query("UPDATE custom_templates SET name = :name, layoutJson = :layoutJson, updatedAt = :now WHERE id = :id")
    suspend fun update(id: String, name: String, layoutJson: String, now: Long): Int

    @Query("UPDATE custom_templates SET name = :name, updatedAt = :now WHERE id = :id")
    suspend fun rename(id: String, name: String, now: Long): Int

    @Query("DELETE FROM custom_templates WHERE id = :id")
    suspend fun delete(id: String): Int
}
