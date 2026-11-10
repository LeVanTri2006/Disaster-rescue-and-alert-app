package com.example.bai1.rescue.routing.network

import com.example.bai1.rescue.routing.model.EdgeSafetySummary
import com.example.bai1.rescue.routing.model.GraphDownloadInfo
import com.example.bai1.rescue.routing.model.RoutingBoundingBox
import com.example.bai1.rescue.routing.model.RoutingBundleInfo
import com.example.bai1.rescue.routing.model.RoutingManifest
import com.example.bai1.rescue.routing.model.RoutingStatusResponse
import com.example.bai1.rescue.routing.model.StrictRoutingReadiness
import com.example.bai1.rescue.routing.model.UnsupportedRestrictionsSummary
import org.json.JSONObject

object RoutingJsonParser {

    private const val EXPECTED_MANIFEST_VERSION = "2.1.0"
    private val SHA256_REGEX = Regex("^[a-fA-F0-9]{64}$")

    @Throws(IllegalArgumentException::class)
    fun parseRoutingStatus(rawJson: String, receivedAtElapsedMillis: Long = RoutingTime.elapsedMillis()): RoutingStatusResponse {
        val root = JSONObject(rawJson)
        val dataObj = if (root.has("data") && !root.isNull("data") && root.get("data") is JSONObject && root.getJSONObject("data").has("manifest")) {
            root.getJSONObject("data")
        } else {
            root
        }

        if (!dataObj.has("manifest")) {
            throw IllegalArgumentException("Missing required 'manifest' object in response")
        }
        if (!dataObj.has("download")) {
            throw IllegalArgumentException("Missing required 'download' object in response")
        }

        val manifest = parseManifest(dataObj.getJSONObject("manifest"))
        val download = parseDownloadInfo(dataObj.getJSONObject("download"), receivedAtElapsedMillis)

        val downloadAvailable = dataObj.optBoolean("downloadAvailable", false)
        val strictRoutingReady = dataObj.optBoolean("strictRoutingReady", false)
        val liveHazardReady = dataObj.optBoolean("liveHazardReady", false)
        val canAttemptStrictRouting = dataObj.optBoolean("canAttemptStrictRouting", false)
        val overallReadinessStr = dataObj.optString("overallStrictRoutingReadiness", "NOT_READY_UNRESOLVED_RESTRICTIONS")
        val overallStrictRoutingReadiness = StrictRoutingReadiness.fromString(overallReadinessStr)

        return RoutingStatusResponse(
            manifest = manifest,
            download = download,
            downloadAvailable = downloadAvailable,
            strictRoutingReady = strictRoutingReady,
            liveHazardReady = liveHazardReady,
            canAttemptStrictRouting = canAttemptStrictRouting,
            overallStrictRoutingReadiness = overallStrictRoutingReadiness
        )
    }

