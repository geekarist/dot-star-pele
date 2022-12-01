@file:OptIn(FlowPreview::class)
@file:Suppress("OPT_IN_IS_NOT_ENABLED")

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
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.charset.Charset
import java.text.Normalizer

class AppViewModel(private val application: Application) : ViewModel() {

    private val db = Room.databaseBuilder(application, AppDb::class.java, "app-db").build()

    private object State {
        val screenUimFlow = MutableStateFlow(AppUiModel.Screen.Home)
        val listingFilterStrFlow = MutableStateFlow<String?>(null)
        val requestedNameTagFlow = MutableStateFlow<Any?>(null)
    }

    private val listingUimFlow = flowListingUim(
        nameRatingEntitiesFlow = db.nameRatingDao().findAll().flowOn(Dispatchers.IO),
        listingDebouncedFilterStrFlow = State.listingFilterStrFlow.debounce(100),
        listingFilterStrFlow = State.listingFilterStrFlow
    )

    private val proposalUimFlow = flowProposalUim(
        unratedNamesFlow = db.nameDao().flowUnrated().flowOn(Dispatchers.IO),
        requestedNameEntityFlow = State.requestedNameTagFlow.filterIsInstance(),
        allNameEntitiesFlow = db.nameDao().flowAll().flowOn(Dispatchers.IO)
    )

    private val uiModelFlow = combine(
        listingUimFlow,
        proposalUimFlow,
        State.screenUimFlow
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
            is Event.Navigation -> State.screenUimFlow.value = event.screen
            is Event.Review -> handleReviewEvent(event)
            is Event.Listing.Filter -> handleFilterName(event.text)
            is Event.Listing.ItemClicked -> handleListingItemClicked(event)
        }
    }

    private fun handleReviewEvent(event: Event.Review) {
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

    private fun handleListingItemClicked(event: Event.Listing.ItemClicked) {
        State.screenUimFlow.value = AppUiModel.Screen.Proposal

        State.requestedNameTagFlow.value = event.nameTag

        viewModelScope.launch(Dispatchers.IO) {
            val nameEntity = event.nameTag as? NameEntity
                ?: error("Wrong name tag type for ${event.nameTag}")
            db.ratingDao()
                .findByName(nameEntity.text, nameEntity.gender)
                ?.let {
                    db.ratingDao().remove(it)
                }
        }
    }

    private fun handleFilterName(text: String) {
        State.listingFilterStrFlow.value = text
    }

    private fun handleReview(
        nameText: String,
        nameGender: GenderEntity,
        newNoteEntity: NoteEntity
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            // Update rating and insert
            val nameEntity = db.nameDao().findOne(nameText, nameGender)
            val ratingEntity = db.ratingDao().findByName(nameEntity.text, nameEntity.gender)
            val newRatingEntity = ratingEntity?.copy(note = newNoteEntity) ?: RatingEntity(
                note = newNoteEntity, nameText = nameEntity.text, nameGender = nameEntity.gender
            )
            Log.d(this@AppViewModel::class.simpleName, "Inserting rating: $newRatingEntity")
            db.ratingDao().insert(newRatingEntity)

            // Clear any name that was requested for next rating
            State.requestedNameTagFlow.value = null
        }
    }

    @Composable
    fun collectUiModel() = uiModelFlow.collectAsState(
        AppUiModel(
            myNames = ListingUiModel(emptyList(), ""), rate = RateUiModel.Loading
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
            data class ItemClicked(val nameTag: Any) : Listing
        }
    }
}

private fun List<NameRatingEntity>.toUiModels(): List<ListingItemUiModel> = map { it.toUiModel() }

private fun NameRatingEntity.toUiModel(): ListingItemUiModel =
    ListingItemUiModel(
        firstName = nameEntity.text,
        rating = ratingEntity?.note.toUiModel(),
        gender = nameEntity.gender.toUiModel(),
        nameTag = nameEntity,
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

private fun isMatch(name: String, filter: String?): Boolean {
    val unaccentedName = unaccented(name)
    val unaccentedFilter = unaccented(filter)
    return unaccentedFilter == unaccentedName
            || unaccentedFilter.isNullOrBlank()
            || unaccentedName?.contains(unaccentedFilter, ignoreCase = true) == true
}

/**
 * Replace accented characters with unaccented ones.
 */
private fun unaccented(str: String?) = str?.let { nonNullStr ->
    // Decompose each accented char into a combined char and a base char
    Normalizer.normalize(nonNullStr, Normalizer.Form.NFD)
        // Remove combined chars
        .replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")
}

private fun flowListingUim(
    nameRatingEntitiesFlow: Flow<List<NameRatingEntity>>,
    listingDebouncedFilterStrFlow: Flow<String?>,
    listingFilterStrFlow: MutableStateFlow<String?>
) = nameRatingEntitiesFlow
    .combine(listingDebouncedFilterStrFlow) { nameRatingEntities, filterStr ->
        nameRatingEntities.filter { nameRatingEntity ->
            isMatch(
                nameRatingEntity.nameEntity.text,
                filterStr
            )
        }
    }.mapNotNull { nameRatingEntities -> // Sort by rank
        nameRatingEntities
            .sortedBy { it.nameEntity.gender }
            .sortedBy { it.nameEntity.text }
            .sortedBy { it.ratingEntity?.note?.rank ?: Int.MAX_VALUE }
    }.map { it.toUiModels() }
    .flowOn(Dispatchers.Default)
    .combine(listingFilterStrFlow) { listingItemUims, filterStr ->
        ListingUiModel(names = listingItemUims, nameFilter = filterStr ?: "")
    }.flowOn(Dispatchers.Default)

private fun flowProposalUim(
    unratedNamesFlow: Flow<List<NameEntity>>,
    requestedNameEntityFlow: Flow<NameEntity?>,
    allNameEntitiesFlow: Flow<List<NameEntity>>
) = unratedNamesFlow
    .map {
        it.shuffled()
    }
    .flowOn(Dispatchers.Default)
    .combine(requestedNameEntityFlow) { unratedNameEntities, requestedNameEntity ->
        val requestedNameEntityList = requestedNameEntity?.let { listOf(it) } ?: emptyList()
        requestedNameEntityList + unratedNameEntities
    }
    .combine(allNameEntitiesFlow) { nameToRateEntities, allNameEntities ->
        nameToRateEntities to allNameEntities.size
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
    .onEach { logd { "Got proposal UI model: $it" } }
    .flowOn(Dispatchers.Default)
