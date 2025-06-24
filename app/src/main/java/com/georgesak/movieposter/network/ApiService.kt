package com.georgesak.movieposter.network

import com.georgesak.movieposter.data.MovieResponse
import com.georgesak.movieposter.data.VideoResponse
import com.georgesak.movieposter.data.GenreResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface ApiService {
    @GET("discover/movie")
    suspend fun getMoviesByGenres(
        @Query("api_key") apiKey: String,
        @Query("with_genres") genreIds: String, // Comma-separated genre IDs
        @Query("with_original_language") language: String = "en", // Add language parameter with default
        @Query("with_release_type") releaseType: String = "2|3" // Add release type parameter with default
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

    @GET("movie/{movie_id}") // Endpoint for movie details
    suspend fun getMovieDetails(
        @Path("movie_id") movieId: Int,
        @Query("api_key") apiKey: String
    ): Response<com.georgesak.movieposter.data.MovieDetail> // Return a single MovieDetail object

    @GET("movie/{movie_id}/release_dates") // Endpoint for movie release dates
    suspend fun getMovieReleaseDates(
        @Path("movie_id") movieId: Int,
        @Query("api_key") apiKey: String
    ): Response<com.georgesak.movieposter.data.ReleaseDateResponse> // Return ReleaseDateResponse
}