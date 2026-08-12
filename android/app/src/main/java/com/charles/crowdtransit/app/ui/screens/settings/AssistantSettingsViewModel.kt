package com.charles.crowdtransit.app.ui.screens.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.charles.crowdtransit.app.ai.chat.AssistantSession
import com.charles.crowdtransit.app.ai.device.DeviceCapability
import com.charles.crowdtransit.app.ai.device.DeviceProbe
import com.charles.crowdtransit.app.ai.model.AssistantModelCatalog
import com.charles.crowdtransit.app.ai.model.AssistantModelDownloader
import com.charles.crowdtransit.app.ai.model.AssistantModelStore
import com.charles.crowdtransit.app.data.preferences.UserPreferencesStore
import com.charles.crowdtransit.app.service.AssistantDownloadService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AssistantSettingsUiState(
    val tier: DeviceCapability.Tier = DeviceCapability.Tier.Unsupported,
    val recommendedVariant: AssistantModelCatalog.Variant? = null,
    val offeredVariants: List<AssistantModelCatalog.Variant> = emptyList(),
    val installedVariant: AssistantModelCatalog.Variant? = null,
    val downloadState: AssistantModelDownloader.State = AssistantModelDownloader.State.Idle,
    val enabled: Boolean = false,
)

@HiltViewModel
class AssistantSettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    deviceProbe: DeviceProbe,
    private val modelStore: AssistantModelStore,
    private val downloader: AssistantModelDownloader,
    private val userPreferences: UserPreferencesStore,
    private val session: AssistantSession,
) : ViewModel() {

    private val tier = deviceProbe.tier()

    val uiState: StateFlow<AssistantSettingsUiState> = combine(
        downloader.state,
        userPreferences.assistantEnabled,
        userPreferences.assistantVariant,
    ) { downloadState, enabled, variantName ->
        // Both variants currently share one underlying model file, so file-presence alone
        // (AssistantModelStore.installedVariant()) can't tell them apart — it would always
        // tie-break to the same variant regardless of which card the user actually picked.
        // The selected-variant preference (set by download() below) is the source of truth
        // for which card shows as installed; it still requires the file to really be there.
        val selected = variantName?.let { runCatching { AssistantModelCatalog.Variant.valueOf(it) }.getOrNull() }
        val installed = selected?.takeIf { modelStore.isInstalled(AssistantModelCatalog.byVariant(it)) }
        AssistantSettingsUiState(
            tier = tier,
            recommendedVariant = recommendedVariant(tier),
            offeredVariants = offeredVariants(tier),
            installedVariant = installed,
            downloadState = downloadState,
            enabled = enabled,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AssistantSettingsUiState(tier = tier))

    private fun recommendedVariant(tier: DeviceCapability.Tier): AssistantModelCatalog.Variant? = when (tier) {
        DeviceCapability.Tier.Unsupported -> null
        DeviceCapability.Tier.Basic, DeviceCapability.Tier.Standard -> AssistantModelCatalog.Variant.TextOnly
        DeviceCapability.Tier.Advanced -> AssistantModelCatalog.Variant.Multimodal
    }

    private fun offeredVariants(tier: DeviceCapability.Tier): List<AssistantModelCatalog.Variant> = when (tier) {
        DeviceCapability.Tier.Unsupported -> emptyList()
        DeviceCapability.Tier.Basic -> listOf(AssistantModelCatalog.Variant.TextOnly)
        DeviceCapability.Tier.Standard, DeviceCapability.Tier.Advanced -> AssistantModelCatalog.Variant.entries.toList()
    }

    fun setEnabled(enabled: Boolean) {
        viewModelScope.launch { userPreferences.setAssistantEnabled(enabled) }
    }

    /** Runs as a dataSync foreground service (AssistantDownloadService) so a multi-GB
     * download survives the app being backgrounded. */
    fun download(variant: AssistantModelCatalog.Variant) {
        viewModelScope.launch {
            userPreferences.setAssistantVariant(variant.name)
            // Always CPU: confirmed on a real device that Engine.initialize() succeeds
            // with GPU (it doesn't eagerly validate the delegate), but the first actual
            // generation then fails with "Can not find OpenCL library on this device" —
            // apps can't reliably access the OpenCL delegate on stock Android. CPU is
            // slower but is the backend that's actually proven to work end-to-end.
            userPreferences.setAssistantBackend("cpu")
        }
        AssistantDownloadService.start(context, variant)
    }

    fun cancelDownload() {
        AssistantDownloadService.stop(context)
    }

    fun delete(variant: AssistantModelCatalog.Variant) {
        session.unload()
        modelStore.delete(AssistantModelCatalog.byVariant(variant))
        viewModelScope.launch {
            userPreferences.setAssistantVariant(null)
            session.clearHistory()
        }
    }

    fun clearHistory() {
        viewModelScope.launch { session.clearHistory() }
    }
}
