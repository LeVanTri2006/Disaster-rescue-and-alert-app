# P5-E10A — SOS Creation & Shared Map Marker

**PASS — phạm vi fixture/test-only UI trên OPPO USB vật lý.** Hoàn tất09/11/2026 cho demo10/11/2026. Người dân gửi SOS qua production transport/client; fixture lưu; đăng xuất và vai trò cứu hộ đọc đúng record, render marker từ GET, bấm xem thông tin. Không có real SOS dispatch. Production SOS UI/MainApp E2E chưa được nghiệm thu.

## 1. Baseline và audit actual source

Đã đọc DEMO_MAP_ALGORITHM_RESCUE_REPORT.md, DEMO_RUNBOOK_USB.md và PHASE5_P5E08_USB_READONLY_WARNING_NOTIFICATION_REPORT.md. Đã audit SOSScreen, SosRescueRequestScreen, SosRescueRequestViewModel, SosRepository/InMemorySosRepository/FirebaseSosRepository, ApiSosTransport, SosApiClient/Models, RescueMissionApiClient, DisasterMapScreen/ViewModel và AppNavGraph. Backend chỉ đọc SosController/SosService/CreateSosDto; không chạy Backend/DB.

| Thành phần thật | Audit và sử dụng |
|---|---|
| ApiSosTransport | Exact production; SosRescueInfo→CreateSosRequest→HTTP; Success có eventId/serverSos, Failure có reason. Không sao chép serialization/transport nghiệp vụ |
| OkHttpSosApiClient / SosApiModels | Exact production POST /sos, GET /sos/active; typed parsing/status/errors. Android interface chưa có GET detail |
| Domain models | Exact SosRescueInfo/SosStatus/SosSubmissionResult/SosUrgency/SosTransportMode; tọa độ TEST, battery=-1 được bỏ khỏi JSON; không GPS/photo |
| OkHttpAuthApiClient | Exact production login HTTP/parser, injected localhost client; synthetic accounts/tokens, không auth/Firebase thật |
| Graph diagnostic E09 | DemoGraph.kt copy byte-equal; RoadGraph/model/parser production, conservative UNKNOWN overlay; Canvas/projection tái sử dụng với controls marker mới |
| SOSScreen | Auto CurrentLocationViewModel/permission/location; không đóng gói vào demo |
| SosRescueRequestScreen / VM | Có viewModelOverride nhưng LaunchedEffect vẫn xin location; factory dùng SosAppDependencies, AndroidLocationProvider, battery, R2 uploader. Có camera/gallery/settings flows. Không dùng actual form/VM trong APK này |
| SosRepository implementations | InMemory optimistic PENDING→SUBMITTED, REST merge/fallback memory, getById local; Firebase wrapper khởi động realtime. Audit-only; không dùng cache này giả shared server state |
| DisasterMapScreen / VM | GoogleMap/location/permission; repository observe + REST refresh4s/nearbyRescueRequests. Audit-only; không SDK/key/production map trong APK |
| AppNavGraph | SOS/SOS_RESCUE_REQUEST/SOS_DETAIL protected routes; không claim actual MainActivity/NavGraph integration ở E10A |
| RescueMission API | Có my/detail/status/accept và nearby/detail rescue-request; không gọi/implement các workflow này |

39 original source files được compile byte-equal:13 generated exact copies +26 routing files. [Source hashes](p5e10a/evidence/physical-complete/source-hashes.json), [security gate](p5e10a/evidence/physical-complete/gate.json). Không phải cả39classes đều được gọi runtime: routing source chủ yếu cung cấp graph/model/parser; app không có hành động UCS.

**Test-only adapter:** DemoActivity cung cấp chọn account/vai trò, nút SOS, form adults/description/tọa độ TEST B-H, manual refresh, map controls/details và epoch guard. Đây không phải production SOSScreen/SosRescueRequestScreen hay RescueMission UI. Giữ nguyên production UI/DI vì chưa có seam cô lập đầy đủ permission/SDK; tái sử dụng actual domain/transport/client và diagnostic graph đã PASS, không viết client nghiệp vụ thay thế.

## 2. Fixture contract và kho dùng chung

Owned ThreadingHTTPServer bind127.0.0.1:cổng OS cấp, adb reverse đúng cổng. Một store dict dưới RLock cho cả phiên/cả vai trò; không hai danh sách server riêng theo screen. Không database/cloud/publisher/forward/dispatch. Dữ liệu RAM chỉ hết khi kết thúc fixture. Runner reset store chỉ giữa test cases độc lập; không reset giữa vai trò/recreation trong cùng case. Script demo không reset khi logout.

