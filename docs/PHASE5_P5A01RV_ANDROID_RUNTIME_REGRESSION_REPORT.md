# PHASE 5 – P5-A01R-V: ANDROID FULL REGRESSION REMEDIATION & SECURITY ACCEPTANCE

Ngày thực hiện: 06/11/2026, Asia/Bangkok.
Android: `/home/vantri/AndroidStudioProjects/bai1`.
Backend chỉ đọc: `/home/vantri/AndroidStudioProjects/canh-bao-thien-tai-backend-app`.

## 1. Executive Summary

**PASS – P5-A01R-V trong phạm vi Android JVM regression, routing security acceptance và debug APK build.** Đã tái hiện đủ 7 failures; sau sửa, cả 7 tên test gốc vẫn hiện diện và PASS trong full JUnit XML. Không xóa/ignore tests, không mở strict routing.

| Kiểm tra thực thi trong task | Kết quả |
|---|---|
| Tái hiện bốn nhóm lỗi trước sửa | 10 tests, 7 failures, 0 errors, 0 skipped; exit 1 |
| Bốn nhóm lỗi sau sửa | 13 tests, 0 failures/errors/skipped; exit 0 |
| P5A01ConsumerAuditTest, fresh --rerun-tasks | 21 tests, 0 failures/errors/skipped; exit 0 |
| Toàn bộ routing package | 413 tests / 11 suites, 0 failures/errors/skipped; exit 0 |
| Full Android JVM | **454 tests / 24 suites, 0 failures/errors/skipped; exit 0** |
| assembleDebug | BUILD SUCCESSFUL, exit 0; APK tồn tại |
| Device E2E | NOT_RUN; ADB không có thiết bị |

Thay đổi 4 production files Auth/SOS và 3 test files tương ứng, thêm báo cáo này. Source/tests routing, auth routing-invalidation, Backend và graph artifacts giữ nguyên SHA-256/mtime_ns. Không phát triển tính năng mới; không truy cập Firebase production hoặc gửi SOS thật. F04–F08 vẫn OPEN; kết quả này không nghiệm thu demo Android–Backend/UI/device hoặc real-world rescue navigation.

## 2. Java/JDK and Gradle Environment

Đặt JAVA_HOME/PATH tạm trong các shell/process chạy Gradle, không sửa cấu hình shell toàn cục:

```sh
export JAVA_HOME=/usr/lib/jvm/java-25-openjdk-amd64
export PATH="$JAVA_HOME/bin:$PATH"
java -version
javac -version
./gradlew --offline --no-daemon --version
```

Runtime xác nhận OpenJDK **25.0.4.1**, javac **25.0.4.1**, Gradle **9.7.1**, launcher/daemon dùng Java 25 theo gradle-daemon-jvm.properties. Gradle distribution báo embedded Kotlin 2.4.0; không đồng nhất con số này với project Kotlin plugin và không sửa project versions. Không cài/download dependency, JDK hoặc SDK; mọi Gradle command dùng --offline.

Lần thử Gradle trong sandbox chỉ đọc cache gặp FileNotFoundException cho wrapper `.zip.lck` (Read-only file system). Chạy lại với quyền cần thiết cho Android repository/cache đã được duyệt, version/tests/build chạy bình thường. Không có automatic approval rejection hoặc blocker môi trường còn lại.

## 3. Repository Baseline

Đã đọc hai báo cáo P5-A01/P5-A01R, HTML index và JUnit XML hiện có, rồi đọc production implementation/test fixtures của bốn nhóm lỗi. Báo cáo có trước được giữ làm lịch sử; trạng thái NOT_RUN trong P5-A01R cũ được thay thế bằng bằng chứng runtime của báo cáo này, không viết lại lịch sử.

Đã chạy pwd, git status --short, git diff --check và kiểm tra AGENTS.md trong repo/cây source/các thư mục cha; không tìm thấy hướng dẫn áp dụng. Trạng thái đầu task có app/build.gradle.kts, MainActivity.kt, AuthRepository.kt modified và routing/docs untracked. Không restore/reset/clean/ghi đè các thay đổi có sẵn.

Baseline chụp **216 Android files và 677 Backend files**, SHA-256 cùng mtime_ns dạng chuỗi, ngoài .git/.gradle/build/dist/node_modules/.idea/.kotlin. Snapshot ở `/tmp/p5a01rv-android-baseline.json` và `/tmp/p5a01rv-backend-baseline.json`. Generated build outputs không thuộc proof source preservation. Không commit/push/deploy, không dùng Antigravity hoặc subagents.

