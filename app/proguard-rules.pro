# ProGuard rules for Cross with Friends Android App
-keepattributes JavascriptInterface
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
-keepclassmembers class com.crosswithfriends.app.WebAppInterface {
    <methods>;
}
