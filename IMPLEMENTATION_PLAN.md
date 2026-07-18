# Card Wallet — Implementation Plan (v1, Kotlin)

A native Android app that stores a user's cards — debit, credit, ID, passport, insurance, loyalty, membership — in one place, encrypted, entirely on-device. No backend, no account, no data ever leaves the phone in v1.

Signature UI: the liquid-glass floating navbar (already built and verified in this repo under `app/src/main/java/com/example/liquidglassdemo/glass/`) is the app's visual identity.

---

## 1. Assumptions and Requirements

Stated up front per `system-design-rules.md` §2 — architecture follows from these:

| Question | Answer for v1 |
|---|---|
| Users / load | Single user per device. No server. Load is irrelevant — data set is tens to low hundreds of cards. |
| Data | Card records: a few KB each. Total vault well under 1 MB. No growth/partitioning concerns. |
| Consistency | Single writer (the app), single store (local DB). ACID via Room/SQLite transactions. |
| Availability | Offline-first by definition. The app must work with airplane mode on, forever. |
| Latency | Unlock-to-list under ~1s. Decryption of the full vault in memory is acceptable at this data size. |
| Platform | Android only for v1. **minSdk 33** — required by the liquid-glass `RuntimeShader` effects, and conveniently guarantees modern Keystore + BiometricPrompt behaviour everywhere we run. iOS is a future separate codebase (SwiftUI + `.glassEffect()`). |
| Team | Solo developer. Architecture must be operable by one person. |

**Threat model:** the adversary is (a) someone who steals or borrows the phone, (b) a malicious app or backup process reading app storage, (c) shoulder-surfing/screenshots. The adversary is NOT a nation-state with physical chip access — we rely on the Android hardware keystore (StrongBox/TEE) as the trust anchor.

### Non-goals for v1

- No payments, no NFC tap-to-pay (closed platforms; out of scope permanently).
- No cloud sync, no accounts, no server (deferred to v2 as E2E-encrypted sync).
- No card-scanning OCR (nice-to-have; Phase 7 stretch goal, on-device only if built).
- No iOS, web, or desktop version in v1.
- No devices below Android 13 (the glass silently no-ops below 13; rather than ship a degraded fork of the UI, we set minSdk 33 and state it on the listing).

---

## 2. Tech Stack (decided)

Per `agents-guidelines/languages/kotlin-rules.md` + `agents-guidelines/mobile/compose-rules.md`:

| Layer | Choice | Rationale |
|---|---|---|
| Language | Kotlin 2.x (K2), Gradle Kotlin DSL + version catalog | `kotlin-rules.md` §1 mandate; already how this repo is set up (AGP 9.3 / Gradle 9.6 / Kotlin 2.4) |
| UI | Jetpack Compose, **Material 3 (JetBrains CMP artifacts)**, edge-to-edge | `compose-rules.md` mandate; the glass navbar is Compose-native. Material 3 is **scaffolding only** (color scheme, type, `Text`/`Button`/dialogs) — the app's identity is the liquid glass + our design tokens. **Not Material 3 Expressive** (Decision 10): it's an AndroidX-only API surface not mirrored in the CMP artifacts the glass library rides on, and its headline features — spring motion, shape morphs, expressive nav/FAB — are things we already own via the glass and our tokens. Expressive character, where wanted, is hand-crafted in our design system, not imported |
| Liquid glass | `io.github.kyant0:backdrop` 2.0.0 + `shapes` 1.2.0 + ported `glass/` components | Already integrated and verified on device |
| Architecture | UDF: ViewModel → `StateFlow<UiState>` → stateless screens; events up as lambdas | `compose-rules.md` §1, §4–5 |
| DI | Hilt (`hiltViewModel()` at screen level, constructor injection everywhere) | `kotlin-rules.md` §13; matches the examples the Compose rules assume |
| Navigation | Navigation Compose, type-safe serializable routes; args via `SavedStateHandle` | `compose-rules.md` §7 |
| Local DB | Room over SQLite — **ciphertext-only rows** (see §4) | Keeps the entire trust boundary in `data/crypto`; per-record AES-GCM gives integrity + secrecy; SQLCipher noted as optional defence-in-depth later (Decision 2) |
| Key storage | Android Keystore directly (`AndroidKeyStore` provider) — VMK is a **non-exportable hardware-backed AES key**, `setUserAuthenticationRequired(true)` | Stronger than the RN plan's stored-bytes VMK: the raw key never exists in app memory |
| Unlock | `androidx.biometric` `BiometricPrompt` with `CryptoObject` (BIOMETRIC_STRONG), app PIN fallback | Platform-blessed path; CryptoObject binds unlock to actual key use |
| Crypto | `javax.crypto` — AES-256-GCM, `SecureRandom` nonces; PIN verifier via PBKDF2WithHmacSHA256 (`SecretKeyFactory`, platform-native) | Vetted platform primitives, zero extra dependencies; Argon2 (libsodium binding) is the documented upgrade if PIN cracking resistance needs to rise |
| Async | Coroutines + Flow per `kotlin-rules.md` §10–11; repositories main-safe with injected dispatchers | Guidelines mandate |
| Serialization | `kotlinx.serialization` JSON for the encrypted `CardPayload` | Type-safe (de)serialization; validation on decrypt |
| Lint | ktlint + detekt, zero warnings | `kotlin-rules.md` §1 |
| Testing | JUnit + `kotlinx-coroutines-test` + Turbine; Compose `createComposeRule` UI tests; instrumented E2E for the critical journey | `kotlin-rules.md` §14, `compose-rules.md` §13, `testing-rules.md` |
| Builds | Gradle `debug` / `release` with minify + resource shrink; Play App Signing | Standard native pipeline; no EAS equivalent needed |

