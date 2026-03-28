# Instrukcja: Xiaomi Mi 9 Lite jako urządzenie testowe

> **Data:** 2026-03-21
> **Urządzenie:** Xiaomi Mi 9 Lite (Android 10, API 29)
> **Cel:** Dedykowane urządzenie do developmentu SleepyHead (zamiast Motoroli Edge 30 — daily driver)

---

## Dlaczego osobny telefon do developmentu?

Wgrywanie aplikacji BLE przez USB debug na Motorolę Edge 30 spowodowało uszkodzenie
stosu Bluetooth (profil HFP — rozmowy telefoniczne w samochodzie). Naprawa wymagała
pełnego flashu firmware przez Rescue and Smart Assistant. Szczegóły w:
`docs/troubleshooting/motorola-edge30-bluetooth-fix.md`

**Zasada:** Motorola Edge 30 = daily driver (rozmowy BT w Kia). Xiaomi Mi 9 Lite = development.

---

## 1. Konfiguracja Xiaomi Mi 9 Lite do developmentu

### 1.1 Załóż konto Xiaomi (Mi Account)

MIUI wymaga konta Xiaomi, żeby włączyć "Instaluj przez USB".

1. `Ustawienia → Konto Mi` (lub "Xiaomi Account")
2. Zarejestruj się — numer telefonu lub e-mail
3. Potwierdź SMS/e-mail
4. Zaloguj się na telefonie

### 1.2 Włącz Opcje deweloperskie

1. `Ustawienia → O telefonie → 7x kliknij "Wersja MIUI"` (**uwaga:** MIUI, nie "Numer kompilacji"!)
2. Pojawi się komunikat "Jesteś teraz deweloperem"

### 1.3 Skonfiguruj Opcje deweloperskie

`Ustawienia → Ustawienia dodatkowe → Opcje deweloperskie`:

| Opcja                                        | Wartość |
|----------------------------------------------|---------|
| Debugowanie USB                              | ✅ ON   |
| Instaluj przez USB                           | ✅ ON   |
| Debugowanie USB (ustawienia zabezpieczeń)    | ✅ ON   |

> **Uwaga:** "Instaluj przez USB" wymaga zalogowanego konta Xiaomi. Bez niego opcja
> będzie wyszarzona.

### 1.4 Podłącz telefon do komputera

1. Podłącz kabel USB-C
2. Na telefonie pojawi się "Zaufaj temu komputerowi?" → **Tak**
3. Sprawdź połączenie w terminalu:
   ```bash
   adb devices
   ```
   Powinien pojawić się identyfikator urządzenia.

### 1.5 Wyłącz agresywne oszczędzanie baterii MIUI

MIUI domyślnie zabija procesy w tle — to psuje BLE streaming z Polar H10.

1. `Ustawienia → Aplikacje → Zarządzaj aplikacjami → SleepyHead`
2. **Autostart:** ON
3. **Oszczędzanie baterii:** Bez ograniczeń
4. `Ustawienia → Bateria → Optymalizacja baterii → SleepyHead → Nie optymalizuj`

### 1.6 Włącz GPS (lokalizacja)

Na Android 10 skanowanie BLE **wymaga włączonej lokalizacji** w systemie.

1. `Ustawienia → Lokalizacja → włącz`
2. Tryb: **Wysoka dokładność** (GPS + Wi-Fi + sieci komórkowe)

> **Ważne:** Bez włączonego GPS-a skanowanie BLE (Polar H10) nie znajdzie żadnych urządzeń!

---

## 2. Zmiany w kodzie — kompatybilność z Android 10

Xiaomi Mi 9 Lite (Android 10, API 29) nie ma uprawnień `BLUETOOTH_SCAN` / `BLUETOOTH_CONNECT`
(wprowadzonych w Android 12). Zamiast nich BLE scan wymaga `ACCESS_FINE_LOCATION`.

### 2.1 AndroidManifest.xml

Uprawnienia lokalizacyjne **bez** `maxSdkVersion` — żeby działały na Android 10:

```xml
<!-- Permissions for Bluetooth scanning and connection (Android 12+ / API 31+) -->
<uses-permission android:name="android.permission.BLUETOOTH_SCAN"
                 android:usesPermissionFlags="neverForLocation" />
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />

<!-- Permissions for older Android versions (< 12) -->
<uses-permission android:name="android.permission.BLUETOOTH"
                 android:maxSdkVersion="30" />
<uses-permission android:name="android.permission.BLUETOOTH_ADMIN"
                 android:maxSdkVersion="30" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION"
                 android:maxSdkVersion="30" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION"
                 android:maxSdkVersion="30" />
```

**Co się zmieniło:**
- `ACCESS_FINE_LOCATION` — usunięto stare `maxSdkVersion="30"`, potem przywrócono z powrotem
  (potrzebne na API 29, ale niepotrzebne na API 31+ — `maxSdkVersion` zapobiega wyświetlaniu
  "wymaga lokalizacji" w Google Play na nowszych Androidach)
- `ACCESS_COARSE_LOCATION` — dodano z `maxSdkVersion="30"` (Android wymaga obu uprawnień razem)

### 2.2 MainActivity.kt — warunkowe uprawnienia

```kotlin
private fun requestBlePermissions() {
    val needed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        // Android 12+ — new BLE permissions, no location needed
        arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT
        )
    } else {
        // Android 6–11 — BLE scan requires location permission
        arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
    }.filter {
        ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
    }.toTypedArray()

    if (needed.isEmpty()) {
        permissionsGranted.value = true
    } else {
        permissionLauncher.launch(needed)
    }
}
```

**Co się zmieniło:**
- Na Android < 12 aplikacja teraz prosi o uprawnienia lokalizacyjne (zamiast zakładać,
  że uprawnienia manifest są wystarczające)
- Na Android 12+ zachowanie bez zmian (BLUETOOTH_SCAN + BLUETOOTH_CONNECT)

---

## 3. Testowanie na Mi 9 Lite — checklist

Przed każdą sesją testową:

- [ ] GPS włączony na telefonie
- [ ] Oszczędzanie baterii wyłączone dla SleepyHead
- [ ] Polar H10 założony i aktywny (mokre elektrody)
- [ ] USB Debugging włączony

Testy do wykonania:

- [ ] Aplikacja prosi o uprawnienia lokalizacyjne (nie BT)
- [ ] Po zaakceptowaniu uprawnień — scan znajduje Polar H10
- [ ] Połączenie z Polar H10 działa
- [ ] Streaming HR (bpm + RR intervals) działa przez dłuższy czas (>5 min)

---

## 4. Różnice: Mi 9 Lite vs Motorola Edge 30

| Cecha                 | Xiaomi Mi 9 Lite      | Motorola Edge 30       |
|-----------------------|-----------------------|------------------------|
| Android               | 10 (API 29)           | 14 (API 34)            |
| Uprawnienia BLE       | ACCESS_FINE_LOCATION  | BLUETOOTH_SCAN/CONNECT |
| GPS wymagany do BLE   | ✅ Tak                | ❌ Nie                 |
| MIUI battery killer   | ⚠️ Wymaga konfiguracji| Nie dotyczy            |
| Konto producenta      | Wymagane (Mi Account) | Nie wymagane           |
| Rola                  | Development only      | Daily driver           |


