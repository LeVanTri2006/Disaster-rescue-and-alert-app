package com.example.bai1.rescue.routing.model

/**
 * Chính sách thời hạn hiệu lực và độ tươi mới (Freshness Policy) cho đánh giá ngập lụt thực địa.
 *
 * @property maxAllowedTtlMillis Thời gian sống tối đa (TTL = expiresAt - verifiedAt) được phép theo quy định hệ thống.
 * @property maxAgeMillis Tuổi thọ tối đa cho phép kể từ thời điểm thẩm định thực địa (currentEpochMillis - verifiedAt).
 */
data class FloodFreshnessPolicy(
    val maxAllowedTtlMillis: Long,
    val maxAgeMillis: Long
) {
    init {
        require(maxAllowedTtlMillis > 0L) { "maxAllowedTtlMillis phải là số dương (> 0)" }
        require(maxAgeMillis > 0L) { "maxAgeMillis phải là số dương (> 0)" }
    }
}

/**
 * Giao diện xác minh nguồn gốc thẩm quyền và tính toàn vẹn (Trust & Provenance Validator).
 */
interface FloodAssessmentTrustValidator {
    /**
     * Xác minh xem [assessment] có đến từ nguồn có thẩm quyền được tin cậy và có provenance hợp lệ hay không.
     */
    fun isTrusted(assessment: LiveFloodAssessment): Boolean
}

/**
 * Giao diện xác minh chữ ký số hoặc mã chứng thực nguồn gốc (Provenance Signature Verifier)
 * cho đánh giá ngập lụt thực địa.
 */
interface FloodAssessmentProvenanceVerifier {
    /**
     * Xác minh chữ ký số / mã chứng thực nguồn gốc gắn với [assessment].
     *
     * @param assessment Đối tượng thẩm định ngập thực địa.
     * @return true nếu chữ ký hợp lệ và bảo đảm tính toàn vẹn của assessment; false nếu không hợp lệ hoặc bị lỗi.
     */
    fun verifyProvenance(assessment: LiveFloodAssessment): Boolean
}

/**
 * Hiện thực mặc định: Fail-Closed tuyệt đối khi hệ thống chưa được cấu hình cơ chế xác minh chữ ký số thực thụ.
 */
object FailClosedFloodAssessmentProvenanceVerifier : FloodAssessmentProvenanceVerifier {
    override fun verifyProvenance(assessment: LiveFloodAssessment): Boolean = false
}

/**
 * Hiện thực kiểm tra tin cậy dựa trên danh sách nguồn được hệ thống cấp phép (Trusted Source Policy)
 * và bộ xác minh chữ ký nguồn gốc (Provenance Verifier).
 *
 * TIÊU CHUẨN AN TOÀN FAIL-CLOSED:
 * 1. Bắt buộc [trustedSourceIds] không rỗng và [assessment.authorizedSource] phải thuộc danh sách này.
 *    Nguồn không nằm trong policy luôn bị từ chối, kể cả khi chữ ký hợp lệ.
 * 2. Bắt buộc [provenanceVerifier] phải được cấu hình và khác [FailClosedFloodAssessmentProvenanceVerifier].
 *    Nếu verifier chưa được cấu hình hoặc là null -> từ chối (Fail-Closed).
 * 3. Bắt buộc [assessment.provenanceSignature] không được null hoặc rỗng (blank).
 * 4. Quyết định an toàn bắt buộc phải dựa trên kết quả xác minh từ [provenanceVerifier.verifyProvenance].
 *    Tuyệt đối KHÔNG tin cậy cờ Boolean tự khai báo [assessment.hasValidProvenance] để bypass cổng an toàn.
 * 5. Bọc try-catch toàn bộ quá trình gọi verifier để bảo đảm Fail-Closed khi có ngoại lệ phát sinh.
 *
 * @property trustedSourceIds Danh sách định danh các cơ quan/nguồn có thẩm quyền được phê duyệt.
 * @property provenanceVerifier Bộ xác minh chữ ký nguồn gốc. Mặc định là [FailClosedFloodAssessmentProvenanceVerifier].
 */
