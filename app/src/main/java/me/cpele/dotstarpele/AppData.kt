package me.cpele.dotstarpele

import androidx.room.*
import kotlinx.coroutines.flow.Flow

enum class NoteDto(val rank: Int) {
    Love(rank = 10),
    Like(rank = 20),
    Dislike(rank = 30),
    Unknown(rank = 40),
}

@Database(
    entities = [NameDto::class, RatingDto::class], version = 2, exportSchema = true
)
abstract class AppDb : RoomDatabase() {
    abstract fun nameDao(): NameDao
    abstract fun ratingDao(): RatingDao
    abstract fun nameRatingDao(): NameRatingDao
}

@Dao
interface RatingDao {
    @Query("SELECT * FROM rating WHERE nameText = :text AND nameGender = :gender")
    fun findByName(text: String, gender: GenderDto): RatingDto?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(newRating: RatingDto)

    @Delete
    fun remove(rating: RatingDto)
}

@Dao
interface NameRatingDao {
    @Query(
        "SELECT * FROM name " +
                "LEFT OUTER JOIN rating " +
                "ON (text = rating.nameText AND gender = rating.nameGender)"
    )
    fun findAll(): Flow<List<NameRatingDto>>
}

data class NameRatingDto(
    @Embedded val ratingDto: RatingDto?, @Embedded val nameDto: NameDto
)

@Entity(
    tableName = "rating", foreignKeys = [ForeignKey(
        entity = NameDto::class,
        parentColumns = ["text", "gender"],
        childColumns = ["nameText", "nameGender"]
    )]
)
data class RatingDto(
    @PrimaryKey(autoGenerate = true) val key: Int = 0,
    val note: NoteDto,
    val nameText: String,
    val nameGender: GenderDto
)

const val FIND_ALL_NAMES = "SELECT * FROM name"

@Dao
interface NameDao {

    @Query(FIND_ALL_NAMES)
    fun findAll(): List<NameDto>

    @Query(FIND_ALL_NAMES)
    fun flowAll(): Flow<List<NameDto>>

    @Query(
        "SELECT * FROM name "
                + "LEFT OUTER JOIN rating "
                + "ON (name.text = rating.nameText AND name.gender = rating.nameGender) "
                + "WHERE rating.note IS NULL "
                + "OR rating.note = 'Unknown'"
    )
    fun flowUnrated(): Flow<List<NameDto>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(names: List<NameDto>)

    @Query("SELECT * FROM name WHERE text = :text")
    fun findByText(text: String): NameDto

    @Query("SELECT * FROM name WHERE text = :text AND gender = :gender")
    fun findOne(text: String, gender: GenderDto): NameDto
}

@Entity(tableName = "name", primaryKeys = ["text", "gender"])
data class NameDto(val text: String, val gender: GenderDto)