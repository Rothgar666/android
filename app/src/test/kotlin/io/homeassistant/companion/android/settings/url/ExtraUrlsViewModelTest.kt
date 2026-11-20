package io.homeassistant.companion.android.settings.url

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.database.server.Server
import io.homeassistant.companion.android.database.server.ServerConnectionInfo
import io.homeassistant.companion.android.database.server.ServerSessionInfo
import io.homeassistant.companion.android.database.server.ServerUserInfo
import io.homeassistant.companion.android.testing.unit.MainDispatcherJUnit5Extension
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

@OptIn(ExperimentalCoroutinesApi::class)
class ExtraUrlsViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @RegisterExtension
    val mainDispatcherExtension = MainDispatcherJUnit5Extension(testDispatcher)

    private val serverId = 1
    private val serverManager: ServerManager = mockk()

    /** The extra URLs as stored in the "database" of the fake server manager. */
    private var storedUrls: List<String> = emptyList()

    /** The ID of the server in the database, which can differ from the ID the settings screen was opened with. */
    private var databaseServerId = serverId

    private fun server() = Server(
        id = databaseServerId,
        _name = "Test Server",
        nameOverride = null,
        _version = "2024.1.0",
        deviceRegistryId = null,
        listOrder = 0,
        deviceName = null,
        connection = ServerConnectionInfo(
            externalUrl = "https://external.example.com",
            internalUrl = "https://internal.example.com:8123",
            extraUrls = storedUrls,
        ),
        session = ServerSessionInfo(),
        user = ServerUserInfo(),
    )

    private fun createViewModel(
        initialUrls: List<String> = emptyList(),
        screenServerId: Int = serverId,
        serverIdInDatabase: Int = serverId,
    ): ExtraUrlsViewModel {
        storedUrls = initialUrls
        databaseServerId = serverIdInDatabase
        // yield() makes reading and writing asynchronous like the real database does.
        coEvery { serverManager.getServer(screenServerId) } coAnswers {
            yield()
            server()
        }
        // Only the real database ID is accepted, like the real update which matches rows by ID.
        coEvery { serverManager.updateExtraUrls(serverIdInDatabase, any()) } coAnswers {
            yield()
            storedUrls = secondArg()
        }
        return ExtraUrlsViewModel(
            state = SavedStateHandle(mapOf(ExtraUrlsFragment.EXTRA_SERVER to screenServerId)),
            serverManager = serverManager,
            application = mockk<Application>(relaxed = true),
        )
    }

    @Test
    fun `Given a server with extra URLs when the screen opens then they are shown`() = runTest(testDispatcher) {
        val viewModel = createViewModel(listOf("https://a.example.com/", "https://b.example.com/"))
        advanceUntilIdle()

        assertEquals(listOf("https://a.example.com/", "https://b.example.com/"), viewModel.uiState.value.urls)
    }

    @Test
    fun `Given a valid URL when adding then it is normalized, saved on its own and the field can be cleared`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        val added = viewModel.addUrl("https://vpn.example.com:8123/lovelace")

        assertTrue(added)
        assertEquals(listOf("https://vpn.example.com:8123/"), viewModel.uiState.value.urls)
        coVerify { serverManager.updateExtraUrls(serverId, listOf("https://vpn.example.com:8123/")) }
        // Only the extra URLs are written, the rest of the server (for example the session) is not.
        coVerify(exactly = 0) { serverManager.updateServer(any()) }
    }

    @Test
    fun `Given the screen opened for the active server when adding a URL then it is saved for the real server`() = runTest(testDispatcher) {
        // The server settings screen can hand over SERVER_ID_ACTIVE instead of the database ID of the server.
        val viewModel = createViewModel(
            screenServerId = ServerManager.SERVER_ID_ACTIVE,
            serverIdInDatabase = 5,
        )
        advanceUntilIdle()

        assertTrue(viewModel.addUrl("https://vpn.example.com"))

        coVerify { serverManager.updateExtraUrls(5, listOf("https://vpn.example.com/")) }
        assertEquals(listOf("https://vpn.example.com/"), storedUrls)
    }

    @Test
    fun `Given the screen opened for the active server when removing a URL then it is removed for the real server`() = runTest(testDispatcher) {
        val viewModel = createViewModel(
            initialUrls = listOf("https://a.example.com/", "https://b.example.com/"),
            screenServerId = ServerManager.SERVER_ID_ACTIVE,
            serverIdInDatabase = 5,
        )
        advanceUntilIdle()

        viewModel.removeUrl("https://a.example.com/")
        advanceUntilIdle()

        assertEquals(listOf("https://b.example.com/"), storedUrls)
    }

    @Test
    fun `Given the internal URL typed without a trailing slash when adding it then it is rejected as duplicate`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        val added = viewModel.addUrl("https://internal.example.com:8123/")

        assertFalse(added)
        assertEquals(ExtraUrlError.Duplicate, viewModel.uiState.value.error)
        coVerify(exactly = 0) { serverManager.updateExtraUrls(any(), any()) }
    }

    @Test
    fun `Given an error when the input changes then the error is cleared`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.addUrl("not a url")
        assertEquals(ExtraUrlError.Invalid, viewModel.uiState.value.error)

        viewModel.clearError()

        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun `Given quick successive edits when they are applied then none of them is lost`() = runTest(testDispatcher) {
        val viewModel = createViewModel(listOf("https://a.example.com/", "https://b.example.com/", "https://c.example.com/"))
        advanceUntilIdle()

        viewModel.removeUrl("https://a.example.com/")
        viewModel.removeUrl("https://b.example.com/")
        advanceUntilIdle()

        assertEquals(listOf("https://c.example.com/"), storedUrls)
        assertEquals(listOf("https://c.example.com/"), viewModel.uiState.value.urls)
    }

    @Test
    fun `Given a URL that is already gone when removing it then nothing is written`() = runTest(testDispatcher) {
        val viewModel = createViewModel(listOf("https://a.example.com/"))
        advanceUntilIdle()

        viewModel.removeUrl("https://gone.example.com/")
        advanceUntilIdle()

        coVerify(exactly = 0) { serverManager.updateExtraUrls(any(), any()) }
        assertEquals(listOf("https://a.example.com/"), storedUrls)
    }

    @Test
    fun `Given URLs when moving one down then the order changes`() = runTest(testDispatcher) {
        val viewModel = createViewModel(listOf("https://a.example.com/", "https://b.example.com/"))
        advanceUntilIdle()

        viewModel.moveUrl("https://a.example.com/", 1)
        advanceUntilIdle()

        assertEquals(listOf("https://b.example.com/", "https://a.example.com/"), storedUrls)
    }
}
