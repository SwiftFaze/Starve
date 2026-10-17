package dev.starve.web

import dev.starve.domain.AccountRules

/**
 * The id of the access-form field a rejection message is about, so the form can mark that field invalid and tie the
 * message to it; null for a message about no one field (too many attempts). [newPassword] is the id of the form's
 * new-password field.
 */
fun invalidFieldOf(
    message: String,
    newPassword: String,
): String? =
    when (message) {
        AccountRules.USERNAME_ERROR, AccountRules.USERNAME_TAKEN -> "username"
        AccountRules.PASSWORD_SHORT, AccountRules.PASSWORD_LONG -> newPassword
        AccountRules.INVALID_SETUP_CODE -> "code"
        AccountRules.CURRENT_PASSWORD_WRONG -> "current-password"
        else -> null
    }
