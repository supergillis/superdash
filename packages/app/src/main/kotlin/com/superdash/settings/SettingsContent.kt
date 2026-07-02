package com.superdash.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.superdash.settings.ui.AdminSettingsSection
import com.superdash.settings.ui.ConnectionSettingsSection
import com.superdash.settings.ui.DeviceSettingsSection
import com.superdash.settings.ui.DoorbellSettingsSection
import com.superdash.settings.ui.EsphomeSettingsSection
import com.superdash.settings.ui.ImmichSettingsSection
import com.superdash.settings.ui.ScreensaverSettingsSection
import com.superdash.settings.ui.SettingsDetailHeader
import com.superdash.settings.ui.SettingsNavigationRow
import com.superdash.settings.ui.SidebarSettingsSection
import com.superdash.settings.ui.VoiceAdvancedTuningSettingsSection
import com.superdash.settings.ui.VoiceCommandRecordingSettingsSection
import com.superdash.settings.ui.VoiceLocalModelsSettingsSection
import com.superdash.settings.ui.VoiceSettingsSection
import com.superdash.settings.ui.VoiceSpeechPipelineSettingsSection

@Composable
internal fun SettingsContent(
    state: SettingsUiState,
    actions: SettingsActions,
    mediaSourcePicker: @Composable (
        onConfirm: (id: String, title: String) -> Unit,
        onDismiss: () -> Unit,
    ) -> Unit,
    forceWideForTest: Boolean? = null,
) {
    var destination: SettingsDestination? by remember { mutableStateOf(null) }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isWide =
            forceWideForTest ?: (maxWidth >= SettingsLayout.wideBreakpoint)
        val navigateUp = {
            destination = SettingsLayout.navigateUp(destination)
        }

        LaunchedEffect(isWide, destination) {
            if (isWide && destination == null) {
                destination = SettingsLayout.initialDestination(isWide)
            }
        }

        val handlesBack =
            destination is SettingsDestination.Child || (!isWide && destination != null)

        BackHandler(enabled = handlesBack) {
            navigateUp()
        }

        Box(modifier = Modifier.fillMaxSize()) {
            val onBackClick = {
                if (handlesBack) {
                    navigateUp()
                } else {
                    actions.onBack()
                }
            }

            if (isWide) {
                SettingsWideContent(
                    destination = destination ?: SettingsDestination.TopLevel.HomeAssistant,
                    state = state,
                    actions = actions,
                    mediaSourcePicker = mediaSourcePicker,
                    onDestinationChange = { selected -> destination = selected },
                    onExitClick = actions.onBack,
                    onUpClick = onBackClick,
                )
            } else {
                SettingsCompactContent(
                    destination = destination,
                    state = state,
                    actions = actions,
                    mediaSourcePicker = mediaSourcePicker,
                    onDestinationChange = { selected -> destination = selected },
                    onBackClick = onBackClick,
                )
            }
        }
    }
}

@Composable
private fun SettingsCompactContent(
    destination: SettingsDestination?,
    state: SettingsUiState,
    actions: SettingsActions,
    mediaSourcePicker: @Composable (
        onConfirm: (id: String, title: String) -> Unit,
        onDismiss: () -> Unit,
    ) -> Unit,
    onDestinationChange: (SettingsDestination) -> Unit,
    onBackClick: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .padding(vertical = 16.dp)
                .fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (destination == null) {
            SettingsBackButton(
                onBackClick = onBackClick,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        } else {
            SettingsDetailHeaderRow(
                destination = destination,
                onBackClick = onBackClick,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
            )
            HorizontalDivider()
        }
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SettingsDestinationContent(
                destination = destination,
                state = state,
                actions = actions,
                mediaSourcePicker = mediaSourcePicker,
                onDestinationChange = onDestinationChange,
            )
        }
    }
}

@Composable
private fun SettingsWideContent(
    destination: SettingsDestination,
    state: SettingsUiState,
    actions: SettingsActions,
    mediaSourcePicker: @Composable (
        onConfirm: (id: String, title: String) -> Unit,
        onDismiss: () -> Unit,
    ) -> Unit,
    onDestinationChange: (SettingsDestination) -> Unit,
    onExitClick: () -> Unit,
    onUpClick: () -> Unit,
) {
    Row(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier =
                Modifier
                    .width(SettingsLayout.navigationPaneWidth)
                    .testTag("settings_navigation_pane")
                    .padding(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SettingsBackButton(
                onBackClick = onExitClick,
                modifier = Modifier.padding(horizontal = 4.dp),
                testTag = "settings_exit",
                contentDescription = "Exit settings",
            )
            HorizontalDivider()
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SettingsTopLevelContent(
                    selectedDestination = destination,
                    showChevron = false,
                    exposeSelection = true,
                    onOpen = { selected -> onDestinationChange(selected) },
                )
            }
        }
        VerticalDivider()
        SettingsDetailPane(
            destination = destination,
            state = state,
            actions = actions,
            mediaSourcePicker = mediaSourcePicker,
            onDestinationChange = onDestinationChange,
            onBackClick = onUpClick,
            modifier =
                Modifier
                    .weight(1f)
                    .widthIn(max = SettingsLayout.detailContentMaxWidth)
                    .testTag("settings_detail_pane"),
        )
    }
}

