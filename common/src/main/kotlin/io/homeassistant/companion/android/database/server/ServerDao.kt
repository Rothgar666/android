package io.homeassistant.companion.android.database.server

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ServerDao {

    @Query("SELECT * FROM servers WHERE id = :id")
    suspend fun get(id: Int): Server?

    @Query("SELECT * FROM servers WHERE webhook_id = :webhookId")
    suspend fun get(webhookId: String): Server?

    @Query("SELECT * FROM servers WHERE id = :serverId")
    fun getFlow(serverId: Int): Flow<Server?>

    @Query("SELECT * FROM servers ORDER BY `list_order` ASC")
    suspend fun getAll(): List<Server>

    @Query("SELECT id FROM servers ORDER BY id DESC LIMIT 1")
    suspend fun getLastServerId(): Int?

    @Query("SELECT * FROM servers ORDER BY `list_order` ASC")
    fun getAllFlow(): Flow<List<Server>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun add(server: Server): Long

    @Update
    suspend fun update(server: Server)

    @Update
    suspend fun update(servers: List<Server>)

    /**
     * Replaces only the extra URLs of a server, leaving every other column untouched. Unlike [update] this
     * cannot overwrite concurrent changes of other fields, such as a refreshed session token.
     */
    suspend fun updateExtraUrls(id: Int, extraUrls: List<String>) {
        updateExtraUrlsJson(id, InternalSsidColumnTypeConverter().fromListToString(extraUrls))
    }

    /**
     * Writes the already converted value of the `extra_urls` column. Use [updateExtraUrls] instead.
     *
     * The value is passed as [String] on purpose: the list converter of [Server] only applies to the columns of
     * the entity. For a list parameter of a query Room would pick another converter of the database, which writes
     * a different format that cannot be read back, so the stored URLs would be lost.
     */
    @Query("UPDATE servers SET extra_urls = :extraUrlsJson WHERE id = :id")
    suspend fun updateExtraUrlsJson(id: Int, extraUrlsJson: String)

    @Query("DELETE FROM servers WHERE id = :id")
    suspend fun delete(id: Int)

    @Query("DELETE FROM servers WHERE webhook_id = :webhookId")
    suspend fun delete(webhookId: String)
}
