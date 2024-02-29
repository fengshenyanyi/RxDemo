package com.leo.demo.rx

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.leo.demo.rx.ui.theme.RxDemoTheme
import com.leo.demo.rx.PlayState.*

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val viewModel = MainViewModel()
        viewModel.setupListener()

        setContent {
            RxDemoTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background
                ) {
                    Greeting(viewModel)
                }
            }
        }
    }
}

@Composable
fun Greeting(viewModel: MainViewModel) {

    Box {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.padding(10.dp)
        ) {
            val list = viewModel.listData

            items(list.value.size) { index ->
                val volume = list.value[index]

                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .background(if (volume.playState == PLAYING) Color.Green else Color.LightGray)
                        .clickable {
                            viewModel.manualPlay(volume)
                        }) {

                    Text(text = (if (volume.readState == ReadState.UNREAD) "(UNREAD)" else "") + volume.name)

                    Text(text = volume.playState.name)
                }
            }
        }

        Button(
            onClick = { viewModel.autoPlay() }, modifier = Modifier.align(Alignment.BottomEnd)
        ) {
            Text(text = "AutoPlay")
        }
    }
}