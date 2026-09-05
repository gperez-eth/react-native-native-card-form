import type { ComponentType, ReactNode } from 'react';
import type { StyleProp, TextStyle, ViewStyle } from 'react-native';
export type CardField = 'number' | 'expiry' | 'cvc';
export type CardFieldStatus = 'empty' | 'incomplete' | 'invalid' | 'valid';
export type CardBrand = 'visa' | 'mastercard' | 'maestro' | 'amex' | 'discover' | 'jcb' | 'unionpay' | 'unknown';
export interface CardFieldState {
    readonly status: CardFieldStatus;
    readonly focused: boolean;
    readonly touched: boolean;
}
export interface NativeCardFormState {
    readonly complete: boolean;
    readonly brand: CardBrand;
    readonly fields: Readonly<Record<CardField, CardFieldState>>;
}
export interface TokenizationResult {
    readonly paymentMethodId: string;
}
export type NativeCardFormErrorCode = 'busy' | 'cancelled' | 'card_incomplete' | 'card_invalid' | 'disposed' | 'network_error' | 'stripe_not_configured' | 'timeout' | 'tokenization_failed';
export interface NativeCardFormRef {
    tokenize(): Promise<TokenizationResult>;
    reset(): Promise<void>;
    focus(field: CardField): Promise<void>;
}
export interface NativeCardFormStrings {
    readonly numberLabel: string;
    readonly expiryLabel: string;
    readonly cvcLabel: string;
    readonly numberPlaceholder: string;
    readonly expiryPlaceholder: string;
    readonly cvcPlaceholder: string;
    readonly numberHint?: string;
    readonly expiryHint?: string;
    readonly cvcHint?: string;
    readonly invalidError: string;
    readonly incompleteError: string;
    readonly enteredValueLabel: string;
    readonly invalidAccessibilityLabel: string;
    readonly brandLabels: Readonly<Record<CardBrand, string>>;
}
export interface CardFieldAppearance {
    readonly containerStyle?: StyleProp<ViewStyle>;
    readonly fieldStyle?: StyleProp<ViewStyle>;
    readonly labelStyle?: StyleProp<TextStyle>;
    readonly inputStyle?: StyleProp<TextStyle>;
    readonly inputContainerStyle?: StyleProp<ViewStyle>;
    readonly errorStyle?: StyleProp<TextStyle>;
    readonly focusedContainerStyle?: StyleProp<ViewStyle>;
    readonly validContainerStyle?: StyleProp<ViewStyle>;
    readonly invalidContainerStyle?: StyleProp<ViewStyle>;
    readonly disabledContainerStyle?: StyleProp<ViewStyle>;
    readonly placeholderColor?: string;
    readonly cursorColor?: string;
}
export interface NativeCardFormAppearance {
    readonly formStyle?: StyleProp<ViewStyle>;
    readonly rowStyle?: StyleProp<ViewStyle>;
    readonly field?: CardFieldAppearance;
}
export interface BrandRendererProps {
    readonly brand: CardBrand;
    readonly accessibilityLabel: string;
}
export interface NativeCardFormProps {
    readonly strings: NativeCardFormStrings;
    readonly appearance?: NativeCardFormAppearance;
    readonly disabled?: boolean;
    readonly autoFocus?: boolean;
    readonly tokenizationTimeoutMs?: number;
    readonly onChange?: (state: NativeCardFormState) => void;
    readonly renderBrand?: ComponentType<BrandRendererProps> | ((props: BrandRendererProps) => ReactNode);
    readonly testID?: string;
}
//# sourceMappingURL=types.d.ts.map