package com.example.runthebusiness.data.local.entity
import androidx.room.*

@Entity(tableName = "employees",
    foreignKeys = [ForeignKey(entity = BusinessLocationEntity::class, parentColumns = ["id"], childColumns = ["locationId"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index("locationId")])
data class EmployeeEntity(
    @PrimaryKey val id: String, val locationId: String, val name: String, val role: String,
    val monthlyGrossSalaryCents: Long, val weeklyWorkingHours: Double,
    val contractType: String, val payrollProfileId: String, val productivity: Double, val isActive: Boolean
)
