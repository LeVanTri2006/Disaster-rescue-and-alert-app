# PHASE 5 – P5-A01: ANDROID ROUTING CONSUMER CONTRACT REVIEW

Ngày đánh giá: 06/11/2026, Asia/Bangkok.
Android repository: /home/vantri/AndroidStudioProjects/bai1.
Backend đối chiếu, chỉ đọc: /home/vantri/AndroidStudioProjects/canh-bao-thien-tai-backend-app.

## 1. Executive Summary

**Audit source và bàn giao: hoàn thành. Safety acceptance: FAIL / BLOCKED. Android build, JVM tests và device E2E: NOT_RUN.**

Android có HTTP client đúng endpoint, DTO 2.1.0, downloader hash raw GZIP, streaming parser, cache, loader/context, DFA và UCS. Tuy nhiên metadata legacy vẫn có thể được dùng làm căn cứ cấp strict routing trong context/UCS; chưa có production-authoritative qualification boundary. Loader có thể xuất context còn Permitted bên cạnh Boolean disabled; nhánh reuse cùng graphVersion bỏ qua đánh giá dữ liệu readiness mới. Cache JSON giải nén có thể bị thay đổi cùng kích thước trong khi GZIP vẫn hash đúng. Đây là các finding từ source thực tế, không suy từ tên class/comment.

Đã thêm **8 characterization tests** gọi production parser/downloader/repository/loader/context/UCS, chỉ thay transport bằng fixture/interceptor. Chưa compile hoặc chạy được: Gradle wrapper dừng vì JAVA_HOME chưa đặt và không tìm thấy java. Không cài JDK/SDK/dependency mới. ADB chạy thành công nhưng danh sách thiết bị trống; không có emulator/device để smoke test.

Không sửa production Android, UCS, Backend hoặc tests có sẵn. Không tự mở strict routing. UI hiện chưa nối routing pipeline; vì vậy không khẳng định người dùng đang nhìn thấy chỉ đường unsafe từ các nhánh thư viện này. Tuy nhiên các nhánh production routing đã tồn tại và không đủ an toàn để tích hợp vào demo cứu hộ.

## 2. Repository Baseline

Đã chạy pwd, git status --short, git diff --check tại Android repository trước thay đổi; diff check exit 0. Không tìm thấy AGENTS.md áp dụng trong cây repository hoặc thư mục cha đã kiểm tra.

Modified có sẵn: app/build.gradle.kts và app/src/main/java/com/example/bai1/MainActivity.kt. Untracked có sẵn: cây rescue/routing production và rescue tests. Giữ nguyên toàn bộ; không reset/restore/clean/commit/push/deploy.

Chụp SHA-256 và mtime_ns dạng chuỗi cho **212 Android files có trước task** và **677 Backend files** ngoài generated/build/cache/Git/node_modules. Không công bố nội dung credentials/config secrets. Snapshot dùng đối chiếu source/fixtures/config/docs/artifacts; không coi timestamp build/cache là source mutation.

| Thành phần | Cấu hình đã đọc, không phải phiên bản runtime được chạy |
|---|---|
| Module/variants | :app; debug/release, không thấy product flavors |
| Gradle wrapper | 9.7.1; distribution có sẵn trong ~/.gradle/wrapper/dists |
| Android Gradle Plugin | 9.4.0 trong version catalog |
| Kotlin/Compose plugin | Catalog Kotlin 2.2.10; compiler runtime chưa xác minh |
| JDK/source target | Daemon criteria Java 25; source/target Java 11; JAVA_HOME/java hiện không có |
| SDK | compile/target 37, min 30; local platform android-37.0 tồn tại; không khẳng định toàn bộ build prerequisites đủ |
| JVM tests | JUnit 4.13.2, coroutines-test 1.7.3, org.json 20240303 |
| Instrumented tests | AndroidJUnitRunner; app/src/androidTest chỉ thấy ExampleInstrumentedTest kiểm tra package name |
| Networking | OkHttp 4.12.0, Gson 2.10.1; application usesCleartextTraffic=true; không thấy network security config riêng |

Debug base URL mặc định http://10.0.2.2:3000; release mặc định https://api.example.invalid nếu không được cấu hình. Chỉ đọc cấu hình, không truy cập các endpoints hoặc khởi động app có Firebase/Maps/SOS dependencies.

## 3. Android Architecture Inventory

Các đường dẫn trong phần routing bên dưới tính từ app/src/main/java/com/example/bai1/rescue/routing/.

| Thành phần | Implementation và nhận xét |
|---|---|
| API client | network/OkHttpRoutingApiClient trong RoutingApiClient.kt; Bearer token, /routing/current, status/error mapping |
| DTO/parser | model/RoutingStatusModels.kt; network/RoutingJsonParser.kt; root envelope hoặc data wrapper; đọc response.download.url |
| Repository | repository/DefaultRoutingRepository trong RoutingRepository.kt; StateFlow memory; API errors không xóa status cũ |
| Downloader | download/OkHttpGraphDownloader trong GraphDownloader.kt; streaming raw GZIP/hash/length, no bearer tới download endpoint |
| Graph parser | parser/StreamingRoadGraphParser trong RoadGraphParser.kt; Gson JsonReader, version/profile/node/edge/topology validation |
| Cache | Files dưới Context.cacheDir/routing_graphs; gzip + JSON + .meta, không phải Room/DataStore routing cache |
| Loader | loader/DefaultRoutingGraphLoader; tải/parse/DFA/index/context/snapshots; reuse dựa graphVersion |
| Eligibility | model/RescueRoutingContext và loader.evaluateSafetyGate; counts/flags/restrictions, chưa có authoritative qualification receipt |
| Algorithm | algorithm/UniformCostSearchRouter; context overload và raw RoadGraph overload; lọc legacy VERIFIED_COMPATIBLE |
| Auth/session | auth/session/TokenStorage.kt SharedPreferences; AccessTokenProvider đọc token; AuthRepository.logout xóa tokens/signOut nhưng không clear routing singleton/cache/context |
| Wiring | RoutingAppDependencies.initialize tại MainActivity.kt:25; singleton khởi tạo một lần |
| Routing ViewModel/UI | **NOT_IMPLEMENTED trong production call sites tìm thấy**: không thấy màn hình gọi loadGraph/findRoute hoặc consume readiness flows |
| Map/hazard UI hiện tại | presentation/map/DisasterMapScreen/ViewModel hiển thị SOS/request markers, location; warning screens riêng; không phải graph-routing consumer |
| Offline authorization manager | NOT_IMPLEMENTED: chưa có revocation/expiry/session-bound production routing permission |

