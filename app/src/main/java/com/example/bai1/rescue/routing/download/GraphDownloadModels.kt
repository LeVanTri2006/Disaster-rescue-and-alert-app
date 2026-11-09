package com.example.bai1.rescue.routing.download

import java.io.File

/**
 * Trạng thái của tiến trình tải và xác thực đồ thị.
 */
sealed class GraphDownloadState {
    object Idle : GraphDownloadState()

    data class Downloading(
        val bytesDownloaded: Long,
        val totalBytes: Long
    ) : GraphDownloadState()

    object Verifying : GraphDownloadState()

    data class Decompressing(
        val bytesDecompressed: Long,
        val targetBytes: Long
    ) : GraphDownloadState()

    data class Completed(
        val graphFile: File,
        val graphVersion: String,
        val uncompressedBytes: Long,
        val fromCache: Boolean = false
    ) : GraphDownloadState()

    data class Failed(
        val error: GraphDownloadError
    ) : GraphDownloadState()
}

/**
 * Các loại lỗi trong quá trình tải, xác thực SHA-256 và giải nén GZIP.
 */
enum class GraphDownloadErrorKind {
    TRANSPORT_REJECTED,
    GRAPH_VALIDATION_FAILED,
    DOWNLOAD_FAILED,
    DOWNLOAD_TIMEOUT,
    URL_EXPIRED,
    SIZE_MISMATCH,
    CHECKSUM_MISMATCH,
    GZIP_INVALID,
    DECOMPRESSED_SIZE_MISMATCH,
    STORAGE_ERROR,
    PRECONDITION_FAILED,
    MAX_SIZE_EXCEEDED
}

/**
 * Thông tin lỗi chi tiết của Graph Downloader.
 */
data class GraphDownloadError(
    val kind: GraphDownloadErrorKind,
    val technicalMessage: String,
    val httpCode: Int? = null,
    val cause: Throwable? = null
) {
    fun userFriendlyMessage(): String = when (kind) {
        GraphDownloadErrorKind.TRANSPORT_REJECTED -> "Phản hồi tải bản đồ không đáp ứng hợp đồng truyền dữ liệu."
        GraphDownloadErrorKind.GRAPH_VALIDATION_FAILED -> "Dữ liệu đồ thị không hợp lệ hoặc không khớp bản kê khai."
        GraphDownloadErrorKind.DOWNLOAD_FAILED -> "Tải dữ liệu bản đồ cứu hộ thất bại. Vui lòng thử lại sau."
        GraphDownloadErrorKind.DOWNLOAD_TIMEOUT -> "Thời gian tải bản đồ quá lâu. Vui lòng kiểm tra lại kết nối mạng."
        GraphDownloadErrorKind.URL_EXPIRED -> "Liên kết tải bản đồ đã hết hạn. Đang lấy liên kết mới từ máy chủ."
        GraphDownloadErrorKind.SIZE_MISMATCH -> "Kích thước gói bản đồ tải về không khớp với thông số từ máy chủ."
        GraphDownloadErrorKind.CHECKSUM_MISMATCH -> "Kiểm tra tính toàn vẹn SHA-256 thất bại. Dữ liệu bản đồ có thể bị lỗi khi truyền."
        GraphDownloadErrorKind.GZIP_INVALID -> "Gói nén bản đồ bị lỗi và không thể giải nén."
        GraphDownloadErrorKind.DECOMPRESSED_SIZE_MISMATCH -> "Dung lượng bản đồ sau khi giải nén không khớp với cam kết từ hệ thống."
        GraphDownloadErrorKind.STORAGE_ERROR -> "Không thể ghi dữ liệu bản đồ vào bộ nhớ thiết bị. Vui lòng kiểm tra dung lượng trống."
        GraphDownloadErrorKind.PRECONDITION_FAILED -> "Hệ thống chưa sẵn sàng cho phép tải bản đồ cứu hộ."
        GraphDownloadErrorKind.MAX_SIZE_EXCEEDED -> "Dung lượng giải nén vượt quá giới hạn an toàn cho phép."
    }
}

/**
 * Đại diện cho artifact đồ thị đã được tải về, giải nén và xác minh toàn vẹn SHA-256 thành công.
 *
 * @property file File JSON đồ thị đã giải nén trên thiết bị.
 * @property graphVersion Phiên bản đồ thị.
 * @property verifiedSha256 Mã băm SHA-256 thực tế đã được tính toán và đối khớp với manifest.
 * @property fromCache Đánh dấu artifact được nạp từ cache hợp lệ hay tải mới.
 */
data class VerifiedGraphArtifact(
    val file: File,
    val graphVersion: String,
    val verifiedSha256: String,
    val fromCache: Boolean = false
) {
    init {
        require(graphVersion.isNotBlank()) { "graphVersion must not be blank" }
        require(verifiedSha256.isNotBlank()) { "verifiedSha256 must not be blank" }
    }
}

/**
 * Kết quả trả về của tiến trình tải và giải nén đồ thị.
 */
sealed class GraphDownloadResult {
    data class Success(
        val file: File,
        val graphVersion: String,
        val fromCache: Boolean = false,
        val verifiedSha256: String = "",
        /** Immutable parser result from the exact JSON stream bound to the verified GZIP. */
        val parsedGraph: com.example.bai1.rescue.routing.model.RoadGraph? = null
    ) : GraphDownloadResult() {
        val artifact: VerifiedGraphArtifact
            get() = VerifiedGraphArtifact(
                file = file,
                graphVersion = graphVersion,
                verifiedSha256 = verifiedSha256,
                fromCache = fromCache
            )
    }

    data class Failure(
        val error: GraphDownloadError
    ) : GraphDownloadResult()
}

