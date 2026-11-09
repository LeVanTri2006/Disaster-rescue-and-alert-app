# PHASE 5 – P5-A01R: ANDROID STRICT ROUTING LOCK & STATE INVALIDATION

Ngày: 06/11/2026, Asia/Bangkok.
Android: `/home/vantri/AndroidStudioProjects/bai1`.
Backend chỉ đọc: `/home/vantri/AndroidStudioProjects/canh-bao-thien-tai-backend-app`.

## 1. Executive Summary

**CONDITIONAL / UNVERIFIED – đã triển khai source remediation F01–F03 và security regressions; chưa nghiệm thu runtime.** Không có production-authoritative qualification verifier, nên các strict routing entry points giữ khóa. Không có production unlock flag, caller approval Boolean, trusted=true, receipt giả hoặc test verifier cấp production approval.

Context không còn đường trả Permitted từ metadata. Bốn overload `findRoute` không còn chuyển quyền điều khiển đến search kernel. Input hợp lệ nhận STRICT_ROUTING_DISABLED; input lỗi vẫn có INVALID_INPUT/GRAPH_UNAVAILABLE, không biến khóa thành NO_PATH. Loader luôn revalidate manifest mới, không reuse chỉ theo graphVersion; loading/error không giữ nested context. Repository xóa dữ liệu phiên cũ khi refresh/lỗi, chặn response muộn và xuất trạng thái download-only. Auth logout/login/restore/refresh/role transition gọi routing invalidation.

Bộ P5-A01 có 21 test declarations: **19 security acceptance cases và 2 characterization cases F04/F05 chưa sửa**. Tăng từ 8 lên 21, không bỏ case cũ. Các suites UCS/loader/parser/repository cũ giữ nguyên số test; kiểm thử tính toán UCS được tách khỏi quyết định cấp quyền. Tất cả vẫn **NOT_COMPILED / NOT_RUN** trong phiên này.

```
JVM_TESTS = NOT_RUN
ANDROID_BUILD = NOT_RUN
DEVICE_E2E = NOT_RUN
```

Không tìm thấy Java/JDK/JBR đã cài ở các vị trí kiểm tra. Wrapper dừng trước Gradle, exit 1. ADB liệt kê được nhưng không có thiết bị. Không cài/download toolchain, không đổi Gradle/AGP/Kotlin, không chạy cloud/Firebase/SOS. F04–F08 vẫn mở; không tuyên bố demo Android–Backend hoàn thành.

## 2. Baseline Repository State

Đã kiểm tra pwd, git status --short và git diff --check. Không tìm thấy AGENTS.md áp dụng tại repo/cây source hoặc các thư mục cha đã kiểm tra. Đã đọc P5-A01 audit, P5-I01R Backend remediation và implementations được yêu cầu, TokenStorage, các tests liên quan. Không sử dụng Antigravity hoặc subagents.

Trước task: app/build.gradle.kts và MainActivity.kt đã modified; routing production/tests và docs đã untracked. Những trạng thái có sẵn được bảo toàn. Không reset/restore/clean/commit/push/deploy.

Baseline SHA-256 + mtime_ns: **214 Android files, 677 Backend files**, ngoài `.git`, `.gradle`, build/dist/node_modules/.idea/.kotlin. Snapshot lưu dạng JSON tại `/tmp/p5a01r-android-baseline.json` và `/tmp/p5a01r-backend-baseline.json`. Đây là proof cho tập files đã chụp, không phải toàn filesystem. Source gốc của các file chỉnh sửa được giữ trong `/tmp/p5a01r-original` để review diff, không phải git restore.

## 3. Java/Gradle Environment Findings

JAVA_HOME rỗng, `command -v java` không có kết quả. Các vị trí `/usr/lib/jvm`, `/opt/android-studio/jbr`, `~/android-studio/jbr`, `~/.jdks` không cung cấp JDK. Tìm thêm dưới /opt, ~/.gradle/jdks, ~/Downloads, ~/.local/share/JetBrains cũng không tìm thấy java executable. Không cài mới, không sửa global shell config, không giả lập compiler bằng kiểm tra văn bản.

