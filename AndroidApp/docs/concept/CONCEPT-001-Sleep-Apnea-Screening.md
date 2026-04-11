# CONCEPT-001: Sleep Apnea Screening — Modularna architektura multi-sensor

**Status:** Draft
**Date:** 2026-04-11
**Author:** Lukasz Seremak
**Relates to:** ADR-001 (Polar H10 Integration), TDR-001 (HRV Monitoring)

---

## 1. Executive Summary

### 1.1 Cel dokumentu

Dokument opisuje koncepcję rozszerzenia aplikacji **SleepyHead** o funkcję
**screeningu bezdechu sennego** (Sleep Apnea Screening) na podstawie danych
z **dowolnej kombinacji sensorów BLE**, przy czym:

- **Polar H10** jest **głównym, samodzielnym sensorem** — dostarcza pełen zestaw kanałów diagnostycznych (EKG, ACC, RR) i stanowi kompletny system pre-screeningu bez dodatkowych urządzeń
- **Pulsoksymetr BLE** ze standardowym profilem PLX (`0x1822`) jest **opcjonalnym rozszerzeniem** — podnosi dokładność eAHI, ale nie jest warunkiem działania systemu
- System **aktywnie informuje użytkownika** o aktualnej konfiguracji sensorów i sugeruje podłączenie dodatkowego sensora, gdy działa w trybie niepełnym

**Polar H10 jako samodzielny system:** Kombinacja RR interwałów, surowego EKG (130 Hz)
i akcelerometru dostarcza wystarczającą ilość kanałów diagnostycznych, aby dostarczyć
użyteczne szacowanie eAHI bez pulsoksymetru. Dodanie pulsoksymetru BLE poprawia dokładność
(z r ≈ 0.65–0.80 do r ≈ 0.80–0.92), ale nie jest warunkiem działania systemu.
Każda konfiguracja sensorów daje użyteczny wynik — nie ma „trybu niedziałającego".

### 1.2 Dlaczego teraz

SleepyHead posiada już działające MVP:
- Połączenie BLE z Polar H10 (ADR-001)
- Streaming HR + RR interwałów w czasie rzeczywistym
- Obliczanie RMSSD z 5-minutowego okna przesuwnego (TDR-001)
- Zapis sesji do plików JSONL, ForegroundService

Te komponenty stanowią **fundament** pod screening bezdechu. Rozszerzenie wymaga
dodania trzech nowych strumieni danych (EKG 130 Hz, akcelerometr, SpO₂) oraz
algorytmów korelacji.

### 1.3 Zakres

Ten dokument **nie jest planem implementacji** — to analiza wykonalności
i uzasadnienie merytoryczne. Plan implementacji powstanie w osobnym dokumencie.

### 1.4 Disclaimer medyczny

> **SleepyHead NIE jest wyrobem medycznym.** Wyniki screeningu mają charakter
> wyłącznie informacyjny i nie stanowią diagnozy medycznej. Rozpoznanie bezdechu
> sennego wymaga badania polisomnograficznego (PSG) przeprowadzonego w certyfikowanym
> laboratorium snu pod nadzorem lekarza. SleepyHead może służyć jedynie jako
> narzędzie wstępnej oceny (pre-screening) sugerujące zasadność konsultacji
> w poradni medycyny snu.

---

## 2. Tło kliniczne

### 2.1 Bezdech senny — definicja i epidemiologia

**Bezdech senny** (Sleep Apnea) to zaburzenie oddychania podczas snu, polegające
na powtarzających się epizodach częściowego lub całkowitego zatrzymania przepływu
powietrza przez drogi oddechowe.

#### Typy bezdechu

| Typ | Mechanizm | Częstość |
|---|---|---|
| **Obturacyjny (OSA)** | Mechaniczne zamknięcie górnych dróg oddechowych (zapadnięcie gardła) przy zachowanym wysiłku oddechowym | ~84% przypadków |
| **Centralny (CSA)** | Brak napędu oddechowego z OUN — mózg „zapomina" wysłać sygnał do mięśni oddechowych | ~1% w izolacji, ~15% jako komponent mieszany |
| **Mieszany** | Rozpoczyna się jako centralny, przechodzi w obturacyjny | ~15% |

#### Epidemiologia

Benjafield et al. (2019) w globalnej metaanalizie (*Lancet Respiratory Medicine*)
oszacowali, że **936 milionów dorosłych** na świecie (mężczyźni i kobiety 30–69 lat)
ma OSA o nasileniu co najmniej łagodnym (AHI ≥ 5). W Polsce, przy ekstrapolacji
danych europejskich, szacuje się ~4–5 mln dorosłych z OSA, z czego **~80%
pozostaje niezdiagnozowanych**.

#### Konsekwencje zdrowotne nieleczonego OSA

- Nadciśnienie tętnicze (2–3× wyższe ryzyko)
- Choroba niedokrwienna serca, zawał, udar mózgu
- Zaburzenia rytmu serca (migotanie przedsionków)
- Cukrzyca typu 2 (insulinooporność)
- Wypadki drogowe (senność dzienna — 2–7× wyższe ryzyko)
- Depresja, zaburzenia poznawcze

### 2.2 Metody diagnostyczne — PSG i poligrafia Typ I–IV

Metody diagnostyczne bezdechu sennego klasyfikuje się według liczby
rejestrowanych kanałów i warunków badania.

#### Typ I — Polisomnografia (PSG) — złoty standard

**Pełne badanie laboratoryjne** przeprowadzane w szpitalnym laboratorium snu,
pod nadzorem technika.

| Kanał | Czujnik | Co mierzy |
|---|---|---|
| EEG (elektroencefalografia) | Elektrody na skórze głowy | Fale mózgowe — fazy snu, przebudzenia |
| EOG (elektrookulografia) | Elektrody przy oczach | Ruchy gałek ocznych — faza REM |
| EMG (elektromiografia) | Elektrody na brodzie + nogach | Napięcie mięśni — bruksizm, RLS |
| EKG (elektrokardiografia) | Elektrody na klatce | Rytm serca, arytmie |
| Przepływ powietrza | Kanula nosowa + termistor | Apnee i hypopnee |
| Wysiłek oddechowy | Pasy piezoelektryczne (pierś + brzuch) | Ruchy oddechowe klatki i brzucha |
| SpO₂ (pulsoksymetria) | Klips na palec | Saturacja tlenem krwi |
| Pozycja ciała | Czujnik pozycji | Leżenie na plecach vs. na boku |
| Chrapanie | Mikrofon | Natężenie i częstość chrapania |
| EMG nóg | Elektrody na piszczelach | Periodic Limb Movements (PLM) |

**Zalety:** Kompletny obraz — pozwala na scoring faz snu, przebudzeń, zdarzeń
oddechowych i ruchowych. Jedyna metoda pozwalająca na pełną klasyfikację
(AHI wg AASM 2012).

**Wady:** Koszt 2000–5000 PLN (NFZ: kolejka 6–12 miesięcy), wymaga noclegu
w szpitalu, „efekt pierwszej nocy" (zaburzenie snu przez warunki laboratoryjne).

#### Typ II — Pełna PSG w domu

Identyczny zestaw czujników jak Typ I, ale **bez nadzoru technika**. Pacjent
zakłada urządzenie samodzielnie w domu.

**Zalety:** Naturalne warunki snu. **Wady:** Ryzyko odklejenia elektrod,
brak korekty w czasie badania.

#### Typ III — Poligrafia domowa (PG)

Uproszczone badanie z **4–7 kanałami**, bez EEG. Nie rejestruje faz snu
ani przebudzeń korowych.

| Kanał | Obecny |
|---|---|
| Przepływ powietrza (kanula nosowa) | ✅ |
| Wysiłek oddechowy (1–2 pasy) | ✅ |
| SpO₂ (pulsoksymetria) | ✅ |
| Tętno / EKG | ✅ |
| Pozycja ciała | ✅ (zwykle) |
| Chrapanie | ✅ (opcjonalnie) |
| EEG | ❌ |
| EOG, EMG | ❌ |

**Wskaźnik:** RDI (Respiratory Disturbance Index) lub REI (Respiratory Event Index)
— nie identyczny z AHI, bo brak EEG uniemożliwia scoring RERA (arousal-related events).

**Zalety:** Tańsze (500–1500 PLN), w domu, dostępne szybciej.
**Wady:** Zaniża wynik u pacjentów z fragmentacją snu bez desaturacji.

#### Typ IV — Uproszczona poligrafia (1–2 kanały)

Najczęściej **sama pulsoksymetria** (SpO₂ + HR) lub SpO₂ + przepływ powietrza.

**Zalety:** Tanie, proste, dostępne natychmiast.
**Wady:** Nie wykrywa zdarzeń bez desaturacji, wysoki odsetek fałszywie ujemnych
wyników u pacjentów z łagodnym OSA.

#### Tabela porównawcza

| Cecha | Typ I (PSG) | Typ II | Typ III (PG) | Typ IV | **SleepyHead** |
|---|---|---|---|---|---|
| Kanały | 16–22 | 16–22 | 4–7 | 1–2 | **5** (ECG+ACC+RR+HR+SpO₂) |
| EEG (fazy snu) | ✅ | ✅ | ❌ | ❌ | ❌ |
| Przepływ powietrza | ✅ bezpośredni | ✅ | ✅ | ✅/❌ | ⚠️ **pośrednio (EDR)** |
| SpO₂ | ✅ medical-grade | ✅ | ✅ | ✅ | ✅ konsumencki |
| EKG (surowy) | ✅ 12-lead | ✅ | ✅/1-lead | ❌ | ✅ **1-lead 130 Hz** |
| Wysiłek oddechowy | ✅ pasy RIP | ✅ | ✅ | ❌ | ✅ **ACC na klatce** |
| Pozycja ciała | ✅ | ✅ | ✅ | ❌ | ✅ **ACC grawitacja** |
| Koszt | 2000–5000 PLN | ~1500 PLN | 500–1500 PLN | 200–800 PLN | **~450 PLN** (jednorazowo) |
| Dostępność | Kolejka 6–12 mies. | 2–6 tyg. | 1–4 tyg. | Natychmiast | **Natychmiast, co noc** |
| Nadzór | Technik na miejscu | Bez nadzoru | Bez nadzoru | Bez nadzoru | Bez nadzoru |
| Rola kliniczna | Diagnoza (gold std.) | Diagnoza | Diagnoza (dom) | Screening | **Pre-screening** |

