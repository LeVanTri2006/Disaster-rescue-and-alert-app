# PHASE 5 – P5-A01R-NET: Security Remediation Report

Ngày nghiệm thu: 2026-11-07 (Asia/Bangkok). Phạm vi: F08 Android network boundary. Không triển khai F06, UI, parity, production strict authorization hoặc deployment.

## 1. Executive Summary

**F08 PASS trong phạm vi source, JVM integration và cấu hình build đã merge/compile. PLATFORM_ENFORCEMENT=UNVERIFIED; DEVICE_E2E=NOT_RUN.**

Release từ chối HTTP; debug chỉ cho HTTP trên ba host local chính xác. API giữ Bearer tại endpoint đã cấu hình; downloader loại bỏ credentials, không follow redirect. URL lease dùng monotonic time, TTL 1–900 giây, refresh qua Backend có authorization và retry tối đa một lần. Content validation, independent stream caps, parser guards và timeouts giữ nguyên raw GZIP SHA-256 binding và atomic cache publication.

Fresh security/cache: 90/90; routing: 462/462; full JVM: 503/503; APK debug và release policy resources: BUILD SUCCESSFUL. Strict routing vẫn DISABLED. Không có production-authoritative qualification verifier.

## 2. Repository Baseline

Android: `/home/vantri/AndroidStudioProjects/bai1`. Backend READ ONLY: `/home/vantri/AndroidStudioProjects/canh-bao-thien-tai-backend-app`.

Đã đọc bốn báo cáo consumer audit, strict lock, runtime regression và cache integrity trước đó, source API/downloader/cache/repository/loader, cùng các test liên quan. Không tìm thấy AGENTS.md áp dụng. Trước sửa đã kiểm tra pwd, git status, git diff --check và snapshot SHA-256/mtime_ns; không reset/clean/restore user source. Repository có modified/untracked từ trước, nên git HEAD không được dùng làm baseline nội dung của task này.

Baseline Android: 220 file ngoài generated/IDE/dependency directories; Backend: 677 file. Sau task: 14 file baseline thay đổi trong phạm vi, 206 file giữ cả hash và mtime, không mất file; thêm 4 file implementation/test/config và báo cáo này. `.idea` bị loại khỏi snapshot nên không suy diễn bảo toàn IDE tree từ số liệu này. Auth/SOS business files, app/build.gradle.kts, UCS, RescueRoutingContext và GraphCacheStorage được bảo toàn theo snapshot.

Baseline routing chạy thật: 433 tests/12 suites, exit 0. Baseline CACHE đã nghiệm thu: full 474, routing 433, targeted 61; không dùng các số này thay kết quả sau sửa.

Môi trường: JAVA_HOME=/usr/lib/jvm/java-25-openjdk-amd64; Java/javac 25.0.4.1; Gradle 9.7.1. Đã kiểm tra java -version và ./gradlew --offline --no-daemon --version. Mọi Gradle command bên dưới dùng Java 25, offline; không thêm dependency/SDK, không đổi Gradle/AGP/Kotlin hoặc cấu hình toàn cục.

## 3. F08 Root Cause

Main manifest trước task cho cleartext toàn cục. Routing client/downloader chưa có một boundary đầy đủ cho scheme/redirect, URL lease, response headers và independent resource caps. expiresIn không đủ bảo vệ nếu không có receipt time và validation; giới hạn dựa vào untrusted manifest không phải hard cap độc lập. Các lỗi này được sửa tại production consumer, không thay bằng mock consumer.

Đối chiếu Backend thực tế: GET /routing/current dùng JwtAuthGuard/RolesGuard; ADMIN/MODERATOR/RESCUER theo guards, RESCUER cần rescue profile APPROVED. Routing storage presign GetObject có TTL 900 giây, ResponseContentType application/gzip và attachment BaseRoadGraph.json.gz. HTTP fixture cũng trả application/gzip và Content-Length. Không đoán contract từ tên class; không sửa Backend.

## 4. Release/Debug Transport Policy

