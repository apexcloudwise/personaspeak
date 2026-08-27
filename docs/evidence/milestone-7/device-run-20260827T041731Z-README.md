# #111 Live Emulator Fresh-Install Journey — Device-Class Run

**Run ID:** 20260827T041731Z-device
**Commit under test:** de797ba (main at PR #113 merge)
**APK:** keyboard/ime/app/build/outputs/apk/debug/app-debug.apk
**APK SHA-256:** db1f000f2a3b47c3d9fcfba89e2fc9a6ea3ad255a68a511a8c6fe5c58dc8f666
**Fixture:** M2_Qual_Fixture AVD (pixel_6, API 34 google_apis arm64-v8a), snapshot m2_pristine, booted `-no-snapshot-save -gpu swiftshader_indirect`
**Host tool:** macOS darwin, emulator/adb from homebrew android-commandlinetools; run driven by opencode-glm-flash

## Evidence class
`emulator_device` — this is a live emulator boot with a real fresh-install event (package absent → installed), real IME enablement and set-default via system settings, and product interactions driven through the actual UI (persona sheet, mood sheet, rewrite panel). Keyboard *letter* input was injected (`adb shell input text`) after tap-target drift made per-key taps unreliable on this host — recorded honestly here as a deviation. The M2 qualification remains the only run that proved full physical key-tap journeys; nothing in this run claims otherwise.

## Steps completed
1. **Pristine baseline**: package `biz.pixelperfectstudios.personaspeak` absent; default IME = Gboard LatinIME.
2. **Fresh install**: streamed install Success (install.log), IME enabled + set default (ime-list-after-enable.txt, default-ime-set.txt).
3. **Settings/onboarding surface**: PersonaSpeakSettingsActivity renders Get-started card, Characters card (Jeeves active), Brain "Not connected" (02, uidump-02).
4. **Character selection to Dr. King Schultz**: browser shows all 4 personas w/ emoji; dossier "Set as Active Character" → "✓ Active Character" (03–06); Witty mood set in settings (07–09) and later re-confirmed on the strip (18).
5. **Brain mock-only**: ProviderSetupScreen documented with empty key field + offline-understudy status (10). No credential ever entered.
6. **Rewrite cycle Apply=1**: host editor pristine `Tea at six.` (uidump-22) → strip Rewrite → Schultz/Witty review card with FakeProvider candidate (19) → Use this → host editor contains exactly the candidate string, single mutation (20, after-apply dump).
7. **Dismiss=0**: second cycle: review shown (23) → Dismiss → back to Resting, field unchanged `Tea at six.` (24, dismiss-final dump).
8. **RTL pass**: app locale ar via `cmd locale set-app-locales` → fully mirrored layout: title right-aligned, X top-left, Browse/Change/Configure flipped (26-rtl-arabic.png). debug.force_rtl=1 alone did NOT mirror this build (25) — recorded.
9. **Theme pair**: dark-mode settings via `cmd uimode night yes` (27) vs light (28); light resting keyboard+strip (29).

## Session-persistence note
Persona/mood live in in-memory session state: the force-stop between legs reset the strip to Jeeves/Polite, which was then re-set through the UI. This matches the documented product behavior ("not saved to disk") and was exercised twice on camera.

## Deviations & honest notes
- Headless emulator (-no-window): owner-directed in chat after windowed boots wedged twice on this host; screenshots are guest-framebuffer captures so visual evidence is unaffected.
- Text entry via `input text`, not per-key taps (see Evidence class note).
- Cycle-2 attempt (21/22) raced the strip's post-apply Done state; the clean dismiss proof is cycle 23→24.
- One stray `a` landed in the host editor during an early key-tap calibration attempt; that leg was abandoned and redone cleanly (all dumps before Rewrite show pristine `Tea at six.`).

## Receipt
See journey-receipt-device.json (same directory) for the machine-checkable verdict record.