> **Wniosek:** SleepyHead mieści się pomiędzy Typem III a Typem IV. Ma przewagę
> nad Typem IV dzięki EKG + akcelerometrowi, ale nie dorównuje Typowi III
> z powodu braku bezpośredniego pomiaru przepływu powietrza (kanula nosowa).

### 2.3 Kluczowe wskaźniki kliniczne

#### AHI — Apnea-Hypopnea Index

**Definicja:** Liczba epizodów bezdechu (apnea) i spłyconego oddechu (hypopnea)
na godzinę snu.

```
AHI = (liczba apnei + liczba hypopnei) / czas snu (godziny)
```

**Klasyfikacja ciężkości:**

| AHI | Kategoria | Interpretacja |
|---|---|---|
| < 5 | Norma | Brak bezdechu sennego |
| 5–14.9 | Łagodny OSA | Zwykle bez objawów lub z łagodną sennością |
| 15–29.9 | Umiarkowany OSA | Senność dzienna, fragmentacja snu |
| ≥ 30 | Ciężki OSA | Znaczna desaturacja, ryzyko sercowo-naczyniowe |

**Definicje zdarzeń wg AASM 2012 (Berry et al.):**

- **Apnea:** ≥90% redukcji przepływu powietrza przez ≥10 sekund
- **Hypopnea (recommended rule):** ≥30% redukcji przepływu przez ≥10 sekund
  + desaturacja SpO₂ ≥3% LUB przebudzenie korowe (EEG arousal)
- **Hypopnea (acceptable rule):** ≥30% redukcji przepływu przez ≥10 sekund
  + desaturacja SpO₂ ≥4%

> **Implikacja dla SleepyHead:** Bez pomiaru przepływu powietrza (kanula nosowa)
> i bez EEG nie można bezpośrednio policzyć AHI. Zamiast tego obliczamy
> **estimated AHI (eAHI)** na podstawie pośrednich markerów (CVHR, EDR, ODI).

#### ODI — Oxygen Desaturation Index

**Definicja:** Liczba epizodów desaturacji (spadku SpO₂ o ≥3% lub ≥4%
od wartości bazowej) na godzinę snu.

```
ODI₃ = (liczba desaturacji ≥3%) / czas snu (godziny)
ODI₄ = (liczba desaturacji ≥4%) / czas snu (godziny)
```

ODI koreluje silnie z AHI (r ≈ 0.85–0.95 w ciężkim OSA), ale zaniża wynik
u pacjentów z hypopneami bez desaturacji.

#### T90% — Czas poniżej 90% SpO₂

**Definicja:** Łączny czas (minuty lub % nocy) spędzony z saturacją SpO₂ < 90%.

| T90% | Interpretacja |
|---|---|
| < 1% nocy | Norma |
| 1–10% | Umiarkowane epizody desaturacji |
| > 10% | Ciężka desaturacja nocna — pilna konsultacja |

#### RMSSD — Root Mean Square of Successive Differences

Zdefiniowany szczegółowo w TDR-001 §2.3. Metryka zmienności rytmu serca (HRV)
odzwierciedlająca aktywność przywspółczulną (parasympatyczną / vagalną).

```
dRR[i]  = RR[i+1] − RR[i]
RMSSD   = √( Σ(dRR[i]²) / (N−1) )
```

W kontekście bezdechu sennego: RMSSD wykazuje **oscylacje** z okresem 30–90 s
odpowiadające cyklom bradykardia–tachykardia (CVHR).

#### CVHR — Cyclic Variation of Heart Rate

Patrz §6 niniejszego dokumentu.

#### EDR — ECG-Derived Respiration

Patrz §5 niniejszego dokumentu.

---

## 3. Sprzęt — dostępne strumienie danych

### 3.1 Polar H10 — możliwości

Polar H10 to pas na klatkę piersiową z **czterema strumieniami danych** dostępnymi
przez Polar BLE SDK:

| Strumień | Metoda SDK | Częstotliwość | Feature Flag SDK | Stan w SleepyHead |
|---|---|---|---|---|
| **Tętno (HR)** | `startHrStreaming()` | ~1 Hz (co uderzenie) | `FEATURE_HR` | ✅ Zaimplementowany |
| **RR interwały** | `startHrStreaming()` | Per-beat (~1 Hz) | `FEATURE_HR` | ✅ Zaimplementowany |
| **Surowe EKG** | `startEcgStreaming()` | **130 Hz** (µV) | `FEATURE_POLAR_ONLINE_STREAMING` | ❌ Do implementacji |
| **Akcelerometr** | `startAccStreaming()` | **25–200 Hz** (mg) | `FEATURE_POLAR_ONLINE_STREAMING` | ❌ Do implementacji |

#### Walidacja badawcza Polar H10

**Gilgen-Ammann et al. (2019)** — *"RR interval signal quality of a heart rate
monitor and an ECG Holter at rest and during exercise"*,
*European Journal of Applied Physiology, 119*(9), 1991–1999.

Wyniki:
- Korelacja RR interwałów Polar H10 vs. EKG Holter: **r = 0.99**
- Średni błąd bezwzględny: **< 2 ms**
- Wniosek: Polar H10 jest **akceptowalny jako narzędzie badawcze** do pomiaru RR

#### Surowe EKG — co daje 130 Hz?

Każda próbka to wartość napięcia w **mikrowoltach (µV)**. Przy 130 Hz otrzymujemy
pełną morfologię przebiegu PQRST:

```
Typowy przebieg EKG (jeden cykl ~0.8 s):

    R
    │╲
    │ ╲
  P │  ╲     T
 ╱╲ │   ╲  ╱ ╲
╱  ╲│    ╲╱   ╲
────┼──────S────╲──── baseline
    │    Q        ╲
    │              U (opcjonalna)
```

Fale:
- **P:** depolaryzacja przedsionków (skurcz przedsionków)
- **QRS:** depolaryzacja komór (skurcz komór) — najwyższa amplituda
- **T:** repolaryzacja komór (powrót do stanu spoczynkowego)
- **Odcinek ST:** faza plateau — klinicznie istotny (niedokrwienie)
- **Odcinek QT:** czas repolaryzacji komór — wydłużenie = ryzyko arytmii

130 Hz daje ~100 próbek na jeden cykl serca (przy 78 BPM), co jest **wystarczające**
do ekstrakcji EDR (minimum w literaturze: 100 Hz — De Chazal 2003).

#### Akcelerometr — 3 osie

```
Polar H10 na klatce piersiowej:

      Z (przód ↔ tył)
      ↑
      │    Y (góra ↔ dół)
      │   ╱
      │  ╱
      │ ╱
      ├──────► X (lewo ↔ prawo)
      
Oddychanie → ruch klatki → modulacja osi Z (głównie)
Pozycja ciała → składowa grawitacyjna → stały offset na osiach
```

### 3.2 Pulsoksymetr BLE — możliwości

Pulsoksymetr przeznaczony do ciągłego pomiaru nocnego dostarcza:

| Dane | Typowa rozdzielczość | Częstotliwość |
|---|---|---|
| **SpO₂** (saturacja) | 1% (zakres 70–100%) | Co 1–4 sekundy |
| **Pulse Rate** (tętno z palca) | 1 bpm | Co 1–4 sekundy |
| **Perfusion Index (PI)** | 0.1% | Co 1–4 sekundy (niektóre modele) |
| **Ruch** (motion flag) | Boolean / wartość | Co 1–4 sekundy (niektóre modele) |

#### Wymagania wobec pulsoksymetru

Dla integracji z SleepyHead pulsoksymetr **musi**:

1. ✅ Obsługiwać **ciągły pomiar** (nie spot-check co kilka minut)
2. ✅ Komunikować się przez **BLE** (nie tylko klasyczny Bluetooth)
3. ✅ Najlepiej: implementować standardowy profil **BLE PLX** (Service `0x1822`)

#### Rekomendacja sprzętowa

Pulsoksymetry implementujące **standardowy profil BLE PLX** (Service UUID `0x1822`):

| Model | Forma | BLE PLX standard | Ciągły pomiar | Cena (~PLN) | Uwagi |
|---|---|---|---|---|---|
| **BerryMed BM1000C** ★ | Klips na palec | ✅ **Tak** | ✅ | ~100–160 | **Rekomendowany — patrz §3.2.1** |
| BerryMed BM1000E | Klips na palec | ✅ **Tak** | ✅ | ~100–160 | Wariant BM1000C z wyświetlaczem OLED |
| Contec CMS50F | Klips na palec | ⚠️ Częściowo (community RE) | ✅ | ~150–250 | Protokół zdekodowany, nie w pełni standardowy |
| Wellue O2Ring | Pierścień | ❌ Proprietarny Viatom | ✅ | ~250–400 | Wygodny na noc, ale proprietarny BLE |
| Wellue SleepU | Nadgarstek + klips | ❌ Proprietarny Viatom | ✅ | ~400–600 | Posiadany — wariant zapasowy |
| Masimo MightySat | Klips na palec | ✅ SDK (komercyjne) | ✅ | ~1500+ | Medical-grade, ale drogi |

#### 3.2.1 Rekomendowany model: BerryMed BM1000C

**BerryMed BM1000C** to pulsoksymetr klips-na-palec z modułem BLE 4.0,
produkowany przez Shanghai Berry Electronic Tech Co., Ltd. — chińskiego
producenta OEM czujników medycznych (dostawca modułów dla wielu marek).