Main manifest: usesCleartextTraffic=false, networkSecurityConfig=@xml/routing_network_security_config. Main/release XML: base-config cleartextTrafficPermitted=false. Debug resource override: base false, domain-config true chỉ cho 10.0.2.2, 127.0.0.1, localhost; includeSubdomains=false, không wildcard. Fixture port được phép thay đổi.

RoutingTransportPolicy kiểm tra HttpUrl tại API/download boundary: chỉ HTTPS, hoặc HTTP debug trên exact allowlist; reject userinfo/password, fragment và invalid scheme. Debug allowance còn bị chặn bởi BuildConfig.DEBUG nên truyền debug=true không thể mở HTTP trong release. API base có query bị từ chối; origin/path outgoing phải đúng endpoint cấu hình. Không đổi Backend production URL, không thử credential production.

JVM test release policy sử dụng production policy debug=false; HTTPS positive dùng interceptor, không chứng minh TLS handshake ngoài thực tế. Debug emulator URL 10.0.2.2 được policy cho phép; chưa gọi từ emulator.

## 5. Bearer and Redirect Security

API: Authorization Bearer chỉ cho /routing/current tại configured origin; validate URL trước khi đọc token. Không follow HTTP/SSL redirects, không automatic retry; authenticators NONE. Network interceptor kiểm tra outgoing target sau các application interceptors. Re-read token provider sau response; token đổi/logout khiến response bị loại NO_TOKEN.

Downloader: không tạo Bearer; followRedirects/followSslRedirects=false, retryOnConnectionFailure=false; cookieJar NO_COOKIES và authenticators NONE. Network interceptor loại Authorization, Proxy-Authorization và Cookie trước wire kể cả khi shared application interceptor đã thêm chúng. Redirect bị reject, không chase Location. 401/403 chỉ kích hoạt flow renewal hữu hạn qua Backend.

Real JVM loopback HTTP tests chứng minh API redirect không gửi request/Bearer tới destination và downloader không đưa shared-client credentials lên wire. Error diagnostics dùng loại lỗi chung; không in JWT, raw presigned URL, server payload hoặc exception message/cause tùy ý. GraphDownloadInfo.toString redacts URL.

## 6. URL Expiry/Freshness

expiresIn phải là integer Number 1–900; string, fractional, âm, zero, overflow, null hoặc >900 bị reject. Receipt monotonic được stamp locally trước API request để transit/parse time tiêu tốn lease, không kéo dài lease; wire receipt fields bị bỏ qua. Android dùng elapsedRealtime (bao gồm deep sleep); JVM dùng nanoTime. Age >= TTL là expired; negative stamp/clock rollback fail closed; tính age thay vì cộng expiry dễ overflow. DTO copy giữ origin; receipt transient không persist.

Direct downloader kiểm tra expiry trước request sau khi cache miss. Valid immutable cache có thể dùng để tham khảo dù lease đã hết, không phát sinh request và không cấp strict approval. URL_EXPIRED/401/403: repository/loader lấy metadata mới qua Backend authorization rồi retry đúng một lần; không recursion. Epoch/reference và token guards loại stale completion; logout giữa denial và refresh không khởi tạo lại session hoặc Backend fetch.

| Khái niệm | Kết luận |
|---|---|
| Download lease | Kiểm tra local monotonic TTL, operational bound; không là issuer proof |
| Manifest data freshness | NOT_VERIFIABLE theo contract hiện tại; timestamp cũ không chứng minh đường mới |
| Backend readiness | Download-only projection; raw claims không cấp strict authority |
| Hazard/field freshness | NOT_VERIFIABLE; ATTACHED không là live certificate |
| Production strict authorization | DISABLED, chưa có authoritative verifier |

strictRoutingReady/liveHazardReady/canAttemptStrictRouting đều false và overall NO_VERIFIED tại projection liên quan. canNavigateSafely=false giữ nguyên. Absent/malformed/unknown/expired metadata không cấp strict authority.

## 7. HTTP Content Validation

