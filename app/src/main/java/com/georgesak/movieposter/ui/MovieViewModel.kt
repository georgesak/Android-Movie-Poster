package com.georgesak.movieposter.ui

import android.app.Application
import android.content.Context
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.georgesak.movieposter.data.Genre
import com.georgesak.movieposter.data.Movie
import com.georgesak.movieposter.data.ReleaseDateResponse // Import ReleaseDateResponse
import com.georgesak.movieposter.network.ApiService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class MovieViewModel(application: Application) : AndroidViewModel(application) { // Update constructor

    private val _movies = mutableStateOf<List<Movie>>(emptyList())
    val movies: State<List<Movie>> = _movies

    private val _currentMovieWithDetails = MutableStateFlow<Movie?>(null)
    val currentMovieWithDetails: StateFlow<Movie?> = _currentMovieWithDetails

    private val _genres = mutableStateOf<List<Genre>>(emptyList())
    val genres: State<List<Genre>> = _genres

    private val _trailerKey = MutableStateFlow<String?>(null)
    val trailerKey: StateFlow<String?> = _trailerKey

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
    }

    private fun getApiKey(): String {
        val sharedPreferences = getApplication<Application>().getSharedPreferences("AppSettings", Context.MODE_PRIVATE)
        return sharedPreferences.getString("api_key", "") ?: ""
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
                        println("Error fetching movies: ${response.errorBody()}")
                    }
                } else {
                    println("API Key is not set.")
                }
            } catch (e: Exception) {
                println("Exception fetching movies: ${e.message}")
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
                        val movie = movieDetailsResponse.body()

                        // Fetch release dates for MPAA rating
                        val releaseDatesResponse = apiService.getMovieReleaseDates(movieId, apiKey)
                        if (releaseDatesResponse.isSuccessful) {
                            val usRelease = releaseDatesResponse.body()?.results?.find { it.iso31661 == "US" }
                            val mpaaRating = usRelease?.release_dates?.firstOrNull { it.certification?.isNotEmpty() == true }?.certification

                            _currentMovieWithDetails.value = movie?.copy(mpaaRating = mpaaRating)
                        } else {
                            println("Error fetching movie release dates: ${releaseDatesResponse.errorBody()}")
                            _currentMovieWithDetails.value = movie // Set movie details even if release dates fail
                        }
                    } else {
                        println("Error fetching movie details: ${movieDetailsResponse.errorBody()}")
                        _currentMovieWithDetails.value = null
                    }
                } else {
                    println("API Key is not set.")
                    _currentMovieWithDetails.value = null
                }
            } catch (e: Exception) {
                println("Exception fetching movie details: ${e.message}")
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
                        println("Error fetching genres: ${response.errorBody()}")
                    }
                } else {
                    println("API Key is not set.")
                }
            } catch (e: Exception) {
                println("Exception fetching genres: ${e.message}")
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
                        val trailer = response.body()?.results?.find { it.site == "YouTube" && it.type == "Trailer" }
                        _trailerKey.value = trailer?.key
                    } else {
                        // Handle error
                        println("Error fetching movie videos: ${response.errorBody()}")
                        _trailerKey.value = null // Clear trailer key on error
                    }
                } else {
                    println("API Key is not set.")
                    _trailerKey.value = null // Clear trailer key if API key is not set
                }
            } catch (e: Exception) {
                // Handle exception
                println("Exception fetching movie videos: ${e.message}")
                _trailerKey.value = null // Clear trailer key on exception
            }
        }
    }

    fun clearTrailerKey() {
        _trailerKey.value = null
    }

    companion object {
        val Factory: androidx.lifecycle.ViewModelProvider.Factory =
            object : androidx.lifecycle.ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : androidx.lifecycle.ViewModel> create(
                    modelClass: Class<T>,
                    extras: androidx.lifecycle.viewmodel.CreationExtras
                ): T {
                    val application =
                        extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY as androidx.lifecycle.viewmodel.CreationExtras.Key<Application>] as Application
                    return MovieViewModel(application) as T
                }
            }
    }
}