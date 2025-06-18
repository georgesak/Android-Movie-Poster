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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.movieposter.data.Genre
import com.example.movieposter.ui.MovieViewModel
import com.example.movieposter.ui.theme.MoviePosterTheme
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.semantics.Role

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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(movieViewModel: MovieViewModel = viewModel()) {
    val context = LocalContext.current
    val sharedPreferences = context.getSharedPreferences("AppSettings", Context.MODE_PRIVATE)

    val (transitionDelay, setTransitionDelay) = remember {
        mutableStateOf(sharedPreferences.getLong("transition_delay", 10000L))
    }
    val (apiKey, setApiKey) = remember {
        mutableStateOf(sharedPreferences.getString("api_key", "") ?: "")
    }

    val genres by movieViewModel.genres
    val (selectedGenreIds, setSelectedGenreIds) = remember {
        val savedGenreIds = sharedPreferences.getStringSet("selected_genre_ids", emptySet())
            ?.mapNotNull { it.toIntOrNull() }?.toSet() ?: emptySet()
        mutableStateOf(savedGenreIds)
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
            Text("Transition Delay (seconds)")
            OutlinedTextField(
                value = (transitionDelay / 1000L).toString(),
                onValueChange = { newValue ->
                    val seconds = newValue.toLongOrNull()
                    val validSeconds = when {
                        seconds == null -> 10L
                        seconds < 10L -> 10L
                        seconds > 600L -> 600L
                        else -> seconds
                    }
                    val milliseconds = validSeconds * 1000L
                    setTransitionDelay(milliseconds)
                    sharedPreferences.edit().putLong("transition_delay", milliseconds).apply()
                },
                label = { Text("Delay") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
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

            Text("Movie Genre(s)")
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .toggleable(
                            value = selectedGenreIds.isEmpty() || selectedGenreIds.contains(0),
                            onValueChange = {
                                setSelectedGenreIds(setOf(0))
                                sharedPreferences.edit().putStringSet("selected_genre_ids", setOf("0")).apply()
                            },
                            role = Role.Checkbox
                        )
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = selectedGenreIds.isEmpty() || selectedGenreIds.contains(0),
                            onCheckedChange = null // null recommended for accessibility with toggleable
                    )
                    Text("All")
                }
                genres.forEach { genre ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .toggleable(
                                value = selectedGenreIds.contains(genre.id),
                                onValueChange = {
                                    val newSelection = if (selectedGenreIds.contains(genre.id)) {
                                        selectedGenreIds - genre.id
                                    } else {
                                        selectedGenreIds + genre.id
                                    }
                                    setSelectedGenreIds(newSelection.filter { it != 0 }.toSet())
                                    sharedPreferences.edit().putStringSet("selected_genre_ids", newSelection.map { it.toString() }.toSet()).apply()
                                },
                                role = Role.Checkbox
                            )
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = selectedGenreIds.contains(genre.id),
                            onCheckedChange = null // null recommended for accessibility with toggleable
                        )
                        Text(genre.name)
                    }
                }
            }

            Button(
                onClick = {
                    val savedGenreIds = sharedPreferences.getStringSet("selected_genre_ids", emptySet())
                        ?.mapNotNull { it.toIntOrNull() }?.toSet() ?: emptySet()
                    movieViewModel.getPopularMovies(savedGenreIds)
                    val intent = android.content.Intent(context, MainActivity::class.java)
                    context.startActivity(intent)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
            ) {
                Text("Save and Return to Slideshow")
            }
        }
    }
}