# Walkthrough - Sauberes Löschen & Referenz-Schutz

Ich habe die Lösch-Logik der App massiv abgesichert. Ab jetzt führt das Löschen eines Artikels nicht mehr zu Verwirrung in deinen Rezepten oder im Tagebuch.

## Was wurde verbessert?

### 1. Radikale Bereinigung beim Löschen
- Wenn du einen Artikel löschst (z. B. "Spezifische Eier"), passiert jetzt folgendes:
    - **Rezepte**: Der Artikel wird sofort aus allen gespeicherten Mahlzeiten-Vorlagen entfernt. Es gibt keine "Geister-Zutaten" mehr.
    - **Tagebuch**: Die Einträge bleiben als Text erhalten (du siehst also noch, dass du Eier gegessen hast), aber die technische Verknüpfung zur ID wird gelöst (`foodItemId = -1`). Dadurch kann kein neuer Artikel mehr fälschlicherweise an diese Stelle rutschen.
    - **Hierarchie**: Wenn du eine Basis-Zutat löschst, werden alle verknüpften Markenartikel automatisch "unabhängig" gemacht (`parentId = null`), anstatt auf eine ungültige ID zu zeigen.

### 2. Intelligente Reparatur (`recalculateIds`)
- Die Reparatur-Funktion ist jetzt "smart". Sie mappt nicht nur IDs um, sondern erkennt verwaiste Zutaten in Mahlzeiten. Wenn ein Rezept eine Zutat enthält, die es in deiner Lebensmittel-Liste nicht mehr gibt, wird diese Zutat nun konsequent entfernt, statt sie einer falschen neuen ID zuzuweisen.

## Warum war das wichtig?
Zuvor blieben beim Löschen Reste in den Rezepten übrig. Da IDs früher bei jedem Start neu vergeben wurden, konnte es passieren, dass die ID eines gelöschten Artikels (z. B. 5 für Eier) an einen neuen Artikel (z. B. 5 für Milch) vergeben wurde. Das Ergebnis war Milch im Eier-Rezept. **Dieses Problem ist nun technisch unmöglich gemacht worden.**

## Verifikation

### Testergebnisse:
- [x] Löschen einer Zutat entfernt diese zuverlässig aus allen betroffenen Mahlzeiten.
- [x] Tagebucheinträge bleiben textlich erhalten, verlieren aber die "gefährliche" ID-Bindung.
- [x] Parent-Child-Beziehungen werden beim Löschen der Basis sauber getrennt.
- [x] Erfolgreicher Build der App.

> [!TIP]
> Deine Datenbank ist jetzt "selbstheilend". Alle Aktionen, die du ausführst, ziehen einen sauberen Rattenschwanz an Korrekturen durch die gesamte App.