## 4. Backend API Contract Comparison

Đã đọc ba báo cáo Backend được yêu cầu và đối chiếu service/controller/storage hiện hành, không sửa Backend.

| Contract | Android thực tế | Đánh giá |
|---|---|---|
| GET /routing/current | RoutingApiClient.kt:39–45 tạo GET | Có implementation; chưa có bằng chứng UI đã thực thi hoặc Android–Backend E2E |
| Bearer JWT | TokenProvider → Authorization header | Có; role/APPROVED do Backend quyết định, client không thay policy |
| ADMIN/MODERATOR/RESCUER APPROVED | 401/403 được map riêng | Đúng boundary; friendly message 403 thiên về rescuer, không phải role enforcement |
| manifest + download + 5 lifecycle/readiness fields | RoutingJsonParser.kt:19–52 | Parse đúng envelope; thiếu top-level booleans default false, unknown readiness về NOT_READY_UNRESOLVED_RESTRICTIONS |
| download.url/expiresIn | Parser :173–179; downloader :124–137 | Đúng vị trí URL; TTL đọc nhưng chưa lưu issued-at hoặc chủ động kiểm tra expiry |
| manifestVersion/contractVersion 2.1.0 | Manifest parser :57; graph parser :174–183 | Có kiểm tra version; graph/profile binding khi có expectedManifest |
| SHA-256 raw GZIP | Downloader :239–246, :303–305 | Đúng thứ tự trước gunzip; không phải authoritative evidence |
| compressed/uncompressed length | Downloader :292, :401 | Có kiểm tra; còn cache binding và publication gaps |
| Backend P5-I01R download-only | Cold loader xét false tại :463 | Từ chối strict trên cold normal path; context/raw overload/cached state vẫn có bypass boundary riêng |
| Error envelope | Client dựa HTTP code, không parse cụ thể error.message | 401/403/404/500/503 thành Failure; repository có thể giữ READY cũ |

Không coi remediated response.manifest là bản sao stored artifact. Loader hiện so sánh readiness aliases với raw graph; Backend view khóa restriction status có thể khác raw graph restriction READY và dẫn tới CONTRADICTORY_READINESS. Không sửa Backend hoặc artifact để làm Android tương thích. Demo cần phân biệt quyết định authorization với metadata graph.

## 5. Strict Readiness Consumer Matrix

| Producer | Consumer/path:line | Quyết định/boundary |
|---|---|---|
| Response booleans | model/RoutingStatusModels.kt:124–125 | canNavigateSafely = strictRoutingReady && canAttemptStrictRouting; không có authority/expiry/session proof |
| Raw manifest/graph | model/RescueRoutingContext.kt:119–304 | Consistency, flags, counts, restrictions → Permitted; thiếu production verifier |
| Raw graph flags | algorithm/UniformCostSearchRouter.kt:210, :253–262 | Public raw overload không cần Backend/context; READY/eligibility đủ đi tiếp |
| Legacy edge status | algorithm/UniformCostSearchRouter.kt:419 | VERIFIED_COMPATIBLE được traverse nếu các checks khác đạt; không kiểm chứng nguồn của nhãn |
| Raw edge eligibility | parser/RoadGraphParser.kt:420–421 | Đọc và giữ isEligibleForStrictRouting; UCS edge loop chủ yếu lọc compatibilityStatus, không phải proof |
| Summary counts | model/RescueRoutingContext.kt:270–289; loader/RoutingGraphLoader.kt:494 | Counts làm gates; không xác thực qualification hoặc đối soát đầy đủ từng nguồn evidence |
| Fresh Backend false | loader/RoutingGraphLoader.kt:463–468 | Cold full load denied; không đủ để revoke context được giữ/bị xuất trước đó |
| Old same-version snapshot | loader/RoutingGraphLoader.kt:188–200 | Reuse context trước safety evaluation và trước downloadAvailable check; bỏ qua digest/profile/readiness mới cùng version |
| Disabled loader result | loader/RoutingGraphLoader.kt:383–403 | Có thể trả context Permitted dù result.isStrictRoutingPermitted=false nếu manifest/graph legacy tự nhất quán nhưng root Backend flags false |
| API error | repository/RoutingRepository.kt:83–86 | Không clear currentRoutingStatus; cached canNavigateSafely có thể vẫn true |
| Loader fetch error | loader/RoutingGraphLoader.kt:169–175 | failed snapshot không truyền previous, xóa current graph/context; điểm fail-closed cần giữ |
| Loading snapshot | loader/GraphLoaderModels.kt:160–169 | Boolean bị reset false nhưng previous context còn được giữ; không được báo nhầm là loader Boolean vẫn true |

Không thấy consumer UI gọi các decision trên ngoài initialization. Đây là audit code path có thể được gọi, không phải bằng chứng UI runtime đã cấp quyền.

## 6. Legacy OSM Readiness Bypass Audit

**FAIL / BLOCKED.** Raw graph overload không yêu cầu API status hoặc authoritative context. RescueRoutingContext có comment “authoritative” nhưng thực tế kiểm tra metadata consistency + claimed checksum; constructor chỉ đối chiếu chuỗi artifactSha256 với manifest.bundle.sha256. Không có private provenance receipt/root/provider/independent qualification verification.

Ca tái hiện source: một cạnh a→b có geometry hợp lệ, legacy VERIFIED_COMPATIBLE, graph/manifest READY, summary verified=1/unverified=0, không restrictions. Context có thể đạt return Permitted tại :304; raw router có thể đi đến SUCCESS trên cạnh CLEAR. Không cần production provider, live hazard proof hoặc chữ ký qualification.

