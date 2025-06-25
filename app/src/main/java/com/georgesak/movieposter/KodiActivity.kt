package com.georgesak.movieposter

import android.content.Context
import android.content.Intent
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
    val sharedPreferences = context.getSharedPreferences("AppSettings", Context.MODE_PRIVATE)
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
        val kodiIpAddress = sharedPreferences.getString("kodi_ip_address", "") ?: ""
        val kodiPort = sharedPreferences.getInt("kodi_port", 8080)
        val kodiBaseUrl = if (kodiIpAddress.isNotEmpty()) "http://$kodiIpAddress:$kodiPort/image/" else ""

        val imageUrl = kodiMovie.art?.poster ?: kodiMovie.art?.fanart ?: kodiMovie.thumbnail
        val decodedAndReplacedUrl = imageUrl?.let { url ->
            try {
                val tempUrl = url
                if (tempUrl.startsWith("image://")) {
                    tempUrl.replace("image://", "")
                }
                val decodedUrl = kodiBaseUrl + java.net.URLEncoder.encode(tempUrl, "UTF-8")
                decodedUrl
            } catch (e: Exception) {
                Log.e("KodiActivity", "Error decoding or replacing Kodi image URL: $url", e)
                url // Return original URL on error
            }
        }
        val username = sharedPreferences.getString("kodi_username", "") ?: ""
        val password = sharedPreferences.getString("kodi_password", "") ?: ""
        var authorizationHeader = ""
        if (username != "" && password != "") {
            val credentials = "$username:$password"
            val encodedCredentials = android.util.Base64.encodeToString(credentials.toByteArray(), android.util.Base64.NO_WRAP)
            authorizationHeader = "Basic $encodedCredentials"
        }
        Log.d("KodiActivity", "decodedAndReplacedUrl: ${decodedAndReplacedUrl}")
        Box(modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(decodedAndReplacedUrl)
                    .addHeader("Authorization", authorizationHeader)
                    .crossfade(1000)
                    .build(),
                contentDescription = kodiMovie.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )

            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.7f))
                    .padding(vertical = 20.dp),
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
}