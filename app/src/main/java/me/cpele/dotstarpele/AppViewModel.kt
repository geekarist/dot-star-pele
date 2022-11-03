package me.cpele.dotstarpele

import android.app.Application
import android.content.Context
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
import kotlin.reflect.safeCast

class AppViewModel(private val application: Application) : ViewModel() {

    private lateinit var db: AppDb

    private val myNamesUimFlow = db.nameDao().flowAll().flowOn(Dispatchers.IO)
        .map { nameEntities -> MyNamesUiModel(names = nameEntities.toUiModels()) }
        .flowOn(Dispatchers.Default)

    private val rateUimFlow = myNamesUimFlow.mapNotNull { myNamesUim ->
        myNamesUim.names.takeIf { it.isNotEmpty() }
    }.filterNotNull().map { nameUims: List<MyNameItemUiModel> ->
        RateUiModel.Ready(
            nameUims[0].firstName, ratedCount = 0, totalCount = nameUims.size
        )
    }.flowOn(Dispatchers.Default)

    private val screenUimFlow = MutableStateFlow(AppUiModel.Screen.Home)

    private val uiModelFlow =
        combine(myNamesUimFlow, rateUimFlow, screenUimFlow) { myNamesUim, rateUim, screenUim ->
            AppUiModel(myNames = myNamesUim, rate = rateUim, screen = screenUim)
        }

    init {
        viewModelScope.launch {
            db = Room.databaseBuilder(application, AppDb::class.java, "app-db").build()

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
            Event.Dislike -> handleDislike()
            Event.Unknown -> Toast.makeText(application, "TODO: you don't know", Toast.LENGTH_SHORT)
                .show()
        }
    }

    private fun handleDislike() {
        // handleDislike(rateUimFlow.value)
    }

    private fun handleDislike(rateUim: RateUiModel) {
        val readyRateUim =
            RateUiModel.Ready::class.safeCast(rateUim) ?: throw IllegalStateException(
                "UI model should be ${RateUiModel::class.simpleName} but is: $rateUim"
            )
        viewModelScope.launch(Dispatchers.IO) {
            val nameEntity = db.nameDao().findByText(readyRateUim.currentName)
            val ratingEntity = db.ratingDao().findByName(nameEntity.text, nameEntity.gender)
            val newNoteEntity = NoteEntity.Dislike
            val newRatingEntity = ratingEntity?.copy(note = newNoteEntity) ?: RatingEntity(
                note = newNoteEntity, nameText = nameEntity.text, nameGender = nameEntity.gender
            )
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

