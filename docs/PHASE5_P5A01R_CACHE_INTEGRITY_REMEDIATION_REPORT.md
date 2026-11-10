# PHASE 5 – P5-A01R-CACHE: ANDROID GRAPH CACHE INTEGRITY, ATOMIC PUBLICATION & PATH SECURITY

Ngày: 06/11/2026, Asia/Bangkok.
Android: `/home/vantri/AndroidStudioProjects/bai1`.
Backend READ ONLY: `/home/vantri/AndroidStudioProjects/canh-bao-thien-tai-backend-app`.

## 1. Executive Summary

**PASS – P5-A01R-CACHE cho content integrity, software transaction/reopen recovery và path-security matrix đã thực thi.** F04/F05/F07 được remediation trong Android; strict routing tiếp tục DISABLED khi chưa có production authority. Không nghiệm thu power-loss durability, device E2E hoặc navigation ngoài thực địa.

| Validation thực tế | Tests / suites | Failures / errors / skipped | Exit |
|---|---|---|---|
| Targeted cache/security sau sửa | 61 / 3 | 0 / 0 / 0 | 0 |
| Fresh targeted --rerun-tasks | 61 / 3 | 0 / 0 / 0 | 0 |
| Full routing package | 433 / 12 | 0 / 0 / 0 | 0 |
| Full Android JVM | **474 / 25** | **0 / 0 / 0** | **0** |
| assembleDebug | BUILD SUCCESSFUL; APK tồn tại | — | 0 |
| Device | Không có device/emulator | NOT_RUN | adb inventory 0 |

Thay cache layout thành immutable generations với một atomic current-pointer commit. GZIP/JSON được đối chiếu bằng hash trên bytes thực tế; hash JSON trên chính input stream của production parser. Loader nhận immutable parsed graph thay vì đọc lại mutable file. Mọi generation mới phải qua GZIP/size/hash/schema/version/profile trước publish. Refresh lỗi không xóa generation A hoặc thay pointer A.

217 Android baseline files: 210 giữ nguyên hash/mtime, đúng7 file được phép sửa thay đổi; thêm cache helper, security test và báo cáo này. 677/677 Backend files, bao gồm20 output JSON/GZIP artifacts, giữ nguyên. Không commit/push/deploy, cài dependency, thay cloud config hoặc tự làm task tiếp theo.

## 2. Baseline Repository State

Đã kiểm tra pwd, git status --short, git diff --check và AGENTS.md trong repo/các thư mục áp dụng; không tìm thấy AGENTS.md áp dụng. Đọc ba báo cáo P5-A01/P5-A01R/P5-A01R-V, downloader/models/parser/API/loader/repository và tests/call sites liên quan. Giữ nguyên dirty/untracked source từ các task trước; không reset/restore/clean hoặc xóa user cache.

Baseline SHA-256 + mtime_ns dạng chuỗi: **217 Android và677 Backend files**, ngoài .git/.gradle/build/dist/node_modules/.idea/.kotlin. Snapshot `/tmp/p5cache-android-baseline.json`, `/tmp/p5cache-backend-baseline.json`; source cũ của các files được sửa trong `/tmp/p5cache-original` để review diff. Không coi generated outputs là source preservation proof.

JAVA_HOME/PATH được đặt tạm trong shell/process: `/usr/lib/jvm/java-25-openjdk-amd64`. java/javac xác nhận **25.0.4.1**; `./gradlew --offline --no-daemon --version` xác nhận **Gradle9.7.1**, daemon Java25. Không đổi Gradle/AGP/Kotlin/SDK/daemon criteria hoặc global shell config. Các Gradle commands đều offline.

Baseline targeted `*GraphDownloaderTest` và `*P5A01ConsumerAuditTest` đã BUILD SUCCESSFUL/exit0 trước sửa; log `/tmp/p5cache-baseline.log`. XML baseline không được snapshot riêng trước khi lần sau ghi đè, nên không dùng một XML sau sửa làm bằng chứng baseline. Hai scenarios F04/F05 lúc baseline vẫn là characterization lỗi.

