# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

ZeroChat is a single-module (`:app`) Android messenger: Kotlin, Jetpack Compose and Material3 on top of Firebase Auth and Firestore, with a local Room database. It was generated and iterated in Google AI Studio (see `.aistudio/`, `metadata.json`, and the comments in `app/build.gradle.kts`). The Kotlin namespace is `com.example`, while the `applicationId` is `com.aistudio.zerochat.xkvq`.

All user-facing text, banners, code comments and CI step names are in **Turkish**. Write new UI strings in Turkish. Per `.aistudio/artifacts/brain/*/implementation_plan.md`, user-facing copy should avoid technical crypto jargon: use "Mesaj", "Kişi" and "Sil" rather than "Zarf", "Düğüm" and "İmha".

## Build and test

No `gradlew` script is committed, only `gradle/wrapper/gradle-wrapper.properties`. Use a system Gradle 9.3.1 with JDK 21, which is what CI uses (or create a wrapper first with `gradle wrapper`).

```bash
gradle assembleDebug -x lint                      # what CI runs (it also adds --stacktrace --no-daemon)
gradle :app:testDebugUnitTest                     # JVM/Robolectric unit tests
gradle :app:testDebugUnitTest --tests "com.example.ExampleRobolectricTest"
gradle :app:testDebugUnitTest --tests "*ZeroChatRepositoryRuleTest.crossUserRoomAccess*"
gradle :app:connectedDebugAndroidTest              # instrumented tests (needs a device/emulator)
```

- The `Test` tasks set `failFast = true` and a 2-minute timeout.
- `ZeroChatRepositoryRuleTest` extends `FirestoreEmulatorTestBase`, so it needs the Firebase emulators from `firebase.json` running: Firestore on `127.0.0.1:8085` and Auth on `127.0.0.1:9099` (for example `firebase emulators:start --only firestore,auth`). Without them these tests time out. `ExampleRobolectricTest` (AES round-trip) doesn't need them.
- `firestore.test.js` tests the security rules with `@firebase/rules-unit-testing` and `node:test`. No `package.json` is committed. Run it from the repo root (it reads `./firestore.rules`) against a running Firestore emulator (`FIRESTORE_EMULATOR_HOST`, default `127.0.0.1:8085`).

### Files the build needs that are not in git

- **`debug.keystore`** at the repo root. The `debugConfig` signing config hard-codes `${rootDir}/debug.keystore` (it is gitignored), so debug builds fail without it.
- **`app/google-services.json`**. The plugin is set to `WARN` when it's missing, but `AuthScreen.kt` uses `R.string.default_web_client_id`, which is generated from this file, so compilation fails without it.
- **`.env`** (optional). The Secrets Gradle Plugin reads `.env` and falls back to `.env.example`.
- Release signing reads the `KEYSTORE_PATH`, `STORE_PASSWORD` and `KEY_PASSWORD` environment variables.
- Do not modify the `signingConfigs` blocks or their assignments in `app/build.gradle.kts`. The in-file comments say AI Studio requires them.

### CI gotcha: the workflow overwrites the source tree

`.github/workflows/build-apk.yml` is about 350 KB. **Don't read it whole**: lines 1–38 and the last ~15 lines are the real steps. Between them is a base64 + xz + tar archive that CI extracts **over the checkout** before running `gradle assembleDebug`. The archive contains `debug.keystore` and `app/google-services.json`, but it also contains a full copy of the Kotlin sources, resources, Gradle files and `firestore.rules`. CI therefore builds the archived snapshot, not necessarily the committed files.

As of commit `5cf5848` the snapshot matches the repo exactly, and past feature commits regenerated it together with the code. If you change source files without regenerating the archive, CI will silently build the old code. Ask the user how they want it handled before editing that file.

## Architecture

There's no DI framework and no Navigation component; the wiring is manual.

- **`MainActivity.kt`**: `ZeroChatRootApp` picks the identity. It uses either a Firebase user (Google Sign-In through Credential Manager in `ui/auth/AuthScreen.kt`) or a "local node" session stored in SharedPreferences (`zerochat_local_node_session`). `ZeroChatAuthenticatedShell` builds the ViewModel by hand with `viewModelFactory`, keyed by uid. Tabs are a plain `currentTab` string (`CHATS` / `STATUSES` / `CALLS` / `VAULT`), call screens are overlays, and `FLAG_SECURE` is toggled from ViewModel state.
- **`ui/ZeroChatViewModel.kt`** (~1900 lines): the single ViewModel for the whole app. It decrypts envelopes into `DecryptedMessageItem` and runs the self-destruct ticker, scheduled-message dispatch, offline outbox (failed sends are queued into Room automatically), vault PIN, panic mode, calls and notification events.
- **`data/repository/ZeroChatRepository.kt`**: all Firestore I/O. Calls return `Result` (`runCatching`) or `Flow`. It has a **dual mode**:
  - If `auth.currentUser == null` and a `fallbackUserId` was passed (local-node mode), every operation runs against in-memory `MutableStateFlow`s instead of Firestore.
  - In cloud mode, writes are also mirrored optimistically into those local flows, and observers merge local-only items with cloud results.
  - New operations should keep both paths.
  - The shell always passes `currentUserId` as `fallbackUserId`. As a result, some observers' `.catch` blocks fall back to local state instead of surfacing Firestore errors, so check logcat (`FirestoreError` tag, from `handleFirestoreError`) when data seems missing.
