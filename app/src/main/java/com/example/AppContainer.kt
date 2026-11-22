package com.example

import android.content.Context
import com.example.ai.GeminiApiClient
import com.example.data.CameraConfigRepository
import com.example.data.FilterRepository
import com.example.data.MediaRepository
import com.example.data.SettingsRepository
import com.example.security.SecureKeyStoreManager

class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val keyStoreManager: SecureKeyStoreManager by lazy { SecureKeyStoreManager(appContext) }
    val settingsRepository: SettingsRepository by lazy { SettingsRepository(appContext) }
    val filterRepository: FilterRepository by lazy { FilterRepository(appContext, settingsRepository) }
    val cameraConfigRepository: CameraConfigRepository by lazy { CameraConfigRepository(appContext, settingsRepository) }
    val mediaRepository: MediaRepository by lazy { MediaRepository(appContext) }
    val geminiApiClient: GeminiApiClient by lazy { GeminiApiClient(keyStoreManager) }
}