Giữ Accept-Encoding: identity để hash trên raw compressed GZIP, tránh transparent decompression. API chấp nhận application/json; graph chấp nhận application/gzip theo Backend thực tế (normalize case/parameters). Content-Encoding chỉ absent/identity. Reject duplicate content headers, invalid/negative/overflow Content-Length, Transfer-Encoding không hợp lệ và TE+CL ambiguity; chunked không length hợp lệ vẫn stream được.

Validate HTTP status, declared/body length, actual bytes, nonempty/truncated GZIP, expected compressed size/SHA-256 và uncompressed size/hash binding như trước. Wrong MIME, gzip/br encoding, redirect, short/midstream body, IO/offline/timeout đều không publish generation mới. Giữ generation A khi refresh lỗi. SocketTimeoutException và tổng call timeout InterruptedIOException được map TIMEOUT/DOWNLOAD_TIMEOUT; cancellation được propagate.

## 8. Resource Limits and Rationale

| Giới hạn | Giá trị | Cơ sở |
|---|---:|---|
| Raw compressed graph | 8 MiB | Độc lập manifest, >6 lần workload khoảng 1.174 MB |
| Uncompressed graph | 64 MiB | Độc lập manifest, khoảng gấp đôi workload 33.008 MB |
| API response | 1 MiB | Metadata nhỏ, bounded stream, không allocation từ untrusted length |
| JSON nesting | 32 | Workload quan sát max depth 6 |
| Raw JSON string | 64 KiB | Workload max string khoảng 250 bytes |
| JSON literal | 128 bytes | Đủ numeric/schema fields; tránh token tích lũy vô hạn |
| Processing budget | 180 s | Check giữa các lần đọc/hash/expand/parse |
| API connect/read/write/call | 10/15/15/25 s | Bound cả connection và whole call |
| Download connect/read/write/call | 15/30/30/120 s | Cho graph workload, không retry tự động |

Constructor chỉ được hạ caps, không nâng quá ceilings. Manifest size phải positive và <= cap trước FS/network. Stream byte counters kiểm tra độc lập kể cả unknown length; gzip expansion bị chặn trước parse vượt cap. JSON guard escape-aware chạy trên bytes thực sự parser tiêu thụ; skip cũng qua bound, không reset/mark bypass. Không đọc toàn graph vào RAM để kiểm header, không preallocate theo server available/length. Parser vẫn materialize graph model theo contract cũ, bounded bytes không chứng minh RAM mọi thiết bị.

Artifact thật output-dryrun-task3b: 1,174,083 compressed bytes, 33,007,647 uncompressed bytes; raw gzip SHA-256 1b7f64a965a763624e0a6c2cd866352fc3ef5f106214907866d0c6dcb49c25b4. Read-only artifact acceptance test PASS: 7294 nodes/16651 edges, dưới hard caps. Output test chỉ ở temporary owned root.

Boundary tests hạ caps để kiểm chính xác at-limit/over-limit cùng đường production; có checks không thể nâng ceilings, oversized Long metadata và depth/string/literal boundaries. Download/prepare chạy Dispatchers.IO; synchronous cache lookup trên main thread trả null trước lock, synchronous clearCache yêu cầu off-main. Không sửa GraphCacheStorage transaction semantics. Budget không preempt CPU/OS tức thì: checked giữa reads; blocking socket còn bị bound bởi network timeout. Caps là per-attempt, không là total retained-cache quota/GC policy.

## 9. Files Modified

14 baseline files được chỉnh; 4 file mới, cộng báo cáo. RoutingStatusModels.kt chứa GraphDownloadInfo thực tế (không phải file giả định RoutingApiModels): chỉ thêm local receipt/transient và redacted toString trực tiếp phục vụ F08; strict getter giữ nguyên.