| API | Contract thực/demonstration |
|---|---|
| GET /demo/info | Test-only ownership handshake P5-E10A-SYNTHETIC-NO-DISPATCH |
| POST /auth/login | Existing auth response shape; citizen/citizen2/citizen3/rescuer@example.invalid và fake token riêng từng account, không JWT thật |
| POST /sos | Existing CreateSosRequest;201 {success:true,data:event}; UUID mới, ACTIVE/ONLINE. Một ACTIVE/account;409 nếu gửi trùng |
| GET /sos/active | Existing production client/controller endpoint;200 {data:[same stored records]}, authenticated read |
| GET /sos/:UUID | Backend controller thật có ParseUUIDPipe/detail. Fixture trả cùng record cho owner hoặc rescuer;400/401/403/404. Đã host-test; Android interface chưa có method nên UI details dùng GET active item |

Backend source xác nhận initial ACTIVE, UUID và giới hạn một ACTIVE/user. Test nhiều SOS dùng công dân synthetic khác nhau. Backend có expiry3ngày, resolve/false-alarm và POST tạo RescueRequest/PENDING/publish Firebase; fixture không thực hiện các side effects/workflows này. Thời gian fixture cố định TEST, không giả freshness/live authority. Fixture chỉ cho citizen POST và hạn chế synthetic region/body, chặt hơn production role policy; không coi đây là kiểm chứng RBAC production.

## 3. Request/response thực trên điện thoại

Production serializer gửi `POST /sos` tới owned localhost, HTTP201. Request:

```json
{
  "adults": 2,
  "children": 0,
  "elderly": 0,
  "neededItems": [],
  "condition": "SYNTHETIC_TEST_DATA",
  "description": "SYNTHETIC_TEST_DATA cần hỗ trợ",
  "latitude": 16.051,
  "longitude": 108.203,
  "urgency": "HIGH",
  "timestamp": "2026-11-10T00:00:00Z"
}
```

Response `data` (envelope `success:true`):

```json
{
  "adults": 2,
  "children": 0,
  "elderly": 0,
  "neededItems": [],
  "condition": "SYNTHETIC_TEST_DATA",
  "description": "SYNTHETIC_TEST_DATA cần hỗ trợ",
  "latitude": 16.051,
  "longitude": 108.203,
  "urgency": "HIGH",
  "timestamp": "2026-11-10T00:00:00Z",
  "eventId": "148b811e-51df-462c-9f5a-3afe2ca8b94e",
  "createdAt": 1794268800000,
  "status": "ACTIVE",
  "transportMode": "ONLINE"
}
```

Token rescuer đọc lại đúng UUID/fields trên. [Raw fixture events](p5e10a/evidence/physical-complete/citizenPostThenIndependentRescuerGetRendersClickableMarker/fixture-events.json), [shared store](p5e10a/evidence/physical-complete/citizenPostThenIndependentRescuerGetRendersClickableMarker/shared-store.json), [cross-role verification](p5e10a/evidence/cross-role-proof.json). Events bỏ auth bodies/password/token; thông tin người cần cứu chỉ là synthetic counts/description theo DTO có sẵn, không phát minh trường tên/số điện thoại.

## 4. Marker từ GET và lifecycle

POST chỉ đặt lastCreated/message, không tạo marker. Logout xóa lastCreated/cache client. Rescuer bắt đầu list trống; **Refresh SOS** gọi actual getActiveSos(). Adapter kiểm UUID/synthetic/ACTIVE/finite TEST coordinates, replace list distinctBy(eventId), không append. Controls marker lấy latitude/longitude của từng GET item, dùng projection E09; bấm nút tím có label SOS UUID hiện ID/status/adults/description/coords của đúng item đó.

B(16.051,108.203) và H(16.052,108.203) có marker riêng. Record trùng tọa độ vẫn giữ UUID riêng và fan16dp có nhãn giải thích; không đổi tọa độ dữ liệu. Refresh nhiều lần không POST/không tạo duplicate. Manual refresh, không realtime/polling/GPS. Failed GET xóa stale markers/selection và báo GET_ERROR; refresh OK đọc lại server.

Recreation yêu cầu login lại, chỉ cổng local được giữ trong own prefs; sau GET nhận cùng UUID. Logout tăng epoch, xóa token/form/counts/fault/markers/selection/busy. Late responses không khôi phục phiên. Logout không xóa server records để vai trò cứu hộ tiếp tục đọc.

