package me.cpele.dotstarpele

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.io.Serializable

data class MyNameItemUiModel(val firstName: String, val rating: RatingUiModel)

enum class RatingUiModel(val text: String, val rank: Int) {
    Love("❤️", 0), Like("\uD83D\uDC4D", 1), Dislike("\uD83D\uDC4E", 2), Unknown("❓", 3),
}

data class AppUiModel(
    val myNames: MyNamesUiModel, val screen: Screen = Screen.Home, val rate: RateUiModel
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
        val totalCount: Int
    ) : RateUiModel
}

data class MyNamesUiModel(val names: List<MyNameItemUiModel> = listOf())

@Composable
fun App(appUim: AppUiModel, dispatch: (AppViewModel.Event) -> Unit) {

    when (appUim.screen) {
        AppUiModel.Screen.Home -> Home(onClickRateNames = {
            dispatch(AppViewModel.Event.Navigation(AppUiModel.Screen.Rate))
        }, onClickMyNames = {
            dispatch(AppViewModel.Event.Navigation(AppUiModel.Screen.My))
        })
        AppUiModel.Screen.Rate -> Rate(
            uim = appUim.rate,
            onClickBack = {
                dispatch(AppViewModel.Event.Navigation(AppUiModel.Screen.Home))
            },
            onClickLove = { dispatch(AppViewModel.Event.Love) },
            onClickLike = { dispatch(AppViewModel.Event.Like) },
            onClickDislike = { nameText, nameGenderText ->
                dispatch(AppViewModel.Event.Dislike(nameText, nameGenderText))
            },
        ) { dispatch(AppViewModel.Event.Unknown) }
        AppUiModel.Screen.My -> My(uim = appUim.myNames) {
            dispatch(AppViewModel.Event.Navigation(AppUiModel.Screen.Home))
        }
    }
}

@Composable
private fun Home(onClickRateNames: () -> Unit, onClickMyNames: () -> Unit) {
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
            Button(onClick = onClickRateNames) {
                Text(text = stringResource(R.string.home_rate_button))
            }
            Button(onClick = onClickMyNames) {
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
    onClickBack: () -> Unit,
    onClickLove: () -> Unit,
    onClickLike: () -> Unit,
    onClickDislike: (String, String) -> Unit,
    onClickUnknown: () -> Unit,
) {
    Column(
        modifier = modifier.padding(16.dp)
    ) {
        BackHandler(onBack = onClickBack)
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
                    onClick = { onClickLove() }) {
                    Text(text = RatingUiModel.Love.text)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Button(modifier = Modifier.align(Alignment.CenterHorizontally),
                    onClick = { onClickLike() }) {
                    Text(text = RatingUiModel.Like.text)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Button(modifier = Modifier.align(Alignment.CenterHorizontally), onClick = {
                    val (key1, key2) = uim.currentNameTag
                    onClickDislike(key1, key2)
                }) {
                    Text(text = RatingUiModel.Dislike.text)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Button(modifier = Modifier.align(Alignment.CenterHorizontally),
                    onClick = { onClickUnknown() }) {
                    Text(text = RatingUiModel.Unknown.text)
                }
                Spacer(modifier = Modifier.height(96.dp))
            }
        }
    }
}

@Composable
fun My(modifier: Modifier = Modifier, uim: MyNamesUiModel, onClickBack: () -> Unit) {
    Column(modifier = modifier.padding(16.dp)) {
        BackHandler(onBack = onClickBack)
        Text(
            text = stringResource(id = R.string.my_head),
            style = MaterialTheme.typography.headlineMedium
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top,
        ) {
            val itemUiModels = uim.names
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(
                    16.dp, alignment = Alignment.Top
                )
            ) {
                itemsIndexed(itemUiModels) { _, itemUim ->
                    Card {
                        Row(Modifier.padding(16.dp)) {
                            Text(
                                text = itemUim.firstName,
                                modifier = Modifier.fillMaxWidth(fraction = .75f)
                            )
                            Text(
                                textAlign = TextAlign.Right,
                                text = itemUim.rating.text,
                                modifier = Modifier.fillMaxWidth(fraction = .75f)
                            )
                        }
                    }
                }
            }
        }
    }
}