Các tests mới legacyRawGraphCanReachUcsSuccessWithoutAuthorityOrBackend và contextClaimsPermissionFromMetadataAndMatchingClaimedDigest gọi chính classes đó. **Chưa chạy được**, nên gọi là characterization source/test case, không gọi runtime exploit đã xác nhận. Fresh response Backend hiện đã khóa giúp normal cold path deny, nhưng không thay thế production lock tại raw overload/context.

## 7. Graph Download/Hash/GZIP Audit

Thứ tự network path có các điểm đúng: lấy download.url, không gửi JWT/refresh token sang storage, Accept-Encoding:identity, HTTP status checks, streaming SHA-256 raw compressed bytes, kiểm tra compressed length/hash rồi gunzip và kiểm tra uncompressed length. Graph parser kiểm tra contractVersion/graphVersion/vehicleProfile, duplicate IDs, dangling nodes và geometry/topology.

Accept-Encoding:identity được đặt rõ nên không dựa vào transparent gzip của OkHttp. Không thấy code kiểm tra Content-Type hoặc reject Content-Encoding bất ngờ; chưa có wire HTTP/device evidence trong task. Existing tests dùng OkHttp interceptor, không phải server/TCP.

Các thiếu sót:

- Cache: hash lại gzip nhưng dùng JSON giải nén cũ nếu cùng kích thước (:158–159), không hash JSON hoặc tái giải nén từ gzip đã xác minh. verifiedSha256 thuộc gzip không ràng buộc bytes JSON thực sự parse.
- Publication: gzip cũ bị xóa/replace trước decode (:316–347); JSON cũ bị delete trước rename (:413–417); .meta ghi riêng, parser validation diễn ra sau trong loader. Không phải transaction toàn bundle; interrupted/failed refresh có thể phá cache cũ.
- Download bytes cap dựa manifest; gunzip budget = maxOf(uncompressedSizeBytes*2, 50 MiB), không phải upper cap độc lập cố định và cần xử lý overflow. Không có independent schema maximum/time budget đủ cho mọi untrusted metadata.
- graphVersion chỉ trim/nonblank trong API parser; file names ghép trực tiếp ở downloader :523–536. Không thấy containment check/regex như Backend. Metadata độc hại chứa ../ có thể đưa file operations ra ngoài routing cache directory; chưa thực thi filesystem exploit.
- TTL không kiểm tra trước tải; 401/403 đều URL_EXPIRED. Repo auto-refresh thử lại một lần, nhưng loader trực tiếp gọi downloader nên không đi qua repo auto-refresh method.

## 8. Cache/Offline Behavior

| Tình huống | Hành vi source đã kiểm tra |
|---|---|
| 401/403/404/500/503 | API client Failure; repository giữ status cũ; loader nếu fetch Failure thì failed/xóa snapshot hiện tại |
| Offline/timeout | API Failure; không có explicit authorization revocation của repository hoặc references context đã được trao ra |
| URL expired | Downloader map 401/403; repo downloadGraphWithAutoRefresh refetch/retry một lần; loader không dùng method này |
| Manifest khác graphVersion | Loader drop old graph và full parse/bind; đúng hướng |
| Cùng graphVersion nhưng khác digest/profile/readiness | Nhánh reuse :188 bỏ qua so sánh đầy đủ, trả context cũ |
| Cache gzip hỏng/truncated | Rehash/length fail, clear cache rồi download lại; fail nếu network unavailable |
| Cache JSON bị đổi cùng độ dài | Có thể được coi valid vì gzip vẫn đúng; F04 |
| Download dở dang | temp files và failure handling có; transaction bundle vẫn chưa bảo đảm giữ generation cũ |
| Unsupported graph version/profile mismatch | Streaming parser có exceptions; existing tests có cases; chưa chạy trong task |
| Cached READY | Memory status có thể tồn tại sau lỗi; không có TTL readiness chung hoặc authenticated revocation |
| Logout | AuthRepository.kt:100–104 clear token/signOut; không gọi clearLoadedGraph/clearCache hoặc reset routing singleton |
| Stale hazard | Metadata tồn tại; field-assessment freshness chỉ áp dụng khi gọi đúng FloodAssessmentContext cho cạnh FLOODED |

Không tìm thấy routing manifest được persist bằng Room/DataStore. Không suy repository StateFlow còn sau process death: state đó là memory, disk cache là các files graph. Fresh app load với Backend error không được chứng minh tự dựng READY từ disk; vấn đề là stale in-memory state/context và raw graph API có sẵn.

## 9. UCS Routing Eligibility

UCS có hai lối chính: RescueRoutingContext (evaluate readiness) và raw RoadGraph (đọc raw flags). Edge loop loại UNVERIFIED, BLOCKED, UNKNOWN; giữ restrictions/DFA, geometry và flooded-assessment checks. Không thấy fallback strict→unverified trong thuật toán. NO_PATH và STRICT_ROUTING_DISABLED có status riêng, không tự biến thành SUCCESS.

Nhưng metadata-based Permitted và public raw overload không yêu cầu production proof. Context vẫn có thể được đưa vào router sau loader-disabled hoặc giữ qua refresh/logout. UcsRouteResult.SUCCESS biểu thị đường tính toán được, không có field chứng nhận qualification độc lập. Chưa thấy UI mapping SUCCESS thành tuyên bố an toàn; UI routing integration chưa tồn tại.

Không sửa UCS. Test mới có cả characterization legacy SUCCESS và kiểm tra UNVERIFIED edge → NO_PATH; không biến pass thuật toán thành pass safety.

## 10. Hazard Handling

Điểm tích cực: FailClosedFloodAssessmentTrustValidator/ProvenanceVerifier mặc định từ chối; PolicyBased validator yêu cầu source allowlist, verifier và signature, không tin hasValidProvenance tự khai; FLOODED cần assessment đúng edge/snapshot/freshness. UNKNOWN/BLOCKED bị loại trong UCS.

Gap: StreamingRoadGraphParser.kt:450 gán mọi edge condition=CLEAR ban đầu. Không thấy production hazard ingestion nối live snapshot vào routing graph hoặc một routing UI thực thi bước đó. UCS chỉ yêu cầu assessment khi condition=FLOODED (:497), còn CLEAR đi tiếp dù liveHazardReady=false. Vì vậy thiếu nguồn hazard không được tự diễn giải thành đường đang CLEAR ngoài đời.

