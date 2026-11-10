package com.example.bai1.rescue.routing

import com.example.bai1.rescue.routing.model.StrictRoutingReadiness
import com.example.bai1.rescue.routing.network.RoutingJsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutingJsonParserTest {

    private val validProductionResponseJson = """
    {
      "manifest": {
        "${'$'}schema": "https://emergency-rescue.vn/schemas/v2/routing-manifest.json",
        "manifestVersion": "2.1.0",
        "generatedAt": "2026-11-04T12:24:50.392Z",
        "graphVersion": "dn_central_real_v1",
        "osmSnapshotTimestamp": "2026-11-03T21:24:23Z",
        "importerVersion": "osm-importer-v2.3-prototype",
        "vehicleProfile": "RESCUE_TRUCK_4X4",
        "boundingBoxType": "PRODUCTION",
        "boundingBox": {
          "minLon": 108.19,
          "minLat": 16.035,
          "maxLon": 108.235,
          "maxLat": 16.075,
          "bufferedMeters": 500.0
        },
        "targetProductionBoundingBox": {
          "minLon": 108.19,
          "minLat": 16.035,
          "maxLon": 108.235,
          "maxLat": 16.075,
          "bufferedMeters": 500.0
        },
        "bundle": {
          "fileName": "BaseRoadGraph.json.gz",
          "compressedSizeBytes": 1173443,
          "uncompressedSizeBytes": 33002111,
          "sha256": "9792dce0abb089a230bcb79e71b2a8ff83301879d417f4fd399711ce4c3a037b"
        },
        "activeHazardSnapshotVersion": null,
        "hazardSnapshotStatus": "NOT_INCLUDED",
        "hazardAssessmentPolicy": "HAZARD_DATA_REQUIRED_FOR_LIVE_CONDITIONS",
        "isRestrictionStrictRoutingEligible": false,
        "restrictionStrictRoutingReadiness": "NOT_READY_UNRESOLVED_RESTRICTIONS",
        "unsupportedRestrictionsSummary": {
          "totalCount": 16,
          "enforcementPolicy": "FAIL_CLOSED_IN_STRICT_MODE"
        },
        "canAttemptStrictRouting": false,
        "isStrictRoutingEligible": false,
        "overallStrictRoutingReadiness": "NOT_READY_UNRESOLVED_RESTRICTIONS",
        "strictRoutingReadiness": "NOT_READY_UNRESOLVED_RESTRICTIONS",
        "edgeSafetySummary": {
          "totalEdges": 16651,
          "verifiedCompatibleEdges": 0,
          "unverifiedEdges": 16651,
          "blockedEdges": 0,
          "conflictedEdges": 0,
          "strictSafeEdgesPercent": 0.0,
          "blockedWays": 1425,
          "excludedBlockedWays": 1425,
          "retainedBlockedWays": 0
        }
      },
      "download": {
        "url": "https://cloudflare-r2.example.com/graphs/dn_central_real_v1/BaseRoadGraph.json.gz?signature=abc123xyz",
        "expiresIn": 900
      },
      "downloadAvailable": true,
      "strictRoutingReady": false,
      "liveHazardReady": false,
      "canAttemptStrictRouting": false,
      "overallStrictRoutingReadiness": "NOT_READY_UNRESOLVED_RESTRICTIONS"
    }
    """.trimIndent()

    @Test
    fun `parses complete production routing response correctly`() {
        val response = RoutingJsonParser.parseRoutingStatus(validProductionResponseJson)

        // Top-level fields
        assertEquals(true, response.downloadAvailable)
        assertEquals(false, response.strictRoutingReady)
        assertEquals(false, response.liveHazardReady)
        assertEquals(false, response.canAttemptStrictRouting)
        assertEquals(StrictRoutingReadiness.NOT_READY_UNRESOLVED_RESTRICTIONS, response.overallStrictRoutingReadiness)
        assertFalse("downloadAvailable alone must not permit navigation", response.canNavigateSafely)

        // Download info
        assertEquals("https://cloudflare-r2.example.com/graphs/dn_central_real_v1/BaseRoadGraph.json.gz?signature=abc123xyz", response.download.url)
        assertEquals(900L, response.download.expiresIn)

        // Manifest metadata
        val manifest = response.manifest
        assertEquals("2.1.0", manifest.manifestVersion)
        assertEquals("dn_central_real_v1", manifest.graphVersion)
        assertEquals("2026-11-04T12:24:50.392Z", manifest.generatedAt)
        assertEquals("2026-11-03T21:24:23Z", manifest.osmSnapshotTimestamp)
        assertEquals("osm-importer-v2.3-prototype", manifest.importerVersion)
        assertEquals("RESCUE_TRUCK_4X4", manifest.vehicleProfile)
        assertEquals("PRODUCTION", manifest.boundingBoxType)

        // BoundingBox
        assertNotNull(manifest.boundingBox)
        assertEquals(108.19, manifest.boundingBox!!.minLon, 0.0001)
        assertEquals(16.035, manifest.boundingBox!!.minLat, 0.0001)
        assertEquals(108.235, manifest.boundingBox!!.maxLon, 0.0001)
        assertEquals(16.075, manifest.boundingBox!!.maxLat, 0.0001)
        assertEquals(500.0, manifest.boundingBox!!.bufferedMeters!!, 0.0001)

        // Bundle
        val bundle = manifest.bundle
        assertEquals("BaseRoadGraph.json.gz", bundle.fileName)
        assertEquals(1173443L, bundle.compressedSizeBytes)
        assertEquals(33002111L, bundle.uncompressedSizeBytes)
        assertEquals("9792dce0abb089a230bcb79e71b2a8ff83301879d417f4fd399711ce4c3a037b", bundle.sha256)

        // Hazard status
        assertNull(manifest.activeHazardSnapshotVersion)
        assertEquals("NOT_INCLUDED", manifest.hazardSnapshotStatus)
        assertEquals("HAZARD_DATA_REQUIRED_FOR_LIVE_CONDITIONS", manifest.hazardAssessmentPolicy)

        // Restrictions
        assertFalse(manifest.isRestrictionStrictRoutingEligible)
        assertEquals(StrictRoutingReadiness.NOT_READY_UNRESOLVED_RESTRICTIONS, manifest.restrictionStrictRoutingReadiness)
        assertEquals(16, manifest.unsupportedRestrictionsSummary.totalCount)
        assertEquals("FAIL_CLOSED_IN_STRICT_MODE", manifest.unsupportedRestrictionsSummary.enforcementPolicy)

        // Edge Safety Summary
        val edges = manifest.edgeSafetySummary
        assertEquals(16651L, edges.totalEdges)
        assertEquals(0L, edges.verifiedCompatibleEdges)
        assertEquals(16651L, edges.unverifiedEdges)
        assertEquals(0L, edges.blockedEdges)
        assertEquals(0L, edges.conflictedEdges)
        assertEquals(0.0, edges.strictSafeEdgesPercent, 0.0001)
        assertEquals(1425L, edges.blockedWays)
        assertEquals(1425L, edges.excludedBlockedWays)
        assertEquals(0L, edges.retainedBlockedWays)
    }

    @Test
    fun `parses wrapped data object format`() {
        val wrappedJson = """
        {
          "success": true,
          "data": $validProductionResponseJson
        }
        """.trimIndent()

        val response = RoutingJsonParser.parseRoutingStatus(wrappedJson)
        assertEquals("dn_central_real_v1", response.manifest.graphVersion)
        assertEquals(1173443L, response.manifest.bundle.compressedSizeBytes)
        assertTrue(response.downloadAvailable)
    }

    @Test
    fun `legacy ready response parses but cannot authorize navigation`() {
        val readyJson = validProductionResponseJson
            .replace("\"strictRoutingReady\": false", "\"strictRoutingReady\": true")
            .replace("\"canAttemptStrictRouting\": false", "\"canAttemptStrictRouting\": true")
            .replace("\"overallStrictRoutingReadiness\": \"NOT_READY_UNRESOLVED_RESTRICTIONS\"", "\"overallStrictRoutingReadiness\": \"READY\"")
            .replace("\"restrictionStrictRoutingReadiness\": \"NOT_READY_UNRESOLVED_RESTRICTIONS\"", "\"restrictionStrictRoutingReadiness\": \"READY\"")
            .replace("\"isRestrictionStrictRoutingEligible\": false", "\"isRestrictionStrictRoutingEligible\": true")
            .replace("\"hazardSnapshotStatus\": \"NOT_INCLUDED\"", "\"hazardSnapshotStatus\": \"ATTACHED\"")
            .replace("\"activeHazardSnapshotVersion\": null", "\"activeHazardSnapshotVersion\": \"dn_flood_20261104\"")
            .replace("\"liveHazardReady\": false", "\"liveHazardReady\": true")

        val response = RoutingJsonParser.parseRoutingStatus(readyJson)
        assertTrue(response.strictRoutingReady)
        assertTrue(response.canAttemptStrictRouting)
        assertTrue(response.liveHazardReady)
        assertEquals(StrictRoutingReadiness.READY, response.overallStrictRoutingReadiness)
        assertFalse("Parsed transport flags cannot authorize navigation", response.canNavigateSafely)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects unsupported manifest version`() {
        val badVersionJson = validProductionResponseJson.replace("\"manifestVersion\": \"2.1.0\"", "\"manifestVersion\": \"1.0.0\"")
        RoutingJsonParser.parseRoutingStatus(badVersionJson)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects invalid sha256 checksum format`() {
        val badShaJson = validProductionResponseJson.replace(
            "\"sha256\": \"9792dce0abb089a230bcb79e71b2a8ff83301879d417f4fd399711ce4c3a037b\"",
            "\"sha256\": \"invalid-short-hash\""
        )
        RoutingJsonParser.parseRoutingStatus(badShaJson)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects invalid compressedSizeBytes`() {
        val badSizeJson = validProductionResponseJson.replace("\"compressedSizeBytes\": 1173443", "\"compressedSizeBytes\": 0")
        RoutingJsonParser.parseRoutingStatus(badSizeJson)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects missing download object`() {
        val missingDownloadJson = """
        {
          "manifest": {
            "manifestVersion": "2.1.0",
            "graphVersion": "v1"
          }
        }
        """.trimIndent()
        RoutingJsonParser.parseRoutingStatus(missingDownloadJson)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects invalid download URL without http scheme`() {
        val badUrlJson = validProductionResponseJson.replace(
            "\"url\": \"https://cloudflare-r2.example.com",
            "\"url\": \"ftp://cloudflare-r2.example.com"
        )
        RoutingJsonParser.parseRoutingStatus(badUrlJson)
    }
}

