package com.georgesak.movieposter

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.georgesak.movieposter.ui.MovieViewModel
import com.georgesak.movieposter.ui.theme.MoviePosterTheme
import com.georgesak.movieposter.data.KodiItem
import com.georgesak.movieposter.Constants

class KodiActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("KodiActivity", "onCreate: KodiActivity created")
        enableEdgeToEdge()

        val window = (this.window)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).let { controller ->
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        setContent {
            MoviePosterTheme {
                KodiScreen()
            }
        }
    }
}

@Composable
fun KodiScreen(
    movieViewModel: MovieViewModel = viewModel(factory = MovieViewModel.Factory)
) {
    val context = LocalContext.current
    val sharedPreferences = context.getSharedPreferences(Constants.APP_SETTINGS_PREFS, Context.MODE_PRIVATE)
    val kodiPlayingMovie by movieViewModel.kodiPlayingMovie.collectAsState()

    LaunchedEffect(kodiPlayingMovie) {
        if (kodiPlayingMovie == null) {
            Log.d("KodiActivity", "kodiPlayingMovie is null, finishing KodiActivity")
            // If Kodi stops playing, finish this activity to return to MoviePosterActivity
            (context as? KodiActivity)?.finish()
        } else {
            Log.d("KodiActivity", "kodiPlayingMovie is not null: ${kodiPlayingMovie!!.title ?: kodiPlayingMovie!!.label}")
        }
    }

    kodiPlayingMovie?.let { kodiMovie ->
        val decodedAndReplacedUrl = buildKodiImageUrl(context, sharedPreferences, kodiMovie)
        val authorizationHeader = getKodiAuthorizationHeader(sharedPreferences)
        KodiMovieDisplay(kodiMovie, decodedAndReplacedUrl, authorizationHeader)
    }
}

@Composable
private fun buildKodiImageUrl(context: Context, sharedPreferences: android.content.SharedPreferences, kodiMovie: KodiItem): String? {
    val kodiIpAddress = sharedPreferences.getString(Constants.KODI_IP_ADDRESS, "") ?: ""
    val kodiPort = sharedPreferences.getInt(Constants.KODI_PORT, Constants.DEFAULT_KODI_PORT)
    val kodiBaseUrl = if (kodiIpAddress.isNotEmpty()) "${Constants.HTTP_PREFIX}$kodiIpAddress:$kodiPort/image/" else ""

    val imageUrl = kodiMovie.art?.poster ?: kodiMovie.art?.fanart ?: kodiMovie.thumbnail
    Log.d("KodiActivity", "imageUrl: $imageUrl")

    return imageUrl?.let { url ->
        try {
            val decodedUrl = kodiBaseUrl + java.net.URLEncoder.encode(url, "UTF-8")
            decodedUrl
        } catch (e: Exception) {
            Log.e("KodiActivity", "Error decoding or replacing Kodi image URL: $url", e)
            url // Return original URL on error
        }
    }
}

private fun getKodiAuthorizationHeader(sharedPreferences: SharedPreferences): String {
    val username = sharedPreferences.getString(Constants.KODI_USERNAME, "") ?: ""
    val password = sharedPreferences.getString(Constants.KODI_PASSWORD, "") ?: ""
    if (username.isEmpty() || password.isEmpty()) {
        return ""
    }
    val credentials = "$username:$password"
    val encodedCredentials = android.util.Base64.encodeToString(credentials.toByteArray(), android.util.Base64.NO_WRAP)
    return "Basic $encodedCredentials"
}

@Composable
fun KodiMovieDisplay(
    kodiMovie: KodiItem,
    decodedAndReplacedUrl: String?,
    authorizationHeader: String
) {
    Box(modifier = Modifier
        .fillMaxSize()
        .background(Color.Black)) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(decodedAndReplacedUrl)
                .addHeader("Authorization", authorizationHeader)
                .crossfade(Constants.CROSSFADE_DURATION_MILLIS)
                .build(),
            contentDescription = kodiMovie.title,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit
        )

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = Constants.KODI_OVERLAY_ALPHA))
                .padding(vertical = Constants.PADDING_MEDIUM),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Now Playing: " + (kodiMovie.title ?: kodiMovie.label),
                style = MaterialTheme.typography.headlineSmall,
                color = Color.Gray
            )
        }
    }
}