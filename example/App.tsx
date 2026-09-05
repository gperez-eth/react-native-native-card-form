import { StripeProvider } from '@stripe/stripe-react-native';
import { useRef } from 'react';
import { Button, SafeAreaView } from 'react-native';
import {
  NativeCardForm,
  type NativeCardFormRef,
} from 'react-native-native-card-form';

const strings = {
  numberLabel: 'Card number',
  expiryLabel: 'Expiry',
  cvcLabel: 'Security code',
  numberPlaceholder: '1234 1234 1234 1234',
  expiryPlaceholder: 'MM/YY',
  cvcPlaceholder: 'CVC',
  invalidError: 'Check this field',
  incompleteError: 'Complete this field',
  enteredValueLabel: 'Value entered',
  invalidAccessibilityLabel: 'Invalid',
  brandLabels: {
    visa: 'Visa',
    mastercard: 'Mastercard',
    maestro: 'Maestro',
    amex: 'American Express',
    discover: 'Discover',
    jcb: 'JCB',
    unionpay: 'UnionPay',
    unknown: 'Card network not identified',
  },
};

export default function App() {
  const formRef = useRef<NativeCardFormRef>(null);
  return (
    <StripeProvider publishableKey="pk_test_replace_me">
      <SafeAreaView>
        <NativeCardForm ref={formRef} strings={strings} />
        <Button title="Focus card number" onPress={() => void formRef.current?.focus('number')} />
        <Button title="Reset" onPress={() => void formRef.current?.reset()} />
        <Button title="Tokenize test card" onPress={() => void formRef.current?.tokenize()} />
      </SafeAreaView>
    </StripeProvider>
  );
}
