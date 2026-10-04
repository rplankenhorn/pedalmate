package dev.pedalmate.overlay

import android.content.Context
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import androidx.savedstate.findViewTreeSavedStateRegistryOwner
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

open class ProbeVm : ViewModel() {
    var cleared = false
    var stateAtClear: Lifecycle.State? = null

    override fun onCleared() {
        cleared = true
        stateAtClear = hostForProbe?.lifecycle?.currentState
    }

    companion object {
        var hostForProbe: ComposeOverlayHost? = null
    }
}

@RunWith(RobolectricTestRunner::class)
class ComposeOverlayHostTest {
    private val ctx = ApplicationProvider.getApplicationContext<Context>()
    private fun newHost() = ComposeOverlayHost(ctx, { _, _ -> }, {}) { }

    @Test
    fun constructionSetsOwnersAndCreatedState() {
        val host = newHost()
        assertEquals(Lifecycle.State.CREATED, host.lifecycle.currentState)
        assertSame(host, host.root.findViewTreeLifecycleOwner())
        assertSame(host, host.composeView.findViewTreeLifecycleOwner())
        assertSame(host, host.root.findViewTreeViewModelStoreOwner())
        assertSame(host, host.composeView.findViewTreeSavedStateRegistryOwner())
    }

    @Test
    fun savedStateRestoredBeforeCreated() {
        assertTrue(newHost().savedStateRegistry.isRestored)
    }

    @Test
    fun onAttachedResumes() {
        val host = newHost()
        host.onAttached()
        assertEquals(Lifecycle.State.RESUMED, host.lifecycle.currentState)
    }

    @Test
    fun destroyDestroysAndClearsViewModels() {
        val host = newHost()
        host.onAttached()
        val vm = ViewModelProvider(host.viewModelStore, ViewModelProvider.NewInstanceFactory())[ProbeVm::class.java]
        host.destroy()
        assertEquals(Lifecycle.State.DESTROYED, host.lifecycle.currentState)
        assertTrue(vm.cleared)
    }

    @Test
    fun lifecycleIsDestroyedBeforeViewModelsAreCleared() {
        val host = newHost()
        ProbeVm.hostForProbe = host
        host.onAttached()
        val vm = ViewModelProvider(host.viewModelStore, ViewModelProvider.NewInstanceFactory())[ProbeVm::class.java]
        host.destroy()
        ProbeVm.hostForProbe = null
        assertEquals(Lifecycle.State.DESTROYED, vm.stateAtClear)
    }
}