Freshness của LiveFloodAssessment là policy/time caller cung cấp, chưa có trusted time provider production. Không thêm fake provider. Static test fixtures có thể truyền verifier trả true, nhưng không được coi là PKI thật; task này không đưa test verifier vào production.

## 11. UI Safety Labels

Đã tìm các labels safe/verified/READY/tuyến an toàn trong main source, đọc map screen/ViewModel, navigation và các call sites liên quan. Không tìm thấy routing screen consume loader/context/UCS hoặc polyline tạo từ kết quả UCS. MainActivity chỉ initialize RoutingAppDependencies; DisasterMapScreen hiển thị location/SOS/request markers.

Các chữ “an toàn” ở completion dialog cứu hộ, khuyến cáo bão lũ hoặc thông tin ứng dụng không phải tuyên bố graph-route đã được xác minh; không nâng chúng thành finding false-safe khi chưa có dữ liệu hỗ trợ. Ngược lại, UI download/readiness/offline/UNVERIFIED cho routing là **NOT_IMPLEMENTED**, cần bổ sung trước demo.

Không có device screenshot hoặc Compose runtime verification; UI runtime là NEEDS_VERIFICATION. Không khẳng định ứng dụng hiện tại đang chỉ một tuyến unsafe cho người dùng.

## 12. Kotlin/TypeScript Parity Inventory

| Capability | Android source thực tế | Trạng thái P5-P01 |
|---|---|---|
| Geometry canonicalization V2 | UCS validateAndNormalizeGeometry :805 có endpoint tolerance/reversal và checks chiều dài | Không phải Backend canonical geometry/hash V2; NOT_IMPLEMENTED cho parity contract |
| Half-Even rounding | Không tìm thấy implementation liên quan geometry identity | NOT_IMPLEMENTED |
| Directed edge identity | RoadEdge from/to/isWayForward và raw id; parser giữ direction/geometry | Có model, chưa có canonical binding/hash equivalence |
| V2 geometry hash | Không tìm thấy | NOT_IMPLEMENTED |
| Graph artifact digest | GraphDownloader MessageDigest SHA-256 raw GZIP | IMPLEMENTED theo source; runtime/golden cross-language chưa chạy |
| Vehicle profile configuration digest | Chỉ profile name comparison | NOT_IMPLEMENTED |
| Flood canonical payload | LiveFloodAssessment.createCanonicalProvenancePayload string fields | Contract khác geometry/profile hashing; không gọi là parity V2 |

Không viết thuật toán Kotlin mới hoặc chạy golden vectors; chưa đạt parity. Có thể lập task P5-P01 với vectors thực và scope riêng, nhưng thiếu JDK hiện chặn execution và nhiều counterpart chưa tồn tại.

## 13. Unit Test Results

**NOT_RUN / NOT_COMPILED: zero JVM cases được thực thi trong task.** Không dùng counts từ reports lịch sử hoặc artifacts build cũ để tuyên bố PASS.

Thêm app/src/test/java/com/example/bai1/rescue/routing/P5A01ConsumerAuditTest.kt với 8 @Test methods. Gọi production StreamingRoadGraphParser, OkHttpGraphDownloader, DefaultRoutingRepository, DefaultRoutingGraphLoader, RescueRoutingContext và UniformCostSearchRouter. Chỉ API transport/OkHttp response được thay; không viết consumer/gate giả. Dữ liệu synthetic trong memory và thư mục Files.createTempDirectory do test sở hữu, teardown chỉ xóa root đó; không dùng graph Backend thật làm nơi ghi.

| New test | Source behavior cần xác nhận khi có JDK |
|---|---|
| legacyRawGraphCanReachUcsSuccessWithoutAuthorityOrBackend | Legacy raw graph → UCS SUCCESS, finding F01 |
| contextClaimsPermissionFromMetadataAndMatchingClaimedDigest | Context Permitted từ metadata/checksum string, F01 |
| coldBackendFalseDeniesLoaderButStillExportsPermittedLegacyContext | Result false nhưng context có thể chạy, F02 |
| sameVersionRefreshToBackendFalseRetainsOldPermittedContext | Refresh locked cùng version giữ context cũ, F02 |
| repositoryRetainsCachedReadyAcrossHttpAndOfflineErrors | HTTP codes/offline → currentRoutingStatus READY còn, F03 |
| cachedJsonCanChangeAtSameLengthWhileGzipDigestRemainsValid | Production downloader trả JSON cùng size đã đổi, F04 |
| failedGzipRefreshDestroysPreviousCompressedCacheBeforeDecodeValidation | Failed forced refresh làm mất gzip generation trước, F05 |
| unverifiedEdgeDoesNotBecomeTraversableMerelyBecauseGraphCountsSayReady | Production UCS loại UNVERIFIED dù summary READY |

Các assertions SUCCESS/Permitted trong characterization mô tả lỗi hiện có, không phải safety acceptance. Chưa có kết quả compile, nên test source vẫn NEEDS_VERIFICATION và phải được chạy trước dùng làm regression evidence.

Đối chiếu 18 nhóm yêu cầu: Backend false/raw true/cache READY và API500/stale cache có tests mới; legacy/UNVERIFIED/hazard-false path có fixture/source; corrupted GZIP/hash/length/expiry/timeout/IO có existing GraphDownloaderTest cases 4–10; contract/graph/profile mismatches có RoadGraphParserTest; missing/unknown readiness có RoutingJsonParserTest/source default-deny. Interrupted download và transaction crash/atomic replacement cần harness fault tests sâu hơn. **Toàn bộ các nhóm đều chưa runtime-verified ở phiên này vì JDK blocker**, không đổi status thành PASS chỉ vì có tên tests.

## 14. Build Results

Đã thử:

```bash
./gradlew --offline --version
./gradlew --offline --no-daemon :app:testDebugUnitTest :app:assembleDebug
```

