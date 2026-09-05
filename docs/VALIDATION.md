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
- v0.x recognizes Visa, Mastercard (51–55 and 2221–2720), Amex (34/37), and
  Discover (6011, 65, 644–649 and 622126–622925).
- Everything else is `unknown`. A generic `6` prefix is never enough to identify
  Discover. Stripe may still accept a Luhn-valid `unknown` card.

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