class PolicyBasedFloodAssessmentTrustValidator(
    val trustedSourceIds: Set<String> = emptySet(),
    val provenanceVerifier: FloodAssessmentProvenanceVerifier? = FailClosedFloodAssessmentProvenanceVerifier
) : FloodAssessmentTrustValidator {

    /**
     * Constructor tương thích ngược hỗ trợ cờ requireProvenance.
     * Nếu requireProvenance == true nhưng không truyền verifier, hệ thống fail-closed với [FailClosedFloodAssessmentProvenanceVerifier].
     */
    constructor(
        trustedSourceIds: Set<String>,
        requireProvenance: Boolean
    ) : this(
        trustedSourceIds = trustedSourceIds,
        provenanceVerifier = if (requireProvenance) FailClosedFloodAssessmentProvenanceVerifier else null
    )

    override fun isTrusted(assessment: LiveFloodAssessment): Boolean {
        // 1. Policy bắt buộc phải có danh sách nguồn có thẩm quyền
        if (trustedSourceIds.isEmpty()) return false

        // 2. Nguồn của assessment phải nằm trong danh sách trustedSourceIds
        val source = assessment.authorizedSource.trim()
        if (source.isEmpty() || !trustedSourceIds.contains(source)) return false

        // 3. Verifier chưa được cấu hình hoặc là verifier fail-closed mặc định -> từ chối
        val verifier = provenanceVerifier ?: return false
        if (verifier is FailClosedFloodAssessmentProvenanceVerifier) return false

        // 4. Chữ ký số bắt buộc phải tồn tại và không rỗng
        val signature = assessment.provenanceSignature?.trim()
        if (signature.isNullOrEmpty()) return false

        // 5. Tuyệt đối KHÔNG tin hasValidProvenance tự khai báo.
        // Quyết định tin cậy ủy quyền hoàn toàn cho verifier chuyên trách; bắt mọi exception để fail-closed.
        return try {
            verifier.verifyProvenance(assessment)
        } catch (_: Throwable) {
            false
        }
    }
}

/**
 * Validator mặc định của hệ thống: Fail-closed tuyệt đối, từ chối mọi assessment khi chưa có cấu hình chứng thực.
 */
object FailClosedFloodAssessmentTrustValidator : FloodAssessmentTrustValidator {
    override fun isTrusted(assessment: LiveFloodAssessment): Boolean = false
}

/**
 * Ngữ cảnh thẩm định hiểm họa và ngập lụt thời gian thực (Hazard & Flood Assessment Context) trong định tuyến cứu hộ.
 *
 * @property liveHazardReady Cờ báo hiệu tầng dữ liệu hiểm họa thời gian thực đã sẵn sàng và được đồng bộ.
 * @property activeHazardSnapshotVersion Mã phiên bản ảnh chụp hiểm họa hiện hành (bắt buộc phải có để đối chiếu).
 * @property freshnessPolicy Chính sách kiểm tra độ tươi mới và thời hạn hiệu lực. Nếu null, hệ thống fail-closed.
 * @property trustValidator Bộ xác thực nguồn tin cậy và provenance. Mặc định là [FailClosedFloodAssessmentTrustValidator].
 * @property currentEpochMillis Mốc thời gian hệ thống hiện hành dùng để đối chiếu hiệu lực (epoch millis).
 */
data class FloodAssessmentContext(
    val liveHazardReady: Boolean = false,
    val activeHazardSnapshotVersion: String? = null,
    val freshnessPolicy: FloodFreshnessPolicy? = null,
    val trustValidator: FloodAssessmentTrustValidator = FailClosedFloodAssessmentTrustValidator,
    val currentEpochMillis: Long = System.currentTimeMillis()
)