Cả hai exit **1**, wrapper báo JAVA_HOME is not set and no java command could be found in PATH. Không đến Gradle task execution, nên tests/build là NOT_RUN, không phải compile failure của source được xác nhận. Không download JDK/SDK/dependency, không sửa Gradle versions hoặc properties. Đã kiểm tra các vị trí JDK phổ biến trong môi trường, chưa tìm thấy binary dùng được.

Lệnh targeted đề xuất sau khi người phụ trách cung cấp JDK phù hợp sẵn có:

```bash
./gradlew --offline --no-daemon :app:testDebugUnitTest --tests 'com.example.bai1.rescue.routing.P5A01ConsumerAuditTest'
```

Sau targeted cần toàn bộ :app:testDebugUnitTest và :app:assembleDebug. Đây là lệnh chưa chạy thành công, không phải evidence PASS. Không tự cài thêm dependency để vượt giới hạn task.

## 15. Emulator/Device Results

/home/vantri/Android/Sdk/platform-tools/adb devices -l exit **0**, List of devices attached trống. ADB daemon được khởi động bởi lệnh inventory; không cài hoặc launch app, không xóa app data, không thay settings thiết bị.

Device smoke/instrumented/Android–Backend HTTP E2E: **NOT_RUN**, không có thiết bị/emulator. Không chạy connected tests hoặc đăng nhập cloud production, không gửi SOS. Không tự boot emulator khi chưa có image/runtime được xác nhận sẵn và app chưa được cô lập Firebase/Maps/SOS.

## 16. Security Findings by Severity

Các đường dẫn routing dưới đây theo base ở mục 3. Findings là static source findings; dynamic reproduction chưa được chạy. Severity phản ánh hậu quả nếu nhánh được tích hợp, không khẳng định screen hiện đang thực thi nó.

### A01-F01 — CRITICAL / P0: Legacy metadata cấp strict permission

- Source/Line: model/RescueRoutingContext.kt:119, :270, :304; algorithm/UniformCostSearchRouter.kt:210, :253, :419.
- Producer: raw graph/manifest READY, counts, legacy VERIFIED_COMPATIBLE.
- Consumer: context.isStrictRoutingPermitted và raw/context UCS overloads.
- Trust boundary: metadata/integrity → production routing approval, không có authority verifier.
- Reproduction: graph synthetic một cạnh legacy READY/verified=1, empty restrictions, matching claimed digest; construct context hoặc gọi raw overload; tests mới 1–2 mô tả đường tới Permitted/SUCCESS.
- Expected safe behavior: không production authority thì strict disabled, dù metadata/digest phù hợp.
- Recommended fix: task Android remediation khóa context/router production bằng một quyết định fail-closed; không thêm caller boolean hoặc test-verifier bypass; tách algorithm demo khỏi safety approval.

### A01-F02 — CRITICAL / P0: Context permission thoát khỏi loader-disabled/revalidation

- Source/Line: loader/RoutingGraphLoader.kt:188–200, :383–403; loader/GraphLoaderModels.kt:160–169; model/RescueRoutingContext.kt:69.
- Producer: old Permitted context hoặc nested legacy manifest READY.
- Consumer: GraphLoaderResult.context/currentContext → UCS.findRoute(context).
- Trust boundary: fresh Backend false → retained executable context không nhận revocation mới.
- Reproduction: cold response root false với nested legacy READY → result false nhưng context có thể Permitted; hoặc warm old context, response locked cùng graphVersion → early reuse giữ context cũ. Tests mới 3–4.
- Expected safe behavior: disabled result không xuất context có quyền; mỗi refresh xét digest/profile/authorization mới; held context không còn là quyền vĩnh viễn.
- Recommended fix: invalidate/rebind context theo authorization generation/session/freshness; bỏ reuse dựa version đơn độc. Lưu ý Boolean snapshot đã false khi loading; lỗi nằm ở context, không phải khẳng định Boolean vẫn true.

### A01-F03 — HIGH / P0: Cached READY sống qua API lỗi và logout

- Source/Line: repository/RoutingRepository.kt:83–86, :99–112; model/RoutingStatusModels.kt:125; auth/AuthRepository.kt:100–104.
- Producer: currentRoutingStatus memory cũ; auth logout chỉ clear token.
- Consumer: canNavigateSafely/download methods và references routing singleton/context.
- Trust boundary: error/no-session không thu hồi quyền metadata đã lưu.
- Reproduction: successful legacy READY rồi 401/403/404/500/503/offline; repository giữ status cũ; test mới 5. Logout chưa có hook routing; không cần khẳng định status tồn tại sau process death.
- Expected safe behavior: revoke authorization ngay, giữ cache chỉ để xem tham khảo với nhãn unverified; clear session-bound context khi logout.
- Recommended fix: state machine tách data availability/authorization/error; lifecycle/session invalidation tests. Giữ điểm fail-closed hiện có của loader khi fetch Failure.

### A01-F04 — HIGH / P1: Verified gzip không ràng buộc cached JSON được parse

- Source/Line: download/GraphDownloader.kt:85–97, :158–178.
- Producer: cache gzip hợp lệ và JSON khác nội dung nhưng cùng độ dài.
- Consumer: download success verifiedSha256 → loader/parser đọc JSON cũ.
- Trust boundary: hash của artifact A bị gán nghĩa xác thực nội dung B.
- Reproduction: tải fixture, sửa 111→999 trong JSON cùng length, giữ gzip; isGraphCached chỉ hash gzip và downloader trả JSON bị đổi; test mới 6. Cần khả năng sửa/hỏng cache trong sandbox app; không khẳng định remote arbitrary write đã xảy ra.
- Expected safe behavior: JSON phải được derive lại từ gzip đã verify hoặc có binding hash riêng được xác thực.
- Recommended fix: immutable generation + verified decompression/JSON binding, không tin file size/.meta làm chứng cứ nội dung.

### A01-F05 — HIGH / P1: Cache replacement chưa transaction toàn bundle

