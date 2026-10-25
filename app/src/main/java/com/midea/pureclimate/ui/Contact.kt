package com.midea.pureclimate.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build

/** Where support requests go, and the email draft people get when they tap the address. */
object Contact {
    const val NAME = "gendev"
    const val EMAIL = "support.gendev@gmail.com"
    const val SUBJECT = "PureClimate Support"

    /** Draft message body. The info block at the bottom helps fix problems faster. */
    fun draft(versionLabel: String, hasIrBlaster: Boolean): String = buildString {
        append("Hi $NAME,\n\n")
        append("What do you need help with? (delete the lines that don't apply)\n")
        append("- Something isn't working\n")
        append("- I have an idea or request\n")
        append("- I have a question\n\n")
        append("Please describe it here:\n\n\n\n")
        append("My air conditioner (brand / model):\n\n")
        append("-- Info that helps me fix things (please leave this) --\n")
        append("App: PureClimate, $versionLabel\n")
        append("Phone: ${Build.MANUFACTURER} ${Build.MODEL}\n")
        append("Android: ${Build.VERSION.RELEASE}\n")
        append("IR blaster: ${if (hasIrBlaster) "yes" else "no"}\n")
    }

    /**
     * Opens the person's email app with a ready-to-send draft addressed to support.
     * Returns false if the phone has no email app.
     */
    fun compose(context: Context, versionLabel: String, hasIrBlaster: Boolean): Boolean {
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:")
            putExtra(Intent.EXTRA_EMAIL, arrayOf(EMAIL))
            putExtra(Intent.EXTRA_SUBJECT, SUBJECT)
            putExtra(Intent.EXTRA_TEXT, draft(versionLabel, hasIrBlaster))
        }
        return try {
            context.startActivity(intent)
            true
        } catch (e: ActivityNotFoundException) {
            false
        }
    }
}
