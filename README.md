# SensorySync 🌟

**SensorySync** is an interactive, multi-device sensory stimulation and visual engagement platform designed for children with neurological conditions, non-verbal communication, and short eye-contact spans.

The platform provides soothing, real-time visual patterns driven by facial tracking and hand gestures, fully controlled and monitored remotely via a companion parent application over local MQTT.

---

## 📱 System Architecture

SensorySync consists of two specialized applications:

```
+------------------------------------+           Local MQTT           +------------------------------------+
|       CHILD TABLET APP (`:app`)    |   (192.168.1.96:1883)          |     PARENT REMOTE APP (`:parent`)  |
|                                    | <============================> |                                    |
| • Fullscreen Kiosk Lockdown        |                                | • Dual-Frame Camera Verification   |
| • 6 Calming Visual Patterns        | • Real-Time Telemetry          | • Target Face Lock & Auto-Matching |
| • ML Kit Face & Landmark Tracking  | • 2 FPS JPEG Stream            | • Live Pulse Frequency Controls    |
| • Scaled Face Biometric Saving     | • Pattern & Hue Switchers      | • Eye Contact Duration & Streaks   |
| • Seizure-Safe Pulse Cap (≤3.0 Hz) | • Calibration & Safety Exit    | • Child Tablet Camera Show/Hide    |
+------------------------------------+                                +------------------------------------+
```

---

## ✨ Key Features

### 1. Child Tablet App (`:app`)
- **Complete Kiosk Lockdown**:
  - Native Android Screen Pinning (`startLockTask()`).
  - Hides and blocks system navigation bars, gesture swipes, notification shade, and hardware buttons.
  - Secret 3-second long press in the bottom-left corner allows caregivers to exit safely.
- **6 Soothing Visual Stimulation Patterns**:
  1. **Gentle Floating Stars**: Soft glowing particle field responding to gaze.
  2. **Breathing Mandala**: Sacred geometry rings with gentle breathing rhythm.
  3. **Calming Pulse Wave**: Gentle entrainment wave capped strictly at $\le 3.0\text{ Hz}$ for photosensitivity safety.
  4. **Liquid Water Ripples**: Dynamic wave interference matrix.
  5. **Soft Horizon Drift**: Peaceful starfield warp guided by eye gaze.
  6. **Swirling Liquid & Smoke**: Viscous fluid ink tendrils.
- **Persistent Facial Biometrics & Auto-Lock**:
  - Extracts scale-invariant geometric landmark ratios (eyes, nose, mouth).
  - Automatically loads and re-locks onto the child's face on startup.
- **Controllable Camera Window**:
  - Hidden by default to maximize visual immersion; remotely toggleable from the parent app.

### 2. Parent Remote Control App (`:parent`)
- **Dual-Frame Face Lock View**:
  - **Left Frame**: Live real-time camera feed (2 FPS) with MediaPipe / ML Kit landmark mesh overlays.
  - **Right Frame**: Persistent photo snapshot of the child's acquired target face.
- **Smart Face Detection Gating**:
  - The "Acquire Child Face" button automatically enables only when a face is detected in the frame.
- **Real-Time Telemetry & Eye Contact Analytics**:
  - Tracks live eye contact duration, focus streaks, and focus score percentage.
  - Offline dimming and auto-reconnect watchdog.
- **Remote Controls**:
  - Change visual patterns, adjust pulse frequency (0.2 Hz – 3.0 Hz), and adjust color spectrum hue in real time.
  - Remotely calibrate gaze tracking or cleanly close the child app.

---

## 🛠️ Tech Stack
- **Languages**: Kotlin
- **UI Framework**: Jetpack Compose & Material 3
- **Vision & ML**: Google ML Kit Face Detection (Landmarks, Tracking, Classification), Pose Detection
- **Camera**: AndroidX CameraX (Front Camera)
- **Communication**: Eclipse Paho MQTT (Local Broker)
- **Architecture**: Multi-module Gradle (`:app`, `:parent`)

---

## 🚀 Getting Started

### Prerequisites
- Android Studio Hedgehog (or newer) / JDK 17+ (or JDK 21)
- Android SDK 34+
- Local MQTT Broker (e.g. Mosquitto on `192.168.1.96:1883`)

### Building the Project
```bash
# Build both Child and Parent APKs
./gradlew assembleDebug
```

Output APKs:
- Child Tablet: `app/build/outputs/apk/debug/app-debug.apk`
- Parent Device: `parent/build/outputs/apk/debug/parent-debug.apk`

---

## 🔒 Safety & Medical Considerations
- **Photosensitivity / Seizure Safety**: Pulse / strobe frequencies are strictly clamped at a maximum of $3.0\text{ Hz}$ (0.2 Hz – 3.0 Hz range), well below the seizure-risk frequency threshold ($\ge 4.0\text{ Hz}$).
- **Non-Distractive Engagement**: Pure visual flow designed to reduce sensory overload and nurture focus.
