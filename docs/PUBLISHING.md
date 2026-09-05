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
npm publish --dry-run
```

Audit the tarball before attaching it and its checksum to a GitHub prerelease.
Do not run `npm publish` in the initial distribution phase. Do not create a
production release until external PCI review is approved.
