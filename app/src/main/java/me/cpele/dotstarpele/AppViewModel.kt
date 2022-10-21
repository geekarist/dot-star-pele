package me.cpele.dotstarpele

import android.content.Context
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

class AppViewModel(context: Context) : ViewModel() {

    val uiModel = mutableStateOf(AppUiModel(MyNamesUiModel()))

    init {
        viewModelScope.launch {
            val newNames = withContext(Dispatchers.IO) {
                val db = Room.databaseBuilder(context, AppDb::class.java, "app-db").build()
                populateDatabase(context, db, R.raw.names_boys, GenderEntity.Boy)
                populateDatabase(context, db, R.raw.names_girls, GenderEntity.Girl)

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
        context: Context,
        db: AppDb,
        @RawRes nameFileRes: Int,
        genderEntity: GenderEntity
    ) {
        context.resources.openRawResource(nameFileRes).use { boyNamesInStream ->
            val reader = boyNamesInStream.reader(Charset.defaultCharset())
            reader.readLines()
        }.filterNot { it.isBlank() }
            .flatMap { it.split("""\s+""".toRegex()) }
            .map { nameFromFile -> NameEntity(text = nameFromFile, gender = genderEntity) }
            .let { nameEntities ->
                val namesDao = db.namesDao()
                namesDao.insertAll(nameEntities)
            }
    }

    fun dispatch(event: Event) {
        when (event) {
            is Event.Navigation -> uiModel.value = uiModel.value.copy(screen = event.screen)
        }
    }

    sealed class Event {
        data class Navigation(val screen: AppUiModel.Screen) : Event()
    }
}

private fun List<NameEntity>.toUiModels(): List<MyNameItemUiModel> =
    map { nameEntity ->
        val rating = when {
            nameEntity.text.matches("^[a-g]".toRegex()) -> RatingUiModel.Love
            nameEntity.text.matches("^[h-q]".toRegex()) -> RatingUiModel.Like
            else -> RatingUiModel.Dislike
        }
        MyNameItemUiModel(firstName = nameEntity.text, rating)
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
