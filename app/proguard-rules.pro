# Kod karıştırma açık; yalnızca gerekli uyarılar susturulur.
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn okhttp3.internal.platform.**
# Günlük (log) çağrıları yayın sürümünden tümüyle çıkarılır
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
}
