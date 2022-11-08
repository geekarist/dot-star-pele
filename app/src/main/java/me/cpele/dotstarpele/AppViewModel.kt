package me.cpele.dotstarpele

import android.app.Application
import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.annotation.RawRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.charset.Charset

class AppViewModel(private val application: Application) : ViewModel() {

    private val db = Room.databaseBuilder(application, AppDb::class.java, "app-db").build()

    private val nameEntitiesFlow = db.nameDao().flowAll().flowOn(Dispatchers.IO)

    private val unratedNameEntitiesFlow = db.nameDao().flowUnrated().flowOn(Dispatchers.IO).onEach {
        Log.d(
            this@AppViewModel::class.simpleName, "Emitting ${it.size} unrated name entities: $it"
        )
    }

    private val myNamesUimFlow =
        db.nameRatingDao().findAll().flowOn(Dispatchers.IO).map { nameEntities ->
            MyNamesUiModel(names = nameEntities.toUiModels())
        }.flowOn(Dispatchers.Default)

    private val rateUimFlow =
        unratedNameEntitiesFlow.mapNotNull { nameEntities -> nameEntities.takeIf { it.isNotEmpty() } }
            .filterNotNull().map { nameEntities ->
                val nameInReview = nameEntities[0]
                val countNames = nameEntities.size
                nameInReview to countNames
            }.map { (nameInReview, countNames) ->
                RateUiModel.Ready(
                    nameInReview.text,
                    ratedCount = 0,
                    totalCount = countNames,
                    currentNameTag = nameInReview.text to nameInReview.gender.name
                )
            }.flowOn(Dispatchers.Default)

    private val screenUimFlow = MutableStateFlow(AppUiModel.Screen.Home)

    private val uiModelFlow =
        combine(myNamesUimFlow, rateUimFlow, screenUimFlow) { myNamesUim, rateUim, screenUim ->
            AppUiModel(myNames = myNamesUim, rate = rateUim, screen = screenUim).also {
                Log.d(this@AppViewModel::class.simpleName, "Emitting app UI model: $it")
            }
        }

    init {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                populateDatabase(application, db, R.raw.names_boys, GenderEntity.Boy)
                populateDatabase(application, db, R.raw.names_girls, GenderEntity.Girl)
            }
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
            is Event.Navigation -> screenUimFlow.value = event.screen
            Event.Love -> Toast.makeText(application, "TODO: you love it", Toast.LENGTH_SHORT)
                .show()
            Event.Like -> Toast.makeText(application, "TODO: you like it", Toast.LENGTH_SHORT)
                .show()
            is Event.Dislike -> handleDislike(
                nameText = event.nameText, nameGender = GenderEntity.valueOf(event.nameGenderText)
            )
            Event.Unknown -> Toast.makeText(application, "TODO: you don't know", Toast.LENGTH_SHORT)
                .show()
        }
    }

    private fun handleDislike(nameText: String, nameGender: GenderEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            val nameEntity = db.nameDao().findOne(nameText, nameGender)
            val ratingEntity = db.ratingDao().findByName(nameEntity.text, nameEntity.gender)
            val newNoteEntity = NoteEntity.Dislike
            val newRatingEntity = ratingEntity?.copy(note = newNoteEntity) ?: RatingEntity(
                note = newNoteEntity, nameText = nameEntity.text, nameGender = nameEntity.gender
            )
            Log.d(this@AppViewModel::class.simpleName, "Inserting rating: $newRatingEntity")
            db.ratingDao().insert(newRatingEntity)
        }

    }

    @Composable
    fun collectUiModel() = uiModelFlow.collectAsState(
        AppUiModel(
            myNames = MyNamesUiModel(), rate = RateUiModel.Loading
        )
    )

    sealed class Event {
        data class Navigation(val screen: AppUiModel.Screen) : Event()
        object Love : Event()
        object Like : Event()
        data class Dislike(val nameText: String, val nameGenderText: String) : Event()
        object Unknown : Event()
    }
}

// TODO: sort by rank
private fun List<NameRatingEntity>.toUiModels(): List<MyNameItemUiModel> = map { it.toUiModel() }

private fun NameRatingEntity.toUiModel(): MyNameItemUiModel =
    MyNameItemUiModel(nameEntity.text, ratingEntity.note.toUiModel())

private fun NoteEntity.toUiModel(): RatingUiModel = when (this) {
    NoteEntity.Dislike -> RatingUiModel.Dislike
    NoteEntity.Like -> RatingUiModel.Like
    NoteEntity.Love -> RatingUiModel.Love
    NoteEntity.Unknown -> RatingUiModel.Unknown
}