## 3. F04 Root Cause & Design

Trước sửa, downloader hash GZIP nhưng chỉ kiểm tra JSON bằng length. JSON khác bytes, cùng length có thể đi vào parser với digest thuộc GZIP khác.

Thiết kế mới ở GraphDownloader.kt:

1. verifyGzip kiểm tra compressed length và SHA-256 raw file bytes theo manifest.
2. expand giải nén qua DigestInputStream, đồng thời hash compressed bytes thực sự dùng để derive, hash expanded JSON và kiểm tra uncompressed length/budget. Digest compressed stream còn được so lại expected digest, không dựa lần đọc trước.
3. boundJson đọc JSON qua DigestInputStream vào production StreamingRoadGraphParser.parseInputStream(expectedManifest). Sau parse, JSON digest thực tế phải bằng expanded digest của verified GZIP.
4. Parser yêu cầu hết một document JSON; trailing garbage/second object bị từ chối, cùng các schema/version/profile checks sẵn có.
5. GraphDownloadResult.Success có optional parsedGraph: immutable RoadGraph từ chính bound stream. Production loader dùng graph này để không reopen file giữa validation và consumption; vẫn giữ defensive graphVersion/profile checks.

Cache hit, isGraphCached và getCachedGraphFile đều revalidate bytes và parser; không tin filename/mtime/.meta/claimed digest đơn độc. JSON tampered/truncated bị reject; nếu mạng hoạt động thì tạo generation mới từ download đã verify, nếu offline thì Failure. Không tự trả altered JSON. Success chỉ là integrity/schema availability; không cấp qualification hoặc live-road approval.

## 4. F05 Root Cause & Transaction Design

Trước sửa, cached GZIP/JSON bị delete/replace riêng trước khi decode/parser hoàn tất. Digest-matched non-GZIP hoặc JSON schema lỗi có thể phá cache cũ.

Layout mới dưới app cache root:

```text
routing_graphs/
  graph-cache-v2/
    <validated-graphVersion>/
      current                         # ASCII generation name, atomic commit pointer
      g-<UUID>/
        BaseRoadGraph.json.gz
        BaseRoadGraph.json
      .stage-<UUID>/                  # never trusted as active
      .pointer-<UUID>                 # private commit staging, never trusted as active
```

Download và decompress chỉ ghi CREATE_NEW trong stage riêng. Sau toàn bộ validation, stage directory được atomic rename sang generation g-UUID; chưa có reader nào nhận nó từ current. Ghi pointer staging rồi atomic replace current trên cùng filesystem: **đây là điểm commit duy nhất**. Không dùng non-atomic fallback; filesystem không hỗ trợ ATOMIC_MOVE sẽ Failure, không xóa A để thử rename lại.

Refresh không sửa/xóa generation đã publish. Readers giữ A vẫn có nguyên files A sau commit B. Không GC generations trong refresh, nên không trộn GZIP A với JSON B hoặc làm mất file reader đang giữ. Root-wide ReentrantLock dùng chung giữa downloader instances trong cùng process serialize read/refresh/clear; atomic pointer + retained immutable directories bảo vệ publication visibility độc lập với object instance.

## 5. F07 Root Cause & Path Containment

Backend contract hiện dùng `^[A-Za-z0-9][A-Za-z0-9_-]{0,127}$` tại RoutingService. Android dùng đúng grammar đó trong GraphCacheIdentity.requireValid; length1–128, không trim/replace/normalize untrusted identifier thành ID khác. RoutingJsonParser và mọi downloader read/write/clear đều validate trước path operation.

GraphCacheStorage.checked dùng normalized Path containment theo component, canonical-root containment và kiểm tra symlink root/descendant. Constructor storage root là trusted app configuration; ancestry trước root thuộc app/platform configuration. Configured root bị thay đổi hoặc chính nó là symlink sẽ bị từ chối khi thao tác. Generation/pointer names không lấy từ manifest: UUID nội bộ, fixed filenames; pointer đọc tối đa80 bytes và chỉ chấp nhận regex g-UUID.

