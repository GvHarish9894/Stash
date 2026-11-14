# Releasing Stash

One manual workflow, `.github/workflows/release.yml`: GitHub → Actions → Release → Run workflow (on `main`).
It has not been run yet, and it only appears on GitHub once the file is on `main`.

## What it does

1. Works out the next version (`scripts/next-version.sh`) and writes it to `iosApp/Configuration/Config.xcconfig`,
   the one file both platforms read (`MARKETING_VERSION` = version name, `CURRENT_PROJECT_VERSION` = build number).
2. Builds a signed Android release APK and an unsigned iOS `.ipa`.
3. Commits the version as "Release vX.Y.Z" in the name of whoever ran it, tags it and pushes both to `main`.
4. Publishes a GitHub Release with `Stash-X.Y.Z.apk`, `Stash-X.Y.Z-unsigned.ipa` and generated notes.

If a build fails, nothing is committed, tagged or published.

## The version

| Choice | Result |
|---|---|
| `auto` (default) | From the commits since the last `v*` tag: a breaking commit (`type!:` or `BREAKING CHANGE`) → major; a `feat:` commit → minor; anything else → patch |
| `patch`, `minor`, `major` | That bump, whatever the commits say |

The build number goes up by one each release. With no release tag yet, the version in the file is published as it is
(1.0 becomes 1.0.0). With no commits since the last tag the workflow stops. `scripts/next-version.sh --self-test`
checks the rule.

## Signing

- Android: the keystores are in `~/.stash-signing/` on the machine that created them, never in the repository. Its
  `README.txt` lists the four GitHub secrets the workflow needs (`STASH_RELEASE_KEYSTORE_BASE64`,
  `STASH_RELEASE_KEYSTORE_PASSWORD`, `STASH_RELEASE_KEY_ALIAS`, `STASH_RELEASE_KEY_PASSWORD`) and the commands that
  set them. Back that folder up: without the release key no update can be published for the same app.
- Local builds read `~/.stash-signing/signing.properties` when it exists; otherwise debug builds use the machine's own
  debug key and release builds are unsigned.
- iOS: the `.ipa` is unsigned and cannot be installed as it is. Signing needs an Apple Developer Program account (a
  distribution certificate and provisioning profiles for the app, the share extension and the widget).

## Before the first run

- If `main` is protected, allow the workflow to push the version commit and the tag.
- Set the four secrets.
