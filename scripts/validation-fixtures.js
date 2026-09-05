#!/usr/bin/env node

const fixtures = require('../fixtures/card-validation.json');

const digits = (value) => value.replace(/\D/g, '');
const luhn = (value) => {
  let sum = 0;
  let alternate = false;
  for (let index = value.length - 1; index >= 0; index -= 1) {
    let digit = Number(value[index]);
    if (alternate) {
      digit *= 2;
      if (digit > 9) digit -= 9;
    }
    sum += digit;
    alternate = !alternate;
  }
  return sum % 10 === 0;
};

function brandFor(input) {
  const value = digits(input);
  if (/^4/.test(value)) return 'visa';
  if (/^(34|37)/.test(value)) return 'amex';
  const prefix4 = Number(value.slice(0, 4));
  if (prefix4 >= 3528 && prefix4 <= 3589) return 'jcb';
  if (value.startsWith('62')) return 'unionpay';
  const prefix2 = Number(value.slice(0, 2));
  if ((value.length >= 2 && prefix2 >= 51 && prefix2 <= 55) ||
      (value.length >= 4 && prefix4 >= 2221 && prefix4 <= 2720)) return 'mastercard';
  const prefix3 = Number(value.slice(0, 3));
  const prefix6 = Number(value.slice(0, 6));
  if (
    value.startsWith('6011') ||
    value.startsWith('65') ||
    (value.length >= 3 && prefix3 >= 644 && prefix3 <= 649) ||
    (value.length >= 6 && prefix6 >= 622126 && prefix6 <= 622925)
  ) return 'discover';
  if (value.startsWith('50') ||
      (value.length >= 2 && ((prefix2 >= 56 && prefix2 <= 61) || (prefix2 >= 63 && prefix2 <= 69)))) {
    return 'maestro';
  }
  return 'unknown';
}

function numberStatus(input) {
  const value = digits(input);
  if (!value) return 'empty';
  const brand = brandFor(value);
  const expected =
    brand === 'amex' ? [15] :
    brand === 'visa' ? [13, 16, 19] :
    brand === 'jcb' || brand === 'unionpay' ? [16, 17, 18, 19] :
    brand === 'maestro' ? Array.from({ length: 8 }, (_, index) => index + 12) :
    [16];
  const max = Math.max(...expected);
  if (value.length < Math.min(...expected)) return 'incomplete';
  if (value.length > max) return 'invalid';
  if (!expected.includes(value.length)) return 'incomplete';
  return luhn(value) ? 'valid' : 'invalid';
}

function expiryStatus(input) {
  const value = digits(input);
  if (!value) return 'empty';
  if (value.length < 2) return 'incomplete';
  const month = Number(value.slice(0, 2));
  if (month < 1 || month > 12) return 'invalid';
  if (value.length < 4) return 'incomplete';
  const year = 2000 + Number(value.slice(2, 4));
  return year > fixtures.clock.year ||
    (year === fixtures.clock.year && month >= fixtures.clock.month)
    ? 'valid'
    : 'invalid';
}

function cvcStatus(input, brand) {
  const value = digits(input);
  if (!value) return 'empty';
  const expected = brand === 'amex' ? 4 : 3;
  if (value.length < expected) return 'incomplete';
  return value.length === expected ? 'valid' : 'invalid';
}

const failures = [];
for (const fixture of fixtures.numbers) {
  if (brandFor(fixture.input) !== fixture.brand || numberStatus(fixture.input) !== fixture.status) {
    failures.push(`number fixture failed: ${fixture.input || '<empty>'}`);
  }
}
for (const fixture of fixtures.expiry) {
  if (expiryStatus(fixture.input) !== fixture.status) failures.push(`expiry fixture failed: ${fixture.input || '<empty>'}`);
}
for (const fixture of fixtures.cvc) {
  if (cvcStatus(fixture.input, fixture.brand) !== fixture.status) failures.push(`cvc fixture failed: ${fixture.input || '<empty>'}`);
}

if (failures.length) {
  console.error(failures.join('\n'));
  process.exit(1);
}
console.log(`validation fixtures: ${fixtures.numbers.length + fixtures.expiry.length + fixtures.cvc.length} passed`);
