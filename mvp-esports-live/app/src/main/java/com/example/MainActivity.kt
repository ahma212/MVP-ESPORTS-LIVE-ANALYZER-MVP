package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.navigation.MainScaffold
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.MvpStationViewModel
import com.example.viewmodel.YouTubeLiveViewModel

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      MyApplicationTheme {
        val mvpStationViewModel: MvpStationViewModel = viewModel()
        val youTubeLiveViewModel: YouTubeLiveViewModel = viewModel()

        MainScaffold(
          mvpStationViewModel = mvpStationViewModel,
          youTubeLiveViewModel = youTubeLiveViewModel
        )
      }
    }
  }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
  Text(text = "Hello $name!", modifier = modifier)
}

