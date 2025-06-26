package com.georgesak.movieposter

import androidx.compose.ui.unit.dp

object Constants {
    // SharedPreferences
    const val APP_SETTINGS_PREFS = "AppSettings"
    const val API_KEY = "api_key"
    const val KODI_IP_ADDRESS = "kodi_ip_address"
    const val KODI_PORT = "kodi_port"
    const val KODI_USERNAME = "kodi_username"
    const val KODI_PASSWORD = "kodi_password"
    const val TRANSITION_DELAY = "transition_delay"
    const val SELECTED_GENRE_IDS = "selected_genre_ids"
    const val TRAILER_PLACEMENT = "trailer_placement"
    const val SHOW_RUNTIME = "show_runtime"
    const val SHOW_RELEASE_DATE = "show_release_date"
    const val SHOW_MPAA_RATING = "show_mpaa_rating"
    const val SHOW_TAGLINE = "show_tagline"

    // Default Values
    const val DEFAULT_KODI_PORT = 8080
    const val DEFAULT_TRANSITION_DELAY = 10000L
    const val DEFAULT_TRAILER_PLACEMENT = "Bottom"

    // Image URLs
    const val TMDB_IMAGE_BASE_URL = "https://image.tmdb.org/t/p/w500"
    const val HTTP_PREFIX = "http://"

    // Animation Durations
    const val SWIPE_ANIMATION_DURATION_MILLIS = 300
    const val AUTO_TRANSITION_ANIMATION_DURATION_MILLIS = 2000
    const val CROSSFADE_DURATION_MILLIS = 1000

    // UI Values
    val PADDING_SMALL = 2.dp
    val PADDING_MEDIUM = 20.dp
    val PADDING_LARGE = 40.dp
    val PADDING_TRAILER_BOTTOM = 100.dp
    const val ALPHA_TRANSLUCENT = 0.25f
    const val SWIPE_THRESHOLD_PERCENTAGE = 0.33f
    const val KODI_OVERLAY_ALPHA = 0.7f
    const val DIALOG_BACKGROUND_ALPHA = 0.8f
}
