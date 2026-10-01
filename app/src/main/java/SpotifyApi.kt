import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

data class Playlist(val id: String?, val name: String?)
data class PlaylistsResponse(val items: List<Playlist?>)

interface SpotifyService {
    @GET("me/playlists")
    suspend fun myPlaylists(
        @Header("Authorization") auth: String,
        @Query("limit") limit: Int = 50
    ): PlaylistsResponse
}

object SpotifyApi {
    val service: SpotifyService = Retrofit.Builder()
        .baseUrl("https://api.spotify.com/v1/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(SpotifyService::class.java)
}