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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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
fun MoviePosterScreen(movieViewModel: MovieViewModel = viewModel(factory = MovieViewModel.Factory)) { // Use the factory
    val movies = movieViewModel.movies.value
    var currentMovieIndex by remember { mutableStateOf(0) }
    var isPaused by remember { mutableStateOf(false) } // State to track if slideshow is paused
    val context = LocalContext.current // Get the current context
    val sharedPreferences = context.getSharedPreferences("AppSettings", Context.MODE_PRIVATE) // Get SharedPreferences
    val transitionDelay = sharedPreferences.getLong("transition_delay", 10000L) // Read transition delay (default 10 seconds)
    val selectedGenreIds = remember {
        mutableStateOf(sharedPreferences.getStringSet("selected_genre_ids", emptySet())?.mapNotNull { it.toIntOrNull() }?.toSet() ?: emptySet())
    }
    var progress by remember { mutableStateOf(0f) }
    val trailerKey by movieViewModel.trailerKey.collectAsState()
    var swipeTrigger by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()
    val dragOffset = remember { Animatable(0f) }

    LaunchedEffect(trailerKey) {
        isPaused = trailerKey != null
    }

    LaunchedEffect(movies, transitionDelay, isPaused, swipeTrigger, selectedGenreIds.value) {
        val currentSavedGenreIds = sharedPreferences.getStringSet("selected_genre_ids", emptySet())?.mapNotNull { it.toIntOrNull() }?.toSet() ?: emptySet()
        if (movies.isEmpty() || selectedGenreIds.value != currentSavedGenreIds) {
            selectedGenreIds.value = currentSavedGenreIds
            movieViewModel.getPopularMovies(selectedGenreIds.value)
        }
        if (movies.isNotEmpty()) {
            while (true) {
                if (!isPaused) {
                    val totalSteps = transitionDelay / 100L
                    for (i in 0..totalSteps) {
                        progress = i.toFloat() / totalSteps.toFloat()
                        delay(100)
                    }
                    currentMovieIndex = (currentMovieIndex + 1) % movies.size
                    progress = 0f
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
                                dragOffset.snapTo(dragOffset.value + dragAmountChange)
                            }
                            change.consume()
                        },
                        onDragEnd = {
                            val swipeThreshold = size.width * 0.43
                            scope.launch {
                                if (dragOffset.value < -swipeThreshold) { // Check for a left swipe
                                    dragOffset.animateTo(-size.width.toFloat(), animationSpec = tween(durationMillis = 300))
                                    currentMovieIndex = (currentMovieIndex + 1) % movies.size
                                    progress = 0f // Reset progress on swipe
                                    isPaused = false // Unpause on swipe
                                    swipeTrigger = (swipeTrigger + 1) % 2 // Trigger LaunchedEffect
                                    dragOffset.snapTo(0f) // Snap immediately to 0 after index change
                                } else if (dragOffset.value > swipeThreshold) { // Check for a right swipe
                                    dragOffset.animateTo(size.width.toFloat(), animationSpec = tween(durationMillis = 300))
                                    currentMovieIndex = (currentMovieIndex - 1 + movies.size) % movies.size
                                    progress = 0f // Reset progress on swipe
                                    isPaused = false // Unpause on swipe
                                    swipeTrigger = (swipeTrigger + 1) % 2 // Trigger LaunchedEffect
                                    dragOffset.snapTo(0f) // Snap immediately to 0 after index change
                                } else {
                                    // Snap back if threshold not met
                                    dragOffset.animateTo(0f, animationSpec = tween(durationMillis = 300))
                                }
                            }
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            if (movies.isEmpty()) {
                CircularProgressIndicator()
            } else {
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val fullWidth = constraints.maxWidth.toFloat()

                    val currentMovie = movies[currentMovieIndex]
                    val nextMovie = movies[(currentMovieIndex + 1) % movies.size]
                    val previousMovie = movies[(currentMovieIndex - 1 + movies.size) % movies.size]

                    // Render previous movie (appears from left when dragging right)
                    if (dragOffset.value > 0f) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data("https://image.tmdb.org/t/p/w500${previousMovie.posterPath}")
                                .crossfade(true)
                                .build(),
                            contentDescription = previousMovie.title,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    translationX = dragOffset.value - fullWidth
                                    alpha = (dragOffset.value / fullWidth).coerceIn(0f, 1f)
                                },
                            contentScale = ContentScale.Fit
                        )
                    }

                    // Render current movie
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data("https://image.tmdb.org/t/p/w500${currentMovie.posterPath}")
                            .crossfade(true)
                            .build(),
                        contentDescription = currentMovie.title,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer { translationX = dragOffset.value },
                        contentScale = ContentScale.Fit
                    )

                    // Render next movie (appears from right when dragging left)
                    if (dragOffset.value < 0f) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data("https://image.tmdb.org/t/p/w500${nextMovie.posterPath}")
                                .crossfade(true)
                                .build(),
                            contentDescription = nextMovie.title,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    translationX = fullWidth + dragOffset.value
                                    alpha = (kotlin.math.abs(dragOffset.value) / fullWidth).coerceIn(0f, 1f)
                                },
                            contentScale = ContentScale.Fit
                        )
                    }
                }

                // Progress bar at the bottom
                LinearProgressIndicator(
                    progress = progress,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(2.dp), // Adjust height as needed
                    color = Color(0xFF202020), // Dark blue color
                    trackColor = Color.Gray // Background color for the track
                )

                // Button to show trailer
                IconButton(
                    onClick = {
                        if (movies.isNotEmpty()) {
                            movieViewModel.getMovieTrailer(movies[currentMovieIndex].id)
                        }
                    },
                    modifier = Modifier
                        .alpha(0.25f)
                        .align(Alignment.BottomEnd)
                        .padding(bottom = 20.dp, end = 20.dp) // Add some padding from the bottom and right
                ) {
                    Icon(
                        imageVector = Icons.Default.Movie,
                        contentDescription = "Play Trailer",
                        tint = Color.White // Set icon color to white for visibility
                    )
                }

                // Button to open settings
                IconButton(
                    onClick = {
                        val settingsIntent = Intent(context, SettingsActivity::class.java)
                        context.startActivity(settingsIntent)
                    },
                    modifier = Modifier
                        .alpha(0.25f)
                        .align(Alignment.BottomStart) // Align to bottom left
                        .padding(bottom = 20.dp, start = 20.dp) // Add some padding from the bottom and left
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Open Settings",
                        tint = Color.White // Set icon color to white for visibility
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
                            , contentAlignment = Alignment.BottomCenter // Center the video player
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
