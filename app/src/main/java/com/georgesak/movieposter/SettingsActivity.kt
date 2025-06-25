package com.georgesak.movieposter

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
import androidx.compose.material3.Card
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import com.georgesak.movieposter.data.Genre
import com.georgesak.movieposter.ui.MovieViewModel
import com.georgesak.movieposter.ui.theme.MoviePosterTheme
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.RadioButton
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll

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

    val (trailerPlacement, setTrailerPlacement) = remember {
        mutableStateOf(sharedPreferences.getString("trailer_placement", "Bottom") ?: "Bottom")
    }

    val (originalLanguage, setOriginalLanguage) = remember {
        mutableStateOf(sharedPreferences.getString("original_language", "en") ?: "en")
    }

    val (showRuntime, setShowRuntime) = remember {
        mutableStateOf(sharedPreferences.getBoolean("show_runtime", true))
    }
    val (showReleaseDate, setShowReleaseDate) = remember {
        mutableStateOf(sharedPreferences.getBoolean("show_release_date", true))
    }
    val (showMpaaRating, setShowMpaaRating) = remember {
        mutableStateOf(sharedPreferences.getBoolean("show_mpaa_rating", true))
    }

    val (kodiIpAddress, setKodiIpAddress) = remember {
        mutableStateOf(sharedPreferences.getString("kodi_ip_address", "") ?: "")
    }
    val (kodiPort, setKodiPort) = remember {
        mutableStateOf(sharedPreferences.getInt("kodi_port", 8080))
    }
    val (kodiPollingInterval, setKodiPollingInterval) = remember {
        mutableStateOf(sharedPreferences.getLong("kodi_polling_interval", 5000L)) // Default 5 seconds
    }

    val (kodiUsername, setKodiUsername) = remember {
        mutableStateOf(sharedPreferences.getString("kodi_username", "") ?: "")
    }

    val (kodiPassword, setKodiPassword) = remember {
        mutableStateOf(sharedPreferences.getString("kodi_password", "") ?: "")
    }

    fun saveSettings(
        sharedPreferences: SharedPreferences,
        transitionDelay: Long,
        apiKey: String,
        selectedGenreIds: Set<Int>,
        trailerPlacement: String,
        originalLanguage: String,
        showRuntime: Boolean,
        showReleaseDate: Boolean,
        showMpaaRating: Boolean,
        kodiIpAddress: String,
        kodiPort: Int,
        kodiPollingInterval: Long,
        kodiUsername: String,
        kodiPassword: String,
        movieViewModel: MovieViewModel,
        context: Context
    ) {
        sharedPreferences.edit().apply {
            putLong("transition_delay", transitionDelay)
            putString("api_key", apiKey)
            putStringSet("selected_genre_ids", selectedGenreIds.map { it.toString() }.toSet())
            putString("trailer_placement", trailerPlacement)
            putString("original_language", originalLanguage)
            putBoolean("show_runtime", showRuntime)
            putBoolean("show_release_date", showReleaseDate)
            putBoolean("show_mpaa_rating", showMpaaRating)
            putString("kodi_ip_address", kodiIpAddress)
            putInt("kodi_port", kodiPort)
            putLong("kodi_polling_interval", kodiPollingInterval)
            putString("kodi_username", kodiUsername)
            putString("kodi_password", kodiPassword)
            apply()
        }
        movieViewModel.getPopularMovies(selectedGenreIds)
        val intent = android.content.Intent(context, MoviePosterActivity::class.java)
        context.startActivity(intent)
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                actions = {
                    Button(
                        onClick = {
                            (context as? ComponentActivity)?.finishAffinity()
                        },
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Text("Quit")
                    }
                    Button(
                        onClick = {
                            // Navigate back to MainActivity without saving
                            val intent = android.content.Intent(context, MoviePosterActivity::class.java)
                            context.startActivity(intent)
                        },
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = {
                            saveSettings(
                                sharedPreferences,
                                transitionDelay,
                                apiKey,
                                selectedGenreIds,
                                trailerPlacement,
                                originalLanguage,
                                showRuntime,
                                showReleaseDate,
                                showMpaaRating,
                                kodiIpAddress,
                                kodiPort,
                                kodiPollingInterval,
                                kodiUsername,
                                kodiPassword,
                                movieViewModel,
                                context
                            )
                        },
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Text("Save")
                    }
                }
            )
        }
    ) { paddingValues ->
        val scrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.padding(top = 16.dp))

            // General Settings Section
            SettingsSection(title = "General Settings") {
                Text("Transition Delay (10 to 600 seconds)", style = MaterialTheme.typography.titleMedium)
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
                    },
                    label = { Text("Delay") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.padding(top = 8.dp))
                Text("MovieDB API Key", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { newValue ->
                        setApiKey(newValue)
                    },
                    label = { Text("API Key") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.padding(top = 8.dp))
                Text("Original Language (e.g., en|fr|es)", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = originalLanguage,
                    onValueChange = { newValue ->
                        setOriginalLanguage(newValue)
                    },
                    label = { Text("Language Code") },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Genre Selection Section
            SettingsSection(title = "Movie Genre(s)") {
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
            }

            // Trailer Placement Section
            SettingsSection(title = "Trailer Placement") {
                val placementOptions = listOf("Top", "Middle", "Bottom")
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.Start
                ) {
                    placementOptions.forEach { option ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .toggleable(
                                    value = trailerPlacement == option,
                                    onValueChange = { setTrailerPlacement(option) },
                                    role = Role.RadioButton
                                )
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            androidx.compose.material3.RadioButton(
                                selected = trailerPlacement == option,
                                onClick = null // null recommended for accessibility with toggleable
                            )
                            Text(option)
                        }
                    }
                }
            }

            // Poster Information Display Settings
            SettingsSection(title = "Poster Information Display") {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .toggleable(
                            value = showRuntime,
                            onValueChange = { setShowRuntime(it) },
                            role = Role.Checkbox
                        )
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = showRuntime,
                        onCheckedChange = null
                    )
                    Text("Show Runtime")
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .toggleable(
                            value = showReleaseDate,
                            onValueChange = { setShowReleaseDate(it) },
                            role = Role.Checkbox
                        )
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = showReleaseDate,
                        onCheckedChange = null
                    )
                    Text("Show Release Date")
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .toggleable(
                            value = showMpaaRating,
                            onValueChange = { setShowMpaaRating(it) },
                            role = Role.Checkbox
                        )
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = showMpaaRating,
                        onCheckedChange = null
                    )
                    Text("Show MPAA Rating")
                }
            }

            // Kodi Settings Section
            SettingsSection(title = "Kodi Settings") {
                Text("Kodi IP Address", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = kodiIpAddress,
                    onValueChange = { setKodiIpAddress(it) },
                    label = { Text("IP Address") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.padding(top = 8.dp))
                Text("Kodi Port", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = kodiPort.toString(),
                    onValueChange = { newValue ->
                        setKodiPort(newValue.toIntOrNull() ?: 8080)
                    },
                    label = { Text("Port") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.padding(top = 8.dp))
                Text("Kodi Polling Interval (seconds)", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = (kodiPollingInterval / 1000L).toString(),
                    onValueChange = { newValue ->
                        val seconds = newValue.toLongOrNull()
                        val validSeconds = when {
                            seconds == null -> 5L
                            seconds < 1L -> 1L // Minimum 1 second
                            else -> seconds
                        }
                        setKodiPollingInterval(validSeconds * 1000L)
                    },
                    label = { Text("Interval") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.padding(top = 8.dp))
                Text("Kodi Username", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = kodiUsername,
                    onValueChange = { setKodiUsername(it) },
                    label = { Text("Username") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.padding(top = 8.dp))
                Text("Kodi Password", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = kodiPassword,
                    onValueChange = { setKodiPassword(it) },
                    label = { Text("Password") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.weight(1f)) // Push content to top if needed
        }
    }
}

@Composable
fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Divider(modifier = Modifier.padding(vertical = 8.dp))
            content()
        }
    }
}