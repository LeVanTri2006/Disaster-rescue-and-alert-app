package com.example.bai1.rescue.routing.network

enum class RoutingApiErrorKind {
    UNAUTHORIZED,       // HTTP 401: Token invalid or expired
    FORBIDDEN,          // HTTP 403: Role not allowed (e.g. not an approved rescuer, admin, or moderator)
    NOT_FOUND,          // HTTP 404: No routing manifest/graph found
    BAD_REQUEST,        // HTTP 400: Malformed client request
    SERVER,             // HTTP 500, 502, 503
    TIMEOUT,            // SocketTimeoutException
    NO_INTERNET,        // IOException / No connectivity
    NO_TOKEN,           // Missing backend access token before request
    PARSE_ERROR,        // Invalid or malformed JSON payload from backend
    UNKNOWN             // Other unexpected runtime error
}

data class RoutingApiError(
    val kind: RoutingApiErrorKind,
    val technicalMessage: String,
    val httpCode: Int? = null
) {
    fun userFriendlyMessage(): String = when (kind) {
        RoutingApiErrorKind.UNAUTHORIZED -> "Phiên làm việc đã hết hạn. Vui lòng đăng nhập lại."
        RoutingApiErrorKind.FORBIDDEN -> "Tài khoản của bạn chưa được cấp quyền truy cập dữ liệu định tuyến cứu hộ (yêu cầu quyền Cứu hộ viên được phê duyệt)."
        RoutingApiErrorKind.NOT_FOUND -> "Chưa có bản đồ định tuyến cứu hộ nào được kích hoạt."
        RoutingApiErrorKind.BAD_REQUEST -> "Yêu cầu không hợp lệ."
        RoutingApiErrorKind.SERVER -> "Máy chủ định tuyến đang gặp sự cố. Vui lòng thử lại sau."
        RoutingApiErrorKind.TIMEOUT -> "Kết nối tới máy chủ định tuyến quá thời gian. Vui lòng thử lại."
        RoutingApiErrorKind.NO_INTERNET -> "Không có kết nối mạng. Vui lòng kiểm tra lại kết nối internet."
        RoutingApiErrorKind.NO_TOKEN -> "Bạn chưa đăng nhập. Vui lòng đăng nhập để tiếp tục."
        RoutingApiErrorKind.PARSE_ERROR -> "Dữ liệu định tuyến từ máy chủ không hợp lệ hoặc không đúng định dạng v2.1.0."
        RoutingApiErrorKind.UNKNOWN -> "Không thể lấy thông tin định tuyến lúc này. Vui lòng thử lại sau."
    }
}

sealed class RoutingApiResult<out T> {
    data class Success<out T>(val data: T) : RoutingApiResult<T>()
    data class Failure(val error: RoutingApiError) : RoutingApiResult<Nothing>()
}

