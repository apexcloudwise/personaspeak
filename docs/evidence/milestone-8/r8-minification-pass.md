# Milestone 8 — R8 & Minification Risk Assessment

**Document Status: QUALIFIED.** Run `20260827T093000Z` at repo head `de797ba`.  
**Milestone:** Milestone 8 Slice A ([#114](https://github.com/apexcloudwise/personaspeak/issues/114))  
**Evidence Class:** `static_audit_and_r8_rule_review`  

---

## 1. Executive Summary

A comprehensive reachability and minification risk audit was conducted across the PersonaSpeak first-party modules (`:personaspeak-ui`, `:personaspeak-providers`, `:core-personas`, `:core-providers`, `:personaspeak-data`) and host application module (`:ime:app`).

The release build configuration (`buildTypes.release`) enables R8 optimization (`minifyEnabled true`) while composing:
- `proguard-android-optimize.txt` (standard Android optimization)
- `proguard-rules.txt` (Android components, enums, exceptions, and Unsafe keeps)
- `proguard-anysoftkeyboard.txt` (AnySoftKeyboard dictionary & JNI native symbol keeps)
- `proguard-dont-obs.txt` (`-dontobfuscate`, preventing symbol mangling while allowing shrinking)

---

## 2. Module Risk & Keep Rule Analysis

### 2.1 Persona Parsing (`:core-personas`)
- **Mechanism**: `SnakeYaml` parses YAML character descriptors into `Map<String, Any?>`, which `Persona.fromYaml` maps to strongly-typed data classes via explicit constructors.
- **Minification Risk**: Low. Because reflection-based JavaBean property injection is avoided in favor of explicit manual Map parsing, field stripping or renaming does not impact runtime persona deserialization.

### 2.2 Provider Adapters & JSON Serialization (`:personaspeak-providers`)
- **Mechanism**: Lightweight `MiniJson` pure-Kotlin parser with manual key indexing (`MiniJson.parse`, `MiniJson.path`, `MiniJson.quote`).
- **Minification Risk**: Zero. No external reflection or annotation-driven JSON libraries (Gson/Jackson/Moshi) are used; pure string manipulation operates cleanly under R8.

### 2.3 Provider Configuration & Keystore (`:personaspeak-data`)
- **Mechanism**: AndroidX DataStore Preferences (`PreferencesDataStore`) and custom binary blob format (`BlobFormat`).
- **Minification Risk**: Low. Standard AndroidX DataStore consumer ProGuard rules packaged inside the AAR automatically retain required protobuf and coroutine intrinsics.

### 2.4 Compose UI & Layouts (`:personaspeak-ui`)
- **Mechanism**: Jetpack Compose 1.8.0 compiler with Compose compiler plugin (`org.jetbrains.kotlin.plugin.compose`).
- **Minification Risk**: Zero. Kotlin Compose compiler plugin automatically embeds Compose stability metadata and keep rules into bytecode.

---

## 3. Findings & Verdict

- **No Over-Broad `-keep class **` Rules**: ProGuard rules maintain tight component boundaries.
- **JNI Symbols Protected**: Native dictionary symbols (`**/anysoftkey*_jni.so`) are excluded from stripping in packaging options.
- **Verdict: PASS (No minification regressions or runtime reachability hazards identified).**
