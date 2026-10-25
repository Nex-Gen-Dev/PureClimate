# PureClimate

An Android IR remote for air conditioners, built for Midea and 80+ other AC protocols. It uses the phone's built-in IR blaster.

## Install

Every push to `main` builds an APK through GitHub Actions and publishes it as a **Release**. Open the repo's *Releases* page on your phone and tap the `.apk`.

Builds are signed with a fixed key (`app/pureclimate-debug.keystore`), so each new APK installs over the previous one.

The app updates itself: on launch it checks the latest release and offers **Install now / Later**. Versions are simple numbers (Version 10, Version 11, …). Internally the release tag and Android `versionCode` are `build-N`, where N is the GitHub run number + 100, and the app shows N − 101 (build 111 is Version 10). Keep the `101` in sync across `app/build.gradle`, `UpdateManager.VERSION_OFFSET` and `.github/workflows/build.yml`.

## Features

- **Remote**: a single-screen, no-scroll remote with an LCD readout, power, temperature rocker (17–30 °C), MODE / FAN / SWING / ECO, and three themes.
- **Rooms**: one independent remote per room, stored on the phone.
- **Find my AC**: sends a power-on test from each protocol (optionally filtered by brand) until your AC beeps, then confirms with power-off. Gives full state control without needing the model number.
- **Choose brand**: browse and test protocols by brand.
- **Learn from remote**: point the original remote at an ESP32 IR receiver plugged into the phone. PureClimate identifies the protocol and switches to full control. If it can't, it saves the raw signals as named buttons.

## How the IR codes are made

Phone IR blasters only send raw timings, so every protocol detail matters: bit order, checksums, repeats, and toggles. Rather than hand-porting protocols, `tools/codegen` compiles the reference library [IRremoteESP8266](https://github.com/crankyoldgit/IRremoteESP8266) (LGPL-2.1). It records the exact timings it sends for every power, mode, temperature, fan and swing combination (561 states × 82 protocol variants) and packs them into `app/src/main/assets/codebook.pcb` (≈270 KB). A Java test checks every packed frame bit-for-bit against the reference.

To regenerate: `tools/codegen/regenerate.sh`.

## IR receiver for learn mode

Phone IR blasters can't receive. For learn mode you need:

- An ESP32 dev board and a 38 kHz IR receiver module (VS1838B / TSOP38238), about $5 total
- Wiring: **OUT → GPIO 14, VCC → 3V3, GND → GND**
- Firmware: `firmware/esp32-ir-receiver/esp32-ir-receiver.ino` (Arduino IDE + the *IRremoteESP8266* library)
- A USB OTG adapter to connect the ESP32 to the phone

It prints one `IR:<µs>,<µs>,…` line per captured signal at 115200 baud.

## License

PureClimate is released under the [MIT License](LICENSE), except for third-party material, which stays under its own license (see below).

## Credits and third-party licenses

The AC code table and the USB library come from open-source projects with their own licenses. See [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md). Support: gendev, support.gendev@gmail.com.
