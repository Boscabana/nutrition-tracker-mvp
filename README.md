# Nutrition Tracker MVP

Native Android MVP in Kotlin + Jetpack Compose + Room.

## Was schon drin ist

- Lebensmittel lokal speichern
- Nährwerte pro 100g
- optionale Stück-/Portionsmenge, z.B. 1 Riegel = 40g, 1 Ei M = 53g
- Tagestracking für heute
- Eintrag wahlweise in Gramm oder Portion/Stück
- automatische Umrechnung: `Gramm = Menge * Portionsgewicht`
- automatische Makro-/Kalorienberechnung

## Öffnen

1. Ordner in Android Studio öffnen.
2. Gradle Sync ausführen.
3. App auf Android-Gerät/Emulator starten.

## Nächster Schritt

Barcode-Scan integrieren:
- ML Kit Google Code Scanner
- EAN speichern
- später Open Food Facts Lookup
