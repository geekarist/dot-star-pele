package me.cpele.dotstarpele

import androidx.room.*

enum class NoteEntity {
    Dislike
}

@Database(
    entities = [NameEntity::class, RatingEntity::class], version = 2, exportSchema = true
)
abstract class AppDb : RoomDatabase() {
    abstract fun nameDao(): NameDao
    abstract fun ratingDao(): RatingDao
}

@Dao
interface RatingDao {
    @Query("SELECT * FROM rating WHERE nameText = :text AND nameGender = :gender")
    fun findByName(text: String, gender: GenderEntity): RatingEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(newRating: RatingEntity?)
}

@Entity(
    tableName = "rating", foreignKeys = [ForeignKey(
        entity = NameEntity::class,
        parentColumns = ["text", "gender"],
        childColumns = ["nameText", "nameGender"]
    )]
)
data class RatingEntity(
    @PrimaryKey(autoGenerate = true) val key: Int = 0,
    val note: NoteEntity,
    val nameText: String,
    val nameGender: GenderEntity
)

@Dao
interface NameDao {
    @Query("SELECT * FROM name")
    fun findAll(): List<NameEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(names: List<NameEntity>)

    @Query("SELECT * FROM name WHERE text = :text")
    fun findByText(text: String): NameEntity
}

@Entity(tableName = "name", primaryKeys = ["text", "gender"])
data class NameEntity(val text: String, val gender: GenderEntity)