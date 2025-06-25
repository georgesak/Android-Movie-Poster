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
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import com.georgesak.movieposter.data.Movie
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
import com.georgesak.movieposter.Constants

@OptIn(ExperimentalMaterial3Api::class) // Add opt-in for ExperimentalMaterial3Api
class MoviePosterActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Check if API key is set, if not, navigate to SettingsActivity
        val sharedPreferences = getSharedPreferences(Constants.APP_SETTINGS_PREFS, Context.MODE_PRIVATE)
        val apiKey = sharedPreferences.getString(Constants.API_KEY, "")

        if (apiKey.isNullOrEmpty()) {
            val settingsIntent = Intent(this, SettingsActivity::class.java)
            startActivity(settingsIntent)
            finish() // Finish MoviePosterActivity so the user can't go back to it without setting the key
        } else {
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
    val sharedPreferences = context.getSharedPreferences(Constants.APP_SETTINGS_PREFS, Context.MODE_PRIVATE) // Get SharedPreferences
    val transitionDelay = sharedPreferences.getLong(Constants.TRANSITION_DELAY, Constants.DEFAULT_TRANSITION_DELAY) // Read transition delay (default 10 seconds)
    val selectedGenreIds = remember {
        mutableStateOf(sharedPreferences.getStringSet(Constants.SELECTED_GENRE_IDS, emptySet())?.mapNotNull { it.toIntOrNull() }?.toSet() ?: emptySet())
    }
    val trailerPlacement = remember {
        mutableStateOf(sharedPreferences.getString(Constants.TRAILER_PLACEMENT, Constants.DEFAULT_TRAILER_PLACEMENT) ?: Constants.DEFAULT_TRAILER_PLACEMENT)
    }
    val showRuntime = remember {
        mutableStateOf(sharedPreferences.getBoolean(Constants.SHOW_RUNTIME, true))
    }
    val showReleaseDate = remember {
        mutableStateOf(sharedPreferences.getBoolean(Constants.SHOW_RELEASE_DATE, true))
    }
    val showMpaaRating = remember {
        mutableStateOf(sharedPreferences.getBoolean(Constants.SHOW_MPAA_RATING, true))
    }
    val trailerKey by movieViewModel.trailerKey.collectAsState()
    var swipeTrigger by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()
    val dragOffset = remember { Animatable(0f) }
    var isAnimatingSwipe by remember { mutableStateOf(false) }
    val autoTransitionOffset = remember { Animatable(0f) }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val fullWidth = with(LocalDensity.current) { maxWidth.toPx() }

        LaunchedEffect(movies, transitionDelay, isPaused, swipeTrigger, selectedGenreIds.value, isAnimatingSwipe, fullWidth) {
            val currentSavedGenreIds = sharedPreferences.getStringSet(Constants.SELECTED_GENRE_IDS, emptySet())?.mapNotNull { it.toIntOrNull() }?.toSet() ?: emptySet()
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
                        autoTransitionOffset.animateTo(targetOffset, animationSpec = tween(durationMillis = Constants.AUTO_TRANSITION_ANIMATION_DURATION_MILLIS)) // Animate over 2 seconds
                        currentMovieIndex = (currentMovieIndex + 1) % movies.size
                        movieViewModel.getMovieDetails(movies[currentMovieIndex].id) // Fetch details for the new movie
                        autoTransitionOffset.snapTo(0f) // Reset for new transition
                    } else {
                        delay(100)
                    }
                }
            }
        }

        // Observe Kodi playing movie and launch KodiActivity if a movie is playing
        val kodiPlayingMovie by movieViewModel.kodiPlayingMovie.collectAsState()
        LaunchedEffect(kodiPlayingMovie) {
            if (kodiPlayingMovie != null) {
                Log.d("MoviePosterActivity", "Kodi is active. Launching KodiActivity. Movie: ${kodiPlayingMovie?.title ?: kodiPlayingMovie?.label}")
                val kodiIntent = Intent(context, KodiActivity::class.java)/*.apply {
                    addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                }*/
                context.startActivity(kodiIntent)
            } else {
                Log.d("MoviePosterActivity", "Kodi is not active (kodiPlayingMovie is null).")
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
                                val swipeThreshold = size.width * Constants.SWIPE_THRESHOLD_PERCENTAGE
                                scope.launch {
                                    isAnimatingSwipe = true // Indicate that a swipe animation is starting
                                    val targetOffset = when {
                                        dragOffset.value < -swipeThreshold -> -size.width.toFloat()
                                        dragOffset.value > swipeThreshold -> size.width.toFloat()
                                        else -> 0f
                                    }

                                    dragOffset.animateTo(targetOffset, animationSpec = tween(durationMillis = Constants.SWIPE_ANIMATION_DURATION_MILLIS))

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
                                    dragOffset.animateTo(0f, animationSpec = tween(durationMillis = Constants.SWIPE_ANIMATION_DURATION_MILLIS))
                                    isPaused = false // Unpause on cancel
                                    isAnimatingSwipe = false // Reset animation state
                                }
                            }
                        )
                    },
                contentAlignment = Alignment.TopCenter
            ) {
                if (movies.isEmpty()) {
                    CircularProgressIndicator()
                    IconButton(
                        onClick = {
                            val settingsIntent = Intent(context, SettingsActivity::class.java)
                            context.startActivity(settingsIntent)
                        },
                        modifier = Modifier
                            .alpha(Constants.ALPHA_TRANSLUCENT)
                            .align(Alignment.BottomEnd)
                            .padding(bottom = Constants.PADDING_MEDIUM, end = Constants.PADDING_MEDIUM)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Open Settings",
                            tint = Color.White
                        )
                    }
                } else {
                    MoviePosterSlideshow(movies, currentMovieIndex, dragOffset, autoTransitionOffset, fullWidth)

                    MovieDetailsOverlay(
                        currentMovieWithDetails,
                        showMpaaRating.value,
                        showReleaseDate.value,
                        showRuntime.value,
                        modifier = Modifier.align(Alignment.BottomCenter)
                    )

                    ControlButtons(
                        isPaused = isPaused,
                        onPlayTrailerClick = {
                            if (movies.isNotEmpty()) {
                                movieViewModel.getMovieTrailer(movies[currentMovieIndex].id)
                            }
                        },
                        onSettingsClick = {
                            val settingsIntent = Intent(context, SettingsActivity::class.java)
                            context.startActivity(settingsIntent)
                        }
                    )
                }

                if (trailerKey != null) {
                    TrailerDialog(trailerKey, trailerPlacement.value) { movieViewModel.clearTrailerKey() }
                }
            }
        }
    }
}

