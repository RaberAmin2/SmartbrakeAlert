
# 🚗 SmartBrakeAlert — Android-basiertes Notbremswarnsystem

## 🧭 Projektbeschreibung

**SmartBrakeAlert** ist eine native Android-App, die mithilfe der Smartphone-Kamera und der GPS-Geschwindigkeit mögliche Kollisionen mit vorausfahrenden Fahrzeugen erkennt.
Sie imitiert einen **Notbremsassistenten**, jedoch ausschließlich als **Warnsystem** (kein Eingriff in Fahrzeugsteuerung).

Die App verwendet:

* **CameraX** für die Live-Kameraaufnahme
* **TensorFlow-Lite (YOLOv8-Nano quantisiert)** zur Objekterkennung
* **GPS-Sensor** zur Geschwindigkeitsmessung
* **Echtzeit-Logik** zur Berechnung der **Zeit-bis-Kollision (TTC)**
* **Akustische und visuelle Warnsignale**, wenn Gefahr erkannt wird

---

## 📂 Projektstruktur

```
SmartBrakeAlert/
 ├── app/
 │   ├── java/com/razamtech/smartbrakealert/
 │   │    ├── MainActivity.kt
 │   │    ├── camera/
 │   │    │    ├── CameraAnalyzer.kt
 │   │    │    └── DistanceEstimator.kt
 │   │    ├── sensors/
 │   │    │    ├── SpeedSensor.kt
 │   │    │    └── KalmanFilter.kt
 │   │    ├── logic/
 │   │    │    ├── CollisionPredictor.kt
 │   │    │    └── WarningController.kt
 │   │    └── ui/
 │   │         ├── OverlayView.kt
 │   │         └── SoundAlert.kt
 │   ├── assets/
 │   │    └── model.tflite
 │   └── res/
 │        ├── layout/activity_main.xml
 │        └── values/strings.xml
 ├── build.gradle
 └── README.md
```

---

## ⚙️ Technische Anforderungen

* **minSdk**: 26
* **compileSdk**: 34
* **Sprache:** Kotlin
* **Gradle Version:** ≥ 8.0
* **TensorFlow-Lite Version:** ≥ 2.14.0

### Abhängigkeiten (Gradle)

```gradle
dependencies {
    implementation "org.tensorflow:tensorflow-lite:2.14.0"
    implementation "org.tensorflow:tensorflow-lite-support:0.4.4"
    implementation "androidx.camera:camera-core:1.3.0"
    implementation "androidx.camera:camera-camera2:1.3.0"
    implementation "androidx.camera:camera-lifecycle:1.3.0"
    implementation "androidx.camera:camera-view:1.3.0"
    implementation "com.google.android.gms:play-services-location:21.0.1"
}
```

---

## 🧱 Modulbeschreibung

### **MainActivity.kt**

* Einstiegspunkt der App
* Initialisiert alle Hauptkomponenten:

  * `CameraAnalyzer` für Objekterkennung
  * `SpeedSensor` für Geschwindigkeitsdaten
  * `CollisionPredictor` für TTC-Berechnung
  * `WarningController` für Warnlogik
  * `OverlayView` für visuelle Warnanzeige

**Aufgaben:**

* Verbindung aller Datenströme
* Übergabe der Analyseergebnisse an UI

---

### **camera/CameraAnalyzer.kt**

* Nutzt **CameraX** (`ImageAnalysis.Analyzer`)
* Führt YOLO-Inference per TensorFlow-Lite durch
* Gibt pro Frame `DetectionResult(label, distance, confidence)` zurück

**Kernfunktion:**

```kotlin
class CameraAnalyzer(context: Context, onResult: (DetectionResult) -> Unit)
```

* lädt `model.tflite` aus `assets/`
* konvertiert `ImageProxy` zu `Bitmap`
* führt `interpreter.run()` aus
* sendet Ergebnisse zurück an Callback

---

### **camera/DistanceEstimator.kt**

* Schätzt die Entfernung zum erkannten Objekt.
* Zwei Modi möglich:

  * **Geometrisch:** anhand Bounding-Box-Breite
  * **ML-basiert:** optional mit Depth-Estimation-Model

**Funktion:**

```kotlin
fun estimateDistance(bboxWidth: Float, focalLengthPx: Float, objectRealWidth: Float): Float
```

---

### **sensors/SpeedSensor.kt**

* Liest Geschwindigkeit über **FusedLocationProviderClient (GPS)**.
* Gibt Wert in km/h als `LiveData<Float>` oder Variable `currentSpeed` zurück.
* Update-Intervall: 500 ms.

**Optional:** spätere Erweiterung über OBD-II Bluetooth (ELM327).

---

### **sensors/KalmanFilter.kt**

* Glättet Messwerte (z. B. GPS-Rauschen).
* Wird auf `SpeedSensor`- und Distanzdaten angewendet.

---

### **logic/CollisionPredictor.kt**

* Berechnet die **Zeit-bis-Kollision (TTC)**.
* TTC = distance / (speed / 3.6)

