# Third-party notices

PureClimate is built on the open-source projects below. The same credits appear in the app under
**Settings → About → Licenses**.

## IRremoteESP8266

- Source: <https://github.com/crankyoldgit/IRremoteESP8266>
- License: GNU Lesser General Public License, version 2.1. Full text: [`licenses/LGPL-2.1-IRremoteESP8266.txt`](licenses/LGPL-2.1-IRremoteESP8266.txt)
- Authors: Ken Shirriff, Mark Szabo, Sebastien Warin, David Conran and many contributors.

The air-conditioner signals in `app/src/main/assets/codebook.pcb` were produced by compiling this library
and recording the exact timings it sends for every power, mode, temperature, fan and swing combination.
The app contains that table of timings, not the library's program code. The tools that generate the table
are in [`tools/codegen`](tools/codegen) so anyone can reproduce it.

## USB Serial for Android

- Source: <https://github.com/mik3y/usb-serial-for-android>
- License: MIT. Full text: [`licenses/MIT-usb-serial-for-android.txt`](licenses/MIT-usb-serial-for-android.txt)
- Copyright (c) 2011-2013 Google Inc., (c) 2013 Mike Wakerly.

Used to read signals from the optional ESP32 IR receiver over USB.

## Android Jetpack, Compose and Material

Copyright The Android Open Source Project. Apache License, Version 2.0 (<https://www.apache.org/licenses/LICENSE-2.0>).

## Kotlin and kotlinx.coroutines

Copyright JetBrains s.r.o. and contributors. Apache License, Version 2.0.
