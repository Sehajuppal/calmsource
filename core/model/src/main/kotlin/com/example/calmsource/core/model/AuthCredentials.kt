package com.example.calmsource.core.model

import kotlinx.serialization.Serializable

@Serializable
data class AuthCredentials(
    @Deprecated("Use xtreamUrl instead")
    val xtreamServerUrl: String? = null,
    @Deprecated("Use username instead")
    val xtreamUsername: String? = null,
    @Deprecated("Use password instead")
    val xtreamPassword: String? = null,
    @Deprecated("Use debridToken instead")
    val realDebridToken: String? = null,
    val xtreamUrl: String? = null,
    val username: String? = null,
    val password: String? = null,
    val debridToken: String? = null,
    val installedExtensions: List<String>? = null
)
