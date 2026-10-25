package com.midea.pureclimate.ui

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** The pages behind Settings > About. Everything is plain text; only the email address is tappable. */
enum class InfoPage(val title: String, val subtitle: String) {
    ABOUT("About PureClimate", "What it does"),
    PRIVACY("Privacy Policy", "What stays on your phone"),
    LICENSES("Licenses", "Open-source credits"),
    CONTACT("Contact", "Questions and feedback"),
}

@Composable
fun InfoScreen(
    page: InfoPage,
    versionLabel: String,
    protocolCount: Int,
    irOk: Boolean,
    onBack: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        ScreenBar(page.title, onBack)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            when (page) {
                InfoPage.ABOUT -> AboutContent(versionLabel, protocolCount)
                InfoPage.PRIVACY -> PrivacyContent()
                InfoPage.LICENSES -> LicensesContent()
                InfoPage.CONTACT -> ContactContent(versionLabel, irOk)
            }
            Gap(28.dp)
        }
    }
}

// ---- small text building blocks ----------------------------------------------------

@Composable
private fun Heading(text: String) {
    val p = LocalPalette.current
    Gap(18.dp)
    Text(text, color = p.text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
    Gap(6.dp)
}

@Composable
private fun Para(text: String) {
    val p = LocalPalette.current
    Text(text, color = p.textMuted, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(bottom = 8.dp))
}

@Composable
private fun Bullet(text: String) {
    val p = LocalPalette.current
    Row(Modifier.padding(bottom = 6.dp)) {
        Text("•", color = p.accent, fontSize = 14.sp, modifier = Modifier.padding(end = 10.dp))
        Text(text, color = p.textMuted, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun Legal(text: String) {
    val p = LocalPalette.current
    Text(
        text,
        color = p.textMuted,
        fontSize = 11.sp,
        lineHeight = 15.sp,
        fontFamily = FontFamily.Monospace,
        modifier = Modifier.padding(bottom = 8.dp),
    )
}

// ---- About -------------------------------------------------------------------------

@Composable
private fun AboutContent(versionLabel: String, protocolCount: Int) {
    val p = LocalPalette.current
    Gap(8.dp)
    Text("PureClimate", color = p.text, fontSize = 26.sp, fontWeight = FontWeight.Bold)
    Text(versionLabel, color = p.textMuted, fontSize = 15.sp)
    Gap(14.dp)
    Para(
        "PureClimate turns your phone's infrared (IR) blaster into a remote control for your air conditioner. " +
            "It is built for Midea and works with many other brands" +
            (if (protocolCount > 0) " ($protocolCount AC protocols are built in)" else "") + ".",
    )
    Heading("What it can do")
    Bullet("One remote for every room, with power, temperature, mode, fan, swing and eco.")
    Bullet("Find my AC: tries codes one by one until your air conditioner responds, so you never need the exact model.")
    Bullet("Learn from remote: copy the buttons of your original remote using an optional ESP32 IR receiver.")
    Bullet("Three looks to choose from, and updates that arrive by themselves.")
    Heading("Good to know")
    Para("Your phone needs a built-in IR blaster. Most iPhones and many Android phones don't have one.")
    Para(
        "PureClimate is an independent app. It is not made by, or affiliated with, Midea or any other " +
            "air-conditioner brand. Brand names are used only to describe which air conditioners it can control.",
    )
    Heading("Made by")
    Para("gendev")
}

// ---- Privacy -----------------------------------------------------------------------

@Composable
private fun PrivacyContent() {
    val p = LocalPalette.current
    Gap(8.dp)
    Text("Last updated: October 25, 2026", color = p.textMuted, fontSize = 12.sp)
    Heading("The short version")
    Para(
        "PureClimate does not collect, sell or share your personal information. There are no accounts, " +
            "no ads, no analytics and no tracking.",
    )
    Heading("What stays on your phone")
    Para(
        "Your room names, air-conditioner settings, learned remote buttons, chosen theme and update " +
            "preference are saved on your phone only. Deleting the app deletes them.",
    )
    Para(
        "If Android backup is turned on, your phone may include this information in your device backup. " +
            "That backup is handled by Google or your phone's maker, not by PureClimate.",
    )
    Heading("Internet use")
    Para(
        "PureClimate uses the internet for one thing: checking whether a new version is available and " +
            "downloading it. To do this it contacts GitHub, where PureClimate's releases are published.",
    )
    Para(
        "Like any website or server, GitHub can see your IP address and basic technical details of the " +
            "request. PureClimate sends no names, no room data and nothing else about you. GitHub's own " +
            "privacy statement applies to what GitHub does with its server records.",
    )
    Para("You can turn off automatic checks in Settings. The app works fully without internet.")
    Heading("Permissions")
    Bullet("Infrared: to send remote-control signals to your air conditioner.")
    Bullet("Internet: to check for and download updates.")
    Bullet("Install apps: so you can install an update. Android asks you first, and nothing is installed unless you tap Install.")
    Bullet("USB: only if you connect the optional IR receiver. What it reads stays on your phone.")
    Heading("Emailing support")
    Para(
        "If you tap the email address in Contact, your email app opens with a draft that includes your " +
            "app version and phone model to help with troubleshooting. You can edit or delete anything " +
            "before sending. Nothing is sent unless you send it.",
    )
    Heading("Children")
    Para("PureClimate collects no personal information from anyone, including children.")
    Heading("Changes")
    Para("If this policy changes, the new version will be in the app with a new date above.")
    Heading("Questions")
    Para("Write to gendev at support.gendev@gmail.com.")
}

// ---- Licenses ----------------------------------------------------------------------

private const val MIT_TEXT = """MIT License

Copyright (c) 2011-2013 Google Inc.
Copyright (c) 2013 Mike Wakerly

Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE."""

@Composable
private fun LicensesContent() {
    Gap(8.dp)
    Para("PureClimate itself is open source, released under the MIT License. Copyright (c) 2026 gendev.")
    Para("PureClimate stands on the work of these open-source projects. Thank you to everyone who built them.")

    Heading("IRremoteESP8266")
    Para("github.com/crankyoldgit/IRremoteESP8266")
    Para(
        "The air-conditioner signals in PureClimate were produced by running this library for every " +
            "supported setting and recording exactly what it sends. Its protocol research covers dozens of " +
            "brands. Original work by Ken Shirriff, Mark Szabo, Sebastien Warin, David Conran and many " +
            "contributors.",
    )
    Para(
        "Licensed under the GNU Lesser General Public License, version 2.1 (LGPL-2.1). PureClimate does not " +
            "contain the library's program code, only a table of signal timings produced by running it. " +
            "The tools used to generate that table, and the full license text, are published with " +
            "PureClimate's source code (github.com/Nex-Gen-Dev/PureClimate).",
    )

    Heading("USB Serial for Android")
    Para("github.com/mik3y/usb-serial-for-android")
    Para("Used to talk to the optional IR receiver over USB. Licensed under the MIT License:")
    Legal(MIT_TEXT)

    Heading("Android Jetpack, Compose and Material")
    Para("developer.android.com/jetpack")
    Para(
        "Copyright The Android Open Source Project. Licensed under the Apache License, Version 2.0 " +
            "(apache.org/licenses/LICENSE-2.0).",
    )

    Heading("Kotlin and kotlinx.coroutines")
    Para("kotlinlang.org")
    Para("Copyright JetBrains s.r.o. and contributors. Licensed under the Apache License, Version 2.0.")

    Heading("No warranty")
    Para(
        "The software is provided as is, without warranty of any kind. Use PureClimate at your own risk. " +
            "It sends the same kind of signals as the remote that came with your air conditioner.",
    )
}

// ---- Contact -----------------------------------------------------------------------

@Composable
private fun ContactContent(versionLabel: String, irOk: Boolean) {
    val p = LocalPalette.current
    val context = LocalContext.current
    Gap(8.dp)
    Para("Found a bug, have an idea, or just need a hand? Get in touch.")
    Gap(6.dp)
    Panel(Modifier.fillMaxWidth()) {
        Column {
            SectionLabel("Developer")
            Gap(4.dp)
            Text(Contact.NAME, color = p.text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Gap(16.dp)
            SectionLabel("Email")
            Gap(6.dp)
            Row(
                Modifier.clip(RoundedCornerShape(10.dp)).clickable {
                    if (!Contact.compose(context, versionLabel, irOk)) {
                        Toast.makeText(
                            context,
                            "No email app found. You can write to ${Contact.EMAIL} from any email service.",
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                }.padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Email, contentDescription = "Write an email", tint = p.accent, modifier = Modifier.size(22.dp))
                HGap(10.dp)
                Text(
                    Contact.EMAIL,
                    color = p.accent,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    textDecoration = TextDecoration.Underline,
                )
            }
        }
    }
    Gap(12.dp)
    Para(
        "Tap the email address and your email app opens with a message already filled in. Just describe " +
            "what's happening and send it.",
    )
}
