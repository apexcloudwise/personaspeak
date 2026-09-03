# PersonaSpeak-owned R8 rules for the second-host release build.
#
# snakeyaml (vendored through :core-personas for persona YAML parsing)
# references java.beans.* on JVM hosts; none of those classes exist on
# Android and none of the referencing code paths run there. AGP's
# generated missing_rules.txt (release dry-run, 2026-09-03) proposed
# exactly these suppressions; they live here so the upstream
# proguard-rules.pro stays byte-untouched (rent discipline,
# UPSTREAM-MODIFIED.md).
-dontwarn java.beans.BeanInfo
-dontwarn java.beans.FeatureDescriptor
-dontwarn java.beans.IntrospectionException
-dontwarn java.beans.Introspector
-dontwarn java.beans.PropertyDescriptor
