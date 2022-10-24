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

@Composable
fun App(appUim: AppUiModel, dispatch: (AppViewModel.Event) -> Unit) {

    when (appUim.screen) {
        AppUiModel.Screen.Home -> Home(onClickRateNames = {
            dispatch(AppViewModel.Event.Navigation(AppUiModel.Screen.Rate))
        }, onClickMyNames = {
            dispatch(AppViewModel.Event.Navigation(AppUiModel.Screen.My))
        })
        AppUiModel.Screen.Rate -> Rate(onClickBack = {
            dispatch(AppViewModel.Event.Navigation(AppUiModel.Screen.Home))
        })
        AppUiModel.Screen.My -> My(uim = appUim.myNames) {
            dispatch(AppViewModel.Event.Navigation(AppUiModel.Screen.Home))
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

data class MyNameItemUiModel(val firstName: String, val rating: RatingUiModel)

enum class RatingUiModel(val text: String, val rank: Int) {
    Love("❤️", 0),
    Like("\uD83D\uDC4D", 1),
    Dislike("\uD83D\uDC4E", 2),
    Unknown("❓", 3),
}

@Composable
fun Rate(modifier: Modifier = Modifier, onClickBack: () -> Unit) {
    Column(
        modifier = modifier.padding(16.dp)
    ) {
        BackHandler(onBack = onClickBack)
        Text(
            text = stringResource(R.string.rate_head),
            style = MaterialTheme.typography.headlineMedium,
        )
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "Kevin",
                style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            Spacer(modifier = Modifier.height(32.dp))
            Button(modifier = Modifier.align(Alignment.CenterHorizontally), onClick = {}) {
                Text(text = RatingUiModel.Like.text)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Button(modifier = Modifier.align(Alignment.CenterHorizontally), onClick = {}) {
                Text(text = RatingUiModel.Dislike.text)
            }
        }
    }
}

data class AppUiModel(val myNames: MyNamesUiModel, val screen: Screen = Screen.Home) :
    Serializable {
    enum class Screen {
        Home, Rate, My
    }
}

data class MyNamesUiModel(val names: List<MyNameItemUiModel> = listOf())

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
                Text(stringResource(R.string.home_rate_button))
            }
            Button(onClick = onClickMyNames) {
                Text(stringResource(R.string.home_mine_button))
            }
        }
    }
}