Gradle daemon criteria: **toolchainVersion=25** trong gradle/gradle-daemon-jvm.properties. Cấu hình hiện có: wrapper 9.7.1, AGP 9.4.0, Kotlin catalog 2.2.10; source/target Java 11. Đây là cấu hình đọc từ source, không phải phiên bản runtime đã xác minh. Giữ nguyên tất cả.

Baseline targeted command trước sửa:

```sh
./gradlew --offline --no-daemon :app:testDebugUnitTest --tests 'com.example.bai1.rescue.routing.P5A01ConsumerAuditTest'
```

Exit 1: `ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH.` Tám characterization tests trước sửa **không chạy**, nên không ghi nhận runtime exploit baseline hoặc baseline PASS.

## 4. Root Cause F01

Legacy manifest/graph READY, counts và VERIFIED_COMPATIBLE từng dẫn đến `StrictRoutingEvaluation.Permitted`; raw RoadGraph overload có đường đi riêng vào UCS. Digest khớp chỉ đối chiếu integrity claim, không có provenance qualification. Constructor/copy không phải authority.

Sửa:

- RescueRoutingContext.kt giữ identity checks và các diagnostic denial cũ; nhánh cuối đổi từ Permitted thành Denied(PRODUCTION_AUTHORITY_UNAVAILABLE). Không có `return StrictRoutingEvaluation.Permitted` trong main source.
- UniformCostSearchRouter.kt giữ input/diagnostic checks, nhưng thay cả hai handoff `executeSearch(...)` bằng STRICT_ROUTING_DISABLED. Hai overload hazard tham số rời tiếp tục delegate vào hai overload đã khóa.
- RoutingStatusModels.kt: `canNavigateSafely` là computed false, không dựa vào DTO flags. Fabricated/copy/JSON READY không mint permission.
- GraphLoaderResult.Success và GraphLoaderSnapshot không còn nhận Boolean isStrictRoutingPermitted trong constructor/copy; property computed false.

Danh sách những nơi còn tên Permitted/SUCCESS và lý do:

| Nơi | Trạng thái sau sửa |
|---|---|
| StrictRoutingEvaluation.Permitted type | Giữ type để tương thích source; không được evaluator trả ra, không nhận từ constructor context hoặc router input |
| Public findRoute(context), findRoute(raw graph), hai overload hazard | Không thể SUCCESS; đầu vào hợp lệ bị khóa, không có production call vào kernel |
| private executeSearch, internal reconstructResult | Còn UcsRouteStatus.SUCCESS cho kết quả tính toán thuần túy; không là approval và không được strict entry point gọi vào. Không đổi thuật toán turn restrictions/geometry/hazard |
| GraphLoaderResult.Success / GraphDownloadResult.Success | Thành công đọc/tải dữ liệu, không phải route success; loader result permission luôn false |
| GraphLoaderState.GraphLoaded | Legacy data carrier còn type để tránh thay API ngoài nhu cầu; production loader không emit nó. Tạo/copy carrier không cấp quyền và router vẫn từ chối |
| Test fixture computation | Chỉ src/test kiểm tra private kernel, trước đó assert strict API thật từ chối. Không được dùng làm security acceptance hoặc đưa vào app/runtime |

## 5. Root Cause F02

Loader trước đây return sớm khi graphVersion giống nhau, trước kiểm tra readiness/downloadAvailable. Snapshot loading reset Boolean nhưng giữ context cũ; disabled result có thể xuất context tự cho Permitted.

Sửa nhánh reuse bị loại bỏ hoàn toàn. Mỗi lần load đi qua fresh status → download/cache validation → parser(expected manifest) → graphVersion/profile checks → context data mới → disabled state. Digest khác được downloader/hash revalidate; profile khác được parser kiểm tra; readiness mới được evaluate để diagnostics. Cùng version không phải lý do bỏ qua các bước này.

