import { requireNativeModule, requireNativeViewManager } from 'expo-modules-core';
import React, { forwardRef, useCallback, useEffect, useId, useImperativeHandle, useMemo, useRef, useState, } from 'react';
import { I18nManager, StyleSheet, Text, useWindowDimensions, View, } from 'react-native';
import Svg, { Path } from 'react-native-svg';
import { DefaultBrand } from './brands';
import { asNativeCardFormError } from './errors';
const NativeModule = requireNativeModule('MackenrowNativeCard');
const PrivateNativeField = requireNativeViewManager('MackenrowNativeCard');
const DEFAULT_TIMEOUT_MS = 30_000;
const EMPTY_FIELD = { status: 'empty', focused: false, touched: false };
const INITIAL_STATE = {
    complete: false,
    brand: 'unknown',
    fields: {
        number: EMPTY_FIELD,
        expiry: EMPTY_FIELD,
        cvc: EMPTY_FIELD,
    },
};
function Field({ field, sessionId, state, props, onNativeChange, trailing }) {
    const { fontScale } = useWindowDimensions();
    const strings = props.strings;
    const appearance = props.appearance?.field;
    const label = strings[`${field}Label`];
    const placeholder = strings[`${field}Placeholder`];
    const hint = strings[`${field}Hint`] ?? placeholder;
    const error = state.status === 'invalid'
        ? strings.invalidError
        : state.touched && state.status !== 'valid' && state.status !== 'empty'
            ? strings.incompleteError
            : undefined;
    const inputText = StyleSheet.flatten(appearance?.inputStyle);
    const textColor = typeof inputText?.color === 'string' ? inputText.color : '#FFFFFF';
    const fontSize = typeof inputText?.fontSize === 'number' ? inputText.fontSize : 16;
    const fontWeight = typeof inputText?.fontWeight === 'string'
        ? inputText.fontWeight
        : typeof inputText?.fontWeight === 'number'
            ? String(inputText.fontWeight)
            : undefined;
    const fontStyle = inputText?.fontStyle;
    const textAlign = inputText?.textAlign;
    const letterSpacing = typeof inputText?.letterSpacing === 'number'
        ? inputText.letterSpacing
        : undefined;
    return (<View style={[styles.fieldColumn, appearance?.fieldStyle]}>
      <Text accessible={false} style={[styles.label, appearance?.labelStyle]}>{label}</Text>
      <View style={[
            styles.inputChrome,
            appearance?.containerStyle,
            state.focused && state.status === 'valid' && [
                styles.validFocused,
                appearance?.validContainerStyle,
            ],
            state.focused && state.status !== 'valid' && [
                styles.focused,
                appearance?.focusedContainerStyle,
            ],
            error && [styles.invalid, appearance?.invalidContainerStyle],
            props.disabled && [styles.disabled, appearance?.disabledContainerStyle],
        ]}>
        <PrivateNativeField accessibilityHint={hint} accessibilityLabel={label} enteredAccessibilityValue={strings.enteredValueLabel} invalidAccessibilityValue={strings.invalidAccessibilityLabel} disabled={props.disabled ?? false} field={field} fontFamily={inputText?.fontFamily} fontSize={fontSize} fontStyle={fontStyle} fontWeight={fontWeight} letterSpacing={letterSpacing} onStateChange={onNativeChange} placeholder={placeholder} placeholderColor={appearance?.placeholderColor ?? '#8A8A93'} cursorColor={appearance?.cursorColor ?? '#E74949'} sessionId={sessionId} style={[
            styles.nativeInput,
            appearance?.inputContainerStyle,
            { minHeight: Math.max(48, 48 * fontScale) },
        ]} textAlign={textAlign} textColor={textColor}/>
        {trailing}
        {field !== 'number' && state.status === 'valid' ? <ValidMark /> : null}
      </View>
      {error ? (<Text accessibilityLiveRegion="polite" accessibilityRole="alert" style={[styles.error, appearance?.errorStyle]}>
          {error}
        </Text>) : null}
    </View>);
}
function ValidMark() {
    return (<View accessible={false} pointerEvents="none" style={styles.validMark}>
      <Svg width={22} height={22} viewBox="0 0 24 24">
        <Path d="m5 12.5 4.2 4.2L19.5 6.8" fill="none" stroke="#22C55E" strokeLinecap="round" strokeLinejoin="round" strokeWidth={2.8}/>
      </Svg>
    </View>);
}
/**
 * Public v0.x wrapper. Sensitive values stay in the three private native fields;
 * this component owns every non-sensitive visual and layout concern.
 */
