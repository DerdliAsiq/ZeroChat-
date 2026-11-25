# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

ZeroChat is a single-module (`:app`) Android messenger with end-to-end encryption. It uses Kotlin, Jetpack Compose and Material3 on Firebase Auth (Google Sign-In) and Firestore, with a local SQLCipher-encrypted Room database. It was originally generated in Google AI Studio (`.aistudio/`, `metadata.json`). The Kotlin namespace is `com.example`; the `applicationId` is `com.aistudio.zerochat.xkvq`.

All user-facing text, comments in the UI and CI step names are in **Turkish**, so write new UI strings in Turkish. Most strings are still hard-coded in Kotlin rather than in `strings.xml`. Keep user-facing copy plain ("Mesaj", "Kişi", "Sil"; not "Zarf", "Düğüm", "İmha"), and never claim a security property the code does not have.

## Build and test

Use the committed wrapper (Gradle 9.3.1, JDK 21):

```bash
./gradlew assembleDebug -x lint                   # what CI builds
./gradlew :app:testDebugUnitTest                  # unit + emulator tests (emulators must be running)
./gradlew :app:testDebugUnitTest --tests "com.example.CryptoProtocolTest"
./gradlew :app:minifyReleaseWithR8                # checks R8 keep rules without signing
npm install                                        # once: firebase-tools + rules-unit-testing
npm run emulators                                  # Firestore :8085 + Auth :9099 (firebase.json)
npm run test:rules                                 # firestore.test.js against the rules
npm test                                           # emulators:exec running rules + Android tests (CI)
```

- `CryptoProtocolTest` is pure Robolectric and needs no emulator. `ZeroChatRepositoryRuleTest` runs the real repository against the emulators. Each test user gets its own repository instance, so a test cannot pass by reading local state.
- `FirestoreEmulatorTestBase` always uses a separately named emulator-only `FirebaseApp`, so tests can never reach the real project.
- **The emulator does not reliably hot-reload rules for the named database.** Restart `npm run emulators` after editing `firestore.rules`, otherwise the Android tests run against stale rules. `firestore.test.js` pushes the rules itself and is not affected.

### Files the build needs that are not in git

- `debug.keystore` (repo root) and `app/google-services.json`. Without the latter, `R.string.default_web_client_id` is missing and compilation fails. CI writes both from the GitHub Secrets `DEBUG_KEYSTORE_B64` and `GOOGLE_SERVICES_JSON_B64`.
- `local.properties` with `sdk.dir`.
- Release signing reads `KEYSTORE_PATH`, `STORE_PASSWORD` and `KEY_PASSWORD`. Do not modify the `signingConfigs` blocks in `app/build.gradle.kts`; AI Studio depends on them.
- Firestore deploys need `firestore.rules`, `firestore.indexes.json` (composite indexes and TTL policies on `expireAt`) and the named database from `firebase.json`.

## Architecture

There is no DI framework and no Navigation component. Wiring is manual through `data/ZeroChatServices.kt` (Firestore for the **named database** `firestore_database_id`, the `IdentityKeyManager`, the vault DAO).

- **`MainActivity.kt`**: `ZeroChatRootApp` shows `AuthScreen` until there is a Firebase user. `ZeroChatAuthenticatedShell` builds the ViewModel in a per-account `ViewModelStore` (`AccountScopeHolder`), so switching accounts clears the previous account's ViewModel and listeners. Tabs are a `currentTab` string; call screens are overlays.
- **`ui/ZeroChatViewModel.kt`**: the single ViewModel.
  - Decryption runs off the main thread (`flowOn(Default)`) with per-envelope caches.
  - A 1 s ticker runs only while TTL messages or typing are visible.
  - Notifications are one-shot `Channel` events.
  - Scheduled messages are sent by an in-process dispatcher plus `data/ScheduledMessageWorker` (WorkManager).
  - Panic mode hides everything and can only be left with the normal PIN.
- **`data/repository/ZeroChatRepository.kt`**: all Firestore I/O.
  - Public calls return `Result` via `firestoreCatching` (never swallows cancellation; logs `FirestoreError` without personal data), or a `Flow` that fails on error.
  - There is no offline or local fallback mode: errors must surface.
  - `userMessage()` in `FirestoreErrorHandler.kt` turns failures into Turkish banner text.
- **`data/local/LocalVaultDatabase.kt`**: Room on SQLCipher. The passphrase is random and wrapped by an Android Keystore key, stored in `noBackupFilesDir`.
  - Schemas are exported to `app/schemas/`.
  - **Every schema change needs a `Migration` and a version bump**; there is no destructive fallback.
  - Stars, pins of rooms, locked rooms, hidden rooms, verified peers and consumed view-once ids are local only.

### End-to-end protocol (V2): `crypto/`