```kotlin
fun calculateTTC(distance: Float, speedKmH: Float): Float
```

Wenn `TTC < 2 s`, wird Gefahrensignal ausgelöst.

---

### **logic/WarningController.kt**

* Koordiniert Warnungen anhand der TTC-Schwellen.
* Übergibt visuelle Warnstufe an `OverlayView` und spielt Ton ab.

**Schwellwerte (Standard):**

| Stufe | Bedingung   | Farbe       | Ton            |
| ----- | ----------- | ----------- | -------------- |
| 0     | sicher      | transparent | —              |
| 1     | TTC < 3.5 s | gelb        | leiser Piepton |
| 2     | TTC < 2 s   | rot         | lauter Alarm   |

---

### **ui/OverlayView.kt**

* Custom-`View`, zeichnet BoundingBox-Rahmen + Farbstufen.
* Overlay liegt **über dem CameraX-PreviewView**.

**Zeigt:**

* Farbliche Warnung (grün / gelb / rot)
* Optional Text: *"Gefahr voraus!"*

---

### **ui/SoundAlert.kt**

* Erzeugt Tonwarnung mit `ToneGenerator`.
* Optional vibrierend über `VibratorManager`.

```kotlin
fun playWarning(context: Context)
```

---

## 📡 Datenflussdiagramm

```
CameraX → YOLOv8 (TFLite) → DetectionResult
                 ↓
           DistanceEstimator
                 ↓
SpeedSensor (GPS)
                 ↓
        CollisionPredictor (TTC)
                 ↓
        WarningController
                 ↓
OverlayView  +  SoundAlert
```

---

## 🧮 Formeln

### Entfernung:

[
distance = \frac{known_width \times focal_length_px}{bbox_width}
]

### Zeit-bis-Kollision (TTC):

[
TTC = \frac{distance}{speed / 3.6}
]

---

## 🧠 Zustandslogik (Pseudocode)

```kotlin
if (ttc < 2 && distance < 10) {
    playSound(ALARM)
    overlay.showDangerLevel(2)
} else if (ttc < 3.5) {
    playSound(WARNING)
    overlay.showDangerLevel(1)
} else {
    overlay.showDangerLevel(0)
}
```

---

## 📱 Benutzeroberfläche

**activity_main.xml**

* enthält:

  * `<androidx.camera.view.PreviewView>` → Kamera-Feed
  * `<com.razamtech.smartbrakealert.ui.OverlayView>` → Warnanzeige
* Vollbild-Layout, Landscape bevorzugt

---

## ⚡ Performance & Optimierung

| Bereich  | Optimierung                                |
| -------- | ------------------------------------------ |
| Inferenz | quantisiertes YOLOv8-Nano `.tflite` (INT8) |
| Kamera   | `ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST`  |
| FPS      | Ziel: 15 – 20 fps                          |
| Energie  | Sensor-Polling nur bei aktiver Fahrt       |
| RAM      | < 1 GB                                     |

---

## 🔊 Akustische Warnungen

Tonarten über `ToneGenerator`:

* **TTC < 2 s:** `TONE_CDMA_ALERT_CALL_GUARD`
* **TTC < 3.5 s:** `TONE_PROP_BEEP`

Lautstärke kann skaliert werden (0–100).

---

## ☁️ Erweiterungen

1. **OBD-Integration:** Fahrzeugdaten (Bremsdruck, Gasstellung)
2. **Nachtmodus:** automatische ISO-Anpassung
3. **Cloud-Sync:** anonyme Statistik über erkannte Warnungen
4. **Sprachwarnungen:** Text-to-Speech („Vorsicht! Fahrzeug voraus!“)
5. **ML-Tracking:** Objektverfolgung über mehrere Frames

---

## 🔒 Berechtigungen

```xml
<uses-permission android:name="android.permission.CAMERA" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.VIBRATE" />
```

---

## 🧩 Build-Hinweise

* Model-Datei `model.tflite` muss im `assets/`-Ordner liegen.
* Datei wird beim Build automatisch gepackt:

  ```gradle
  android {
      aaptOptions {
          noCompress "tflite"
      }
  }
  ```
* Kamera und Standort müssen **zur Laufzeit erlaubt** werden.

---

## ⚠️ Haftungsausschluss

Dieses Projekt dient **ausschließlich Forschungs- und Demonstrationszwecken**.
Es ist **nicht als sicherheitskritisches Fahrerassistenzsystem zugelassen**.
Es darf **nicht** in echten Verkehrssituationen zum Steuern oder Bremsen eines Fahrzeugs verwendet werden.
Verwendung auf eigene Verantwortung.

---

## 🎯 Ziel von Codex-Generierung

> Auf Basis dieses README soll **Codex** das komplette Android-Projekt `SmartBrakeAlert` generieren:
>
> * alle genannten Dateien, Klassen, Packages und Layouts
> * funktionsfähige Verbindung von Kamera, ML-Modell und GPS
> * TTC-Berechnung, akustische und visuelle Warnung
> * vollständiges Gradle-Setup
> * Dummy-`model.tflite` in `assets/`

---
