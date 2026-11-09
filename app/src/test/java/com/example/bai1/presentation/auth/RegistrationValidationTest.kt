package com.example.bai1.presentation.auth

import org.junit.Assert.*
import org.junit.Test

class RegistrationValidationTest {
    private fun error(name: String = "Fixture Person", email: String = "fixture@example.invalid", phone: String = "",
        password: String = "fixture-password", confirmation: String = password, terms: Boolean = true) =
        registrationValidationError(name, email, phone, password, confirmation, terms)
    @Test fun matchingEmailRegistrationIsAccepted() { assertNull(error()) }
    @Test fun phoneOnlyUsesExistingDtoLengthContract() { assertNull(error(email = "", phone = "00000000")); assertNull(error(email = "", phone = "123456789012345")) }
    @Test fun confirmationMustMatchWithoutTrimmingSecrets() { assertNotNull(error(confirmation = "different")); assertNotNull(error(confirmation = "fixture-password ")) }
    @Test fun uncheckedExistingConsentBlocksSubmission() { assertNotNull(error(terms = false)) }
    @Test fun blankRequiredNameIsRejected() { assertNotNull(error(name = "  ")) }
    @Test fun nameLengthIsBoundedByDto() { assertNotNull(error(name = "a".repeat(101))); assertNull(error(name = "a".repeat(100))) }
    @Test fun atLeastOneContactIsRequired() { assertNotNull(error(email = "", phone = "")) }
    @Test fun malformedSuppliedEmailIsRejectedEvenWithPhone() { assertNotNull(error(email = "bad", phone = "00000000")); assertNotNull(error(email = "a b@example.invalid")) }
    @Test fun phoneLengthIsCheckedWhenSupplied() { assertNotNull(error(phone = "1234567")); assertNotNull(error(phone = "1".repeat(16))) }
    @Test fun minimumPasswordLengthMatchesDto() { assertNotNull(error(password = "12345")); assertNull(error(password = "123456")) }
}
