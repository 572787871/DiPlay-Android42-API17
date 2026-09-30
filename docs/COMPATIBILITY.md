# Compatibility

This public preview is an independent receiver, not an Apple-certified CarPlay accessory. The experimental bundled accessory identity is extractable and its future acceptance is not guaranteed.

| Area | Current scope |
| --- | --- |
| Head unit | Mobile APK: Android 4.4+ (API 19); Automotive APK remains Android 9+ |
| Phone | Standard, non-jailbroken iPhone with CarPlay enabled; device/iOS compatibility varies |
| Physical evidence | Previous private builds: wired and wireless picture, touch and audio confirmed on the development car with iPhone XS / iOS 18.7.10 |
| Other cars | Mixed community reports across DiLink generations; not a certified model support list |
| Current release | DiLink5.1: HUD/street names and Car hotspot confirmed; Wi-Fi Direct improved, occasional audio cutouts remain |
| Wi-Fi | Prefer 5 GHz without an established station connection; align to a supported existing station channel; explicit 2.4 GHz fallback for firmware that rejects 5 GHz or automatic channel selection |
| Video | Default H.264 / 30 fps; 60 fps and HEVC increase device-specific demands |

## Legacy Android transport matrix

| Android version | Wired USB | Manual car hotspot | LocalOnlyHotspot | Wi-Fi Direct group |
| --- | --- | --- | --- | --- |
| 4.4 (API 19) | Compatibility USB control/request backend | Yes | Platform unavailable | Platform configuration unavailable |
| 5–7 (API 21–25) | Framework USB backend | Yes | Platform unavailable | Falls back to manual hotspot |
| 8–9 (API 26–28) | Framework USB backend | Yes | Yes | Falls back to LocalOnlyHotspot |
| 10+ (API 29+) | Framework USB backend | Yes | Yes | Yes |

The feature choice remains visible across versions, but a mode that the operating system cannot provide is mapped to the closest supported backend. The classic View UI, legacy media-button receiver, pre-channel notifications, pre-23 audio recording/playback, pre-21 codec buffers, multidex and desugared Java APIs keep the same application flow available on API 19.

The API 19, 21, 24 and 27 emulator matrix validates installation, activity creation, native-library loading and absence of class-verification/API-level crashes. USB, MFi, Bluetooth handoff, radio behavior, HUD integration and sustained audio/video still require physical head-unit and iPhone testing; an emulator cannot validate those peripherals.

## BYD HUD and car hotspot

See [BYD navigation](BYD_NAVIGATION.md) for the exact verified firmware and lifecycle limits. Car hotspot now starts CarPlay on the development car using scoped IPv6. The phone must join the configured car hotspot. Neither result guarantees support on every firmware.

## Known limitations

- Some units stutter, particularly under higher video load. A 2.4 GHz link alone does not prove the cause: interference, firmware and decoder stalls can all contribute. Try Default icons, 30 fps and a lower resolution, then attach a report.
- Some iOS/head-unit combinations do not visibly apply icon and text size. Reconnection is implemented; that does not guarantee the iPhone chooses the requested layout.
- A radio that supports joining a 5 GHz network may still reject a 5 GHz Wi-Fi Direct group. The capability flag is diagnostic, not proof of group-owner support.
- Automatic startup depends on the car's firmware and startup permissions.
- USB requires a data port and correct host/device-role behavior.
- Calls, Siri, background reconnection, long journeys and future iOS releases need broader testing.
- Source-only debug builds intentionally omit the MFi identity. They run for development and diagnostics but cannot authenticate a CarPlay session until the external assets described in [the build guide](BUILD.md) are supplied.

Reports record requested and actual frequencies, station association state, fallback failures and remembered-configuration events. Wi-Fi credentials and protocol payloads are excluded. A successful hotspot is not itself a successful CarPlay session.

Android references: [SupplicantState](https://developer.android.com/reference/android/net/wifi/SupplicantState), [explicit P2P operating frequency](https://developer.android.com/reference/android/net/wifi/p2p/WifiP2pConfig.Builder#setGroupOperatingFrequency(int)).
