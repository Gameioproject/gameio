package com.nendo.argosy.ui.screens.addons

import androidx.lifecycle.viewModelScope
import com.nendo.argosy.data.addon.*
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class AddonsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val repository = mockk<AddonRepository>()
    private val debrid = mockk<AddonDebridResolver>()
    private val addons = MutableStateFlow(listOf(addon()))
    private val account = MutableStateFlow(false)
    private lateinit var vm: AddonsViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
        every { repository.addons } returns addons
        every { repository.unreadableCount } returns MutableStateFlow(0)
        coEvery { repository.load() } returns addons.value
        every { debrid.hasAccount } returns account
        coEvery { debrid.loadAccount() } returns false
        vm = AddonsViewModel(repository, debrid)
        dispatcher.scheduler.advanceUntilIdle()
    }

    @After
    fun cleanup() {
        vm.viewModelScope.cancel()
        Dispatchers.resetMain()
    }

    @Test
    fun `import opens a picker and cancelled picker installs nothing`() {
        vm.activate(AddonAction.Import) { fail("must not exit") }
        assertTrue(vm.state.value.pickerOpen)
        vm.closePicker()
        assertFalse(vm.state.value.pickerOpen)
        coVerify(exactly = 0) { repository.importFile(any(), any()) }
    }

    @Test
    fun `import delegates private storage and surfaces success`() {
        coEvery { repository.importFile("/games/provider.json", null) } returns addon()
        vm.activate(AddonAction.Import) { }
        vm.importFile("/games/provider.json")
        dispatcher.scheduler.advanceUntilIdle()
        coVerify(exactly = 1) { repository.importFile("/games/provider.json", null) }
        assertEquals("Test sources", vm.state.value.importedName)
        assertFalse(vm.state.value.busy)
        assertFalse(vm.state.value.pickerOpen)
    }

    @Test
    fun `reimport enforces identity and presents failure without deleting existing addon`() {
        coEvery { repository.importFile("/games/wrong.json", "test.sources") } throws AddonException(AddonFailure.INVALID_MANIFEST)
        vm.activate(AddonAction.Reimport("test.sources")) { }
        vm.importFile("/games/wrong.json")
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(AddonFailure.INVALID_MANIFEST, vm.state.value.failure)
        assertEquals(listOf("test.sources"), vm.state.value.addons.map { it.manifest.id })
        assertTrue(AddonAction.Reload in vm.state.value.actions)
        coVerify(exactly = 0) { repository.remove(any()) }
    }

    @Test
    fun `remove requires confirmation and only calls addon removal`() {
        coEvery { repository.remove("test.sources") } coAnswers { addons.value = emptyList() }
        vm.activate(AddonAction.Remove("test.sources")) { }
        coVerify(exactly = 0) { repository.remove(any()) }
        vm.cancelRemove()
        coVerify(exactly = 0) { repository.remove(any()) }
        vm.activate(AddonAction.Remove("test.sources")) { }
        vm.confirmRemove()
        dispatcher.scheduler.advanceUntilIdle()
        coVerify(exactly = 1) { repository.remove("test.sources") }
        assertTrue(vm.state.value.addons.isEmpty())
        assertTrue(vm.state.value.focusedIndex in vm.state.value.actions.indices)
    }

    @Test
    fun `toggle uses persisted enabled state and updates without import`() {
        coEvery { repository.setEnabled("test.sources", false) } coAnswers {
            addons.value = listOf(addon().copy(enabled = false))
        }
        vm.activate(AddonAction.Toggle("test.sources")) { }
        dispatcher.scheduler.advanceUntilIdle()
        assertFalse(vm.state.value.addons.single().enabled)
        coVerify(exactly = 1) { repository.setEnabled("test.sources", false) }
        coVerify(exactly = 0) { repository.importFile(any(), any()) }
    }

    @Test
    fun `controller wraps and cannot act behind a child modal`() {
        val handler = AddonsInputHandler(vm) { fail("modal must block exit") }
        handler.onUp()
        assertEquals(vm.state.value.actions.lastIndex, vm.state.value.focusedIndex)
        vm.activate(AddonAction.Import) { }
        val focus = vm.state.value.focusedIndex
        handler.onDown()
        handler.onConfirm()
        handler.onBack()
        handler.onLeft()
        handler.onMenu()
        assertTrue(vm.state.value.pickerOpen)
        assertEquals(focus, vm.state.value.focusedIndex)
    }

    @Test
    fun `account connects through validation and clears token from UI`() {
        coEvery { debrid.setToken("private-token") } coAnswers { account.value = true }
        vm.activate(AddonAction.Account) { }
        vm.setToken("private-token")
        vm.saveToken()
        dispatcher.scheduler.advanceUntilIdle()
        assertTrue(vm.state.value.hasAccount)
        assertEquals("", vm.state.value.token)
        assertFalse(vm.state.value.tokenBusy)
        coVerify(exactly = 1) { debrid.setToken("private-token") }
    }

    @Test
    fun `invalid token stays editable and failure is actionable`() {
        coEvery { debrid.setToken(any()) } throws AddonException(AddonFailure.ACCOUNT_REJECTED)
        vm.activate(AddonAction.Account) { }
        vm.setToken("invalid-token")
        vm.saveToken()
        dispatcher.scheduler.advanceUntilIdle()
        assertFalse(vm.state.value.hasAccount)
        assertEquals(AddonFailure.ACCOUNT_REJECTED, vm.state.value.tokenFailure)
        assertEquals("invalid-token", vm.state.value.token)
        vm.closeAccount()
        assertEquals("", vm.state.value.token)
    }

    @Test
    fun `cancel account validation cancels work and clears token`() {
        coEvery { debrid.setToken(any()) } coAnswers { delay(10_000); account.value = true }
        vm.activate(AddonAction.Account) { }
        vm.setToken("private-token")
        vm.saveToken()
        dispatcher.scheduler.runCurrent()
        vm.closeAccount()
        dispatcher.scheduler.advanceUntilIdle()
        assertFalse(vm.state.value.accountOpen)
        assertFalse(vm.state.value.hasAccount)
        assertFalse(vm.state.value.tokenBusy)
        assertEquals("", vm.state.value.token)
    }

    @Test
    fun `account controller reaches paste and token keyboard blocks underlying actions`() {
        vm.activate(AddonAction.Account) { }
        var pasted = false
        val handler = AddonAccountInputHandler(vm, { pasted = true }, { })
        handler.onDown()
        handler.onConfirm()
        assertTrue(pasted)
        handler.onUp()
        handler.onConfirm()
        assertTrue(vm.state.value.tokenKeyboard)
        handler.onDown()
        handler.onConfirm()
        assertEquals(0, vm.state.value.tokenFocus)
        handler.onBack()
        assertFalse(vm.state.value.tokenKeyboard)
        assertTrue(vm.state.value.accountOpen)
        handler.onBack()
        assertFalse(vm.state.value.accountOpen)
    }

    @Test
    fun `unreadable credentials expose reset even when account is disconnected`() {
        coEvery { debrid.loadAccount() } throws AddonException(AddonFailure.STORAGE)
        vm.reload()
        dispatcher.scheduler.advanceUntilIdle()
        assertFalse(vm.state.value.hasAccount)
        assertTrue(vm.state.value.accountNeedsReset)
        vm.activate(AddonAction.Account) { }
        assertEquals(AddonFailure.STORAGE, vm.state.value.tokenFailure)
        coEvery { debrid.disconnect() } returns Unit
        val handler = AddonAccountInputHandler(vm, { }, { })
        repeat(5) { handler.onDown() }
        handler.onConfirm()
        dispatcher.scheduler.advanceUntilIdle()
        coVerify(exactly = 1) { debrid.disconnect() }
        assertFalse(vm.state.value.accountNeedsReset)
        assertNull(vm.state.value.tokenFailure)
    }

    companion object {
        private fun addon() = InstalledAddon(AddonManifest(
            schemaVersion = 1, id = "test.sources", name = "Test sources", version = "1",
            adapter = "catalog-shards-v1", lookup = AddonLookup("igdbId:platformSlug", "sha256-prefix-2", "https://example.org/{shard}.json"),
            allowedHosts = listOf("example.org")
        ))
    }
}
