# Validation and editing specification

This file and `fixtures/card-validation.json` are the cross-platform v0.x source
of truth. Platform implementations use public Stripe validators where available
and normalize their output to this contract.

## Number and brand

- Empty input is `empty`.
- Potential prefixes and numbers shorter than their final valid length are
  `incomplete`.
- A final-length number is `valid` only when its length is allowed for its brand
  and it passes Luhn; otherwise it is `invalid`.
- **Brand and length rules both come straight from Stripe's own validators**
  (`STPCardValidator` on iOS, `CardBrand`/`CardUtils` on Android) — this package
  does not maintain its own BIN-prefix table or length list for any brand.
  Whatever Stripe's classifier returns is what gets shown and what decides
  length; we never second-guess it.
- `visa`, `mastercard`, `amex`, `discover`, `jcb` and `unionpay` are the brands
  Stripe returns a dedicated case for and this package renders an icon for.
  Everything else — including Diners Club, Cartes Bancaires, and any BIN Stripe
  itself doesn't recognize — is `unknown`.
- **`maestro` stays in the `CardBrand` type and has an icon, but Stripe's SDKs
  have no dedicated Maestro case at all**: `STPCardBrand`/`CardBrand` classify a
  Maestro-network BIN (prefix `67`, say) as `mastercard` on purpose (see
  `STPBINController`'s hardcoded `"67" -> mastercard // Maestro` range). So a
  real Maestro card is expected to show the Mastercard icon and be judged by
  Mastercard's length rule (16), not a wider Maestro-specific range — widening
  it client-side would only risk a form that says "valid" for a number Stripe's
  own servers still decline at confirmation time. Verified against a real
  16-digit 67-prefixed PAN on Android's compiled SDK: brand reports
  `MasterCard`, `isValidCardNumberLength` is `true`; the same PAN padded to 17
  or 18 digits reports `isValidCardNumberLength: false` and is rejected.
- **`unknown` is always `invalid`, never a Luhn-valid pass-through — this
  document used to claim the opposite, and that claim was never true on iOS.**
  `STPCardValidator.validationState(forNumber:validatingCardBrand:)` on iOS
  returns `.invalid` the instant the BIN range is unrecognized (see
  `STPCardValidator.swift`: `if binRange.brand == .unknown && validatingCardBrand
  { return .invalid }`), **before it even looks at length or Luhn** — a single
  digit that matches no known brand prefix is already `invalid`, not
  `incomplete`. Android's `CardBrand.Unknown` doesn't expose that policy
  directly (`isValidCardNumberLength` on it returns `false` for every length
  from 1 to at least 18 digits, verified by running the compiled SDK, and
  `getMaxLengthForCardNumber` never grows past 16 either — so composing those
  two primitives the same way as every other brand leaves an unrecognized PAN
  stuck in `incomplete` forever, with no way to become `valid` and no error
  ever shown). `CardValidation` on Android special-cases `CardBrand.Unknown` to
  `invalid` explicitly, to reproduce iOS's real behavior instead of leaving a
  third, accidental one in its place.
- **Visa's valid lengths are NOT guaranteed to be the same list on both
  platforms, and this document no longer pretends otherwise.** iOS's hardcoded
  BIN table lists several 13-digit Visa ranges (`STPBINController.swift`);
  Android's `CardBrand.Visa.isValidCardNumberLength()`, verified by running the
  compiled SDK, accepted only 16 digits for every 13- and 19-digit Visa PAN
  tried against it. The two platforms both still do exactly what Stripe's own
  code on that platform says — this package still isn't intervening — but "what
  Stripe says" can genuinely differ by platform, and a single shared claim here
  would have been wrong for one of them. `fixtures/card-validation.json` /
  `scripts/validation-fixtures.js` only assert what's been verified on both;
  the platform-specific 13/17/18/19-digit cases live in each platform's own
  native test suite, answered by that platform's own SDK.

## Expiry and CVC

- Expiry is `MM/YY`, month 01–12, and remains valid through the last day of its
  displayed month.
- CVC is four digits for Amex and three for the other v0.x brands, including
  `unknown`.

## Presentation state

While a control has focus, an intrinsically invalid partial value is exposed as
`incomplete`; `invalid` becomes visible on blur or a tokenization attempt. Empty
remains `empty`. A field becomes `touched` after its first blur or tokenize.

Auto-advance happens once when the current edit transitions into a definitely
valid value. It is suppressed for paste and does not run merely because an
already-valid field was edited or refocused. After CVC becomes valid, the form
blurs the final field so the completed form does not keep the keyboard focus.
Clearing a field resets its transition, so completing it again advances again.

## Editing and clipboard

Formatting preserves the logical cursor position (number of digits before the
selection) when inserting or deleting in the middle. Paste is allowed and
sanitized to digits. Copy and cut are blocked; selection and hardware-keyboard
navigation remain available.

## Testing `CardValidation`

The number/expiry/CVC/brand decision logic lives in one pure file per
platform — `android/src/main/java/com/mackenrow/nativecard/CardValidation.kt`
and `ios/CardValidation.swift` — with no `Context`, no live text field, and
(beyond the one `STPCardValidator`/`CardUtils`+`CardBrand` dependency) no
framework coupling. Both are covered by real, native test suites that run
against the actual Stripe SDK — not a JS reimplementation of it — and both
run in CI (`.github/workflows/ci.yml`):

```bash
# Android — plain local JVM unit tests, no emulator, no Robolectric
cd example && npx expo prebuild --platform android --no-install
cd android && ./gradlew testDebugUnitTest --tests "com.mackenrow.nativecard.*"

# iOS — XCTest via the repo-root Package.swift (test-only; unrelated to how
# the package ships via CocoaPods), against a booted simulator
xcodebuild test -scheme NativeCardFormValidation \
  -destination 'platform=iOS Simulator,name=iPhone 16,OS=latest'
```

Every expected value in both suites was checked by actually running it
against the real, compiled SDK before being written down. That discipline is
what surfaced several real, non-obvious platform-specific facts worth reading
before touching either file again:

- **Visa's valid lengths differ by platform.** iOS's hardcoded BIN table
  lists several 13-digit Visa ranges; Android's `CardBrand.Visa` only
  validates 16 digits on `com.stripe:stripe-android:21.29.2`.
- **A brand Stripe can't recognize is always `invalid`, never a Luhn-valid
  pass-through** — even a single digit that matches no known prefix at all.
  `CardBrand.Unknown.isValidCardNumberLength()` on Android returns `false`
  for every length, so composing it the way every other brand is composed
  would leave the field stuck in `incomplete` forever; `numberStatus`
  special-cases it to mirror iOS's real, verified policy instead.
- **CVC length is effectively brand-blind on iOS.** Every brand string this
  package can pass through `cvcStatus` ends up mapped to either `.amex` or
  `.unknown`, and both of those report a max CVC length of 4 — so 3 **or** 4
  digits are accepted as valid for every card, not just Amex. Android's own
  `cvcStatus` genuinely does enforce "exactly 4 for Amex, exactly 3
  otherwise" — another real divergence, not a bug in either test suite.
- **Expiry-year validation on iOS checks the YEAR's length before the month's
  validity.** Typing `00` or `13` with no year digits yet reports
  `incomplete`, not `invalid` — the month is only judged once two year
  digits exist. Android's hand-written `expiryStatus` checks the month
  first, so the same input is `invalid` there immediately.
- **iOS caps an expiry year at 50 years out**
  (`STPCardValidator.swift`: `yearInt - moddedYear <= 50`). Android's
  hand-written comparison has no upper bound at all.

None of the above are bugs this package is trying to paper over — per this
document's own rule, brand and length rules come straight from Stripe's
validators and this package does not second-guess them. They're recorded here
because "what Stripe actually does" turned out to differ by platform in ways
that aren't visible from either platform's documentation alone, only from
running the real SDK.
