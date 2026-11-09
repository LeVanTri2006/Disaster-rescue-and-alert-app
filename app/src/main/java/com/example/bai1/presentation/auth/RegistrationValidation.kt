package com.example.bai1.presentation.auth

/** UI checks for the existing register DTO plus the form's confirmation/consent controls.
 * Phone remains the DTO's 8..15-character string contract; no new country/digit policy.
 * At least one contact is required; each supplied contact is checked independently.
 */
fun registrationValidationError(
    fullName: String, email: String, phone: String, password: String,
    confirmPassword: String, agreeTerms: Boolean
): String? = when {
    fullName.isBlank() -> "Vui lòng nhập họ và tên."
    fullName.trim().length > 100 -> "Họ và tên không được quá 100 ký tự."
    email.isBlank() && phone.isBlank() -> "Vui lòng nhập email hoặc số điện thoại."
    email.isNotBlank() && !Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$").matches(email.trim()) -> "Email không hợp lệ."
    phone.isNotBlank() && phone.trim().length !in 8..15 -> "Số điện thoại phải có từ 8 đến 15 ký tự."
    password.length < 6 -> "Mật khẩu phải có ít nhất 6 ký tự."
    password != confirmPassword -> "Mật khẩu xác nhận không khớp."
    !agreeTerms -> "Vui lòng đồng ý với Điều khoản sử dụng và Chính sách bảo mật."
    else -> null
}
