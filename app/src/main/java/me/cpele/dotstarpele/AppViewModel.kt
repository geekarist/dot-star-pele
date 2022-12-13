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
        val screenUimFlow = MutableStateFlow<AppUiModel.Screen>(AppUiModel.Screen.Home)
        val listingFilterStrFlow = MutableStateFlow<String?>(null)
    }

    // TODO: define `Distributary`, `Tributary`, `Delta`? interfaces, `compound` function?
    private val listingUimFlow = setUpListingUimFlow(
        nameRatingDtosFlow = db.nameRatingDao().findAll().flowOn(Dispatchers.IO),
        listingDebouncedFilterStrFlow = State.listingFilterStrFlow.debounce(100),
        listingFilterStrFlow = State.listingFilterStrFlow
    )

    private val proposalUimFlow = setUpProposalUimFlow(
        unratedNamesFlow = db.nameDao().flowUnrated().flowOn(Dispatchers.IO),
        allNameDtosFlow = db.nameDao().flowAll().flowOn(Dispatchers.IO),
        screenFlow = State.screenUimFlow
    )

    private val uiModelFlow = combine(
        listingUimFlow,
        proposalUimFlow,
        State.screenUimFlow
    ) { listingUim, proposalUim, screenUim ->
        AppUiModel(listing = listingUim, proposal = proposalUim, screen = screenUim)
    }

    init {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                populateDatabase(application, db, R.raw.names_boys, GenderDto.Boy)
                populateDatabase(application, db, R.raw.names_girls, GenderDto.Girl)
            }
        }
    }

    private fun populateDatabase(
        context: Context,
        db: AppDb,
        @RawRes nameFileRes: Int,
        genderDto: GenderDto
    ) {
        context.resources.openRawResource(nameFileRes)
            .use { inputStream -> // Read file lines
                val reader = inputStream.reader(Charset.defaultCharset())
                reader.readLines()
            }
            .filterNot { it.isBlank() } // Filter blank lines
            .flatMap { it.split("""\s+""".toRegex()) } // One name per word
            .map { nameStr -> // Create DTOs
                NameDto(text = nameStr, gender = genderDto)
            }
            .let { nameDtos -> // Insert DTOs
                val namesDao = db.nameDao()
                namesDao.insertAll(nameDtos)
            }
    }

    fun dispatch(event: Event) {
        when (event) {
            is Event.Navigation -> State.screenUimFlow.value = event.screen
            is Event.Review -> handleReviewEvent(event)
            is Event.Listing.Filter -> handleFilterName(event.text)
        }
    }

    private fun handleReviewEvent(event: Event.Review) {
        logd { "Handling review event: $event" }
        val newNoteDto = when (event) {
            is Event.Review.Love -> NoteDto.Love
            is Event.Review.Like -> NoteDto.Like
            is Event.Review.Dislike -> NoteDto.Dislike
            is Event.Review.Unknown -> NoteDto.Unknown
        }
        handleReview(
            nameText = event.nameText,
            nameGender = GenderDto.valueOf(event.nameGenderText),
            newNoteDto = newNoteDto,
        )
    }

    private fun handleFilterName(text: String) {
        State.listingFilterStrFlow.value = text
    }

    private fun handleReview(
        nameText: String,
        nameGender: GenderDto,
        newNoteDto: NoteDto
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            // Update rating and insert
            val nameDto = db.nameDao().findOne(nameText, nameGender)
            val ratingDto = db.ratingDao().findByName(nameDto.text, nameDto.gender)
            val newRatingDto = ratingDto?.copy(note = newNoteDto) ?: RatingDto(
                note = newNoteDto, nameText = nameDto.text, nameGender = nameDto.gender
            )
            Log.d(this@AppViewModel::class.simpleName, "Inserting rating: $newRatingDto")
            db.ratingDao().insert(newRatingDto)
        }
    }

    @Composable
    fun collectUiModel() = uiModelFlow.collectAsState(
        AppUiModel(
            listing = ListingUiModel(emptyList(), ""),
            proposal = ProposalUiModel.Loading(AppUiModel.Screen.Home)
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

private fun List<NameRatingDto>.toUiModels(): List<ListingItemUiModel> = map { it.toUiModel() }

private fun NameRatingDto.toUiModel(): ListingItemUiModel =
    ListingItemUiModel(
        firstName = nameDto.text,
        rating = ratingDto?.note.toUiModel(),
        gender = nameDto.gender.toUiModel(),
        nameTag = nameDto,
    )

private fun GenderDto.toUiModel() = when (this) {
    GenderDto.Boy -> GenderUiModel.Boy
    GenderDto.Girl -> GenderUiModel.Girl
}

private fun NoteDto?.toUiModel(): RatingUiModel = when (this) {
    NoteDto.Dislike -> RatingUiModel.Dislike
    NoteDto.Like -> RatingUiModel.Like
    NoteDto.Love -> RatingUiModel.Love
    NoteDto.Unknown, null -> RatingUiModel.Unknown
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

private fun setUpListingUimFlow(
    nameRatingDtosFlow: Flow<List<NameRatingDto>>,
    listingDebouncedFilterStrFlow: Flow<String?>,
    listingFilterStrFlow: MutableStateFlow<String?>
) = nameRatingDtosFlow
    .combine(listingDebouncedFilterStrFlow) { nameRatingDtos, filterStr ->
        filterNames(nameRatingDtos, filterStr)
    }.mapNotNull { nameRatingDtos ->
        sort(nameRatingDtos)
    }.map { it.toUiModels() }
    .combine(listingFilterStrFlow) { listingItemUims, filterStr ->
        ListingUiModel(names = listingItemUims, nameFilter = filterStr ?: "")
    }.flowOn(Dispatchers.Default)

private fun sort(nameRatingDtos: List<NameRatingDto>) =
    nameRatingDtos
        .sortedBy { it.nameDto.gender }
        .sortedBy { unaccented(it.nameDto.text) }
        .sortedBy { it.ratingDto?.note?.rank ?: Int.MAX_VALUE }

private fun filterNames(
    nameRatingDtos: List<NameRatingDto>,
    filterStr: String?
) = nameRatingDtos.filter { nameRatingDto ->
    isMatch(
        nameRatingDto.nameDto.text,
        filterStr
    )
}

private fun setUpProposalUimFlow(
    unratedNamesFlow: Flow<List<NameDto>>,
    allNameDtosFlow: Flow<List<NameDto>>,
    screenFlow: Flow<AppUiModel.Screen>
) = unratedNamesFlow
    .map { it.shuffled() }
    .combine(screenFlow, allNameDtosFlow) { shuffledUnratedNameDtos, screenUim, allNameDtos ->
        Quartet(
            proposeNames(shuffledUnratedNameDtos, screenUim).takeIf { it.isNotEmpty() },
            allNameDtos.size,
            identifyNextScreen(screenUim),
            identifyPrevScreen(screenUim)
        )
    }
    .filter { (proposedNameDtos, _, _, _) -> proposedNameDtos != null }
    .map { (proposedNameDtos, countAll, nextScreen, prevScreen) ->
        val nameDto = proposedNameDtos?.getOrNull(0)
        val countUnrated = proposedNameDtos?.size
        Quintet(nameDto, countUnrated, countAll, nextScreen, prevScreen)
    }
    .map { (nameDto, proposedCount, countAll, nextScreen, prevScreen) ->
        modelUi(nameDto, proposedCount, countAll, nextScreen, prevScreen)
    }
    .filterNotNull()
    .onEach { logd { "Got proposal UI model: $it" } }
    .flowOn(Dispatchers.Default)

inline fun <reified T1, reified T2, reified T3, R> Flow<T1>.combine(
    flow2: Flow<T2>,
    flow3: Flow<T3>,
    noinline transform: suspend (T1, T2, T3) -> R
): Flow<R> = combine(this, flow2, flow3, transform)

private fun modelUi(
    nameDto: NameDto?,
    proposedCount: Int?,
    countAll: Int,
    nextScreen: AppUiModel.Screen?,
    prevScreen: AppUiModel.Screen
) = if (nameDto != null && proposedCount != null) {
    ProposalUiModel.Ready(
        nameDto.text,
        ratedCount = countAll - proposedCount,
        totalCount = countAll,
        currentNameTag = nameDto.text to nameDto.gender.name,
        gender = nameDto.gender.toUiModel(),
        nextScreen = nextScreen,
        prevScreen = prevScreen
    )
} else {
    null
}

data class Quintet<out A, out B, out C, out D, out E>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D,
    val fifth: E
)

fun identifyPrevScreen(screenUim: AppUiModel.Screen) =
    if (screenUim is AppUiModel.Screen.Proposal) {
        screenUim.previous
    } else {
        AppUiModel.Screen.Home
    }

data class Quartet<out A, out B, out C, out D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D
)

fun identifyNextScreen(screenUim: AppUiModel.Screen): AppUiModel.Screen? =
    if (screenUim is AppUiModel.Screen.Proposal) {
        screenUim.next
    } else {
        null
    }

fun proposeNames(unratedNameDtos: List<NameDto>, screenUim: AppUiModel.Screen) =
    if (screenUim is AppUiModel.Screen.Proposal && screenUim.nameTag is NameDto) {
        listOf(screenUim.nameTag) + (unratedNameDtos - screenUim.nameTag)
    } else {
        unratedNameDtos
    }

