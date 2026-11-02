package com.example.appgestin.model

/** Local access for the app prototype; grants no staff role on the API. */
object LocalAccess {
    fun accepts(username: String, password: String): Boolean =
        username.trim() == "admin" && password == "admin"
}

data class LocalProfile(
    val name: String = "Admin",
    val email: String = "",
    val bio: String = "",
    val photoPath: String? = null
)