Cleanup dùng walkFileTree không FOLLOW_LINKS. Symlink con được xóa link khi clear cả namespace; không follow tới target. Targeted clear đối với version path symlink bị reject. Không xây cache paths trong Backend artifact directories. Tất cả adversarial tests chỉ dùng owned temp root và sibling sentinel cũng thuộc cùng owned temp directory, không khai thác write/delete ngoài temp.

Giới hạn: đây là app-private storage, không tuyên bố chống hoàn toàn một tiến trình cùng UID có thể hoán đổi parent directory giữa check và syscall. Không có capability filesystem boundary chống đối thủ đã kiểm soát sandbox/constructor root. Pre-existing symlink và untrusted metadata traversal được block/test; không giả claim rằng canonicalize một lần giải quyết mọi TOCTOU.

## 6. Exact Files Changed

5 production files đã có, đúng scope:

| Path dưới rescue/routing | Thay đổi |
|---|---|
| download/GraphDownloader.kt | Verified stream binding, stage/commit protocol, cancellation/fault handling, cache read validation |
| download/GraphDownloadModels.kt | GRAPH_VALIDATION_FAILED + friendly message; optional immutable parsedGraph trong Success |
| network/RoutingJsonParser.kt | Graph version theo shared validated grammar; không trim identifier |
| loader/RoutingGraphLoader.kt | Consume parsedGraph từ bound stream; giữ phân loại mismatch version/profile khi validation chuyển sang downloader |
| parser/RoadGraphParser.kt | Yêu cầu END_DOCUMENT sau root graph, reject trailing data |

Production file mới: `download/GraphCacheStorage.kt` – managed namespace, ID grammar, path checks, root lock, atomic pointer protocol và safe cleanup.

Tests đã sửa: GraphDownloaderTest.kt, P5A01ConsumerAuditTest.kt.
Test mới: GraphCacheSecurityTest.kt (20 cases).
Báo cáo mới: docs/PHASE5_P5A01R_CACHE_INTEGRITY_REMEDIATION_REPORT.md.

Không sửa Auth/SOS/Firebase, UCS computation/strict public locks, RescueRoutingContext, repository session invalidation, RoutingStatusModels, AndroidManifest/network security, dependencies/versions/signing, Backend/importer/qualification/graph artifacts.

API compatibility: GraphDownloader methods giữ signature; constructor thêm optional I/O checkpoint callback với default no-op; Success thêm parsedGraph default null, nên tests/custom downloader cũ vẫn compile. Loader fallback parser cho legacy/custom downloader vẫn hoạt động. Real production downloader luôn cung cấp bound immutable graph. File basename/layout đổi có chủ đích; callers nhận File, tests được cập nhật để không giả định legacy flat layout.

## 7. GZIP/JSON Content Integrity Chain

```text
expected manifest digest + compressed length
    -> raw GZIP verified
    -> actual compressed stream hash verified during expansion
    -> expanded JSON hash + exact uncompressed length
    -> exact parser input-stream digest compared with expanded digest
    -> schema 2.1.0 + graphVersion + vehicleProfile validation
    -> immutable parsed RoadGraph
    -> atomic generation pointer commit
    -> loader consumes immutable parsed graph
    -> strict routing remains disabled
```

Không dùng .meta làm credential, không hash JSON rồi gắn digest GZIP cho nội dung khác. Mutation sau khi downloader trả File không làm loader parse bytes mới: test wrapper chỉ mutate File sau **real** download, loader thật vẫn dùng immutable parsed graph111, không đọc giá trị999 bị thay; lần cache lookup tiếp theo phát hiện tamper. Wrapper này chỉ mô phỏng lỗi filesystem tại boundary, không fake validation/consumer.

File API còn cho caller giữ path; một caller tự reopen/mutate File sau validation không được cấp bất biến bytes vô thời hạn. Production loader đã tránh boundary này bằng immutable parsedGraph. Arbitrary same-UID mutation ngoài managed code không thể được giải quyết bằng nhãn File read-only trên Android sandbox; không coi returned File là authorization credential.

