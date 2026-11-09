package com.example.bai1.rescue.routing.repository

import com.example.bai1.rescue.routing.download.GraphDownloadError
import com.example.bai1.rescue.routing.download.GraphDownloadErrorKind
import com.example.bai1.rescue.routing.download.GraphDownloadResult
import com.example.bai1.rescue.routing.download.GraphDownloadState
import com.example.bai1.rescue.routing.download.GraphDownloader
import com.example.bai1.rescue.routing.model.RoutingStatusResponse
import com.example.bai1.rescue.routing.network.RoutingApiClient
import com.example.bai1.rescue.routing.network.RoutingApiError
import com.example.bai1.rescue.routing.network.RoutingApiResult
import kotlinx.coroutines.CancellationException
import com.example.bai1.rescue.routing.network.RoutingApiErrorKind
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

interface RoutingRepository {
    val currentRoutingStatus: StateFlow<RoutingStatusResponse?>
    val isLoading: StateFlow<Boolean>
    val lastError: StateFlow<RoutingApiError?>
    val downloadState: StateFlow<GraphDownloadState>

    /**
     * Lấy trạng thái định tuyến hiện hành từ backend.
     * Cung cấp manifest v2.1.0 và presigned download URL từ Cloudflare R2.
     *
     * LƯU Ý BẢO MẬT VÀ NGHIỆP VỤ:
     * - downloadAvailable = true chỉ biểu thị file đồ thị có thể tải về từ R2.
     * - TUYỆT ĐỐI KHÔNG coi downloadAvailable = true là điều kiện đủ để bật chỉ đường.
     * - Transport metadata never authorizes strict routing without production authority.
     */
    suspend fun getCurrentRouting(): RoutingApiResult<RoutingStatusResponse>

    /** Refresh only while the originating routing session/status is still current. */
    suspend fun refreshExpiredDownloadStatus(previous: RoutingStatusResponse): RoutingApiResult<RoutingStatusResponse> =
        RoutingApiResult.Failure(RoutingApiError(RoutingApiErrorKind.NO_TOKEN, "Routing lease refresh unavailable"))

    /**
     * Tải và giải nén graph từ presigned URL hiện tại.
     */
    suspend fun downloadGraph(forceRefresh: Boolean = false): GraphDownloadResult

    /**
     * Tải và giải nén graph, tự động làm mới presigned URL từ backend nếu URL bị hết hạn.
     */
    suspend fun downloadGraphWithAutoRefresh(forceRefresh: Boolean = false): GraphDownloadResult

    /**
     * Kiểm tra xem graph hiện hành đã có file giải nén hợp lệ trong cache hay chưa.
     */
    fun isCurrentGraphCached(): Boolean

    /**
     * Lấy file đồ thị đã giải nén nếu có trong cache.
     */
    fun getCachedGraphFile(): File?
}

