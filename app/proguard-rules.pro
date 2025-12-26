# Gson ProGuard rules - 增强版
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes EnclosingMethod
-keepattributes InnerClasses

# Keep Gson internals
-keep class sun.misc.Unsafe { *; }
-keep class com.google.gson.stream.** { *; }
-keep class com.google.gson.** { *; }

# Preserve HanApp data models
-keep class com.hanapp.data.model.** { *; }

# Keep generic signature for TypeToken and prevent obfuscation of anonymous classes
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken
-keepclassmembers class * extends com.google.gson.reflect.TypeToken { *; }

# Prevent obfuscation of classes using TypeToken
-keep class com.hanapp.data.Converters { *; }
-keep class com.hanapp.viewmodel.** { *; }
-keep class com.hanapp.MainActivity { *; }

# Keep all fields and methods that use Gson
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# Prevent stripping of generic signatures
-keepattributes Signature

# Keep all UI components and helpers (fix for stroke animation and drawing board)
-keep class com.hanapp.ui.components.** { *; }

# Keep HanziData for Gson deserialization (fix for character template display)
-keep class com.hanapp.ui.components.HanziData { *; }
-keepclassmembers class com.hanapp.ui.components.HanziData { *; }

# Keep Compose functions (fix for button click handlers)
-keep @androidx.compose.runtime.Composable class * { *; }
-keep @androidx.compose.runtime.Stable class * { *; }