    @Throws(IllegalArgumentException::class)
    fun parseManifest(manifestObj: JSONObject): RoutingManifest {
        val manifestVersion = manifestObj.getString("manifestVersion")
        if (manifestVersion != EXPECTED_MANIFEST_VERSION) {
            throw IllegalArgumentException("Unsupported manifestVersion: expected $EXPECTED_MANIFEST_VERSION, got $manifestVersion")
        }

        val graphVersion = com.example.bai1.rescue.routing.download.GraphCacheIdentity.requireValid(
            manifestObj.getString("graphVersion")
        )

        val generatedAt = manifestObj.getString("generatedAt")
        val osmSnapshotTimestamp = manifestObj.getString("osmSnapshotTimestamp")
        val fixtureGeneratedAt = if (manifestObj.has("fixtureGeneratedAt") && !manifestObj.isNull("fixtureGeneratedAt")) {
            manifestObj.getString("fixtureGeneratedAt")
        } else null

        val importerVersion = manifestObj.getString("importerVersion")
        val vehicleProfile = manifestObj.getString("vehicleProfile")
        val boundingBoxType = manifestObj.getString("boundingBoxType")

        val boundingBox = parseBoundingBox(manifestObj.optJSONObject("boundingBox"))
        val targetProductionBoundingBox = parseBoundingBox(manifestObj.optJSONObject("targetProductionBoundingBox"))

        val bundleObj = manifestObj.getJSONObject("bundle")
        val fileName = bundleObj.getString("fileName")
        val compressedSizeBytes = bundleObj.getLong("compressedSizeBytes")
        val uncompressedSizeBytes = bundleObj.getLong("uncompressedSizeBytes")
        val sha256 = bundleObj.getString("sha256").trim().lowercase()

        if (compressedSizeBytes <= 0L) {
            throw IllegalArgumentException("bundle.compressedSizeBytes must be greater than 0")
        }
        if (uncompressedSizeBytes <= 0L) {
            throw IllegalArgumentException("bundle.uncompressedSizeBytes must be greater than 0")
        }
        if (!SHA256_REGEX.matches(sha256)) {
            throw IllegalArgumentException("bundle.sha256 must be a valid 64-character hex string")
        }

        val bundle = RoutingBundleInfo(
            fileName = fileName,
            compressedSizeBytes = compressedSizeBytes,
            uncompressedSizeBytes = uncompressedSizeBytes,
            sha256 = sha256
        )

        val activeHazardSnapshotVersion = if (manifestObj.has("activeHazardSnapshotVersion") && !manifestObj.isNull("activeHazardSnapshotVersion")) {
            manifestObj.getString("activeHazardSnapshotVersion")
        } else null

        val hazardSnapshotStatus = manifestObj.getString("hazardSnapshotStatus")
        val hazardAssessmentPolicy = manifestObj.getString("hazardAssessmentPolicy")

        val isRestrictionStrictRoutingEligible = manifestObj.getBoolean("isRestrictionStrictRoutingEligible")
        val restrictionStrictRoutingReadiness = StrictRoutingReadiness.fromString(manifestObj.getString("restrictionStrictRoutingReadiness"))

        val restrictionsSummaryObj = manifestObj.getJSONObject("unsupportedRestrictionsSummary")
        val unsupportedRestrictionsSummary = UnsupportedRestrictionsSummary(
            totalCount = restrictionsSummaryObj.getInt("totalCount"),
            enforcementPolicy = restrictionsSummaryObj.optString("enforcementPolicy", "FAIL_CLOSED_IN_STRICT_MODE")
        )

        val canAttemptStrictRouting = manifestObj.getBoolean("canAttemptStrictRouting")
        val isStrictRoutingEligible = if (manifestObj.has("isStrictRoutingEligible") && !manifestObj.isNull("isStrictRoutingEligible")) {
            manifestObj.getBoolean("isStrictRoutingEligible")
        } else null

        val overallStrictRoutingReadiness = StrictRoutingReadiness.fromString(manifestObj.getString("overallStrictRoutingReadiness"))
        val strictRoutingReadiness = if (manifestObj.has("strictRoutingReadiness") && !manifestObj.isNull("strictRoutingReadiness")) {
            StrictRoutingReadiness.fromString(manifestObj.getString("strictRoutingReadiness"))
        } else null

        val edgeSafetyObj = manifestObj.getJSONObject("edgeSafetySummary")
        val edgeSafetySummary = EdgeSafetySummary(
            totalEdges = edgeSafetyObj.getLong("totalEdges"),
            verifiedCompatibleEdges = edgeSafetyObj.getLong("verifiedCompatibleEdges"),
            unverifiedEdges = edgeSafetyObj.getLong("unverifiedEdges"),
            blockedEdges = edgeSafetyObj.getLong("blockedEdges"),
            conflictedEdges = edgeSafetyObj.getLong("conflictedEdges"),
            strictSafeEdgesPercent = edgeSafetyObj.getDouble("strictSafeEdgesPercent"),
            blockedWays = edgeSafetyObj.getLong("blockedWays"),
            excludedBlockedWays = if (edgeSafetyObj.has("excludedBlockedWays") && !edgeSafetyObj.isNull("excludedBlockedWays")) {
                edgeSafetyObj.getLong("excludedBlockedWays")
            } else null,
            retainedBlockedWays = if (edgeSafetyObj.has("retainedBlockedWays") && !edgeSafetyObj.isNull("retainedBlockedWays")) {
                edgeSafetyObj.getLong("retainedBlockedWays")
            } else null
        )

        return RoutingManifest(
            schema = if (manifestObj.has("\$schema") && !manifestObj.isNull("\$schema")) manifestObj.getString("\$schema") else null,
            manifestVersion = manifestVersion,
            generatedAt = generatedAt,
            graphVersion = graphVersion,
            osmSnapshotTimestamp = osmSnapshotTimestamp,
            fixtureGeneratedAt = fixtureGeneratedAt,
            importerVersion = importerVersion,
            vehicleProfile = vehicleProfile,
            boundingBoxType = boundingBoxType,
            boundingBox = boundingBox,
            targetProductionBoundingBox = targetProductionBoundingBox,
            bundle = bundle,
            activeHazardSnapshotVersion = activeHazardSnapshotVersion,
            hazardSnapshotStatus = hazardSnapshotStatus,
            hazardAssessmentPolicy = hazardAssessmentPolicy,
            isRestrictionStrictRoutingEligible = isRestrictionStrictRoutingEligible,
            restrictionStrictRoutingReadiness = restrictionStrictRoutingReadiness,
            unsupportedRestrictionsSummary = unsupportedRestrictionsSummary,
            canAttemptStrictRouting = canAttemptStrictRouting,
            isStrictRoutingEligible = isStrictRoutingEligible,
            overallStrictRoutingReadiness = overallStrictRoutingReadiness,
            strictRoutingReadiness = strictRoutingReadiness,
            edgeSafetySummary = edgeSafetySummary
        )
    }

    private fun parseDownloadInfo(downloadObj: JSONObject, receivedAtElapsedMillis: Long): GraphDownloadInfo {
        val url = downloadObj.getString("url")
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            throw IllegalArgumentException("Invalid download URL: must start with http:// or https://")
        }
        val rawTtl = downloadObj.get("expiresIn")
        require(rawTtl is Number && rawTtl.toString().matches(Regex("^[0-9]{1,3}$"))) { "Invalid download TTL" }
        val expiresIn = rawTtl.toString().toLong()
        RoutingTransportPolicy.ttl(expiresIn)
        return GraphDownloadInfo(url = url, expiresIn = expiresIn, receivedAtElapsedMillis = receivedAtElapsedMillis)
    }

    private fun parseBoundingBox(bboxObj: JSONObject?): RoutingBoundingBox? {
        if (bboxObj == null) return null
        return RoutingBoundingBox(
            minLon = bboxObj.getDouble("minLon"),
            minLat = bboxObj.getDouble("minLat"),
            maxLon = bboxObj.getDouble("maxLon"),
            maxLat = bboxObj.getDouble("maxLat"),
            bufferedMeters = if (bboxObj.has("bufferedMeters") && !bboxObj.isNull("bufferedMeters")) {
                bboxObj.getDouble("bufferedMeters")
            } else null
        )
    }
}

