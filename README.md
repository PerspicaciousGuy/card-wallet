# Card Wallet

**Every card you carry, in one encrypted place — and nothing ever leaves your phone.**

[![Platform](https://img.shields.io/badge/platform-Android%2013%2B-3DDC84?logo=android&logoColor=white)](#requirements)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.4-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4)](https://developer.android.com/compose)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Status](https://img.shields.io/badge/status-in%20active%20development-orange)](#roadmap)

Card Wallet stores your debit, credit, ID, passport, insurance, loyalty and membership
cards in a single on-device vault. There is no account, no server, no analytics, no
network permission needed — the app's entire promise is that **your data never leaves
your device**.

> ⚠️ **Work in progress.** The security core and card vault are still being built —
> see the [roadmap](#roadmap). Don't trust real card data to it until a release exists.

---

## Why this app

- **Local-only by design.** No backend, no sync, no telemetry. Airplane mode forever is
  a supported configuration.
- **Hardware-backed encryption.** The vault master key is generated *inside* the
  Android Keystore (StrongBox where available) — non-exportable, gated by biometrics or
  your device credential at every use. Card records are encrypted with AES-256-GCM.
- **Liquid glass UI.** The signature floating navbar does real refraction, dispersion
  and drag-to-select — not a blur imitation (see [credits](#credits)).

## The security model, in one diagram

```
Vault Master Key (VMK)          AES-256, generated INSIDE AndroidKeyStore
  ├─ non-exportable, hardware-backed (StrongBox / TEE)
  ├─ every use gated by biometric or device credential (OS-enforced)
  └─ encrypts each card record: AES-256-GCM, fresh nonce per write

App PIN (fallback)              never stored — PBKDF2 verifier only;
                                authorizes the same Keystore path

Export passphrase (backups)     never stored — derives the backup file key;
                                backups are the ONLY way data leaves the app,
                                explicitly, encrypted, by you
```

Additional hardening: `allowBackup=false` (the OS never clouds the vault),
`FLAG_SECURE` (no screenshots or app-switcher leaks), clipboard auto-clear,
decryption-failure lockout, and glass effects are never rendered over revealed
card secrets (the backdrop capture samples chrome, not data).

## Requirements

- **Android 13+ (API 33).** The liquid-glass refraction is an AGSL `RuntimeShader`
  effect that exists only on Android 13 and newer; the app deliberately doesn't ship a
  degraded fallback UI.
- JDK 17 and Android Studio (or plain Gradle) to build.

## Building

```bash
git clone https://github.com/PerspicaciousGuy/card-wallet.git
cd card-wallet
./gradlew :app:assembleDebug
# APK lands in app/build/outputs/apk/debug/
```

Lint gates: `./gradlew :app:ktlintCheck :app:detekt` — both must pass clean.

## Project structure

```
app/src/main/java/com/cardwallet/
  ui/theme/        Material 3 design system (light + dark) + wallet tokens
  ui/glass/        Liquid-glass navbar (vendored, see credits)
  ui/components/   Shared feature-agnostic composables
  navigation/      Type-safe routes; central lock gate
  features/        lock, cards, settings, home — one feature per package
  data/            crypto, db, repository, session (arrives with the vault)
  domain/          Card model types
```

Architecture is unidirectional data flow: ViewModels expose `StateFlow<UiState>`,
screens are stateless composables, events flow up as lambdas.

## Roadmap

- [x] **Phase 0** — scaffold: design system, navigation, liquid-glass shell
- [ ] **Phase 1** — security core: Keystore VMK, AES-256-GCM vault cipher, biometric +
      PIN unlock, session lock gate
- [ ] **Phase 2** — vault storage: Room (ciphertext-only rows), repository, in-memory
      session cache
- [ ] **Phase 3** — card UI: list, detail with masked fields, add/edit forms
- [ ] **Phase 4** — hardening: FLAG_SECURE, auto-lock, clipboard hygiene, log audit
- [ ] **Phase 5** — encrypted backup export/import
- [ ] **Phase 6** — Play Store release

## Credits

The liquid-glass effect is built on the excellent
**[AndroidLiquidGlass](https://github.com/Kyant0/AndroidLiquidGlass)** by
**[Kyant](https://github.com/Kyant0)** — the `backdrop` and `shapes` libraries provide
the real-refraction rendering, and the navbar component in `ui/glass/` is adapted from
the catalog app in that repository (Apache-2.0, see [NOTICE](NOTICE)). If you want
this effect in your own app, start there.

**Want to build this navbar yourself?** We wrote a detailed, from-scratch
walkthrough of the whole implementation — the backdrop capture model, the
three-layer accent-reveal trick, the drag physics, and every pitfall we hit:
[docs/liquid-glass-navbar.md](docs/liquid-glass-navbar.md).

## License

[Apache License 2.0](LICENSE) — see [NOTICE](NOTICE) for third-party attributions.
