# Changelog

## 0.1.0-alpha.2

- Breaking: move the supported baseline to Expo SDK 57, React Native 0.86
  and `@stripe/stripe-react-native` 0.64 (peer ranges updated to match). The
  0.56 → 0.64 Stripe step changes the native SDKs underneath, so CocoaPods
  could not resolve this package next to Stripe 0.64 at all.
- Native Stripe SDKs: iOS `StripePayments` `~> 24.25.0` → `~> 25.11.0`,
  Android `stripe-android` 21.29.2 → 23.4.0. No source changes needed: the
  iOS validation suite (44 tests) and the Android JUnit suite (46 tests) pass
  unchanged on the new SDKs, and `Stripe.dispatchResult` still delivers every
  callback on `Dispatchers.Main` (re-checked by decompiling `payments-core`
  23.4.0), so `CardSessionRegistry`'s main-thread confinement still holds.
- iOS deployment target for consumers is now 16.4 (Expo SDK 57's floor); the
  podspec itself still declares 15.1. The podspec now reads its version from
  `package.json` instead of a stale literal.
- Tooling: TypeScript 6 (`rootDir` set explicitly), `expo-module-scripts` 56.
  The build now emits `react/jsx-runtime` calls instead of preserved JSX.
- Example app moved to Expo SDK 57.

## 0.1.0-alpha.1

- Fix: strip the default `EditText` underline on Android — the host theme's
  `android:editTextBackground` was drawing Material's underline on every
  field (#9).
- Fix: confine `CardSessionRegistry` (Android) to the main thread and drop
  all its locks, removing the ANR class that came from mixing a
  `@Synchronized` section with a Fabric-reaching call on the same lock (#8).
- Fix: stop `dispose`/`reset` deadlocking the main thread on Android (#7).
- Fix: delegate card brand detection fully to Stripe's own classifier
  instead of guessing brand prefixes independently (#3).
- Publish the `0.1.0-alpha.1` prerelease to npm under the `alpha` dist-tag.

## 0.1.0-alpha.0

- Define the sanitized v0.x API and Promise-based ref commands.
- Add Expo module scaffolding for iOS 15.1+ and host-controlled Android minSdk.
- Keep React-owned layout fluid while forwarding field typography and appearance
  to the private native inputs on both platforms.
- Reject invalid native color configuration instead of silently changing it.
- Add tarball/source completeness checks for the clean Expo example.
- Publish the `0.1.0-alpha.0` prerelease to npm under the `alpha` dist-tag.
