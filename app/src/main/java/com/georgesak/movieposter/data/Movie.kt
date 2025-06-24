package com.georgesak.movieposter.data

import com.google.gson.annotations.SerializedName

data class Movie(
    val id: Int, // Add movie ID
    val title: String,
    @SerializedName("poster_path") // Assuming the API uses "poster_path" for the image URL
    val posterPath: String?,
    val runtime: Int?, // Add runtime field
    @SerializedName("release_date")
    val releaseDate: String?, // Add release date field
    @SerializedName("certification")
    val mpaaRating: String? // Add MPAA rating field
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

data class ReleaseDateResponse(
    val id: Int,
    val results: List<ReleaseDateResult>
)

data class ReleaseDateResult(
    @SerializedName("iso_3166_1")
    val iso31661: String, // Country code, e.g., "US"
    val release_dates: List<ReleaseDate>
)

data class ReleaseDate(
    val certification: String?, // MPAA rating
    @SerializedName("iso_639_1")
    val iso6391: String?,
    val release_date: String,
    val type: Int,
    val note: String?
)