## 8. Cache Generation/Commit/Recovery Semantics

| Tình huống | Semantics |
|---|---|
| Network/length/hash/GZIP/schema/profile/version fail trước commit | current A nguyên; generation/files A giữ hash và timestamp; cleanup stage operation-owned |
| Stage đã rename thành g-B nhưng pointer chưa commit | B là orphan, không được current reader tự chọn; A nguyên |
| Pointer commit thành công | Readers mới qua current thấy B trọn vẹn; held A còn nguyên |
| Cancellation trước checkpoint commit | ensureActive abort, không publish incomplete generation; stage cleanup hoặc orphan inert |
| Cancellation sau commit linearization | B có thể đã commit đầy đủ; không hứa rollback một generation hợp lệ đã publish |
| Reopen/restart đọc filesystem | Chỉ current hợp lệ mới được xét; stage/pending/orphan không auto-promote; bytes vẫn revalidate |
| Corrupt pointer | Cache miss/Failure nếu không download được; không chọn generation theo tên/mtime |
| Interrupted stage từ run cũ | Inert; không tự xóa stage khác đang có thể thuộc process còn chạy |
| clearCache(version/null) | Chỉ namespace v2; explicit destructive action, callers cần release held readers |

Legacy flat cache không được tin/migrate âm thầm. Nó được để nguyên, không đọc như verified và không xóa tự động. Lần đầu cần download để tạo v2; offline legacy-only cache có thể mất availability tham khảo, nhưng không có false verified/strict fallback. clearCache chỉ quản lý namespace v2; unrelated/legacy files được giữ.

Old generations/orphans còn tới explicit clear; đây là tradeoff bảo vệ held readers và A khi lỗi. Chưa có bounded GC/lease. No fsync/directory durability proof: không hứa current A/B sống sót sau mất điện/kernel/filesystem corruption. Nếu pointer không đọc được, cache fail-closed. Thử nghiệm process-kill/power-cut và Android filesystem thật chưa thực thi; chỉ xác nhận deterministic I/O interruption + reopen protocol trong JVM filesystem hiện tại.

## 9. Filesystem Validation Matrix

| Input/path | Expected và runtime assertion |
|---|---|
| a, dn_central_real_v1, A-9_,128 ASCII chars | Download/cache identity giữ nguyên và PASS |
| ../sentinel, . và .. | Reject download/read/delete; không network/FS escape |
| /absolute | Reject |
| nested/path, nested\\path | Reject |
| empty, leading/trailing space, LF, NUL,129 chars | Reject |
| percent encoded traversal | Reject bởi grammar, không decode thành path |
| JSON metadata graphVersion nguy hiểm | RoutingJsonParser reject trước path construction |
| Symlink version directory | Read/write reject; targeted clear reject; namespace clear chỉ xóa link |
| Symlink generation JSON file | Cache reject; namespace clear không xóa target sentinel |
| Symlink configured root | Preparation Failure, không write qua link |
| forged .meta / corrupt current pointer | Không tạo false verified result |
| explicit cleanup | Sentinel ngoài cache root và unrelated/legacy file trong root giữ nguyên |

Path names fixed/UUID nội bộ cũng đi qua checked/child; read, temp, pointer, generation move và cleanup không có lối ghép graphVersion raw khác. Không test exploit trên cache thật của người dùng hoặc Backend directories.

## 10. Failure/Fault Injection Tests

GraphCacheSecurityTest chạy production OkHttpGraphDownloader/StreamingRoadGraphParser/loader/repository/UCS. Transport là OkHttp fixture interceptors trả GZIP thật, không network. CacheWritePoint hook chỉ quan sát/throw I/O/cancellation, không thay validation result, không cấp authority.

