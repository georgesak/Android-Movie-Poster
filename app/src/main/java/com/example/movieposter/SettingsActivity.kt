package com.example.movieposter

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
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
    var transitionDelay by remember {
        mutableStateOf(sharedPreferences.getLong("transition_delay", 10000L)) // Default to 10 seconds (10000ms)
    }
    var apiKey by remember {
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
                    transitionDelay = milliseconds // Update state in milliseconds
                    with(sharedPreferences.edit()) {
                        putLong("transition_delay", milliseconds) // Save in milliseconds
                        apply()
                    }
                },
                label = { Text("Delay") },
                keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number) // Corrected usage
            )

            Text("MovieDB API Key")
            OutlinedTextField(
                value = apiKey,
                onValueChange = { newValue ->
                    apiKey = newValue
                    with(sharedPreferences.edit()) {
                        putString("api_key", newValue)
                        apply()
                    }
                },
                label = { Text("API Key") }
            )

            Spacer(modifier = Modifier.weight(1f)) // Push the button to the bottom
            Button(
                onClick = {
                    val intent = android.content.Intent(context, MainActivity::class.java)
                    context.startActivity(intent)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save and Return to Slideshow")
            }
        }
    }
}