# Add project specific ProGuard rules here.

# From https://firebase.google.com/docs/crashlytics/android/get-deobfuscated-reports
-keepattributes SourceFile,LineNumberTable        # Keep file names and line numbers.
-keep public class * extends java.lang.Exception  # Optional: Keep custom exceptions.
