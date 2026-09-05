import type { NativeCardFormErrorCode } from './types';

const PUBLIC_MESSAGES: Record<NativeCardFormErrorCode, string> = {
  busy: 'A tokenization is already in progress.',
  cancelled: 'The operation was cancelled.',
  card_incomplete: 'The card details are incomplete.',
  card_invalid: 'The card details are invalid.',
  disposed: 'The card form is no longer available.',
  network_error: 'The payment network is unavailable.',
  stripe_not_configured: 'NativeCardForm must be rendered under a configured StripeProvider.',
  timeout: 'The operation timed out.',
  tokenization_failed: 'The card could not be tokenized.',
};

export class NativeCardFormError extends Error {
  readonly code: NativeCardFormErrorCode;

  constructor(code: NativeCardFormErrorCode) {
    super(PUBLIC_MESSAGES[code]);
    this.name = 'NativeCardFormError';
    this.code = code;
  }
}

export function asNativeCardFormError(value: unknown): NativeCardFormError {
  if (value instanceof NativeCardFormError) return value;
  const code =
    typeof value === 'object' &&
    value !== null &&
    'code' in value &&
    typeof value.code === 'string' &&
    value.code in PUBLIC_MESSAGES
      ? (value.code as NativeCardFormErrorCode)
      : 'tokenization_failed';
  return new NativeCardFormError(code);
}
