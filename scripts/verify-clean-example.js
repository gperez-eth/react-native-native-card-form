#!/usr/bin/env node

const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { spawnSync } = require('node:child_process');

const platform = process.argv[2];
if (!['android', 'ios'].includes(platform)) {
  console.error('usage: node scripts/verify-clean-example.js <android|ios>');
  process.exit(2);
}
if (platform === 'ios' && process.platform !== 'darwin') {
  console.error('iOS verification requires macOS');
  process.exit(2);
}

const root = path.resolve(__dirname, '..');
const temporary = fs.mkdtempSync(path.join(os.tmpdir(), 'native-card-form-example-'));
const example = path.join(temporary, 'example');

function run(command, args, cwd) {
  const result = spawnSync(command, args, { cwd, stdio: 'inherit', shell: false });
  if (result.status !== 0) process.exit(result.status ?? 1);
}

try {
  run('npm', ['run', 'build'], root);
  run('npm', ['pack', '--pack-destination', temporary], root);
  fs.cpSync(path.join(root, 'example'), example, { recursive: true });
  run('npm', ['install'], example);
  run('npm', ['ls', '@stripe/stripe-react-native', '--all'], example);

  const installedPackage = path.join(example, 'node_modules', 'react-native-native-card-form');
  for (const relativePath of [
    'src/index.ts',
    'src/NativeCardForm.tsx',
    'src/brands.tsx',
    'build/index.js',
    'build/NativeCardForm.js',
    'build/brands.js',
  ]) {
    if (!fs.existsSync(path.join(installedPackage, relativePath))) {
      throw new Error(`installed tarball is missing ${relativePath}`);
    }
  }

  run('npx', ['expo', 'prebuild', '--platform', platform, '--no-install'], example);

  if (platform === 'android') {
    run('./gradlew', [':native-card-form:compileDebugKotlin', 'assembleDebug', '--console=plain'], path.join(example, 'android'));
  } else {
    run('pod', ['install'], path.join(example, 'ios'));
    run(
      'xcodebuild',
      [
        '-workspace', 'NativeCardFormExample.xcworkspace',
        '-scheme', 'NativeCardFormExample',
        '-configuration', 'Debug',
        '-sdk', 'iphonesimulator',
        '-destination', 'generic/platform=iOS Simulator',
        'IPHONEOS_DEPLOYMENT_TARGET=15.1',
        'CODE_SIGNING_ALLOWED=NO',
        'build',
      ],
      path.join(example, 'ios'),
    );
  }
  console.log(`clean ${platform} tarball example: PASS (${temporary})`);
} finally {
  if (!process.env.KEEP_NATIVE_CARD_EXAMPLE) fs.rmSync(temporary, { recursive: true, force: true });
}
