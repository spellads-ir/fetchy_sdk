# راهنمای کامل راه‌اندازی Fetchy SDK در اندروید (Kotlin)

این سند یک راهنمای عمومی و کامل برای نصب و راه‌اندازی `Fetchy SDK` در هر پروژه اندرویدی است.  
مخاطب اصلی: توسعه‌دهنده Junior که می‌خواهد بدون ابهام SDK را از صفر تا تست نهایی بالا بیاورد.

---

## Fetchy SDK چیست؟

`Fetchy SDK` برای ثبت دستگاه، دریافت نوتیفیکیشن و مدیریت Sync استفاده می‌شود.  
پس از اتصال صحیح:

- اپ شما در بک‌اند Fetchy ثبت می‌شود
- توکن دستگاه ساخته می‌شود
- نوتیفیکیشن‌ها قابل دریافت می‌شوند

---

## 1) پیش‌نیازها

قبل از شروع، این موارد باید آماده باشد:

- Android Studio (نسخه پایدار جدید)
- JDK 11
- اینترنت برای دانلود dependency
- دسترسی به API Key معتبر Fetchy
- پروژه Android با `minSdk >= 24`

---

## 2) اضافه کردن ریپازیتوری JitPack

در فایل `settings.gradle.kts` پروژه، JitPack را اضافه کنید:

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

> اگر این مرحله انجام نشود، Gradle نمی‌تواند SDK را resolve کند.

---

## 3) اضافه کردن dependency SDK

در فایل `app/build.gradle.kts`:

```kotlin
dependencies {
    implementation 'com.github.spellads-ir:fetchy_sdk:main-SNAPSHOT'
}
```

سپس Gradle Sync بزنید.

---

## 4) ساخت فایل کانفیگ SDK

Fetchy کانفیگ را از فایل asset می‌خواند.  
این فایل باید دقیقا در مسیر زیر باشد:

`app/src/main/assets/fetchy-config.json`

اگر پوشه `assets` وجود ندارد، بسازید.

نمونه کانفیگ:

```json
{
  "environment": "production",
  "base_url": "https://api.spellads.ir",
  "api_key": "YOUR_API_KEY",
  "pull": {
    "enabled": true,
    "api_key": "YOUR_API_KEY",
    "worker_enabled": true,
    "poll_interval_minutes": 15
  },
  "notification": {
    "channel_id": "pn_notification_channel",
    "channel_name": "Fetchy Notifications",
    "channel_description": "Notifications delivered by Fetchy SDK"
  }
}
```

### نکات مهم کانفیگ

- نام فایل باید دقیقا `fetchy-config.json` باشد.
- `base_url` و `api_key` اجباری هستند.
- `api_key` می‌تواند در ریشه یا در `pull.api_key` باشد.
- اگر `base_url` اشتباه باشد، ثبت توکن انجام نمی‌شود.

---

## 5) تنظیم Permissionها در AndroidManifest

در `app/src/main/AndroidManifest.xml` این permissionها را داشته باشید:

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
```

> در Android 13 به بالا، `POST_NOTIFICATIONS` باید در Runtime هم از کاربر درخواست شود.

---

## 6) مقداردهی اولیه SDK در Application

بهترین روش این است که SDK یک بار در `Application` initialize شود.

### 6.1 ساخت کلاس Application

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

### 6.2 معرفی کلاس در Manifest

```xml
<application
    android:name=".App"
    ... >
</application>
```

---

## 7) درخواست Runtime Permission برای نوتیفیکیشن (Android 13+)

در Activity اول اپ:

```kotlin
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
    val permission = Manifest.permission.POST_NOTIFICATIONS
    if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
        ActivityCompat.requestPermissions(this, arrayOf(permission), 1001)
    }
}
```

اگر کاربر این permission را رد کند، ممکن است نوتیف روی دستگاه نمایش داده نشود.

---

## 8) اجرای پروژه و تست اولیه

1. اپ را اجرا کنید.
2. مطمئن شوید crash روی startup ندارید.
3. از پنل/بک‌اند Fetchy یک نوتیف تستی بفرستید.
4. دریافت نوتیف روی دستگاه را بررسی کنید.

---

## 9) گرفتن توکن Fetchy

بسته به نسخه SDK شما، API گرفتن توکن ممکن است مستقیم در دسترس باشد یا نباشد.

### حالت اول: متد عمومی در SDK وجود دارد

```kotlin
// مثال مفهومی - بسته به نسخه SDK
val token = Fetchy.getToken(context)
```

### حالت دوم: متد عمومی وجود ندارد

در بعضی نسخه‌ها باید با روش fallback (reflection یا خواندن state داخلی) عمل کنید.

پیشنهاد عملی:

- ابتدا 30 تا 60 ثانیه بعد از initialize منتظر بمانید (برای ثبت اولیه)
- سپس مقدار token/state را بخوانید

---

## 10) چک‌لیست سریع نهایی

- [ ] JitPack به `settings.gradle.kts` اضافه شده
- [ ] dependency SDK اضافه و Sync موفق بوده
- [ ] فایل `fetchy-config.json` در مسیر درست قرار دارد
- [ ] `base_url` و `api_key` معتبر هستند
- [ ] `Application` سفارشی ساخته و در Manifest معرفی شده
- [ ] permissionها در Manifest و Runtime تنظیم شده‌اند
- [ ] نوتیف تستی دریافت شده است

---

## 11) خطاهای رایج و راه‌حل

### خطا: `FileNotFoundException: fetchy-config.json`

علت:

- فایل وجود ندارد یا مسیر/نام اشتباه است.

راه‌حل:

- فایل را دقیقا در `app/src/main/assets/fetchy-config.json` قرار دهید.

---

### خطا: اپ بالا می‌آید ولی توکن ثبت نمی‌شود

علت‌های رایج:

- `base_url` اشتباه یا غیرقابل دسترس
- `api_key` اشتباه
- اینترنت دستگاه قطع است

راه‌حل:

- `base_url` و `api_key` را با مقادیر معتبر چک کنید
- با یک شبکه دیگر تست بگیرید
- لاگ خطاهای HTTP را در Logcat بررسی کنید

---

### مشکل: نوتیف می‌رسد ولی توکن در UI نمایش داده نمی‌شود

علت:

- نسخه SDK شما API مستقیم `getToken` ندارد یا کد UI خیلی زود توکن را می‌خواند.

راه‌حل:

- خواندن توکن را با تأخیر/تکرار انجام دهید
- fallback سازگار با نسخه SDK پیاده‌سازی کنید

---

## 12) توصیه‌های Production

- API Key واقعی را داخل ریپازیتوری عمومی commit نکنید
- برای `dev/stage/prod` کانفیگ جدا داشته باشید
- قبل از Release، مسیر endpointها و permission flow را دوباره تست کنید
- لاگ‌های حساس را در نسخه Release خاموش کنید

---

## 13) نمونه ساختار فایل‌ها

```text
app/
  src/
    main/
      java/.../App.kt
      assets/
        fetchy-config.json
      AndroidManifest.xml
```

---

اگر خواستی، در قدم بعدی یک نسخه **Template** هم می‌سازم که فقط با جایگزین کردن `API_KEY` و `BASE_URL` آماده استفاده در پروژه‌های بعدی‌ات باشد.