**Not used, deliberately:** crash reporting SDKs (Sentry/Crashlytics) in v1 — the app's promise is "nothing leaves the device"; revisit with scrubbed config only if crash volume demands it. No analytics, ever, for the same reason. No Firebase.

---

## 3. Security Architecture

The core of the app. Built and verified first (Phase 1), on dummy data, before any card UI exists.

### Key hierarchy

```
Data Encryption Key (DEK)         random AES-256, generated at vault creation
  ├─ encrypts every card record (AES-256-GCM, fresh 96-bit nonce per write)
  ├─ exists in plain form ONLY in memory while the app is unlocked;
  │  zeroed on lock (same exposure class as the decrypted session cache)
  └─ stored at rest ONLY as the two wraps below

Wrap A — hardware wrap            DEK encrypted by the VMK
  └─ VMK: AES-256 generated INSIDE AndroidKeyStore; non-exportable,
     hardware-backed (StrongBox when available, TEE otherwise);
     auth-per-use — every unwrap authorized via BiometricPrompt.CryptoObject
     (BIOMETRIC_STRONG | DEVICE_CREDENTIAL system sheet)

Wrap B — PIN wrap (double layer)  the app-PIN fallback (owner decision 2026-07-17)
  ├─ inner: DEK encrypted with KEK = PBKDF2-HMAC-SHA256(app PIN, per-install
  │  salt, 600k iterations). Wrong PIN ⇒ GCM tag failure — the wrap IS the
  │  verifier; no separate PIN hash is stored.
  └─ outer: that blob encrypted again by a second Keystore key (no user-auth
     requirement, but device-bound and non-extractable). Offline brute force
     against exfiltrated storage is therefore impossible — PIN guessing must
     run on-device through the Keystore, where app backoff applies.

Export passphrase (user-chosen, per export)
  └─ PBKDF2-derived key encrypts the backup file; never stored anywhere.
     (Export path cannot use Keystore keys — backups must be portable.)
```

Consequences of the dual-wrap design (vs. the earlier "PIN never derives a key"
stance, revised by owner decision): the residual weakness is on-device PIN
guessing bounded by app-level backoff (accepted); the gain is self-recovery —
biometric key invalidation (enrollment change, lock-screen reset) leaves Wrap B
intact, so PIN unlock recovers the vault and Wrap A is simply re-created. The
VMK's raw material remains **never readable by app code at all**. Decision 6
re-argued below.

### Rules (enforced throughout, from `security-rules.md` + AGENTS.md §7)

