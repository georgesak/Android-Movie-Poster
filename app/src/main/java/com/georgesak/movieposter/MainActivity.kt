package com.georgesak.movieposter

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import com.georgesak.movieposter.data.MovieDetail
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import com.georgesak.movieposter.ui.theme.Grey
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.georgesak.movieposter.ui.MovieViewModel
import com.georgesak.movieposter.ui.theme.MoviePosterTheme
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.options.IFramePlayerOptions
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.YouTubePlayerView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import android.util.Log

@OptIn(ExperimentalMaterial3Api::class) // Add opt-in for ExperimentalMaterial3Api
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Check if API key is set, if not, navigate to SettingsActivity
        val sharedPreferences = getSharedPreferences("AppSettings", Context.MODE_PRIVATE)
        val apiKey = sharedPreferences.getString("api_key", "")

        if (apiKey.isNullOrEmpty()) {
            val settingsIntent = Intent(this, SettingsActivity::class.java)
            startActivity(settingsIntent)
            finish() // Finish MainActivity so the user can't go back to it without setting the key
        } else {
            // Request POST_NOTIFICATIONS permission for Android 13+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(
                        this,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }

            setContent {
                // Enable immersive mode
                val window = (this.window)
                WindowCompat.setDecorFitsSystemWindows(window, false)
                WindowInsetsControllerCompat(window, window.decorView).let { controller ->
                    controller.hide(WindowInsetsCompat.Type.systemBars())
                    controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                }

                MoviePosterTheme {
                    MoviePosterScreen()
                }
            }
        }
    }

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                // Permission is granted. You can start the service here if needed,
                // but it's already started in onCreate.
            } else {
                // Permission is denied. Handle this case, perhaps inform the user.
            }
        }
}