| Required matrix cases | Case/nhóm đã chạy |
|---|---|
| 1 valid GZIP/JSON, download-only | validCacheAndLoaderRemainDownloadOnly |
| 2 same-length JSON / 3 truncated JSON | sameLengthAndTruncatedJsonCannotBecomeVerifiedOffline + converted P5A01 F04 |
| 4 changed GZIP / 5 truncated GZIP | changedAndTruncatedGzipCannotBecomeVerifiedOffline; truncated network case |
| 6 compressed length / 7 uncompressed length | failedRefreshAtEveryValidationStepPreservesGenerationA; existing downloader mismatch tests |
| 8 version/profile mismatch | Same validation-step matrix; P5A01 same-version profile/digest acceptance vẫn PASS |
| 9 corrupt metadata | corruptPointerAndForgedMetaCannotCreateVerifiedEntry |
| 10 A→B fail / 11 decode fail / 12 schema fail | Validation-step matrix, converted F05, trailingJsonAndDigestMatchedMalformedJsonFailBeforeCommit |
| 13 interrupted write | injectedWriteFailuresAtEveryCheckpointPreserveAAndReopen; midStreamIOExceptionDoesNotPublishPartialDownload |
| 14 successful commit B | successfulCommitKeepsHeldGenerationAndPublishesConsistentB |
| 15 concurrent read/refresh | concurrentReadWaitsForCommitWithoutMixingGenerations; concurrentRefreshesReturnTheirOwnImmutableGenerations |
| 16 coroutine cancellation | cancellationBeforePointerCommitPreservesA, cancellation after generation move/before pointer |
| 17 reopen interrupted update | reopenedCacheIgnoresUncommittedStagingAndOrphans; I/O-fault loop creates new downloader instance |
| 18 valid ID / 19 traversal / 20 absolute / 21 separator / 22 malformed ID | validIdentifiersIncludingMaximumLengthRemainUnchanged; invalidIdentifiersRejectEveryEntryPointBeforeFilesystemOperations |
| 23 cleanup sentinel | clearCachePreservesUnmanagedFilesAndOutsideSentinel và symlink tests |
| 24 cache không strict / 25 Backend false | validCacheAndLoaderRemainDownloadOnly + P5A01ConsumerAuditTest |
| 26 error/offline không READY / 27 logout | P5A01 error/offline/session/logout/late-response tests giữ nguyên scenarios, đều runtime PASS |

Fault checkpoints AFTER_DOWNLOAD, AFTER_DECOMPRESSION, BEFORE_GENERATION_MOVE, BEFORE_POINTER_COMMIT đều được test với IOException. AFTER_DOWNLOAD còn làm stage thành partial bytes trước throw; actual stream test ghi10 bytes rồi read IOException. Không lấp đầy disk thật; ENOSPC là deterministic fault simulation. Assertions snapshot SHA/mtime cho A+current phải nguyên qua mọi failure. No @Ignore, no fake router/downloader chấp nhận giả.

## 11. Characterization-to-Acceptance Conversion

- cachedJsonCanChangeAtSameLengthWhileGzipDigestRemainsValid → **cachedJsonTamperingAtSameLengthIsRejectedAndReplaced**. Vẫn tải bằng production downloader rồi thay111→999 cùng length. Giờ isGraphCached=false; redownload tạo generation sạch, fromCache=false, parsedGraph length111. Không chỉ đảo assertion mà bỏ thực thi download.
- failedGzipRefreshDestroysPreviousCompressedCacheBeforeDecodeValidation → **failedGzipRefreshPreservesPreviousGeneration**. Vẫn forced refresh digest-matched non-GZIP; Failure nhưng JSON/GZIP A còn nguyên, cacheA hợp lệ và returned cached file vẫn A.

GraphDownloaderTest giữ20 cases, sửa success fixtures thành graph2.1.0 hợp lệ thay text/arbitrary JSON; validate-before-commit không được bypass để giữ fixture cũ. Đổi path assertions theo generation layout, giữ hash/length/GZIP/HTTP/JWT/no-partial scenarios. New security suite bổ sung20 cases, không xóa old scenarios.

