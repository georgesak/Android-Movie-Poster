package com.example.movieposter.ui

import android.app.Application
import android.content.Context
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.movieposter.data.Movie
import com.example.movieposter.network.ApiService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class MovieViewModel(application: Application) : AndroidViewModel(application) { // Update constructor

    private val _movies = mutableStateOf<List<Movie>>(emptyList())
    val movies: State<List<Movie>> = _movies

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
        getPopularMovies()
    }

    private fun getApiKey(): String {
        val sharedPreferences = getApplication<Application>().getSharedPreferences("AppSettings", Context.MODE_PRIVATE)
        return sharedPreferences.getString("api_key", "") ?: ""
    }

    private fun getPopularMovies() {
        viewModelScope.launch {
            try {
                val apiKey = getApiKey() // Get API key from SharedPreferences
                if (apiKey.isNotEmpty()) {
                    val response = apiService.getPopularMovies(apiKey)
                    if (response.isSuccessful) {
                        _movies.value = response.body()?.results ?: emptyList()
                    } else {
                        // Handle error
                        println("Error fetching movies: ${response.errorBody()}")
                    }
                } else {
                    println("API Key is not set.")
                }
            } catch (e: Exception) {
                // Handle exception
                println("Exception fetching movies: ${e.message}")
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