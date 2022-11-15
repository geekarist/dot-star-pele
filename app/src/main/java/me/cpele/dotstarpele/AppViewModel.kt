package me.cpele.dotstarpele

import android.app.Application
import android.content.Context
import android.util.Log
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

    private val allNameEntitiesFlow = db.nameDao().flowAll().flowOn(Dispatchers.IO)

    private val unratedNameEntitiesFlow = db.nameDao().flowUnrated()
        .flowOn(Dispatchers.IO)
        .map {
            it.shuffled()
        }
        .flowOn(Dispatchers.Default)

    private val listingFilterFlow = MutableStateFlow<String?>(null)

    private val myNamesUimFlow = db.nameRatingDao().findAll()
        .flowOn(Dispatchers.IO)
        .combine(listingFilterFlow) { nameRatingEntities, filterStr ->
            nameRatingEntities.filter { nameRatingEntity ->
                isFuzzyMatch(
                    nameRatingEntity.nameEntity.text,
                    filterStr
                )
            } to filterStr
        }.mapNotNull { (nameEntities, filterStr) -> // Sort by rank
            nameEntities
                .sortedBy { it.nameEntity.gender }
                .sortedBy { it.nameEntity.text }
                .sortedBy { it.ratingEntity?.note?.rank ?: Int.MAX_VALUE } to filterStr
        }.map { (nameEntities, filterStr) -> // Convert to UI model
            MyNamesUiModel(names = nameEntities.toUiModels(), nameFilter = filterStr ?: "")
        }.flowOn(Dispatchers.Default)

    private val rateUimFlow = unratedNameEntitiesFlow
        .combine(allNameEntitiesFlow) { unratedNameEntities, allNameEntities ->
            unratedNameEntities to allNameEntities.size
        }
        .mapNotNull { (nameEntities, countAll) ->
            nameEntities.takeIf { it.isNotEmpty() } to countAll
        }
        .filter { (unratedNameEntities, _) ->
            unratedNameEntities != null
        }
        .map { (unratedNameEntities, countAll) ->
            val nameEntity = unratedNameEntities?.getOrNull(0)
            val countUnrated = unratedNameEntities?.size
            Triple(nameEntity, countUnrated, countAll)
        }
        .map { (nameEntity, unratedCount, countAll) ->
            if (nameEntity != null && unratedCount != null) {
                RateUiModel.Ready(
                    nameEntity.text,
                    ratedCount = countAll - unratedCount,
                    totalCount = countAll,
                    currentNameTag = nameEntity.text to nameEntity.gender.name,
                    gender = nameEntity.gender.toUiModel(),
                )
            } else {
                null
            }
        }
        .filterNotNull()
        .flowOn(Dispatchers.Default)

    private val screenUimFlow = MutableStateFlow(AppUiModel.Screen.Home)

    private val uiModelFlow = combine(
        myNamesUimFlow,
        rateUimFlow,
        screenUimFlow
    ) { myNamesUim, rateUim, screenUim ->
        AppUiModel(myNames = myNamesUim, rate = rateUim, screen = screenUim)
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
        context: Context,
        db: AppDb,
        @RawRes nameFileRes: Int,
        genderEntity: GenderEntity
    ) {
        context.resources.openRawResource(nameFileRes)
            .use { inputStream -> // Read file lines
                val reader = inputStream.reader(Charset.defaultCharset())
                reader.readLines()
            }
            .filterNot { it.isBlank() } // Filter blank lines
            .flatMap { it.split("""\s+""".toRegex()) } // One name per word
            .map { nameStr -> // Create entities
                NameEntity(text = nameStr, gender = genderEntity)
            }
            .let { nameEntities -> // Insert entities
                val namesDao = db.nameDao()
                namesDao.insertAll(nameEntities)
            }
    }

    fun dispatch(event: Event) {
        when (event) {
            is Event.Navigation ->
                screenUimFlow.value = event.screen
            is Event.Review -> {
                val newNoteEntity = when (event) {
                    is Event.Review.Love -> NoteEntity.Love
                    is Event.Review.Like -> NoteEntity.Like
                    is Event.Review.Dislike -> NoteEntity.Dislike
                    is Event.Review.Unknown -> NoteEntity.Unknown
                }
                handleReview(
                    nameText = event.nameText,
                    nameGender = GenderEntity.valueOf(event.nameGenderText),
                    newNoteEntity = newNoteEntity
                )
            }
            is Event.Listing.Filter -> {
                handleFilterName(event.text)
            }
        }
    }

    private fun handleFilterName(text: String) {
        listingFilterFlow.value = text
    }

    private fun handleReview(
        nameText: String,
        nameGender: GenderEntity,
        newNoteEntity: NoteEntity
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val nameEntity = db.nameDao().findOne(nameText, nameGender)
            val ratingEntity = db.ratingDao().findByName(nameEntity.text, nameEntity.gender)
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
            myNames = MyNamesUiModel(emptyList(), ""), rate = RateUiModel.Loading
        )
    )

    sealed interface Event {

        data class Navigation(val screen: AppUiModel.Screen) : Event

        sealed interface Review : Event {
            val nameText: String
            val nameGenderText: String

            data class Dislike(
                override val nameText: String,
                override val nameGenderText: String
            ) : Review

            data class Like(
                override val nameText: String,
                override val nameGenderText: String
            ) : Review

            data class Love(
                override val nameText: String,
                override val nameGenderText: String
            ) : Review

            data class Unknown(
                override val nameText: String,
                override val nameGenderText: String
            ) : Review
        }

        sealed interface Listing : Event {
            data class Filter(val text: String) : Listing
        }
    }
}

private fun List<NameRatingEntity>.toUiModels(): List<MyNameItemUiModel> = map { it.toUiModel() }

private fun NameRatingEntity.toUiModel(): MyNameItemUiModel =
    MyNameItemUiModel(
        nameEntity.text,
        ratingEntity?.note.toUiModel(),
        nameEntity.gender.toUiModel()
    )

private fun GenderEntity.toUiModel() = when (this) {
    GenderEntity.Boy -> GenderUiModel.Boy
    GenderEntity.Girl -> GenderUiModel.Girl
}

private fun NoteEntity?.toUiModel(): RatingUiModel = when (this) {
    NoteEntity.Dislike -> RatingUiModel.Dislike
    NoteEntity.Like -> RatingUiModel.Like
    NoteEntity.Love -> RatingUiModel.Love
    NoteEntity.Unknown, null -> RatingUiModel.Unknown
}

private fun isFuzzyMatch(name: String, filter: String?) =
    filter.isNullOrBlank() || name.contains(filter)