@Composable
private fun SettingsDetailPane(
    destination: SettingsDestination,
    state: SettingsUiState,
    actions: SettingsActions,
    mediaSourcePicker: @Composable (
        onConfirm: (id: String, title: String) -> Unit,
        onDismiss: () -> Unit,
    ) -> Unit,
    onDestinationChange: (SettingsDestination) -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .padding(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (destination is SettingsDestination.Child) {
            SettingsDetailHeaderRow(
                destination = destination,
                onBackClick = onBackClick,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
            )
        } else {
            SettingsDetailHeader(
                title = destination.title,
                summary = destination.summary(),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
            )
        }
        HorizontalDivider()
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SettingsDestinationContent(
                destination = destination,
                state = state,
                actions = actions,
                mediaSourcePicker = mediaSourcePicker,
                onDestinationChange = onDestinationChange,
            )
        }
    }
}

@Composable
private fun SettingsDetailHeaderRow(
    destination: SettingsDestination,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.Top,
    ) {
        SettingsBackButton(onBackClick = onBackClick)
        SettingsDetailHeader(
            title = destination.title,
            summary = destination.summary(),
            modifier =
                Modifier
                    .weight(1f)
                    .padding(end = 16.dp),
        )
    }
}

@Composable
private fun SettingsBackButton(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String = "settings_back",
    contentDescription: String = "Back",
) {
    IconButton(
        onClick = onBackClick,
        modifier =
            modifier
                .testTag(testTag),
    ) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = contentDescription)
    }
}

@Composable
private fun SettingsDestinationContent(
    destination: SettingsDestination?,
    state: SettingsUiState,
    actions: SettingsActions,
    mediaSourcePicker: @Composable (
        onConfirm: (id: String, title: String) -> Unit,
        onDismiss: () -> Unit,
    ) -> Unit,
    onDestinationChange: (SettingsDestination) -> Unit,
) {
    when (destination) {
        null -> {
            SettingsTopLevelContent(
                selectedDestination = null,
                showChevron = true,
                exposeSelection = false,
                onOpen = { selected -> onDestinationChange(selected) },
            )
        }
        SettingsDestination.TopLevel.HomeAssistant -> {
            ConnectionSettingsSection(
                state = state.connection,
                actions = actions.connection,
            )
        }
        SettingsDestination.TopLevel.Kiosk -> {
            DeviceSettingsSection(
                state = state.device,
                actions = actions.device,
            )
        }
        SettingsDestination.TopLevel.Sidebar -> {
            SidebarSettingsSection(
                state = state.sidebar,
                actions = actions.sidebar,
            )
        }
        SettingsDestination.TopLevel.Voice -> {
            VoiceSettingsSection(
                state = state.voice,
                actions = actions.voice,
                onOpenSpeechPipeline = {
                    onDestinationChange(SettingsDestination.Child.VoiceSpeechPipeline)
                },
                onOpenLocalModels = {
                    onDestinationChange(SettingsDestination.Child.VoiceLocalModels)
                },
                onOpenCommandRecording = {
                    onDestinationChange(SettingsDestination.Child.VoiceCommandRecording)
                },
                onOpenAdvancedTuning = {
                    onDestinationChange(SettingsDestination.Child.VoiceAdvancedTuning)
                },
            )
        }
        SettingsDestination.TopLevel.Screensaver -> {
            ScreensaverSettingsSection(
                state = state.screensaver,
                haEntities = state.haEntities,
                actions = actions.screensaver,
                mediaSourcePicker = mediaSourcePicker,
                onOpenImmichSettings = {
                    onDestinationChange(SettingsDestination.Child.ImmichPhotos)
                },
            )
        }
        SettingsDestination.TopLevel.Doorbell -> {
            DoorbellSettingsSection(
                state = state.doorbell,
                haEntities = state.haEntities,
                actions = actions.doorbell,
            )
        }
        SettingsDestination.TopLevel.Esphome -> {
            EsphomeSettingsSection(
                state = state.esphome,
                actions = actions.esphome,
            )
        }
        SettingsDestination.TopLevel.Admin -> {
            AdminSettingsSection(
                actions = actions.admin,
            )
        }
        SettingsDestination.Child.VoiceSpeechPipeline -> {
            VoiceSpeechPipelineSettingsSection(
                state = state.voice,
                actions = actions.voice,
            )
        }
        SettingsDestination.Child.VoiceLocalModels -> {
            VoiceLocalModelsSettingsSection(
                state = state.voice,
                actions = actions.voice,
            )
        }
        SettingsDestination.Child.VoiceCommandRecording -> {
            VoiceCommandRecordingSettingsSection(
                state = state.voice,
                actions = actions.voice,
            )
        }
        SettingsDestination.Child.VoiceAdvancedTuning -> {
            VoiceAdvancedTuningSettingsSection(
                state = state.voice,
                actions = actions.voice,
            )
        }
        SettingsDestination.Child.ImmichPhotos -> {
            ImmichSettingsSection(
                state = state.immich,
                actions = actions.immich,
            )
        }
    }
}

@Composable
private fun SettingsTopLevelContent(
    selectedDestination: SettingsDestination?,
    showChevron: Boolean,
    exposeSelection: Boolean,
    onOpen: (SettingsDestination.TopLevel) -> Unit,
) {
    val selectedTopLevel = SettingsLayout.selectedTopLevel(selectedDestination)

    for (destination in SettingsDestination.topLevel) {
        SettingsNavigationRow(
            label = destination.title,
            supportingText = destination.summary,
            icon = destination.icon(),
            selected = destination == selectedTopLevel,
            exposeSelection = exposeSelection,
            showChevron = showChevron,
            onClick = { onOpen(destination) },
            testTag = SettingsLayout.navigationTestTag(destination),
        )
    }
}
