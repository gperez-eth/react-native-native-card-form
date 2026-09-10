#!/usr/bin/env node

const fs = require('node:fs');
const path = require('node:path');
const { execFileSync } = require('node:child_process');

const root = path.resolve(__dirname, '..');
const rawOutput = execFileSync('npm', ['pack', '--json', '--ignore-scripts'], {
  cwd: root,
  encoding: 'utf8',
});
// `--ignore-scripts` does not reliably suppress the `prepare` lifecycle script
// across npm versions (observed with the npm bundled with Node 20.20.2 on
// GitHub-hosted runners): its stdout can land ahead of the JSON payload that
// `npm pack --json` prints. `npm pack --json`'s only output is a single
// top-level array, always the last thing written, so slicing from the first
// `[` discards any such leading noise without masking a genuinely malformed
// response (a missing `[` still throws, as it should).
const jsonStart = rawOutput.indexOf('[');
if (jsonStart === -1) {
  throw new Error(`npm pack --json produced no JSON array:\n${rawOutput}`);
}
const output = JSON.parse(rawOutput.slice(jsonStart));
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
