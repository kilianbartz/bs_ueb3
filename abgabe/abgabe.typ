#let header = {
  grid(
    columns: (1fr, auto),
    gutter: 0pt,
    [#text(weight: "bold")[Übungsblatt 3]],
    [Kilian Bartz (Mknr.: 1538561)]
  )
  line(length: 100%, stroke: 0.5pt)
}

#set page(
  paper: "a4",
  header: context {
  if counter(page).get().first() > 1 [
    #header
  ]
  
},
  numbering: "1",
)
#set text(
  font: "New Computer Modern"
)
#set par(
  first-line-indent: 1em,
)
#let notice(title: "Hinweis", body) = {
  block(
    width: 100%,
    inset: 0pt,
    radius: 4pt,
    stroke: 1pt + rgb(0,0,191),
    [
      #block(
        width: 100%,
        fill: rgb(0,0,191),
        inset: (x: 10pt, y: 6pt),
        radius: (top-left: 3pt, top-right: 3pt),
        text(white, weight: "bold", size: 11pt, title)
      )
      #block(
        width: 100%,
        inset: (left: 10pt, right: 10pt, top: -5pt, bottom: 10pt),
        body
      )
    ]
  )
}

#v(10pt)
#align(center, text(17pt)[
  *Übungsblatt 3: Optimistische Nebenläufigkeit mit ZFS-Snapshots*
])
#v(10pt)

#notice([Der zugehörige Code befindet sich im folgenden Github Repository: #link("https://github.com/kilianbartz/bs_ueb3").])


= Aufgabe 1
Zur Bearbeitung dieser Aufgabe habe ich die im Ordner `a1_a3` befindliche Java Bibilothek _Transaction_ erstellt. Um eine möglich breite Kompatibilität zu anderen Programmen zu bieten, stellt die Anwendung ein *zustandsloses* _Command Line Interface_ bereit. Implementiert sind die folgenden 5 Operationen:
- *transaction* start _name_: Startet eine Transaktion, speichert den Ausgangszustand des _zfs directory_#footnote([_zfs directory_ meint ein Verzeichnis innerhalb eines zfs pools (hier werden später die Operationen innerhalb der Transaktionen persistiert) und ist $eq.not$ _working directory_ (Hilfsverzeichnis für temporäre Transaktions-Dateien)]) und legt einen ZFS Snapshot an.
- *transaction* Commit _name_: Falls keine Konflikte vorliegen (also seit Start der Transaktion keine Änderungen im _zfs directory_ durchgeführt wurden), persistiere die writes innerhalb der Transaktion und lösche anschließend den zugehörigen Snapshot und die Transaktions-Datei. Andernfalls: Führe ein Rollback zum Transaktions-Start aus.
- *write* _name_ _datei_ _inhalt_: Plane _datei_ mit _inhalt_ als Teil der Transaktion _name_ zu schreiben. Diese Änderung wird erst beim Commit persisitiert.
- *read* _name_ _datei_: Lese _datei_ als Teil der Transaktion _name_. Hat sich der Zustand des _zfs directory_ seit Beginn der Transaktion geändert, führe ein Rollback durch.
- *remove* _name_ _datei_: Plane _datei_ als Teil der Transaktion _name_ zu löschen. Diese Änderung wird erst beim Commit persisitiert.
- *redo*: Vervollständige den Commit jeder Transaktion, bei der der Commit schon begonnen wurde.

== Was ist eine Transaktion und wie wird Atomizität und Isolation gewahrt?
Wird eine Transaktion erstellt (`TransactionNoBuffering.java`), wird in der HashMap `fileTimestamps` der ursprüngliche Zustand des _zfs directory_ festgehalten (für jede Datei wird der *Timestamp* der letzten Modifikation gespeichert) und ein ZFS Snapshot erstellt. Wird eine Datei verändert (read/write/remove), wird ihr Name zunächst in der Liste `relevantFiles` gespeichert. Beim Commit wird für alle Dateien, die relevant für diese Transaktion sind, verifiziert, dass es keine Konflikte gibt. 
- Ist kein Konflikt aufgetreten, können die Änderungen dieser Transaktion erhalten bleiben und sowohl die Transaktions-Datei, als auch der Snapshot können gelöscht werden.
- Gab es einen Konflikt, wird diese Transaktion durch einen ZFS Reroll zurückgesetzt und die Transaktions-Datei gelöscht.
Zudem wird ein Flag `startedCommit` verwaltet, um Transaktionen zu finden, die noch *nicht vollständig commitet* wurden (wenn die Transaktions-Datei nach einem Crash existiert, jedoch dieses Flag gesetzt ist, ist der Crash während des Commit passiert). Diese Transaktionen sind relevant für die Redo-Operation.

#figure(image("TransactionProblem.drawio.png"), caption: "Am Anfang existiert a.txt noch nicht. Transaktionen A und B starten gleichzeitig, Transaktion B wird erfolgreich commitet, Transaktion A muss durch den Konflikt zurückgesetzt werden und löscht damit auch die Änderungen von Transaktion B.")<transaction_err>

Eine derartige Implementierung gewährleistet zwar Konsistenz und Atomizität, jedoch *keine Isolation*: In der Situation aus @transaction_err werden durch das Zurücksetzen von Transaktion A *auch die Änderungen von Transaktion B* gelöscht, welche gemäß des Durability-Prinzips erhalten bleiben müssten. Wären beide Transaktionen isoliert und sequenziell ausgeführt worden, hätte a.txt am Ende entweder den Inhalt, der in Transaktion A oder B geschrieben wurde; in diesem Fall würde der ZFS Rollback a.txt jedoch komplett löschen. Korrekterweise müsste der Rollback von Transaktion A den Zustand nach dem Abschluss von Transaktion B wiederherstellen, jedoch hat Transaktion A keine Möglichkeit auf diesen zuzugreifen.

Als Alternative, welche besser mit den ACID-Prinzipien klarkommt und zusätzlich nicht auf ZFS angewiesen ist, habe ich daher in `Transaction.java` eine Variante der Transaktion implementiert, welche darauf beruht sämtliche (geplanten) Änderungen zunächst in den Datenstrukturen `writes` und `removes` im Sinne des _Write Ahead Logging_ zu puffern. Erst wenn zum Commit-Zeitpunkt kein Konflikt vorliegt, werden diese Änderungen tatsächlich im _zfs directory_ geschrieben. Kommt es stattdessen zu einem Konflikt, so wird einfach die Transaktions-Datei verworfen und keine Datei im _zfs directory_ wurde von der Transaktion modifiziert. Diese zweite Implementierung bietet zudem eine Referenz, mit der die Performance der ZFS Lösung verglichen werden kann. 

Diese Implementierung hat neben den Limitierungen von _Write Ahead Logging_ (z. B. nur Redo möglich, Änderungen müssen 2x geschrieben werden) zusätzlich den Nachteil, dass die Größe des Logs nicht begrenzt ist und alle geplanten Änderungen aus einer Transaktions-Datei gelesen werden müssen, was vermutlich je nach Situation die Performance deutlich verschlechtern könnte. 

== Aufgabe 2