@Composable
fun MoviePosterScreen(
    movieViewModel: MovieViewModel = viewModel(factory = MovieViewModel.Factory)
) { // Use the factory
    val movies = movieViewModel.movies.value
    val currentMovieWithDetails: MovieDetail? by movieViewModel.currentMovieWithDetails.collectAsState() // Observe movie details
    var currentMovieIndex by remember { mutableStateOf(0) }
    var isPaused by remember { mutableStateOf(false) } // State to track if slideshow is paused
    val context = LocalContext.current // Get the current context
    val sharedPreferences = context.getSharedPreferences("AppSettings", Context.MODE_PRIVATE) // Get SharedPreferences
    val transitionDelay = sharedPreferences.getLong("transition_delay", 10000L) // Read transition delay (default 10 seconds)
    val selectedGenreIds = remember {
        mutableStateOf(sharedPreferences.getStringSet("selected_genre_ids", emptySet())?.mapNotNull { it.toIntOrNull() }?.toSet() ?: emptySet())
    }
    val trailerPlacement = remember {
        mutableStateOf(sharedPreferences.getString("trailer_placement", "Bottom") ?: "Bottom")
    }
    val showRuntime = remember {
        mutableStateOf(sharedPreferences.getBoolean("show_runtime", true))
    }
    val showReleaseDate = remember {
        mutableStateOf(sharedPreferences.getBoolean("show_release_date", true))
    }
    val showMpaaRating = remember {
        mutableStateOf(sharedPreferences.getBoolean("show_mpaa_rating", true))
    }
    val trailerKey by movieViewModel.trailerKey.collectAsState()
    var swipeTrigger by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()
    val dragOffset = remember { Animatable(0f) }
    var isAnimatingSwipe by remember { mutableStateOf(false) }
    val autoTransitionOffset = remember { Animatable(0f) }

    val kodiPlayingMovie by movieViewModel.kodiPlayingMovie.collectAsState() // Observe Kodi playing movie

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val fullWidth = with(LocalDensity.current) { maxWidth.toPx() }

        LaunchedEffect(trailerKey, kodiPlayingMovie) {
            isPaused = trailerKey != null || kodiPlayingMovie != null
            if (kodiPlayingMovie != null) {
                Log.d("MainActivity", "Kodi movie playing: ${kodiPlayingMovie?.label}")
            }
        }

        // Moved LaunchedEffect inside BoxWithConstraints to access fullWidth
        LaunchedEffect(movies, transitionDelay, isPaused, swipeTrigger, selectedGenreIds.value, isAnimatingSwipe, fullWidth) {
            val currentSavedGenreIds = sharedPreferences.getStringSet("selected_genre_ids", emptySet())?.mapNotNull { it.toIntOrNull() }?.toSet() ?: emptySet()
            if (movies.isEmpty() || selectedGenreIds.value != currentSavedGenreIds) {
                selectedGenreIds.value = currentSavedGenreIds
                movieViewModel.getPopularMovies(selectedGenreIds.value)
            }
            if (movies.isNotEmpty() && fullWidth > 0f) { // Ensure fullWidth is available
                while (true) {
                    if (!isPaused && !isAnimatingSwipe) {
                        delay(transitionDelay) // Use delay directly
                        val initialOffset = autoTransitionOffset.value
                        val targetOffset = -fullWidth // Use fullWidth here
                        autoTransitionOffset.animateTo(targetOffset, animationSpec = tween(durationMillis = 2000)) // Animate over 2 seconds
                        currentMovieIndex = (currentMovieIndex + 1) % movies.size
                        movieViewModel.getMovieDetails(movies[currentMovieIndex].id) // Fetch details for the new movie
                        autoTransitionOffset.snapTo(0f) // Reset for new transition
                    } else {
                        delay(100)
                    }
                }
            }
        }

        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
            Box(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
                    .background(Color.Black) // Set background color to black
                    .clickable { isPaused = !isPaused } // Toggle pause state on click
                    .pointerInput(Unit) { // Add pointerInput for gesture detection
                        detectHorizontalDragGestures(
                            onHorizontalDrag = { change, dragAmountChange ->
                                scope.launch {
                                    // Pause auto-advance during drag
                                    isPaused = true
                                    dragOffset.snapTo(dragOffset.value + dragAmountChange)
                                }
                                change.consume()
                            },
                            onDragEnd = {
                                val swipeThreshold = size.width * 0.43f
                                scope.launch {
                                    isAnimatingSwipe = true // Indicate that a swipe animation is starting
                                    val targetOffset = when {
                                        dragOffset.value < -swipeThreshold -> -size.width.toFloat()
                                        dragOffset.value > swipeThreshold -> size.width.toFloat()
                                        else -> 0f
                                    }

                                    dragOffset.animateTo(targetOffset, animationSpec = tween(durationMillis = 300))

                                    if (targetOffset != 0f) {
                                        currentMovieIndex = if (targetOffset < 0) { // Swiped left
                                            (currentMovieIndex + 1) % movies.size
                                        } else { // Swiped right
                                            (currentMovieIndex - 1 + movies.size) % movies.size
                                        }
                                        movieViewModel.getMovieDetails(movies[currentMovieIndex].id) // Fetch details for the new movie
                                    }
                                    dragOffset.snapTo(0f) // Reset dragOffset to 0 for the new current movie
                                    isPaused = false // Unpause after swipe animation
                                    swipeTrigger = (swipeTrigger + 1) % 2 // Trigger LaunchedEffect
                                    isAnimatingSwipe = false // Indicate that swipe animation has ended
                                }
                            },
                            onDragCancel = {
                                scope.launch {
                                    dragOffset.animateTo(0f, animationSpec = tween(durationMillis = 300))
                                    isPaused = false // Unpause on cancel
                                    isAnimatingSwipe = false // Reset animation state
                                }
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                if (movies.isEmpty()) {
                    CircularProgressIndicator()
                    IconButton(
                        onClick = {
                            val settingsIntent = Intent(context, SettingsActivity::class.java)
                            context.startActivity(settingsIntent)
                        },
                        modifier = Modifier
                            .alpha(0.25f)
                            .align(Alignment.BottomEnd)
                            .padding(bottom = 20.dp, end = 20.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Open Settings",
                            tint = Color.White
                        )
                    }
                } else {
                    val currentMovie = movies[currentMovieIndex]
                    val nextMovie = movies[(currentMovieIndex + 1) % movies.size]
                    val previousMovie = movies[(currentMovieIndex - 1 + movies.size) % movies.size]

                    Log.d("MainActivity", "Kodi poster: ${kodiPlayingMovie?.art?.poster}")
                    // Display Kodi movie poster if a movie is playing on Kodi
                    kodiPlayingMovie?.let { kodiMovie ->
                        val kodiIpAddress = sharedPreferences.getString("kodi_ip_address", "") ?: ""
                        val kodiPort = sharedPreferences.getInt("kodi_port", 8080)
                        val kodiBaseUrl = if (kodiIpAddress.isNotEmpty()) "http://$kodiIpAddress:$kodiPort/image/" else ""

                        val imageUrl = kodiMovie.art?.poster ?: kodiMovie.thumbnail
                        val decodedAndReplacedUrl = imageUrl?.let { url ->
                            try {
                                val tempUrl = url
                                if (tempUrl.startsWith("image://")) {
                                    tempUrl.replace("image://", "")
                                }
                                val decodedUrl = kodiBaseUrl + java.net.URLEncoder.encode(tempUrl, "UTF-8")
                                decodedUrl
                            } catch (e: Exception) {
                                Log.e("MainActivity", "Error decoding or replacing Kodi image URL: $url", e)
                                url // Return original URL on error
                            }
                        }
                        // 2. Encode the credentials for Basic Auth
                        val username = sharedPreferences.getString("kodi_username", "") ?: ""
                        val password = sharedPreferences.getString("kodi_password", "") ?: ""
                        var authorizationHeader = ""
                        if (username != "" && password != "") {
                            val credentials = "$username:$password"
                            val encodedCredentials = android.util.Base64.encodeToString(credentials.toByteArray(), android.util.Base64.NO_WRAP)
                            authorizationHeader = "Basic $encodedCredentials"
                        }
                        Log.d("MainActivity", "decodedAndReplacedUrl: ${decodedAndReplacedUrl}")
                        Box(modifier = Modifier.fillMaxSize()) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(decodedAndReplacedUrl)
                                    .addHeader("Authorization", authorizationHeader) // Add the Authorization header
                                    .crossfade(1000)
                                    .build(),
                                contentDescription = kodiMovie.title,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit
                            )

                            // "Now Playing" banner at the top
                            Column(
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .fillMaxWidth()
                                    .background(Color.Black.copy(alpha = 0.7f)) // Semi-transparent black background
                                    .padding(vertical = 20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "Now Playing: " + kodiMovie.title ?: kodiMovie.label,
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = Color.Gray
                                )
                            }
                        }
                    } ?: run {
                        // Original slideshow logic

                    // Render previous movie (appears from left when dragging right)
                    if (dragOffset.value > 0f) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data("https://image.tmdb.org/t/p/w500${previousMovie.posterPath}")
                                .crossfade(1000) // Slow down fade effect to 1 second
                                .build(),
                            contentDescription = previousMovie.title,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    translationX = dragOffset.value - fullWidth
                                },
                            contentScale = ContentScale.Fit
                        )
                    }

                    // Render current movie
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data("https://image.tmdb.org/t/p/w500${currentMovie.posterPath}")
                            .crossfade(1000) // Slow down fade effect to 1 second
                            .build(),
                        contentDescription = currentMovie.title,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer { translationX = dragOffset.value + autoTransitionOffset.value },
                        contentScale = ContentScale.Fit
                    )

                    // Render next movie (appears from right when dragging left or delay is over)
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data("https://image.tmdb.org/t/p/w500${nextMovie.posterPath}")
                            .crossfade(1000) // Slow down fade effect to 1 second
                            .build(),
                        contentDescription = nextMovie.title,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                translationX = fullWidth + dragOffset.value + autoTransitionOffset.value
                            },
                        contentScale = ContentScale.Fit
                    )

                    movieViewModel.getMovieDetails(movies[currentMovieIndex].id) // Fetch details for the new movie

                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 40.dp), // Adjust padding for the whole column
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Original movie details display
                        // Movie MPAA rating
                        if (showMpaaRating.value) {
                            //Log.d("MainActivity", "Showing MPAA" + currentMovieWithDetails?.mpaaRating)
                            currentMovieWithDetails?.mpaaRating?.let { mpaaRating ->
                                Text(
                                    text = "MPAA Rating: $mpaaRating",
                                    color = Grey,
                                    modifier = Modifier.padding(vertical = 2.dp)
                                )
                            }
                        }

                        // Movie release date
                        if (showReleaseDate.value) {
                            currentMovieWithDetails?.releaseDate?.let { releaseDate ->
                                Text(
                                    text = "Release Date: $releaseDate",
                                    color = Grey,
                                    modifier = Modifier.padding(vertical = 2.dp)
                                )
                            }
                        }

                        // Movie runtime
                        if (showRuntime.value) {
                            currentMovieWithDetails?.runtime?.let { runtime ->
                                val hours = runtime / 60
                                val minutes = runtime % 60
                                val runtimeText = when {
                                    hours > 0 && minutes > 0 -> "${hours}h ${minutes}m"
                                    hours > 0 -> "${hours}h"
                                    minutes > 0 -> "${minutes}m"
                                    else -> ""
                                }
                                if (runtimeText.isNotEmpty()) {
                                    Text(
                                        text = runtimeText,
                                        color = Grey,
                                        modifier = Modifier.padding(vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                    } // End of run block for original slideshow logic
                } // End of kodiPlayingMovie?.let block

                // Button to show trailer
                if (kodiPlayingMovie == null) { // Only show trailer button if Kodi is not playing a movie
                    IconButton(
                        onClick = {
                            if (movies.isNotEmpty()) {
                                movieViewModel.getMovieTrailer(movies[currentMovieIndex].id)
                            }
                        },
                        modifier = Modifier
                            .alpha(0.25f)
                            .align(Alignment.BottomStart)
                            .padding(bottom = 20.dp, start = 20.dp) // Add some padding from the bottom and right
                    ) {
                        Icon(
                            imageVector = Icons.Default.Movie,
                            contentDescription = "Play Trailer",
                            tint = Color.White // Set icon color to white for visibility
                        )
                    }
                }


                // Button to open settings
                IconButton(
                    onClick = {
                        val settingsIntent = Intent(context, SettingsActivity::class.java)
                        context.startActivity(settingsIntent)
                    },
                    modifier = Modifier
                        .alpha(0.25f)
                        .align(Alignment.BottomEnd) // Align to bottom left
                        .padding(bottom = 20.dp, end = 20.dp) // Add some padding from the bottom and left
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Open Settings",
                        tint = Color.White // Set icon color to white for visibility
                    )
                }

                // Paused indicator
                if (isPaused && kodiPlayingMovie == null) {
                    Icon(
                        imageVector = Icons.Default.Pause,
                        contentDescription = "Paused",
                        tint = Color.White,
                        modifier = Modifier
                            .alpha(0.25f) // Make it 25% translucent
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 20.dp)
                    )
                }

                // Display trailer dialog if trailerKey is not null
                if (trailerKey != null) {
                    Dialog(
                        onDismissRequest = { movieViewModel.clearTrailerKey() },
                        properties = DialogProperties(usePlatformDefaultWidth = false)
                    ) {
                        Box(modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.8f)) // Semi-transparent black background
                            .clickable { movieViewModel.clearTrailerKey() } // Dismiss on click outside player
                            , contentAlignment = when (trailerPlacement.value) {
                                "Top" -> Alignment.TopCenter
                                "Middle" -> Alignment.Center
                                "Bottom" -> Alignment.BottomCenter
                                else -> Alignment.BottomCenter
                            }
                        ) {
                            val lifecycleOwner = LocalLifecycleOwner.current
                            AndroidView(
                                factory = { context ->
                                    YouTubePlayerView(context).apply {
                                        enableAutomaticInitialization = false
                                        lifecycleOwner.lifecycle.addObserver(this)
                                        val options = IFramePlayerOptions.Builder().controls(0).rel(0).ivLoadPolicy(0).build()
                                        initialize(object : AbstractYouTubePlayerListener() {
                                            override fun onReady(youTubePlayer: YouTubePlayer) {
                                                trailerKey?.let { key ->
                                                    youTubePlayer.loadVideo(key, 0f)
                                                }
                                            }

                                            override fun onStateChange(
                                                youTubePlayer: YouTubePlayer,
                                                state: com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants.PlayerState
                                            ) {
                                                if (state == com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants.PlayerState.ENDED) {
                                                    movieViewModel.clearTrailerKey()
                                                }
                                            }
                                        }, options)
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .wrapContentHeight()
                                    .padding(bottom = 100.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
