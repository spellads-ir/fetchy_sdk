# راهنمای اتصال SDK فچی (Fetchy) به پروژه اندروید

این راهنما مراحل اضافه کردن و راه‌اندازی SDK فچی را در یک پروژه اندرویدی با Kotlin و Gradle KTS توضیح می‌دهد.

## ۱. اضافه کردن مخزن JitPack

در وضعیت فعلی مصرف این SDK، وابستگی از طریق JitPack دریافت می‌شود. بنابراین باید مخزن زیر را به `settings.gradle.kts` پروژه اضافه کنید:

```kotlin
dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
  repositories {
    google()
    mavenCentral()
    maven { url = uri("https://jitpack.io") }
  }
}
```

## ۲. اضافه کردن وابستگی

در فایل `build.gradle.kts` ماژول اپلیکیشن، وابستگی SDK را اضافه کنید:

```kotlin
dependencies {
  implementation("com.github.spellads-ir:fetchy_sdk:5da42ee")
}
```

می‌توانید به جای شناسه کامیت، از tag یا نسخه‌ای که برای انتشار روی JitPack در دسترس است هم استفاده کنید.

## ۳. ایجاد فایل پیکربندی

SDK در زمان راه‌اندازی فایل `fetchy-config.json` را از پوشه `assets` می‌خواند. این فایل باید در مسیر `app/src/main/assets/fetchy-config.json` قرار بگیرد.

نمونه پیکربندی:

```json
{
  "environment": "production",
  "base_url": "https://api.spellads.ir",
  "api_key": "YOUR_API_KEY_HERE",
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

نکات مهم:

1. مقدار `base_url` اجباری است.
2. مقدار `api_key` اجباری است و می‌تواند در ریشه فایل یا در `pull.api_key` قرار بگیرد.
3. اگر پوشه `assets` وجود ندارد، آن را در مسیر `app/src/main/assets/` بسازید.
4. فایل نمونه آماده نیز در همین مخزن با نام `fetchy-config.sample.json` قرار دارد.

## ۴. مقداردهی اولیه SDK

برای شروع کار SDK کافی است یک `Context` به متد `Fetchy.initialize(...)` بدهید. بهترین محل برای این کار کلاس `Application` است تا SDK فقط یک بار هنگام بالا آمدن برنامه راه‌اندازی شود.

```kotlin
import android.app.Application
import com.fetchy.sdk.Fetchy

class App : Application() {
  override fun onCreate() {
    super.onCreate()
    Fetchy.initialize(this)
  }
}
```

در صورت نیاز می‌توانید این متد را از اولین `Activity` هم صدا بزنید، اما استفاده از `Application` پایدارتر است.

## ۵. دسترسی‌ها و نوتیفیکیشن

کتابخانه در مانیفست خود این permissionها را اعلام می‌کند:

- `INTERNET`
- `ACCESS_NETWORK_STATE`
- `POST_NOTIFICATIONS`

نکته مهم: اضافه شدن `POST_NOTIFICATIONS` به مانیفست به معنای دریافت خودکار مجوز از کاربر نیست. در اندروید ۱۳ و بالاتر، اپلیکیشن شما همچنان باید این مجوز را در زمان اجرا درخواست کند.

در صورت نیاز می‌توانید وضعیت این مجوز را هم از طریق APIهای زیر بخوانید:

```kotlin
Fetchy.getNotificationPermissionStatus(context)
Fetchy.syncNotificationPermissionStatus(context)
```