Loading và failed snapshot factories đặt context=null. Loader bắt đầu refresh không mang previous graph/context. Disabled result vẫn có thể giữ **Denied context để đọc dữ liệu**, nhưng cả context permission lẫn result permission đều false. Reference được trao trước đó cũng luôn Denied; không cần phát minh credential/session-bound approval khi authority chưa tồn tại.

Session generation counter chỉ bảo vệ publication: clearLoadedGraph tăng generation và clear snapshot/in-flight slot; request cũ không publish state hoặc trả successful current-session result sau clear. Request cũ đang xếp hàng bị bỏ qua trước API fetch. Response/download đã chạy có thể hoàn tất data work, nhưng không khôi phục snapshot phiên cũ. Không xóa disk cache.

## 6. Root Cause F03

Repository trước đây giữ currentRoutingStatus qua HTTP lỗi; canNavigateSafely suy trực tiếp hai flags. Auth logout không biết routing singleton và không reset status/context.

DefaultRoutingRepository hiện clear status ngay đầu refresh và trên Failure. 401/403/404/500/503, timeout/offline và exception không khôi phục READY. Success được project về strictRoutingReady=false, canAttemptStrictRouting=false, liveHazardReady=false, overall=NOT_READY_NO_VERIFIED_EDGES; giữ manifest/download data phục vụ đọc và diagnostics. Không mutate incoming DTO hoặc graph bytes.

Một generation counter dưới lock bảo vệ status publication. Refresh mới hoặc invalidateSession làm response cũ trở thành Failure thay vì republish. Cancellation giữ status đã clear và nhả loading; exception thông thường trả Failure UNKNOWN. Không dùng counter làm qualification credential.

RoutingAppDependencies.invalidateRoutingSession gọi repository.invalidateSession và loader.clearLoadedGraph, có guard initialization. AuthRepository gọi hook trước logout và trước register/login/googleLogin/restoreSession/updateRole/refreshOnce. Hook chỉ reset routing memory; AuthRepository tiếp tục chính sách token/Firebase vốn có. Callback injection chỉ phục vụ wiring/invalidation, không thể cấp routing permission.

## 7. Files Modified

8 production files, đúng allowlist:

| File dưới app/src/main/java/com/example/bai1 | Thay đổi |
|---|---|
| rescue/routing/model/RescueRoutingContext.kt | Final denial thay metadata permission; cập nhật mô tả trust |
| rescue/routing/model/RoutingStatusModels.kt | canNavigateSafely không mint từ flags |
| rescue/routing/algorithm/UniformCostSearchRouter.kt | Khóa hai core overload; giữ hai delegates và computation kernel |
| rescue/routing/loader/RoutingGraphLoader.kt | Bỏ version-only reuse, luôn disabled, chống late publication |
| rescue/routing/loader/GraphLoaderModels.kt | Computed permission, bỏ caller Boolean, clear context loading/failed |
| rescue/routing/repository/RoutingRepository.kt | Refresh/error/session invalidation, download-only projection |
| rescue/routing/RoutingAppDependencies.kt | Lifecycle hook, không circular call hoặc khởi tạo auth từ routing |
| auth/AuthRepository.kt | Hook routing tại session transitions; không đổi network/Firebase policy |

6 existing test files chỉnh sửa dưới app/src/test/java/com/example/bai1/rescue/routing:
P5A01ConsumerAuditTest.kt, RoutingRepositoryTest.kt, RoutingJsonParserTest.kt, RoutingGraphLoaderTest.kt, UniformCostSearchRouterTest.kt, EndToEndRoutingSafetyVerificationTest.kt.

2 files mới:

- app/src/test/java/com/example/bai1/rescue/routing/P5A01RComputationalInspection.kt.
- docs/PHASE5_P5A01R_ANDROID_STRICT_LOCK_REMEDIATION_REPORT.md (báo cáo này).

Không sửa Backend/importer/qualification engine/artifacts, GraphDownloader/parser/geometry/hash contract, AndroidManifest/network security hoặc build configuration.

## 8. Strict Permission Design

