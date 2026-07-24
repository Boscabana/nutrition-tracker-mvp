# Walkthrough - Platzsparendere UI für Artikel und Mahlzeiten

Ich habe die Benutzeroberfläche für Lebensmittel (Artikel) und Mahlzeiten optimiert, um Informationen kompakter darzustellen und erst bei Bedarf (Klick) einzublenden.

## Änderungen

### 1. Lebensmittel-Liste (Artikel)
- **Kompakte Ansicht**: Zeigt jetzt standardmäßig nur noch den Namen des Lebensmittels in einer Zeile an.
- **Ausklappbar**: Ein Klick auf das Element öffnet eine detaillierte Ansicht mit:
    - Marke
    - Kalorien pro 100g/ml
    - Makronährstoffen (Protein, Kohlenhydrate, Zucker, Fett, gesättigte Fette)
    - Hinterlegten Portionen und Packungsgrößen
- **Visuelles Feedback**: Ein Icon (ExpandMore/ExpandLess) zeigt den Status an.

### 2. Mahlzeiten-Verwaltung
- **Portionen-Support**: Das `MealEntity` wurde um ein Feld `servings` erweitert.
- **Mahlzeiten-Liste**:
    - Zeigt im eingeklappten Zustand den Namen und die **Kalorien pro Portion** an.
    - Beim Ausklappen wird die Zutatenliste eingeblendet.
    - Die Mengen der Zutaten werden automatisch auf eine Portion heruntergerechnet dargestellt.
- **Bearbeitungsdialog**: Es gibt nun ein Eingabefeld für die Anzahl der Gesamtportionen einer Mahlzeit.

### 3. Tagebuch-Integration
- Beim Hinzufügen einer Mahlzeit zum Tagebuch wird nun automatisch **eine Portion** berechnet und eingetragen (inkl. angepasster Zutatenmengen für diesen Log-Eintrag).

## Verifikation

### Manuelle Tests durchgeführt:
- [x] Lebensmittel-Liste auf-/zuklappen funktioniert flüssig.
- [x] Mahlzeit erstellt mit 2 Portionen -> Anzeige zeigt korrekte Kalorien (Gesamt/2).
- [x] Ausgeklappte Mahlzeit zeigt Zutatenmengen pro Portion (z.B. 250g statt 500g bei 2 Portionen).
- [x] Swipe-Aktionen (Löschen/Bearbeiten) funktionieren weiterhin auf den Karten.
- [x] Bearbeiten der Portionen einer Mahlzeit aktualisiert die Anzeige sofort.

> [!TIP]
> Die Mahlzeiten-Kalorien werden jetzt als "kcal pro Portion" gelabelt, um Missverständnisse bei Rezepten für mehrere Personen zu vermeiden.
