package com.nendo.argosy.ui.screens.settings

import androidx.lifecycle.viewModelScope
import com.nendo.argosy.R
import com.nendo.argosy.core.notification.NotificationText
import com.nendo.argosy.core.notification.NotificationType
import com.nendo.argosy.data.remote.romm.normalizeSupportUrl
import kotlinx.coroutines.launch

internal fun routeSupportGameio(vm: SettingsViewModel) {
    val url = normalizeSupportUrl(vm._uiState.value.server.supportUrl) ?: return
    vm.viewModelScope.launch { vm._openUrlEvent.emit(url) }
}

internal fun routeOpenUrlFailed(vm: SettingsViewModel) {
    vm.notificationManager.show(
        title = NotificationText.Res(R.string.support_gameio_browser_error),
        type = NotificationType.ERROR
    )
}
