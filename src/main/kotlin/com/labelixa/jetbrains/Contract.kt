package com.labelixa.jetbrains

/**
 * WIRE VOCABULARY: the API's own field names, in one file.
 *
 * Some fields of the Labelixa API are named in Turkish (`mesaj`, `komutlar`,
 * `quickfix.baslik`). They are PROTOCOL: renaming them here would not
 * translate anything, it would simply stop reading the server's answer. So
 * they are not translated, and they are not hidden either.
 *
 * They live in this single file so the publish guard can exempt exactly one
 * file from the "no Turkish words" rule instead of losing that rule across
 * the whole package. Everything else (identifiers, comments, user facing
 * text) stays English, and the guard still checks this file for ticket ids,
 * non-English characters and audit markers.
 *
 * Adding a name here is a claim that the server really sends it. Measure
 * first (https://labelixa.com/docs/api).
 */
object Contract {
    /** Diagnostics response. */
    const val FINDINGS = "diagnostics"
    const val MESSAGE = "mesaj"
    const val MESSAGE_KEY = "message_key"
    /** Absolute address of the rule's documentation page; absent for codes
     * without a page (analyzer notes). */
    const val RULE_URL = "url"

    /** Quick-fix object inside a finding. */
    const val FIX = "quickfix"
    const val FIX_TITLE = "baslik"
    const val FIX_START = "offset"
    const val FIX_END = "end_offset"
    const val FIX_TEXT = "yeni"
}