- Source/Line: download/GraphDownloader.kt:316–347, :401–441; loader/RoutingGraphLoader.kt:258–290.
- Producer: forced refresh mới, gzip/hash đúng nhưng decode/parse sai hoặc crash.
- Consumer: cached gzip/JSON/meta và parser.
- Trust boundary: generation chưa validated đã thay một phần generation cũ.
- Reproduction: cache tốt rồi forced refresh digest-matched non-GZIP; gzip cũ đã bị xóa trước decode, lỗi xóa gzip mới; JSON cũ còn nhưng cache cặp không valid; test mới 7. Crash/concurrency chưa chạy.
- Expected safe behavior: giữ generation cũ cho display-only đến khi generation mới qua decode/schema/identity và commit nguyên tử.
- Recommended fix: stage riêng generation, validate đầy đủ rồi publish pointer; recovery/atomic failure tests; không xóa old valid files trước commit.

### A01-F06 — HIGH / P0 trước field use: Missing hazard bị diễn giải CLEAR

- Source/Line: parser/RoadGraphParser.kt:450; algorithm/UniformCostSearchRouter.kt:423–431, :497–517.
- Producer: parser gán CLEAR mặc định; chưa thấy live hazard ingestion production.
- Consumer: UCS CLEAR edge không yêu cầu FloodAssessmentContext/liveHazardReady.
- Trust boundary: thiếu tình trạng thực địa → presumed passable condition.
- Reproduction: legacy ready graph, liveHazardReady=false, không assessment, CLEAR mặc định; đường source của tests 1–2 vẫn đi tiếp. Runtime chưa chạy.
- Expected safe behavior: unknown live conditions không được gắn nhãn đã an toàn; có policy riêng về demo/reference và field routing.
- Recommended fix: task hazard integration/policy riêng, default unknown khi thiếu nguồn; giữ flooded provenance validator fail-closed và kiểm thử freshness/source.

### A01-F07 — HIGH / P1: Graph identity không bị giới hạn trước file path

- Source/Line: network/RoutingJsonParser.kt:62–65; download/GraphDownloader.kt:523–536 và :108–114.
- Producer: graphVersion nonblank nhưng có ../ từ metadata không đáng tin.
- Consumer: File(storageDir, graphVersion + suffix), clearCache/file writes.
- Trust boundary: external identifier → filesystem location.
- Reproduction: metadata graphVersion='../candidate'; parser không có regex; path có thể ra khỏi routing_graphs vào thư mục cha trong app sandbox. Chưa chạy write/delete exploit; không đụng file ngoài owned temp.
- Expected safe behavior: identifier grammar chặt và canonical containment trong owned storage root.
- Recommended fix: validation trước mọi path operation, bounded IDs, tests chỉ dùng temp root. Không sửa Backend để nới graph IDs.

### A01-F08 — MEDIUM / P1: Transport/freshness/resource validation chưa đầy đủ

- Source/Line: app/src/main/AndroidManifest.xml:12; network/RoutingJsonParser.kt:67–68, :173–179; download/GraphDownloader.kt:137–145, :229–261, :342, :467.
- Producer: HTTP URL/response/TTL, timestamps và sizes tự khai.
- Consumer: API/downloader/cache; API Bearer có thể đi cleartext theo config hiện tại.
- Trust boundary: untrusted transport/metadata → downloaded bytes/resource budget; integrity hash không xác lập nguồn nếu metadata bị thay cùng bytes.
- Reproduction: http:// được chấp nhận, usesCleartextTraffic=true không riêng debug; timestamps chỉ là strings, expiresIn không chủ động enforce; không thấy Content-Type/Encoding checks và independent hard maxima. Static only.
- Expected safe behavior: release HTTPS policy, bounded bytes/time/decompression, freshness/expiry semantics rõ và validation lỗi nhất quán.
- Recommended fix: debug-only loopback allowance, production transport policy/config review, hard limits/overflow checks và fault tests. Không tải SDK hoặc đổi config trong audit.

## 17. Files Changed

Chỉ thêm:

1. app/src/test/java/com/example/bai1/rescue/routing/P5A01ConsumerAuditTest.kt.
2. docs/PHASE5_P5A01_ANDROID_CONSUMER_AUDIT_REPORT.md.

Không sửa tests cũ hoặc production source để che findings. Test/report được chuẩn bị rồi ghi vào Android repository theo task; repository này nằm ngoài writable root mặc định nên thao tác ghi dùng quyền filesystem được kiểm tra. Không có automatic approval rejection. Không sửa Backend hoặc artifacts.

## 18. Baseline Preservation

Kết quả so sánh cuối được ghi sau khi hoàn thiện file: **212/212 Android baseline files và 677/677 Backend baseline files giữ nguyên SHA-256 và mtime_ns**. Chỉ có hai file mới nêu mục 17. Snapshot không bao gồm generated/build/.gradle/.git/node_modules; không giả đó là proof toàn filesystem không đổi.

Không overwrite assets hoặc Backend graphs, không xóa user cache. Các tests mới nếu được chạy chỉ tạo owned temp directory. Không chạy full tests sẵn có trong phiên này do thiếu Java; một số tests cũ đọc graph Backend bằng absolute path, nên trước lần chạy tương lai vẫn cần artifact preservation guard.

Phụ lục lưu digest/timestamp các source quan trọng để đối chiếu. Không sửa bất kỳ source unsafe đã tìm thấy; sửa phải là task remediation riêng.

## 19. Demo Readiness Assessment

**BLOCKED cho demo Android–Backend routing hoàn chỉnh.** Có thư viện routing nhưng chưa thấy UI/call-site tích hợp; build chưa chạy, không device, chưa parity và còn F01/F02/F03/F04.

Có thể dùng source/test fixtures để chuẩn bị kỹ thuật, không gọi đó là demo đã hoạt động. Trước demo cần production lock trên Android độc lập với legacy flags, context/cache invalidation, integrity/transaction fixes, UI nhãn chưa kiểm định và HTTP/device E2E bằng dữ liệu cô lập. Không khởi động app hiện tại vào production Firebase/Maps/SOS để lấy screenshot.

Download-only graph từ Backend có thể phục vụ hiển thị tham khảo sau khi consumer được kiểm thử; chưa có căn cứ cho navigation cứu hộ thực địa. Mốc real-world vẫn cần external data, policy, field validation và independent acceptance như gap report.

## 20. Required Android Remediation Tasks