Treat the formats here as wire formats. Changing a context string, AAD layout or HKDF info makes existing data unreadable.

- **Identity** (`IdentityKeyManager.kt`). Each user has a P-256 ECDH key pair (wrapping) and a P-256 ECDSA key pair (signing). They are stored encrypted with an AES-GCM key in the Android Keystore, which works from API 24. The public keys are published in `users/{uid}` (`publicKey`, `signingKey`, `protocolVersion = 2`). A new device or reinstall means a new identity, and old history is not readable there.
- **Room keys** (`CryptoEngine.wrapKey`). Each room key version is 32 random bytes, stored in `rooms/{roomId}/keys/{version}` wrapped for every participant with ECIES (ephemeral ECDH → HKDF-SHA256 → AES-GCM, with context `room|roomId|version|uid`).
  - `ZeroChatRepository.currentRoomKey()` creates a new version automatically when there is none, when membership changed, or when a participant's identity key id changed.
  - Group membership edits rotate the key in the same batch.
- **Messages** (`MessageCodec.kt`). The whole payload JSON `{type, text, sender, meta}` is AES-256-GCM encrypted with AAD `ZC_MSG_V2|roomId|envelopeId|senderId|keyVersion` and ECDSA-signed. `meta` carries media base64, file, location, poll and reply data.
  - The server only sees routing fields: `roomId`, `senderId`, `participantUids`, `keyVersion`, `ttlSeconds`, `expireAt`.
  - Unverifiable messages are dropped, unless the signer key id differs from the sender's current key (reinstall), in which case they are shown with a warning.
- **Previews, annotations, call media and statuses** use the same key with their own AAD (see `MessageCodec`). Reactions, poll votes and pins are per-user encrypted docs in `rooms/{id}/annotations/{envelopeId}_{uid}_{kind}`. Statuses use a per-status key wrapped for the author's contacts.
- **Safety numbers and emojis** (`CryptoEngine.safetyNumber` / `safetyEmojis`) and QR fingerprints are derived from both users' public keys, not their usernames.
- **Legacy V1 data** (`LegacyCrypto.kt`). Old rooms have a plaintext `roomKeyHint` that the V1 key was derived from. These messages are only decrypted for display (marked `isLegacy`) and are never written again.

### Firestore rules and schema

- The collections are `users`, `usernames/{handle}` (unique handle claims), `rooms` (with `keys`, `annotations` and `presence` subcollections), `envelopes`, `statuses` (with `views`) and `calls` (with `media/{uid}`).
- The models are in `data/model/ZeroChatModels.kt`; fields marked `@get:Exclude` are filled by the ViewModel and never stored. The schema description is in `firebase-blueprint.json`.
- The rules are strict allow-lists: `keys().hasOnly`, `diff().affectedKeys().hasOnly`, and server timestamps must equal `request.time`. **A new field means updating rules, model, blueprint and `firestore.test.js` together.** Key invariants the rules enforce:
  - Envelopes must target a room the sender belongs to, with exactly the room's `participantUids`. Only the sender edits or deletes; anyone may delete once `expireAt` has passed.
  - Direct rooms use the id `dm_{uidA}_{uidB}` (sorted uids) and start `PENDING`. Only the non-requester can accept; only the requester may write while pending.
  - Group membership is owner-only and must come with a new key version. Members may only remove themselves.
  - Call status only moves `RINGING → CONNECTED` (callee) or `→ ENDED`.
- Rules deny reads of documents that do not exist when the rule depends on `resource.data`. The repository treats `PERMISSION_DENIED` on the DM existence check as "not found".

### Calls

Calls are signalled on `calls/{id}`. Each side writes encrypted media to `calls/{id}/media/{uid}` about every 2 s (`CALL_CHUNK_MS`): an AAC chunk plus the latest JPEG frame. That stays under Firestore's ~1 write/s per document. Incoming audio plays from an ordered queue. `util/CallForegroundService` (microphone|camera types) keeps the mic and camera available in the background. There is no WebRTC.

### Size limits

One envelope ciphertext is at most 700 000 chars by rule. The client caps media at `MAX_ATTACHMENT_BYTES` (350 KB raw) and voice notes at `MAX_VOICE_NOTE_SECONDS` (100 s); `util/AttachmentAndLocationHelpers.kt` handles both. Attachments are rejected, never truncated.

## Conventions

- Commit messages use Conventional Commits (`feat:`, `fix:`, `build:`, `docs:`), with a `!` for breaking protocol changes.
- Camera code must unbind only its own use cases, never `unbindAll()`, and must not block on `ProcessCameraProvider` futures in `onDispose`.
- Heavy work (base64 decode, image scaling, crypto) stays off the main thread: use `produceState` + `Dispatchers.Default` in Compose and `flowOn` in the ViewModel.
