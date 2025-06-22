package com.georgesak.movieposter

import android.app.Application
import coil.Coil
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy

class MoviePosterApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        Coil.setImageLoader(
            ImageLoader.Builder(this)
                .memoryCache {
                    MemoryCache.Builder(this)
                        .maxSizePercent(0.25) // Use 25% of the application's available memory for the memory cache.
                        .build()
                }
                .diskCache {
                    DiskCache.Builder()
                        .directory(cacheDir.resolve("image_cache")) // Store images in a custom directory.
                        .maxSizePercent(0.02) // Use 2% of the device's free space for the disk cache.
                        .build()
                }
                .respectCacheHeaders(false) // Ignore cache headers for simplicity, or set to true if needed
                .networkCachePolicy(CachePolicy.ENABLED) // Enable network caching
                .diskCachePolicy(CachePolicy.ENABLED) // Enable disk caching
                .build()
        )
    }
}