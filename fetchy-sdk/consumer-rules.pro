-keep class com.fetchy.sdk.** { *; }
-keepclassmembers class ** {
    @androidx.room.* <fields>;
    @androidx.room.* <methods>;
}
-keep class androidx.work.impl.background.systemjob.SystemJobService
