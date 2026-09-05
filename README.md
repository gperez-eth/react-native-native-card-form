# react-native-native-card-form

A composable React Native card form whose PAN, expiry and CVC inputs remain in
private iOS/Android primitives. React owns layout, labels, errors and logos;
JavaScript receives only sanitized validity/focus state and a PaymentMethod ID.

> **Security boundary, not PCI certification.** Keeping card values out of the
> React Native bridge does not by itself make an integration PCI compliant or
> eligible for SAQ A. Obtain an assessment for the complete host application.
> Screenshots and app-switcher previews are the host's responsibility.

## Compatibility

| Dependency | Supported v0.x baseline |
| --- | --- |
| Expo | 54 (development build/CNG) |
| React Native | 0.81 |
| `@stripe/stripe-react-native` | 0.56 |
| iOS | 15.1+ |
| Android | the host application's `minSdk` |

Expo Go is not supported because this package contains native code. The clean
example's effective Android minSdk is 24; the library itself inherits the
consuming host's value. See
[`docs/COMPATIBILITY.md`](docs/COMPATIBILITY.md).

## Install

Install the release tarball together with its peers:

```sh
npm install ./react-native-native-card-form-0.1.0-alpha.0.tgz \
  @stripe/stripe-react-native@0.56
npx expo prebuild
```

For Expo CNG, configure Stripe's plugin with an Apple merchant identifier (and
enable Google Pay only if the host has configured it):

```json
{
  "expo": {
    "plugins": [
      [
        "@stripe/stripe-react-native",
        {
          "merchantIdentifier": "merchant.com.example.app",
          "enableGooglePay": true
        }
      ]
    ]
  }
}
```

Bare React Native consumers use normal CocoaPods and Gradle autolinking.
The package does not bundle Stripe's native SDK; the Stripe React Native peer is
the single source of the compatible native version.

## Use

`StripeProvider` is required. The package does not accept a publishable-key prop.

```tsx
import { StripeProvider } from '@stripe/stripe-react-native';
import {
  NativeCardForm,
  type NativeCardFormRef,
} from 'react-native-native-card-form';

const ref = useRef<NativeCardFormRef>(null);

<StripeProvider publishableKey={publishableKey}>
  <NativeCardForm ref={ref} strings={strings} onChange={setState} />
</StripeProvider>;

const { paymentMethodId } = await ref.current!.tokenize();
```

Only Visa, Mastercard, Amex, Discover and `unknown` are represented in v0.x.
See [`docs/API.md`](docs/API.md) for commands, errors and lifecycle.

The default wrapper includes accessible SVG marks for all four named networks.
Pass `renderBrand` to replace them. Layout uses normal flex flow: labels and
errors increase its intrinsic height, and no native coordinate event or fixed
form height is involved.

`appearance` customizes the React Native form/row/field/chrome/error styles and
the native text presentation. Text color, placeholder color, cursor color, font
family, size, weight, style, alignment and letter spacing are applied by the
native inputs on both platforms. Invalid native colors are surfaced as
configuration errors; they are never replaced silently.

## System policies

Paste is supported. Copy/cut and sensitive state restoration are disabled.
There is no internal font-scale cap. The host owns screenshot/app-switcher
protection, observability redaction, scrolling and any external font limits.
See [`docs/ACCESSIBILITY.md`](docs/ACCESSIBILITY.md) for the complete host/native
responsibility split.

## Distribution

Initial releases are GitHub Release tarballs. npm metadata is ready, but npm
publishing is intentionally deferred. See [`docs/PUBLISHING.md`](docs/PUBLISHING.md).