| Parametr | Wartość |
|---|---|
| **Model** | BM1000C (szukaj też: „BerryMed BM1000C BLE" na AliExpress / Amazon) |
| **Forma** | Klips na palec (finger clip) |
| **BLE** | Bluetooth Low Energy 4.0 |
| **BLE Service** | Pulse Oximeter Service `0x1822` (standardowy PLX) |
| **Ciągły pomiar** | ✅ Tak — streamuje co ~1 s **dopóki palec jest w klipsie** |
| **Dane** | SpO₂ (%), Pulse Rate (bpm), Perfusion Index (PI), sygnał pletyzmograficzny |
| **Zakres SpO₂** | 70–100% (±2% w zakresie 80–100%) |
| **Zakres PR** | 30–250 bpm (±2 bpm) |
| **Bateria** | 2× AAA (~30 h ciągłego pomiaru — wystarczy na 3–4 noce) |
| **Auto-off** | Po ~8 s **bez wykrytego sygnału** (palec wyjęty lub utrata kontaktu) |
| **Cena** | ~$20–35 USD (~80–150 PLN) na AliExpress / Amazon |
| **Dostępność** | AliExpress, Amazon, eBay — szukaj: *"BerryMed BM1000C Bluetooth"* |

##### Użytkowanie nocne — da się, wymaga zabezpieczenia klipsa

BM1000C to urządzenie typu **spot-check** (zaprojektowane do jednorazowych
pomiarów), ale **da się go używać do ciągłego pomiaru nocnego** — pod warunkiem,
że palec pozostaje w klipsie. Analogiczne klipsy na palec stosuje się
w badaniach klinicznych poligrafii Typ III i Typ IV — również przez całą noc.

**Mechanizm auto-off:** Urządzenie wyłącza się po ~8 s **bez wykrytego sygnału
fotopletyzmograficznego** (PPG). Nie chodzi o timer — chodzi o utratę kontaktu
optycznego z palcem. **Dopóki palec jest w klipsie i czujnik widzi sygnał,
urządzenie pracuje ciągle** i streamuje dane przez BLE.

**Jak zabezpieczyć klips na noc (metody stosowane w klinice):**

1. **Przylepiec medyczny (micropore tape)** — 2–3 okrążenia wokół klipsa
   i palca. Najtańsze i najskuteczniejsze rozwiązanie. Stosowane standardowo
   w poligrafiach domowych.
2. **Cienka rękawiczka bawełniana** — zakładana na rękę z klipsem.
   Zapobiega przypadkowemu zrzuceniu, dodaje komfort.
3. **Obie metody łącznie** — tape + rękawiczka = maksymalne zabezpieczenie.

> **Realistyczna ocena:** Z taśmą medyczną klips trzyma się na palcu całą noc
> u większości osób. Sporadyczne chwilowe utraty sygnału (np. przy zmianie
> pozycji) są akceptowalne — aplikacja SleepyHead powinna obsłużyć krótkie
> przerwy w danych (gap handling), co i tak jest potrzebne dla każdego
> pulsoksymetru (nawet droższe pierścienie jak O2Ring tracą sygnał przy ruchu).

**Dlaczego BM1000C?**

| Kryterium | BM1000C | Wellue SleepU (posiadany) |
|---|---|---|
| **Protokół BLE** | ✅ Standardowy PLX (`0x1822`) | ❌ Proprietarny Viatom |
| **Integracja z SleepyHead** | ~50 linii kodu (standard GATT) | ~200–400 linii + reverse-engineering |
| **Ryzyko zmiany protokołu** | ❌ Zerowe (standard Bluetooth SIG) | ✅ Wysokie (firmware update) |
| **Community support** | Używany w OSCAR, open-source projekty | Ograniczony |
| **Wygoda nocna** | ⚠️ Klips na palec — wymaga tape'u, ale działa (jak w klinice) | ✅ Nadgarstek + klips — wygodniejszy |
| **Cena** | ~100 PLN | ~500 PLN (już posiadany) |

**Znane warianty nazewnicze** (ten sam sprzęt, różne etykiety sprzedażowe):
- BerryMed BM1000C
- „Bluetooth Fingertip Pulse Oximeter BLE 4.0"
- Niektóre sprzedawane pod markami „Creative Medical" lub „Jumper" (ten sam moduł BerryMed wewnątrz)

> **⚠️ Uwaga przy zakupie:** Upewnij się, że w opisie produktu jest wyraźnie
> wymienione **„Bluetooth 4.0 BLE"** (nie „Bluetooth 2.0" — to klasyczny BT,
> nie BLE). Sprawdź w recenzjach/pytaniach, czy urządzenie działa z aplikacjami
> BLE typu „nRF Connect" — jeśli tak, jest kompatybilne.


#### 3.2.2 Strategia dwóch adapterów

Architektura heksagonalna SleepyHead pozwala na obsługę obu pulsoksymetrów
bez zmian w domenie i warstwie aplikacji:

```
PulseOximeterPort (output port — application layer)
│
├── BlePlxAdapter           ← BerryMed BM1000C (standard PLX 0x1822)
│   Integracja: standardowy GATT, ~50 LOC
│   Priorytet: ★★★★★ (główny adapter)
│
└── SleepuBleAdapter        ← Wellue SleepU (proprietarny Viatom)
    Integracja: reverse-engineered protocol, ~200-400 LOC
    Priorytet: ★★☆☆☆ (zapasowy, jeśli będzie czas)
```

> **Rekomendacja:** Kup **BerryMed BM1000C** (~100 PLN) jako główne urządzenie
> do developmentu i testów. Wellue SleepU zachowaj jako wariant zapasowy —
> adapter Viatom można dopisać później. Obie ścieżki korzystają z tego samego
> portu `PulseOximeterPort`, więc zamiana adaptera to jedna linia w `AppDependencies.kt`.

### 3.3 Konfiguracje sensorów — profile funkcjonalne

System SleepyHead działa z różnymi konfiguracjami sprzętowymi, każda dostarczając
użyteczny wynik screeningu. Poniższa tabela opisuje dostępne kanały i szacowaną
dokładność dla każdej konfiguracji.

| Konfiguracja | Dostępne kanały | eAHI korelacja z PSG | Typ odpowiadający | Sugestia systemu |
|---|---|---|---|---|
| **Polar H10 only** | RR + EKG + ACC | r ≈ 0.65–0.80 | Pomiędzy Typ III a Typ IV | "Podłącz pulsoksymetr, aby zwiększyć dokładność" |
| **Polar H10 + pulsoksymetr** | RR + EKG + ACC + SpO₂ | r ≈ 0.80–0.92 | Pomiędzy Typ II a Typ III | Pełna konfiguracja |
| **Pulsoksymetr only** *(przyszłość)* | SpO₂ + Pulse Rate | r ≈ 0.65–0.75 | Typ IV | "Podłącz Polar H10, aby uzyskać pełny screening" |
| **Tylko RR (inny pas)** *(przyszłość)* | RR interwały | r ≈ 0.55–0.70 | Poniżej Typ IV | "Polar H10 dodaje EKG i ACC — znacznie wyższa dokładność" |

System **zawsze informuje użytkownika** o aktualnej konfiguracji sensorów i szacowanej
dokładności, oraz sugeruje podłączenie dodatkowego sensora. Każda konfiguracja daje
użyteczny wynik — nie ma „trybu niedziałającego".

### 3.4 Macierz pokrycia — SleepyHead vs. badania kliniczne

| Kanał diagnostyczny | PSG (Typ I) | Poligrafia (Typ III) | Typ IV | **SleepyHead (H10 + SpO₂)** | **SleepyHead (H10 only)** |
|---|---|---|---|---|---|
| Fale mózgowe (EEG) | ✅ | ❌ | ❌ | ❌ | ❌ |
| Ruchy oczu (EOG) | ✅ | ❌ | ❌ | ❌ | ❌ |
| Napięcie mięśni (EMG) | ✅ | ❌ | ❌ | ❌ | ❌ |
| EKG / HR | ✅ | ✅ | ❌/częściowo | ✅ **130 Hz EKG** | ✅ **130 Hz EKG** |
| Przepływ powietrza | ✅ kanula | ✅ kanula | ✅/❌ | ⚠️ **pośrednio (EDR)** | ⚠️ **pośrednio (EDR)** |
| SpO₂ | ✅ medical | ✅ medical | ✅ | ✅ konsumencki | ❌ |
| Wysiłek oddechowy | ✅ pasy RIP | ✅ pasy | ❌ | ✅ **ACC na klatce** | ✅ **ACC na klatce** |
| Pozycja ciała | ✅ | ✅ | ❌ | ✅ **ACC grawitacja** | ✅ **ACC grawitacja** |

---

## 4. Integracja BLE — profil Pulse Oximeter (PLX, 0x1822)

### 4.1 Bluetooth SIG PLX Service

Bluetooth SIG definiuje standardowy profil **Pulse Oximeter Service (PLX)**
o UUID `0x1822`. Jest to odpowiednik Heart Rate Service (`0x180D`),
którego Polar H10 używa do streamowania tętna.

| Element | UUID | Opis |
|---|---|---|
| **Service** | `0x1822` | Pulse Oximeter Service |
| PLX Continuous Measurement | `0x2A5F` | Ciągły pomiar SpO₂ + PR (Notify) |
| PLX Spot-Check Measurement | `0x2A5E` | Pomiar jednorazowy (Indicate) |
| PLX Features | `0x2A60` | Opis możliwości urządzenia (Read) |

#### Format danych PLX Continuous Measurement (`0x2A5F`)

```
Byte 0: Flags
  Bit 0: SpO₂ & PR present (zawsze 1)
  Bit 1: Sensor status present
  Bit 2: Pulse amplitude present

Byte 1–2: SpO₂ (SFLOAT, IEEE 11073) — np. 97.0%
Byte 3–4: Pulse Rate (SFLOAT) — np. 62.0 bpm
Byte 5+:  Opcjonalnie: sensor status, pulse amplitude
```

#### Analogia z istniejącym kodem

Odczyt SpO₂ przez BLE GATT jest **strukturalnie identyczny** z istniejącym
odczytem HR w SleepyHead (który jest aktualnie obsługiwany przez Polar SDK,
ale używa tego samego wzorca BLE). Adapter wymaga:

1. Skan po Service UUID `0x1822`
2. Subskrypcja Characteristic `0x2A5F` (Notify)
3. Parsowanie bajtów → `SpO2Sample` (obiekt domenowy)

### 4.2 Dual-device BLE na Androidzie

Android obsługuje **jednoczesne połączenia z wieloma urządzeniami BLE** (typowy
limit: 4–7 urządzeń, zależnie od chipsetu). Polar H10 + pulsoksymetr to
**dwa połączenia** — mieści się z zapasem.