| File | Thay đổi |
|---|---|
| `app/src/debug/res/xml/routing_network_security_config.xml` | variant-specific cleartext allowlist |
| `app/src/main/AndroidManifest.xml` | cleartext false và network security config |
| `app/src/main/java/com/example/bai1/rescue/routing/download/GraphDownloadModels.kt` | TRANSPORT_REJECTED mapping |
| `app/src/main/java/com/example/bai1/rescue/routing/download/GraphDownloader.kt` | transport/headers/expiry, streaming caps, timeout, IO/cache guards |
| `app/src/main/java/com/example/bai1/rescue/routing/loader/RoutingGraphLoader.kt` | propagate expiry và một guarded refresh/retry |
| `app/src/main/java/com/example/bai1/rescue/routing/model/RoutingStatusModels.kt` | GraphDownloadInfo receipt/redaction |
| `app/src/main/java/com/example/bai1/rescue/routing/network/RoutingApiClient.kt` | transport/auth headers, content/API bounds, local lease và sanitized errors |
| `app/src/main/java/com/example/bai1/rescue/routing/network/RoutingJsonParser.kt` | strict TTL và local receipt |
| `app/src/main/java/com/example/bai1/rescue/routing/network/RoutingTransportPolicy.kt` | central transport, monotonic time, HTTP validators và resource guards |
| `app/src/main/java/com/example/bai1/rescue/routing/repository/RoutingRepository.kt` | guarded refresh, epoch/session-safe single retry |
| `app/src/main/res/xml/routing_network_security_config.xml` | variant-specific cleartext allowlist |
| `app/src/test/java/com/example/bai1/rescue/routing/GraphCacheSecurityTest.kt` | Cập nhật fixture MIME hoặc TTL về actual contract; không bỏ scenarios |
| `app/src/test/java/com/example/bai1/rescue/routing/GraphDownloaderTest.kt` | Cập nhật fixture MIME hoặc TTL về actual contract; không bỏ scenarios |
| `app/src/test/java/com/example/bai1/rescue/routing/P5A01ConsumerAuditTest.kt` | Cập nhật fixture MIME hoặc TTL về actual contract; không bỏ scenarios |
| `app/src/test/java/com/example/bai1/rescue/routing/RoutingApiClientTest.kt` | Cập nhật fixture MIME hoặc TTL về actual contract; không bỏ scenarios |
| `app/src/test/java/com/example/bai1/rescue/routing/RoutingJsonParserTest.kt` | Cập nhật fixture MIME hoặc TTL về actual contract; không bỏ scenarios |
| `app/src/test/java/com/example/bai1/rescue/routing/RoutingNetworkSecurityTest.kt` | 29 production-boundary security tests |
| `app/src/test/java/com/example/bai1/rescue/routing/RoutingRepositoryTest.kt` | Cập nhật fixture MIME hoặc TTL về actual contract; không bỏ scenarios |

GraphCacheStorage, graph parser schema/strictness, UCS, RescueRoutingContext, Auth/SOS/Firebase business logic, Backend và graph artifacts không bị sửa.

## 10. Security Test Matrix

29 test methods mới trong RoutingNetworkSecurityTest gọi production policy/client/downloader/repository/loader. Không thay consumer bằng mock implementation. Synthetic responses/interceptors và local HttpServer chỉ thay transport đầu ngoài; workload graph thật được đọc tại local disk.

| Nhóm bắt buộc | Evidence và kết quả |
|---|---|
| Debug local, release HTTP/HTTPS, invalid scheme/userinfo/fragment | Policy exact-host tests; invalid/release HTTP reject trước token/request; PASS |
| API redirect và download Bearer | Real loopback destination=0 calls; download shared credentials absent trên wire; PASS |
| Download redirect | Reject 3xx/unsafe target, giữ cache A; PASS |
| Expired/valid TTL, malformed/overflow/receipt injection | 999ms valid, 1000ms expired với TTL1; clock rollback reject; stale lease valid cache không network; PASS |
| Logout/refresh races | Changed token discards response; logout during403 không thêm Backend fetch; PASS |
| Finite renewal | Always403 tối đa2API+2download; stale metadata renewal thành công; loader guarded retry; PASS |
| MIME/encoding/status/length/chunked/short/empty | Safe rejection hoặc identity raw-GZIP success đúng contract; PASS |
| Raw SHA-256/cache content binding | Existing downloader/cache suites + raw gzip fixtures, graph parser exact bytes; PASS |
| Compressed/uncompressed caps và expansion bomb | At boundary success, +1 reject MAX_SIZE_EXCEEDED; ceilings không nâng được; PASS |
| JSON/API resources/Long overflow | Depth32, string64KiB, literal128 và API byte boundary; untrusted available không allocation; PASS |
| Timeout/offline/truncated/cancellation | InterruptedIOException/SocketTimeoutException mapping; existing streaming cancellation/publication tests; PASS |
| Failed refresh preserves A | Pointer và generation files giữ nguyên qua transport/content/resource failures; PASS |
| Readiness/hazard/strict deny | Old timestamp, ATTACHED, false/raw true claims không cấp authority; F01–F05/F07 regressions PASS |
| Diagnostics | Token/URL/server payload không xuất hiện trong normal error diagnostics; redacted DTO; PASS |
| Valid production workload | Read-only 33MB graph test PASS 7294 nodes/16651 edges |