## 4. Original 7 Failures Reproduction

Đã chạy trực tiếp trước sửa:

```sh
./gradlew --offline --no-daemon :app:testDebugUnitTest \
  --tests '*AuthApiClientTest' --tests '*SosRepositoryTest' \
  --tests '*FirebaseSosEventMapperTest' --tests '*FirebaseSosRepositoryTest'
```

Thực thi test task, exit 1: **10 tests completed, 7 failed**. User cung cấp lịch sử full 451/7; task này tái hiện bằng bốn nhóm lỗi, không nhận lịch sử đó làm lần chạy full mới của mình. JUnit XML trước sửa được copy sang `/tmp/p5a01rv-baseline-results` trước các lần chạy tiếp theo.

| Failure gốc | Expected / actual từ runtime | Root stack frame |
|---|---|---|
| AuthApiClientTest – 401 is mapped without exposing backend payload | HTTP 401 / secret backend detail; ComparisonFailure | AuthApiClientTest.kt:60, coroutine invokeSuspend |
| SosRepositoryTest – test expired events older than 3 days are filtered out | Chưa đến expiry assertion; RuntimeException Method d in android.util.Log not mocked | InMemorySosRepository.kt:115 → SosRepositoryTest.kt:85 |
| FirebaseSosEventMapperTest – maps terminal statuses to removal | Removed(event-resolved) / null | FirebaseSosEventMapperTest.kt:43 |
| FirebaseSosEventMapperTest – rejects missing ids and invalid coordinates | terminal parse phải là Removed / assertTrue thất bại; các assert invalid-coordinate trước đó không phải điểm fail | FirebaseSosEventMapperTest.kt:58 |
| FirebaseSosEventMapperTest – maps active projection using server event id and rescue fields | SUBMITTED / ACTIVE | FirebaseSosEventMapperTest.kt:38 |
| FirebaseSosRepositoryTest – filters out events older than 3 days | Latest size 1 / 0; suppressed RuntimeException Log.d not mocked | FirebaseSosRepositoryTest.kt:85; suppressed FirebaseSosRepository.kt:83 |
| FirebaseSosRepositoryTest – deduplicates by event id and removes terminal events | 4 snapshots / 1; suppressed RuntimeException Log.d not mocked | FirebaseSosRepositoryTest.kt:48; suppressed FirebaseSosRepository.kt:83 |

Đây là line của source baseline. Không kết luận date/time/Firebase SDK race từ tên test. Stack traces Firebase cho thấy collector bị ngắt bởi logging bên trong MutableStateFlow.update trước publication.

## 5. Root Cause of Each Failure

1. **Auth 401 – lỗi production error sanitization.** executeJson parse tùy ý error.message/message của non-2xx vào technicalReason, kể cả 401; vi phạm contract giữ HTTP 401 và không lưu payload nhạy cảm.
2. **In-memory expiry – phụ thuộc Android logging trong JVM, cộng fixture assumption cần sửa.** getActiveSos gọi Log.d không được mock. Đồng thời FakeInternetSosTransport trả Success không serverSos, timestamp mặc định hôm nay; repository dùng acknowledgement mới làm createdAt theo nhánh fallback vốn có. Test muốn kiểm tra sự kiện cũ nhưng đã dựng một acknowledgement mới. Không sửa chính sách server timestamp để làm test xanh; sửa fixture cung cấp serverSos với createdAt cũ, tương tự production API transport có full server snapshot.
3. **Terminal mapper – lỗi production.** parse kiểm tra tọa độ trước khi xử lý terminal. Terminal projection có thể chỉ có ID/status nên bị trả null, để marker cũ tồn tại.
4. **Invalid coordinates test – cùng lỗi terminal mapper.** Assertion cuối về terminal mới là điểm fail; không phải lỗi WGS84 validation. Giữ các assertions invalid coordinates.
5. **ACTIVE mapper expectation – test assumption cũ.** Domain đã có SosStatus.ACTIVE; REST SosEventResponse.toDomain dùng valueOf(status), Backend SOS dùng ACTIVE. Realtime producer đã map ACTIVE đúng; SUBMITTED là trạng thái local sau submission. Sửa expectation sang ACTIVE, giữ các assertions server ID/people/items/location/urgency, không ép production quay về enum cũ.
6. **Firebase expiry – lỗi phụ thuộc logging.** Log.d trong update lambda ném RuntimeException, nên event mới không được publish. Không có bằng chứng expiry duration sai.
7. **Firebase dedup – cùng lỗi logging trước publication.** Collector ngừng nhận updates; không phải mất sự kiện do SharedFlow capacity hoặc dedup ordering. Giữ assertions đầy đủ empty → first → changed → removed.

