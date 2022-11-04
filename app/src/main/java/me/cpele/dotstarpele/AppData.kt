package me.cpele.dotstarpele

import androidx.room.*
import kotlinx.coroutines.flow.Flow

enum class NoteEntity {
    Dislike
}

@Database(
    entities = [NameEntity::class, RatingEntity::class], version = 2, exportSchema = true
)
abstract class AppDb : RoomDatabase() {
    abstract fun nameDao(): NameDao
    abstract fun ratingDao(): RatingDao
    abstract fun ratedNameDao(): RatedNameDao
}

@Dao
interface RatedNameDao {
    @Query(
        """SELECT * FROM rating JOIN name ON (rating.nameText = name.text AND rating.nameGender = name.gender) WHERE rating.note = :note"""
    )
    fun flowByNote(note: NoteEntity): RatedNameEntity
}

@Dao
interface RatingDao {
    @Query("SELECT * FROM rating WHERE nameText = :text AND nameGender = :gender")
    fun findByName(text: String, gender: GenderEntity): RatingEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(newRating: RatingEntity?)

}

const val FIND_ALL_NAMES = "SELECT * FROM name"

@Dao
interface NameDao {

    @Query(FIND_ALL_NAMES)
    fun findAll(): List<NameEntity>

    @Query(FIND_ALL_NAMES)
    fun flowAll(): Flow<List<NameEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(names: List<NameEntity>)

    @Query("SELECT * FROM name WHERE text = :text")
    fun findByText(text: String): NameEntity

    @Query("SELECT * FROM name WHERE text = :text AND gender = :gender")
    fun findOne(text: String, gender: GenderEntity): NameEntity
}

@Entity(tableName = "name", primaryKeys = ["text", "gender"])
data class NameEntity(val text: String, val gender: GenderEntity)

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

@Entity
data class RatedNameEntity(
    @Embedded val name: NameEntity, @Embedded val rating: RatingEntity
)
