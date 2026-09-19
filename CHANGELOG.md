# Changelog

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
