# P5-E10-M01 — Google Maps runtime trên OPPO USB

Ngày09/11/2026, Asia/Bangkok. **PASS — Google Maps SDK thật đã hiển thị tiles và hai marker TEST trên physical OPPO CPH2159.** Không Canvas/ảnh bản đồ thay thế. Final8unique instrumentation cases,0failures/errors/skipped, mọi instrumentation exit0. Đã kiểm onMapLoaded và xem trực quan screenshots không chỉnh sửa từ chính điện thoại.

```text
GOOGLE_MAPS_TILE_RENDER = PASS
RESCUER_MARKER = PASS
SOS_MARKER = PASS
GOOGLE_MAPS_PHYSICAL_USB = PASS
GOOGLE_MAPS_DEMO_READY = PASS

GOOGLE_MAPS_DEPENDENCY = Maps SDK19.0.0 / Maps Compose4.4.1
GOOGLE_MAPS_API_KEY = EXISTING_KEY_REUSE_USER_AUTHORIZED / RUNTIME_ACCEPTED
GOOGLE_MAPS_RESRCUER_MARKER = PASS
GOOGLE_MAPS_SOS_MARKER = PASS
UCS_COMPUTATION = NOT_IN_SCOPE
UCS_POLYLINE = NOT_IN_SCOPE
MISSION_ACCEPTANCE = NOT_IN_SCOPE
F06 = OPEN
STRICT_ROUTING_DISABLED = true
```

## 1. Trả lời kết quả runtime

| Nội dung | Kết quả |
|---|---|
| SDK tích hợp | Actual GoogleMap Composable/MapView, SDK19.0.0 + Compose4.4.1; standalone demo, không production main app/MapScreen |
| Tiles thật trên OPPO | PASS: roads/labels/POI Đà Nẵng, Google attribution, viewport đầy đủ; actual onMapLoaded + screenshot review |
| Key | Tái sử dụng key MAPS_API_KEY có sẵn theo quyền người dùng xác nhận; không tạo/copy key vào source/Backend/fixture |
| Restrictions | Người dùng xác nhận key trùng local.properties, Application restrictions=None, API restrictions có Maps SDK for Android. Agent không thay Cloud và không tự xác minh Console |
| Billing/quota | Phiên runtime thực đã được phục vụ, positive cases không có Maps authorization failure/billing/quota blocker. Chưa kiểm cấu hình/quota tổng thể trên Cloud; không suy diễn account/billing details |
| Rescuer marker | Pin xanh, native Marker.position16.0500/108.2020; bấm có native title/snippet/info window đúng |
| SOS marker | Pin đỏ, native Marker.position16.0510/108.2030; bấm có native title/snippet/info window đúng |
| Camera/gesture/recreation | Initial16.0505/108.2025 zoom15.5; actual double-tap zoom và drag pan đổi SDK camera; recreate tạo MapView mới/tải lại không crash |
| GPS/business integration | Không location permissions/layer/FusedLocationProvider/Firebase Auth/Database/SOS/rescue/credentials/routing DI; toolbar mở directions/external Maps tắt |
| Physical counts | **8unique cases PASS**:7positive +1negative API-key failure; không cộng repetitions/development runs/JVM/Canvas baseline |
| APK/startup | Actual Maps debug artifact ở gmaps01/artifacts/gmaps01-debug.apk; startup script đã mở Activity/nhận callback trên phone, giữ riêng bản demo để bàn giao |

## 2. Actual implementation và nguồn key

`tools/google-maps-harness` giữ Maps SDK19.0.0/Maps Compose4.4.1 và toolchain hiện có. Minimal test-only MapsDemoActivity gọi actual GoogleMap, MapEffect nhận NativeGoogleMap, rememberCameraPositionState và2Marker SDK thật. Không tái sử dụng nguyên DisasterMapScreen production vì screen/VM khởi tạo location/SOS/rescue DI và REST/realtime; audit cũ vẫn áp dụng. Không sửa MainActivity/AppNavGraph/production screen.

Header: GOOGLE MAPS DEMO, DỮ LIỆU VỊ TRÍ MÔ PHỎNG và F06/strict banner. Camera khởi đầu tại vùng Đà Nẵng. My Location/buttonfalse; mapToolbarEnabledfalse để không mở directions/app bên ngoài. Zoom/scroll gesture và native +/− được phép. Marker click trảfalse để SDK hiện info window; callback lưu actual Marker object cho test đọc position/title/isInfoWindowShown, không gọi callback bằng tay.

