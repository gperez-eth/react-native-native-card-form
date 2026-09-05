# Compatibility matrix

Baseline verified for the v0.x prerelease:

| Layer | Version / policy | Verification |
| --- | --- | --- |
| Expo | 54 | host and clean-example CNG |
| React Native | 0.81.4 | New Architecture enabled |
| Stripe React Native | 0.56.x | peer only; one resolved native SDK graph |
| iOS | deployment target 15.1+ | pod install and simulator build |
| Android | host-owned minSdk | example host declares 24; library reads `rootProject.ext.minSdkVersion` |

Expo Go is not supported. Use an Expo development build or bare React Native.

## Reproducible commands

From the package repository:

```sh
npm install
npm run build
npm pack
npm run check:tarball
node scripts/verify-clean-example.js android
node scripts/verify-clean-example.js ios
```

The verifier packs the normal tarball, creates a temporary copy of `example`,
installs only from the tarball and public registry, checks that Stripe resolves
once, runs Expo prebuild/autolinking, and performs the directed native build.

The example places `NativeCardForm` below `StripeProvider` and exercises
`focus`, `reset` and `tokenize`. Without a configured provider, tokenization
fails with only `stripe_not_configured`.

## Verification status — 2026-09-05

- Package build, lint, validation fixtures and tarball audit passed before the
  latest native appearance changes.
- The clean-example native matrix remains pending after the latest changes.
- A previous clean-example attempt stopped during Expo prebuild because the
  Stripe plugin was configured without its required `merchantIdentifier`; the
  example now uses the documented tuple configuration. Android and iOS must be
  rerun before this matrix can be described as passing.
- No device tokenization, production payment, PCI assessment or QA evidence is
  implied by package compilation.

CI repeats the package audit and both platform builds. Device tokenization and
provider-negative smoke results belong to the QA evidence in issue #881; a
simulator compilation is not represented as a live Stripe transaction.