## 5. Test results và JUnit XML

OPPO CPH2159 Android13/API33, physicalUSB/authorized, boot1/qemu không1. Chọn serial động, evidence redacted. df root/home trên2GB trước build; không dọn dữ liệu. [Environment](p5e10a/evidence/environment.json).

**Final15 unique physical cases PASS,0failures/errors/skipped; mọi instrumentation exit0.** [Actual JUnit XML](p5e10a/evidence/physical-complete/TEST-demo.xml), [results/commands/outputs](p5e10a/evidence/physical-complete/results.json), [driver log](p5e10a/evidence/physical-complete-driver.log).

| Actual JUnit method | Result |
|---|---|
| citizenPostThenIndependentRescuerGetRendersClickableMarker | PASS |
| multipleSosHaveSeparateResponseMarkersAndRefreshNeverPosts | PASS |
| sameCoordinateSosRetainDifferentIds | PASS |
| duplicateActiveSosForSameCitizenReturns409 | PASS |
| post403DoesNotStoreOrShowFalseSuccess | PASS |
| post500DoesNotStoreOrShowFalseSuccess | PASS |
| postTimeoutPrecommitFixtureDoesNotShowLateSuccess | PASS |
| postOfflineDoesNotReachFixtureOrShowSuccess | PASS |
| failedGetRemovesStaleMarkersAndReportsFailure | PASS |
| get403TimeoutAndOfflineNeverInventMarkers | PASS |
| activityRecreationRequiresLoginAndReloadsSameServerIds | PASS |
| logoutClearsSessionFormSelectionAndRetainsOnlyServerData | PASS |
| logoutDuringPendingGetCannotRestoreMarkersOrSession | PASS |
| logoutDuringPendingPostCannotRestoreSuccess | PASS |
| rescuerHasNoCreationOrMissionActionAndStrictGateStaysClosed | PASS |

**Host contract4/4 PASS:** detail ownership/same record; two owners/409; auth/path rejection;6 concurrent POST chỉ commit1. [Host JUnit XML](p5e10a/evidence/TEST-fixture-host.xml), [log](p5e10a/evidence/fixture-host.log). Không cộng host/focused repetitions vào15phone. Standalone target/test APK build exit0. Không chạy lại545JVM/162unrelated physical trong E10A; các số đó chỉ baseline E09/E08.

403/500/timeout/offline không false-success/commit ở fixture. Offline không có POST tới server. Timeout fixture cố ý lỗi **trước commit**, sleep3s; client read1s/call2s, không retry. Timeout Internet thực có thể đã commit server: không claim rollback/idempotency/cancellation của production. App không tự retry; dùng rescuer GET đối chiếu khi chưa biết kết quả. Duplicate409 giữ nguyên record.

Development evidence giữ nguyên: build đầu lỗi Int×Dp bị chặn trước install; physical-run2 bị dừng sau UI failures, focused-scroll còn stale accessibility tree API33; helper refresh root/child nodes đã sửa, focused-refresh2/2 PASS. physical-accepted có12case đầu PASS nhưng gián đoạn USB, case tiếp theo thiếu XML và cleanup thất bại; **không dùng lượt đó để nghiệm thu**. Kết nối lại, xác minh SHA APK owned mới gỡ đúng2namespace, ghi [resume-cleanup](p5e10a/evidence/resume-cleanup.json). physical-complete15/15 là final đầy đủ, cleanup thành công. Không biến output pm rỗng khi USB gián đoạn thành bằng chứng package vắng mặt.

## 6. Security và bảo toàn

**Preinstall security gate PASS:** appID riêng `com.example.bai1.p5e10a.demo`/test absent trước install; install-t không-r, UID non-system khác nhau. Default Application, allowBackupfalse/no sharedUID; INTERNET và AndroidX own signature permission. Không location/camera/mic/SMS/notification permission, provider/service/receiver/SDK metadata. Một launcher exported có chủ đích. TestAPK chỉ AndroidX bootstrap activities.

