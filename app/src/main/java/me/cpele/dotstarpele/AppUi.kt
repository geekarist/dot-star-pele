@file:OptIn(ExperimentalMaterial3Api::class) @file:Suppress("OPT_IN_IS_NOT_ENABLED")

package me.cpele.dotstarpele

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import java.io.Serializable

data class ListingItemUiModel(
    val firstName: String, val rating: RatingUiModel, val gender: GenderUiModel, val nameTag: Any
)

enum class GenderUiModel(
    val tint: Color, @DrawableRes val icon: Int, @StringRes val description: Int
) {
    Boy(Color.Blue, R.drawable.ic_male, R.string.my_male), Girl(
        Color.Magenta,
        R.drawable.ic_female,
        R.string.my_female
    ),
}

enum class RatingUiModel(val emoji: String, @StringRes val text: Int) {
    Love("❤️", R.string.listing_love), Like(
        "\uD83D\uDC4D",
        R.string.listing_like
    ),
    Dislike("\uD83D\uDC4E", R.string.listing_dislike), Unknown("❓", R.string.listing_unknown),
}

data class AppUiModel(
    val listing: ListingUiModel, val screen: Screen = Screen.Home, val proposal: ProposalUiModel
) : Serializable {

    sealed class Screen {

        object Home : Screen()

        data class Proposal(
            val nameTag: Any? = null,
            val previous: Screen = Home,
            val next: Screen? = null
        ) : Screen()

        object Listing : Screen()
    }
}

sealed interface ProposalUiModel {
    val prevScreen: AppUiModel.Screen

    data class Loading(override val prevScreen: AppUiModel.Screen) : ProposalUiModel

    data class Ready(
        val currentName: String,
        val currentNameTag: Pair<String, String>,
        val ratedCount: Int,
        val totalCount: Int,
        val gender: GenderUiModel,
        val nextScreen: AppUiModel.Screen? = null,
        override val prevScreen: AppUiModel.Screen
    ) : ProposalUiModel
}

data class ListingUiModel(val names: List<ListingItemUiModel> = listOf(), val nameFilter: String)

inline fun logd(provideMsg: () -> String) {
    val myObject = object : Any() {}
    val tag = myObject.javaClass.name.replace("\\$.*$".toRegex(), "")
    Log.d(tag, provideMsg())
}

@Composable
fun App(appUim: AppUiModel, dispatch: (AppViewModel.Event) -> Unit) {
    logd { "Recomposing UI model" }
    val appState = remember { AppState() }
    when (appUim.screen) {
        AppUiModel.Screen.Home -> Home(dispatch = dispatch)
        is AppUiModel.Screen.Proposal -> Proposal(uim = appUim.proposal, dispatch = dispatch)
        AppUiModel.Screen.Listing -> Listing(
            uim = appUim.listing,
            state = appState.listingState,
            dispatch = dispatch
        )
    }
}

data class AppState(val listingState: ListingState = ListingState())

data class ListingState(val lazyListState: LazyListState = LazyListState())

@Composable
private fun Home(dispatch: (AppViewModel.Event) -> Unit) {
    Column(
        verticalArrangement = Arrangement.Top, modifier = Modifier.padding(16.dp)
    ) {
        Text(
            text = stringResource(R.string.home_title),
            style = MaterialTheme.typography.headlineMedium,
        )
        Column(
            modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(
                space = 8.dp, alignment = Alignment.CenterVertically
            ), horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Button(onClick = {
                dispatch(AppViewModel.Event.Navigation(AppUiModel.Screen.Proposal()))
            }) {
                Text(text = stringResource(R.string.home_rate_button))
            }
            Button(onClick = { dispatch(AppViewModel.Event.Navigation(AppUiModel.Screen.Listing)) }) {
                Text(text = stringResource(R.string.home_mine_button))
            }
            Spacer(modifier = Modifier.height(96.dp))
        }
    }
}

