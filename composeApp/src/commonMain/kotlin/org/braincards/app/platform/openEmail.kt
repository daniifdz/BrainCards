package org.braincards.app.platform

expect fun openEmailClient(
    recipient: String,
    subject: String,
    body: String
)