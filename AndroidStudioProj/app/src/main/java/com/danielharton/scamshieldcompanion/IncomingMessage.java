package com.danielharton.scamshieldcompanion;

/** One message taken from a WhatsApp notification. Lives only in memory. */
public final class IncomingMessage {
    public final String packageName;
    public final String senderName;   // contact name, or the number itself for unknown senders
    public final String phoneNumber;  // null when WhatsApp does not expose it
    public final String text;
    public final boolean knownContact;

    public IncomingMessage(String packageName, String senderName, String phoneNumber, String text, boolean knownContact) {
        this.packageName = packageName;
        this.senderName = senderName;
        this.phoneNumber = phoneNumber;
        this.text = text;
        this.knownContact = knownContact;
    }
}
