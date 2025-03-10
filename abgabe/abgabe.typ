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
#let header = {
  
}

#v(10pt)
#align(center, text(17pt)[
  *Übungsblatt 3: Optimistische Nebenläufigkeit mit ZFS-Snapshots*
])
#v(10pt)

#notice([Der zugehörige Code befindet sich im folgenden Github Repository: https://github.com/kilianbartz/bs_ueb3.])

= Aufgabe 1
Zur Bearbeitung dieser Aufgabe habe ich die im Ordner `a1_a3` befindliche Java Bibilothek _Transaction_ erstellt. Um eine möglich breite Kompatibilität zu anderen Programmen zu bieten, stellt die Anwendung ein *zustandsloses* _Command Line Interface_ bereit. Implementiert sind die folgenden 5 Operationen:
- *transaction* start _name_: Startet eine Transaktion, speichert den Ausgangszustand des _zfs directory_#footnote([_zfs directory_ meint ein Verzeichnis innerhalb eines zfs pools (hier werden später die Operationen innerhalb der Transaktionen persistiert) und ist $eq.not$ _working directory_ (Hilfsverzeichnis für temporäre Transaktions-Dateien)]) und legt einen ZFS Snapshot an.
- *transaction* Commit _name_: Falls keine Konflikte vorliegen (also seit Start der Transaktion keine Änderungen im _zfs directory_ durchgeführt wurden), persistiere die writes innerhalb der Transaktion und lösche anschließend den zugehörigen Snapshot und die Transaktions-Datei. Andernfalls: Führe ein Rollback zum Transaktions-Start aus.
- *write* _name_ _datei_ _inhalt_: Plane _datei_ mit _inhalt_ als Teil der Transaktion _name_ zu schreiben. Diese Änderung wird erst beim Commit persisitiert.
- *read* _name_ _datei_: Lese _datei_ als Teil der Transaktion _name_. Hat sich der Zustand des _zfs directory_ seit Beginn der Transaktion geändert, führe ein Rollback durch.
- *remove* _name_ _datei_: Plane _datei_ als Teil der Transaktion _name_ zu löschen. Diese Änderung wird erst beim Commit persisitiert.
- *redo*: Vervollständige den Commit jeder Transaktion, bei der der Commit schon begonnen wurde.

== Was ist eine Transaktion und wie wird Atomizität und Isolation gewahrt?
Zur Gewährleistung der Atomizität funktionieren meine Transaktionen so, dass sämtliche Änderungen (read oder remove) in entsprechenden Datenstrukturen innerhalb der Transaktion gepuffert werden. Wird eine Transaktion angelegt (gestartet), wird eine entsprechende Transaktions-Datei im _working directory_ angelegt. Da meine Anwendung zustandslos ist und nicht permanent im Hintergrund mitläuft, muss sie immer, wenn die Transaktion modifiziert wird (z. B. ein write geplant wird), entsprechend aktualisiert werden. 

Neben den geplanten writes/removes enthält die Transaktion(s-Datei) noch weitere Informationen:
+ Eine HashMap, die für jede Datei, die sich zum Transaktions-Start im _zfs directory_ befindet, den Timestamp der letzten Modifikation speichert. Mithilfe dieser Timestamps werden Konflikte festgestellt.
+ Eine Flag `startedCommit` um Transaktionen zu finden, die noch nicht vollständig commitet wurden (wenn die Transaktions-Datei nach einem Crash existiert, jedoch dieses Flag gesetzt ist, ist der Crash während des Commit passiert). Diese Transaktionen sind relevant für die Redo-Operation.