Development history: một test literal boundary ban đầu dùng integer cực lớn bị strict Gson parser reject vì malformed JSON theo parser; đây là failure thật, không được ghi là PASS. Không nới parser hoặc đổi dependency. Dùng valid decimal fractional zeros tại 128-byte boundary để kiểm guard độc lập với giant-integer handling, giữ direct production-parser assertion; byte129 bị reject. Final matrix chạy lại toàn bộ sau khi sửa test và mapping InterruptedIOException. Không @Ignore hoặc bỏ scenarios để làm xanh.

Fresh command (exit 0; 90 tests, 4 suites; failures/errors/skipped=0; --rerun-tasks thực thi lại):

```bash
./gradlew --offline --no-daemon :app:testDebugUnitTest --rerun-tasks \
  --tests '*RoutingNetworkSecurityTest' --tests '*GraphCacheSecurityTest' \
  --tests '*GraphDownloaderTest' --tests '*P5A01ConsumerAuditTest'
```

Counts đọc từ JUnit XML, không đếm test declarations. XML snapshots /tmp/p5net-fresh-results; log /tmp/p5net-fresh.log.

## 11. Routing Regression Results

```bash
./gradlew --offline --no-daemon :app:testDebugUnitTest \
  --tests 'com.example.bai1.rescue.routing.*'
```

Exit 0; **462 tests/13 suites; 0 failures, 0 errors, 0 skipped**. Baseline433 +29 mới. Giữ các F01–F05/F07 regressions, UCS/turn restriction/DFA, loader/repository và cache security. XML snapshot /tmp/p5net-routing-results; log /tmp/p5net-routing.log.

## 12. Full Android JVM Results

```bash
./gradlew --offline --no-daemon :app:testDebugUnitTest
```

Exit 0; **503 tests/26 suites; 0 failures, 0 errors, 0 skipped**. Full474 trước task +29 mới. Bao gồm Auth/SOS regression, không sửa business source của chúng. XML /tmp/p5net-full-results và app/build/test-results/testDebugUnitTest; HTML app/build/reports/tests/testDebugUnitTest/index.html; log /tmp/p5net-full.log. Existing DerivedStateFlow/API/deprecation warnings không phải test failure, không refactor ngoài scope.

## 13. APK Build Results

```bash
./gradlew --offline --no-daemon :app:assembleDebug
```

Exit 0, BUILD SUCCESSFUL. APK: `/home/vantri/AndroidStudioProjects/bai1/app/build/outputs/apk/debug/app-debug.apk`.

Size: 96,434,434 bytes. SHA-256: `b906a76df36f4b09fcfc7861ffe885dc68694131df22b9e9e8faace616e524dd`.

ApplicationId com.example.bai1, versionCode1/versionName1.0 không đổi. Không ký/build phát hành release, không install/open APK hoặc deploy. Log /tmp/p5net-apk.log.

## 14. Merged Manifest/Platform Verification

```bash
./gradlew --offline --no-daemon :app:generateReleaseBuildConfig \
  :app:processReleaseManifest :app:processReleaseResources
```

Exit 0, BUILD SUCCESSFUL. Kiểm tra actual outputs:

- app/build/intermediates/merged_manifests/debug/processDebugManifest/AndroidManifest.xml
- app/build/intermediates/merged_manifests/release/processReleaseManifest/AndroidManifest.xml
- app/build/generated/source/buildConfig/release/com/example/bai1/BuildConfig.java

Cả hai merged manifest có usesCleartextTraffic=false và đúng networkSecurityConfig reference; debug debuggable=true, release không có debug enable; generated release BuildConfig.DEBUG=false.

Dump compiled XML bằng aapt2 (exit 0):

```bash
/home/vantri/Android/Sdk/build-tools/36.0.0/aapt2 dump xmltree \
  app/build/outputs/apk/debug/app-debug.apk --file res/xml/routing_network_security_config.xml
/home/vantri/Android/Sdk/build-tools/36.0.0/aapt2 dump xmltree \
  app/build/intermediates/linked_resources_binary_format/release/processReleaseResources/linked-resources-binary-format-release.ap_ \
  --file res/xml/routing_network_security_config.xml
```

Debug compiled resource: base false, domain true chỉ ba exact local hosts, includeSubdomains=false. Release compiled resource: base false, không domain-config/true/debug allowance. Dumps /tmp/p5net-debug-nsc.txt và /tmp/p5net-release-nsc.txt; release build log /tmp/p5net-release-policy.log. Đây là evidence merge/resource compile, không là device/platform runtime test. **PLATFORM_ENFORCEMENT=UNVERIFIED.**

## 15. Emulator Inventory

Đã chạy adb devices -l, emulator -list-avds, adb -s emulator-5554 emu avd name và adb -s emulator-5554 shell getprop sys.boot_completed (exit0). Kết quả:

| Trạng thái | Giá trị |
|---|---|
| AVD_EXISTS | true — Pixel_6_Pro |
| AVD_RUNNING | true — emulator-5554 |
| DEVICE_CONNECTED | true — device, sdk_gphone16k_x86_64 |
| DEVICE_BOOT_COMPLETED | true — sys.boot_completed=1 |
| DEVICE_TESTS_EXECUTED | false |
| DEVICE_E2E | NOT_RUN |

Không thiếu emulator; lý do chưa chạy device tests là chưa có Firebase/SOS production isolation được chứng minh cho app launch. Theo yêu cầu task, không tự install APK/mở app. JVM loopback không được trình bày như Android network enforcement.

## 16. Backend/Graph Artifact Preservation

Baseline-vs-final SHA-256 và mtime_ns: **677/677 Backend files unchanged; 0 changed/new/removed**, gồm20 JSON/GZIP artifacts trong năm output directories. Android baseline206/220 unchanged,14changed đúng scope,0removed;4newfiles+báo cáo. Snapshot bao gồm untracked source, không chỉ git-tracked. Excluded path parts: .git, .gradle, build, dist, node_modules, .idea, .kotlin; không claim generated build outputs unchanged.

Snapshots: /tmp/p5net-android-baseline.json, /tmp/p5net-backend-baseline.json và final counterparts; comparisons /tmp/p5net-android-comparison.json, /tmp/p5net-backend-comparison.json. Không commit/push/deploy/reset/clean; không sửa Backend service/controller/storage/qualification/graph bytes. git diff --check cuối task exit0.

Fingerprint các file task (SHA before/after và mtime_ns; file mới không có before):

