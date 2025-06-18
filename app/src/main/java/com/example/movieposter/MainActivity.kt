package com.example.movieposter

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
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.movieposter.ui.MovieViewModel
import com.example.movieposter.ui.theme.MoviePosterTheme
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.options.IFramePlayerOptions
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.YouTubePlayerView
import kotlinx.coroutines.delay

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

            // Start the foreground service
            val serviceIntent = Intent(this, MoviePosterService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
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
    var progress by remember { mutableStateOf(0f) } // State to track progress (0f to 1f)
    val trailerKey by movieViewModel.trailerKey.collectAsState() // Observe the trailer key
    var swipeTrigger by remember { mutableStateOf(0) } // State to trigger LaunchedEffect on swipe

    // Pause slideshow when trailer is shown
    LaunchedEffect(trailerKey) {
        isPaused = trailerKey != null
    }

    // Auto-cycle the posters and update progress
    LaunchedEffect(movies, transitionDelay, isPaused, swipeTrigger) { // Add swipeTrigger as a key
        if (movies.isNotEmpty()) {
            while (true) {
                if (!isPaused) {
                    // Update progress
                    val totalSteps = transitionDelay / 100L // Update progress every 100ms
                    for (i in 0..totalSteps) {
                        progress = i.toFloat() / totalSteps.toFloat()
                        delay(100)
                    }
                    currentMovieIndex = (currentMovieIndex + 1) % movies.size
                    progress = 0f // Reset progress for the next image
                } else {
                    // If paused, just delay to prevent the coroutine from finishing
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
                    var horizontalDragAmount = 0f // Use separate drag amounts
                    detectHorizontalDragGestures(
                        onHorizontalDrag = { change, dragAmountChange ->
                            horizontalDragAmount += dragAmountChange
                            change.consume()
                        },
                        onDragEnd = {
                            val swipeThreshold = 100 // Define a swipe threshold in pixels
                            if (horizontalDragAmount < -swipeThreshold) { // Check for a left swipe
                                currentMovieIndex = (currentMovieIndex + 1) % movies.size
                                progress = 0f // Reset progress on swipe
                                isPaused = false // Unpause on swipe
                                swipeTrigger = (swipeTrigger + 1) % 2 // Trigger LaunchedEffect
                            } else if (horizontalDragAmount > swipeThreshold) { // Check for a right swipe
                                currentMovieIndex = (currentMovieIndex - 1 + movies.size) % movies.size
                                progress = 0f // Reset progress on swipe
                                isPaused = false // Unpause on swipe
                                swipeTrigger = (swipeTrigger + 1) % 2 // Trigger LaunchedEffect
                            }
                            horizontalDragAmount = 0f // Reset drag amount
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            if (movies.isEmpty()) {
                CircularProgressIndicator()
            } else {
                Crossfade(
                    targetState = movies[currentMovieIndex],
                    animationSpec = tween(durationMillis = 1000) // Fade duration
                ) { movie ->
                    // Assuming a base URL for the poster images
                    val imageUrl = "https://image.tmdb.org/t/p/w500${movie.posterPath}"
                    AsyncImage(
                        model = imageUrl,
                        contentDescription = movie.title,
                        modifier = Modifier.fillMaxSize(), // Make image fill the screen
                        contentScale = ContentScale.Fit // Scale the image to fit the bounds while maintaining aspect ratio
                    )
                }

                // Progress bar at the bottom
                LinearProgressIndicator(
                    progress = progress,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(4.dp), // Adjust height as needed
                    color = Color(0xFF000033), // Dark blue color
                    trackColor = Color.Gray // Background color for the track
                )

                // Button to show trailer
                Button(
                    onClick = {
                        if (movies.isNotEmpty()) {
                            movieViewModel.getMovieTrailer(movies[currentMovieIndex].id)
                        }
                    },
                    modifier = Modifier
                        .alpha(0.25f) // Make button 25% translucent
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 20.dp) // Add some padding from the bottom
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Play Trailer"
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
