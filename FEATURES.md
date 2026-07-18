# Card Wallet — Feature Requirements

Product requirements: **what** each feature does and **why**. Build order and
technical architecture live in `IMPLEMENTATION_PLAN.md`; each feature maps to its
phase. Requirement IDs (`F1.2`) exist so commits, tests, and reviews can reference
them.

Decisions locked with the owner (2026-07-17): card-shaped tiles for the list;
add-card as a **detached circular glass + button beside the navbar** (the capsule
holds tabs only, so it scales to future tabs); PIN mandatory at onboarding;
theme = system default with manual override.

---

## F1 — Onboarding

**Purpose:** first launch ends with a usable, secured, empty vault — and the user
understanding the one promise that defines the app.

**User stories**
- As a new user, I understand immediately that my data never leaves the device.
- As a new user, I cannot end up with a vault that has no way back in.

**Functional requirements**
- F1.1 Welcome screen: the local-only promise in one sentence, no permission
  requests, no account, no skippable-fine-print.
- F1.2 Secure-lock check: if the device has no secure lock screen (PIN/pattern/
  password), explain why one is required and deep-link to system settings.
  Onboarding cannot proceed without it (the Keystore VMK requires
  user-authentication gating).
- F1.3 PIN setup (mandatory): 6 digits, entered twice to confirm; mismatch clears
  and restarts entry. PIN exists before the vault does.
- F1.4 Biometric opt-in: offered only when hardware exists and biometrics are
  enrolled; skippable; state clearly that PIN remains the fallback.
- F1.5 Completion lands on the empty Cards tab (F4.8 empty state). Onboarding never
  reappears once completed.
- F1.6 Backup nudges do NOT appear during onboarding (nothing to back up yet) —
  see F7.6.

**Security requirements**
- F1.7 VMK is generated during onboarding, inside AndroidKeyStore, after the
  secure-lock check passes (plan §3).
- F1.8 The PIN itself is never stored — verifier only (PBKDF2), created here.

**UI states:** secure-lock missing (blocking explainer); biometric unavailable
(step auto-skipped, not shown as an error).

**Acceptance criteria:** fresh install on a device without a lock screen is blocked
with guidance; with a lock screen, welcome → PIN → biometric-offer → empty vault in
under a minute; kill + relaunch after onboarding goes to the lock screen, not
onboarding.

**Phase:** 1 (security steps), 3 (visual polish of welcome/empty states).

---

## F2 — Lock & Unlock

**Purpose:** the vault is inaccessible without the user proving presence, every
time the app is cold-started or left idle.

**User stories**
- As a user, unlocking feels instant: one glance / one touch.
- As a user with failed biometrics (wet hands), the PIN always gets me in.
- As a thief with the phone in hand, I get nothing, and guessing is punished.

**Functional requirements**
- F2.1 Cold start always lands on the lock screen; biometric prompt fires
  automatically when enabled; "Use PIN" is always visible.
- F2.2 Return from background after the auto-lock timeout (F6.1) → locked.
- F2.3 Wrong PIN: generic error copy only. After `MAX_PIN_ATTEMPTS` (5),
  exponential backoff with a visible countdown timer; input disabled during
  backoff.
- F2.4 Locking wipes the in-memory decrypted cache (repository session cache).
- F2.5 Biometric-enrollment change invalidates the hardware wrap → the app
  detects `KeyPermanentlyInvalidatedException`, tells the user to unlock with
  PIN (the PIN wrap survives invalidation), and disables biometric unlock until
  it is re-enabled from Settings — never a crash or silent failure.

**Security requirements**
- F2.6 The lock gate is enforced centrally at the app root — no per-screen
  checks (compose-rules §7).
- F2.7 Two unlock paths to the same DEK (plan §3): biometric/device-credential
  authorizes the Keystore-gated hardware wrap via BiometricPrompt.CryptoObject;
  the app PIN opens the double-layer PIN wrap (PBKDF2 inner + device-bound
  Keystore outer, so PIN guessing cannot happen off-device).

**UI states:** locked (default), authenticating, backoff countdown, key-invalidated
recovery.

**Acceptance criteria:** no screen except lock/onboarding is reachable while
locked; both unlock paths work on a physical device; 5 wrong PINs triggers visible
backoff; enrollment change lands in recovery, not a crash.

