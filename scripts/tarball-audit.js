#!/usr/bin/env node

const fs = require('node:fs');
const path = require('node:path');
const { execFileSync } = require('node:child_process');

const root = path.resolve(__dirname, '..');
const output = JSON.parse(
  execFileSync('npm', ['pack', '--json', '--ignore-scripts'], { cwd: root, encoding: 'utf8' }),
);
const artifact = output[0];
const names = artifact.files.map((file) => file.path);
const required = [
  'LICENSE',
  'README.md',
  'build/index.js',
  'build/index.d.ts',
  'build/NativeCardForm.js',
  'build/brands.js',
  'src/NativeCardForm.tsx',
  'src/brands.tsx',
  'expo-module.config.json',
  'android/build.gradle',
  'ios/ReactNativeNativeCardForm.podspec',
];
const forbidden = [
  /^example\//,
  /^\.github\//,
  /mackenrow-app/i,
  /\.env/i,
  /\.(?:pem|keystore|jks)$/i,
];
const failures = required.filter((name) => !names.includes(name)).map((name) => `missing ${name}`);
for (const name of names) {
  if (forbidden.some((pattern) => pattern.test(name))) failures.push(`forbidden tar entry ${name}`);
}

const packageJson = JSON.parse(fs.readFileSync(path.join(root, 'package.json'), 'utf8'));
if (packageJson.license !== 'MIT') failures.push('package license is not MIT');
if (!packageJson.exports || !packageJson.types) failures.push('exports or declarations entry missing');
if (packageJson.dependencies?.['@stripe/stripe-react-native']) failures.push('Stripe must be a peer, not bundled');

fs.rmSync(path.join(root, artifact.filename));
if (failures.length) {
  console.error(failures.join('\n'));
  process.exit(1);
}
console.log(`tarball audit: ${artifact.filename}, ${artifact.files.length} allowlisted files`);