Bằng chứng sau sửa: 13/13 related cases PASS, bao gồm toàn bộ 7 tên failures cũ; không đổi TTL, clock hoặc order/dedup algorithm production.

## 6. Exact Files Changed

| File dưới app/src | Sửa tối thiểu |
|---|---|
| main/java/com/example/bai1/auth/network/AuthApiClient.kt | Không lấy message từ payload HTTP401; giữ HTTP401 diagnostics/error kind |
| main/java/com/example/bai1/sos/realtime/FirebaseSosEventMapper.kt | Trả Removed cho RESOLVED/FALSE_ALARM trước tọa độ validation |
| main/java/com/example/bai1/sos/repository/InMemorySosRepository.kt | Bỏ debug Log.d và import Android Log; giữ các phép tính/filter/network behavior |
| main/java/com/example/bai1/sos/repository/FirebaseSosRepository.kt | Bỏ debug Log.d khỏi data-flow/update/merge; không đổi dedup/expiry/removal |
| test/java/com/example/bai1/auth/network/AuthApiClientTest.kt | Giữ 2 tests cũ; thêm case nested/top-level/malformed 401 payload |
| test/java/com/example/bai1/sos/SosRepositoryTest.kt | Fixture server snapshot giữ thời điểm sự kiện; thêm boundary exactly72h/+1ms |
| test/java/com/example/bai1/sos/realtime/FirebaseSosEventMapperTest.kt | ACTIVE expectation theo contract; thêm terminal invalid-coordinates/blank-ID và non-finite coordinates |

File mới duy nhất: `docs/PHASE5_P5A01RV_ANDROID_RUNTIME_REGRESSION_REPORT.md`.

Không sửa FirebaseSosRepositoryTest hoặc fixtures khác để che mất snapshots. Không thêm mock-framework/dependency, Android framework stub hoặc returnDefaultValues. Không sửa AuthRepository, token storage, routing singleton, loader/context/UCS, build scripts/signing/applicationId/cloud rules.

## 7. Auth 401 Remediation

AuthApiClient.kt:195–210: non-success khởi tạo serverMsg=HTTP code; nhánh parse backend message chỉ áp dụng response.code != 401. Với 401, technicalReason luôn HTTP 401, kind UNAUTHORIZED, userMessage giữ thông báo hiện hành. Điều này còn chặn arbitrary text như “already in use” trong 401 dẫn đến thông báo user sai do heuristic của AuthApiError.

Tests gọi production OkHttpAuthApiClient với interceptor nội bộ; không gửi request ra mạng. Giữ test gốc secret backend detail; thêm nested error.message, top-level message và malformed body, tất cả phải giữ diagnostic và user-facing message cố định. Không đổi JWT/login/refresh/network policy hoặc routing invalidation.

## 8. SOS Expiry and Mapping Remediation

InMemorySosRepository chỉ bỏ diagnostic Android logging. removeExpiredSosEvents, update/submission logic và TTL MAX_SOS_AGE_MS vẫn nguyên. Test expiry nhận serverSos fixture có original createdAt; assertions getActiveSos và observeSos đều vẫn yêu cầu chỉ fresh-1 tồn tại. Không sửa fake transport dùng chung hoặc production acknowledgement semantics.

Boundary regression dùng now cố định 1_800_000_000_000L và production SosRescueInfo.isExpiredAt: đúng72 giờ chưa expired, +1ms expired, event hiện tại chưa expired. Không phụ thuộc timezone hoặc ngày chạy; TTL production vẫn >72h như contract.

Firebase mapper terminal removal chỉ cần ID hợp lệ và terminal status; active projection vẫn cần tọa độ hợp lệ. Test mới bảo vệ terminal cleanup dù tọa độ NaN/out-of-range và từ chối blank ID; active NaN/±Infinity vẫn bị từ chối. Không thêm status normalization/policy mới, không đổi timestamp parser hoặc default statuses. REST/domain/Realtime đều giữ ACTIVE.

## 9. Firebase SOS Repository Remediation

