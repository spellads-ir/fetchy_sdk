# Fetchy Kotlin SDK

Fetchy is a pull-only Android SDK for backend-driven notifications.

## Exported Public API

Package: `com.fetchy.sdk`

### `Fetchy`

1. `Fetchy.initialize(context: Context)`
- Initializes the SDK with default client type `ANDROID_NATIVE`.
- Loads `fetchy-config.json` from app assets.
- Schedules startup and periodic pull sync via `WorkManager`.

2. `Fetchy.initialize(context: Context, clientType: FetchyClientType)`
- Initializes the SDK with an explicit client type.
- Intended for wrappers/bridges such as Flutter plugin integrations.

3. `Fetchy.getNotificationPermissionStatus(context: Context): FetchyNotificationPermissionStatus`
- Returns current notification permission state.

4. `Fetchy.syncNotificationPermissionStatus(context: Context): FetchyNotificationPermissionStatus`
- Re-checks and persists permission state.

### `FetchyClientType`

- `ANDROID_NATIVE` -> `android_native`
- `FLUTTER_ANDROID` -> `flutter_android`

This value is included in `/tokens/register` payload as `client_type`.

### `FetchyNotificationPermissionStatus`

- `GRANTED`
- `DENIED`
- `UNKNOWN`

## SDK Contract Summary

1. Registers or refreshes backend token through `/tokens/register`.
2. Polls `/feed` on startup and periodically.
3. Stores notifications locally and displays them through Android notification stack.
4. Sends direct click ack only for internal app links that the SDK opens inside the host device.

The SDK does not expose push callback APIs and does not require Firebase/FCM.

## Required App Asset

Create `fetchy-config.json` in host app assets:

```json
{
  "environment": "production",
  "base_url": "https://your-api.example.com",
  "api_key": "your_fetchy_app_api_key",
  "pull": {
    "enabled": true,
    "worker_enabled": true,
    "poll_interval_minutes": 15
  },
  "notification": {
    "channel_id": "pn_notification_channel",
    "channel_name": "Fetchy Notifications",
    "channel_description": "Notifications delivered by the Fetchy SDK."
  }
}
```

## Minimal Integration Example

```kotlin
import android.app.Application
import com.fetchy.sdk.Fetchy

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Fetchy.initialize(applicationContext)
    }
}
```

## Build and Publish

1. Build:

```bash
./gradlew :fetchy-sdk:assembleRelease
```

2. Publish to Maven Local:

```bash
./gradlew :fetchy-sdk:publishToMavenLocal
```