```
[Polar H10]     ──BLE Connection 1──► [Android BLE Stack]
                                           │
[Pulsoksymetr]  ──BLE Connection 2──► [Android BLE Stack]
                                           │
                                      [SleepyHead App]
                                           │
                                    Synchronizacja po timestampach
```

#### Synchronizacja czasowa

Oba urządzenia nie mają wspólnego zegara. Synchronizacja opiera się na:

- **Timestamp po stronie telefonu** (`System.currentTimeMillis()`) przypisywany
  w momencie odbioru notyfikacji BLE
- Dokładność: ±50 ms (wystarczająca — zdarzenia oddechowe trwają ≥10 s)
- To ten sam mechanizm, który już stosujemy dla RR interwałów (TDR-001 §6.1)

### 4.3 Architektura heksagonalna — nowe elementy

```
APPLICATION (port/output/)
├── HeartRateMonitorPort.kt         ← istniejący (Polar H10)
├── PulseOximeterPort.kt            ← NOWY: abstrakcja źródła SpO₂
│     fun scanForOximeters(): Flow<FoundDevice>
│     suspend fun connect(deviceId: String)
│     fun disconnect(deviceId: String)
│     fun getSpO2Stream(deviceId: String): Flow<SpO2Sample>
│
FRAMEWORK (adapter/output/)
├── polar/PolarBleAdapter.kt        ← istniejący
├── oximeter/
│   ├── BlePlxAdapter.kt            ← NOWY: standardowy GATT PLX (0x1822)
│   └── SleepuBleAdapter.kt         ← OPCJONALNY: proprietarny Viatom
```

Port `PulseOximeterPort` jest **niezależny** od `HeartRateMonitorPort`.
Adaptery są wymienne — dodanie obsługi nowego pulsoksymetru wymaga jedynie
nowego adaptera, bez zmian w domenie i warstwie aplikacji.

### 4.4 Wellue SleepU — wariant proprietarny

Wellue SleepU (Viatom Technology) **nie** implementuje standardowego profilu PLX.
Używa proprietarnego protokołu:

| Element | UUID | Opis |
|---|---|---|
| Service | `0000FFF0-...` | Viatom custom service |
| Write (komendy) | `0xFFF1` / `0xFFF2` | Inicjacja streamingu |
| Notify (dane) | `0xFFF4` | Pakiety binarne co ~4 s |

Format pakietu (z reverse-engineeringu community — OSCAR project):

```
Byte 0: 0xAA (sync)
Byte 1: Command type
Byte 2: SpO₂ (%)
Byte 3: Pulse Rate (bpm)
Byte 4: Perfusion Index (‰)
Byte 5+: Status flags
```

> **Ryzyko:** Viatom może zmienić protokół w aktualizacji firmware bez powiadomienia.
> Dlatego **rekomendujemy pulsoksymetr ze standardowym BLE PLX** jako główne
> urządzenie, z adapterem Viatom jako opcjonalnym wariantem.

---

## 5. ECG-Derived Respiration (EDR)

### 5.1 Zasada działania

**EDR** to technika ekstrakcji sygnału oddechowego z surowego zapisu EKG,
bez dodatkowych czujników oddechowych.

Podstawą fizyczną jest fakt, że **oddychanie zmienia impedancję elektryczną
klatki piersiowej**:

```
Wdech → klatka się rozszerza → impedancja rośnie → amplituda R-piku maleje
Wydech → klatka się kurczy  → impedancja maleje → amplituda R-piku rośnie
```

Ta modulacja jest **cykliczna** z częstotliwością oddychania (12–20 cykli/min
u dorosłego w spoczynku) i widoczna w surowym sygnale EKG.

### 5.2 Metody ekstrakcji EDR

#### Metoda 1: Modulacja amplitudy R-piku

Najprostsza metoda. Mierzymy amplitudę (wysokość) każdego kolejnego R-piku
i tworzymy z tego nową serię czasową — ta seria odzwierciedla oddychanie.

```
Surowe EKG (130 Hz):
  R₁     R₂      R₃     R₄     R₅     R₆
  │╲     │╲      │╲     │╲     │╲     │╲
  │ ╲    │ ╲     │ ╲    │ ╲    │ ╲    │ ╲
──┤  ╲───┤  ╲────┤  ╲───┤  ╲───┤  ╲───┤  ╲───
  
R-amplituda:
  ▲      ▲▲      ▲▲▲    ▲▲     ▲      ▲▲
  ╲    ╱    ╲  ╱    ╲ ╱    ╲  ╱   ← sygnał oddechowy (EDR)
   ╲  ╱      ╲╱      ╲      ╲╱
   wydech   wdech   wydech  wdech
```

**Algorytm:**
1. Detekcja R-pików w surowym EKG (np. algorytm Pan-Tompkins)
2. Pomiar amplitudy każdego R-piku (peak-to-baseline)
3. Interpolacja do równomiernej siatki czasowej (np. 4 Hz)
4. Filtracja pasmowa 0.15–0.5 Hz (zakres oddychania)
5. Wynikowa seria = **sygnał oddechowy**

#### Metoda 2: Arytmia oddechowa (RSA — Respiratory Sinus Arrhythmia)

RR interwały naturalne skracają się podczas wdechu i wydłużają podczas wydechu
(modulacja nerwu błędnego). Filtracja pasmowa serii RR w zakresie 0.15–0.4 Hz
daje sygnał oddechowy.

**Zaleta:** Nie wymaga surowego EKG — działa z samymi RR interwałami.
**Wada:** RSA słabnie z wiekiem i w patologii autonomicznej — mniej wiarygodna
u starszych pacjentów.

#### Metoda 3: PCA-based EDR (Varon 2015)

Analiza głównych składowych (PCA) na wycinkach EKG obejmujących pełny
kompleks QRS. Pierwsza składowa główna koreluje z oddychaniem.

**Zaleta:** Najwyższa dokładność. **Wada:** Wyższy koszt obliczeniowy.

#### Porównanie metod

| Metoda | Wymagane dane | Złożoność | Dokładność EDR | Źródło |
|---|---|---|---|---|
| R-amplitude | Surowe EKG (≥100 Hz) | Niska | ★★★☆☆ | Moody et al. (1985) |
| RSA (filtracja RR) | Tylko RR interwały | Najniższa | ★★☆☆☆ | — |
| PCA-based | Surowe EKG (≥100 Hz) | Wysoka | ★★★★☆ | Varon et al. (2015) |

### 5.3 Wykonalność z Polar H10

Polar H10 dostarcza surowe EKG z częstotliwością **130 Hz**. Minimum wymagane
w literaturze to ~100 Hz (De Chazal 2003 używał 100 Hz z bazy MIT-BIH).
130 Hz jest **wystarczające** dla wszystkich trzech metod EDR.

**Ograniczenia:**
- Polar H10 to **single-lead** (jedno odprowadzenie) — odpowiednik Lead I
  z umieszczeniem na klatce. Mniejsza informacja niż 12-lead szpitalne.
- Jakość sygnału zależy od kontaktu elektrod z ciałem (sucha skóra, ruch).
- Podczas snu ruchy ciała są minimalne — korzystne dla jakości sygnału.

### 5.4 EDR w detekcji bezdechu

Podczas normalnego oddychania sygnał EDR wykazuje regularne oscylacje.
Podczas **apnei** oscylacje **zanikają** (brak przepływu powietrza):

```
Normalne oddychanie:           Apnea:                  Po apnei:

EDR:                           EDR:                     EDR:
  ∿∿∿∿∿∿∿∿∿                   ──────────────           ∿∿∿∿∿
  regularne                    brak modulacji           powrót
  12-20/min                    (flat line)              oddychania
```

**Kryteria detekcji apnei z EDR:**
- Zanik oscylacji EDR (amplituda < próg) przez ≥10 sekund
- Odpowiada definicji apnei (cessation of airflow ≥10 s)

> **Kluczowe ograniczenie EDR:** EDR wykrywa **modulację impedancji**, a nie
> bezpośredni przepływ powietrza. W apnei obturacyjnej wysiłek oddechowy
> (ruchy klatki) jest obecny, ale przepływ powietrza jest zablokowany.
> EDR może wykazywać resztkową modulację od wysiłku oddechowego, co komplikuje
> detekcję. Dlatego **EDR najlepiej działa w połączeniu z innymi kanałami**
> (SpO₂, CVHR, ACC).

### 5.5 Referencje

- **Moody, G.B. et al. (1985).** *"A new method for detecting atrial fibrillation
  using RR intervals."* Computers in Cardiology, 227–230.
  — Oryginalna publikacja techniki EDR.

- **De Chazal, P. et al. (2003).** *"Automated processing of the single-lead
  electrocardiogram for the detection of obstructive sleep apnoea."*
  IEEE Transactions on Biomedical Engineering, 50(6), 686–696.
  — Użyli surowego EKG 100 Hz + EDR + HRV features do minutowej klasyfikacji
  apnea/normal. Dokładność: **92.3%** na bazie MIT-BIH Apnea-ECG (70 pacjentów).

- **Varon, C. et al. (2015).** *"A novel algorithm for the automatic detection
  of sleep apnea from single-lead ECG."* IEEE Transactions on Biomedical
  Engineering, 62(9), 2269–2278.
  — PCA-based EDR + LS-SVM klasyfikator. Per-recording accuracy: ~90%.

---

## 6. Cyclic Variation of Heart Rate (CVHR)

### 6.1 Mechanizm fizjologiczny

CVHR to **patognomoniczny** (jednoznacznie wskazujący na chorobę) wzorzec
zmienności tętna towarzyszący epizodowi bezdechu obturacyjnego:

```
Faza 1: APNEA (10–90 s)
  │
  │  Brak przepływu powietrza
  │  → Hipoksja (spadek O₂ we krwi)
  │  → Stymulacja nerwu błędnego (X) przez chemoreceptory
  │  → BRADYKARDIA (spowolnienie tętna)
  │  → RR interwały się WYDŁUŻAJĄ
  │
  ▼
Faza 2: AROUSAL / PRZEBUDZENIE (3–15 s)
  │
  │  Mikroprzebudzenie korowe (wybudzenie z głębokiego snu)
  │  → Otwarcie dróg oddechowych
  │  → Hiperwentylacja kompensacyjna
  │  → Wyrzut katecholamin (adrenalina, noradrenalina)
  │  → TACHYKARDIA (przyspieszenie tętna)
  │  → RR interwały się SKRACAJĄ
  │
  ▼
Faza 3: POWRÓT DO SNU (10–30 s)
  │
  │  Pacjent zasypia ponownie
  │  → Tętno wraca do normy
  │  → Cykl się powtarza (5–100 razy/godzinę)
```