Bỏ 5 debug Log.d trong getActiveSos/observeSos/update/merge để logging không thể ngắt tính toán/publish state trong pure JVM. Không nuốt exception collector hoặc thay đổi MutableStateFlow/SharedFlow scheduling để làm test xanh.

Hai tests Firebase repository giữ nguyên source; chạy lại PASS với đầy đủ snapshot/removal/dedup/expiry assertions. Realtime data source trong test là Flow fixture, local repository là EmptySosRepository fixture; không khởi tạo Firebase SDK production, không network write hoặc thay đổi SOS thật.

## 10. P5-A01R Security Acceptance

Fresh command có --rerun-tasks, test task và compile thực sự thực thi:

```sh
./gradlew --offline --no-daemon :app:testDebugUnitTest --rerun-tasks \
  --tests 'com.example.bai1.rescue.routing.P5A01ConsumerAuditTest'
```

**21/21 PASS, 0 errors, 0 skipped.** Trong đó 19 security acceptance và 2 F04/F05 characterization. Security suite gọi production consumers trực tiếp, không dùng computational-inspection helper để cấp quyền. Xác nhận:

| Bất biến yêu cầu | Bằng chứng cases đã chạy PASS |
|---|---|
| Raw legacy không strict SUCCESS | legacyRawGraphCannotAuthorizeStrictRouting; raw overload hazard cũng bị khóa |
| READY/count/digest không Permitted | contextCannotClaimPermissionFromMetadataAndMatchingClaimedDigest, copiedAndJsonDtosCannotMintPermission |
| Backend false và old context không bypass | coldBackendFalseDeniesLoaderAndExportedContext, sameVersionRefreshToBackendFalseRevalidatesAndHeldContextStaysDenied |
| Same version revalidate digest/profile/readiness | Same-version digest/profile cases và Backend false/readiness case |
| HTTP401/403/404/500/503/offline/timeout revoke | repositoryClearsCachedStatusAcrossHttpAndOfflineErrors, timeoutAndThrownTransportErrorsClearPriorData |
| Logout / session transitions / token expiry | realLogoutClearsRoutingMemoryAndPreservesGraphFiles, userLoginCannotInheritAdminRoutingState, expiredTokenRestoreRevokesRoutingSession |
| Late response/download không republish | invalidationRejectsLateRepositoryResponse, logoutDuringLoadCannotRepublishOldSessionSnapshot, lateDownloadAfterLogoutCannotRepublishContext |
| Repeated cycles fail-closed | repeatedLoadRefreshAndErrorsStayDisabled, loadingAndFailedSnapshotsDoNotRetainNestedContexts |
| Download/hazard không approval | downloadAndAttachedHazardNeverGrantQualificationOrLiveSafety; UNVERIFIED case giữ disabled |

Source/tests routing và AuthRepository invalidation giữ nguyên hash/mtime_ns so baseline. Không tích hợp verifier mới, không test approval Boolean/receipt, không mở public UCS entry points. Private mathematical kernel tests còn trong routing suite và không chứng minh production authority.

## 11. Routing Test Results

```sh
./gradlew --offline --no-daemon :app:testDebugUnitTest \
  --tests 'com.example.bai1.rescue.routing.*'
```

**413 tests / 11 suites PASS; failures=errors=skipped=0.** Gradle exit0/BUILD SUCCESSFUL. XML được lưu riêng `/tmp/p5a01rv-routing-results`, để không bị full run ghi đè. Security XML riêng `/tmp/p5a01rv-security-results`. Không cộng các số overlapping thành tổng test độc lập.

Cảnh báo compile đã tái hiện khi fresh build: RoutingGraphLoader.kt:76:5, DerivedStateFlow implements StateFlow – “Inheriting from this kotlinx.coroutines API is unstable”, yêu cầu opt-in vì inherited contract có thể thay đổi. Đây là warning compatibility/API inheritance, không compile failure hoặc permission grant. Các tests loader/concurrency/session và full build hiện PASS; không suy đã kiểm chứng mọi runtime edge case. Giữ loader nguyên trạng theo scope; đánh giá StateFlow wrapper/opt-in ở task riêng khi cập nhật coroutines. Các cảnh báo UI deprecation/always-false condition cũng được ghi trong log, không sửa UI ngoài phạm vi.

## 12. Full JVM Regression Results

```sh
./gradlew --offline --no-daemon :app:testDebugUnitTest
```