@Composable
fun Proposal(
    modifier: Modifier = Modifier,
    uim: ProposalUiModel,
    dispatch: (AppViewModel.Event) -> Unit,
) {
    SideEffect {
        Log.d("UI", "Recomposing with UI model: $uim")
    }
    Column(
        modifier = modifier.padding(16.dp)
    ) {
        BackHandler(onBack = {
            dispatch(AppViewModel.Event.Navigation(uim.prevScreen))
        })
        Text(
            text = stringResource(R.string.rate_head),
            style = MaterialTheme.typography.headlineMedium,
        )
        when (uim) {
            is ProposalUiModel.Loading -> Text(text = "Loading names...")
            is ProposalUiModel.Ready -> Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Image(
                    colorFilter = ColorFilter.tint(uim.gender.tint),
                    imageVector = ImageVector.vectorResource(id = uim.gender.icon),
                    modifier = Modifier
                        .size(48.dp)
                        .padding(bottom = 8.dp),
                    contentDescription = stringResource(id = uim.gender.description)
                )
                Text(
                    text = uim.currentName,
                    style = MaterialTheme.typography.headlineLarge,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    stringResource(
                        R.string.rate_count_ratings, uim.ratedCount, uim.totalCount
                    )
                )
                Spacer(modifier = Modifier.height(32.dp))
                Button(modifier = Modifier.align(Alignment.CenterHorizontally), onClick = {
                    val (key1, key2) = uim.currentNameTag
                    dispatch(
                        AppViewModel.Event.Review.Love(
                            nameText = key1, nameGenderText = key2
                        )
                    )
                    uim.nextScreen?.let { nextScreen ->
                        dispatch(AppViewModel.Event.Navigation(screen = nextScreen))
                    }
                }) {
                    Text(text = RatingUiModel.Love.emoji)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Button(modifier = Modifier.align(Alignment.CenterHorizontally), onClick = {
                    val (key1, key2) = uim.currentNameTag
                    dispatch(
                        AppViewModel.Event.Review.Like(
                            nameText = key1, nameGenderText = key2
                        )
                    )
                    uim.nextScreen?.let { nextScreen ->
                        dispatch(AppViewModel.Event.Navigation(screen = nextScreen))
                    }
                }) {
                    Text(text = RatingUiModel.Like.emoji)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Button(modifier = Modifier.align(Alignment.CenterHorizontally), onClick = {
                    val (key1, key2) = uim.currentNameTag
                    dispatch(
                        AppViewModel.Event.Review.Dislike(
                            nameText = key1, nameGenderText = key2
                        )
                    )
                    uim.nextScreen?.let { nextScreen ->
                        dispatch(AppViewModel.Event.Navigation(screen = nextScreen))
                    }
                }) {
                    Text(text = RatingUiModel.Dislike.emoji)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Button(modifier = Modifier.align(Alignment.CenterHorizontally), onClick = {
                    val (key1, key2) = uim.currentNameTag
                    dispatch(
                        AppViewModel.Event.Review.Unknown(
                            nameText = key1, nameGenderText = key2
                        )
                    )
                    uim.nextScreen?.let { nextScreen ->
                        dispatch(AppViewModel.Event.Navigation(screen = nextScreen))
                    }
                }) {
                    Text(text = RatingUiModel.Unknown.emoji)
                }
                Spacer(modifier = Modifier.height(96.dp))
            }
        }
    }
}

@Composable
fun Listing(
    modifier: Modifier = Modifier,
    state: ListingState,
    uim: ListingUiModel,
    dispatch: (AppViewModel.Event) -> Unit
) {
    Column(modifier = modifier.padding(16.dp), Arrangement.spacedBy(16.dp)) {
        ListingControls(uim, dispatch)
        ListingBody(uim, state, dispatch)
    }
}

@Composable
private fun ListingBody(
    uim: ListingUiModel,
    state: ListingState,
    dispatch: (AppViewModel.Event) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top,
    ) {
        val itemUiModels = uim.names
        LazyColumn(
            state = state.lazyListState,
            verticalArrangement = Arrangement.spacedBy(
                8.dp, alignment = Alignment.Top
            ),
        ) {
            itemsIndexed(itemUiModels) { itemIndex, itemUim ->
                val prevItemUim = remember(itemUiModels, itemIndex, itemUim) {
                    itemUiModels.getOrNull(itemIndex - 1)
                }
                val prevRatingUim = remember(prevItemUim) { prevItemUim?.rating }
                val isNewRating =
                    remember(prevRatingUim, itemUim.rating) { prevRatingUim != itemUim.rating }
                ListingItem(isNewRating, itemUim, dispatch)
            }
        }
    }
}

@Composable
private fun ListingControls(
    uim: ListingUiModel, dispatch: (AppViewModel.Event) -> Unit
) {
    BackHandler(onBack = {
        dispatch(AppViewModel.Event.Navigation(AppUiModel.Screen.Home))
    })
    Text(
        text = stringResource(id = R.string.my_head),
        style = MaterialTheme.typography.headlineMedium
    )
    TextField(placeholder = { stringResource(R.string.listing_filter) },
        value = uim.nameFilter,
        modifier = Modifier.fillMaxWidth(),
        onValueChange = { value: String -> dispatch(AppViewModel.Event.Listing.Filter(value)) })
}

@Composable
private fun ListingItem(
    isNewRating: Boolean,
    itemUim: ListingItemUiModel,
    dispatch: (AppViewModel.Event) -> Unit
) {
    if (isNewRating) {
        Text(
            text = stringResource(id = itemUim.rating.text),
            modifier = Modifier.padding(vertical = 8.dp),
            style = MaterialTheme.typography.titleMedium
        )
    }
    Card(onClick = {
        dispatch(
            AppViewModel.Event.Navigation(
                AppUiModel.Screen.Proposal(
                    nameTag = itemUim.nameTag,
                    previous = AppUiModel.Screen.Listing,
                    next = AppUiModel.Screen.Listing
                )
            )
        )
    }) {
        Row(Modifier.padding(16.dp)) {
            Image(
                colorFilter = ColorFilter.tint(itemUim.gender.tint),
                modifier = Modifier.padding(end = 8.dp),
                imageVector = ImageVector.vectorResource(id = itemUim.gender.icon),
                contentDescription = stringResource(id = itemUim.gender.description)
            )
            Text(
                text = itemUim.firstName,
                modifier = Modifier
                    .weight(1f)
                    .align(Alignment.CenterVertically)
            )
            Text(
                text = itemUim.rating.emoji
            )
        }
    }
}