| File | Before SHA-256 / mtime_ns | After SHA-256 / mtime_ns |
|---|---|---|
| `app/src/debug/res/xml/routing_network_security_config.xml` | `NEW` | `073ac2ac23d6c6206291944d55093ad6f22a3eb4bbc16a1795905b19db171a5e / 1793984051287503079` |
| `app/src/main/AndroidManifest.xml` | `e423200e77a48a115cb75f40c70e29df9966a60ed1a4b9af5595f91198c2e7bb / 1789742519292389652` | `b98c9e60f57e44345f1b4ba4d95ea5d0a3ea3a984d7062790e1dfb0022f99454 / 1793984051287030160` |
| `app/src/main/java/com/example/bai1/rescue/routing/download/GraphDownloadModels.kt` | `e17782c78e3bc13baefb4bfc9c9b4f0184febf116cc3669473c71cee0c067b54 / 1793981822497654244` | `4c26fc98fb027595fd263d036de5ede9d4445881dfdccb76db0dc718f8e81c6e / 1793984143223280139` |
| `app/src/main/java/com/example/bai1/rescue/routing/download/GraphDownloader.kt` | `844cb7f0c11cb01bb3afba38725b8d18fcab2656e81a6f069aae2086ae3184c7 / 1793981989395799116` | `0c8dcd2b3ac68fad30918fb158003cd055f970228e5b57b8ffcd0d4640181785 / 1793985611242891138` |
| `app/src/main/java/com/example/bai1/rescue/routing/loader/RoutingGraphLoader.kt` | `ae316f692cb7f48f8e5cbdaec43557b0db3242e17722ae2a158112a4ee416f63 / 1793981989395629775` | `678241c769d21ff8175d0cf29927626e89dad20abdcf6ad3d86452ef1968434c / 1793984196161770863` |
| `app/src/main/java/com/example/bai1/rescue/routing/model/RoutingStatusModels.kt` | `128abb5508fa2e2c4e1df72994fc93ef1763d9dd9d9daf9b8584661f51976fc2 / 1793978137309111213` | `14b8eec4b8d504f6651354e7d047f969443e5c0133925b0c15a66efdb58f0dcb / 1793984745860348524` |
| `app/src/main/java/com/example/bai1/rescue/routing/network/RoutingApiClient.kt` | `87c8d443dc52a0c3b656e14fe778b598e690e2d3caaaee0ce441aa47dedd6f64 / 1793807129190059761` | `bef49159fc8f0bd926bfb2d62b0d4768feda277ded12c2b75342b220447a2790 / 1793985611242637640` |
| `app/src/main/java/com/example/bai1/rescue/routing/network/RoutingJsonParser.kt` | `3ee6040370db7f5148b09c1d24cda5b0e67591c901f1fe03e95e4682953b29f2 / 1793981759101286196` | `d7e5a56ded5a74ecd9f9c68dd35103232bc1ccf2e76e280e2882d935a5f985cd / 1793984051286444705` |
| `app/src/main/java/com/example/bai1/rescue/routing/network/RoutingTransportPolicy.kt` | `NEW` | `3c0e30e1dfe9e991ea33516658a51db3362520ae749b3595bef498e81af15afe / 1793985007375343633` |
| `app/src/main/java/com/example/bai1/rescue/routing/repository/RoutingRepository.kt` | `12f949144a2f4d662c48bc769a72f9ccbbaf2a461e0cedd6656f6b25028346a9 / 1793978137311285363` | `034c9db2c04c183436cf41a25aef923ca45081e72dd3909edd6e9da491b3ef95 / 1793984196160886854` |
| `app/src/main/res/xml/routing_network_security_config.xml` | `NEW` | `dea35ebed76536c24c13f74036f02dc874f944147cb3a49c2ad0550f71e3e0c7 / 1793984051287230925` |
| `app/src/test/java/com/example/bai1/rescue/routing/GraphCacheSecurityTest.kt` | `f56b500fe709028bd33351007c3fda211e174cba32b5a32e3972e57e26509f3a / 1793982271415696662` | `485adef9958097879e5fd399cab9dde8c36921d272e927dd64ad772103f14676 / 1793984196163886868` |
| `app/src/test/java/com/example/bai1/rescue/routing/GraphDownloaderTest.kt` | `f5fd25699e2c2ed562cb11f4c0029e0bcfa293532fafc953f6c2f1b1b701efa3 / 1793982271415904829` | `01700373d233d2dc0e94e0e10231f54568e694035e79be7dc8cee20b92798e0a / 1793984196163147499` |
| `app/src/test/java/com/example/bai1/rescue/routing/P5A01ConsumerAuditTest.kt` | `743adb5533708b5248f03d76466de30b56c0c4b8bb2139f56360b6aa242393eb / 1793981899957826319` | `659cba52c4f7554339fc3c8c52b3ca71dac24ca85cb465fc439e9e1485e672a5 / 1793984196163526176` |
| `app/src/test/java/com/example/bai1/rescue/routing/RoutingApiClientTest.kt` | `0e170791bf198bdfbabade9ed453bd523b469a386564e5f9ff15f827a843fc34 / 1793807129295062620` | `a68ff920d62e95b2c1a0d587f1f36fe1ee9f5a3d9fa44ba52272b8ae16412017 / 1793984196162195417` |
| `app/src/test/java/com/example/bai1/rescue/routing/RoutingJsonParserTest.kt` | `038f4cb008a101d30501a06b9b37eac597b4221a77c478cd0a93d391e16bb966 / 1793978485034928969` | `450afa6baafc2cdd49e6dc812c215c4cd044e2c143476712f13f029f8c37d90b / 1793984196162455690` |
| `app/src/test/java/com/example/bai1/rescue/routing/RoutingNetworkSecurityTest.kt` | `NEW` | `a9967cab08d99489b7e1298da7e32c8ea718a740a1e426b287f02dd2adc78af8 / 1793985611243132840` |
| `app/src/test/java/com/example/bai1/rescue/routing/RoutingRepositoryTest.kt` | `f5570bfa4f9fde1316b197a9597e277ddc204a241a6bc5b04ab462c23761c241 / 1793978485034669299` | `4f39f784f51d67f19d096183fe1245c51a0189d33e7fea97f4cb2ced517a69ba / 1793984196162699065` |

