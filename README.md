# LocalChat

Eine eigenständige Android-App für eine lokale Ollama-KI auf dem Windows-PC. Die Oberfläche ist als eigene, mobile Chat-Erfahrung gestaltet – mit dunklem Design, mehreren Chats, **Neuem Chat**, Verbindungsstatus, Modell- und Servereinstellungen sowie den Modi **Schnell**, **Standard** und **Denken**. Sie verwendet weder ChatGPT-Branding noch Cloud-APIs.

## Was die App kann

- Mehrere lokale Chatverläufe, die auf dem Android-Gerät gespeichert werden
- Native, touchfreundliche Dark-Mode-Oberfläche
- Serverstatus, Zugriffscode und Modellwahl in den Einstellungen
- Schnell / Standard / Denken mit passenden Antwortlängen
- Kompatibel mit dem alten `/chat`-Server aus der ersten LocalChat-Version
- Mit dem neuen Server: Gesprächskontext, Modellliste und serverseitige Modi

## Schnellere KI für Ryzen 5 7530U mit 16 GB RAM

Als schnelle, weiterhin brauchbare Option ist **`qwen3:1.7b`** voreingestellt empfohlen. Es benötigt bei Ollama etwa 1,4 GB statt rund 2,5 GB für `qwen3:4b` und reagiert typischerweise deutlich flüssiger. Das größere `qwen3:4b` bleibt eine gute Wahl für anspruchsvollere Antworten.

Auf dem PC einmalig installieren:

```powershell
ollama pull qwen3:1.7b
```

Danach in der App **Einstellungen → Geladene Modelle anzeigen** und `qwen3:1.7b` wählen. Solange kein Modell in der App ausgewählt ist, verwendet der neue Server automatisch `qwen3:1.7b`, wenn es installiert ist, andernfalls `qwen3:4b`.

Die Modellgrößen und die verfügbaren Qwen3-Varianten sind in der offiziellen [Ollama-Qwen3-Bibliothek](https://ollama.com/library/qwen3) dokumentiert.

## Server am Windows-PC aktualisieren

Kopiere `server/localchat_server.py` anstelle deiner bisherigen Datei `localchat_server.py` nach `Dokumente`. Dann in PowerShell:

```powershell
python -m pip install -r "$env:USERPROFILE\Documents\localchat-ai-\server\requirements.txt"
python "$env:USERPROFILE\Documents\localchat_server.py"
```

Wenn du das Repository nicht nach `Documents\localchat-ai-` geklont hast, installiere die beiden Pakete alternativ mit `python -m pip install Flask requests`.

### Optionaler Zugriffscode für das WLAN

Wenn das Handy zugreifen soll, ist ein Zugriffscode sinnvoll. Er wird im gleichen PowerShell-Fenster gesetzt und anschließend auch in der App eingetragen:

```powershell
$env:LOCALCHAT_TOKEN = "einen-langen-eigenen-code-hier-eintragen"
python "$env:USERPROFILE\Documents\localchat_server.py"
```

Der Code wird nur an deinen LocalChat-Server im lokalen Netz gesendet. Ollama selbst bleibt dabei an `127.0.0.1:11434` gebunden.

## Netzwerk bewusst eng halten

Die App benötigt Klartext-HTTP, weil die Adresse des privaten PCs normalerweise kein öffentliches HTTPS-Zertifikat hat. Das ist ausschließlich für deine konfigurierte LAN-Adresse gedacht; die App öffnet keine Ports.

Falls die Windows-Firewall die Verbindung blockiert, **nicht** die Firewall allgemein deaktivieren und **nicht** Port `11434` öffnen. Erlaube nur TCP-Port `8787` und begrenze die Regel auf die aktuelle IP des Handys. Beispiel – nur wenn dein Handy gerade `192.168.3.21` hat:

```powershell
New-NetFirewallRule -DisplayName "LocalChat – Mein Handy" -Direction Inbound -Protocol TCP -LocalPort 8787 -RemoteAddress 192.168.3.21 -Action Allow
```

Ändert sich die Handy-IP, die alte Regel wieder entfernen und mit der neuen Adresse anlegen. Ein Gast-WLAN kann Geräte trotz korrekter Firewall absichtlich voneinander isolieren; in dem Fall ein privates WLAN verwenden.

## APK bauen

Bei jedem Push auf `main` und über **Actions → Build LocalChat APK → Run workflow** erstellt GitHub Actions die installierbare Debug-APK. Lade nach erfolgreichem Lauf das Artefakt **LocalChat-APK** herunter.

Lokal, sofern Android SDK, Java 17 und Gradle vorhanden sind:

```powershell
gradle :app:assembleDebug
```

Die Datei liegt danach unter `app/build/outputs/apk/debug/app-debug.apk`.
