# Pleos SDK: AIDL·리플렉션으로 서비스와 통신하므로 통째로 보존
-keep class ai.pleos.** { *; }
-keep interface ai.pleos.** { *; }
-dontwarn ai.pleos.**
# AAOS Car API (useLibrary, 기기 제공)
-dontwarn android.car.**
# SDK 내부 Gson이 제네릭 정보(TypeToken)를 쓴다. 지우면 차량 신호를 못 받는다(2026-11-12 실측)
-keepattributes Signature, InnerClasses, EnclosingMethod, *Annotation*
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken
-keep class com.google.gson.** { *; }
-dontwarn com.google.gson.**
