import android.content.Context
import android.content.Intent
import android.net.Uri
import net.openid.appauth.*
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

object SpotifyConfig {
    const val CLIENT_ID = "PASTE_YOUR_SPOTIFY_CLIENT_ID_HERE"
    const val REDIRECT_URI = "reeltune://callback"
    const val SCOPES = "playlist-read-private playlist-modify-private playlist-modify-public"
}

class SpotifyAuth(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("reeltune", Context.MODE_PRIVATE)
    private val service = AuthorizationService(appContext)
    private val config = AuthorizationServiceConfiguration(
        Uri.parse("https://accounts.spotify.com/authorize"),
        Uri.parse("https://accounts.spotify.com/api/token")
    )
    private var authState: AuthState = prefs.getString("auth_state", null)
        ?.let { AuthState.jsonDeserialize(it) } ?: AuthState(config)

    val isLoggedIn: Boolean get() = authState.isAuthorized

    private fun save() = prefs.edit().putString("auth_state", authState.jsonSerializeString()).apply()

    fun buildLoginIntent(): Intent {
        val request = AuthorizationRequest.Builder(
            config, SpotifyConfig.CLIENT_ID, ResponseTypeValues.CODE, Uri.parse(SpotifyConfig.REDIRECT_URI)
        ).setScope(SpotifyConfig.SCOPES).build()   // AppAuth adds PKCE automatically
        return service.getAuthorizationRequestIntent(request)
    }

    fun handleLoginResult(data: Intent?, onDone: (Boolean, String?) -> Unit) {
        val resp = data?.let { AuthorizationResponse.fromIntent(it) }
        val ex = data?.let { AuthorizationException.fromIntent(it) }
        if (resp == null) { onDone(false, ex?.errorDescription ?: "Login cancelled"); return }
        authState.update(resp, ex)
        service.performTokenRequest(resp.createTokenExchangeRequest()) { tokenResp, tokenEx ->
            authState.update(tokenResp, tokenEx)
            save()
            onDone(tokenResp != null, tokenEx?.errorDescription)
        }
    }

    suspend fun getAccessToken(): String = suspendCancellableCoroutine { cont ->
        authState.performActionWithFreshTokens(service) { token, _, ex ->
            if (token != null) { save(); cont.resume(token) }
            else cont.resumeWithException(ex ?: Exception("No token"))
        }
    }

    fun logout() { authState = AuthState(config); save() }

    // Target playlist
    fun saveTarget(id: String, name: String) =
        prefs.edit().putString("target_id", id).putString("target_name", name).apply()
    fun targetId(): String? = prefs.getString("target_id", null)
    fun targetName(): String? = prefs.getString("target_name", null)
}