# seamless-bitbucket-comments

IntelliJ-Plugin für nahtlose Bitbucket Server 9.x Commit-Kommentar-Workflows.

## Features

- **VCS-Log-Badge** ? Zeigt pro Commit die Gesamtzahl der Kommentare; Mentions des aktuellen Nutzers in Blau.
- **Diff-Viewer-Inlays** ? Kommentare werden als Block-Inlays direkt in den Diff-Editor gerendert (kein Dokumenteneingriff).
- **Gutter-Icon** ? Klick auf das **+**-Symbol einer beliebigen Zeile öffnet ein Eingabefeld zum direkten Kommentieren.
- **CRUD** ? Kommentare erstellen, bearbeiten (PUT mit Optimistic Locking), löschen.
- **Sicher** ? Token wird ausschließlich im OS-Schlüsselbund gespeichert (macOS Keychain, Windows Credential Manager, KeePass).

## Projektstruktur

```
src/main/kotlin/.../
??? api/
?   ??? client/BitbucketRestClient.kt   HTTP-Client (JDK 21, paging, error parsing)
?   ??? dto/                            Jackson-DTOs (PagedResponse, CommentDto, ?)
??? domain/
?   ??? CommitComment.kt                Immutable Domain-Modell
?   ??? CommitCommentSummary.kt         Aggregat für Badge-Rendering
?   ??? service/
?       ??? MentionParser.kt            Slug-basierter @[~slug]-Scanner
?       ??? AnchorMapper.kt             DTO ? Domain & Gutter-Payload-Builder
??? platform/
?   ??? settings/
?   ?   ??? BitbucketSettingsState.kt   PersistentStateComponent (nicht-sensitiv)
?   ?   ??? BitbucketConfigurable.kt    Settings-UI + PasswordSafe-Integration
?   ??? CommentCache.kt                 LRU-Cache (500 Einträge, thread-safe)
?   ??? CommitCommentService.kt         Fassade: Netzwerk + Cache + Fehler-Notify
?   ??? PluginStartupActivity.kt        Warmup + whoami-Auflösung beim Start
??? ui/
    ??? diff/
    ?   ??? CommitDiffExtension.kt      DiffExtension-EP, orchestriert UI-Installation
    ?   ??? CommentInlayRenderer.kt     Block-Inlay-Renderer (Kommentartext + Replies)
    ?   ??? GutterCommentIconRenderer.kt Gutter-Icon + Kommentar-Post-Action
    ??? vcslog/
        ??? CommentCountColumn.kt       VCS-Log-Spalte + Badge-Renderer
```

## Konfiguration

1. **Settings ? Tools ? Seamless Bitbucket Comments**
2. Server-URL und Personal Access Token eintragen.
3. `projectKey` und `repoSlug` werden bevorzugt automatisch aus den Git-Remotes im Workspace erkannt.
4. Fallback: Falls keine Bitbucket-Remote geparst werden kann, werden die manuell hinterlegten Werte verwendet.
5. Der Token wird sicher im OS-Schlüsselbund gespeichert.

## Schnellstart

```bash
./gradlew test       # Unit-Tests (keine IntelliJ-Plattform erforderlich)
./gradlew runIde     # Plugin in einer Sandbox-IDE starten
```