Lần đầu sau refactor:41 tests,1 failure (P5A01 sameVersionDifferentProfileMustRevalidateParserIdentity). Root cause: parser phát hiện profile mismatch trước commit trong downloader, nhưng loader map thành DOWNLOAD_FAILED. Đã giữ error.cause và map GRAPH_VALIDATION_FAILED sang VEHICLE_PROFILE_MISMATCH/VERSION_MISMATCH/PARSE_FAILED. Không giảm assertion hoặc nới parser. Các lần sau61/61 PASS. Initial failure XML ở `/tmp/p5cache-first-results`, log `/tmp/p5cache-first.log`.

## 12. Routing Security Regression

Fresh command sau mọi thay đổi:

```sh
./gradlew --offline --no-daemon :app:testDebugUnitTest --rerun-tasks \
  --tests '*GraphCacheSecurityTest' --tests '*GraphDownloaderTest' \
  --tests '*P5A01ConsumerAuditTest'
```

**61 tests / 3 suites PASS, failures=errors=skipped=0, exit0**;27 tasks thực thi, không nhận cached test result làm fresh evidence. XML riêng `/tmp/p5cache-fresh-results`.

Sau đó:

```sh
./gradlew --offline --no-daemon :app:testDebugUnitTest \
  --tests 'com.example.bai1.rescue.routing.*'
```

**433 tests /12 suites PASS, failures=errors=skipped=0, exit0**. P5A01ConsumerAuditTest vẫn21 tests; F04/F05 bây giờ là acceptance, không còn characterization chấp nhận lỗi.

Strict UCS public lock, RescueRoutingContext denial và repository/auth session invalidation giữ hash/mtime baseline. Loader chỉ thay phần data consumption/error mapping; backend readiness false và held-context/error/logout invariants đều có runtime tests PASS. Không có verifier/fake approval mới. Hash/schema success không là production qualification.

Warning StateFlow inheritance tại RoutingGraphLoader.kt và các UI/SOS test warnings có trước vẫn được compiler ghi; không refactor warning ngoài scope. New test nullable-File warnings trong lần phát triển đã được sửa bằng explicit non-null owned parent, không còn trong final fresh run.

## 13. Full Android JVM Results

```sh
./gradlew --offline --no-daemon :app:testDebugUnitTest
```

**474 tests /25 suites, failures=0, errors=0, skipped=0; BUILD SUCCESSFUL, exit0.** Counts lấy từ JUnit XML sau đúng full invocation; tăng20 từ baseline454 do new GraphCacheSecurityTest. Không cộng overlapping fresh/routing/full counts thành số tests độc lập.

Evidence: `/tmp/p5cache-validation-results.json`, XML snapshots `/tmp/p5cache-fresh-results`, `/tmp/p5cache-routing-results`, `/tmp/p5cache-full-results`; logs `/tmp/p5cache-fresh.log`, `-routing.log`, `-full.log`, `-apk.log`. Current HTML `app/build/reports/tests/testDebugUnitTest/index.html`, XML `app/build/test-results/testDebugUnitTest/` là full474 result. Intermediate targeted61/61 trước fresh ở `/tmp/p5cache-targeted-results` và `/tmp/p5cache-targeted-final.log`.

## 14. Android APK Build Results

```sh
./gradlew --offline --no-daemon :app:assembleDebug
```

**BUILD SUCCESSFUL, exit0.** APK `/home/vantri/AndroidStudioProjects/bai1/app/build/outputs/apk/debug/app-debug.apk`.

- Size95,665,573 bytes.
- SHA-256 `80fb40d2bd652bd5470c119e9a6c5fadb98ed4d126e664e2164252e623922882`.
- output-metadata.json: applicationId com.example.bai1, variant debug, versionCode1/versionName1.0.

Không thay build configuration, package ID, signing, Firebase/Maps/SOS configuration hoặc install/deploy app. APK là generated build output, không phải production graph artifact.

## 15. Emulator/Device Status

`/home/vantri/Android/Sdk/platform-tools/adb devices -l` exit0, chỉ header List of devices attached, không device/emulator.

**DEVICE_E2E=NOT_RUN.** Không boot emulator, cài SDK hoặc app, chạy tài khoản production hoặc gửi SOS thật. JVM filesystem/atomic move semantics đã chạy trên Linux hiện tại; chưa chứng minh filesystem/device Android cụ thể.

