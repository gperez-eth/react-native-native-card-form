import type { NativeCardFormErrorCode } from './types';
export declare class NativeCardFormError extends Error {
    readonly code: NativeCardFormErrorCode;
    constructor(code: NativeCardFormErrorCode);
}
export declare function asNativeCardFormError(value: unknown): NativeCardFormError;
//# sourceMappingURL=errors.d.ts.map