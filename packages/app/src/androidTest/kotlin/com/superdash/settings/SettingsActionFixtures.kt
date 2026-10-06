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
                onAllowBackgroundLaunch = {},
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
        feed =
            FeedSettingsActions(
                onFeedEnabledChange = {},
                onUpsertFeed = {},
                onRemoveFeed = {},
                onTestFeed = {},
            ),
        camera =
            CameraSettingsActions(
                onRequestCameraEnable = {},
                onCameraDisable = {},
                onFacingChange = {},
                onResolutionChange = {},
                onMotionModeChange = {},
                onMotionSensitivityChange = {},
                onMaxFpsChange = {},
                onWakeOnMotionChange = {},
                onAllowRemoteEnableChange = {},
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
                onEdgeHandleChange = {},
                onShortcutsChange = {},
            ),
        admin =
            AdminSettingsActions(
                onBatteryHelp = {},
                onOpenWsDebug = {},
            ),
        onBack = {},
    )
