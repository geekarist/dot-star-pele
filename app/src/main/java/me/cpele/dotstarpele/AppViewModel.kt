package me.cpele.dotstarpele

import android.app.Application
import android.content.Context
import android.widget.Toast
import androidx.annotation.RawRes
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.*
import androidx.room.OnConflictStrategy.REPLACE
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.charset.Charset
import kotlin.reflect.safeCast

class AppViewModel(private val application: Application) : ViewModel() {

    private lateinit var db: AppDb

    val uiModel = mutableStateOf(
        AppUiModel(
            myNames = MyNamesUiModel(), rate = RateUiModel.Loading
        )
    )

    init {
        viewModelScope.launch {
            db = Room.databaseBuilder(application, AppDb::class.java, "app-db").build()

            val newNames = withContext(Dispatchers.IO) {
                populateDatabase(application, db, R.raw.names_boys, GenderEntity.Boy)
                populateDatabase(application, db, R.raw.names_girls, GenderEntity.Girl)

                db.nameDao().findAll().toUiModels()
            }
            val currentAppUim = uiModel.value
            val currentMyNamesUim = currentAppUim.myNames
            val newMyNamesUim = currentMyNamesUim.copy(names = newNames)
            val newAppUim = currentAppUim.copy(
                myNames = newMyNamesUim, rate = RateUiModel.Ready(
                    currentName = newNames[0].firstName, ratedCount = 0, totalCount = newNames.size
                )
            )
            uiModel.value = newAppUim
        }
    }

    private fun populateDatabase(
        context: Context, db: AppDb, @RawRes nameFileRes: Int, genderEntity: GenderEntity
    ) {
        context.resources.openRawResource(nameFileRes).use { boyNamesInStream ->
            val reader = boyNamesInStream.reader(Charset.defaultCharset())
            reader.readLines()
        }.filterNot { it.isBlank() }.flatMap { it.split("""\s+""".toRegex()) }
            .map { nameFromFile -> NameEntity(text = nameFromFile, gender = genderEntity) }
            .let { nameEntities ->
                val namesDao = db.nameDao()
                namesDao.insertAll(nameEntities)
            }
    }

    fun dispatch(event: Event) {
        when (event) {
            is Event.Navigation -> uiModel.value = uiModel.value.copy(screen = event.screen)
            Event.Love -> Toast.makeText(application, "TODO: you love it", Toast.LENGTH_SHORT)
                .show()
            Event.Like -> Toast.makeText(application, "TODO: you like it", Toast.LENGTH_SHORT)
                .show()
            Event.Dislike -> handleDislike()
            Event.Unknown -> Toast.makeText(application, "TODO: you don't know", Toast.LENGTH_SHORT)
                .show()
        }
    }

    private fun handleDislike() {
        val rateUim =
            RateUiModel.Ready::class.safeCast(uiModel.value.rate) ?: throw IllegalStateException(
                "UI model should be ${RateUiModel::class.simpleName} but is: $uiModel.value.rate"
            )
        viewModelScope.launch(Dispatchers.IO) {
            val nameEntity = db.nameDao().findByText(rateUim.currentName)
            val ratingEntity = db.ratingDao().findByName(nameEntity.text, nameEntity.gender)
            val newNoteEntity = NoteEntity.Dislike
            val newRatingEntity = ratingEntity?.copy(note = newNoteEntity) ?: RatingEntity(
                note = newNoteEntity, nameText = nameEntity.text, nameGender = nameEntity.gender
            )
            db.ratingDao().insert(newRatingEntity)
        }
    }

    sealed class Event {
        data class Navigation(val screen: AppUiModel.Screen) : Event()
        object Love : Event()
        object Like : Event()
        object Dislike : Event()
        object Unknown : Event()
    }
}

enum class NoteEntity {
    Dislike
}

private fun List<NameEntity>.toUiModels(): List<MyNameItemUiModel> = map { nameEntity ->
    val rating = when {
        nameEntity.text.matches("^[Rr]obi.*".toRegex()) -> RatingUiModel.Love
        nameEntity.text.matches("^[Ss]ic.*".toRegex()) -> RatingUiModel.Like
        nameEntity.text.matches("^[Tt]an.*".toRegex()) -> RatingUiModel.Dislike
        else -> RatingUiModel.Unknown
    }
    MyNameItemUiModel(firstName = nameEntity.text, rating)
}.sortedBy {
    val note = it.rating.rank
    val name = it.firstName
    "$note-$name"
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

    @Insert
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

    @Insert(onConflict = REPLACE)
    fun insertAll(names: List<NameEntity>)

    @Query("SELECT * FROM name WHERE text = :text")
    fun findByText(text: String): NameEntity
}

@Entity(tableName = "name", primaryKeys = ["text", "gender"])
data class NameEntity(val text: String, val gender: GenderEntity)
