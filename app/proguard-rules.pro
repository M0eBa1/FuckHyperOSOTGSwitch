-keep class cn.moebai.fuckhyperosotgswitch.OtgKeeperHook { *; }
-keep class cn.moebai.fuckhyperosotgswitch.OtgKeeperHook$* { *; }
-keepclassmembers class * implements io.github.libxposed.api.XposedInterface$Hooker {
    *;
}
-keepattributes *Annotation*