1. **Nothing sensitive outside Keystore/ciphertext.** No card data, keys, or PIN in plain SharedPreferences, DataStore, saved instance state, logs, or navigation routes. Routes carry record IDs only.
2. **App lock:** locked on cold start and on return from background after a timeout (default 60s, configurable). Lock state lives in a session state holder; the root of the nav graph gates centrally (`compose-rules.md` §7 — no per-screen checks). TanStack-equivalent: the in-memory decrypted cache is wiped on lock.
3. **Screen protection:** `FLAG_SECURE` on the activity (blocks screenshots + hides content in the app switcher on Android — one flag does both).
4. **Glass discipline:** the liquid-glass backdrop captures live UI into GPU layers. Glass is applied to **chrome only** (navbar, sheets, buttons) over decorative or masked content — never over a screen region showing revealed card secrets. Card detail with revealed values renders no glass sampling above it.
5. **Clipboard hygiene:** copy actions use `ClipboardManager` with a sensitive-content flag (`EXTRA_IS_SENSITIVE`) and auto-clear after 30s (`CLIPBOARD_CLEAR_MS` constant).
6. **No sensitive logging:** no `Log.*` of card fields anywhere; detekt forbidden-comment/forbidden-method rules keep hot paths clean; release builds strip logging.
7. **Fail securely:** decryption failure → locked error state, never a partial render. Wrong PIN → generic message, exponential backoff after 5 attempts (`MAX_PIN_ATTEMPTS`). GCM auth-tag failure is treated as tamper, not as "skip this record."
8. **Root detection:** detect and warn (banner), don't hard-block.
9. **Validation at the boundary:** all form input validated before it reaches the repository; all data read from the DB re-validated after decrypt via `kotlinx.serialization` strict parsing + domain `require(...)` checks (guards against corruption).
10. **Crypto discipline:** platform `javax.crypto` only, AES-256-GCM with authenticated tags, `SecureRandom` for nonces — never `Random`. Never roll our own primitives. All crypto code lives in one module-internal package (`data/crypto`) — nothing else touches a `Cipher`.
11. **Backups:** `android:allowBackup="false"` + explicit `dataExtractionRules` excluding the DB — the OS must never copy the vault to Google backup; restoring ciphertext without the (non-exportable, device-bound) VMK would brick it anyway. The user-facing export (Phase 5) is the only sanctioned backup path.

---

## 4. Data Model

One Room table. Everything the user typed is inside the ciphertext; only non-identifying metadata is plaintext (needed for ordering/migrations).

```kotlin
@Entity(tableName = "cards")
data class CardRecord(
    @PrimaryKey val id: String,          // UUID
    val schemaVersion: Int,
    val createdAt: String,               // ISO 8601
    val updatedAt: String,
    val nonce: String,                   // base64, unique per write
    val ciphertext: String,              // base64 AES-256-GCM( CardPayload JSON )
)
```

```kotlin
// Decrypted shape — the domain type the whole app works with (kotlin-rules.md §5)
@Serializable
data class Card(
    val id: String,
    val type: CardType,                  // PAYMENT | IDENTITY | TRAVEL | LOYALTY | OTHER
    val title: String,                   // "HDFC Debit", "Passport"
    val fields: List<CardField>,         // ordered label/value pairs, per-type templates
    val color: CardColorToken,           // theme token reference, not a hex value
    val isFavorite: Boolean,
    val createdAt: String,
    val updatedAt: String,
)

@Serializable
data class CardField(
    val label: String,                   // "Number", "Expiry", "CVV", "Passport No."
    val value: String,
    val isMasked: Boolean,               // render as •••• until tapped
)

enum class CardType { PAYMENT, IDENTITY, TRAVEL, LOYALTY, OTHER }
```

Search/filter/sort happen in memory after decrypt — trivial at this scale. The repository decrypts once per unlock into an in-memory session cache exposed as `Flow<List<Card>>`; the cache is wiped on lock. Sorting/filtering live in the ViewModel (`compose-rules.md` §8 — never inline in composition).

---

## 5. Project Structure

Per `compose-rules.md` §2 — feature-first. The existing demo files are migrated or retired as noted in Phase 0.

