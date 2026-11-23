# Build DS Forever Ext

## GitHub Actions

Run **Android test release** from the Actions tab. A successful manual run produces
the `ds-forever-ext-apk` artifact. It includes the APK, `SHA256SUMS`, signing
certificate details and a build receipt.
Select **publish** to publish the verified APK directly from Actions after all
gates pass. This avoids downloading and re-uploading the large APK locally.
Leave it off for an artifact-only test build.

Pushing a tag such as `v1.0.37-ext.2` builds, tests and publishes a prerelease.
First update `versionName` and increase `versionCode` in `app/build.gradle.kts`.
The tag and `versionName` must match. Keep the `DS-Forever-<version>.apk` asset
name: the Ext updater uses it.

The repository must have these Actions secrets:

| Secret | Value |
| --- | --- |
| `EXT_KEYSTORE_BASE64` | Base64-encoded fork PKCS12 signing keystore |
| `EXT_KEYSTORE_PASSWORD` | Keystore and key password |

The signing alias is `ds-forever-ext`. Preserve the existing signing key. A new
key cannot update installed Ext builds. Never use the original DS Forever key.
Do not commit keys or put passwords in workflow commands.

The release gate requires this public signing certificate SHA-256:

```text
2703b1fd09c7dc3fa45e9e8235771bb6bf78a10d0644d976c3cc8302573c1b3e
```

## Linux build

Use JDK 17, Python 3, CMake, Ninja and the Android SDK. Install SDK platform 36,
build-tools 36.1.0 and NDK 27.2.12479018. Set `ANDROID_HOME`.

```bash
bash tools/ci_bootstrap.sh
bash gradlew :app:assembleModernDebug
```

The bootstrap downloads the four hash-pinned upstream runtime archives. It builds
CascLib, Recast, Whisper and the browser-login helper. It also restores the pinned
Moonlight static dependencies. Game files and accounts are not build inputs.

Debug builds use a local debug key unless the upstream `WC_KEY*` environment
variables are supplied. Do not install a debug build over a signed release.

## Release checks

The workflow runs driver import/selection tests, independent FPS measurement and
counter-action tests, Questie migration and menu tests, Ext version/update tests,
Lua 5.1 compilation and the add-on manifest check. It
also checks APK signing, package identity, runtime archive hashes, required native
libraries, post-R8 native callbacks and the upstream game-art exclusion gate.

These checks do not prove that a driver or the game works on an AYN Thor. Test on
hardware before treating a build as stable. See [test instructions](RELEASE_EXT.md).