class DefaultRoutingRepository(
    private val apiClient: RoutingApiClient,
    private val graphDownloader: GraphDownloader? = null
) : RoutingRepository {

    private val _currentRoutingStatus = MutableStateFlow<RoutingStatusResponse?>(null)
    override val currentRoutingStatus: StateFlow<RoutingStatusResponse?> = _currentRoutingStatus.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    override val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _lastError = MutableStateFlow<RoutingApiError?>(null)
    override val lastError: StateFlow<RoutingApiError?> = _lastError.asStateFlow()

    override val downloadState: StateFlow<GraphDownloadState>
        get() = graphDownloader?.state ?: MutableStateFlow(GraphDownloadState.Idle).asStateFlow()

    private val sessionLock = Any()
    private var generation = 0L

    /** Revoke only routing memory; preserve graph files and unrelated user data. */
    fun invalidateSession() = synchronized(sessionLock) {
        generation++
        _currentRoutingStatus.value = null
        _lastError.value = null
        _isLoading.value = false
    }

    override suspend fun getCurrentRouting(): RoutingApiResult<RoutingStatusResponse> = fetchCurrentRouting(null)

    override suspend fun refreshExpiredDownloadStatus(previous: RoutingStatusResponse): RoutingApiResult<RoutingStatusResponse> {
        val expected = synchronized(sessionLock) { if (_currentRoutingStatus.value === previous) generation else null }
            ?: return RoutingApiResult.Failure(RoutingApiError(RoutingApiErrorKind.NO_TOKEN, "Routing session invalidated"))
        return fetchCurrentRouting(expected)
    }

    private suspend fun fetchCurrentRouting(expectedGeneration: Long?): RoutingApiResult<RoutingStatusResponse> {
        val requestGeneration = synchronized(sessionLock) {
            if (expectedGeneration != null && expectedGeneration != generation) return@synchronized null
            generation++ // New refresh also supersedes any older response.
            _currentRoutingStatus.value = null
            _isLoading.value = true
            _lastError.value = null
            generation
        } ?: return RoutingApiResult.Failure(RoutingApiError(RoutingApiErrorKind.NO_TOKEN, "Routing session invalidated"))
        val result = try {
            apiClient.getCurrentRouting()
        } catch (e: CancellationException) {
            synchronized(sessionLock) {
                if (generation == requestGeneration) _isLoading.value = false
            }
            throw e
        } catch (e: Exception) {
            RoutingApiResult.Failure(RoutingApiError(RoutingApiErrorKind.UNKNOWN, "Routing request failed"))
        }
        return synchronized(sessionLock) {
            if (generation != requestGeneration) {
                RoutingApiResult.Failure(RoutingApiError(RoutingApiErrorKind.NO_TOKEN, "Routing session or request invalidated"))
            } else {
                _isLoading.value = false
                when (result) {
                    is RoutingApiResult.Success -> {
                        // Download-only projection; nested metadata is retained for diagnostics, never credentials.
                        val data = result.data.copy(strictRoutingReady = false, canAttemptStrictRouting = false,
                            liveHazardReady = false,
                            overallStrictRoutingReadiness = com.example.bai1.rescue.routing.model.StrictRoutingReadiness.NOT_READY_NO_VERIFIED_EDGES)
                        _currentRoutingStatus.value = data
                        RoutingApiResult.Success(data)
                    }
                    is RoutingApiResult.Failure -> {
                        _currentRoutingStatus.value = null
                        _lastError.value = result.error
                        result
                    }
                }
            }
        }
    }

    override suspend fun downloadGraph(forceRefresh: Boolean): GraphDownloadResult = prepareDownload(forceRefresh, false)

    override suspend fun downloadGraphWithAutoRefresh(forceRefresh: Boolean): GraphDownloadResult = prepareDownload(forceRefresh, true)

    private fun unavailableDownload() = GraphDownloadResult.Failure(GraphDownloadError(
        GraphDownloadErrorKind.PRECONDITION_FAILED, "Routing download unavailable or session invalidated"))

    private suspend fun prepareDownload(forceRefresh: Boolean, autoRefresh: Boolean): GraphDownloadResult {
        val downloader = graphDownloader ?: return unavailableDownload()
        var status = _currentRoutingStatus.value ?: when (val fetched = getCurrentRouting()) {
            is RoutingApiResult.Success -> fetched.data
            is RoutingApiResult.Failure -> return unavailableDownload()
        }
        var epoch = synchronized(sessionLock) { if (_currentRoutingStatus.value === status) generation else null }
            ?: return unavailableDownload()
        var result = downloader.downloadAndPrepareGraph(status, forceRefresh)
        if (synchronized(sessionLock) { generation != epoch }) return unavailableDownload()
        // Exactly one authorized refresh and one retry; no recursive URL retry loop.
        if (autoRefresh && result is GraphDownloadResult.Failure && result.error.kind == GraphDownloadErrorKind.URL_EXPIRED) {
            when (val fresh = refreshExpiredDownloadStatus(status)) {
                is RoutingApiResult.Failure -> return result
                is RoutingApiResult.Success -> status = fresh.data
            }
            epoch = synchronized(sessionLock) { if (_currentRoutingStatus.value === status) generation else null }
                ?: return unavailableDownload()
            result = downloader.downloadAndPrepareGraph(status, forceRefresh)
            if (synchronized(sessionLock) { generation != epoch }) return unavailableDownload()
        }
        return result
    }

    override fun isCurrentGraphCached(): Boolean {
        val manifest = _currentRoutingStatus.value?.manifest ?: return false
        return graphDownloader?.isGraphCached(manifest) ?: false
    }

    override fun getCachedGraphFile(): File? {
        val manifest = _currentRoutingStatus.value?.manifest ?: return null
        return graphDownloader?.getCachedGraphFile(manifest)
    }
}
