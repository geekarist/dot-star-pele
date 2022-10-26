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

class AppViewModel(private val application: Application) : ViewModel() {

    val uiModel = mutableStateOf(
        AppUiModel(
            myNames = MyNamesUiModel(), rate = RateUiModel(
                currentName = "Kevin", ratedCount = 19, totalCount = 284
            )
        )
    )

    init {
        viewModelScope.launch {
            val newNames = withContext(Dispatchers.IO) {
                val db = Room.databaseBuilder(application, AppDb::class.java, "app-db").build()
                populateDatabase(application, db, R.raw.names_boys, GenderEntity.Boy)
                populateDatabase(application, db, R.raw.names_girls, GenderEntity.Girl)

                db.namesDao().findAll().toUiModels()
            }
            val currentAppUim = uiModel.value
            val currentMyNamesUim = currentAppUim.myNames
            val newMyNamesUim = currentMyNamesUim.copy(names = newNames)
            val newAppUim = currentAppUim.copy(myNames = newMyNamesUim)
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
                val namesDao = db.namesDao()
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
            Event.Dislike -> Toast.makeText(
                application, "TODO: you don't like it", Toast.LENGTH_SHORT
            ).show()
            Event.Unknown -> Toast.makeText(application, "TODO: you don't know", Toast.LENGTH_SHORT)
                .show()
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

@Database(entities = [NameEntity::class], version = 1)
abstract class AppDb : RoomDatabase() {
    abstract fun namesDao(): NameDao
}

@Dao
interface NameDao {
    @Query("SELECT * FROM name")
    fun findAll(): List<NameEntity>

    @Insert(onConflict = REPLACE)
    fun insertAll(names: List<NameEntity>)
}

@Entity(tableName = "name", primaryKeys = ["text", "gender"])
data class NameEntity(val text: String, val gender: GenderEntity)