/**
 * Đánh giá hiện hành từ thực địa về tình trạng ngập lụt của đoạn đường (Live Field Assessment).
 *
 * TIÊU CHUẨN AN TOÀN CỨU HỘ BẮT BUỘC (FAIL-CLOSED ROAD CONDITION SAFETY GATE):
 * Trong chế độ định tuyến cứu hộ nghiêm ngặt (Strict Rescue Routing), một đoạn đường FLOODED
 * TUYỆT ĐỐI KHÔNG được phép lưu thông nếu thiếu đánh giá thực địa có thẩm quyền và còn hiệu lực.
 *
 * Tuyệt đối phân biệt:
 * 1. Vehicle compatibility: Khả năng tương thích tĩnh của hạ tầng đường với RESCUE_TRUCK_4X4.
 * 2. Strict routing eligibility: Cờ tĩnh của cạnh trong đồ thị OSM.
 * 3. Live road condition: Tình trạng giao thông thời gian thực (CLEAR / FLOODED / BLOCKED / UNKNOWN).
 * 4. Live Field verification: Bằng chứng thẩm định thực địa hiện hành có thẩm quyền.
 *
 * @property edgeId Định danh chính xác của đoạn đường được thẩm định (RoadEdge.id).
 * @property authorizedSource Cơ quan hoặc nguồn thẩm định (ví dụ: "BAN_CHI_HUY_PCTT_DA_NANG", "RESCUE_TEAM_UNIT_1").
 * @property verifiedAtEpochMillis Thời điểm đo đạc / thẩm định thực địa (epoch timestamp milliseconds).
 * @property expiresAtEpochMillis Thời hạn hiệu lực tối đa của đánh giá (epoch timestamp milliseconds).
 * @property isPassableForRescueTruck Kết luận thẩm định rõ ràng rằng xe cứu hộ (RESCUE_TRUCK_4X4) có thể lưu thông an toàn.
 * @property maxWaterDepthMeters Độ sâu ngập nước thực tế đo được tại thời điểm thẩm định (mét) nếu có.
 * @property hazardSnapshotVersion Phiên bản ảnh chụp hiểm họa (hazard snapshot) mà đánh giá này trực thuộc.
 * @property hasValidProvenance Cờ xác thực nguồn gốc và tính toàn vẹn từ backend/nguồn có thẩm quyền.
 * @property provenanceSignature Chữ ký số hoặc mã chứng thực nguồn gốc nếu có.
 */
data class LiveFloodAssessment(
    val edgeId: String,
    val authorizedSource: String,
    val verifiedAtEpochMillis: Long,
    val expiresAtEpochMillis: Long,
    val isPassableForRescueTruck: Boolean,
    val maxWaterDepthMeters: Double? = null,
    val hazardSnapshotVersion: String? = null,
    val hasValidProvenance: Boolean = false,
    val provenanceSignature: String? = null
) {
    /**
     * Tạo chuỗi dữ liệu chuẩn hóa (canonical payload string) chứa toàn bộ các trường trọng yếu
     * liên quan trực tiếp đến quyết định an toàn định tuyến để phục vụ việc ký và xác minh chữ ký:
     * - edgeId
     * - authorizedSource
     * - hazardSnapshotVersion
     * - verifiedAtEpochMillis
     * - expiresAtEpochMillis
     * - isPassableForRescueTruck
     * - maxWaterDepthMeters
     *
     * Bất kỳ sự thay đổi nào đối với các trường này sau khi ký đều làm thay đổi chuỗi payload chuẩn hóa,
     * dẫn đến việc xác minh chữ ký thất bại.
     */
    fun createCanonicalProvenancePayload(): String {
        return buildString {
            append("edgeId=").append(edgeId).append(";")
            append("source=").append(authorizedSource.trim()).append(";")
            append("snapshot=").append(hazardSnapshotVersion?.trim() ?: "").append(";")
            append("verifiedAt=").append(verifiedAtEpochMillis).append(";")
            append("expiresAt=").append(expiresAtEpochMillis).append(";")
            append("passable=").append(isPassableForRescueTruck).append(";")
            append("depth=").append(maxWaterDepthMeters?.toString() ?: "null")
        }
    }

    /**
     * Kiểm tra tính hợp lệ về độ tươi mới (Freshness) của đánh giá theo [policy] tại thời điểm [currentEpochMillis].
     */
    fun isFreshAccordingToPolicy(
        currentEpochMillis: Long,
        policy: FloodFreshnessPolicy
    ): Boolean {
        // 1. verifiedAt không được nằm trong tương lai
        if (verifiedAtEpochMillis <= 0L || verifiedAtEpochMillis > currentEpochMillis) return false

        // 2. expiresAt phải lớn hơn verifiedAt
        if (expiresAtEpochMillis <= verifiedAtEpochMillis) return false

        // 3. currentEpochMillis phải nằm trong khoảng hiệu lực [verifiedAtEpochMillis, expiresAtEpochMillis]
        if (currentEpochMillis < verifiedAtEpochMillis || currentEpochMillis > expiresAtEpochMillis) return false

        // 4. TTL (expiresAt - verifiedAt) không được dài hơn mức trần chính sách quy định
        val ttl = expiresAtEpochMillis - verifiedAtEpochMillis
        if (ttl > policy.maxAllowedTtlMillis) return false

        // 5. Tuổi tối đa (currentEpochMillis - verifiedAt) không được vượt quá maxAgeMillis
        val age = currentEpochMillis - verifiedAtEpochMillis
        if (age > policy.maxAgeMillis) return false

        return true
    }
}
