package com.superdash.settings

internal fun testSettingsActions(): SettingsActions =
    SettingsActions(
        general =
            GeneralSettingsActions(
                onSelectLanguage = {},
            ),
        connection =
            ConnectionSettingsActions(
                onHaUrlChange = {},
                onTestConnection = { true },
                onReauthenticate = {},
                onDashboardPathChange = {},
            ),
        device =
            DeviceSettingsActions(
                onKeepScreenOnChange = {},
                onStartOnBootChange = {},
            ),
        voice =
            VoiceSettingsActions(
                onRequestVoiceEnable = {},
                onVoiceDisable = {},
                onActiveWakeWordChange = {},
                onVoiceAssistProviderChange = {},
                onPrimarySttProviderChange = {},
                onSecondarySttProviderChange = {},
                onSelectedSttModelChange = {},
                onSelectedIntentEmbeddingModelChange = {},
                onLocalIntentRecognizerEnabledChange = {},
                onDownloadVoiceModel = {},
                onDeleteVoiceModel = {},
                onVoiceResponseModeChange = {},
                onCommandRecordingEnabledChange = {},
                onCommandRecordingRetentionChange = {},
                onClearCommandRecordings = {},
                onVadSilenceMsChange = {},
            ),
        doorbell =
            DoorbellSettingsActions(
                onDoorbellEnabledChange = {},
                onDoorbellAutoCloseSecChange = {},
                onUpsertDoorbell = {},
                onRemoveDoorbell = {},
                onTestDoorbell = {},
            ),
        esphome =
            EsphomeSettingsActions(
                onEsphomeEnabledChange = {},
                onSavePskBase64 = { true },
                onClearPsk = {},
            ),
        screensaver =
            ScreensaverSettingsActions(
                onDayScreensaverModeChange = {},
                onNightScreensaverModeChange = {},
                onIdleTimeoutSecChange = {},
                onWeatherEntityIdChange = {},
                onCalendarEntityIdChange = {},
                onPowerUsageEntityIdChange = {},
                onSolarPowerEntityIdChange = {},
                onGridPowerEntityIdChange = {},
                onOverlayPositionChange = {},
                onPictureSpacingDpChange = {},
                onMediaLibrarySourceChange = { _, _ -> },
                onMediaLibraryOrderChange = {},
                onTestScreensaver = {},
            ),
        immich =
            ImmichSettingsActions(
                onImmichUrlChange = {},
                onImmichApiKeyChange = {},
                onImmichAlbumChange = {},
                onImmichCatalogTtlHoursChange = {},
                onRefreshImmichCatalog = { "" },
                onTestImmich = { _, _, _ -> "" },
            ),
        sidebar =
            SidebarSettingsActions(
                onPositionChange = {},
                onPinnedChange = {},
                onShowLabelsChange = {},
                onShortcutsChange = {},
            ),
        admin =
            AdminSettingsActions(
                onBatteryHelp = {},
                onOpenWsDebug = {},
            ),
        onBack = {},
    )
