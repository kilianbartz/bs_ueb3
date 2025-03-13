Kilian Bartz (1538561)
--------------------
Dieses Repo enthält meine Abgabe zu Übung 3, Betriebssysteme, WiSe 2024/25. Enthalten sind

- im Ordner `a1_a3`: 
  - Der Java-Code für mein Command-Line Anwendung zu Aufgabe 1
  - Der Java-Code für die Validierung in Aufgabe 3 (`Validate.java`)
  - ein `makefile` zum Kompilieren und erstellen der Uberjar zur Verwendung in Aufgabe 2
  - das Bash-Skript `validate.sh`, um die Ergebnisse der Szenarien, die ich in der Validierung in Aufgabe 3 betrachtet habe, zu generieren
  - eine Python-Umgebung und zugehöriges Jupyter-Notebook, mit dessen Hilfe die Plots für Aufgabe 3 erstellt wurden.
- im Ordner `a2`: Ein Python-Projekt, das in `main.py` das Brainstorming-Tool für Aufgabe 2 implementiert
- im Ordner `abgabe`: Der Sourcecode für den Ergebnisbericht
- der Ergebnisbericht (`abgabe.pdf`).

Die Python-Umgebungen werden mittels [uv](https://docs.astral.sh/uv/) ("extremely fast Python package and project manager, written in Rust") verwaltet. Nach der [Installation von uv](https://docs.astral.sh/uv/getting-started/installation/) kann z. B. `main.py` mittels des Befehls `uv run python main.py` ausgeführt werden.

Das Java-Projekt wird mittels maven verwaltet. Zur Kompilierung steht ein `makefile` zur Verfügung.

Der Ergebnisbericht wurde mit [typst](https://typst.app/) (auch rust-basiert😃) verfasst.