Không tích hợp một verifier giả hoặc API approval mới. Thiết kế hiện tại là production hard lock bằng code, không flag cấu hình. Các legacy flags vẫn là dữ liệu đầu vào để diagnostics, không đủ cấp quyền. Digest vẫn chỉ là integrity.

Context constructor/copy giữ checks graphVersion/profile/index/hash; readonly readiness không thể truyền vào constructor. Snapshot/result permission cũng không thể truyền true/copy true. Router tự khóa độc lập: ngay cả thay đổi evaluator sau này cũng không tự làm hai handoff hiện tại gọi kernel. Mở production cần task thiết kế/acceptance riêng, không bật qua config, test receipt hoặc hazard validator.

Security tests gọi production findRoute/loader/repository/parser/downloader/AuthRepository thật. Không fake router, không mock verifier trả approved. Synthetic fixture có VERIFIED_COMPATIBLE/READY cố ý để chứng minh các claims đó không đủ.

## 9. Context/Session Invalidation

| Tình huống | Hành vi source / kiểm thử đã viết |
|---|---|
| Fresh Backend false, nested READY | Loader disabled; exported context Denied; route disabled |
| Cùng version đổi readiness | Full revalidation; context mới Denied, held context cũ cũng không route |
| Cùng version đổi digest | Downloader rechecks; fixture digest sai gây Failure, không reuse context |
| Cùng version đổi profile | Parser profile mismatch gây Failure; không reuse context |
| HTTP errors/offline/timeout | Repository status=null; loader error context=null; held references vẫn không credential |
| Logout | Repository status/error/loading reset; loader Idle/context=null; cache files còn để đọc tham khảo |
| ADMIN data rồi USER login | Auth transition invalidate trước lưu phiên mới; USER 403 không nhận state cũ |
| Token hết hạn/refresh thất bại | Restore/refresh hooks reset routing; auth clear tokens theo logic cũ |
| API/download trả muộn sau logout | Generation ngăn republish trạng thái; tests có barriers để tái hiện ordering |
| Repeated load/forceRefresh/error | Mỗi chu kỳ disabled, không trạng thái false-safe |

Không gọi clearCache hoặc xóa user files. Tests dùng thư mục temp riêng và MemoryTokens thuộc fixture; không chạm token/cache tài khoản thật. Auth lifecycle integration với singleton được kiểm tra source; runtime Android process/UI session chưa được chứng minh.

## 10. Backend Contract Compatibility

Backend P5-I01R hiện download-only; Android khóa độc lập và không yêu cầu Backend mở lại readiness để tải. Giữ manifestVersion/contractVersion 2.1.0, download URL, bundle sha/sizes/identity/profile và parser/downloader semantics. Permission projection trong repository không đổi stored artifact hoặc raw JSON; nested manifest vẫn đọc để diagnostics.

Backend status và artifact raw readiness có thể khác có chủ đích. Loader diagnostic CONTRADICTORY_READINESS không được coi là lý do bật strict hoặc sửa graph; vẫn tải/parse dữ liệu khi integrity/identity hợp lệ và trả GraphLoadedButRoutingDisabled. liveHazardReady=false trong repository/loader, không suy từ ATTACHED.

Raw network DTO parser vẫn có thể đọc true từ JSON: đó là transport claim, `canNavigateSafely` luôn false; production repository còn project false. Không thay Backend role policy/JWT hoặc cloud config. Chưa có Android–Backend HTTP/device E2E.

## 11. Security Acceptance Tests

P5A01ConsumerAuditTest có 21 declarations. Chỉ transport bị thay bằng fixture: OkHttp interceptor fixture.invalid trả GZIP thật, RoutingApiClient fixture cung cấp status/errors, auth transport fixture cung cấp token/user responses. Real parser/downloader/repository/loader/context/UCS/auth lifecycle được gọi. Mọi kiểm thử sau **NOT_RUN**, không dùng bảng coverage làm bằng chứng PASS.