**454 tests / 24 suites, failures=0, errors=0, skipped=0. BUILD SUCCESSFUL in 12s, exit0.** Số thực tế lấy từ sum attributes tests/failures/errors/skipped của JUnit XML sau đúng full run, không dự đoán theo số451 cũ. Tăng3 do các regression cases mới ở Auth/SOS/mapper.

Đã đối chiếu theo tuple suite name + testcase name: toàn bộ 7 failures ban đầu đều có mặt và PASS trong full results. Không có @Ignore/test removal mới. Full evidence được lưu `/tmp/p5a01rv-full-results` và kết quả tổng hợp `/tmp/p5a01rv-validation-results.json`. HTML cuối tại `app/build/reports/tests/testDebugUnitTest/index.html`; XML cuối tại `app/build/test-results/testDebugUnitTest/`.

Related groups riêng: 13 tests/4 suites PASS, `/tmp/p5a01rv-targeted-results`. Build logs `/tmp/p5a01rv-targeted.log`, `-security.log`, `-routing.log`, `-full.log`, `-apk.log`. Generated reports/logs là bằng chứng thực thi, không source artifact thuộc preservation baseline.

## 13. Android APK Build Results

```sh
./gradlew --offline --no-daemon :app:assembleDebug
```

**BUILD SUCCESSFUL in 11s, exit0.** APK: `/home/vantri/AndroidStudioProjects/bai1/app/build/outputs/apk/debug/app-debug.apk`.

- Dung lượng: 95,665,573 bytes.
- SHA-256: `ca286bd65d49272bb590302fc955c6d4c8a66375d699906b200e1e9f79560c87`.
- output-metadata.json: applicationId com.example.bai1, variant debug, versionCode1/versionName1.0.

Build config/package/signing configuration và Firebase/Maps/SOS integration source giữ nguyên baseline. APK mới là generated debug output; không install/deploy/chạy tài khoản production. Build PASS không thay thế device E2E.

## 14. Device/Emulator Status

`/home/vantri/Android/Sdk/platform-tools/adb devices -l` exit0, chỉ header “List of devices attached”, không thiết bị/emulator.

**DEVICE_E2E=NOT_RUN.** Không connected tests, không boot emulator hoặc cài SDK. Theo acceptance task này, không có thiết bị không chặn JVM/build acceptance; không tuyên bố app/device/HTTP UI đã chạy.

## 15. Artifact Preservation

Final SHA-256/mtime_ns đối chiếu baseline: **209/216 Android files giữ nguyên; đúng7 files Auth/SOS/test được phép thay đổi; không xóa file.** File mới duy nhất là báo cáo này. **677/677 Backend files nguyên SHA/mtime_ns**, không file Backend mới. Bao gồm **20 output graph/manifest JSON/GZIP artifacts** được chụp.

Toàn bộ routing production/tests, AuthRepository lifecycle hook, MainActivity/build.gradle vốn dirty, AndroidManifest, Gradle config, Firebase config và báo cáo cũ giữ nguyên so đầu task. Không dựa riêng git diff vì routing đang untracked. Kiểm tra supplementary hash/mtime áp dụng cả source untracked. git diff --check exit0; final status giữ dirty/untracked cũ và liệt kê7 files sửa mới.

Không commit/push/deploy hoặc network mutation. Chi tiết before/after digest/timestamp của7 files ghi phụ lục; snapshot baseline/comparison ở /tmp. Không coi generated APK/build/cache changes là source preservation violation.

## 16. Remaining F04–F08 Issues

| Finding | Trạng thái |
|---|---|
| F04 – GZIP/JSON cache binding | OPEN; characterization PASS vẫn chỉ mô tả lỗi tồn tại |
| F05 – Atomic cache generation | OPEN; characterization PASS không chứng minh atomic publication |
| F06 – Missing hazard/default CLEAR | OPEN; chưa sửa policy/parser |
| F07 – graphVersion/path containment | OPEN; chưa sửa filesystem boundary |
| F08 – HTTPS/TTL/resource/freshness controls | OPEN; chưa sửa network/config/limits |

Chưa thực hiện CACHE/NET/P5-P01/P5-I02/routing UI/production qualification. Compile warnings StateFlow inheritance và UI deprecations được lưu để xử lý task riêng, không ảnh hưởng kết quả build hiện tại. Không kết luận graph/download/reference demo an toàn chỉ vì hard lock strict đã được kiểm thử.

## 17. Final Decision

