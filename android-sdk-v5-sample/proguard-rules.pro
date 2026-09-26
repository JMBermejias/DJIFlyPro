# Gson-backed mission and recipe files are an interchange format. Keep the
# serialized model names stable across debug and release builds.
-keepattributes Signature
-keepattributes *Annotation*

-keep class dji.sampleV5.aircraft.pro.mission.MissionPlan { *; }
-keep class dji.sampleV5.aircraft.pro.mission.MissionRequest { *; }
-keep class dji.sampleV5.aircraft.pro.mission.GeoPoint { *; }
-keep class dji.sampleV5.aircraft.pro.mission.PlannedWaypoint { *; }
-keep enum dji.sampleV5.aircraft.pro.mission.MissionTemplate { *; }
-keep enum dji.sampleV5.aircraft.pro.mission.RoutePattern { *; }
-keep enum dji.sampleV5.aircraft.pro.mission.FinishAction { *; }

-keep class dji.sampleV5.aircraft.pro.algorithm.AlgorithmRecipe { *; }
-keep class dji.sampleV5.aircraft.pro.algorithm.AlgorithmDefaults { *; }

# Build-time allowlist entries are serialized from the packaged JSON asset.
-keep class dji.sampleV5.aircraft.pro.mission.ValidatedWpmlProfile { *; }
-keep class dji.sampleV5.aircraft.pro.mission.ValidatedWpmlProfileFile { *; }

# Optional transitive references from third-party libraries. They are not part
# of any code path DJIFlyPro uses, so R8 only reports them as missing.
-dontwarn org.dom4j.**
-dontwarn org.jaxen.**
-dontwarn org.gjt.xpp.**
-dontwarn org.relaxng.datatype.**
-dontwarn com.sun.msv.datatype.**
-dontwarn java.beans.**
-dontwarn javax.swing.**
-dontwarn javax.xml.bind.**
-dontwarn javax.xml.stream.**
-dontwarn dji.v5.manager.aircraft.megaphone.MegaphoneInfo$Builder
-dontwarn com.mikepenz.iconics.dsl.ExperimentalIconicsDSL