#### Wizualizacja CVHR na serii RR

```
RR (ms)
 1100│
 1000│    ╭──╮              ╭──╮              ╭──╮
  900│ ──╯    ╲──        ──╯    ╲──        ──╯    ╲──
  800│         ╲──╭──╮          ╲──╭──╮          ╲──
  700│            ╰──╯             ╰──╯             ╰──
     └──────────────────────────────────────────────── czas
      │← bradykardia →│← tachy →│     cykl ~40-90 s
      │    (apnea)     │(arousal)│
```

**Okres cyklu CVHR:** Typowo 30–90 sekund (odpowiada czasowi trwania
jednego epizodu bezdechu + przebudzenia).

### 6.2 Algorytm detekcji CVHR

**Wejście:** Seria RR interwałów (już dostępna w SleepyHead z TDR-001).

**Kroki:**

1. **Interpolacja** RR do równomiernej siatki (np. 4 Hz) — cubic spline
2. **Filtracja pasmowa** 0.01–0.05 Hz (okres 20–100 s) — zakres CVHR
3. **Detekcja cykli:** Znajdź lokalne minima i maksima w przefiltrowanym sygnale
4. **Walidacja cyklu:**
   - ΔHR (max − min) > **10 bpm**
   - Czas trwania cyklu: **20–120 s**
   - Bradykardia (minimum) poprzedza tachykardię (maksimum)
5. **CVHR Index:** Liczba walidowanych cykli / godzinę → estymacja AHI

#### Korelacja CVHR Index z AHI

Penzel et al. (2002) wykazali:

| CVHR Index | Korelacja z AHI (PSG) |
|---|---|
| Per-minute classification (apnea/normal) | Accuracy: **87%**, Sensitivity: **85%**, Specificity: **80%** |
| Per-patient AHI category | r ≈ **0.85** |
| Ciężki OSA (AHI ≥ 30) detekcja | Sensitivity: **~93%** |

### 6.3 Ograniczenia CVHR

| Ograniczenie | Wpływ |
|---|---|
| Przyjmowanie beta-blokerów | Tłumi odpowiedź HR → CVHR słabszy |
| Neuropatia autonomiczna (cukrzyca) | Osłabiona odpowiedź nerwu błędnego |
| Migotanie przedsionków (AF) | Chaotyczny rytm → CVHR niewykrywalny |
| Centralne apnee bez arousalu | Brak fazy tachykardii → cykl niepełny |
| Łagodny OSA z hypopneami | Słabsza odpowiedź HR → CVHR subtelny |

### 6.4 Referencje

- **Penzel, T. et al. (2002).** *"Systematic comparison of different algorithms
  for apnoea detection based on electrocardiogram recordings."*
  Medical and Biological Engineering and Computing, 40(4), 402–407.
  — Porównanie 8 algorytmów ECG-based apnea detection na danych PhysioNet.
  CVHR-based detection: sensitivity 85%, specificity 80%.

- **Guilleminault, C. et al. (1984).** *"Cyclical variation of the heart rate
  in sleep apnoea syndrome."* The Lancet, 323(8369), 126–131.
  — Pierwsza publikacja opisująca CVHR jako marker bezdechu sennego.

---

## 7. Akcelerometr — wysiłek oddechowy i pozycja ciała

### 7.1 Detekcja wysiłku oddechowego

Polar H10, umieszczony na klatce piersiowej, rejestruje **ruchy klatki**
poprzez wbudowany akcelerometr 3-osiowy (do 200 Hz).

```
Normalne oddychanie:
  ACC oś Z (przód–tył):
  ∿∿∿∿∿∿∿∿∿∿∿∿∿∿∿∿∿∿∿
  regularne ruchy 12-20/min, amplituda ~50-200 mg

Apnea OBTURACYJNA:
  ACC oś Z:
  ∿̃∿̃∿̃∿̃∿̃∿̃∿̃∿̃∿̃∿̃∿̃∿̃∿̃∿̃∿̃∿̃∿̃∿̃∿̃
  ruchy OBECNE, ale chaotyczne/wzmożone
  (pacjent walczy z zamkniętą drogą oddechową)

Apnea CENTRALNA:
  ACC oś Z:
  ─────────────────────────────
  brak ruchów klatki
  (mózg nie wysyła sygnału do oddychania)
```

#### Kluczowa wartość diagnostyczna ACC

**Akcelerometr pozwala odróżnić typ bezdechu:**

| Typ bezdechu | Przepływ powietrza | Wysiłek oddechowy (ACC) | CVHR (RR) | SpO₂ |
|---|---|---|---|---|
| **Obturacyjny (OSA)** | ❌ Brak | ✅ **Obecny** (wzmagający się) | ✅ Brady→tachy | ✅ Desaturacja |
| **Centralny (CSA)** | ❌ Brak | ❌ **Brak** | ✅ Brady→tachy (słabszy) | ✅ Desaturacja |
| **Mieszany** | ❌ Brak | ❌→✅ (brak → narastający) | ✅ Brady→tachy | ✅ Desaturacja |

> **Bez ACC nie można odróżnić obturacyjnego od centralnego bezdechu.**
> Jest to jedyny kanał w SleepyHead, który daje tę informację — EDR i SpO₂
> nie pozwalają na to rozróżnienie.

### 7.2 Detekcja pozycji ciała

Składowa **grawitacyjna** (DC, stała) sygnału ACC wskazuje orientację czujnika
względem ziemi. Pozwala na klasyfikację pozycji ciała:

```
Na plecach (supine):    Na lewym boku:       Na brzuchu (prone):
  Z = +1g               Z ≈ 0g               Z = -1g
  X ≈ 0g               X = +1g (lub -1g)     X ≈ 0g
  Y ≈ 0g               Y ≈ 0g                Y ≈ 0g
```

#### Znaczenie kliniczne pozycji

**Positional OSA** to podtyp bezdechu obturacyjnego, w którym AHI jest
**≥2× wyższe** w pozycji na plecach (supine) niż na boku.

Szacuje się, że **50–60% pacjentów z OSA** ma komponent pozycyjny
(Oksenberg et al., 1997). Dla tych pacjentów sama zmiana pozycji snu
(terapia pozycyjna) może zredukować AHI o 50%.

**Detekcja pozycji z ACC Polar H10** pozwala na:
- Raportowanie % nocy spędzonego w każdej pozycji
- Korelowanie AHI z pozycją (np. "na plecach: eAHI = 22, na boku: eAHI = 8")
- Rekomendację terapii pozycyjnej

### 7.3 Polar H10 ACC — uwagi praktyczne

| Parametr | Wartość | Komentarz |
|---|---|---|
| Zakres częstotliwości | 25–200 Hz | Dla oddychania wystarczy 25 Hz (downsample) |
| Rozdzielczość | 1 mg | Wystarczająca |
| Zużycie baterii | Zwiększone o ~20–30% | Przy 50 Hz — akceptowalne na noc (~20h) |
| Polar SDK | `startAccStreaming(deviceId, settings)` | Wymaga `FEATURE_POLAR_ONLINE_STREAMING` |

**Rekomendacja:** Streaming ACC z częstotliwością **25–50 Hz** (nie 200 Hz).
Dla detekcji oddychania (0.15–0.5 Hz) i pozycji (DC) 25 Hz jest więcej niż
wystarczające. Niższa częstotliwość = mniejsze zużycie baterii i transferu BLE.

---

## 8. Fuzja sygnałów — algorytm screeningu bezdechu

### 8.1 Pipeline przetwarzania

```
┌─────────────────────────────────────────────────────────────────────┐
│                        POLAR H10                                     │
│                                                                      │
│  RR interwały (1 Hz) ──────► CVHR Detector ──────┐                   │
│                                                   │                  │
│  Raw ECG (130 Hz) ─────────► R-peak detect ──┐    │                  │
│                              EDR extraction ──┤    │                  │
│                              QT interval ─────┤    │                  │
│                                               │    │                  │
│  Accelerometer (25 Hz) ────► Respiratory ─────┤    │                  │
│                              effort detect    │    │                  │
│                              Body position ───┤    │                  │
│                                               │    │                  │
└───────────────────────────────────────────────┤────┤─────────────────┘
                                                │    │
┌───────────────────────────────────────────────┤────┤─────────────────┐
│                     PULSOKSYMETR               │    │                  │
│                                                │    │                  │
│  SpO₂ (0.25–1 Hz) ────────► ODI calculator ──┤    │                  │
│                              Desat detector ──┘    │                  │
│                                                    │                  │
└────────────────────────────────────────────────────┤─────────────────┘
                                                     │
                                                     ▼
                                          ┌──────────────────┐
                                          │  APNEA SCORER    │
                                          │                  │
                                          │  Per-epoch (60s) │
                                          │  classification  │
                                          │  → eAHI          │
                                          │  → event list    │
                                          │  → night report  │
                                           └──────────────────┘
```

#### 8.1.1 Tryby pracy — adaptacja do dostępnych sensorów

System SleepyHead dynamicznie dostosowuje aktywne ścieżki przetwarzania
do dostępnych sensorów. W każdym trybie scorer dostosowuje reguły i pewność wyników.

**Tryb 1 — H10 only (Milestone 1):**
- ✅ Aktywne ścieżki: RR → CVHR, ECG → EDR, ACC → respiratory effort + body position
- ❌ Wyłączone: ODI, SpO₂ desaturation, T90%
- eAHI oznaczony jako „szacunkowy (bez SpO₂)" — informacja widoczna w UI
- Zdarzenia z obniżonym poziomem pewności względem trybu pełnego

**Tryb 2 — H10 + pulsoksymetr (Milestone 2):**
- ✅ Wszystkie ścieżki aktywne: RR + ECG + ACC + SpO₂
- Pełna fuzja kanałów — wyższa pewność i dokładność scorera
- eAHI oznaczony jako „pełna konfiguracja"

