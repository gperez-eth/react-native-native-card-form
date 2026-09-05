# Accessibility, localization and system policy

The host supplies every visible label, placeholder, hint, error and accessible
brand name through `NativeCardFormStrings`. Native code contains no user-facing
fallback copy. Controls remain three separate accessibility elements in the
order number, expiry, CVC; each announces its label, protected entered state,
hint and invalid state. React errors use a polite live region.

The wrapper uses a 48-point/dp minimum target (above iOS's 44-point floor) and
grows that minimum with the system font scale. It does not clamp Dynamic Type or
Android font scale. Consumers must place the form in a scroll container and must
not impose a fixed parent height. Normal flex direction supports RTL.

Paste is enabled. Copy and cut are disabled in native text controls. Sensitive
state restoration is disabled. Card-number autofill is enabled; expiry and CVC
autofill are disabled in v0.x to avoid platform-specific restoration behavior.

## Host responsibilities

The package does **not** protect screenshots or app-switcher snapshots. The host
must apply its own iOS/Android window policy for the complete payment surface.
The host also owns log/Sentry/breadcrumb/storage redaction, external font-scale
policy, translations and end-to-end VoiceOver/TalkBack/Switch Control testing.
