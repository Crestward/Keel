// SPDX-License-Identifier: AGPL-3.0-only
package com.keel.ingestion

/**
 * Verified Nigerian bank app package names and SMS sender IDs — April 2026.
 *
 * Package names: cross-referenced against Play Store. Case-sensitive — Android package
 * names are case-sensitive and these are the exact strings the system reports.
 *
 * SMS sender IDs: match case-insensitively AND verify body contains bank keywords.
 * NEVER filter by sender ID alone — IDs vary across MTN/Airtel/Glo/9mobile networks
 * and can change without notice.
 */
object BankPackages {

    val NOTIFICATION_PACKAGES: Set<String> = setOf(
        "com.gtbank.gtworldv1",          // GTWorld (GTBank)
        "com.accessbank.nextgen",         // Access More (Access Bank)
        "com.firstbank.firstmobile",      // FirstMobile (First Bank)
        "com.ubanquity.redd.uba",         // UBA Mobile App (current)
        "com.uba.vericash",               // UBA Mobile Banking (legacy — keep both)
        "com.zenithBank.eazymoney",       // Zenith Bank Mobile — case-sensitive 'B'
        "com.kudabank.app",               // Kuda
        "team.opay.pay",                  // OPay
        "com.transsnet.palmpay",          // PalmPay
        "com.moniepoint.personal",        // Moniepoint Personal Banking
        "com.sterlingng.sterlingmobile",  // Sterling OneBank
        "com.wemabank.alat.prod",         // ALAT by Wema Bank
        "com.vulte.app.diaspora",         // VULTe (Polaris Bank)
        "com.StanbicMobile",              // Stanbic IBTC Mobile — capital S and M
        "com.ceva.ubmobile.stallion",     // UnionMobile (Union Bank)
        "com.ifs.banking.fiid4450",       // Fidelity Bank
        "com.lenddo.mobile.paylater",     // Carbon (formerly Paylater)
        "com.vfd.app",                    // V by VFD
    )

    /**
     * SMS sender IDs — matched case-insensitively.
     * ALWAYS combined with body keyword check before inserting as a bank message.
     */
    val SMS_SENDER_IDS: Set<String> = setOf(
        // HIGH confidence — confirmed from bank docs / user reports
        "GTBank", "GTBANK",
        "Zenith", "ZenithBank",
        "FirstBank", "FIRSTBANK",
        "AccessBnk", "AccessBank",       // 11-char limit causes truncation on some networks
        "UBA", "UBAConnect",
        // MEDIUM confidence — community reports
        "Kuda",
        "OPay",
        "PalmPay",
        "Moniepoint",
        "Sterling",
        "WemaBank", "ALAT",
        "Polaris",
        "StanbicIBTC", "Stanbic",
        "UnionBank",
        "Fidelity",
        "Carbon",
        "VFD",
    )

    /** Lowercase set for case-insensitive matching */
    private val SMS_SENDER_IDS_LOWER: Set<String> = SMS_SENDER_IDS.map { it.lowercase() }.toSet()

    /** Bank-related keywords that must appear in the body for SMS acceptance */
    private val BODY_KEYWORDS = setOf(
        "debit", "credit", "ngn", "₦", "acct", "account", "balance", "bal",
        "transaction", "transfer", "payment", "purchase", "withdrawal",
        "pos", "atm", "airtime", "data",
    )

    fun isBankSender(address: String): Boolean =
        address.lowercase() in SMS_SENDER_IDS_LOWER

    fun hasBankKeywords(body: String): Boolean {
        val lower = body.lowercase()
        return BODY_KEYWORDS.any { lower.contains(it) }
    }

    /** Returns true if this SMS looks like a genuine bank alert */
    fun isBankSms(senderAddress: String, body: String): Boolean =
        isBankSender(senderAddress) && hasBankKeywords(body)
}
