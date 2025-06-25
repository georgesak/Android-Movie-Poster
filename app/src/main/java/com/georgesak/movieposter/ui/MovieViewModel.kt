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
import com.georgesak.movieposter.MoviePosterApplication
import com.georgesak.movieposter.data.Genre
import com.georgesak.movieposter.data.KodiItem
import com.georgesak.movieposter.data.Movie
import com.georgesak.movieposter.data.MovieDetail
import com.georgesak.movieposter.network.ApiService
import com.georgesak.movieposter.network.KodiMonitor
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class MovieViewModel(application: Application, private val kodiMonitor: KodiMonitor) : AndroidViewModel(application) {

    private val _movies = mutableStateOf<List<Movie>>(emptyList())
    val movies: State<List<Movie>> = _movies

    private val _currentMovieWithDetails = MutableStateFlow<MovieDetail?>(null)
    val currentMovieWithDetails: StateFlow<MovieDetail?> = _currentMovieWithDetails.asStateFlow()

    private val _genres = mutableStateOf<List<Genre>>(emptyList())
    val genres: State<List<Genre>> = _genres

    private val _trailerKey = MutableStateFlow<String?>(null)
    val trailerKey: StateFlow<String?> = _trailerKey.asStateFlow()

    val kodiPlayingMovie: StateFlow<KodiItem?> = kodiMonitor.kodiPlayingMovie

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
        // Kodi monitoring is now handled by KodiMonitor in MoviePosterApplication
    }

    private fun getApiKey(): String {
        val sharedPreferences = getApplication<Application>().getSharedPreferences("AppSettings", Context.MODE_PRIVATE)
        return sharedPreferences.getString("api_key", "") ?: ""
    }

    override fun onCleared() {
        super.onCleared()
        // Kodi monitoring is now handled by KodiMonitor, no need to stop here
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
                            Log.e(TAG, "MPAA: ${mpaaRating}")
                            _currentMovieWithDetails.value = movieDetail?.copy(mpaaRating = mpaaRating ?: "N/A")
                        } else {
                            Log.e(TAG, "Error fetching movie release dates: ${releaseDatesResponse.code()} - ${releaseDatesResponse.errorBody()?.string()}")
                            _currentMovieWithDetails.value = movieDetail
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
                val application = extras[APPLICATION_KEY] as Application
                val kodiMonitor = (application as MoviePosterApplication).kodiMonitor
                if (modelClass.isAssignableFrom(MovieViewModel::class.java)) {
                    return MovieViewModel(application, kodiMonitor) as T
                }
                throw IllegalArgumentException("Unknown ViewModel class")
            }
        }
    }
}