@Composable
fun MoviePosterSlideshow(
    movies: List<Movie>,
    currentMovieIndex: Int,
    dragOffset: Animatable<Float, androidx.compose.animation.core.AnimationVector1D>,
    autoTransitionOffset: Animatable<Float, androidx.compose.animation.core.AnimationVector1D>,
    fullWidth: Float
) {
    val currentMovie = movies[currentMovieIndex]
    val nextMovie = movies[(currentMovieIndex + 1) % movies.size]
    val previousMovie = movies[(currentMovieIndex - 1 + movies.size) % movies.size]

    // Render previous movie (appears from left when dragging right)
    if (dragOffset.value > 0f) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data("${Constants.TMDB_IMAGE_BASE_URL}${previousMovie.posterPath}")
                .crossfade(Constants.CROSSFADE_DURATION_MILLIS)
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
            .data("${Constants.TMDB_IMAGE_BASE_URL}${currentMovie.posterPath}")
            .crossfade(Constants.CROSSFADE_DURATION_MILLIS)
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
            .data("${Constants.TMDB_IMAGE_BASE_URL}${nextMovie.posterPath}")
            .crossfade(Constants.CROSSFADE_DURATION_MILLIS)
            .build(),
        contentDescription = nextMovie.title,
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                translationX = fullWidth + dragOffset.value + autoTransitionOffset.value
            },
        contentScale = ContentScale.Fit
    )
}

