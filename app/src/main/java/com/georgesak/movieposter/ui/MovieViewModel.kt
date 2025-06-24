package com.georgesak.movieposter.ui

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewModelScope
import com.georgesak.movieposter.data.Genre
import com.georgesak.movieposter.data.KodiActivePlayer
import com.georgesak.movieposter.data.KodiItem
import com.georgesak.movieposter.data.KodiRpcRequest
import com.georgesak.movieposter.data.Movie
import com.georgesak.movieposter.data.MovieDetail
import com.georgesak.movieposter.network.ApiService
import com.georgesak.movieposter.network.KodiApiClient
import com.georgesak.movieposter.network.KodiApiService
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class MovieViewModel(application: Application) : AndroidViewModel(application) { // Update constructor

    private val _movies = mutableStateOf<List<Movie>>(emptyList())
    val movies: State<List<Movie>> = _movies

    private val _currentMovieWithDetails = MutableStateFlow<MovieDetail?>(null)
    val currentMovieWithDetails: StateFlow<MovieDetail?> = _currentMovieWithDetails.asStateFlow()

    private val _genres = mutableStateOf<List<Genre>>(emptyList())
    val genres: State<List<Genre>> = _genres

    private val _trailerKey = MutableStateFlow<String?>(null)
    val trailerKey: StateFlow<String?> = _trailerKey.asStateFlow()

    private val _kodiPlayingMovie = MutableStateFlow<KodiItem?>(null)
    val kodiPlayingMovie: StateFlow<KodiItem?> = _kodiPlayingMovie.asStateFlow()

    private var monitoringJob: Job? = null

    private val apiService: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.themoviedb.org/3/") // Base URL for The Movie Database (TMDb) API
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }

    init {
        getMovieGenres()
        getPopularMovies()
        startMonitoringKodi()
    }

    private fun getApiKey(): String {
        val sharedPreferences = getApplication<Application>().getSharedPreferences("AppSettings", Context.MODE_PRIVATE)
        return sharedPreferences.getString("api_key", "") ?: ""
    }

    private fun getKodiApiService(): KodiApiService? {
        val sharedPreferences = getApplication<Application>().getSharedPreferences("AppSettings", Context.MODE_PRIVATE)
        val ipAddress = sharedPreferences.getString("kodi_ip_address", "")
        val port = sharedPreferences.getInt("kodi_port", 8080)
        return if (!ipAddress.isNullOrEmpty()) {
            KodiApiClient.createKodiApiService(ipAddress, port)
        } else {
            null
        }
    }

    fun startMonitoringKodi() {
        Log.d(TAG, "Starting Kodi monitoring...")
        monitoringJob?.cancel() // Cancel any existing job
        monitoringJob = viewModelScope.launch {
            val pollingInterval = getApplication<Application>().getSharedPreferences("AppSettings", Context.MODE_PRIVATE).getLong("kodi_polling_interval", 5000L)
            while (true) {
                try {
                    val kodiApiService = getKodiApiService()
                    if (kodiApiService == null) {
                        Log.d(TAG, "Kodi API service is null. Check IP address and port settings.")
                        _kodiPlayingMovie.value = null
                        delay(pollingInterval)
                        continue
                    }
                    Log.d(TAG, "Kodi API service created. Polling interval: $pollingInterval ms")

                    // 1. Get active players
                    val activePlayersRequest = KodiRpcRequest(
                        id = 1,
                        method = "Player.GetActivePlayers"
                    )
                    val activePlayersResponse = kodiApiService.getActivePlayers(activePlayersRequest)

                    if (activePlayersResponse.isSuccessful) {
                        val activePlayers = activePlayersResponse.body()?.result
                        Log.d(TAG, "Active players: $activePlayers")

                        val videoPlayer = activePlayers?.find { it.type == "video" }

                        if (videoPlayer != null) {
                            Log.d(TAG, "Video player found: ${videoPlayer.playerid}")
                            // 2. Get player properties for the active video player
                            val playerPropertiesRequest = KodiRpcRequest(
                                id = 2,
                                method = "Player.GetProperties",
                                params = mapOf(
                                    "playerid" to videoPlayer.playerid,
                                    "properties" to listOf("speed", "time", "totaltime", "percentage")
                                )
                            )
                            val playerPropertiesResponse = kodiApiService.getPlayerProperties(playerPropertiesRequest)

                            if (playerPropertiesResponse.isSuccessful) {
                                val playerProperties = playerPropertiesResponse.body()?.result
                                Log.d(TAG, "Player properties: $playerProperties")
                                if (playerProperties?.speed == 1) { // Speed 1 means playing
                                    // 3. Get item details for the active player
                                    val playerItemRequest = KodiRpcRequest(
                                        id = 3,
                                        method = "Player.GetItem",
                                        params = mapOf(
                                            "playerid" to videoPlayer.playerid,
                                            "properties" to listOf("title", "file", "thumbnail", "fanart", "art", "year", "plot", "genre", "director", "cast", "rating", "tagline", "runtime", "streamdetails")
                                        )
                                    )
                                    val playerItemResponse = kodiApiService.getPlayerItem(playerItemRequest)

                                    if (playerItemResponse.isSuccessful) {
                                        val kodiItem = playerItemResponse.body()?.result?.item
                                        if (kodiItem != null) {
                                            Log.d(TAG, "Kodi item playing: ${kodiItem.label}")
                                            _kodiPlayingMovie.value = kodiItem
                                        } else {
                                            Log.d(TAG, "No item found for active player. Setting kodiPlayingMovie to null.")
                                            _kodiPlayingMovie.value = null
                                        }
                                    } else {
                                        _kodiPlayingMovie.value = null
                                        Log.e(TAG, "Error getting player item: ${playerItemResponse.code()} - ${playerItemResponse.errorBody()?.string()}")
                                    }
                                } else {
                                    Log.d(TAG, "Movie paused or stopped. Speed: ${playerProperties?.speed}. Setting kodiPlayingMovie to null.")
                                    _kodiPlayingMovie.value = null // Paused or stopped
                                }
                            } else {
                                _kodiPlayingMovie.value = null
                                Log.e(TAG, "Error getting player properties: ${playerPropertiesResponse.code()} - ${playerPropertiesResponse.errorBody()?.string()}")
                            }
                        } else {
                            Log.d(TAG, "No video player active. Setting kodiPlayingMovie to null.")
                            _kodiPlayingMovie.value = null // No video player active
                        }
                    } else {
                        _kodiPlayingMovie.value = null
                        Log.e(TAG, "Error getting active players: ${activePlayersResponse.code()} - ${activePlayersResponse.errorBody()?.string()}")
                    }
                } catch (e: Exception) {
                    _kodiPlayingMovie.value = null
                    Log.e(TAG, "Error monitoring Kodi: ${e.message}", e)
                }
                delay(pollingInterval)
            }
        }
    }

    fun stopMonitoringKodi() {
        Log.d(TAG, "Stopping Kodi monitoring.")
        monitoringJob?.cancel()
        monitoringJob = null
        _kodiPlayingMovie.value = null // Clear playing movie when monitoring stops
    }

    override fun onCleared() {
        super.onCleared()
        stopMonitoringKodi()
    }

    fun getPopularMovies(genreIds: Set<Int>? = null) {
        viewModelScope.launch {
            try {
                val apiKey = getApiKey()
                val sharedPreferences = getApplication<Application>().getSharedPreferences("AppSettings", Context.MODE_PRIVATE)
                val originalLanguage = sharedPreferences.getString("original_language", "en") ?: "en"

                if (apiKey.isNotEmpty()) {
                    val response = if (genreIds != null && genreIds.isNotEmpty() && !genreIds.contains(0)) {
                        apiService.getMoviesByGenres(apiKey, genreIds.joinToString("|"), originalLanguage)
                    } else {
                        apiService.getMoviesByGenres(apiKey, "", originalLanguage)
                    }

                    if (response.isSuccessful) {
                        _movies.value = response.body()?.results ?: emptyList()
                        // Fetch details for the first movie if available
                        _movies.value.firstOrNull()?.let { movie ->
                            getMovieDetails(movie.id)
                        }
                    } else {
                        Log.e(TAG, "Error fetching movies: ${response.code()} - ${response.errorBody()?.string()}")
                    }
                } else {
                    Log.e(TAG, "API Key is not set.")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception fetching movies: ${e.message}", e)
            }
        }
    }

    fun getMovieDetails(movieId: Int) {
        viewModelScope.launch {
            try {
                val apiKey = getApiKey()
                if (apiKey.isNotEmpty()) {
                    val movieDetailsResponse = apiService.getMovieDetails(movieId, apiKey)
                    if (movieDetailsResponse.isSuccessful) {
                        val movieDetail = movieDetailsResponse.body()

                        // Fetch release dates for MPAA rating
                        val releaseDatesResponse = apiService.getMovieReleaseDates(movieId, apiKey)
                        if (releaseDatesResponse.isSuccessful) {
                            val usRelease = releaseDatesResponse.body()?.results?.find { it.iso31661 == "US" }
                            val mpaaRating = usRelease?.release_dates?.firstOrNull { it.certification?.isNotEmpty() == true }?.certification

                            _currentMovieWithDetails.value = movieDetail?.copy(mpaaRating = mpaaRating)
                        } else {
                            Log.e(TAG, "Error fetching movie release dates: ${releaseDatesResponse.code()} - ${releaseDatesResponse.errorBody()?.string()}")
                            _currentMovieWithDetails.value = movieDetail // Set movie details even if release dates fail
                        }
                    } else {
                        Log.e(TAG, "Error fetching movie details: ${movieDetailsResponse.code()} - ${movieDetailsResponse.errorBody()?.string()}")
                        _currentMovieWithDetails.value = null
                    }
                } else {
                    Log.e(TAG, "API Key is not set.")
                    _currentMovieWithDetails.value = null
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception fetching movie details: ${e.message}", e)
                _currentMovieWithDetails.value = null
            }
        }
    }

    private fun getMovieGenres() {
        viewModelScope.launch {
            try {
                val apiKey = getApiKey()
                if (apiKey.isNotEmpty()) {
                    val response = apiService.getMovieGenres(apiKey)
                    if (response.isSuccessful) {
                        _genres.value = response.body()?.genres ?: emptyList()
                    } else {
                        Log.e(TAG, "Error fetching genres: ${response.code()} - ${response.errorBody()?.string()}")
                    }
                } else {
                    Log.e(TAG, "API Key is not set.")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception fetching genres: ${e.message}", e)
            }
        }
    }

    fun getMovieTrailer(movieId: Int) {
        viewModelScope.launch {
            try {
                val apiKey = getApiKey() // Get API key from SharedPreferences
                 if (apiKey.isNotEmpty()) {
                    val response = apiService.getMovieVideos(movieId, apiKey)
                    if (response.isSuccessful) {
                        val trailer = response.body()?.results?.firstOrNull { it.site == "YouTube" && it.type == "Trailer" }
                        _trailerKey.value = trailer?.key
                    } else {
                        // Handle error
                        Log.e(TAG, "Error fetching movie videos: ${response.code()} - ${response.errorBody()?.string()}")
                        _trailerKey.value = null // Clear trailer key on error
                    }
                } else {
                    Log.e(TAG, "API Key is not set.")
                    _trailerKey.value = null // Clear trailer key if API key is not set
                }
            } catch (e: Exception) {
                // Handle exception
                Log.e(TAG, "Exception fetching movie videos: ${e.message}", e)
                _trailerKey.value = null // Clear trailer key on exception
            }
        }
    }

    fun clearTrailerKey() {
        _trailerKey.value = null
    }

    companion object {
        private const val TAG = "MovieViewModel"

        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                val application = extras[APPLICATION_KEY]
                if (modelClass.isAssignableFrom(MovieViewModel::class.java)) {
                    return MovieViewModel(application as Application) as T
                }
                throw IllegalArgumentException("Unknown ViewModel class")
            }
        }
    }
}