| Yêu cầu | Case/nhóm tương ứng |
|---|---|
| 1 raw legacy graph không strict success | legacyRawGraphCannotAuthorizeStrictRouting; cả raw overload hazard |
| 2 READY/count không Permitted | contextCannotClaimPermissionFromMetadataAndMatchingClaimedDigest |
| 3 Backend false/nested READY | coldBackendFalseDeniesLoaderAndExportedContext |
| 4 held context không bypass fresh false | sameVersionRefreshToBackendFalseRevalidatesAndHeldContextStaysDenied |
| 5 same version/digest khác | sameVersionDifferentDigestMustRevalidateBytes |
| 6 same version/profile khác | sameVersionDifferentProfileMustRevalidateParserIdentity |
| 7 same version/readiness khác | sameVersionRefreshToBackendFalseRevalidatesAndHeldContextStaysDenied |
| 8 HTTP500 revoke READY | repositoryClearsCachedStatusAcrossHttpAndOfflineErrors, repeatedLoadRefreshAndErrorsStayDisabled |
| 9 HTTP401/403 | Error matrix có rewarm trước từng code, không chỉ test trên status đã null |
| 10 offline sau READY | Error matrix rewarm trước offline; timeoutAndThrownTransportErrorsClearPriorData |
| 11 logout | realLogoutClearsRoutingMemoryAndPreservesGraphFiles |
| 12 USER sau ADMIN state | userLoginCannotInheritAdminRoutingState; không fake authorization router |
| 13 UNVERIFIED | unverifiedEdgeDoesNotBecomeTraversableMerelyBecauseGraphCountsSayReady; status đổi từ NO_PATH sang DISABLED |
| 14 download không qualification | downloadAndAttachedHazardNeverGrantQualificationOrLiveSafety |
| 15 hazard ATTACHED không live-safe | Cùng case trên, input liveHazardReady=true vẫn bị project false |
| 16 context trước refresh không credential | Same-version, digest, repeated-error, logout cases gọi router trên held context |
| 17 repeated state transitions | repeatedLoadRefreshAndErrorsStayDisabled, loadingAndFailedSnapshotsDoNotRetainNestedContexts |
| 18 JSON/copy/fabricated status | copiedAndJsonDtosCannotMintPermission; real RoutingJsonParser + context/snapshot/result copy |
| Ordering / token expiry bổ sung | invalidationRejectsLateRepositoryResponse, logoutDuringLoadCannotRepublishOldSessionSnapshot, lateDownloadAfterLogoutCannotRepublishContext, expiredTokenRestoreRevokesRoutingSession |

Hai case cachedJsonCanChangeAtSameLengthWhileGzipDigestRemainsValid và failedGzipRefreshDestroysPreviousCompressedCacheBeforeDecodeValidation giữ characterization F04/F05; assertion mô tả lỗi không phải chấp nhận an toàn cache.

Các old positive permission assertions được đổi vì READY/count/verified metadata chưa bao giờ là production credential. Không xóa tests hoặc thêm @Ignore để làm xanh.

## 12. Existing JVM Regression Results

**NOT_RUN, NOT_COMPILED.** Targeted sau sửa và full JVM command đều exit 1 trước Gradle vì Java không có:

```sh
./gradlew --offline --no-daemon :app:testDebugUnitTest --tests 'com.example.bai1.rescue.routing.P5A01ConsumerAuditTest'
./gradlew --offline --no-daemon :app:testDebugUnitTest
```

Số @Test trước → sau (inventory source, không phải runtime counts):

| Suite | Trước → sau |
|---|---|
| P5A01ConsumerAuditTest | 8 → 21 |
| UniformCostSearchRouterTest | 125 → 125 |
| EndToEndRoutingSafetyVerificationTest | 50 → 50 |
| RoutingGraphLoaderTest | 56 → 56 |
| RoutingRepositoryTest | 8 → 8 |
| RoutingJsonParserTest | 8 → 8 |

Giữ nguyên assertions đường đi/cost/DFA/hazard/geometry ở algorithmic suites. Các lời gọi cần kiểm tra tính toán chuyển sang `inspectFixtureComputation` **chỉ trong src/test**: helper assert public strict API thật trả DISABLED (và context không Permitted) trước, sau đó reflection gọi private executeSearch để quan sát phép tính. Không sửa visibility kernel, không thêm production test seam/flag, không trả approval vào production. Các assertions SUCCESS này chỉ là toán học trên fixture; security acceptance suite không dùng helper này. Tên suite E2E lịch sử không biến nó thành device/HTTP E2E.