@Composable
fun MovieDetailsOverlay(
    currentMovieWithDetails: MovieDetail?,
    showMpaaRating: Boolean,
    showReleaseDate: Boolean,
    showRuntime: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .padding(bottom = Constants.PADDING_LARGE),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (showMpaaRating) {
            currentMovieWithDetails?.tagline?.let { tagline ->
                Text(
                    text = tagline,
                    color = Grey,
                    modifier = Modifier.padding(vertical = Constants.PADDING_SMALL)
                )
            }
        }

        if (showMpaaRating) {
            currentMovieWithDetails?.mpaaRating?.let { mpaaRating ->
                Text(
                    text = "MPAA Rating: $mpaaRating",
                    color = Grey,
                    modifier = Modifier.padding(vertical = Constants.PADDING_SMALL)
                )
            }
        }

        if (showReleaseDate) {
            Log.d("MoviePosterActivity", "Showing Date" + currentMovieWithDetails?.release_date)
            currentMovieWithDetails?.release_date?.let { release_date ->
                Text(
                    text = "Release Date: $release_date",
                    color = Grey,
                    modifier = Modifier.padding(vertical = Constants.PADDING_SMALL)
                )
            }
        }

        if (showRuntime) {
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
                        text = "Runtime: ${runtimeText}",
                        color = Grey,
                        modifier = Modifier.padding(vertical = Constants.PADDING_SMALL)
                    )
                }
            }
        }
    }
}

@Composable
fun ControlButtons(
    isPaused: Boolean,
    onPlayTrailerClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) { // Wrap content in a Box to provide BoxScope for align
        IconButton(
            onClick = onPlayTrailerClick,
            modifier = Modifier
                .alpha(Constants.ALPHA_TRANSLUCENT)
                .align(Alignment.BottomStart)
                .padding(bottom = Constants.PADDING_MEDIUM, start = Constants.PADDING_MEDIUM)
        ) {
            Icon(
                imageVector = Icons.Default.Movie,
                contentDescription = "Play Trailer",
                tint = Color.White
            )
        }

        IconButton(
            onClick = onSettingsClick,
            modifier = Modifier
                .alpha(Constants.ALPHA_TRANSLUCENT)
                .align(Alignment.BottomEnd)
                .padding(bottom = Constants.PADDING_MEDIUM, end = Constants.PADDING_MEDIUM)
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "Open Settings",
                tint = Color.White
            )
        }

        if (isPaused) {
            Icon(
                imageVector = Icons.Default.Pause,
                contentDescription = "Paused",
                tint = Color.White,
                modifier = Modifier
                    .alpha(Constants.ALPHA_TRANSLUCENT)
                    .align(Alignment.BottomCenter)
                    .padding(bottom = Constants.PADDING_MEDIUM)
            )
        }
    }
}

@Composable
fun TrailerDialog(
    trailerKey: String?,
    trailerPlacement: String,
    onDismiss: () -> Unit
) {
    if (trailerKey != null) {
        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = Constants.DIALOG_BACKGROUND_ALPHA))
                .clickable { onDismiss() }
                , contentAlignment = when (trailerPlacement) {
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
                                    trailerKey.let { key ->
                                        youTubePlayer.loadVideo(key, 0f)
                                    }
                                }

                                override fun onStateChange(
                                    youTubePlayer: YouTubePlayer,
                                    state: com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants.PlayerState
                                ) {
                                    if (state == com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants.PlayerState.ENDED) {
                                        onDismiss()
                                    }
                                }
                            }, options)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                        .padding(bottom = Constants.PADDING_TRAILER_BOTTOM)
                )
            }
        }
    }
}