**PASS – P5-A01R-V.** Bảy failures được tái hiện và xử lý theo root cause; related13, security21, routing413, full454 đều PASS, debug APK build PASS, không mất F01–F03 và không sửa source/artifacts ngoài scope. Kết luận dựa trên JUnit runtime/Gradle outputs cùng preservation hashes, không dựa số test declarations hoặc fake consumer.

Phạm vi PASS là JVM/build và contract strict fail-closed hiện tại. Production authority vẫn chưa có, strict routing vẫn DISABLED. Device/Firebase production/real SOS/HTTP UI E2E chưa chạy; F04–F08 chưa được nghiệm thu. Báo cáo này cập nhật runtime evidence cho source P5-A01R trước đó, không nghiệm thu toàn bộ ứng dụng cứu hộ.

## 18. Recommended Next Task

Đề xuất task riêng xử lý cache binding/atomic generation/path containment theo findings F04/F05/F07, có preservation guard, fault/recovery tests và không mở strict permission. Network/hazard controls theo F08/F06 cũng cần scope riêng. Warning DerivedStateFlow nên được đánh giá khi bảo trì coroutine API.

Dừng tại P5-A01R-V. Không tự bắt đầu CACHE/NET/P5-P01/P5-I02, không nâng dependency, không production unlock hoặc deploy. Device/HTTP UI integration chỉ nghiệm thu sau khi các prerequisites tương ứng được xử lý và có thiết bị cô lập phù hợp.

### Phụ lục – SHA-256 và mtime_ns source/test được sửa

`app/src/main/java/com/example/bai1/auth/network/AuthApiClient.kt`

- Trước: `a1c0097dbdc8b235ae3da54d0e79b740ac9945d3ca1dd842d8b42caefdb2e533`, mtime_ns `1789742519285281056`.
- Sau: `5cd17a05ce42009985e826f9efc8e9292666f4888a92b7ba202628ae75e17b20`, mtime_ns `1793980542164819780`.

`app/src/main/java/com/example/bai1/sos/realtime/FirebaseSosEventMapper.kt`

- Trước: `0cd9b2a8e6904f2a1f4283ff674824d2fbca47567be8a2aa8dce68ae10e87188`, mtime_ns `1793441967734839792`.
- Sau: `a5f4f525b4a93df318a94612b426fbabe15e8c3c482c6f89e52d5466eb045871`, mtime_ns `1793980542202345918`.

`app/src/main/java/com/example/bai1/sos/repository/FirebaseSosRepository.kt`

- Trước: `888ed00cb792472defffd0c8f12576572c6ba8392fd10dc12b1df36541a63c02`, mtime_ns `1793423647406636380`.
- Sau: `9fe68d44688eaed5efc2c7521754856c6731538c04820e477a3568881d816e70`, mtime_ns `1793980542203670378`.

`app/src/main/java/com/example/bai1/sos/repository/InMemorySosRepository.kt`

- Trước: `cb4e3847f02a278954b9bb4a5d7e4d3779db5263405348a68958925a99f0c744`, mtime_ns `1793423613037691178`.
- Sau: `4a47d9235d639a89e57c2a37b2c2d91596b5e39ae8b4853a55bb7eda7a3ef621`, mtime_ns `1793980542202824615`.

`app/src/test/java/com/example/bai1/auth/network/AuthApiClientTest.kt`

- Trước: `433ab96468589951dfb188d8c0504e12de056557edf19b24804ea7cc80ac87ed`, mtime_ns `1789742519293463940`.
- Sau: `691447c31c78ea9edea6fb9836e1e8756e48ab735ac30537fd69bdfe0da26a0b`, mtime_ns `1793980542206054655`.

`app/src/test/java/com/example/bai1/sos/SosRepositoryTest.kt`

- Trước: `0cec3ccad4fd4a61c19d1a402095a3d46bc180c593c6942d697fb03d9e432e5c`, mtime_ns `1793423723455995046`.
- Sau: `7e96eee42d93aa152fe149a1f1f3f23ca962cab693f289aaf345e570f7b9db42`, mtime_ns `1793980542205681890`.

`app/src/test/java/com/example/bai1/sos/realtime/FirebaseSosEventMapperTest.kt`

- Trước: `a7a35663bfee4318e0c3d89f0b7304145ec35dacb9b0bfb06c0680a8447ce485`, mtime_ns `1789742519292685624`.
- Sau: `663ca77f0f10ac7e12a7188b32639bbe78142f537c6f9946392ef8c0718311dc`, mtime_ns `1793980542205288477`.

