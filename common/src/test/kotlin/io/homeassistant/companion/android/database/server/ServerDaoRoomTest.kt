package io.homeassistant.companion.android.database.server

import android.content.Context
import androidx.room3.Room
import androidx.test.core.app.ApplicationProvider
import dagger.hilt.android.testing.HiltTestApplication
import io.homeassistant.companion.android.database.AppDatabase
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests the [ServerDao] against a real in-memory database. The extra URLs are stored in a column with a type
 * converter, and which converter Room uses depends on where the value is written, so a mocked DAO cannot show
 * whether a stored list can be read back.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class ServerDaoRoomTest {

    private lateinit var database: AppDatabase
    private lateinit var serverDao: ServerDao

    private fun server(extraUrls: List<String> = emptyList()) = Server(
        id = 1,
        _name = "Test Server",
        connection = ServerConnectionInfo(
            externalUrl = "https://external.example.com",
            internalSsids = listOf("Home", "Home 5G"),
            extraUrls = extraUrls,
        ),
        session = ServerSessionInfo(accessToken = "access-token", refreshToken = "refresh-token"),
        user = ServerUserInfo(),
    )

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        serverDao = database.serverDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `Given extra URLs written with the whole server when reading then they are returned`() = runTest {
        serverDao.add(server(extraUrls = listOf("https://a.example.com/", "http://100.64.0.1:8123/")))

        assertEquals(
            listOf("https://a.example.com/", "http://100.64.0.1:8123/"),
            serverDao.get(1)?.connection?.extraUrls,
        )
    }

    @Test
    fun `Given a server when updating only the extra URLs then they can be read back`() = runTest {
        serverDao.add(server())

        serverDao.updateExtraUrls(1, listOf("https://a.example.com/", "http://100.64.0.1:8123/"))

        assertEquals(
            listOf("https://a.example.com/", "http://100.64.0.1:8123/"),
            serverDao.get(1)?.connection?.extraUrls,
        )
    }

    @Test
    fun `Given a single extra URL when updating only the extra URLs then it can be read back`() = runTest {
        serverDao.add(server())

        serverDao.updateExtraUrls(1, listOf("http://100.76.208.98:8123/"))

        assertEquals(listOf("http://100.76.208.98:8123/"), serverDao.get(1)?.connection?.extraUrls)
    }

    @Test
    fun `Given extra URLs when updating with an empty list then none are returned`() = runTest {
        serverDao.add(server(extraUrls = listOf("https://a.example.com/")))

        serverDao.updateExtraUrls(1, emptyList())

        assertEquals(emptyList<String>(), serverDao.get(1)?.connection?.extraUrls)
    }

    @Test
    fun `Given a server when updating only the extra URLs then no other column changes`() = runTest {
        serverDao.add(server())

        serverDao.updateExtraUrls(1, listOf("https://a.example.com/"))

        val stored = checkNotNull(serverDao.get(1))
        assertEquals("access-token", stored.session.accessToken)
        assertEquals("refresh-token", stored.session.refreshToken)
        assertEquals(listOf("Home", "Home 5G"), stored.connection.internalSsids)
        assertEquals("https://external.example.com", stored.connection.externalUrl)
    }

    @Test
    fun `Given extra URLs updated on their own when the whole server is saved again then they are kept`() = runTest {
        serverDao.add(server())
        serverDao.updateExtraUrls(1, listOf("https://a.example.com/"))

        val stored = checkNotNull(serverDao.get(1))
        serverDao.update(stored.copy(session = stored.session.copy(accessToken = "new-token")))

        val reloaded = checkNotNull(serverDao.get(1))
        assertEquals(listOf("https://a.example.com/"), reloaded.connection.extraUrls)
        assertEquals("new-token", reloaded.session.accessToken)
    }

    @Test
    fun `Given an unknown server ID when updating the extra URLs then nothing is changed`() = runTest {
        serverDao.add(server(extraUrls = listOf("https://a.example.com/")))

        serverDao.updateExtraUrls(-1, listOf("https://b.example.com/"))

        assertEquals(listOf("https://a.example.com/"), serverDao.get(1)?.connection?.extraUrls)
    }
}