BothDEX không Firebase/GMS/Maps/credentials/productionDI/location; production config values0matches.39exact original source hashes; không reflection/private-kernel unlock/qualification/CLEAR insertion. NSC basefalse, chỉ127.0.0.1 cleartext. Client pin HTTP127.0.0.1:ownedport/4paths, no proxy/redirect/retry, request4KB/response64KB/timeouts. Kiểm tất cả fixture events đúng owned origin/path. Đây là source/manifest/DEX/owned-request proof, không claim global packet capture. [Gate](p5e10a/evidence/physical-complete/gate.json), [target manifest](p5e10a/evidence/physical-complete/target-manifest.xml), [test manifest](p5e10a/evidence/physical-complete/test-manifest.xml), [compiled policy](p5e10a/evidence/physical-complete/compiled-policy.txt), [UIDs](p5e10a/evidence/physical-complete/owned-uids.json).

15ownPID/time-window logs scan crash/ANR/unexpected Firebase init/credential literals,0findings. [Log review](p5e10a/evidence/log-review.json). Không global logcatclear, không đọc GPS/accounts/SMS/photos/files cá nhân, không đổi permission/settings/production app. Chỉ own UID/prefs. Final runner gỡ đúng2app mới, xóa đúng reverse và shutdownfixture; không remove-all. Không tuyên bố forensic hash dữ liệu cá nhân.

Tất cả3300file Android có sẵn giữ nguyên SHA256+mtime; Backend677file giữ nguyên SHA+mtime,0new/removed, gồm graph artifacts. E09 APK/source/report không đổi. gitdiffcheck0 cả hai repo; inheriteddirty giữ nguyên, không reset/clean/commit/push/deploy. [Preservation và inventory file mới](p5e10a/evidence/preservation.json), [evidence index](p5e10a/evidence/evidence-index.json).

## 7. Các file mới, APK và startup

Không sửa file có sẵn. File implementation mới dưới `tools/sos-map-harness/`:

- build.gradle.kts/settings.gradle.kts/gradle.properties; manifest/NSC.
- src/main/java/com/example/bai1/demo/DemoActivity.kt; DemoGraph.kt copy E09.
- fixture.py (shared store/contract); demo_session.py (presentation session).
- src/androidTest/.../DemoPhoneTest.kt; DeviceXmlListener.kt; IsolatedInstrumentationRunner.kt.
- run_demo_tests.py; test_fixture.py; verify_evidence.py; smoke_session.py.

Thêm báo cáo này, docs/demo/p5e10a/RUNBOOK_USB.md, artifacts và evidence. Inventory trong preservation.json liệt kê mọi file mới ngoài generated build/evidence; evidence-index chứa hash các bằng chứng/artifacts.

APK: [p5e10a-demo.apk](p5e10a/artifacts/p5e10a-demo.apk), [test APK](p5e10a/artifacts/p5e10a-demo-test.apk), [SHA256SUMS](p5e10a/artifacts/SHA256SUMS).

App SHA256: `62a5cac771ce61c0f2696e10036e42a2ba90043ba69002ec3ae452f45bb214e7`.

```bash
cd /home/vantri/AndroidStudioProjects/bai1
python3 tools/sos-map-harness/demo_session.py --install --keep-demo
```

`--keep-demo` là lựa chọn rõ của người chạy. Script từ chối ghi đè package có SHA khác; dùng owned fixture/reverse và Ctrl+C cleanup. APK/evidence giữ trên laptop; không tự giữ app trên điện thoại sau test. [Runbook và kịch bản hai vai trò](p5e10a/RUNBOOK_USB.md). Lệnh startup đã smoke-test thực trên phone, amstart READY rồi Ctrl+C, exit0 và cleanup PASS: [session smoke](p5e10a/evidence/session-smoke.json). Không cộng smoke vào15JUnit.

## 8. Acceptance và blockers

```text
SOS_SYNTHETIC_PHYSICAL_POST = PASS
SHARED_FIXTURE_STORE = PASS
RESCUER_READ_SAME_SOS = PASS
GET_RESPONSE_TO_CLICKABLE_MAP_MARKER = PASS
MULTI_SOS_REFRESH_RECREATION_LOGOUT = PASS
SECURITY_GATE = PASS
REAL_SOS_DISPATCH = NONE
PRODUCTION_SOS_FORM_AND_MAIN_APP_E2E = NOT_RUN
F06 = OPEN
STRICT_ROUTING_DISABLED = true
```

ScopeA đạt PASS: đã có rescuer GET và map render từ cùng record server, không chỉ form. Blockers ngoài scope: actual production form/Map/DI isolation, production auth/realtime/cloud, mission workflow, authoritative qualification/F06. Không tự nhận/hoàn thành nhiệm vụ, UCS/realtimeGPS hay strict unlock. Dừng sau P5-E10A.