## 16. Backend/Graph Artifact Preservation

So sánh cuối SHA-256/mtime_ns: **210/217 Android baseline files giữ nguyên; đúng7 edited files thuộc scope; không xóa file.** Ba files mới là cache helper, new cache tests, báo cáo này. **677/677 Backend files giữ nguyên**, bao gồm20 output JSON/GZIP graph/manifest artifacts; không Backend file mới.

Auth/SOS/Firebase P5-A01R-V, UCS computation/strict context, routing repository/session lifecycle, AndroidManifest/network config/build dependencies, production artifacts và ba báo cáo cũ đều nguyên baseline. Tests chỉ ghi/xóa owned temporary directories; không dùng backend artifacts làm writable output hoặc dọn cache thật. Legacy flat app cache không bị tự xóa/migrate.

`git diff --check` exit0. Hash/mtime check bao gồm untracked files, không chỉ git diff. Snapshot/comparison ở `/tmp/p5cache-*-baseline.json`, `/tmp/p5cache-*-comparison.json`; phụ lục giữ digest/timestamp trước/sau của edited source/tests. Generated build/.gradle outputs bị loại khỏi preservation scope có chủ đích.

## 17. Remaining F06/F08 and Technical Limitations

- **F06 OPEN:** parser vẫn có default CLEAR khi thiếu hazard. Chỉ thêm EOF/schema completion, không sửa hazard policy. Strict hard lock vẫn ngăn rescue approval.
- **F08 OPEN:** HTTP/HTTPS policy, TTL/freshness, independent resource maxima chưa được remediation. Giữ decompression budget hiện có; chỉ dùng saturating arithmetic để tránh overflow trong phép tính budget. Budget vẫn manifest-dependent, không claim hardened resource limits.
- Không power-loss durability proof hoặc fsync transaction. Không actual kill/power-cut/device filesystem testing; deterministic I/O failure/cancellation/reopen đã thực thi.
- App-private trusted-root/cooperative writer threat model; canonical/symlink checks không là bảo đảm trước malicious same-UID path-swap race. No-follow cleanup bảo vệ pre-existing links theo tests.
- Immutable generations/orphans giữ tới explicit clear, chưa bounded GC. Legacy layout không trusted/migrated tự động, offline legacy-only availability có thể giảm.
- Root lock serialize I/O/parser/cache lookup; kiểm tra synchronous cache hiện tốn thêm decompress/hash/parse. Chưa benchmark UI latency hoặc cross-process hostile clear; explicit clear cần quiescent held readers.
- Blocking OkHttp read có thể chỉ thấy cancellation sau read trả về/timeout; ensureActive trên loops và checkpoints chặn incomplete publication, không hứa instant network abort.

Các giới hạn trên không biến cache success thành authorization. Không tự thực hiện F06/F08, P5-P01/P5-I02 hoặc routing UI.

## 18. Final Acceptance: PASS / CONDITIONAL / FAIL

**PASS – P5-A01R-CACHE cho phạm vi kiểm thử đã nêu.** F04 có cryptographic binding trên GZIP/expanded/parser streams; F05 có validate-before-commit và bảo toàn A khi software refresh thất bại; F07 có contract ID validation, canonical containment, symlink/no-follow cleanup matrix. Production paths và security/routing/full/build đều PASS, không mở strict routing hoặc sửa Backend/artifacts ngoài scope.

PASS này không mở rộng thành power-loss durability, adversarial same-UID filesystem capability security, Android device E2E hoặc production rescue qualification. Những trường hợp chưa kiểm thử được nêu ở17; không có runtime failing test, skipped test hoặc build blocker còn tồn tại trong scope JVM/build của task. Nếu acceptance môi trường triển khai yêu cầu actual power-cut/device/filesystem hoặc hostile same-UID process, phần assurance đó vẫn UNVERIFIED và cần test/task riêng trước khi sử dụng trong môi trường đó.

## 19. Recommended Next Task