**Tryb 3 — pulsoksymetr only *(przyszłość)*:**
- ✅ Aktywne ścieżki: SpO₂ + PR → ODI
- ❌ Wyłączone: CVHR, EDR, ACC (respiratory effort, body position)
- Odpowiednik badania Typ IV — wykrywa ciężki OSA z desaturacją

### 8.2 Per-epoch scoring

Każda **epoka** (okres 60 sekund) jest klasyfikowana na podstawie wektora cech:

| Feature | Źródło | Typ | Waga diagnostyczna |
|---|---|---|---|
| `cvhr_flag` | Seria RR | Boolean: wykryto cykl CVHR | ★★★★☆ |
| `cvhr_delta_hr` | Seria RR | Amplituda cyklu (bpm) | ★★★☆☆ |
| `edr_cessation` | Raw ECG → EDR | Boolean: zanik oscylacji >10 s | ★★★★☆ |
| `edr_amplitude` | Raw ECG → EDR | Amplituda modulacji oddechowej | ★★★☆☆ |
| `spo2_desat` | SpO₂ | Boolean: spadek ≥3% od baseline | ★★★★★ |
| `spo2_nadir` | SpO₂ | Najniższa wartość SpO₂ w epoce | ★★★★☆ |
| `acc_effort` | ACC | Boolean: wysiłek oddechowy obecny | ★★★☆☆ |
| `acc_effort_type` | ACC | Normalny / wzmożony / brak | ★★★★★ (OSA vs CSA) |
| `body_position` | ACC (DC) | Supine / lateral / prone | ★★☆☆☆ |
| `qt_prolongation` | Raw ECG | Boolean: QT wydłużony vs. baseline | ★★☆☆☆ |

### 8.3 Algorytm rule-based (faza 1)

Pierwsza implementacja używa prostych reguł (bez ML):

```
APNEA EVENT detected IF:
  (cvhr_flag = TRUE  AND  spo2_desat = TRUE)     → HIGH confidence
  OR
  (cvhr_flag = TRUE  AND  edr_cessation = TRUE)   → MEDIUM confidence
  OR
  (spo2_desat = TRUE AND  edr_cessation = TRUE)   → MEDIUM confidence
  OR
  (cvhr_flag = TRUE  AND  cvhr_delta_hr > 15)      → LOW-MEDIUM confidence

APNEA TYPE:
  IF acc_effort = PRESENT  → OBSTRUCTIVE
  IF acc_effort = ABSENT   → CENTRAL
  IF acc_effort = ONSET_DELAYED → MIXED

SEVERITY MODIFIER:
  IF spo2_nadir < 80%  → SEVERE desaturation flag
  IF qt_prolongation    → Cardiac risk flag
```

#### Reguły dla Polar H10 only (bez SpO₂)

```
APNEA EVENT detected (H10 only mode) IF:
  (cvhr_flag = TRUE AND edr_cessation = TRUE)   → MEDIUM confidence
  OR
  (cvhr_flag = TRUE AND cvhr_delta_hr > 15)      → LOW-MEDIUM confidence
  OR
  (edr_cessation = TRUE AND acc_effort = ABSENT) → LOW confidence (CSA suspect)

CONFIDENCE MODIFIER (H10 only):
  - Wszystkie zdarzenia obniżone o jeden poziom vs. tryb pełny
  - eAHI = "szacunkowy (bez SpO₂)" — informacja wyświetlana użytkownikowi
  - Reguły oparte na spo2_desat / spo2_nadir / ODI są NIEAKTYWNE
```

### 8.4 Estymacja AHI

```
eAHI = (suma wykrytych zdarzeń) / (czas rejestracji w godzinach)
```

**Uwaga:** eAHI nie jest identyczne z AHI z PSG, ponieważ:
- Nie mierzymy bezpośrednio przepływu powietrza
- Nie mamy EEG do definiowania czasu snu (używamy czasu rejestracji)
- Hypopnee bez desaturacji mogą być pominięte

---

## 9. Szacowane korelacje z PSG i poligrafią

### 9.1 Opublikowane dane dokładności

Poniższe dane pochodzą z recenzowanych publikacji naukowych.

#### Algorytmy oparte na samym EKG (bez SpO₂)

| Badanie | Metoda | Dane | Czułość | Swoistość | Dokładność | Korelacja z AHI |
|---|---|---|---|---|---|---|
| **Penzel et al. (2002)** | CVHR + HRV features | PhysioNet Apnea-ECG (70 pac.) | 85% | 80% | 87% (per-minute) | r ≈ 0.85 |
| **De Chazal et al. (2003)** | ECG features (EDR + HRV + QRS) | MIT-BIH Apnea-ECG (70 pac.) | 86.4% | 76.9% | **92.3%** (per-minute, zbalansowane) | — |
| **Varon et al. (2015)** | PCA-EDR + LS-SVM | UZ Leuven (83 pac.) | ~88% | ~82% | ~90% (per-recording) | — |
| **Bsoul et al. (2011)** | Real-time ECG+SpO₂ | 8 pacjentów | 96% | 91% | — | — |

#### Algorytmy oparte na SpO₂ (ODI)

| Badanie / metoda | Czułość (AHI≥15) | Swoistość | Korelacja ODI vs AHI |
|---|---|---|---|
| ODI₃ (3% desaturacja) | 85–93% | 60–75% | r ≈ 0.85–0.90 |
| ODI₄ (4% desaturacja) | 75–85% | 75–85% | r ≈ 0.80–0.85 |
| T90% jako predictor | ~80% | ~70% | — |

### 9.2 Oczekiwana wydajność SleepyHead

Na podstawie powyższych danych literaturowych, **ekstrapolujemy** oczekiwaną
korelację SleepyHead z referencyjnymi metodami diagnostycznymi.

> **⚠️ Poniższe wartości to szacunki, nie wyniki walidacji klinicznej SleepyHead.**
> Rzeczywista wydajność może być wyższa lub niższa i wymaga walidacji w badaniu
> klinicznym z PSG jako referencją.

#### Tabela A: Korelacja z PSG (Typ I) — złoty standard

| Konfiguracja SleepyHead | AHI korelacja (r) | Czułość (AHI≥15) | Swoistość (AHI≥15) | Czułość (AHI≥5) | Swoistość (AHI≥5) |
|---|---|---|---|---|---|
| **Tylko RR interwały (CVHR)** | 0.55–0.70 | 70–80% | 65–75% | 50–60% | 55–65% |
| **RR + surowe EKG (CVHR + EDR)** | 0.65–0.80 | 75–85% | 70–80% | 55–70% | 60–75% |
| **Polar H10 only — RR + EKG + ACC (Milestone 1)** ★ baseline | 0.65–0.80 | 75–85% | 70–80% | 55–70% | 60–75% |
| **Tylko SpO₂ (ODI)** | 0.65–0.75 | 75–85% | 70–80% | 50–65% | 55–65% |
| **RR + SpO₂ (CVHR + ODI)** | 0.75–0.88 | 82–90% | 75–85% | 65–80% | 65–80% |
| **Polar H10 + pulsoksymetr — RR + EKG + ACC + SpO₂ (Milestone 2)** | **0.80–0.92** | **85–93%** | **80–90%** | **70–85%** | **70–85%** |

#### Tabela B: Korelacja z poligrafią Typ III

| Konfiguracja SleepyHead | Szacowana korelacja z PG Typ III |
|---|---|
| Tylko RR interwały | r ≈ 0.50–0.65 |
| RR + EKG + ACC | r ≈ 0.60–0.75 |
| RR + EKG + ACC + SpO₂ (pełny) | **r ≈ 0.80–0.90** |

> Poligrafia Typ III ma bezpośredni pomiar przepływu powietrza (kanula nosowa),
> którego SleepyHead nie ma. Dlatego korelacja nigdy nie osiągnie ~1.0 —
> najsłabszym punktem jest detekcja hypopnei bez istotnej desaturacji.

#### Tabela C: Korelacja z poligrafią Typ IV (SpO₂)

| Konfiguracja SleepyHead | vs. Typ IV |
|---|---|
| Tylko SpO₂ (gdybyśmy mieli sam pulsoksymetr) | ≈ identyczny (to jest Typ IV) |
| **RR + EKG + ACC + SpO₂ (pełny)** | **Przewyższa Typ IV** — dodatkowe kanały EKG + ACC |

#### Tabela D: Detekcja wg kategorii ciężkości OSA

| Kategoria | AHI | SleepyHead (pełny) — trafność | Komentarz |
|---|---|---|---|
| **Ciężki OSA** (≥30) | ≥30 | **85–95%** | Dramatyczne zdarzenia, silna CVHR, głębokie desaturacje |
| **Umiarkowany OSA** (15–30) | 15–30 | **70–85%** | Mieszanka apnei i hypopnei |
| **Łagodny OSA** (5–15) | 5–15 | **50–65%** | Dominują hypopnee bez głębokiej desaturacji — najtrudniejsze |
| **Norma** (<5) | <5 | **75–85%** | Brak wzorca CVHR i desaturacji → prawidłowa klasyfikacja |

### 9.3 Główne ograniczenia i ich wpływ

| Ograniczenie | Wpływ na wynik | Możliwość kompensacji |
|---|---|---|
| **Brak pomiaru przepływu powietrza** | Nie wykrywa hypopnei bez desaturacji | EDR daje pośredni sygnał oddechowy |
| **Brak EEG** | Nie może definiować czasu snu ani arousal | Użycie czasu rejestracji zamiast TST |
| **Konsumencki pulsoksymetr** | Niższa dokładność SpO₂ niż medical-grade | Większy margines błędu na desaturacje |
| **Single-lead EKG** | Mniejsza informacja niż 12-lead | Wystarczające dla EDR i CVHR (potwierdzone w literaturze) |
| **Ruch podczas snu** | Artefakty ruchowe w EKG i ACC | Filtracja + odrzucanie epok z artefaktami |

### 9.4 Wartość kliniczna mimo ograniczeń

Pomimo wymienionych ograniczeń, SleepyHead ma **unikalną przewagę**
nad wszystkimi powyższymi metodami:

**Możliwość monitoringu co noc, przez wiele tygodni.**

