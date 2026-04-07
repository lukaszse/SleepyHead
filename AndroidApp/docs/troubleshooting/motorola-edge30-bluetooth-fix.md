# Naprawa Bluetooth — Flash firmware Motorola Edge 30

## Problem
Po testowym wgraniu aplikacji SleepyHead na Motorolę Edge 30 (USB debug) przestał działać dźwięk 
przy rozmowach telefonicznych przez Bluetooth w samochodzie (Kia). Spotify działa — problem dotyczy 
tylko profilu HFP (Hands-Free Profile). Factory reset NIE pomógł, bo nie czyści partycji 
firmware BT/modem.

## Rozwiązanie — pełny flash firmware przez Rescue and Smart Assistant

### Przygotowanie

1. **Naładuj telefon do min. 50%**
2. **Zrób backup:**
   - Zdjęcia/pliki → Google Drive lub komputer
   - Kontakty → powinny być zsynchronizowane z Google
   - SMS → aplikacja "SMS Backup & Restore" z Google Play
   - Zapisz sobie listę zainstalowanych aplikacji
3. **Pobierz Rescue and Smart Assistant** z oficjalnej strony Motoroli:
   - https://www.motorola.com/us/rescue-and-smart-assistant/p
   - (Alternatywnie szukaj "Lenovo Rescue and Smart Assistant download")
4. **Pobierz sterowniki Motorola USB** (jako backup):
   - https://motorola-global-portal.custhelp.com/app/answers/prod_answer_detail/a_id/89882
5. **Przygotuj kabel USB-C** — najlepiej oryginalny z zestawu (nie "charge-only")
6. **Wyłącz tymczasowo antywirusa/firewall** — LRSA pobiera firmware z serwerów Lenovo

### Instalacja narzędzia

1. Uruchom pobrany `SmartAssistant_Setup.exe`
2. Zaakceptuj licencję → wybierz folder → **Install**
3. Po instalacji program sprawdzi aktualizacje — pozwól mu się zaktualizować
4. Uruchom Rescue and Smart Assistant

### Flashowanie — krok po kroku

1. **Wyłącz telefon** całkowicie
2. W aplikacji LRSA kliknij **"Rescue"** (lub "Repair") na ekranie głównym
3. Program wyświetli instrukcję — postępuj zgodnie:
   - **Podłącz WYŁĄCZONY telefon** kablem USB do komputera
   - **Przytrzymaj Volume Down** podczas podłączania kabla 
     (wejście w tryb Emergency Download Mode)
   - Alternatywnie: jeśli program poprosi o tryb Fastboot → wyłącz telefon, 
     przytrzymaj **Vol Down + Power** przez ~10 sekund
4. LRSA wykryje urządzenie i automatycznie dobierze firmware dla 
   **Motorola Edge 30 (XT2243 / dubai)**
5. Kliknij **"Start"** / **"Rescue"**
6. Program pobierze pełny obraz firmware (kilka GB) i rozpocznie flashowanie

### ⚠️ WAŻNE PODCZAS FLASHOWANIA

- **NIE ODŁĄCZAJ telefonu!!!** (trwa 10–30 minut)
- Podłącz kabel bezpośrednio do portu USB na komputerze (NIE przez hub USB)
- Nie usypiaj/wyłączaj komputera
- Cierpliwie czekaj — telefon może się kilka razy restartować

### Po flashowaniu

1. Telefon uruchomi się z ekranem konfiguracji (jak nowy)
2. Przejdź przez standardową konfigurację Android
3. **Napraw parowanie Bluetooth z samochodem:**
   - Na telefonie: **Ustawienia → Połączone urządzenia → Sparuj nowe urządzenie**
   - **W samochodzie (Kia): NAJPIERW usuń stare parowanie** z Motorolą, 
     potem sparuj od nowa
   - To ważne — nie próbuj się łączyć ze starym parowaniem!
4. **Test:** Wykonaj rozmowę telefoniczną przez BT w samochodzie i sprawdź dźwięk

### Jeśli nadal nie działa po flashu

Jeśli problem z dźwiękiem rozmów BT nie zniknie po pełnym flashu firmware, 
możliwe przyczyny:
- **Defekt sprzętowy** modułu BT/WiFi (chip Qualcomm WCN6855) → naprawa serwisowa
- **Problem po stronie samochodu** → sprawdź aktualizację firmware infotainmentu Kia
- **Niekompatybilność** → niektóre Motorole mają znane problemy z HFP w konkretnych samochodach

---

## Metoda alternatywna — Fastboot (dla zaawansowanych)

> ⚠️ Ta metoda wymaga **odblokowania bootloadera**, co kasuje dane 
> i unieważnia gwarancję. Używaj tylko jeśli LRSA nie działa.

### Odblokowanie bootloadera
1. Ustawienia → System → Informacje o telefonie → 7x klik w "Numer kompilacji"
2. Ustawienia → System → Opcje deweloperskie → włącz "Odblokowanie OEM"
3. Wejdź w fastboot (Vol Down + Power)
4. Na komputerze: `fastboot oem unlock`
5. Potwierdź na telefonie

### Flash
1. Zainstaluj Android Platform Tools: https://developer.android.com/tools/releases/platform-tools
2. Pobierz firmware dla "dubai" (RETEU dla Europy) z:
   https://mirrors.lolinet.com/firmware/motorola/ (folder `dubai`)
3. Wyłącz telefon → Vol Down + Power → fastboot
4. Podłącz USB → `fastboot devices` (sprawdzenie)
5. Uruchom skrypt flashowania z paczki firmware

---

## Zapobieganie w przyszłości

Przy kolejnych testach aplikacji BLE na telefonie:
- Testuj na dedykowanym urządzeniu testowym, nie na daily driver
- Po odinstalowaniu apki BLE: Ustawienia → Aplikacje → Bluetooth → Wyczyść dane
- Regularnie restartuj telefon po sesjach debugowania BLE

