# Athena — Artemis Feature Parity Map

**Generated:** 2026-10-02  
**Upstream baseline:** `b48494cb` — moonlight-stream/moonlight-android v12.2  
**Artemis baseline:** `27ded2ad` — shared ancestor of upstream and Artemis  
**Artemis tip:** `c5cf27f4` — ClassicOldSong/moonlight-android `moonlight-noir`  
**Artemis commits ahead of base:** 555  
**Upstream commits since base:** 68 (translations + API-level cleanup + NDK r29 + OkHttp 5.5 + Keyboard Android 16 + libopus 1.6.1)

---

## Git Topology

```
27ded2ad  ← merge-base (Artemis branched here)
│
├──[68 commits]──→ b48494cb  (upstream v12.2, Athena main)
│
└──[555 commits]──→ c5cf27f4  (Artemis moonlight-noir, Athena artemis-ref)
```

**moonlight-common-c submodule:**
- Upstream: `874ac954` from `moonlight-stream/moonlight-common-c`
- Artemis: `c9994368` from `ClassicOldSong/moonlight-common-c` (forked; content unknown without submodule init)

---

## Build Environment Gap

| Requirement | Available | Gap |
|---|---|---|
| NDK r29.0.14206865 | NDK 26.1.10909125 | ❌ Need NDK r29 |
| compileSdk 37 / targetSdk 36 | android-34 | ❌ Need platform 37 |
| Gradle 9.7.1 | Gradle 8.4 cached | ❌ Need Gradle 9.7.1 download |
| Build-tools 34.0.0 | 34.0.0 | ✅ |
| JDK 17 | JDK 17.0.20.1 | ✅ |
| CMake 3.22.1 | 3.22.1 | ✅ |

**Real build is not feasible without SDK/Gradle downloads.** Patches are code-level verified only.

---

## Ported This Session (Slice 1 — Branch: `athena/touch-preservation-slice`)

| Feature | Files Changed | Status | Notes |
|---|---|---|---|
| Configurable touch button mapping | `AbsoluteTouchContext.java` | ✅ Ported & Tested | Backward-compat default constructor; `buttonPrimary`/`buttonSecondary` fields. Upstream `Game.java` unaffected. Compiled and verified in test harness. |
| Trackpad sensitivity scaling | `RelativeTouchContext.java` | ✅ Ported & Tested | Added `sensitivityX`/`sensitivityY` constructor params (default 100 = 1.0x). Applies `sensitivityX * 0.01f` scaling to `sendMouseMove`. Upstream callers unaffected. Compiled and verified in test harness. |
| TrackpadContext class & cancellation bug fix | `TrackpadContext.java` | ✅ Ported, Hardened & Verified | Full Artemis TrackpadContext ported without BOM (`\uFEFF` encoding bug resolved). Upstream/Artemis cancellation bug fixed: held mouse buttons tracked independently via `pressedButtons`; `cancelTouch()` now idempotently releases held buttons, cancels timers, resets flicking, velocity, and delta state with zero stale timer leaks. Verified compiling real production source. |
| Standalone JVM regression test harness | `test-harness/` | ✅ Implemented & Verifying Production Source | Standalone JVM test harness compiling ACTUAL production `TouchContext`, `AbsoluteTouchContext`, `RelativeTouchContext`, and `TrackpadContext` directly against pure-JVM stubs (`android.os.Handler`, `Looper`, `android.view.View`, `NvConnection`, `MouseButtonPacket`, `PreferenceConfiguration`). **Replaced previous fake mirror (`TrackpadLogicMirror`) with direct production bytecode execution.** All 19 production tests pass with deterministic virtual scheduler, owner-scoped handler cancellation on shared looper, pointer/scroll momentum regressions, and click release / scroll transition timer cancellation. |

---

## Feature Inventory — Not Yet Ported

### 🔴 Large Scope / Not Safe to Cherry-Pick Alone

| Feature | Artemis Files | Blocking Dependency | Priority |
|---|---|---|---|
| **TrackpadContext wiring in Game.java** | `Game.java` | Requires `StreamContainer` (replaces `StreamView`), `PanZoomHandler`, `KeyBoardLayoutController`, `GameInputDevice`, external display utilities | High — core trackpad mode activation |
| **Keyboard on-screen controller** | `virtual_controller/keyboard/KeyBoardController.java`, `KeyBoardLayoutController.java`, `KeyBoardAnalogStickButton.java`, `KeyBoardDigitalButton.java`, `KeyBoardControllerConfigurationLoader.java`, `LayoutSnappingHelper.java`, `keyboard.json`, `specialbuttons.json` | Requires Game.java refactor, new layout inflation | High |
| **Free-float analog sticks** | `AnalogStickFree.java`, `LeftAnalogStickFree.java`, `RightAnalogStickFree.java`, `keyAnalogStickFree.java` | Depends on KeyBoardController infrastructure | Medium |
| **External display support** | `StartExternalDisplayControlReceiver.java`, `utils/ExternalDisplayControlActivity.java`, `utils/ServerHelper.java` | New activities, requires manifest, Game.java hooks | Medium |
| **Game menu overlay** | `GameMenu.java` | Requires Game.java integration, overlay UI | Medium |
| **Profile system** | `ProfilesActivity.java`, `EditProfileActivity.java`, `profiles/ProfilesManager.java` | New preferences sub-system | Low–Medium |
| **Depth-based input (tflite)** | `midas-midas-v2-w8a8.tflite`, related inference code | ML model + runtime dependency | Low |
| **Keyboard accessibility service** | `KeyboardAccessibilityService.java` | AndroidManifest entry + service lifecycle | Low |
| **Debug info activity** | `DebugInfoActivity.java` | Standalone, but touches many internals | Low |
| **Sensitivity preference UI** | `PreferenceConfiguration.java` additions: `touchPadSensitivity`, `touchPadYSensitity`, `trackpadSensitivityX/Y`, `trackpadSwapAxis` | Requires SharedPreferences keys + Settings UI XML | Medium — needed to expose above params |

