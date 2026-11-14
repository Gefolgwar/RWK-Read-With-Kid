# JNA: native code looks up classes, fields and methods by name, and Structure/Callback
# subclasses are inspected reflectively. Neither JNA nor vosk-android ships consumer rules.
-keep class com.sun.jna.** { *; }
-keep class * implements com.sun.jna.** { *; }
-keepclassmembers class * extends com.sun.jna.** { *; }
# Desktop-only JNA code paths, absent on Android
-dontwarn java.awt.**

# Vosk: LibVosk binds its native methods by name via Native.register, and Model/Recognizer
# are JNA PointerType subclasses created reflectively.
-keep class org.vosk.** { *; }
