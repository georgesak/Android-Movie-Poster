package com.example.movieposter.network

import com.example.movieposter.data.MovieResponse
import com.example.movieposter.data.VideoResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface ApiService {
    @GET("movie/popular")
    suspend fun getPopularMovies(
        @Query("api_key") apiKey: String
    ): Response<MovieResponse>

    @GET("movie/{movie_id}/videos") // Add endpoint for movie videos
    suspend fun getMovieVideos(
        @Path("movie_id") movieId: Int, // Add movie_id path parameter
        @Query("api_key") apiKey: String
    ): Response<VideoResponse> // Return VideoResponse
}