| Metoda | Liczba nocy | Zmienność noc-do-nocy |
|---|---|---|
| PSG (Typ I) | 1 noc | NIE uwzględniona — „snapshot" |
| Poligrafia (Typ III) | 1 noc | NIE uwzględniona |
| **SleepyHead** | **Wiele nocy (tygodnie/miesiące)** | **✅ Uwzględniona** |

AHI zmienia się z nocy na noc nawet o **30–50%** w zależności od pozycji snu,
alkoholu, zmęczenia, kongestii nosowej. Jednorazowe badanie PSG może trafić
na „dobrą noc" i dać fałszywie ujemny wynik. SleepyHead, monitorując **wiele
nocy**, może uchwycić tę zmienność i dać bardziej reprezentatywny obraz.

> **Le Bon et al. (2000):** *"Night-to-night variability of the AHI"* —
> wykazali, że u 20% pacjentów jednokrotne PSG daje błędną klasyfikację
> ciężkości w porównaniu z uśrednioną z wielu nocy.

---

## 10. Porównanie z innymi pasami na klatkę piersiową

### 10.1 Macierz funkcjonalności

| Funkcja | **Polar H10** | CooSpo H6 | Garmin HRM-Pro+ | Wahoo TICKR X | **Movesense** |
|---|---|---|---|---|---|
| **Tętno (HR)** | ✅ | ✅ | ✅ | ✅ | ✅ |
| **RR interwały** | ✅ | ✅ | ✅ | ✅ | ✅ |
| **Surowe EKG** | ✅ **130 Hz** | ❌ | ❌ | ❌ | ✅ **125/250 Hz** |
| **Akcelerometr** | ✅ **200 Hz** | ❌ | ⚠️ (ANT+ only) | ⚠️ (running only) | ✅ **13–416 Hz** |
| **Żyroskop** | ❌ | ❌ | ❌ | ❌ | ✅ |
| **Magnetometr** | ❌ | ❌ | ❌ | ❌ | ✅ |
| **Android BLE SDK** | ✅ Polar BLE SDK | ❌ | ❌ | ❌ | ✅ Movesense SDK |
| **Walidacja badawcza** | ✅ Bardzo szeroka | ❌ Brak | ⚠️ Ograniczona | ⚠️ Ograniczona | ✅ Dobra |
| **Bateria** | **~400 h** (CR2025) | ~50 h | ~24 h | ~20 h | ~10–40 h |
| **Cena (~PLN)** | ~350–450 | ~100–180 | ~450–550 | ~250–350 | ~500–800 |
| **EDR możliwe** | ✅ | ❌ | ❌ | ❌ | ✅ |
| **CVHR możliwe** | ✅ | ✅ (tylko z RR) | ✅ (tylko z RR) | ✅ (tylko z RR) | ✅ |
| **Respiratory effort** | ✅ (ACC) | ❌ | ❌ | ❌ | ✅ (ACC) |
| **Body position** | ✅ (ACC) | ❌ | ❌ | ❌ | ✅ (ACC) |
| **OSA vs CSA** | ✅ | ❌ | ❌ | ❌ | ✅ |

### 10.2 Wpływ wyboru pasa na diagnostykę bezdechu

| Konfiguracja | Dostępne kanały | Szacowana korelacja eAHI z PSG |
|---|---|---|
| **CooSpo H6** + SpO₂ | RR + SpO₂ | r ≈ **0.65–0.80** |
| **Garmin HRM-Pro+** + SpO₂ | RR + SpO₂ | r ≈ **0.65–0.80** |
| **Wahoo TICKR X** + SpO₂ | RR + SpO₂ | r ≈ **0.65–0.80** |
| **Polar H10** + SpO₂ | RR + EKG + ACC + SpO₂ | r ≈ **0.80–0.92** |
| **Movesense** + SpO₂ | RR + EKG (250 Hz) + ACC + Gyro + SpO₂ | r ≈ **0.82–0.93** |

> **Wniosek:** Pasy bez EKG i ACC (CooSpo, Garmin, Wahoo) dają jedynie CVHR
> z RR interwałów — tracą EDR, wysiłek oddechowy i pozycję ciała.
> Korelacja z PSG spada o **~0.10–0.15** (istotna różnica kliniczna).

### 10.3 Dlaczego Polar H10

- **Jedyny konsumencki pas** z surowym EKG + ACC + otwartym Android SDK w tej cenie
- Movesense jest alternatywą z lepszymi parametrami (250 Hz EKG, żyroskop),
  ale droższy (~2×) i z mniejszym ekosystemem konsumenckim
- CooSpo/Garmin/Wahoo to pasy **fitness**, nie **medyczne** — brak surowych danych

### 10.4 Możliwość adaptacji do innych pasów

Architektura heksagonalna SleepyHead pozwala na dodanie adaptera do dowolnego
pasa, który dostarcza przynajmniej HR + RR interwały. Wersja „light" algorytmu
(bez EDR, bez ACC) będzie działać z gorszą korelacją:

```
HeartRateMonitorPort                    ← istniejący port
├── PolarBleAdapter                     ← obecny adapter (pełny: HR+RR+ECG+ACC)
├── CooSpoAdapter (przyszłość)          ← standard BLE HR (0x180D): HR+RR only
├── GenericBleHrAdapter (przyszłość)    ← dowolny pas BLE z HR Service
└── MovesenseAdapter (przyszłość)       ← pełny: HR+RR+ECG+ACC+Gyro
```

---

## 11. Decyzje i rekomendacje

### 11.1 Wybór sprzętu

| Komponent | Decyzja | Uzasadnienie |
|---|---|---|
| Pas na klatkę piersiową | **Polar H10** (posiadany) | Jedyny pas z EKG+ACC+SDK w tej cenie. Walidowany badawczo. Samodzielny system pre-screeningu (Milestone 1). |
| Pulsoksymetr (główny) | **BerryMed BM1000C** (~100 PLN, **opcjonalny, zalecany do Milestone 2**) | Standardowy BLE PLX (`0x1822`). Łatwa integracja (~50 LOC). Patrz §3.2.1. |
| Pulsoksymetr (alternatywny) | **Wellue SleepU** (posiadany) | Proprietarny protokół Viatom = wyższe ryzyko, ale już posiadany |
| **Konfiguracja minimalna (Milestone 1)** | **Polar H10 (~350 PLN)** | Samodzielny, w pełni funkcjonalny system pre-screeningu — żaden dodatkowy sprzęt nie jest wymagany |
| **Łączny koszt sprzętu** | **Milestone 1: ~350 PLN \| Milestone 2: ~450–550 PLN** | Polar H10 (~350 PLN) + opcjonalnie BerryMed BM1000C (~100 PLN) |

### 11.2 Strategia integracji

| Faza | Opis | Priorytet | Milestone |
|---|---|---|---|
| **1. EKG streaming z Polar H10** | `FEATURE_POLAR_ONLINE_STREAMING` + R-peak detection + EDR | ★★★★★ | M1 |
| **2. ACC streaming z Polar H10** | Respiratory effort + body position | ★★★★★ | M1 |
| **3. CVHR detector** | Detekcja cykli bradykardia-tachykardia z serii RR | ★★★★☆ | M1 |
| **4. Apnea Scorer (H10-only mode)** | Fuzja kanałów EKG+ACC+RR → eAHI (bez SpO₂) | ★★★★★ | **✅ M1 gotowy** |
| **5. Pulsoksymetr BLE PLX** | Adapter generic GATT PLX, real-time SpO₂ streaming | ★★★★☆ | M2 |
| **6. Apnea Scorer (enhanced mode)** | Fuzja wszystkich kanałów z SpO₂ → wyższa dokładność eAHI | ★★★★★ | **✅ M2 gotowy** |
| **7. Adapter Wellue SleepU** | Proprietarny protokół Viatom jako alternatywa PLX | ★★☆☆☆ | Opcja |
| **8. Adapter CooSpo / generic HR** | Standard BLE HR (0x180D) — wersja „light" (RR only) | ★★☆☆☆ | Opcja |

### 11.3 Algorytm scoringu

| Faza | Podejście | Uzasadnienie |
|---|---|---|
| **Faza 1** | Rule-based (reguły logiczne) | Prostszy, interpretowalny, nie wymaga danych treningowych |
| **Faza 2 (przyszłość)** | ML classifier (Random Forest / SVM) | Po zebraniu danych z wielu nocy — potencjalnie wyższa dokładność |

---

## 12. Referencje

### 12.1 Referencje podstawowe

