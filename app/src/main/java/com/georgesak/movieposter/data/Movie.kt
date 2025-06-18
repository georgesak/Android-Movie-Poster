package com.georgesak.movieposter.data

import com.google.gson.annotations.SerializedName

data class Movie(
    val id: Int, // Add movie ID
    val title: String,
    @SerializedName("poster_path") // Assuming the API uses "poster_path" for the image URL
    val posterPath: String?
)

data class MovieResponse(
    val results: List<Movie>
)

data class Genre(
    val id: Int,
    val name: String
)

data class GenreResponse(
    val genres: List<Genre>
)

data class Video(
    val key: String, // YouTube video key
    val site: String, // e.g., "YouTube"
    val type: String // e.g., "Trailer"
)

data class VideoResponse(
    val results: List<Video>
)