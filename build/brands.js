import React from 'react';
import { Text, View } from 'react-native';
import Svg, { Circle, Path, Rect } from 'react-native-svg';
const frame = {
    width: 44,
    height: 28,
    alignItems: 'center',
    justifyContent: 'center',
};
function Visa() {
    return (<Svg width={44} height={28} viewBox="0 0 44 28">
      <Rect width={44} height={28} rx={4} fill="#FFFFFF"/>
      <Path d="M8 9h4l2 10h-4L8 9Zm6.8 10L18 9h4l-5.4 10h-1.8Zm7.3 0L24 9h3.7l-1.9 10h-3.7Zm6.1-7.2c0-2 1.9-3.1 4.6-3.1 1.2 0 2.4.3 3.2.6l-.5 2.8a7 7 0 0 0-2.8-.5c-.9 0-1.4.3-1.4.8 0 1.4 4.3 1.2 4.3 4 0 2.1-1.8 3-4.5 3-1.6 0-3-.4-3.8-.8l.5-2.8c.9.4 2.1.8 3.4.8.8 0 1.4-.3 1.4-.8 0-1.5-4.4-1.2-4.4-4Z" fill="#1434CB"/>
    </Svg>);
}
function Mastercard() {
    return (<Svg width={44} height={28} viewBox="0 0 44 28">
      <Rect width={44} height={28} rx={4} fill="#FFFFFF"/>
      <Circle cx={18} cy={14} r={8} fill="#EB001B"/>
      <Circle cx={26} cy={14} r={8} fill="#F79E1B"/>
      <Path d="M22 7.1a8 8 0 0 1 0 13.8 8 8 0 0 1 0-13.8Z" fill="#FF5F00"/>
    </Svg>);
}
function Amex() {
    return (<Svg width={44} height={28} viewBox="0 0 44 28">
      <Rect width={44} height={28} rx={4} fill="#2E77BC"/>
      <Path d="M5 10h7l1 2 1-2h8v8h-4v-4l-2.5 4h-4L9 14v4H5v-8Zm18 0h7l2 2 2-2h5l-4.5 4 4.5 4h-5l-2-2-2 2h-7v-8Zm3 3v2h3l1-1-1-1h-3Z" fill="#FFFFFF"/>
    </Svg>);
}
function Discover() {
    return (<Svg width={44} height={28} viewBox="0 0 44 28">
      <Rect width={44} height={28} rx={4} fill="#FFFFFF"/>
      <Path d="M5 10h2.5c3 0 4.5 1.5 4.5 4s-1.5 4-4.5 4H5v-8Zm2 2v4h.6c1.4 0 2.2-.7 2.2-2s-.8-2-2.2-2H7Zm6 6v-8h2v8h-2Zm3-4c0-2.4 1.8-4.2 4.4-4.2 1 0 1.9.3 2.6.8l-1.1 1.7a2.5 2.5 0 0 0-1.5-.5c-1.3 0-2.2.9-2.2 2.2 0 1.3.9 2.2 2.2 2.2.6 0 1.1-.2 1.5-.5l1.1 1.7c-.7.5-1.6.8-2.6.8-2.6 0-4.4-1.8-4.4-4.2Z" fill="#172B4D"/>
      <Circle cx={28} cy={14} r={4} fill="#F58220"/>
      <Path d="M33 10h2.2l1.5 5 1.5-5h2.2l-2.7 8h-2L33 10Z" fill="#172B4D"/>
    </Svg>);
}
const marks = {
    visa: Visa,
    mastercard: Mastercard,
    amex: Amex,
    discover: Discover,
};
export function DefaultBrand({ brand, accessibilityLabel }) {
    if (brand === 'unknown') {
        return (<View style={frame} accessible accessibilityLabel={accessibilityLabel} pointerEvents="none">
        <Text style={{ color: '#A8A8AE', fontSize: 18 }}>?</Text>
      </View>);
    }
    const Mark = marks[brand];
    return (<View style={frame} accessible accessibilityLabel={accessibilityLabel} pointerEvents="none">
      <Mark />
    </View>);
}
//# sourceMappingURL=brands.js.map