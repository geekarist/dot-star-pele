@file:OptIn(ExperimentalMaterial3Api::class)
@file:Suppress("OPT_IN_IS_NOT_ENABLED")

package me.cpele.dotstarpele

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
    val firstName: String,
    val rating: RatingUiModel,
    val gender: GenderUiModel
)

enum class GenderUiModel(
    val tint: Color,
    @DrawableRes val icon: Int,
    @StringRes val description: Int
) {
    Boy(Color.Blue, R.drawable.ic_male, R.string.my_male),
    Girl(Color.Magenta, R.drawable.ic_female, R.string.my_female),
}

enum class RatingUiModel(val text: String) {
    Love("❤️"), Like("\uD83D\uDC4D"), Dislike("\uD83D\uDC4E"), Unknown("❓"),
}

data class AppUiModel(
    val myNames: ListingUiModel, val screen: Screen = Screen.Home, val rate: RateUiModel
) : Serializable {
    enum class Screen {
        Home, Rate, My
    }
}

sealed interface RateUiModel {
    object Loading : RateUiModel
    data class Ready(
        val currentName: String,
        val currentNameTag: Pair<String, String>,
        val ratedCount: Int,
        val totalCount: Int,
        val gender: GenderUiModel,
    ) : RateUiModel
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
    when (appUim.screen) {
        AppUiModel.Screen.Home -> Home(dispatch = dispatch)
        AppUiModel.Screen.Rate -> Rate(uim = appUim.rate, dispatch = dispatch)
        AppUiModel.Screen.My -> My(uim = appUim.myNames, dispatch = dispatch)
    }
}

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
            Button(onClick = { dispatch(AppViewModel.Event.Navigation(AppUiModel.Screen.Rate)) }) {
                Text(text = stringResource(R.string.home_rate_button))
            }
            Button(onClick = { dispatch(AppViewModel.Event.Navigation(AppUiModel.Screen.My)) }) {
                Text(text = stringResource(R.string.home_mine_button))
            }
            Spacer(modifier = Modifier.height(96.dp))
        }
    }
}

@Composable
fun Rate(
    modifier: Modifier = Modifier,
    uim: RateUiModel,
    dispatch: (AppViewModel.Event) -> Unit,
) {
    SideEffect {
        Log.d("UI", "Recomposing with UI model: $uim")
    }
    Column(
        modifier = modifier.padding(16.dp)
    ) {
        BackHandler(onBack = {
            dispatch(AppViewModel.Event.Navigation(AppUiModel.Screen.Home))
        })
        Text(
            text = stringResource(R.string.rate_head),
            style = MaterialTheme.typography.headlineMedium,
        )
        when (uim) {
            is RateUiModel.Loading -> Text(text = "Loading names...")
            is RateUiModel.Ready -> Column(
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
                Button(modifier = Modifier.align(Alignment.CenterHorizontally),
                    onClick = {
                        val (key1, key2) = uim.currentNameTag
                        dispatch(
                            AppViewModel.Event.Review.Love(
                                nameText = key1,
                                nameGenderText = key2
                            )
                        )
                    }) {
                    Text(text = RatingUiModel.Love.text)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Button(modifier = Modifier.align(Alignment.CenterHorizontally),
                    onClick = {
                        val (key1, key2) = uim.currentNameTag
                        dispatch(
                            AppViewModel.Event.Review.Like(
                                nameText = key1,
                                nameGenderText = key2
                            )
                        )
                    }) {
                    Text(text = RatingUiModel.Like.text)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Button(modifier = Modifier.align(Alignment.CenterHorizontally), onClick = {
                    val (key1, key2) = uim.currentNameTag
                    dispatch(
                        AppViewModel.Event.Review.Dislike(
                            nameText = key1,
                            nameGenderText = key2
                        )
                    )
                }) {
                    Text(text = RatingUiModel.Dislike.text)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Button(modifier = Modifier.align(Alignment.CenterHorizontally),
                    onClick = {
                        val (key1, key2) = uim.currentNameTag
                        dispatch(
                            AppViewModel.Event.Review.Unknown(
                                nameText = key1,
                                nameGenderText = key2
                            )
                        )
                    }) {
                    Text(text = RatingUiModel.Unknown.text)
                }
                Spacer(modifier = Modifier.height(96.dp))
            }
        }
    }
}

@Composable
fun My(
    modifier: Modifier = Modifier,
    uim: ListingUiModel,
    dispatch: (AppViewModel.Event) -> Unit
) {
    Column(modifier = modifier.padding(16.dp), Arrangement.spacedBy(16.dp)) {
        BackHandler(onBack = {
            dispatch(AppViewModel.Event.Navigation(AppUiModel.Screen.Home))
        })
        Text(
            text = stringResource(id = R.string.my_head),
            style = MaterialTheme.typography.headlineMedium
        )
        TextField(
            placeholder = { stringResource(R.string.listing_filter) },
            value = uim.nameFilter,
            modifier = Modifier
                .fillMaxWidth(),
            onValueChange = { value: String -> dispatch(AppViewModel.Event.Listing.Filter(value)) })
        Column(
            modifier = Modifier
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top,
        ) {
            val itemUiModels = uim.names
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(
                    8.dp, alignment = Alignment.Top
                ),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                itemsIndexed(itemUiModels) { itemIndex, itemUim ->
                    val prevItemUim = remember(itemUiModels, itemIndex, itemUim) {
                        itemUiModels.getOrElse(itemIndex - 1) { itemUim }
                    }
                    val prevRatingUim = remember(prevItemUim) { prevItemUim.rating }
                    val isNewRating =
                        remember(prevRatingUim, itemUim.rating) { prevRatingUim != itemUim.rating }
                    if (isNewRating) {
                        Text(
                            text = "·",
                            style = MaterialTheme.typography.displaySmall
                        )
                    }
                    Card {
                        Row(Modifier.padding(16.dp)) {
                            Image(
                                colorFilter = ColorFilter.tint(itemUim.gender.tint),
                                modifier = Modifier
                                    .padding(end = 8.dp),
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
                                text = itemUim.rating.text
                            )
                        }
                    }
                }
            }
        }
    }
}