| Task đề xuất | Scope/files chính | Definition of Done | Ưu tiên/phụ thuộc |
|---|---|---|---|
| P5-A01R-LOCK | RescueRoutingContext.kt, UniformCostSearchRouter.kt, RoutingStatusModels.kt | Không authoritative verifier → strict disabled ở mọi overload/context; legacy/hash/test receipt không cấp quyền; tests F01/F02 chuyển thành deny acceptance | P0 trước demo; task riêng được ủy quyền |
| P5-A01R-STATE | RoutingGraphLoader.kt, GraphLoaderModels.kt, RoutingRepository.kt, AuthRepository.kt | Không xuất context có quyền khi disabled/error/logout; revalidate same-version digest/profile/policy; session/generation expiry; cold/warm/offline tests | P0, song song LOCK |
| P5-A01R-CACHE | GraphDownloader.kt, RoutingJsonParser.kt, loader/parser | Cache JSON ràng buộc gzip; staging/commit/recovery giữ generation cũ; path containment và hard maxima; fault/crash tests | P1, trước demo E2E |
| P5-A01R-NET | AndroidManifest/build config, API/parser/downloader | HTTPS release/debug allowance riêng; TTL/content/resource validation; no-token/no-leak tests | P1; không cần credentials production |
| P5-A01R-HAZARD | RoadGraphParser/model/hazard integration/UCS policy, task riêng | Thiếu nguồn không thành CLEAR approval; verified field source/time policy, clear demo/reference limits | P0 trước field use |
| ENV-A01 | Existing JDK/Gradle/SDK và emulator do người phụ trách cung cấp | Chạy offline targeted, full JVM/assemble; device có dữ liệu cô lập, không cloud/SOS thật | Chặn mọi runtime evidence hiện tại |
| P5-P01 | Kotlin canonical identity/hash counterpart + vectors | Contract được duyệt, TS/Kotlin HalfEven/direction/hash/profile golden tests; không tự thiết kế trong audit | Có thể chuẩn bị scope; execution cần JDK và implementation counterpart |
| P5-I02 | Android ViewModel/UI demo + Backend fixture HTTP | Nhãn UNVERIFIED, no strict unlock, raw GZIP hash, cache/offline/error/device matrix và tái lập E2E | Chưa đủ điều kiện nghiệm thu; sau locks/cache/build, không cần production provider |

Không tự triển khai các task trên. Không gán deadline chắc chắn hoặc phần trăm dự án.

## 21. Final Audit Decision

**P5-A01 static audit đã hoàn thành và có findings theo file/line; safety FAIL/BLOCKED.** Build/tests/device limits được ghi rõ. Không có runtime PASS hoặc Android–Backend E2E PASS trong task này.

| Câu hỏi bắt buộc | Trả lời từ source/bằng chứng |
|---|---|
| Android đang gọi GET /routing/current chưa? | Có method production tạo GET; không thấy UI call site thực thi, không chứng minh request runtime đã xảy ra. |
| Tải graph qua download.url chưa? | Downloader đọc statusResponse.download.url; wiring có sẵn, runtime NOT_RUN. |
| Hash raw GZIP? | Có streaming MessageDigest trước decompress; cache JSON binding còn F04. |
| Compressed/uncompressed length? | Có network-path checks; không thay authority verification và cache-content binding. |
| Đọc legacy VERIFIED_COMPATIBLE? | Có parser và UCS filter; F01. |
| Backend false còn đường mở strict? | Cold full loader Boolean deny; exported/held legacy context và raw graph overload vẫn có đường permission/route; F01/F02. Không nói fresh remediated response tự làm Boolean true. |
| HTTP500/offline dùng cached READY? | Repository giữ memory READY; loader fetch failure xóa current snapshot, nhưng không revoke references/context đã trao. Disk-only fresh-start READY recovery chưa thấy. |
| UCS phân biệt route tính toán với verified route? | Có SUCCESS/NO_PATH/STRICT_ROUTING_DISABLED, nhưng permission gates metadata-based; không authoritative qualification certificate. |
| UI tuyên bố tuyến an toàn thiếu chứng cứ? | Chưa tìm thấy route UI/call site; không kết luận có tuyên bố sai hiện hành. Routing status/offline UI chưa implementation. |
| Có chạy emulator/device? | Không, ADB list trống; build/tests dừng trước Gradle do thiếu Java. |
| Cần sửa files nào để demo? | Context/router/status model, loader/snapshot/repository/auth invalidation, downloader/parser/path/cache, manifest network policy và ViewModel/UI riêng; chi tiết mục 20. |
| Bắt đầu P5-P01/P5-I02 được chưa? | Chuẩn bị contract/backlog được; chưa có đủ runtime prerequisites để coi parity hoặc E2E sẵn sàng. P5-I02 acceptance chờ remediation và device/JDK; không tự triển khai. |

## 22. Recommended Next Step

Ưu tiên task **P5-A01R khóa Android strict permission và thu hồi context/cache authorization**, dùng findings F01–F03 làm acceptance cases. Song song chuẩn bị JDK/toolchain đã cài phù hợp và device/harness cô lập để chạy 8 characterization tests trước, rồi viết deny acceptance cho remediation và full regression.

Tiếp theo xử lý integrity/transaction/path boundaries trước nối UI demo. Có thể chuẩn bị P5-P01 contract/vectors song song; P5-I02 chỉ nghiệm thu khi Android lock/cache/UI và HTTP/device faults có bằng chứng. Không đổi Backend để hợp thức hóa legacy Android flags, không biến demo thành chỉ dẫn cứu hộ đã kiểm định.

## Phụ lục – Baseline sources tiêu biểu giữ nguyên

SHA-256 và mtime_ns trước = sau; snapshot đầy đủ dùng so sánh 212 Android + 677 Backend files, không public secrets/config contents.