export const NativeCardForm = forwardRef(function NativeCardForm(props, ref) {
    const { autoFocus = false, onChange, renderBrand, testID, tokenizationTimeoutMs = DEFAULT_TIMEOUT_MS, } = props;
    const reactId = useId();
    const sessionId = `native-card-form:${reactId}`;
    const disposed = useRef(false);
    const stateRef = useRef(INITIAL_STATE);
    const [state, setState] = useState(INITIAL_STATE);
    useEffect(() => {
        disposed.current = false;
        NativeModule.ensureSession(sessionId);
        if (autoFocus) {
            requestAnimationFrame(() => {
                if (!disposed.current)
                    void NativeModule.focus(sessionId, 'number').catch(() => undefined);
            });
        }
        return () => {
            disposed.current = true;
            void NativeModule.disposeSession(sessionId).catch(() => undefined);
        };
    }, [autoFocus, sessionId]);
    const updateField = useCallback((event) => {
        const { field, brand, status, focused, touched } = event.nativeEvent;
        const current = stateRef.current;
        const fields = { ...current.fields, [field]: { status, focused, touched } };
        const next = {
            complete: Object.values(fields).every((item) => item.status === 'valid'),
            brand: field === 'number' ? brand : current.brand,
            fields,
        };
        stateRef.current = next;
        setState(next);
        onChange?.(next);
    }, [onChange]);
    useImperativeHandle(ref, () => ({
        async tokenize() {
            if (disposed.current)
                throw asNativeCardFormError({ code: 'disposed' });
            try {
                return await NativeModule.tokenize(sessionId, tokenizationTimeoutMs);
            }
            catch (error) {
                throw asNativeCardFormError(error);
            }
        },
        async reset() {
            if (disposed.current)
                throw asNativeCardFormError({ code: 'disposed' });
            try {
                await NativeModule.reset(sessionId);
                stateRef.current = INITIAL_STATE;
                setState(INITIAL_STATE);
                onChange?.(INITIAL_STATE);
            }
            catch (error) {
                throw asNativeCardFormError(error);
            }
        },
        async focus(field) {
            if (disposed.current)
                throw asNativeCardFormError({ code: 'disposed' });
            try {
                await NativeModule.focus(sessionId, field);
            }
            catch (error) {
                throw asNativeCardFormError(error);
            }
        },
    }), [onChange, sessionId, tokenizationTimeoutMs]);
    const brandLabel = props.strings.brandLabels[state.brand];
    const brandNode = useMemo(() => {
        const rendererProps = { brand: state.brand, accessibilityLabel: brandLabel };
        return renderBrand
            ? React.createElement(renderBrand, rendererProps)
            : <DefaultBrand {...rendererProps}/>;
    }, [brandLabel, renderBrand, state.brand]);
    return (<View style={[styles.form, props.appearance?.formStyle]} testID={testID} accessibilityRole="none">
        <Field field="number" onNativeChange={updateField} props={props} sessionId={sessionId} state={state.fields.number} trailing={brandNode}/>
        <View style={[styles.row, props.appearance?.rowStyle]}>
          <Field field="expiry" onNativeChange={updateField} props={props} sessionId={sessionId} state={state.fields.expiry}/>
          <Field field="cvc" onNativeChange={updateField} props={props} sessionId={sessionId} state={state.fields.cvc}/>
        </View>
      </View>);
});
const styles = StyleSheet.create({
    form: {
        gap: 12,
        direction: I18nManager.isRTL ? 'rtl' : 'ltr',
    },
    row: {
        flexDirection: 'row',
        gap: 12,
    },
    fieldColumn: {
        flex: 1,
        minWidth: 0,
        gap: 6,
    },
    label: {
        color: '#D4D4D8',
        fontSize: 14,
    },
    inputChrome: {
        minHeight: 48,
        paddingHorizontal: 12,
        borderWidth: 1,
        borderColor: '#3F3F46',
        borderRadius: 10,
        backgroundColor: '#18181B',
        flexDirection: 'row',
        alignItems: 'center',
    },
    nativeInput: {
        minHeight: 48,
        flex: 1,
    },
    validMark: {
        width: 24,
        height: 24,
        alignItems: 'center',
        justifyContent: 'center',
        marginLeft: 8,
    },
    focused: {
        borderColor: '#E74949',
    },
    validFocused: {
        borderColor: '#22C55E',
    },
    invalid: {
        borderColor: '#EF4444',
    },
    disabled: {
        opacity: 0.5,
    },
    error: {
        color: '#F87171',
        fontSize: 12,
    },
});
//# sourceMappingURL=NativeCardForm.js.map