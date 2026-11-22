package com.example

import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.model.OrientationLockMode
import com.example.ui.components.AnamolAnimatedSplashOverlay
import com.example.ui.screens.CameraConfigScreen
import com.example.ui.screens.CameraScreen
import com.example.ui.screens.CollageScreen
import com.example.ui.screens.FilterManagerScreen
import com.example.ui.screens.GalleryScreen
import com.example.ui.screens.OnboardingScreen
import com.example.ui.screens.PhotoEditorScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.AnamolCameraTheme
import com.example.viewmodel.CameraViewModel
import com.example.viewmodel.GalleryViewModel
import com.example.viewmodel.SettingsViewModel

object Routes {
    const val ONBOARDING = "onboarding"
    const val CAMERA = "camera"
    const val GALLERY = "gallery"
    const val EDITOR = "editor/{mediaId}"
    const val COLLAGE = "collage"
    const val FILTERS = "filters"
    const val CONFIGS = "configs"
    const val SETTINGS = "settings"

    fun editor(mediaId: String) = "editor/$mediaId"
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = AppContainer(applicationContext)

        setContent {
            val settings by container.settingsRepository.settingsFlow
                .collectAsStateWithLifecycle(initialValue = com.example.model.AppSettings())

            LaunchedEffect(settings.orientationLockMode) {
                requestedOrientation = when (settings.orientationLockMode) {
                    OrientationLockMode.AUTO_ROTATE -> ActivityInfo.SCREEN_ORIENTATION_FULL_USER
                    OrientationLockMode.LOCK_PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                    OrientationLockMode.LOCK_LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                }
            }

            var showSplash by remember { mutableStateOf(true) }

            AnamolCameraTheme(themeMode = settings.themeMode) {
                Box(modifier = Modifier.fillMaxSize()) {
                    AnamolCameraNavHost(
                        container = container,
                        settings = settings
                    )
                    AnamolAnimatedSplashOverlay(
                        visible = showSplash,
                        onFinished = { showSplash = false }
                    )
                }
            }
        }
    }
}

@Composable
private fun AnamolCameraNavHost(
    container: AppContainer,
    settings: com.example.model.AppSettings
) {
    val navController = rememberNavController()
    val context = androidx.compose.ui.platform.LocalContext.current

    val cameraViewModel: CameraViewModel = viewModel(
        factory = remember { CameraViewModel.Factory(context, container) }
    )
    val galleryViewModel: GalleryViewModel = viewModel(
        factory = remember { GalleryViewModel.Factory(container) }
    )
    val settingsViewModel: SettingsViewModel = viewModel(
        factory = remember { SettingsViewModel.Factory(container) }
    )

    val startDest = if (settings.onboardingCompleted) Routes.CAMERA else Routes.ONBOARDING

    NavHost(navController = navController, startDestination = startDest) {
        composable(Routes.ONBOARDING) {
            OnboardingScreen(
                onSaveApiKey = { key -> settingsViewModel.saveAndVerifyApiKey(key) },
                onFinish = {
                    settingsViewModel.updateSettings { it.copy(onboardingCompleted = true) }
                    navController.navigate(Routes.CAMERA) {
                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.CAMERA) {
            CameraScreen(
                viewModel = cameraViewModel,
                mediaRepository = container.mediaRepository,
                onOpenGallery = { navController.navigate(Routes.GALLERY) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onOpenFilterManager = { navController.navigate(Routes.FILTERS) },
                onOpenConfigManager = { navController.navigate(Routes.CONFIGS) }
            )
        }

        composable(Routes.GALLERY) {
            GalleryScreen(
                viewModel = galleryViewModel,
                onBack = { navController.popBackStack() },
                onOpenEditor = { item -> navController.navigate(Routes.editor(item.id)) },
                onOpenCollage = { navController.navigate(Routes.COLLAGE) }
            )
        }

        composable(
            route = Routes.EDITOR,
            arguments = listOf(navArgument("mediaId") { type = NavType.StringType })
        ) { backStack ->
            val mediaId = backStack.arguments?.getString("mediaId").orEmpty()
            val items by galleryViewModel.items.collectAsStateWithLifecycle()
            val item = items.find { it.id == mediaId }
            if (item != null) {
                PhotoEditorScreen(
                    item = item,
                    galleryViewModel = galleryViewModel,
                    onBack = { navController.popBackStack() }
                )
            }
        }

        composable(Routes.COLLAGE) {
            CollageScreen(
                galleryViewModel = galleryViewModel,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.FILTERS) {
            FilterManagerScreen(
                filterRepository = container.filterRepository,
                mediaRepository = container.mediaRepository,
                onApplyFilterToCamera = { filter ->
                    cameraViewModel.selectSingleFilter(filter)
                    navController.popBackStack()
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.CONFIGS) {
            CameraConfigScreen(
                configRepository = container.cameraConfigRepository,
                mediaRepository = container.mediaRepository,
                activeConfigId = settings.activeCameraConfigId,
                themeMode = settings.themeMode,
                onToggleTheme = { cameraViewModel.cycleThemeMode() },
                onApplyConfigToCamera = { cfg ->
                    cameraViewModel.selectCameraConfig(cfg.id)
                    navController.popBackStack()
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                viewModel = settingsViewModel,
                onOpenConfigs = { navController.navigate(Routes.CONFIGS) },
                onReplayOnboarding = { navController.navigate(Routes.ONBOARDING) },
                onBack = { navController.popBackStack() }
            )
        }
    }
}
