package me.cpele.dotstarpele

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import me.cpele.dotstarpele.ui.theme.DotStarPeleTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DotStarPeleTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background
                ) {
                    val factory = viewModelFactory { initializer { AppViewModel(application) } }
                    val appViewModel: AppViewModel = viewModel(factory = factory)
                    val appUiModel by appViewModel.collectUiModel()
                    App(appUiModel, appViewModel::dispatch)
                }
            }
        }
    }
}