**Phase:** 1.

---

## F3 — Vault & Encryption

**Purpose:** invisible to the user; the guarantees every other feature stands on.

**Functional requirements**
- F3.1 All card content is stored as AES-256-GCM ciphertext rows; only
  non-identifying metadata (id, timestamps, schema version, nonce) is plaintext.
- F3.2 Fresh 96-bit nonce on every write; auth-tag verified on every read.
- F3.3 Tamper or corruption on read → the record is treated as compromised: shown
  as an unreadable-record error entry, never partially rendered; the rest of the
  vault still loads.
- F3.4 Decrypt-once-per-unlock into an in-memory cache; search/filter/sort operate
  on the cache; cache is wiped on lock (F2.4).

**Acceptance criteria:** pulling the DB file off a device shows zero plaintext card
data; a flipped ciphertext bit fails loudly in tests and in the UI per F3.3.

**Phase:** 1 (cipher), 2 (storage + repository).

---

## F4 — Card Management

**Purpose:** the product. Store any card, find it fast, read it safely.

**User stories**
- As a user, I add my debit card in under a minute using a template.
- As a user at a counter, I find and read a number in seconds, one-handed.
- As a user, my most-used cards are always on top.

**Functional requirements — list (Cards tab)**
- F4.1 Card-shaped tiles: full-width, credit-card proportions, accent color, title,
  type icon, favorite marker. Tiles show titles/types only — never secret values.
- F4.2 Ordering: favorites first, then most recently updated.
- F4.3 Search across card titles and field labels (never rendering secret values in
  results).
- F4.4 Filter chips by type: Payment / Identity / Travel / Loyalty / Other.
- F4.8 Empty state: friendly "add your first card" pointing at the + button.

**Functional requirements — detail**
- F4.5 Field rows per template; masked fields render as •••• until tapped
  (tap-to-reveal, re-masks on screen exit).
- F4.6 Copy button per field → clipboard with `EXTRA_IS_SENSITIVE`, auto-cleared
  after 30s (F6 constants).
- F4.7 Edit and delete; delete requires confirmation.

**Functional requirements — add/edit form**
- F4.9 Type picker first; the type's template pre-populates ordered fields:
  - Payment: Number*, Expiry, CVV*, Cardholder name, Notes
  - Identity: ID number*, Full name, Issued, Expires, Notes
  - Travel: Document no.*, Full name, Nationality, Issued, Expires, Notes
  - Loyalty: Member ID, Program name, Notes
  - Other: starts empty
  (* = masked by default; masking is per-field toggleable)
- F4.10 Custom fields: add, remove, rename, reorder on any card.
- F4.11 Accent color picker from the theme's named card-color tokens.
- F4.12 Validation: title required; at least one field with a value; inline errors;
  the form never silently drops input (IME-aware, state survives rotation).

**Security requirements**
- F4.13 The card detail screen renders no glass sampling above revealed values
  (plan §3 rule 4); tiles and list are safe (no secrets rendered).

**UI states:** list loading/empty/error; detail unreadable-record (F3.3); form
validation errors.

**Acceptance criteria:** create → list → search → reveal → copy (auto-clears) →
edit → delete round-trip on device; TalkBack passes on list and detail; revealed
values never appear in the app switcher (with F6.2).

**Phase:** 3.

---

## F5 — Navigation Shell & Liquid-Glass Navbar

**Purpose:** the app's visual identity; everything reachable in one thumb reach.

**Functional requirements**
- F5.1 Floating glass capsule holds **tabs only** (Cards, Settings today); the
  selection pill travels between contiguous tab slots. Adding future tabs means
  adding slots — the capsule layout never reserves space for actions.
- F5.2 **Add-card is a detached circular glass + button** sitting beside the
  capsule (same row, same backdrop, accent-tinted glass per the tinted-button
  recipe). It is an action, not a tab: it never participates in pill travel.
- F5.3 Tab switch keeps per-tab state (list scroll position survives).
- F5.4 Glass renders over scrolling list content (safe: tiles hold no secrets —
  F4.1); the navbar is hidden on lock and onboarding screens.
- F5.5 All navbar elements have semantics: tab role + labels; + labeled "Add card";
  48dp minimum targets.