Static comparison xác nhận phần executeSearch/reconstructResult giữ nguyên nội dung thuật toán (chỉ thêm comment). Main source chỉ còn declaration executeSearch, không còn call site. Helper dùng reflection phụ thuộc tên/signature private, là hạn chế bảo trì test cần kiểm tra khi có JVM. Không coi inventory/static diff là compile/test PASS. Không thể phân loại lỗi compile/runtime tiềm ẩn trước khi toolchain chạy; không khẳng định full regression xanh.

## 13. Android Build Results

```sh
./gradlew --offline --no-daemon :app:assembleDebug
```

Exit 1, cùng lỗi JAVA_HOME/java, không chạy compiler/Android tasks. **ANDROID_BUILD=NOT_RUN.** Không tạo/kiểm chứng APK; không hạ versions hoặc sửa daemon criteria. Sau các chỉnh sửa source cuối, blocker môi trường vẫn nguyên; không giả định code đã được compile.

## 14. Emulator/Device Results

`/home/vantri/Android/Sdk/platform-tools/adb devices -l`: lần sandbox không bind được socket local (Operation not permitted); chạy lại với filesystem/process permission được duyệt cho lệnh inventory cho exit 0, chỉ có header `List of devices attached`, không device/emulator.

**DEVICE_E2E=NOT_RUN.** Không boot emulator, install app/SDK, mở tài khoản production hoặc gửi SOS. Đây là lỗi môi trường/sandbox ở lần đầu, không phải auto-review rejection; không có action bị automatic approval từ chối.

## 15. Artifact Preservation

Kết quả đối chiếu SHA-256 và mtime_ns sau thay đổi: **200/214 Android baseline files nguyên trạng; đúng 14 file cho phép sửa thay đổi (8 production + 6 tests), không xóa file. 677/677 Backend baseline files nguyên trạng, không có Backend file mới.** Hai Android files mới là test inspection helper và báo cáo này.

Giữ nguyên MainActivity.kt/app/build.gradle.kts vốn đã dirty, AndroidManifest, downloader/cache implementation, graph parser/hash/geometry, artifacts, config/toolchain và báo cáo P5-A01 lịch sử. Không reset source có sẵn. Full baseline/comparison JSON ở /tmp phục vụ kiểm tra lại; bảng digest/mmtime quan trọng được lưu cuối báo cáo.

`git diff --check` exit 0. Vì routing đang untracked, kiểm tra bổ sung toàn bộ Kotlin files mới/chỉnh sửa không có trailing whitespace; không chỉ dựa git diff. Static kiểm tra không có return Permitted hay production handoff vào executeSearch. Đây là kiểm tra source/preservation, không runtime safety PASS.

## 16. Remaining F04–F08 Findings

| Finding | Tình trạng |
|---|---|
| F04 JSON cache không ràng buộc hashed GZIP | OPEN; characterization giữ nguyên; không sửa downloader |
| F05 cache update chưa atomic | OPEN; characterization giữ nguyên; không sửa publication/recovery |
| F06 missing hazard thành CLEAR | OPEN; khóa strict ngăn navigation, nhưng parser/hazard semantics chưa sửa |
| F07 graphVersion/path containment | OPEN; không chạy path traversal write/delete exploit |
| F08 HTTPS/TTL/resource/freshness | OPEN; không đổi manifest/network/config hoặc limits |

Hard lock không chữa các lỗi integrity/path/resource/transport này. Download/reference demo cũng cần xử lý các risk liên quan trước nghiệm thu. Không tuyên bố cache hoặc real-world route an toàn chỉ vì strict disabled.

## 17. Final Safety Acceptance