```text
app/src/main/java/com/cardwallet/
  MainActivity.kt                  // setContent + FLAG_SECURE + root wiring only
  CardWalletApp.kt                 // @HiltAndroidApp
  ui/
    theme/                         // Theme.kt, Color.kt, Type.kt, Spacing.kt — design system (§6)
    components/                    // shared UI: FieldRow, MaskedValue, EmptyState, ErrorView
    glass/                         // LiquidBottomTabs, DampedDragAnimation,
                                   //   InteractiveHighlight, DragGestureInspector (moved from demo)
  navigation/
    NavGraph.kt                    // typed routes, lock gate at the root
    Routes.kt                      // @Serializable route classes (IDs only)
  features/
    lock/
      LockScreen.kt                // stateful wrapper + stateless screen
      LockViewModel.kt
      LockUiState.kt
      components/                  // PinPad
    cards/
      list/    CardListScreen.kt, CardListViewModel.kt, CardListUiState.kt
      detail/  CardDetailScreen.kt, CardDetailViewModel.kt, CardDetailUiState.kt
      form/    CardFormScreen.kt, CardFormViewModel.kt, CardFormUiState.kt
      components/                  // CardTile, CategoryChips, ColorPicker
    settings/
      SettingsScreen.kt, SettingsViewModel.kt, SettingsUiState.kt
    backup/
      export/import logic + passphrase KDF + share-sheet integration
  data/
    crypto/                        // VaultCipher (the ONLY code touching javax.crypto),
                                   //   KeystoreManager (VMK create/load), PinVerifier (PBKDF2)
    db/                            // Room database, DAO, migrations
    repo/                          // CardRepository: encrypt-on-write, decrypt+validate-on-read,
                                   //   in-memory session cache, main-safe suspend API
    session/                       // SessionStateHolder: locked/unlocked, lastActiveAt
    settings/                      // non-sensitive prefs via DataStore
  domain/                          // Card, CardField, CardType, CardColorToken
```

File limits per AGENTS.md: soft 300 / hard 500 lines. One screen per file.

---

## 6. Design System

Per AGENTS.md §6 and `compose-rules.md` §9 — created before any UI work (Phase 0):

- `ui/theme/` is the single source of truth: Material 3 `ColorScheme` (light + dark from day one), `Typography`, `Shapes`, plus a custom theme object via `CompositionLocal` for tokens Material doesn't cover (card accent colors, glass surface alphas, spacing scale, animation durations).
- No raw hex/`dp`/`sp` values in feature composables — theme roles and tokens only. Card accent colors are named tokens (`CardColorToken.BLUE`…), so `Card.color` stores a token key, exactly as the RN plan intended.
- The glass components' tunables (accent color, container color, blur/lens magnitudes) become theme tokens — currently they're parameters with hardcoded defaults in `glass/`.
- Accessibility baked in per `compose-rules.md` §11: contentDescriptions, ≥48dp touch targets, `sp` for text, TalkBack pass on list + detail, previews at large font scale.

---

## 7. Build Phases

Each phase ends with the app building, running, and its acceptance checks passing. Later phases don't start until the prior phase's checks pass.

### Phase 0 — Rescaffold and foundations
- [ ] `git init`; **add `AGENTS.md`, `agents-guidelines/`, `local.properties`, `*.apk` to `.gitignore` before the first commit** (AGENTS.md privacy rule).
- [ ] Rename package `com.example.liquidglassdemo` → `com.cardwallet` (new `applicationId`); restructure to §5 layout. Demo files: `glass/` moves to `ui/glass/` unchanged; `PageContent.kt`/`TabIcons.kt`/demo assets retired (the navbar stays, exercised by the real app shell).
- [ ] Add Hilt, Navigation Compose, Room (+ ksp), `kotlinx.serialization`, `androidx.biometric`, DataStore to the version catalog.
- [ ] ktlint + detekt wired into the build; zero-warning baseline.
- [ ] `ui/theme/` design system (light + dark) — first, before any screen.
- [ ] Nav graph with typed routes; empty screens (Lock, Cards, Settings) navigable behind the liquid-glass navbar; lock gate stubbed open.
- **Accept:** app builds and runs with the glass navbar over placeholder screens; ktlint/detekt clean; `.gitignore` verified before commit #1.

### Phase 1 — Security core (dummy data only)
- [ ] `data/crypto/KeystoreManager`: VMK create-on-first-run inside AndroidKeyStore (StrongBox with TEE fallback), `setUserAuthenticationRequired(true)`, invalidated-key detection.
- [ ] `data/crypto/VaultCipher`: AES-256-GCM encrypt/decrypt with auth-tag verification; unit tests incl. tamper detection (flipped ciphertext bit must throw) and nonce-uniqueness property test.
- [ ] `data/crypto/PinVerifier`: PBKDF2 set/verify with per-install salt; attempt counter + exponential backoff.
- [ ] Lock feature: `BiometricPrompt` (BIOMETRIC_STRONG + DEVICE_CREDENTIAL) with `CryptoObject`, PIN pad fallback, backoff UI.
- [ ] `data/session/SessionStateHolder` + root nav gate: cold start → locked; background > timeout → locked; decrypted cache cleared on lock.
- **Accept:** cannot reach any screen while locked; VMK survives app restart; biometric and PIN paths both unlock; biometric-enrollment change invalidates the key and lands in the recovery flow, not a crash; crypto tests green.