Loading state khởi đầu LOADING, MapEffect→SDK_READY, **chỉ actual onMapLoaded**→LOADED_CALLBACK. Watchdog30s không có callback→ERROR_OR_TIMEOUT, không vẽ tiles/pins giả hoặc tự kết luận nguyên nhân billing. SDK không có callback phân loại chi tiết mọi lỗi auth/network; dùng redacted runtime logs để xác định lỗi cụ thể khi có. Trạng thái loaded thể hiện callback đã xảy ra, không tuyên bố mạng luôn online/freshness sau đó.

Gradle dùng **java.util.Properties.load** trên Android root local.properties; MAPS_API_KEY trùng khai báo thì giá trị cuối có hiệu lực. GMAPS01_API_KEY environment override ưu tiên nếu được đặt. Inject qua manifestPlaceholders vào com.google.android.geo.API_KEY, không BuildConfig hardcode. Đã build kiểm override với giá trị giả không mạng, rồi khôi phục key được phép và kiểm merged manifest khớp input hiệu lực. [Key-wiring check](gmaps01/evidence/runtime/key-wiring-check.json).

Key được phép nằm trong APK theo cơ chế Maps SDK. Source không chứa key; root local.properties không sửa/không track. Generated raw manifests nằm trong ignored build; bản export luôn REDACTED. APK được Git ignore. Build/instrumentation/ownPID logs được lọc exact key + Google API key pattern **trước khi ghi**, không lưu bản raw rồi mới che. Quét lại runtime evidence và payload JUnit Base64 đã decode,0key matches. [Secret scan](gmaps01/evidence/runtime/secret-scan.json). Không sửa Cloud/restrictions/billing/quota, không gửi key sang Backend/fixture.

## 3. Bằng chứng visual actual OPPO

Screenshots do UiAutomation chụp sau khi xác nhận đúng owned package foreground, lưu trong own files rồi đọc bằng run-as đúng UID. Không chỉnh sửa ảnh. Review nhận thấy tiles roads/POI/nhãn Duy Tân/Cảng hàng không quốc tế Đà Nẵng, Google attribution, hai pin TEST. Các POI nền của Google không phải SOS thêm hoặc vị trí GPS người dùng.

- [Tiles loaded](gmaps01/evidence/runtime/physical-final-namespace/actualGoogleMapLoadsTilesAtSyntheticCamera/tiles-loaded.png).
- [Native rescuer info window](gmaps01/evidence/runtime/physical-final-namespace/actualRescuerMarkerTapShowsCorrectInfo/rescuer-info.png).
- [Native SOS info window](gmaps01/evidence/runtime/physical-final-namespace/actualSosMarkerTapShowsCorrectInfo/sos-info.png).
- [Zoom/pan thực](gmaps01/evidence/runtime/physical-final-namespace/actualDoubleTapZoomAndDragPanChangeSdkCamera/zoom-pan.png).
- [Key giả: không tiles, hiện lỗi](gmaps01/evidence/runtime/physical-final-namespace/invalidApiKeyCannotProduceLoadedSuccess/invalid-key-no-tiles.png).

