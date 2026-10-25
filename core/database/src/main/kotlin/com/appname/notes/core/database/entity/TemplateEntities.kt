package com.appname.notes.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** A template the user made (schema v8, owner brief §4). The design is stored as structured JSON, never an image. */
@Entity(tableName = "custom_templates")
data class CustomTemplateEntity(
    @PrimaryKey val id: String,
    val name: String,
    val layoutJson: String,
    val createdAt: Long,
    val updatedAt: Long,
)
