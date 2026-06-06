راهنمای اتصال SDK فچی (Fetchy) به پروژه اندروید
این مستند مراحل لازم برای اضافه کردن و راه‌اندازی SDK فچی را در یک پروژه اندرویدی (با استفاده از Kotlin و Gradle KTS) توضیح می‌دهد.

۱. اضافه کردن مخزن JitPack از آنجایی که این کتابخانه در JitPack میزبانی می‌شود، باید آدرس آن را به تنظیمات پروژه اضافه کنید.
در فایل settings.gradle.kts (یا فایل build.gradle پروژه):

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // اضافه کردن این خط
        maven { url = uri("https://jitpack.io") }
    }
}
۲. اضافه کردن وابستگی (Dependency) در فایل build.gradle.kts مربوط به ماژول اپلیکیشن (معمولاً در مسیر app/build.gradle.kts)، خط زیر را به بخش dependencies اضافه کنید:
dependencies {
    // استفاده از آخرین نسخه یا شناسه کامیت
    implementation("com.github.spellads-ir:fetchy_sdk:5da42ee")
}
۳. ایجاد فایل پیکربندی این SDK برای کارکرد صحیح به یک فایل تنظیمات با نام fetchy-config.json در پوشه assets نیاز دارد.
۱. اگر پوشه assets وجود ندارد، آن را در مسیر app/src/main/assets/ بسازید. ۲. فایلی با نام fetchy-config.json ایجاد کرده و محتوای زیر را در آن قرار دهید:

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
نکته: مقدار `api_key` را با کلیدی که از پنل دریافت کرده‌اید جایگزین کنید.

۴. مقداردهی اولیه (Initialization) بهترین مکان برای راه‌اندازی SDK، کلاس Application یا اولین Activity برنامه است.
در MainActivity.kt:

import com.fetchy.sdk.Fetchy

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // مقداردهی اولیه با استفاده از Context
        Fetchy.initialize(this)
    }
}
۵. دسترسی‌ها (Permissions) کتابخانه به صورت خودکار دسترسی‌های لازم (INTERNET, ACCESS_NETWORK_STATE, POST_NOTIFICATIONS) را به مانیفست شما اضافه می‌کند.
توجه: در اندروید ۱۳ (API 33) و بالاتر، برای نمایش نوتیفیکیشن‌ها باید در زمان اجرا (Runtime) مجوز POST_NOTIFICATIONS را از کاربر درخواست کنید.
