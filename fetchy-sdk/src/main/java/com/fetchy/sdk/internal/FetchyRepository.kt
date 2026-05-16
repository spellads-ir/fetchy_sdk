package com.fetchy.sdk.internal

import android.content.Context
import android.os.Build
import com.fetchy.sdk.BuildConfig
import com.fetchy.sdk.FetchyNotificationPermissionStatus
import com.fetchy.sdk.internal.data.SpAckRecordEntity
import com.fetchy.sdk.internal.data.SpNotificationEntity
import com.fetchy.sdk.internal.data.SpStateEntity
import com.fetchy.sdk.internal.data.FetchyDatabase
import com.fetchy.sdk.internal.model.RegisterTokenRequest
import com.fetchy.sdk.internal.model.FetchyNotificationPayload

internal class FetchyRepository(private val context: Context) {
    private val database = FetchyDatabase.get(context)

    suspend fun persistConfig(rawJson: String) {
        upsertState(FetchyConstants.stateConfigJson, rawJson)
    }

    suspend fun getConfig(): FetchyConfig? {
        val json = database.stateDao().getValue(FetchyConstants.stateConfigJson) ?: return null
        return FetchyConfigLoader.fromJson(json)
    }

    suspend fun saveBackendToken(token: String) {
        upsertState(FetchyConstants.stateBackendToken, token)
    }

    suspend fun getBackendToken(): String? = database.stateDao().getValue(FetchyConstants.stateBackendToken)

    suspend fun saveClientType(clientType: String) {
        upsertState(FetchyConstants.stateClientType, clientType)
    }

    suspend fun getClientType(): String {
        return database.stateDao().getValue(FetchyConstants.stateClientType)
            ?: "android_native"
    }

    suspend fun saveNotificationPermissionStatus(
        status: FetchyNotificationPermissionStatus,
        updatedAtEpochMs: Long
    ): Boolean {
        val normalizedStatus = status.name
        val currentStatus = database.stateDao().getValue(FetchyConstants.stateNotificationPermissionStatus)
        if (currentStatus == normalizedStatus) {
            return false
        }
        upsertState(FetchyConstants.stateNotificationPermissionStatus, normalizedStatus)
        upsertState(FetchyConstants.stateNotificationPermissionUpdatedAt, updatedAtEpochMs.toString())
        return true
    }

    suspend fun getNotificationPermissionStatus(): FetchyNotificationPermissionStatus? {
        val rawStatus = database.stateDao().getValue(FetchyConstants.stateNotificationPermissionStatus) ?: return null
        return FetchyNotificationPermissionStatus.entries.firstOrNull { it.name == rawStatus }
    }

    suspend fun getRegisterFingerprint(): String? = database.stateDao().getValue(FetchyConstants.stateRegisterFingerprint)

    suspend fun saveRegisterFingerprint(fingerprint: String) {
        upsertState(FetchyConstants.stateRegisterFingerprint, fingerprint)
    }

    suspend fun getLastRetrieve(): Long {
        return database.stateDao().getValue(FetchyConstants.stateLastRetrieve)?.toLongOrNull() ?: 0L
    }

    suspend fun saveLastRetrieve(lastRetrieve: Long) {
        upsertState(FetchyConstants.stateLastRetrieve, lastRetrieve.toString())
    }

    suspend fun persistNotification(payload: FetchyNotificationPayload, receivedAtEpochMs: Long): SpNotificationEntity? {
        val entity = SpNotificationEntity(
            dedupeKey = payload.dedupeKey(),
            source = payload.source.name,
            scope = payload.scope.name,
            fetchyId = payload.fetchyId,
            schemaVersion = payload.schemaVersion,
            remoteNotificationId = payload.remoteNotificationId,
            orgId = payload.orgId,
            appId = payload.appId,
            title = payload.title,
            body = payload.body,
            badgeUrl = payload.badgeUrl,
            imageUrl = payload.imageUrl,
            linkUrl = payload.linkUrl,
            actionButtonsJson = FetchyJson.encodeActionButtonList(payload.actionButtons),
            clickAckSignature = payload.clickAckSignature,
            createdAtEpochMs = payload.createdAtEpochMs,
            receivedAtEpochMs = receivedAtEpochMs,
            displayedAtEpochMs = null,
            openedAtEpochMs = null
        )
        val insertId = database.notificationDao().insert(entity)
        return if (insertId == -1L) null else database.notificationDao().getById(insertId)
    }

    suspend fun getNotification(localId: Long): SpNotificationEntity? = database.notificationDao().getById(localId)

    suspend fun getNotificationByDedupeKey(dedupeKey: String): SpNotificationEntity? =
        database.notificationDao().getByDedupeKey(dedupeKey)

    suspend fun markNotificationDisplayed(localId: Long, displayedAtEpochMs: Long) {
        database.notificationDao().markDisplayed(localId, displayedAtEpochMs)
    }

    suspend fun markNotificationOpened(localId: Long, openedAtEpochMs: Long) {
        database.notificationDao().markOpened(localId, openedAtEpochMs)
    }

    suspend fun reserveAck(ackKey: String, ackType: String): Boolean {
        return database.ackDao().insert(
            SpAckRecordEntity(
                ackKey = ackKey,
                ackType = ackType,
                createdAt = System.currentTimeMillis()
            )
        ) != -1L
    }

    suspend fun buildRegisterRequest(config: FetchyConfig): RegisterTokenRequest {
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        val appVersion = packageInfo.versionName ?: packageInfo.longVersionCode.toString()
        return RegisterTokenRequest(
            appApiKey = config.pull.effectiveApiKey ?: config.apiKey,
            existingToken = null,
            clientType = getClientType().take(50),
            deviceBrand = Build.BRAND.take(100),
            deviceModel = Build.MODEL.take(100),
            androidVersion = Build.VERSION.RELEASE.orEmpty().take(50),
            androidApiLevel = Build.VERSION.SDK_INT,
            appVersion = appVersion.take(50),
            sdkVersion = BuildConfig.FETCHY_SDK_VERSION.take(50)
        )
    }

    private suspend fun upsertState(key: String, value: String) {
        database.stateDao().upsert(
            SpStateEntity(
                key = key,
                value = value,
                updatedAt = System.currentTimeMillis()
            )
        )
    }
}

internal object FetchyRepositoryProvider {
    @Volatile
    private var instance: FetchyRepository? = null

    fun get(context: Context): FetchyRepository {
        return instance ?: synchronized(this) {
            instance ?: FetchyRepository(context.applicationContext).also { instance = it }
        }
    }
}