[Visual review với image hashes](gmaps01/evidence/runtime/visual-review.json). Callback đơn lẻ không dùng để PASS tiles. [Google onMapLoaded reference](https://developers.google.com/android/reference/com/google/android/gms/maps/GoogleMap.OnMapLoadedCallback).

## 4. Physical tests và yêu cầu M01

[Final actual JUnit XML](gmaps01/evidence/runtime/physical-final-namespace/TEST-maps.xml), [results/commands/exits/actual outputs](gmaps01/evidence/runtime/physical-final-namespace/results.json), [driver log](gmaps01/evidence/runtime/physical-final-namespace-driver.log). Mỗi case có actual RunListener XML1test/runCount1 và ownPID window; host không tự tạo testcase PASS.

| Actual JUnit method | Result |
|---|---|
| actualGoogleMapLoadsTilesAtSyntheticCamera | PASS |
| actualRescuerMarkerTapShowsCorrectInfo | PASS |
| actualSosMarkerTapShowsCorrectInfo | PASS |
| actualDoubleTapZoomAndDragPanChangeSdkCamera | PASS |
| actualActivityRecreationReloadsSdkWithoutCrash | PASS |
| noLocationPermissionsAndSdkMyLocationDisabled | PASS |
| isolatedDemoKeepsF06AndStrictDisabledBanner | PASS |
| invalidApiKeyCannotProduceLoadedSuccess | PASS |

| Requirement | Evidence/result |
|---|---|
| M01-T01 install | PASS; target/test install-t exit0, packages absent trước install, không-r |
| M01-T02 Activity | PASS; actual ActivityScenario và startup amstart, không crash |
| M01-T03 GoogleMap init | PASS; actual MapView tìm trong View tree, MapEffect nhận NativeGoogleMap |
| M01-T04 tiles | PASS; onMapLoaded + actual OPPO image review, không nền trắng |
| M01-T05 camera | PASS; NativeGoogleMap.cameraPosition16.0505/108.2025/15.5 |
| M01-T06 rescuer | PASS; native Marker.position/title/info window |
| M01-T07 SOS | PASS; native Marker.position/title/info window |
| M01-T08 marker click | PASS; actual touch input vào projected marker, SDK callback + isInfoWindowShown |
| M01-T09 zoom/pan | PASS; actual double-tap/drag, NativeGoogleMap camera thay đổi |
| M01-T10 recreate | PASS; new Activity/native map instance tải lại |
| M01-T11 failure | PASS API-error path; key giả trong package riêng, SDK Authorization failure, callback0/loadedfalse, watchdog ERROR_OR_TIMEOUT, visual blank. Không đổi mạng/settings điện thoại; mất mạng riêng không được mô phỏng/test bằng cách tắt mạng |
| M01-T12 no GPS | PASS; requested permissions và runtime isMyLocationEnabled/buttonfalse |
| M01-T13 isolation | PASS; source/manifest/DEX/dependency và runtime startup review |
| M01-T14 F06/strict | PASS; actual visible banner + production routing source giữ nguyên |
| M01-T15 logs | PASS; ownPID/time-window logs, không FATAL/ANR; expected auth error chỉ ở negative |

15requirement IDs được bao phủ bởi8JUnit methods cộng host gates/visual review; không báo15JUnit cases. Không chạy lại545JVM/Canvas162/15baseline hay dùng chúng làm Maps PASS.

Biến thể negative dùng applicationId `.negative`, test package riêng và literal key giả không nhạy cảm; không thay Maps key production, không sửa network/billing/Cloud. Log thực có `Authorization failure`,0onMapLoaded, loadedfalse. Đây là failure test, không phải demo APK để trình bày. Positive key được phục vụ thành công; không suy diễn quota/billing ngoài phiên quan sát.

Một số ownPID logs có GMS Phenotype.API/GoogleApiManager DEVELOPER_ERROR hoặc Unknown calling package name được bắt trong GMS feature flags. Chúng xuất hiện dù Map tải bình thường; không diễn giải thành lỗi key/billing hoặc Firebase production. [Log review](gmaps01/evidence/runtime/log-review.json).

Development history giữ đầy đủ: kiểm env override đầu đọc stale manifest của task khác, đã đổi sang processDebugManifest và kiểm lại; lần screenshot/touch đầu gặp UiAutomation foreground chưa sẵn sàng, đã thêm own-window wait/refresh; findAccessibilityNodeInfosByText không đọc đúng Compose tree trên API33, sửa traversal/refresh giữ nguyên assertions. Các lượt fail/focused và8case PASS trung gian không cộng vào final8. Sau khi tắt external toolbar và căn chính xác Android namespace, đã retest artifact cuối đầy đủ. Không nới safety assertions hoặc gán success thủ công.

## 5. Runtime isolation / security gate

Cả Android namespace và applicationId chính: `com.example.bai1.p5e10maps.demo`. Kotlin Activity class `com.example.bai1.gmaps01.MapsDemoActivity` được khai báo fully qualified, không phụ thuộc production Application. Negative có suffix riêng; main/E09/E10A không bị install/overwrite.

Merged target manifest chỉ INTERNET, ACCESS_NETWORK_STATE và AndroidX own signature permission; allowBackupfalse/no sharedUID/default Application. Own launcher exported có chủ đích, GMS GoogleApiActivity nonexported; metadata chỉ Maps key + GMS version. Không provider/receiver/service/Firebase startup/credential/permission/location/camera/SMS/contacts/mic. Test manifest chỉ AndroidX bootstrap activities/runner; UID pairs non-system, khác nhau, không sharedUID.

DEX thực có GoogleMapKt/MapView và own MapsDemoActivity. Không main app/auth/SOS/rescue/routing/credentials/FusedLocationProviderClient/Firebase Auth/Database SDK. GMS basement18.9.0 chứa3compatibility exceptions FirebaseException/FirebaseApiNotAvailableException/FirebaseExceptionMapper; gate chỉ allow đúng3type đã kiểm AAR nguồn, không blanket-allow Firebase. Dynamic Maps renderer/GMS network là dependency được phép, không startup production Firebase.

Fresh runtime graph giữ Maps19.0.0/Compose4.4.1, GMS base/basement18.9.0/tasks18.4.0, không firebase/location/auth/credentials modules. NSC cleartextfalse/localhostfalse, không fixture/reverse. Source chỉ Maps SDK network hạ tầng, không backend client/business DI. Không claim có global packet capture hoặc ngăn mọi internal Google network operation.

[Debug gate](gmaps01/evidence/runtime/debug-gate.json), [negative gate](gmaps01/evidence/runtime/negative-gate.json), [redacted target manifest](gmaps01/evidence/runtime/debug-manifest-redacted.xml), [test manifest](gmaps01/evidence/runtime/debug-test-manifest.xml), [fresh dependency graph](gmaps01/evidence/runtime/runtime-dependency-graph.txt), [runtime isolation](gmaps01/evidence/runtime/runtime-isolation.json).

## 6. Artifacts, startup và điện thoại bàn giao

- [Actual Google Maps demo APK](gmaps01/artifacts/gmaps01-debug.apk).
- [Instrumentation APK](gmaps01/artifacts/gmaps01-debug-test.apk).
- [Negative API-error APK](gmaps01/artifacts/gmaps01-negative.apk), [negative test APK](gmaps01/artifacts/gmaps01-negative-test.apk).
- Main APK SHA256: `be5ef4f6fcb79decb77021151139d34c2226f36047d6a903067618703c83c2dd`.
- [SHA256SUMS](gmaps01/artifacts/SHA256SUMS); [certificate verification](gmaps01/evidence/runtime/debug-certificate.txt).
- Build logs được redaction trước ghi: build-final-namespace.log và build-final-namespace-negative.log trong runtime evidence; offline, exit0. Không nâng toolchain/global dependencies.

```text
Application ID / Android namespace: com.example.bai1.p5e10maps.demo
Signing SHA-1: E4:EA:13:1A:81:21:D3:34:86:B8:3B:EA:64:F1:A4:1C:68:F0:80:07
```

```bash
cd /home/vantri/AndroidStudioProjects/bai1
python3 tools/google-maps-harness/demo_session.py --install --keep-demo
```

Script chỉ cài nếu namespace absent; nếu có, kiểm installed public APK SHA khớp artifact, khác SHA thì dừng không ghi đè/gỡ. Thu ownPID logs đã che key và nhận actual onMapLoaded. [Runbook USB đầy đủ](gmaps01/RUNBOOK_USB.md).

Instrumentation tự gỡ đúng target/test/negative do nó mới cài. Sau final tests, startup smoke install-t actual main demo, amstart exit0 và actual callback; **giữ riêng bản demo chính trên OPPO để bàn giao**, test/negative packages không còn. Khi cần thay namespace packaging trong phiên này, chỉ gỡ đúng APK M01 do phiên vừa tạo sau kiểm SHA, không gỡ bản baseline/của người dùng. [Handoff session](gmaps01/evidence/runtime/handoff-final/session.json), [phone handoff](gmaps01/evidence/runtime/phone-handoff.json).

## 7. Source changes và preservation

Chỉ sửa M01 harness và tài liệu/artifacts gmaps01: build.gradle.kts (Properties/env/manifestPlaceholders, exact namespace, negative build/test config), manifest (Maps Activity/key metadata), configuration_status.py và report/runbook/SHA256SUMS. Thêm MapsDemoActivity.kt; native phone tests/runner/JUnit listener; runtime_common.py; gate_runtime.py; run_maps_tests.py; demo_session.py; finalize_runtime.py; artifacts .gitignore; runtime evidence.

Production app/E09/E10A source/config/APK và Backend giữ nguyên SHA256+mtime so với runtime baseline; Backend không có file mới/removed. Không reset/clean/commit/push/deploy. Không thay Cloud, phone settings/permissions/network, không đọc/xóa dữ liệu cá nhân. Chỉ packages/files thuộc owned demo; FLAG_KEEP_SCREEN_ON chỉ scope foreground Activity, không đổi setting hệ thống. gitdiffcheck0 cả hai repo. [Preservation/inventory](gmaps01/evidence/runtime/preservation.json), [source hashes](gmaps01/evidence/runtime/source-hashes.json), [evidence index](gmaps01/evidence/runtime/evidence-index.json).

Báo cáo/runbook phaseB cũ được lưu tại runtime/prior-report-phaseB.md và prior-runbook-phaseB.md. Root phaseB evidence lịch sử/CONFIGURATION ONLY APK vẫn giữ; runtime/physical-final-namespace là nghiệm thu hiện hành, không lấy statusBLOCKED cũ làm kết quả mới.

## 8. Stop / giới hạn

M01 PASS cho **standalone Google Maps SDK + hai marker static TEST trên OPPO**, không phải MainApp/production DisasterMapScreen E2E, không đọc SOS GET hay graph OSM trong màn này. M02/M03/M04, rescue mission, UCS computation, route Polyline, realtime GPS, SOS dispatch đều không thực hiện. F06 OPEN, STRICT_ROUTING_DISABLED=true. Không tự triển khai bước sau. Không có Maps runtime blocker còn lại trong phiên đã test; cấu hình Cloud billing/quota ngoài phiên không được chứng nhận.
