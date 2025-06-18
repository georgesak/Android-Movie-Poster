package com.example.movieposter

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.movieposter.ui.theme.MoviePosterTheme

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MoviePosterTheme {
                SettingsScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class) // Suppress experimental API warning for TopAppBar
@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val sharedPreferences = context.getSharedPreferences("AppSettings", Context.MODE_PRIVATE)
    val (transitionDelay, setTransitionDelay) = remember {
        mutableStateOf(sharedPreferences.getLong("transition_delay", 10000L)) // Default to 10 seconds (10000ms)
    }
    val (apiKey, setApiKey) = remember {
        mutableStateOf(sharedPreferences.getString("api_key", "") ?: "") // Default to empty string
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Settings") })
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .padding(16.dp)
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Transition Delay (seconds)") // Changed label to seconds
            OutlinedTextField(
                value = (transitionDelay / 1000L).toString(), // Display in seconds
                onValueChange = { newValue ->
                    val seconds = newValue.toLongOrNull()
                    val validSeconds = when {
                        seconds == null -> 10L // Default to 10 seconds if parsing fails
                        seconds < 10L -> 10L // Minimum 10 seconds
                        seconds > 600L -> 600L // Maximum 600 seconds
                        else -> seconds // Valid seconds
                    }
                    val milliseconds = validSeconds * 1000L
                    setTransitionDelay(milliseconds) // Update state in milliseconds
                    sharedPreferences.edit().putLong("transition_delay", milliseconds).apply() // Save in milliseconds
                },
                label = { Text("Delay") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number) // Corrected usage
            )

            Text("MovieDB API Key")
            OutlinedTextField(
                value = apiKey,
                onValueChange = { newValue ->
                    setApiKey(newValue)
                    sharedPreferences.edit().putString("api_key", newValue).apply()
                },
                label = { Text("API Key") }
            )

            Button(
                onClick = {
                    val intent = android.content.Intent(context, MainActivity::class.java)
                    context.startActivity(intent)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp) // Add some top padding to separate from fields
            ) {
                Text("Save and Return to Slideshow")
            }
        }
    }
}