1. **Task Force of the ESC and NASPE (1996).** *"Heart rate variability: standards
   of measurement, physiological interpretation, and clinical use."*
   European Heart Journal, 17(3), 354–381.
   DOI: [10.1093/oxfordjournals.eurheartj.a014868](https://doi.org/10.1093/oxfordjournals.eurheartj.a014868)

2. **Penzel, T. et al. (2002).** *"Systematic comparison of different algorithms
   for apnoea detection based on electrocardiogram recordings."*
   Medical and Biological Engineering and Computing, 40(4), 402–407.
   DOI: [10.1007/BF02345072](https://doi.org/10.1007/BF02345072)

3. **De Chazal, P. et al. (2003).** *"Automated processing of the single-lead
   electrocardiogram for the detection of obstructive sleep apnoea."*
   IEEE Transactions on Biomedical Engineering, 50(6), 686–696.
   DOI: [10.1109/TBME.2003.812203](https://doi.org/10.1109/TBME.2003.812203)

4. **Varon, C. et al. (2015).** *"A novel algorithm for the automatic detection
   of sleep apnea from single-lead ECG."*
   IEEE Transactions on Biomedical Engineering, 62(9), 2269–2278.
   DOI: [10.1109/TBME.2015.2422378](https://doi.org/10.1109/TBME.2015.2422378)

5. **Gilgen-Ammann, R. et al. (2019).** *"RR interval signal quality of a heart
   rate monitor and an ECG Holter at rest and during exercise."*
   European Journal of Applied Physiology, 119(9), 1991–1999.
   DOI: [10.1007/s00421-019-04187-6](https://doi.org/10.1007/s00421-019-04187-6)

6. **Berry, R.B. et al. (2012).** *"Rules for scoring respiratory events in sleep:
   update of the 2007 AASM Manual for the Scoring of Sleep and Associated Events."*
   Journal of Clinical Sleep Medicine, 8(5), 597–619.
   DOI: [10.5664/jcsm.2172](https://doi.org/10.5664/jcsm.2172)

### 12.2 Referencje uzupełniające

7. **Moody, G.B. et al. (1985).** *"A new method for detecting atrial fibrillation
   using RR intervals."* Computers in Cardiology, 12, 227–230.
   — Oryginalna publikacja techniki EDR z EKG.

8. **Guilleminault, C. et al. (1984).** *"Cyclical variation of the heart rate
   in sleep apnoea syndrome."* The Lancet, 323(8369), 126–131.
   — Pierwsza publikacja opisująca CVHR.

9. **Benjafield, A.V. et al. (2019).** *"Estimation of the global prevalence and
   burden of obstructive sleep apnoea: a literature-based analysis."*
   The Lancet Respiratory Medicine, 7(8), 687–698.
   DOI: [10.1016/S2213-2600(19)30198-5](https://doi.org/10.1016/S2213-2600(19)30198-5)

10. **Bsoul, M. et al. (2011).** *"Apnea MedAssist: real-time sleep apnea monitor
    using single-lead ECG."* IEEE Transactions on Information Technology in
    Biomedicine, 15(3), 416–427.

11. **Oksenberg, A. et al. (1997).** *"Positional vs nonpositional obstructive sleep
    apnea patients."* Chest, 112(3), 629–639.

12. **Le Bon, O. et al. (2000).** *"Mild to moderate sleep respiratory events:
    one negative night may not be enough."* Chest, 118(2), 353–359.

13. **Flatt, A.A. & Esco, M.R. (2016).** *"Smartphone-derived heart-rate variability
    and biofeedback physiological interaction."* Journal of Sports Sciences.

14. **Bluetooth SIG.** *Pulse Oximeter Service (PLX) — GATT Specification.*
    [bluetooth.com/specifications/specs/pulse-oximeter-service-1-0/](https://www.bluetooth.com/specifications/specs/)

---

## 13. Słowniczek terminów medycznych i technicznych

| Termin | Skrót | Definicja |
|---|---|---|
| **Apnea (bezdech)** | — | Całkowite lub niemal całkowite (≥90%) zatrzymanie przepływu powietrza przez drogi oddechowe na ≥10 sekund |
| **Hypopnea (spłycenie oddechu)** | — | Częściowa (≥30%) redukcja przepływu powietrza na ≥10 s, z desaturacją ≥3% lub przebudzeniem korowym |
| **Obstructive Sleep Apnea** | OSA | Bezdech obturacyjny — mechaniczna blokada gardła przy zachowanym wysiłku oddechowym. Najczęstszy typ (~84%) |
| **Central Sleep Apnea** | CSA | Bezdech centralny — brak sygnału oddechowego z OUN. Klatka piersiowa nie wykonuje ruchów oddechowych |
| **Apnea-Hypopnea Index** | AHI | Liczba epizodów bezdechu + hypopnei na godzinę snu. <5 norma, 5–15 łagodny, 15–30 umiarkowany, ≥30 ciężki OSA |
| **Oxygen Desaturation Index** | ODI | Liczba epizodów spadku SpO₂ o ≥3% (ODI₃) lub ≥4% (ODI₄) na godzinę snu |
| **Estimated AHI** | eAHI | Szacunkowy AHI obliczony pośrednio (z EKG, HRV, SpO₂), nie bezpośrednio z pomiaru airflow |
| **Polysomnography** | PSG | Polisomnografia — pełne badanie snu z EEG + EOG + EMG + EKG + airflow + SpO₂ + effort + position. Złoty standard diagnostyczny |
| **Polygraphy** | PG | Poligrafia — uproszczone badanie oddychania w czasie snu (bez EEG). Typy II–IV w zależności od liczby kanałów |
| **SpO₂ (Saturacja)** | SpO₂ | Procentowe wysycenie hemoglobiny tlenem mierzone pulsoksymetrem. Norma: 95–100%. <90% = hipoksemia |
| **T90%** | — | Łączny czas (minuty lub %) nocy spędzony z SpO₂ < 90%. >5% nocy = znacząca desaturacja |
| **Nadir SpO₂** | — | Najniższa wartość SpO₂ zarejestrowana podczas nocy. <80% = ciężka desaturacja |
| **Heart Rate Variability** | HRV | Zmienność rytmu serca — zmiany w odstępach czasu między kolejnymi uderzeniami serca (RR interwałach) |
| **RMSSD** | — | Root Mean Square of Successive Differences — metryka HRV odzwierciedlająca aktywność przywspółczulną (vagalną). Patrz TDR-001 |
| **SDNN** | — | Standard Deviation of NN intervals — odchylenie standardowe interwałów RR. Odzwierciedla ogólną zmienność autonomiczną |
| **RR interval** | — | Czas (ms) między dwoma kolejnymi R-pikami w EKG. Odwrotność chwilowej częstości akcji serca |
| **Cyclic Variation of Heart Rate** | CVHR | Cykliczna zmienność tętna — patognomoniczny wzorzec bradykardia→tachykardia powtarzający się z okresem 30–90 s, towarzyszący epizodom bezdechu |
| **ECG-Derived Respiration** | EDR | Sygnał oddechowy wyekstrahowany z surowego EKG na podstawie modulacji amplitudy R-piku przez zmiany impedancji klatki piersiowej podczas oddychania |
| **Respiratory Sinus Arrhythmia** | RSA | Fizjologiczne wahania tętna zsynchronizowane z oddychaniem — przyspieszenie podczas wdechu, zwolnienie podczas wydechu |
| **Bradykardia** | — | Zwolnienie akcji serca poniżej 60 bpm. W kontekście bezdechu: odpowiedź wagalna na hipoksję podczas apnei |
| **Tachykardia** | — | Przyspieszenie akcji serca powyżej 100 bpm. W kontekście bezdechu: odpowiedź sympatyczna po mikroprzebudzeniu |
| **Arousal (przebudzenie korowe)** | — | Krótkotrwałe (3–15 s) wybudzenie z głębszej fazy snu, widoczne w EEG. Fragmentuje sen, powoduje senność dzienną |
| **Hipoksja / hipoksemia** | — | Niedotlenienie tkanek (hipoksja) / niski poziom tlenu we krwi (hipoksemia). SpO₂ < 90% |
| **EEG** | — | Elektroencefalografia — rejestracja aktywności elektrycznej mózgu. Pozwala na staging snu (N1, N2, N3, REM) |
| **EOG** | — | Elektrookulografia — rejestracja ruchów gałek ocznych. Identyfikacja fazy REM |
| **EMG** | — | Elektromiografia — rejestracja napięcia mięśni (podbródek, nogi). Atonia w REM, PLM |
| **EKG** | ECG | Elektrokardiografia — rejestracja aktywności elektrycznej serca. Fale PQRST |
| **Odcinek QT** | — | Czas od początku depolaryzacji do końca repolaryzacji komór serca. Wydłużenie (Long QT) = ryzyko arytmii |
| **Kompleks QRS** | — | Wychylenie w EKG odpowiadające depolaryzacji komór serca (skurcz). Najwyższa amplituda w cyklu PQRST |
| **Fala P** | — | Wychylenie w EKG odpowiadające depolaryzacji przedsionków serca |
| **Fala T** | — | Wychylenie w EKG odpowiadające repolaryzacji komór serca (powrót do spoczynku) |
| **Lead I (odprowadzenie I)** | — | Jedno z odprowadzeń EKG. Polar H10 na klatce piersiowej daje sygnał zbliżony do zmodyfikowanego Lead I |
| **BLE** | — | Bluetooth Low Energy — energooszczędny protokół bezprzewodowy używany przez sensory medyczne i fitness |
| **GATT** | — | Generic Attribute Profile — protokół BLE definiujący strukturę danych (Service → Characteristic → Value) |
| **PLX** | — | Pulse Oximeter Service — standardowy profil BLE (UUID 0x1822) do komunikacji z pulsoksymetrami |
| **RIP** | — | Respiratory Inductance Plethysmography — metoda pomiaru wysiłku oddechowego za pomocą pasów indukcyjnych na klatce i brzuchu (stosowana w PSG i PG Typ III) |
| **Positional OSA** | — | Podtyp OSA, w którym AHI jest ≥2× wyższe w pozycji na plecach (supine) niż na boku. Dotyczy ~50–60% pacjentów z OSA |
| **Nerw błędny (X)** | — | Dziesiąty nerw czaszkowy — główny nerw układu przywspółczulnego. Odpowiada za bradykardię w odpowiedzi na hipoksję |
| **Układ sympatyczny** | — | Część autonomicznego układu nerwowego odpowiedzialna za reakcje „walcz lub uciekaj" — przyspieszenie tętna, skurcz naczyń |
| **Układ przywspółczulny (parasympatyczny)** | — | Część autonomicznego układu nerwowego odpowiedzialna za „odpoczynek i trawienie" — zwolnienie tętna, relaksacja |
| **Chemoreceptory** | — | Receptory wykrywające zmiany stężenia O₂, CO₂ i pH we krwi. Stymulują nerw błędny podczas hipoksji |
| **Pan-Tompkins** | — | Klasyczny algorytm detekcji kompleksów QRS w sygnale EKG (Pan & Tompkins, 1985). Bazuje na filtracji pasmowej + adaptacyjnym progu |
| **SFLOAT** | — | Short Float — 16-bitowy format zmiennoprzecinkowy IEEE 11073 używany w BLE GATT do przesyłania wartości pomiarowych |

---

## 14. Powiązane dokumenty

| Dokument | Opis |
|---|---|
| `ADR-001-Polar-H10-Integration.md` | Decyzja o wyborze Polar H10 i platformy Android |
| `ADR-002-Testing-Stack.md` | Stack testowy: JUnit + MockK + Turbine |
| `TDR-001-HRV-Monitoring.md` | Design HRV monitoring — RMSSD, sesje, persistence |
| `android-implementation-plan.md` | Ogólny roadmap implementacji |
| *(przyszły)* `TDR-002-Sleep-Apnea-Screening.md` | Plan implementacji screeningu bezdechu (osobny dokument) |

