# Public API v0.x

The only public component is `NativeCardForm`. Consumers provide labels, hints,
errors and appearance in React and hold a `NativeCardFormRef`.

```tsx
const form = useRef<NativeCardFormRef>(null);

<NativeCardForm ref={form} strings={localizedStrings} onChange={setCardState} />;
const { paymentMethodId } = await form.current!.tokenize();
await form.current!.focus('number');
await form.current!.reset();
```

`tokenize`, `focus` and `reset` are Promise-based commands. The component does
not use prop counters or expose a resolver callback. Only one tokenization may
be active for a session. Reset, timeout and disposal settle pending work exactly
once with a closed `NativeCardFormError.code`.

## Appearance

`appearance.formStyle` and `appearance.rowStyle` style the React Native layout.
`appearance.field` styles the field column, label, input chrome, native input
container and error text. The `inputStyle` text properties are also forwarded to
both private native inputs:
`color`, `fontSize`, `fontFamily`, `fontWeight`, `fontStyle`, `textAlign` and
`letterSpacing`. `placeholderColor` and `cursorColor` are applied natively on
both platforms. Invalid color values are rejected instead of silently falling
back to a different color.

When a field is both focused and valid, the wrapper applies its green valid
border. Override it with `validContainerStyle` if the host uses another success
color.

The wrapper owns the layout, so a label or error can grow its field naturally.
Consumers should not position a brand renderer or a native field with absolute
coordinates.

## Data boundary

JavaScript receives only:

- `complete`;
- `empty | incomplete | invalid | valid`, `focused` and `touched` per field;
- `visa | mastercard | amex | discover | unknown`;
- a successful `paymentMethodId`; or
- a documented, closed error code.

PAN, expiry, CVC, partial values, card suffixes, Stripe parameters and raw Stripe
errors are never part of props, events, state or results. v0.x has no postal-code
or cardholder-name field.

## Lifecycle and errors

Each mounted form owns one opaque native session. Unmount disposes it. Concurrent
forms must be isolated, and callbacks arriving after dispose are ignored.

Closed errors are: `busy`, `cancelled`, `card_incomplete`, `card_invalid`,
`disposed`, `network_error`, `stripe_not_configured`, `timeout` and
`tokenization_failed`.

Invalid state is presented after blur or an attempted tokenization. Ordinary
typing remains `incomplete`.

## Compatibility and semver

The package requires `StripeProvider` from peer
`@stripe/stripe-react-native`. It never accepts a publishable key.

During `0.x`, breaking changes may ship in a minor release; patch releases remain
backwards compatible. Sensitive primitives, session identifiers, native view
names, coordinates and intrinsic implementation details are private and carry
no compatibility guarantee.