- **Named Firestore database**: the ID is in `res/values/firebase_applet_config.xml` (`firestore_database_id`) and `firebase.json`. Always use `FirebaseFirestore.getInstance(databaseId)`, never the default instance.
- **`data/local/LocalVaultDatabase.kt`**: all Room entities and the DAO live in one file. It is at version 4 with `fallbackToDestructiveMigration(true)` and `exportSchema = false`, so bumping the version wipes local data (vault PINs, outbox, cached messages, verified peers). The Room compiler runs through KSP.
- **`util/`**: biometrics, the CameraX QR scanner and photo capture, the live video-call preview, base64 media encoding, `VoiceAudioManager` (AAC recording), and `ZeroChatNotificationHelper`.

### Crypto model (`crypto/CryptoEngine.kt`)

Treat these formats as wire formats. Changing any derivation string breaks decryption of existing envelopes.

- The message key is SHA-256 of `"ZEROCHAT_E2EE_V1::" + roomKeyHint`, or of `"ZEROCHAT_E2EE_RATCHET_V2::<hint>::STEP_n"` when `ratchetStep > 0`. Messages use AES-256-GCM with a 12-byte IV. The `roomKeyHint` is stored in plaintext on the `rooms` document, so any participant who can read the room can derive the key.
- Direct rooms are deterministic: the room ID is `"dm_" + sha256(sortedUids)` and the key hint is `computeDirectRoomKeyHint(uidA, uidB)`. Group rooms get random IDs.
- Connection-request state is encoded inside `roomKeyHint` as `REQ_FROM::<username>::<hint>` or `REQ_REJECTED::...`. `ChatRoom.effectiveRoomKeyHint` and `CryptoEngine.stripKeyHintPrefix` strip the prefix. Use the helpers on `ChatRoom` (`isPendingRequest`, `isIncomingRequestFor`, etc.) rather than parsing it yourself.
- `rooms.lastCipherPreview` uses `encryptPreviewPacket`, with the IV embedded. `decrypt()` falls back to the preview format when `iv` is blank.
- `encryptedMeta` is plaintext JSON despite its name. It holds `ratchetStep`, `readByUids`, reply info, poll data, view-once/spoiler flags and file info.
- The safety emojis and safety number come from both users' sorted, lowercased **usernames** (`"PAIR::a::b"` in `deriveCanonicalPeerSafetyEmojis` / `deriveCanonicalSafetyNumber`), not from their public keys, so both devices show the same values.

### Media and calls

There's no Cloud Storage and no WebRTC. Images, files and voice notes are base64 strings inside envelope documents (`encodeDocumentUriToBase64` caps files at 122,880 bytes). Calls are `calls/{id}` documents with `status` `RINGING` → `CONNECTED` → `ENDED`. Live audio and video frames are pushed as base64 chunks into `callerAudioB64` / `calleeAudioB64` / `callerVideoB64` / `calleeVideoB64` with a `mediaSeq` counter. Stay within the rule limits: 262,144 chars for envelope `ciphertext` / `encryptedMeta`, and 131,072 per call chunk field.

## Firestore schema and rules

- The collections are `users`, `rooms`, `envelopes`, `statuses` and `calls`. Kotlin models are in `data/model/ZeroChatModels.kt`, the schema description in `firebase-blueprint.json`, and the rules in `firestore.rules`.
- The rules are strict allow-lists. Creates check `keys().hasAll([...])` and `hasOnly([...])`, updates check `diff().affectedKeys().hasOnly([...])`, and fields are checked against enums (for example `messageType in ['TEXT','IMAGE','VOICE','POLL','LOCATION','FILE']`). **When you add a field, enum value or updatable key, update `firestore.rules`, the model and `firebase-blueprint.json` together**, or writes fail with `PERMISSION_DENIED`.
- Access is granted by `request.auth.uid in participantUids`. List queries must filter with `whereArrayContains("participantUids", uid)` or the rules reject them.

## Conventions

- Commit messages use Conventional Commits (`feat:`, `refactor:`, `ci:`, `build(ci):`).
- UI lives in large multi-composable files, one per area, under `ui/screens/`. The theme palette (`ObsidianBlack`, `CarbonSurface`, `NeonCyberGreen`, `CyberCyan`) is in `ui/theme/Color.kt`, and the bundled fonts are in `res/font/`.
- Dependencies are managed through `gradle/libs.versions.toml`. Several catalog entries (Retrofit, Moshi, OkHttp, Navigation, Firebase AI, App Check) are declared but commented out in `app/build.gradle.kts`.
