# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# ---- ZeroChat ----
# Firestore maps documents to these classes reflectively (field names = document keys).
-keep class com.example.data.model.** { *; }
-keepclassmembers class com.example.data.model.** { <init>(...); }
-keepattributes Signature,*Annotation*,InnerClasses,EnclosingMethod

# SQLCipher uses JNI callbacks into these classes.
-keep class net.zetetic.database.** { *; }
-dontwarn net.zetetic.database.**

# Keep line numbers for crash reports, hide source file names.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