Báo cáo không chứa self-hash để tránh self-reference; final snapshot chứa fingerprint của báo cáo.

## 17. Remaining F06 Hazard Gap

**F06 OPEN.** Chưa tích hợp authoritative live hazard/field freshness, chưa có production strict-routing qualification verifier. ATTACHED metadata không chứng nhận an toàn; URL còn hạn hoặc graph integrity không thay thế hazard authority. Không làm hazard parser, Backend verifier hoặc kích hoạt route trong task này.

## 18. Remaining Device/Network Limitations

Platform cleartext enforcement, Android TLS/device HTTP, 10.0.2.2 actual fixture reachability và device E2E chưa chạy. Không gọi real Backend/R2/Firebase hoặc dùng production credentials. HTTPS-positive JVM là interceptor acceptance, không là real TLS/R2 validation. Android elapsedRealtime/main-thread branches được review ở source nhưng không instrumented. Large workload JVM acceptance không chứng nhận RAM/latency mọi điện thoại.

Không thay thế các limitation CACHE đã ghi nhận: chưa chứng minh power-loss durability, same-UID hostile race hoặc total retained-generation quota/GC; byte caps không khắc phục các điểm đó. Không nới cache binding/atomic publication để xử lý network errors. Per-read budget và socket timeout có granularity như mục8. Các giới hạn này không được ghi là platform/security tests đã PASS.

## 19. Final Acceptance

**F08: PASS (source + fresh JVM integration + merged/compiled variant policy assurance).** Release HTTP reject; debug allowlist hữu hạn; JWT không đi qua download/redirect; TTL/session renewal fail-closed; content/raw hash và resource boundaries có tests; graph hiện tại parse thành công; F01–F05/F07, routing/full JVM và debug build PASS. Strict routing DISABLED. Backend/artifacts không đổi.

**PLATFORM_ENFORCEMENT=UNVERIFIED; DEVICE_E2E=NOT_RUN; F06=OPEN.** Không tuyên bố production/network/device certification. Không triển khai ngoài scope.

## 20. Recommended Next Task

Task riêng để chuẩn bị môi trường Android instrumentation/fixture cô lập Auth/Firebase/SOS và xác minh platform HTTP/HTTPS, exact debug allowlist cùng release deny trên thiết bị. F06 hazard authority cần task/contract riêng trước bất kỳ production strict activation nào. Chỉ đề xuất, chưa bắt đầu các task đó; không tự làm F06/P5-P01/P5-I02/UI hoặc deploy.