### Phase 2 — Vault storage layer
- [ ] `data/db`: Room database, `cards` table, migration scaffolding keyed on `schemaVersion`.
- [ ] `data/repo/CardRepository`: `list / get / create / update / remove` — encrypt on write, decrypt+validate on read; fresh nonce every write; writes in Room transactions; main-safe with injected dispatchers; exposes `Flow<List<Card>>` from the session cache.
- **Accept:** CRUD round-trips through real encryption; DB file pulled and inspected manually contains no plaintext card data; repository unit tests green (in-memory Room).

### Phase 3 — Card UI
- [ ] Card list: `LazyColumn` with stable keys, card-styled tiles, category filter chips, search, favorites first, empty state — behind the liquid-glass navbar (list content scrolls under the glass; tiles show titles/types only, never secret values).
- [ ] Card detail: field rows, masked values (tap to reveal), copy-with-auto-clear, edit/delete (delete confirms). No glass overlays this screen (§3 rule 4).
- [ ] Add/edit form: per-type field templates, token-based color picker, validation with inline errors, IME-aware.
- [ ] Loading / error / empty states on every screen per `compose-rules.md` §10; previews for all stateless screens (light + dark).
- **Accept:** full create→list→view→copy→edit→delete journey works on device; TalkBack pass on list + detail; recomposition sanity-checked with Layout Inspector.

### Phase 4 — Hardening
- [ ] `FLAG_SECURE` on the activity; verify screenshots blocked and switcher shows blank.
- [ ] Auto-lock timeout setting (Settings screen); clipboard auto-clear + `EXTRA_IS_SENSITIVE` wired everywhere.
- [ ] Root detection warning banner.
- [ ] PIN change + biometric toggle flows in Settings.
- [ ] `allowBackup=false` + `dataExtractionRules` verified (adb backup attempt yields nothing).
- [ ] Log audit: grep + detekt rule — zero logging of card fields; release build strips `Log` calls.
- **Accept:** screenshot blocked; switcher blank; app locks after timeout; clipboard clears; backup extraction empty.

### Phase 5 — Backup export/import
- [ ] Export: serialize vault → encrypt with PBKDF2-derived key from a user passphrase (min strength enforced) → share as file via share sheet (SAF).
- [ ] Import: pick file → passphrase → validate + decrypt → merge strategy (skip/replace on ID collision) → report.
- [ ] Explicit UX copy: "we cannot recover this file without your passphrase."
- **Accept:** export on device A, import on device B (or after reinstall) restores the vault byte-perfect; wrong passphrase fails cleanly; export file inspected contains no plaintext.

### Phase 6 — Release
- [ ] Release build: minify + resource shrink; ProGuard rules audited (crypto/Room/Hilt keep rules correct); Play App Signing.
- [ ] App icon, splash, store listing assets; privacy policy page ("all data stays on your device; we collect nothing") + Play data-safety form to match.
- [ ] Instrumented E2E: launch → unlock → add card → relaunch → unlock → verify card.
- [ ] Play internal testing track (note the 12-testers/14-days rule for personal accounts — start this clock at end of Phase 4, in parallel).
- [ ] Store submission.
- **Accept:** release build passes review; E2E green on a physical device.

### Phase 7 — Stretch (post-release, separate tasks)
- On-device card OCR (CameraX + ML Kit text recognition, strictly offline).
- v2 discovery: E2E-encrypted sync design doc (ciphertext-only server) — an ADR before any code.
- iOS: separate SwiftUI codebase reusing this plan's §3–4 design; `.glassEffect()` for the navbar.

---

## 8. Testing Strategy

Per `kotlin-rules.md` §14, `compose-rules.md` §13, and `testing-rules.md` (read before writing tests):

- **Unit:** crypto (round-trip, tamper, nonce uniqueness), KeystoreManager (instrumented — Keystore has no JVM fake worth trusting), PinAttemptTracker (backoff schedule), PinKdf (determinism/divergence), repository (in-memory Room), session holder, serialization schemas. `runTest` + injected `TestDispatcher`s; Turbine for flows.
- **Compose UI tests:** stateless screens with constructed states — lock screen (wrong PIN → error, backoff), card form (validation), list (empty/data/error). Semantics-based queries; no DI in tests.
- **E2E (instrumented):** the one critical journey (Phase 6), on a physical device — Keystore + biometric behaviour is not faithfully emulated.
- Security-critical code (crypto, keystore, repository) targets exhaustive coverage; UI targets critical flows.

---

