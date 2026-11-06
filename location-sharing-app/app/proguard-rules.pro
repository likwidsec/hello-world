# Keep Firebase Realtime Database model classes so field names survive shrinking.
-keepclassmembers class com.example.locationsharing.** {
    <init>();
    <fields>;
}
