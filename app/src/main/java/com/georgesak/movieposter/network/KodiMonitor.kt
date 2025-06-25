package com.georgesak.movieposter.network

import android.app.Application
import android.content.Context
import android.util.Log
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import com.georgesak.movieposter.data.KodiItem
import com.georgesak.movieposter.data.KodiRpcRequest
import kotlinx.coroutines.CancellationException

class KodiMonitor(private val application: Application) {

    private val _kodiPlayingMovie = MutableStateFlow<KodiItem?>(null)
    val kodiPlayingMovie: StateFlow<KodiItem?> = _kodiPlayingMovie.asStateFlow()

    private var monitoringJob: Job? = null
    private val monitorScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private fun getKodiApiService(): KodiApiService? {
        val sharedPreferences = application.getSharedPreferences("AppSettings", Context.MODE_PRIVATE)
        val ipAddress = sharedPreferences.getString("kodi_ip_address", "")
        val port = sharedPreferences.getInt("kodi_port", 8080)
        val username = sharedPreferences.getString("kodi_username", "") ?: ""
        val password = sharedPreferences.getString("kodi_password", "") ?: ""
        return if (!ipAddress.isNullOrEmpty()) {
            KodiApiClient.createKodiApiService(ipAddress, port, username, password)
        } else {
            null
        }
    }

    fun startMonitoringKodi() {
        Log.d(TAG, "Starting Kodi monitoring...")
        monitoringJob?.cancel() // Cancel any existing job
        monitoringJob = monitorScope.launch {
            val pollingInterval = application.getSharedPreferences("AppSettings", Context.MODE_PRIVATE).getLong("kodi_polling_interval", 5000L)
            while (true) {
                try {
                    val kodiApiService = getKodiApiService()
                    if (kodiApiService == null) {
                        Log.d(TAG, "Kodi API service is null. Check IP address and port settings.")
                        _kodiPlayingMovie.value = null
                        delay(pollingInterval)
                        continue
                    }
                    Log.d(TAG, "Kodi API service created. Polling interval: $pollingInterval ms")

                    // 1. Get active players
                    val activePlayersRequest = KodiRpcRequest(
                        id = 1,
                        method = "Player.GetActivePlayers"
                    )
                    val activePlayersResponse = kodiApiService.getActivePlayers(activePlayersRequest)

                    if (activePlayersResponse.isSuccessful) {
                        val activePlayers = activePlayersResponse.body()?.result
                        Log.d(TAG, "Active players: $activePlayers")

                        val videoPlayer = activePlayers?.find { it.type == "video" }

                        if (videoPlayer != null) {
                            Log.d(TAG, "Video player found: ${videoPlayer.playerid}")
                            // 2. Get player properties for the active video player
                            val playerPropertiesRequest = KodiRpcRequest(
                                id = 2,
                                method = "Player.GetProperties",
                                params = mapOf(
                                    "playerid" to videoPlayer.playerid,
                                    "properties" to listOf("speed", "time", "totaltime", "percentage")
                                )
                            )
                            val playerPropertiesResponse = kodiApiService.getPlayerProperties(playerPropertiesRequest)

                            if (playerPropertiesResponse.isSuccessful) {
                                val playerProperties = playerPropertiesResponse.body()?.result
                                Log.d(TAG, "Player properties: $playerProperties")
                                // 3. Get item details for the active player
                                val playerItemRequest = KodiRpcRequest(
                                    id = 3,
                                    method = "Player.GetItem",
                                    params = mapOf(
                                        "playerid" to videoPlayer.playerid,
                                        "properties" to listOf("title", "file", "thumbnail", "fanart", "art", "year", "plot", "genre", "director", "cast", "rating", "tagline", "runtime", "streamdetails")
                                    )
                                )
                                val playerItemResponse = kodiApiService.getPlayerItem(playerItemRequest)

                                if (playerItemResponse.isSuccessful) {
                                    val kodiItem = playerItemResponse.body()?.result?.item
                                    if (kodiItem != null) {
                                        Log.d(TAG, "Kodi item playing: ${kodiItem.label}")
                                        _kodiPlayingMovie.value = kodiItem
                                    } else {
                                        Log.d(TAG, "No item found for active player. Setting kodiPlayingMovie to null.")
                                        _kodiPlayingMovie.value = null
                                    }
                                } else {
                                    _kodiPlayingMovie.value = null
                                    Log.e(TAG, "Error getting player item: ${playerItemResponse.code()} - ${playerItemResponse.errorBody()?.string()}")
                                }
                            } else {
                                _kodiPlayingMovie.value = null
                                Log.e(TAG, "Error getting player properties: ${playerPropertiesResponse.code()} - ${playerPropertiesResponse.errorBody()?.string()}")
                            }
                        } else {
                            Log.d(TAG, "No video player active. Setting kodiPlayingMovie to null.")
                            _kodiPlayingMovie.value = null // No video player active
                        }
                    } else {
                        _kodiPlayingMovie.value = null
                        Log.e(TAG, "Error getting active players: ${activePlayersResponse.code()} - ${activePlayersResponse.errorBody()?.string()}")
                    }
                } catch (e: Exception) {
                    if (e is CancellationException) {
                        // Re-throw CancellationException to propagate cancellation
                        throw e
                    }
                    _kodiPlayingMovie.value = null
                    Log.e(TAG, "Error monitoring Kodi: ${e.message}", e)
                }
                delay(pollingInterval)
            }
        }
    }

    fun stopMonitoringKodi() {
        Log.d(TAG, "Stopping Kodi monitoring.")
        monitoringJob?.cancel()
        monitoringJob = null
        _kodiPlayingMovie.value = null // Clear playing movie when monitoring stops
    }

    companion object {
        private const val TAG = "KodiMonitor"
    }
}