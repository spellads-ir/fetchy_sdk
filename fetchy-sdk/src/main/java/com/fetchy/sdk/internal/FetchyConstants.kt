package com.fetchy.sdk.internal

internal object FetchyConstants {
    const val configAssetName = "fetchy-config.json"
    const val periodicPullIntervalMinutes = 15L
    const val uniquePeriodicWorkName = "pn_notif_worker"
    const val uniqueSyncWorkName = "pn_notif_sync"
    const val databaseName = "pn_fetchy.db"

    const val stateConfigJson = "pn_config_json"
    const val stateBackendToken = "pn_backend_token"
    const val stateClientType = "pn_client_type"
    const val stateLastRetrieve = "pn_last_retrieve"
    const val stateRegisterFingerprint = "pn_register_fingerprint"
    const val stateNotificationPermissionStatus = "pn_notification_permission_status"
    const val stateNotificationPermissionUpdatedAt = "pn_notification_permission_updated_at"

    const val intentExtraLocalNotificationId = "pn_extra_local_notification_id"
    const val intentExtraActionUrl = "pn_extra_action_url"

    const val ackTypeLinkClick = "pn_link_click"
}
