# Uncommitted changes

Date: 2026-08-19  
Branch: `main`  
Version: **1.3.8 → 1.4.0**

This changelog covers **all current uncommitted work**: FCM inside the SDK, 1-minute foreground polling, source-agnostic 48-hour dedup, and related README/config updates.

---

## Summary

The SDK is the hybrid delivery layer:

- Pull remains durable (foreground 1 min, background WorkManager ≥ 15 min).
- FCM is acceleration (data-only payloads).
- Push and pull of the same campaign share one local id; the notification tray uses a stable notify id so a later pull does not double-post.

---

## Added

- `FetchyFirebaseMessagingService` (`MESSAGING_EVENT`) — default FCM entry
- Public `Fetchy.onNewToken` / `Fetchy.handleRemoteMessage` for host apps that already own `FirebaseMessagingService`
- `FetchyFcmPayloadParser` — maps FCM `data` to `FetchyNotificationPayload`
- `FetchyForegroundPoller` — `ProcessLifecycleOwner`, 60s ticker while the app is in foreground
- `fcm_token` on `/tokens/register` (included in register fingerprint)
- Room DB version **5 → 6**; `deleteOlderThan` 48h purge on sync
- Unit tests: FCM parser, pull/push same `dedupeKey()`, recurring `run_id` differs, register body includes `fcm_token`

---

## Changed

- `firebase-messaging:24.1.1`, `lifecycle-process`, `lifecycle-runtime-ktx`
- Config: `push.enabled` from server; `backgroundPollIntervalMinutes` clamped to WorkManager minimum 15 (foreground interval is **not** forced to 15)
- `dedupeKey()` is source-agnostic:
  - exclusive: `exclusive:{id}`
  - one-shot broadcast: `broadcast:{id}`
  - recurring: `broadcast:{id}:{run_id}`
- Tray notify id = `dedupeKey().hashCode()` (not `localId.hashCode()`)
- README: data-only FCM, host service forwarding, recurring vs no-FCM

---

## Test scenarios

### A. Unit tests (JVM)

From the SDK repo:

```bash
./gradlew :fetchy-sdk:test
```

Confirm:

| Test | Expectation |
| --- | --- |
| `FetchyNotificationPayloadTest` | Pull and push of the same exclusive / one-shot broadcast share a key; recurring keys differ by `run_id` |
| `FetchyFcmPayloadParserTest` | Data map → payload (title, body, urls, buttons, `notification_id`, `scope`, `run_id`) |
| `FetchyApiClientTest.registerToken_includesFcmToken` | Register JSON includes `fcm_token` |

### B. Host app / device (manual or instrumentation)

Host app must apply Google Services plugin, ship `google-services.json` from pull-notif SDK-config, and request `POST_NOTIFICATIONS` (API 33+).

1. **Init**  
   `Fetchy.init` with a valid API key. Token register succeeds; logs or network inspector show `fcm_token` when Play Services are present.

2. **Foreground poll**  
   App in foreground: `/feed` about every **60 seconds**. App to background: ticker stops; WorkManager continues at ≥ 15 minutes.

3. **No FCM / Play Services missing**  
   Device still registers (token may be empty). Immediate `delivery_mode=push` broadcast still appears from pull. No crash.

4. **Dedup: push then pull**  
   Immediate push campaign. FCM tray appears. Next `/feed` of the same `broadcast:{id}` does **not** create a second tray notification.

5. **Dedup: pull then push**  
   Delay FCM (or airplane mode then enable). Pull shows the tray first; later FCM with the same key updates / does not stack a duplicate.

6. **48h store**  
   After 48 hours, Room purge allows the same campaign to show again if it is still on `/feed`. Within 48h, re-sync is a no-op for that key.

7. **Exclusive**  
   Exclusive push: FCM + Redis/feed once; second poll empty. Other devices never show it.

8. **Recurring**  
   Same campaign, new `run_id` on FCM: new tray item. No-FCM device: feed still shows the campaign once (no extra pull occurrences).

9. **Host FCM service**  
   If the host already has `FirebaseMessagingService`, it must call `Fetchy.onNewToken` / `Fetchy.handleRemoteMessage`. Confirm both default SDK service and host-forward path.

10. **Channel**  
    Data-only FCM uses `channel_id` from payload / SDK config (`fetchy_notification_channel` default). No system “default FCM notification” duplicate (server must not send a top-level `notification` block).

### C. Paired with pull-notif

Walk through pull-notif `CHANGES.md` scenarios 5–12 with this SDK installed instead of `test/sdkmock`.

---

## What is needed to run the tests

### Unit tests

| Need | Why |
| --- | --- |
| **JDK 11+** with `JAVA_HOME` set | Gradle JVM tests |
| **Android SDK** (`ANDROID_HOME` / cmdline tools) | Android library module compile |
| Network on first Gradle sync | Download `firebase-messaging` and other deps |
| Executable `./gradlew` | Wrapper (chmod +x if needed) |

This environment previously had **no Java**, so `:fetchy-sdk:test` could not be run here.

### Device / hybrid tests

| Need | Why |
| --- | --- |
| Android Studio or `adb` + emulator/device | FCM + notifications |
| Host app with `google-services.json` matching the Firebase Android app | Token minting |
| pull-notif server running, app provisioned, API key | Register + feed + dispatcher |
| `POST_NOTIFICATIONS` granted | Visible tray |
| Optional: second device **without** FCM | Prove pull-only hybrid |
| Firebase project used by pull-notif (`FIREBASE_*`) | Real data-only FCM |

### Cannot run from this agent without you

- Install/set `JAVA_HOME` and Android SDK on this machine, **or**
- Run `./gradlew :fetchy-sdk:test` locally and paste the output
- Provide an emulator/device for scenarios B–C