**CONDITIONAL / UNVERIFIED – không PASS – P5-A01R.** Source review chỉ ra F01–F03 được khóa/revoke theo thiết kế; runtime confirmation chưa có. Điều kiện PASS bắt buộc production code-path tests thực sự chạy, full JVM regression PASS và Android build PASS chưa đạt.

Không thấy source path public strict findRoute có thể SUCCESS; context evaluator không trả Permitted; repository/cache/session không còn là nguồn strict permission theo source. Các kết luận đó cần được xác nhận bằng toolchain thật. Không fake verifier/consumer hoặc test count thay thế bằng chứng chạy. Computational helper không phải production authorization.

Không nghiệm thu Android–Backend demo, UI routing, HTTP/device E2E hoặc production qualification. Dừng ở task này.

## 18. Next Task Recommendation

Ưu tiên cung cấp JDK 25 đã cài phù hợp cho daemon; chỉ cấu hình JAVA_HOME/PATH tạm trong shell, xác minh java -version rồi chạy lại targeted → full JVM → assembleDebug offline. Nếu lỗi fixture/compile xuất hiện, phân loại riêng với production regression, sửa trong phạm vi và giữ đầy đủ security assertions. Thiết bị cần được cung cấp trước smoke test cô lập; không tự boot/cài mới theo task này.

Sau nghiệm thu P5-A01R, cần task riêng sửa F04–F08 theo scope được duyệt. P5-P01/P5-I02 và UI/HTTP/device integration vẫn chưa thực hiện. Không mở production qualification, không sửa Backend, không commit/push/deploy.

### Phụ lục SHA-256 / mtime_ns của 14 file được phép sửa

`app/src/main/java/com/example/bai1/auth/AuthRepository.kt`

- Trước: SHA-256 `fd33dfd922bf68473439f60dad432812bcaf9e50dc047df5383fcd40971b738d`, mtime_ns `1789742519285490107`.
- Sau: SHA-256 `16bbc49bb21c6f4eddcb9a7f06c09ed0536844326f9c98c2f9fc470fe2ff65a4`, mtime_ns `1793978137311780577`.

`app/src/main/java/com/example/bai1/rescue/routing/RoutingAppDependencies.kt`

- Trước: SHA-256 `5b5bd09a6856efa4a8da0ad529dbdc08dd462657141efe6feb659aa5a139bf02`, mtime_ns `1793808887735251074`.
- Sau: SHA-256 `9ed8fb33961faa49b92cf5aea79e49f36ec7c7920b8797319ec88955c5b61348`, mtime_ns `1793978701401418674`.

`app/src/main/java/com/example/bai1/rescue/routing/algorithm/UniformCostSearchRouter.kt`

- Trước: SHA-256 `6ee13f403210b8fea4b06ab542395a3de2ee9bcad09f1f0972afe580cf927a2b`, mtime_ns `1793882464288810959`.
- Sau: SHA-256 `0e870be17a190cf83981c45f5c4f60e2a01650d05f74dd25d6d42a4633536522`, mtime_ns `1793978485034284419`.

`app/src/main/java/com/example/bai1/rescue/routing/loader/GraphLoaderModels.kt`

- Trước: SHA-256 `263d01a1707536bddc4e8ae7ba3a92a728fc3b889beb7f69de59f2a5db410573`, mtime_ns `1793876772679930771`.
- Sau: SHA-256 `3f56e7483af16dbb009ce61e212b7923d3945515863b7a98cc62f489d4165329`, mtime_ns `1793978551687710486`.

`app/src/main/java/com/example/bai1/rescue/routing/loader/RoutingGraphLoader.kt`

- Trước: SHA-256 `9b453c7f20bd591579319fd166efd40ede4d5cf8091a0fd28dce5b2bfe862564`, mtime_ns `1793881014661373816`.
- Sau: SHA-256 `b8156450afdf3ba13cfaef515daa2399bfa79a583e0543576da5acfcc9121b46`, mtime_ns `1793978701401090924`.

`app/src/main/java/com/example/bai1/rescue/routing/model/RescueRoutingContext.kt`