## 9. Decision Log (mini-ADRs)

| # | Decision | Alternatives considered | Why |
|---|---|---|---|
| 1 | Local-only vault for v1 | E2E-encrypted sync backend | Zero server attack surface, no compliance scope, fastest to ship; sync deferred to v2 |
| 2 | Ciphertext rows in plain Room/SQLite | SQLCipher | Original RN rationale (managed-workflow friction) is gone, but the stronger argument stands: per-record AEAD gives integrity + secrecy with the trust boundary in one package; SQLCipher would encrypt again with a key we'd have to manage outside the Keystore-gated path. Add it later as defence-in-depth only if a concrete threat justifies it |
| 3 | Platform `javax.crypto` + Keystore | Tink, libsodium bindings | Zero added dependencies, hardware-backed, BiometricPrompt-integrated; Tink is the documented fallback if we ever need primitives the platform lacks |
| 4 | Repository session cache as `Flow` + ViewModel state | A query/cache library | TanStack Query has no Kotlin equivalent worth adopting at this scale; a repository-owned in-memory cache wiped on lock is one class and fully testable |
| 5 | No crash reporting/analytics in v1 | Crashlytics/Sentry with scrubbing | "Nothing leaves the device" is the product promise; deviation accepted consciously |
| 6 | VMK as non-exportable Keystore key | Random bytes in encrypted prefs (RN plan's design) | Hardware-backed and never present in app memory; biometric/credential gating enforced by the OS at key-use time, not by app logic. Cost: key is device-bound — which is exactly why Phase 5 export exists |
| 7 | Hilt for DI | Koin, manual DI | Compose rules' examples assume it; compile-time validation; solo-dev-friendly given the small graph |
| 8 | minSdk 33 | minSdk 26 + glass fallback UI | The glass is the product's visual identity; shipping a degraded fork doubles UI test surface for a shrinking device cohort. Conscious reach trade-off, stated on the listing |
| 9 | PBKDF2 (600k) for the PIN wrap KEK + export KDF | Argon2 via libsodium | Platform-native (`SecretKeyFactory`), no new deps. Since the PIN wrap's outer layer is a device-bound Keystore key, PIN guessing cannot run off-device, so PBKDF2 cost is a secondary defence, not the primary one. Argon2 is the documented upgrade path for the export passphrase (which has no device-bound outer layer) if needed |
| 10 | Material 3 (CMP), **not** Material 3 Expressive | AndroidX Compose + M3 Expressive | Expressive is an AndroidX-only surface not mirrored in the JetBrains CMP artifacts the glass library depends on; switching stacks risks the glass integration for features (spring motion, shape morphs, expressive nav/FAB) we already own via the glass + our tokens. Material 3 is scaffolding; expressive character is hand-crafted (owner decision 2026-07-18) |
| 11 | Dual-wrap DEK: hardware wrap + PIN wrap | Single Keystore-gated key with device-credential fallback (the "PIN never derives a key" design) | Reverses the original plan on owner decision: a real app-PIN unlock path can't be honestly built on the Keystore alone (correct app-PIN still needs a second OS credential prompt — two PINs), so the PIN gets its own wrap. Residual weakness is on-device PIN guessing bounded by app backoff (accepted); gains are a true PIN fallback and self-recovery when the hardware wrap is invalidated |

---

## 10. Risks

| Risk | Mitigation |
|---|---|
| Keystore key invalidated (biometric re-enrollment, OS "clear credentials", vendor quirks) | Detect `KeyPermanentlyInvalidatedException` on unlock → guided recovery via backup import; test this path explicitly in Phase 1; onboarding pushes an early export |
| User loses phone with no backup | Phase 5 export is promoted in onboarding + periodic reminder |
| Glass backdrop captures sensitive pixels into GPU layers | §3 rule 4: glass over chrome/masked content only; card detail (revealed values) renders glass-free; verified during Phase 3 review |
| minSdk 33 excludes older devices | Accepted (Decision 8); stated on the Play listing |
| ProGuard/R8 breaking crypto or Room reflection in release | Phase 6 audits keep rules; E2E runs against the release build |
| Play review friction (financial-data category) | Local-only + "we collect nothing" data-safety form; no payment functionality claimed |
| Play 14-day/12-tester requirement delays launch | Start internal testing at end of Phase 4, in parallel with Phase 5 |
| OEM Keystore implementation bugs (StrongBox flakiness) | StrongBox → TEE fallback at key creation; instrumented tests run on physical hardware |
