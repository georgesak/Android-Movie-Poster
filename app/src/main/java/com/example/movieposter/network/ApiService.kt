package com.example.movieposter.network

import com.example.movieposter.data.MovieResponse
import com.example.movieposter.data.VideoResponse
import com.example.movieposter.data.GenreResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface ApiService {
    @GET("movie/popular")
    suspend fun getPopularMovies(
        @Query("api_key") apiKey: String
    ): Response<MovieResponse>

    @GET("discover/movie")
    suspend fun getMoviesByGenres(
        @Query("api_key") apiKey: String,
        @Query("with_genres") genreIds: String // Comma-separated genre IDs
    ): Response<MovieResponse>

    @GET("genre/movie/list") // Endpoint for movie genre list
    suspend fun getMovieGenres(
        @Query("api_key") apiKey: String
    ): Response<GenreResponse>

    @GET("movie/{movie_id}/videos") // Add endpoint for movie videos
    suspend fun getMovieVideos(
        @Path("movie_id") movieId: Int, // Add movie_id path parameter
        @Query("api_key") apiKey: String
    ): Response<VideoResponse> // Return VideoResponse
}