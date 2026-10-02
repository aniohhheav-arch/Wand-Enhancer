# Riftverse obfuscation profile: rename, never strip or rewrite.
-dontshrink
-dontoptimize
-dontwarn **
-dontnote **
-ignorewarnings
-useuniqueclassmembernames
-repackageclasses 'dev.riftverse.z'
-allowaccessmodification
-renamesourcefileattribute RV
-keepattributes Signature,InnerClasses,EnclosingMethod,Record,PermittedSubclasses,NestHost,NestMembers,*Annotation*,Exceptions,LineNumberTable,SourceFile

# entry points found by name
-keep @net.neoforged.fml.common.Mod class * { *; }
-keep @net.neoforged.fml.common.EventBusSubscriber class * { *; }
-keepclassmembers class * { @net.neoforged.bus.api.SubscribeEvent *; }
-keep class dev.riftverse.mixin.** { *; }

# enum constants are looked up by name (commands, saves, valueOf)
-keepclassmembers enum * {
    <fields>;
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# records keep their component accessors (codecs and payloads)
-keepclassmembers class * extends java.lang.Record { <fields>; <methods>; }
