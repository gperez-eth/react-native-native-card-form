# Public repository and release runbook

The package directory is the clean export root for the dedicated public
repository `gperez-eth/react-native-native-card-form`. Do not push Mackenrow's
history, configuration or sibling files.

## One-time external action

1. Create the public GitHub repository with no generated README or license.
2. Export this directory into a new, orphan history.
3. Verify author, package and podspec all name Guillermo Pérez / `gperez-eth`.
4. Add branch protection and the included CI workflow.
5. Configure a GitHub environment that does not contain Mackenrow secrets.

Creating the public repository requires owner credentials and remains an
explicit external step.

## Prerelease

```sh
npm ci
npm run build
npm pack
shasum -a 256 react-native-native-card-form-*.tgz
npm publish --access public --tag alpha
```

The first prerelease is published as `0.1.0-alpha.0` under the `alpha` tag:

```sh
npm view react-native-native-card-form version dist-tags --json
```

The npm token is stored only as the `NPM_TOKEN` secret in the project's EAS
development, preview and production environments. It is not prefixed with
`EXPO_PUBLIC_` and must never be read by application code or committed to the
repository.

Do not create a production payment release until the external PCI review is
approved.