Đề xuất scope riêng F08 network/freshness/resource controls và F06 hazard semantics trước field-use. Có thể lập task vận hành cache GC/leases và device filesystem/crash-durability validation nếu cần; giữ nguyên hard lock, không lấy hash làm qualification credential.

Dừng tại P5-A01R-CACHE. Không tự làm P5-P01/P5-I02/UI/production qualification, không commit/push/deploy.

### Phụ lục – SHA-256 / mtime_ns của7 edited files

`app/src/main/java/com/example/bai1/rescue/routing/download/GraphDownloadModels.kt`

- Trước: `d56420d29177f05c0fa92540db8e0744ec61703a193b051124ed23ab19a76514`, mtime_ns `1793879338793458211`.
- Sau: `e17782c78e3bc13baefb4bfc9c9b4f0184febf116cc3669473c71cee0c067b54`, mtime_ns `1793981822497654244`.

`app/src/main/java/com/example/bai1/rescue/routing/download/GraphDownloader.kt`

- Trước: `91e44b9cb34611c607dacc899a55bcadb0e4a3a6315e6e50a09f7ad41574ac1d`, mtime_ns `1793879338855458696`.
- Sau: `844cb7f0c11cb01bb3afba38725b8d18fcab2656e81a6f069aae2086ae3184c7`, mtime_ns `1793981989395799116`.

`app/src/main/java/com/example/bai1/rescue/routing/loader/RoutingGraphLoader.kt`

- Trước: `b8156450afdf3ba13cfaef515daa2399bfa79a583e0543576da5acfcc9121b46`, mtime_ns `1793978701401090924`.
- Sau: `ae316f692cb7f48f8e5cbdaec43557b0db3242e17722ae2a158112a4ee416f63`, mtime_ns `1793981989395629775`.

`app/src/main/java/com/example/bai1/rescue/routing/network/RoutingJsonParser.kt`

- Trước: `4e6982c0d5454d05e7b9de30e026de4e554e39a36d2dfb399d1efde640ad9219`, mtime_ns `1793807129244061231`.
- Sau: `3ee6040370db7f5148b09c1d24cda5b0e67591c901f1fe03e95e4682953b29f2`, mtime_ns `1793981759101286196`.

`app/src/main/java/com/example/bai1/rescue/routing/parser/RoadGraphParser.kt`

- Trước: `30e727d75e5de5f324588c73f4918e2ac0111cf100c9fdebf2001f2bcc6206f2`, mtime_ns `1793879339211825725`.
- Sau: `75c1be9e87e258c93d4c5462b5a8d2c65eb0cc39a85cf4d6b682b22fc5da9ecd`, mtime_ns `1793982271415449384`.

`app/src/test/java/com/example/bai1/rescue/routing/GraphDownloaderTest.kt`

- Trước: `11aea4d94cbaaed1953ad42e65cbb435c2c5f75c5be074234e52d00c6b82f1d9`, mtime_ns `1793878990815958740`.
- Sau: `f5fd25699e2c2ed562cb11f4c0029e0bcfa293532fafc953f6c2f1b1b701efa3`, mtime_ns `1793982271415904829`.

`app/src/test/java/com/example/bai1/rescue/routing/P5A01ConsumerAuditTest.kt`

- Trước: `7be5153a51db6fe986d26143a4bc3d670dd5641ea58bdb3879960e6794c3bf9e`, mtime_ns `1793978701401575466`.
- Sau: `743adb5533708b5248f03d76466de30b56c0c4b8bb2139f56360b6aa242393eb`, mtime_ns `1793981899957826319`.

### Phụ lục – SHA-256 / mtime_ns new implementation/tests

`app/src/main/java/com/example/bai1/rescue/routing/download/GraphCacheStorage.kt`

- SHA-256 `ad3922164082a555b5a14895027e1b9a3f89919a9783b64bf39b21b298d596c5`, mtime_ns `1793981759100866010`.

`app/src/test/java/com/example/bai1/rescue/routing/GraphCacheSecurityTest.kt`

- SHA-256 `f56b500fe709028bd33351007c3fda211e174cba32b5a32e3972e57e26509f3a`, mtime_ns `1793982271415696662`.

