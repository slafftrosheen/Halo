package com.halo.app.data.knowledge

import androidx.room.*
import com.halo.app.data.everyday.CollectorSourceEntity

@Entity(tableName = "material_images", foreignKeys = [ForeignKey(entity = CollectorSourceEntity::class,
    parentColumns = ["id"], childColumns = ["sourceId"], onDelete = ForeignKey.CASCADE)], indices = [Index("sourceId")])
data class MaterialImageEntity(@PrimaryKey val id: String, val sourceId: String, val savedAt: Long, val bytes: Long)
