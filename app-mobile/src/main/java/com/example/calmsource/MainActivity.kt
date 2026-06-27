package com.example.calmsource

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import dagger.hilt.android.AndroidEntryPoint
import com.example.calmsource.core.ui.theme.LumenTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

private fun saveMobileScreen(screen: MobileScreen): Bundle {
    return Bundle().apply {
        when (screen) {
            is MobileScreen.Home -> putString("type", "Home")
            is MobileScreen.LiveTv -> putString("type", "LiveTv")
            is MobileScreen.Library -> putString("type", "Library")
            is MobileScreen.Search -> putString("type", "Search")
            is MobileScreen.Settings -> putString("type", "Settings")
            is MobileScreen.Details -> {
                putString("type", "Details")
                putString("media_item_json", kotlinx.serialization.json.Json.encodeToString(com.example.calmsource.core.model.MediaItem.serializer(), screen.mediaItem))
                putLong("start_position_ms", screen.startPositionMs)
            }
            is MobileScreen.Player -> {
                putString("type", "Player")
                if (screen.parentScreen != null) {
                    putBundle("parent_screen", saveMobileScreen(screen.parentScreen))
                }
                // Persist a privacy-safe resume reference (never the resolved URL/credentials):
                // a deep link that the navigation layer re-resolves on restore. Live channels
                // rebuild directly into the player; VOD resumes via the details screen.
                resumeDeepLinkFor(screen.request)?.let { putString("resume_deeplink", it) }
            }
            is MobileScreen.Resume -> {
                putString("type", "Player")
                putString("resume_deeplink", screen.deepLink)
                putBundle("parent_screen", saveMobileScreen(screen.parentScreen))
            }
        }
    }
}

private fun resumeDeepLinkFor(request: com.example.calmsource.core.model.PlaybackRequest): String? {
    val source = request.source
    return if (source.metadata?.isLive == true) {
        com.example.calmsource.core.model.CalmSourceDeepLink.channelUri(source.id)
    } else {
        request.userMemoryReference?.let { reference ->
            com.example.calmsource.core.model.CalmSourceDeepLink.detailsUri(reference, request.startPositionMs)
        }
    }
}

private fun restoreMobileScreen(bundle: Bundle): MobileScreen? {
    return when (val type = bundle.getString("type")) {
        "Home" -> MobileScreen.Home
        "LiveTv" -> MobileScreen.LiveTv
        "Library" -> MobileScreen.Library
        "Search" -> MobileScreen.Search
        "Settings" -> MobileScreen.Settings
        "Details" -> {
            val json = bundle.getString("media_item_json")
            val mediaItem = json?.let { kotlinx.serialization.json.Json.decodeFromString(com.example.calmsource.core.model.MediaItem.serializer(), it) }
            if (mediaItem != null) {
                MobileScreen.Details(mediaItem, bundle.getLong("start_position_ms"))
            } else {
                MobileScreen.Home
            }
        }
        "Player" -> {
            val resumeDeepLink = bundle.getString("resume_deeplink")
            val parentBundle = bundle.getBundle("parent_screen")
            val parentScreen = if (parentBundle != null) {
                restoreMobileScreen(parentBundle) ?: MobileScreen.Home
            } else {
                MobileScreen.Home
            }
            if (resumeDeepLink != null) {
                MobileScreen.Resume(resumeDeepLink, parentScreen)
            } else {
                parentScreen
            }
        }
        else -> null
    }
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
  private var pendingDeepLink by mutableStateOf<String?>(null)
  private var restoredScreen: MobileScreen? = null
  private var currentScreen: MobileScreen = MobileScreen.Home

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    if (savedInstanceState == null) {
      pendingDeepLink = intent?.dataString
    } else {
      val screenBundle = savedInstanceState.getBundle("current_screen")
      if (screenBundle != null) {
        restoredScreen = restoreMobileScreen(screenBundle)
        // If we died while playing, restore the underlying tab/parent above and replay a
        // privacy-safe resume deep link on top so playback (or details) comes back.
        if (screenBundle.getString("type") == "Player") {
          pendingDeepLink = screenBundle.getString("resume_deeplink")
        }
      }
    }

    enableEdgeToEdge()
    setContent {
      LumenTheme(isTv = false) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
          MainNavigation(
            deepLinkUri = pendingDeepLink,
            onDeepLinkConsumed = { pendingDeepLink = null },
            initialScreen = restoredScreen,
            onScreenChanged = { currentScreen = it }
          )
        }
      }
    }
  }

  override fun onSaveInstanceState(outState: Bundle) {
    super.onSaveInstanceState(outState)
    outState.putBundle("current_screen", saveMobileScreen(currentScreen))
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    pendingDeepLink = intent.dataString
  }
}