**Acceptance criteria:** pill drag-to-select works unchanged in the capsule; the
+ button opens the add flow from both tabs; TalkBack announces both tabs and the
"Add card" button correctly; layout still fits with a hypothetical third tab
(design-time check, not shipped).

**Phase:** 0 (two-tab shell, done) → 3 (detached + button added with the
add-card flow).

---

## F6 — Settings

**Purpose:** every knob the app legitimately needs — and nothing else. Screenshot
protection and encryption are not settings; they are always on.

**Functional requirements**
- F6.1 Security — Auto-lock timeout: Immediately / 30s / 1 min / 5 min
  (default 1 min).
- F6.2 Security — note: FLAG_SECURE is permanent behavior, listed in Settings as
  an informational row, not a toggle.
- F6.3 Security — Biometric unlock toggle; enabling/disabling re-verifies PIN.
- F6.4 Security — Change PIN: requires current PIN, then new PIN twice.
- F6.5 Backup — Export encrypted backup (F7).
- F6.6 Backup — Import / restore (F7).
- F6.7 Appearance — Theme: System (default) / Light / Dark; persisted in DataStore
  (non-sensitive).
- F6.8 About — version, open-source licenses (satisfies Apache attribution),
  GitHub link, the privacy statement ("we collect nothing") in plain words.
- F6.9 Danger zone — Erase vault: separated visually, requires typing a
  confirmation word, then wipes DB + Keystore entries + prefs and returns to
  onboarding. Irreversible and says so.

**Non-settings (explicitly rejected):** screenshot-protection toggle, analytics
opt-in (no analytics exist), cloud sync toggle (no cloud exists in v1).

**UI states:** each row's disabled state when preconditions fail (e.g. biometric
toggle without enrolled biometrics).

**Acceptance criteria:** every setting persists across restart; timeout change
takes effect without restart; erase leaves no data behind (DB file, prefs, and
Keystore alias all gone).

**Phase:** 4 (security settings), 5 (backup rows), 3 (theme + about can land with
card UI).

---

## F7 — Backup & Restore

**Purpose:** the only sanctioned way data leaves the device — explicit, encrypted,
user-held keys. Also the recovery path for device loss and key invalidation
(F2.5).

**Functional requirements**
- F7.1 Export: user chooses a passphrase (minimum strength enforced: length ≥ 8,
  not all-same-character); vault serialized → encrypted (PBKDF2-derived key,
  AES-256-GCM) → shared as a single file via the system share sheet.
- F7.2 The passphrase is never stored, and the UX says plainly: "we cannot recover
  this file without your passphrase."
- F7.3 Import: pick file → passphrase → validate + decrypt → preview count →
  merge with strategy on ID collision: skip or replace (user chooses once for the
  whole import) → summary report.
- F7.4 Wrong passphrase or corrupt file fails with a clear message and imports
  nothing (all-or-nothing validation before any write).
- F7.5 Backup file contains ciphertext only; inspectable without leaking data.
- F7.6 Nudge: after the first card is saved, prompt once to export; afterwards a
  dismissible periodic reminder (default: monthly, dismiss = snooze).

**Acceptance criteria:** export on device A → import on device B restores the
vault byte-perfect; wrong passphrase imports nothing; the exported file contains
no plaintext.

**Phase:** 5.

---

## Out of scope for v1 (recorded so they're deliberate)

- NFC/tap-to-pay, payment functionality of any kind (permanent non-goal).
- Cloud sync and accounts (v2 discovery: E2E-encrypted, ciphertext-only server).
- Card OCR / camera scanning (Phase 7 stretch, on-device only if built).
- Barcode/QR display for loyalty cards — good candidate for v1.1; needs a
  barcode-format field on the loyalty template. Parked, not rejected.
- iOS (separate SwiftUI codebase later; same security design).
- Manual drag-reorder of cards (favorites + recency ordering first; revisit on
  feedback).

## Open questions (to resolve before their phase starts)

1. Payment-card number grouping: auto-format digits in groups of 4 while typing?
   (Phase 3 form work — cosmetic but touches validation.)
2. Backup reminder cadence default (F7.6): monthly, or after every N new cards?
3. Should "unreadable record" entries (F3.3) offer a delete shortcut, or force
   the user through detail → delete?
