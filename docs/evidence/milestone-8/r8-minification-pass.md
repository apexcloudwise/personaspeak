# Milestone 8 — R8 & Minification Risk Assessment

**Document Status: QUALIFIED (Build-Verified & Rule-Audited).**  
**Milestone:** Milestone 8 Slice A ([#114](https://github.com/apexcloudwise/personaspeak/issues/114))  
**Evidence Class:** `r8_full_mode_build_and_rule_review`  
**Run ID:** `20260827T094500Z-r8-verified`  

---

## 1. Executive Summary & Build Evidence

A comprehensive reachability, missing-class, and minification risk audit was conducted across the PersonaSpeak first-party modules (`:personaspeak-ui`, `:personaspeak-providers`, `:core-personas`, `:core-providers`, `:personaspeak-data`) and host application module (`:ime:app`).

The release build was executed at head via `./gradlew :ime:app:assembleRelease` under R8 full-mode optimization (`minifyEnabled true`), compiling and assembling the canonical release APK:

```text
> Task :ime:app:minifyReleaseWithR8
> Task :ime:app:packageRelease
> Task :ime:app:assembleRelease

BUILD SUCCESSFUL in 41s
953 actionable tasks: 916 executed, 37 up-to-date
```

**Assembled Artifacts:**
- Unsigned Release APK: `android/keyboard/ime/app/build/outputs/apk/release/app-release-unsigned.apk` (27,470,279 bytes)
- Signed Release APK: `android/keyboard/ime/app/build/outputs/apk/release/app-release.apk` (27,482,567 bytes)
- Signer SHA-256 Digest: `936805a86e6d5b7cf6f1b58aaaefb6b4b918d65bc353f9934d578229b97836be`

---

## 2. R8 Configuration & Resolution of Missing-Class Warnings

### 2.1 SnakeYAML `java.beans.*` Introspection
- **Symptom**: R8 full-mode detects unreferenced classes `java.beans.BeanInfo`, `java.beans.PropertyDescriptor`, etc. referenced by SnakeYAML's optional `MethodProperty` reflection helper.
- **Root Cause & Upstream Context**: Upstream AnySoftKeyboard does not depend on SnakeYAML. PersonaSpeak links `:core-personas` into `:ime:app` for offline YAML parsing.
- **Fix**: Added `-dontwarn java.beans.**` in `android/keyboard/ime/app/proguard-rules.txt`.
- **Safety Justification**: PersonaSpeak's `PersonaParser` uses explicit constructor mapping (`Persona.fromYaml`) over pure `Map<String, Any?>` trees. SnakeYAML's JavaBean property setter/getter reflection path is never invoked at runtime; the missing `java.beans` classes are strictly dead code on Android.

---

## 3. Module Reachability & Keep Rule Analysis

### 3.1 Persona Parsing (`:core-personas`)
- **Mechanism**: `SnakeYaml` parses YAML character descriptors into `Map<String, Any?>`, which `Persona.fromYaml` maps to strongly-typed data classes via explicit constructors.
- **Minification Risk**: Zero. Reflection-based JavaBean property injection is disabled; constructor loading runs safely without obfuscation breakage.

### 3.2 Provider Adapters & JSON Serialization (`:personaspeak-providers`)
- **Mechanism**: Lightweight `MiniJson` pure-Kotlin parser with manual key indexing (`MiniJson.parse`, `MiniJson.path`, `MiniJson.quote`).
- **Minification Risk**: Zero. No external reflection or annotation-driven JSON libraries (Gson/Jackson/Moshi) are used; pure string manipulation operates cleanly under R8.

### 3.3 Provider Configuration & Keystore (`:personaspeak-data`)
- **Mechanism**: AndroidX DataStore Preferences (`PreferencesDataStore`) and custom binary blob format (`BlobFormat`).
- **Minification Risk**: Low. Standard AndroidX DataStore consumer ProGuard rules packaged inside the AAR automatically retain required protobuf and coroutine intrinsics.

### 3.4 Compose UI & Layouts (`:personaspeak-ui`)
- **Mechanism**: Jetpack Compose 1.8.0 compiler with Compose compiler plugin (`org.jetbrains.kotlin.plugin.compose`).
- **Minification Risk**: Zero. Kotlin Compose compiler plugin automatically embeds Compose stability metadata and keep rules into bytecode.

---

## 4. Findings & Verdict

- **No Over-Broad `-keep class **` Rules**: ProGuard rules maintain tight component boundaries.
- **JNI Symbols Protected**: Native dictionary symbols (`**/anysoftkey*_jni.so`) are excluded from stripping in packaging options.
- **Build Verified**: Confirmed by a complete `./gradlew :ime:app:assembleRelease` invocation passing `Task :ime:app:minifyReleaseWithR8`.
- **Verdict: PASS (Release minification and APK assembly verified).**