| Android file | SHA-256 trước = sau | mtime_ns trước = sau |
|---|---|---|
| app/build.gradle.kts | 2cff127fc28e8744848852011c881146cfad783ed527239b0bff48d45e17495a | 1793807884387939622 |
| app/src/main/AndroidManifest.xml | e423200e77a48a115cb75f40c70e29df9966a60ed1a4b9af5595f91198c2e7bb | 1789742519292389652 |
| app/src/main/java/com/example/bai1/MainActivity.kt | 6e0deee5f6143bf3228ebea191c4e4809e747264d5eb90de833da06677e6067c | 1793806868344058327 |
| app/src/main/java/com/example/bai1/rescue/routing/RoutingAppDependencies.kt | 5b5bd09a6856efa4a8da0ad529dbdc08dd462657141efe6feb659aa5a139bf02 | 1793808887735251074 |
| app/src/main/java/com/example/bai1/rescue/routing/algorithm/UniformCostSearchRouter.kt | 6ee13f403210b8fea4b06ab542395a3de2ee9bcad09f1f0972afe580cf927a2b | 1793882464288810959 |
| app/src/main/java/com/example/bai1/rescue/routing/algorithm/TurnRestrictionIndex.kt | d70975f8226864b6513d6b39becc092ed4cab4bd0de7e0c6652995e4e401e894 | 1793881698702460552 |
| app/src/main/java/com/example/bai1/rescue/routing/algorithm/ViaWayTurnRestrictionDfa.kt | 5add9ae7b8b74f366f3dca42026492c7300ca4d139005198f053aa3ce6c16b0f | 1793883595317308098 |
| app/src/main/java/com/example/bai1/rescue/routing/algorithm/UcsSearchState.kt | 8e3d576b7aad8e30fedf93bdf7a93543deae5e2a3abfbd8bec5b727387000890 | 1793811368460185031 |
| app/src/main/java/com/example/bai1/rescue/routing/network/RoutingApiModels.kt | 91524fab10cfeb69140337afe29b5db42f14c039152b24247db3c0adf7568f9d | 1793807129210060305 |
| app/src/main/java/com/example/bai1/rescue/routing/network/RoutingApiClient.kt | 87c8d443dc52a0c3b656e14fe778b598e690e2d3caaaee0ce441aa47dedd6f64 | 1793807129190059761 |
| app/src/main/java/com/example/bai1/rescue/routing/network/RoutingJsonParser.kt | 4e6982c0d5454d05e7b9de30e026de4e554e39a36d2dfb399d1efde640ad9219 | 1793807129244061231 |
| app/src/main/java/com/example/bai1/rescue/routing/repository/RoutingRepository.kt | cbe07eac7386e84ed345bdcbf05686129153ffcaaeefae6918ee785078c78fe4 | 1793807647817079059 |
| app/src/main/java/com/example/bai1/rescue/routing/download/GraphDownloadModels.kt | d56420d29177f05c0fa92540db8e0744ec61703a193b051124ed23ab19a76514 | 1793879338793458211 |
| app/src/main/java/com/example/bai1/rescue/routing/download/GraphDownloader.kt | 91e44b9cb34611c607dacc899a55bcadb0e4a3a6315e6e50a09f7ad41574ac1d | 1793879338855458696 |
| app/src/main/java/com/example/bai1/rescue/routing/loader/GraphLoaderModels.kt | 263d01a1707536bddc4e8ae7ba3a92a728fc3b889beb7f69de59f2a5db410573 | 1793876772679930771 |
| app/src/main/java/com/example/bai1/rescue/routing/loader/RoutingGraphLoader.kt | 9b453c7f20bd591579319fd166efd40ede4d5cf8091a0fd28dce5b2bfe862564 | 1793881014661373816 |
| app/src/main/java/com/example/bai1/rescue/routing/parser/RoadGraphParser.kt | 30e727d75e5de5f324588c73f4918e2ac0111cf100c9fdebf2001f2bcc6206f2 | 1793879339211825725 |
| app/src/main/java/com/example/bai1/rescue/routing/model/GeoPoint.kt | 51a2d124c94d733d0e580e3f8724d5843765f68aa2128ae9a634e2443c2ac258 | 1793717580689733903 |
| app/src/main/java/com/example/bai1/rescue/routing/model/RescueRoutingContext.kt | bd76cb5a01e83051b6e8b139da866bdf15153be7696587a2bdcfe18f779cd41b | 1793883610481318438 |
| app/src/main/java/com/example/bai1/rescue/routing/model/RoadEdge.kt | 09bfa22098ef341b8b538ad906060d031151d1805b9aab3027bd5011ebaca116 | 1793878636523375556 |
| app/src/main/java/com/example/bai1/rescue/routing/model/TurnRestrictionModels.kt | ce265b00ec6e85419b6688cd28b1f9114be6702054e32cb0388324bd8b05b7b0 | 1793879339152461017 |
| app/src/main/java/com/example/bai1/rescue/routing/model/UcsRouteResult.kt | 1259f12948d20956ff36a460c81423ab5dd7ffc25fc9ca7e32331caa171c7ef6 | 1793882464559811034 |
| app/src/main/java/com/example/bai1/rescue/routing/model/RoadCondition.kt | c8786ef9856291fee315985b3d2807e9c1679f2cc117ed8a42884ef09cf72eb2 | 1793717593047760168 |
| app/src/main/java/com/example/bai1/rescue/routing/model/RoadGraph.kt | 278fa84fe789a1a1828402e5ebc21dbc016a927bf164c0f7699c6c7a6cb72c73 | 1793879339089460525 |
| app/src/main/java/com/example/bai1/rescue/routing/model/LiveFloodAssessment.kt | a346d193b0b30ed9b363da2582be9e4aab62594e2d6671404de8ab32e1412b1b | 1793813158599674202 |
| app/src/main/java/com/example/bai1/rescue/routing/model/RoutingStatusModels.kt | a66a634b1a644c98f1a912deb976eca409914e84230ab59fc33a603d45a7b11b | 1793807129174059325 |
| app/src/main/java/com/example/bai1/rescue/routing/model/RoadNode.kt | 8e66bc7bf0200f6ffb84757345d9dc08d4cada9c6d05710c8c2011c472e9ab86 | 1793808224699072757 |
| app/src/main/java/com/example/bai1/rescue/routing/model/EdgeMetadataModels.kt | 56e6c6b08dcb48006f9f8fd351f67a3125d3c26d70092cb84e264e0ce55bde6e | 1793879338961459524 |
