# FreeFlow ProGuard Rules

# Keep Bouncy Castle
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**

# Keep FreeFlow protocol classes
-keep class com.freeflow.app.protocol.** { *; }
-keep class com.freeflow.app.crypto.** { *; }
-keep class com.freeflow.app.client.** { *; }
-keep class com.freeflow.app.identity.** { *; }
-keep class com.freeflow.app.data.** { *; }
