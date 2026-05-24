# Project Summary

## Project Structure

Here's an overview of the project's file and directory structure:

*   **src**: Contains the main source code of the project.
*   **.git**: Git version control repository.
*   **.run**: Contains run configurations.
*   **.idea**: IntelliJ IDEA project files.
*   **build**: Output directory for build artifacts.
*   **gradle**: Gradle wrapper files.
*   **gradlew**: Gradle wrapper script (for Linux/macOS).
*   **README.md**: Project README file.
*   **.gitignore**: Specifies intentionally untracked files to ignore.
*   **gradlew.bat**: Gradle wrapper script (for Windows).
*   **build.gradle.kts**: Main Gradle build script (Kotlin DSL).
*   **.intellijPlatform**: Related to IntelliJ Platform Plugin development.
*   **gradle.properties**: Gradle project properties.
*   **settings.gradle.kts**: Gradle settings file (Kotlin DSL).

## Build Configuration (`build.gradle.kts`)

*   **Plugins**:
    *   `java`
    *   `org.jetbrains.kotlin.jvm` version `2.1.0`
    *   `org.jetbrains.intellij.platform` version `2.7.1`
*   **Group**: `dev.silverhorn.fca`
*   **Version**: `1.0-SNAPSHOT`
*   **Repositories**: `mavenCentral()`, `intellijPlatform` (with `defaultRepositories()`)
*   **Dependencies**:
    *   `com.fasterxml.jackson.module:jackson-module-kotlin:2.17.2` (for JSON (de)serialization)
    *   `kotlin("test")`
    *   `junit:junit:4.13.2` (test runtime only)
    *   `intellijPlatform` with `IC` version `2025.1.4.1`
    *   `bundledPlugin("Git4Idea")` (provides VCS log APIs)
*   **IntelliJ Platform Configuration**:
    *   `sinceBuild = "251"`
    *   `changeNotes` for version `0.1.0`
*   **JVM Compatibility**: Source and target compatibility set to Java 21.
*   **Kotlin**: JVM toolchain set to 21, `jvmTarget` set to `JVM_21`.

## Settings Configuration (`settings.gradle.kts`)

*   **Plugin Management**: `kotlin("jvm")` version `2.2.20`.
*   **Plugins**: `id("org.gradle.toolchains.foojay-resolver-convention")` version `0.8.0`.
*   **Root Project Name**: `seamless-bitbucket-comments`.

## Project Description (`README.md`)

The project is an IntelliJ-Plugin for seamless Bitbucket Server 9.x Commit-Comment workflows.

### Features

*   **VCS-Log-Badge**: Displays total comments per commit, with mentions of the current user highlighted.
*   **Diff-Viewer-Inlays**: Renders comments as block inlays directly in the diff editor.
*   **Gutter-Icon**: A '+' icon in the gutter allows direct commenting on any line.
*   **CRUD**: Create, edit (with optimistic locking), and delete comments.
*   **Secure**: Tokens are stored securely in the OS keychain (macOS Keychain, Windows Credential Manager, KeePass).

### Project Structure (from README)

*   `src/main/kotlin/.../`
    *   `api/`
        *   `client/BitbucketRestClient.kt`: HTTP-Client (JDK 21, paging, error parsing)
        *   `dto/`: Jackson-DTOs (PagedResponse, CommentDto, etc.)
    *   `domain/`
        *   `CommitComment.kt`: Immutable Domain-Model
        *   `CommitCommentSummary.kt`: Aggregate for Badge-Rendering
        *   `service/`
            *   `MentionParser.kt`: Slug-based @[~slug]-Scanner
            *   `AnchorMapper.kt`: DTO -> Domain & Gutter-Payload-Builder
    *   `platform/`
        *   `settings/`
            *   `BitbucketSettingsState.kt`: PersistentStateComponent (non-sensitive)
            *   `BitbucketConfigurable.kt`: Settings-UI + PasswordSafe-Integration
        *   `CommentCache.kt`: LRU-Cache (500 entries, thread-safe)
        *   `CommitCommentService.kt`: Facade: Network + Cache + Error-Notify
        *   `PluginStartupActivity.kt`: Warmup + whoami-resolution on startup
    *   `ui/`
        *   `diff/`
            *   `CommitDiffExtension.kt`: DiffExtension-EP, orchestrates UI installation
            *   `CommentInlayRenderer.kt`: Block-Inlay-Renderer (comment text + replies)
            *   `GutterCommentIconRenderer.kt`: Gutter-Icon + Comment-Post-Action
        *   `vcslog/`
            *   `CommentCountColumn.kt`: VCS-Log-Column + Badge-Renderer

### Configuration

1.  Access settings via `Settings -> Tools -> Seamless Bitbucket Comments`.
2.  Enter Server-URL and Personal Access Token.
3.  `projectKey` and `repoSlug` are preferably auto-detected from Git remotes.
4.  Manual fallback values are used if Git remotes cannot be parsed.
5.  Token is securely stored in the OS keychain.

### Quickstart

*   `./gradlew test`: Runs unit tests (no IntelliJ Platform required).
*   `./gradlew runIde`: Starts the plugin in a sandbox IDE.

## Conclusion

This project is an IntelliJ Platform Plugin written in Kotlin, targeting Java 21. It integrates with Bitbucket Server 9.x to enhance commit comment workflows within the IDE. It uses Gradle for building and depends on Jackson for JSON processing. The plugin provides features like displaying comment counts in the VCS log, rendering comments directly in the diff viewer, and allowing inline commenting. Security is handled by storing sensitive tokens in the OS keychain.