### 🟡 Moderate Scope — Isolable with Care

| Feature | Artemis Files | Notes | Priority |
|---|---|---|---|
| **Per-axis trackpad sensitivity prefs** | `PreferenceConfiguration.java` | Add 4 preference fields + Settings XML entries. Required to wire `RelativeTouchContext`/`TrackpadContext` sensitivity params. | High (next step) |
| **TouchContext interface: optional extra methods** | `TouchContext.java` | Artemis version has `\r\n` line endings only — no logic delta vs upstream. | No-op |
| **VirtualController layout enhancements** | `VirtualController.java`, `VirtualControllerConfigurationLoader.java`, `VirtualControllerElement.java`, `AnalogStick.java`, etc. | Artemis modifies existing virtual controller classes. Needs line-by-line diff review. | Medium |
| **Pro Controller USB driver** | `ProConController.java` (new) | Self-contained USB HID driver for Nintendo Pro Con. | Low |
| **ArtemisApplication** | `ArtemisApplication.java` (new) | Custom Application subclass. Needs manifest wiring. | Low |
| **Sensitivity bean** | `SensitivityBean.java` (new) | POJO used by profile system. | Low |
| **Touchpad face-button drawables** | `facebutton_touchpad.xml`, `facebutton_touchpad_press.xml` | Resource-only, safe to add any time. | Low |

---

## Upstream-Only Changes Since Artemis Fork (68 commits)

Things in Athena's `main` that Artemis does NOT have yet:

| Change | Commit(s) | Impact on Artemis port |
|---|---|---|
| JDK 17 compatibility (`-source 17 -target 17`) | `9221a0ca` | Artemis may have JDK 11 compat; verify Artemis build.gradle |
| NDK r29 | `1fa0e2a0` | Artemis uses older NDK version |
| AGP 9.4.0 | `801dba1b` | Artemis build.gradle needs update |
| OkHttp 5.5 | `98c12beb` | Network layer change |
| libopus 1.6.1 + OpenSSL 4.0.2 | `31b70030` | Native lib update |
| Android 16.1 keyboard capture | `ddb674a9` | New Android API; Artemis misses this |
| Target API 36, compile 37 | `4b2221d3` | API level bump |
| Disabled H.264 constraint on Oreo+ | `68adf9ec` | Video codec behavior |
| Remove `< API 21` code paths | `3df0103a` | minSdk 21 cleanup |
| Core library desugaring | `4eb24a8d` | jmDNS/Android compat fix |
| SDL joystick sync | `c2e224eb` | Controller input update |
| Controller LED rumble fix | `abde6021` | Bug fix |
| Xbox Series S/X controller | `280454fd` | New hardware support |

---

## moonlight-common-c Compatibility Note

Artemis uses `ClassicOldSong/moonlight-common-c` (commit `c9994368`). The upstream uses `moonlight-stream/moonlight-common-c` (commit `874ac954`). The Artemis fork may contain patches for multi-touch or extended input protocol support. **Until the Artemis common-c fork is inspected, TrackpadContext's NvConnection call surface must be assumed compatible** — both forks implement the same `sendMouseMove`, `sendMouseButtonDown`, `sendMouseButtonUp`, `sendMouseHighResScroll` calls that TrackpadContext uses. If the Artemis common-c adds new calls (e.g. `sendTouchEvent` for absolute multi-touch protocol), those calls would fail to compile against upstream common-c.

---

## Recommended Next Steps (Ordered by Risk/Value)

1. **[Low risk, high enablement]** Add `touchPadSensitivity` (X), `touchPadYSensitivity` (Y), `trackpadSwapAxis`, `trackpadSensitivityX`, `trackpadSensitivityY` to `PreferenceConfiguration.java` with defaults, and add preference items to the settings XML. This unblocks wiring `RelativeTouchContext` and `TrackpadContext` params to real user preferences.

2. **[Medium risk]** Inspect `ClassicOldSong/moonlight-common-c` at `c9994368` vs upstream `874ac954` to enumerate API differences. Determine whether Athena needs the ClassicOldSong fork or can stay on upstream common-c.

3. **[Medium risk]** Wire `TrackpadContext` into `Game.java` via minimal changes — add `trackpadContextMap[]`, switch input context based on `prefConfig.touchscreenTrackpad` without adopting `StreamContainer`. The existing `prefConfig.touchscreenTrackpad` branch in upstream `Game.java` (line 497) becomes the attachment point.

4. **[Large scope]** Port the on-screen keyboard controller (`KeyBoardController` tree). This is Artemis's largest feature delta and should be treated as a separate vertical slice after step 3.

5. **[After SDK install]** Configure SDK manager to install NDK r29, platform-37, and let Gradle wrapper download 9.7.1. Then attempt a real `gradlew assembleNoroot` to verify compilation of the ported slice.

---

## File Counts

| Category | Artemis-only Added Files | Modified vs Fork-Base |
|---|---|---|
| Total | 129 new | 237 modified |
| Touch/input | 4 new | 15 modified |
| VirtualController/keyboard | 14 new | 8 modified |
| Activities/UI | 8 new | 6 modified |
| Preferences/profiles | 5 new | 2 modified |
| Resources/assets | ~30 new | ~80 modified |
