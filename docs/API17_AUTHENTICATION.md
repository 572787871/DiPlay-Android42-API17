# Android 4.2 / API 17 authentication preservation

This API 17 compatibility branch keeps the DiPlay authentication flow intact. It does not
create, modify, bypass, or publish an accessory identity.

## Reference-package comparison

The supplied Android 4.4 reference package was inspected by file name and native-library
metadata only. It contains these authentication asset paths:

```text
assets/offline-mfi/identity.pk8
assets/offline-mfi/certificate.p7b
```

It also contains the `libxcertplay_i2c.so` and `liblocal_hotspot_radio.so` JNI libraries for
`armeabi-v7a`. The API 17 source package retains the corresponding native interfaces and builds
them for `armeabi-v7a`. Credential bytes from the reference APK were not extracted, copied, or
used in this repository.

## Retained loading and initialization path

The standalone path is deliberately identical on API 17 and newer Android versions:

1. Gradle accepts an explicitly supplied directory through `DIPLAY_AUTH_ASSETS_DIR`.
2. The directory must contain only these required files:

   ```text
   offline-mfi/identity.pk8
   offline-mfi/certificate.p7b
   ```

3. `:mobile:verifyStandaloneAuthentication` checks that both files are present and non-empty.
4. `DiPlayBootstrap.ensure()` copies them from the signed APK assets into the app-private
   `offline-mfi` directory, validates the P-256 key/certificate pair, and installs it atomically.
   API 17 uses `filesDir`; API 21 and newer use `noBackupFilesDir`.
5. `CarPlayController` opens the installed identity through
   `LocalMfiAuthenticationClient`. The iAP2 wired and wireless control clients continue to use
   the same `MfiAuthenticationClient` interface.

If validation fails, the app fails closed. It does not substitute another identity or report a
successful authentication.

## Building a standalone APK locally

Keep authorized assets outside the source tree and build with:

```sh
DIPLAY_AUTH_ASSETS_DIR=/absolute/path/to/runtime-assets \
  ./gradlew :mobile:assembleStandaloneDebug
```

The directory is ignored by Git, and the build rejects unexpected key/certificate containers.

## Building in GitHub Actions

The default workflow produces a source-only ARMv7 package with no accessory identity. It is safe
to publish and is useful for API 17 installation/startup validation.

To create an authorized standalone package, a repository administrator must add these repository
secrets:

| Secret | Purpose |
| --- | --- |
| `DIPLAY_MFI_IDENTITY_PK8_B64` | Base64 of the authorized `identity.pk8` file |
| `DIPLAY_MFI_CERTIFICATE_P7B_B64` | Base64 of the matching authorized certificate |
| `DIPLAY_ANDROID_KEYSTORE_B64` | Base64 of the signing keystore used for updates |
| `DIPLAY_ANDROID_KEYSTORE_PASSWORD` | Keystore password |
| `DIPLAY_ANDROID_KEY_ALIAS` | Signing alias |
| `DIPLAY_ANDROID_KEY_PASSWORD` | Key password |

The **Build Android 4.2 API17 Standalone APK** workflow expands those files only into the runner
temporary directory, passes the authentication directory through `DIPLAY_AUTH_ASSETS_DIR`, and
verifies the packaged bytes against the supplied inputs before upload. Do not put credentials or
a signing keystore in the repository or a public release.

## API 17 compatibility boundaries

Authentication itself uses Java cryptography and app-private files, which are available on API 17.
The compatibility branch keeps the app's legacy USB host, Bluetooth, manual-hotspot, and Wi-Fi
Direct code paths. On API 17 there are no runtime permissions: the wireless permission list is
empty and Android uses the manifest-granted legacy Bluetooth/Wi-Fi permissions. Android 12+
continues to request `BLUETOOTH_CONNECT`, and Android 13+ requests
`NEARBY_WIFI_DEVICES` only for Wi-Fi Direct modes.

Actual CarPlay pairing still requires a legally provisioned identity, compatible vehicle hardware,
and a head-unit Wi-Fi/USB driver. These cannot be fully validated by a source build or emulator.
