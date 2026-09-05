# Security quarantine

This package is a reconstruction workspace and **must not be enabled in
production** yet. Local tarballs are allowed for build/QA, but a public release
remains gated by the external review in issue #892.

The previous prototype emitted an incremental `last4`, allowing a sequence of
otherwise small events to reconstruct a PAN. Public events and tokenization
results must contain only:

- field state, focus/touched state and a closed card-brand value;
- a successful `paymentMethodId`; or
- a closed, sanitized error code.

Avoiding card data in JavaScript is not PCI certification and does not establish
SAQ A eligibility. Production remains gated by issue #892. If a release is needed
before that review, use Stripe's supported `CardField`, `CardForm` or
`PaymentSheet` instead.
