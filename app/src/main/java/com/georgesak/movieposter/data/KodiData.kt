package com.georgesak.movieposter.data

import com.google.gson.annotations.SerializedName

data class KodiRpcRequest(
    val jsonrpc: String = "2.0",
    val id: Int,
    val method: String,
    val params: Any? = null
)

data class KodiRpcResponse<T>(
    val id: Int,
    val jsonrpc: String,
    val result: T? = null,
    val error: KodiRpcError? = null
)

data class KodiRpcError(
    val code: Int,
    val message: String
)

data class KodiActivePlayer(
    val playerid: Int,
    val type: String
)

data class KodiPlayerProperties(
    val speed: Int,
    val time: KodiTime,
    val totaltime: KodiTime,
    val percentage: Double
)

data class KodiItemResponse(
    val item: KodiItem
)

data class KodiTime(
    val hours: Int,
    val minutes: Int,
    val seconds: Int,
    val milliseconds: Int
)

data class KodiItem(
    val id: Int,
    val type: String,
    val label: String,
    val title: String? = null,
    val thumbnail: String? = null,
    val file: String? = null,
    @SerializedName("art") val art: KodiArt? = null,
    val year: Int? = null,
    val plot: String? = null,
    val genre: List<String>? = null,
    val director: List<String>? = null,
    val cast: List<KodiCast>? = null,
    val rating: Double? = null,
    val tagline: String? = null,
    val runtime: Int? = null,
    val streamdetails: KodiStreamDetails? = null
)

data class KodiCast(
    val name: String,
    val role: String,
    val thumbnail: String? = null
)

data class KodiStreamDetails(
    val video: List<KodiVideoDetails>? = null,
    val audio: List<KodiAudioDetails>? = null,
    val subtitle: List<KodiSubtitleDetails>? = null
)

data class KodiVideoDetails(
    val codec: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val duration: Int? = null
)

data class KodiAudioDetails(
    val codec: String? = null,
    val language: String? = null,
    val channels: Int? = null
)

data class KodiSubtitleDetails(
    val language: String? = null
)

data class KodiArt(
    val poster: String? = null,
    val fanart: String? = null,
    val banner: String? = null,
    val clearart: String? = null,
    val landscape: String? = null,
    val keyart: String? = null,
    val discart: String? = null
)