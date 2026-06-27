package com.example.calmsource.core.discoveryengine.providers

import android.content.Context
import android.util.Log
import com.example.calmsource.core.database.DatabaseProvider
import com.example.calmsource.core.database.entity.ExtensionProviderEntity
import com.example.calmsource.core.discoveryengine.providers.adapters.GenericStremioAddonProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

object AddonProviderManager {

    private const val TAG = "AddonProviderManager"

    @Volatile private var initialized = false
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun init(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            initialized = true
            val appContext = context.applicationContext
            scope.launch {
                runCatching {
                    DatabaseProvider.getDatabase(appContext)
                        .extensionDao()
                        .getAllExtensions()
                        .collectLatest { extensions ->
                            syncExtensions(extensions)
                        }
                }.onFailure { error ->
                    Log.w(TAG, "Unable to observe extension providers: ${error.message}")
                }
            }
        }
    }

    private suspend fun syncExtensions(extensions: List<ExtensionProviderEntity>) {
        val activeProviderIds = mutableSetOf<String>()
        extensions
            .filter { it.isEnabled }
            .sortedBy { it.priority }
            .forEach { entity ->
                val capabilities = GenericStremioAddonProvider.capabilitiesFor(entity)
                if (capabilities.isEmpty()) return@forEach
                activeProviderIds += entity.id
                ProviderManager.registerAddonProvider(
                    addonId = entity.id,
                    addonName = entity.name.ifBlank { entity.id },
                    endpointUrl = entity.url,
                    capabilities = capabilities,
                    isEnabled = entity.isEnabled,
                    priority = entity.priority,
                    createProvider = { GenericStremioAddonProvider(entity) }
                )
            }

        ProviderManager.retainAddonProviders(activeProviderIds)
    }
}