- Trước: SHA-256 `bd76cb5a01e83051b6e8b139da866bdf15153be7696587a2bdcfe18f779cd41b`, mtime_ns `1793883610481318438`.
- Sau: SHA-256 `cf4f54134960d204c176192a2550b460cdc1d56d09ccf8d61cd30276b12b00d8`, mtime_ns `1793978701401297450`.

`app/src/main/java/com/example/bai1/rescue/routing/model/RoutingStatusModels.kt`

- Trước: SHA-256 `a66a634b1a644c98f1a912deb976eca409914e84230ab59fc33a603d45a7b11b`, mtime_ns `1793807129174059325`.
- Sau: SHA-256 `128abb5508fa2e2c4e1df72994fc93ef1763d9dd9d9daf9b8584661f51976fc2`, mtime_ns `1793978137309111213`.

`app/src/main/java/com/example/bai1/rescue/routing/repository/RoutingRepository.kt`

- Trước: SHA-256 `cbe07eac7386e84ed345bdcbf05686129153ffcaaeefae6918ee785078c78fe4`, mtime_ns `1793807647817079059`.
- Sau: SHA-256 `12f949144a2f4d662c48bc769a72f9ccbbaf2a461e0cedd6656f6b25028346a9`, mtime_ns `1793978137311285363`.

`app/src/test/java/com/example/bai1/rescue/routing/EndToEndRoutingSafetyVerificationTest.kt`

- Trước: SHA-256 `3d247651cbafbd99cacd7297efebc669dde46b1ea5dbfe9e33c8869a16272bc9`, mtime_ns `1793885264997933568`.
- Sau: SHA-256 `098dc897deeee2ded4ef0bc78339f228180517676096160fea0b045c01c7ef3f`, mtime_ns `1793978485042525921`.

`app/src/test/java/com/example/bai1/rescue/routing/P5A01ConsumerAuditTest.kt`

- Trước: SHA-256 `b9219153dfe25828848247c24616466b76c32237fbdea2663a4b994db690da73`, mtime_ns `1793976860436053835`.
- Sau: SHA-256 `7be5153a51db6fe986d26143a4bc3d670dd5641ea58bdb3879960e6794c3bf9e`, mtime_ns `1793978701401575466`.

`app/src/test/java/com/example/bai1/rescue/routing/RoutingGraphLoaderTest.kt`

- Trước: SHA-256 `16dca8c75d3ff7ab16ab24c0ab3e4d9e9e3c32cbd1fe8885fb2bb14ca83decf7`, mtime_ns `1793882464700714253`.
- Sau: SHA-256 `56cf7e2dc1f33a6942473703b0b339e154d2072a72b90643e450e64dfd38dc5c`, mtime_ns `1793978485036746881`.

`app/src/test/java/com/example/bai1/rescue/routing/RoutingJsonParserTest.kt`

- Trước: SHA-256 `ac7903ec974276b4aad046853bec278329928348ca58c8c36290343e61567b44`, mtime_ns `1793807129313063110`.
- Sau: SHA-256 `038f4cb008a101d30501a06b9b37eac597b4221a77c478cd0a93d391e16bb966`, mtime_ns `1793978485034928969`.

`app/src/test/java/com/example/bai1/rescue/routing/RoutingRepositoryTest.kt`

- Trước: SHA-256 `f5dfe7fec90d3a26bfb2abee1bedf2660f836ff73f13233786bfa6579b1becfb`, mtime_ns `1793807647888079079`.
- Sau: SHA-256 `f5570bfa4f9fde1316b197a9597e277ddc204a241a6bc5b04ab462c23761c241`, mtime_ns `1793978485034669299`.

`app/src/test/java/com/example/bai1/rescue/routing/UniformCostSearchRouterTest.kt`

- Trước: SHA-256 `e216b22754b48aedb7211f2c9cf371858eeb16d4670f069bdc5e3567ff4538d8`, mtime_ns `1793883111741987521`.
- Sau: SHA-256 `1f6a262f847cd678bcef0c277bf259fcfbd6dfeed5c1cb1ed3175070ac7c51b1`, mtime_ns `1793978551687059976`.

