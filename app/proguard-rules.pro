# Add project specific ProGuard rules here.
# Keep the JavaScript interface bridge so methods called from WebView JS are not stripped.
-keepclassmembers class com.example.webscraper.ui.main.WebViewJsInterface {
    @android.webkit.JavascriptInterface <methods>;
}
