package com.georgesak.movieposter.data

import com.georgesak.movieposter.data.Genre
data class MovieDetail(
    val id: Int,
    val title: String,
    val overview: String,
    val posterPath: String?,
    val backdropPath: String?,
    val releaseDate: String?,
    val runtime: Int?,
    val mpaaRating: String?,
    val video: Boolean,
    val voteAverage: Double,
    val voteCount: Int,
    val genres: List<Genre>,
    val videos: VideoResponse
)