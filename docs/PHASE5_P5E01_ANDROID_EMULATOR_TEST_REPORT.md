# PHASE 5 – P5-E01: Isolated Android Emulator Test Report

Ngày: 2026-11-07, Asia/Bangkok. Không commit/push/deploy; không chuyển task khác.

## 1. Executive Summary

**CONDITIONAL — harness cô lập đã chạy thật trên Android; có finding release platform policy cần remediation riêng.**

- Debug device: **18/18 PASS**, không errors/skips.
- Nondebuggable release-like device: **3/5 PASS, 2 FAIL**, không errors/skips. Hai failure được giữ nguyên, không vô hiệu hóa assertions.
- Tổng final instrumentation: **23 tests, 21 pass, 2 fail**; không cộng các lần chạy lặp vào tổng này.
- Full JVM thực thi mới: **503/503 PASS**, 26 suites; assembleDebug PASS.
- Isolation gate PASS: package/data riêng, không Firebase/Maps/Auth repository/SOS client, không production Application/MainActivity/SDK providers.

Debug cleartext allowlist và socket fixture 10.0.2.2 hoạt động khi cấp quyền local network API37 chỉ cho harness. Release-like phát hiện platform vẫn cho HTTP loopback dù compiled XML chỉ base-config=false. Production routing client độc lập vẫn reject HTTP trước khi đọc token. Không sửa production policy trong P5-E01 để làm test xanh.

DEBUG_PLATFORM_ENFORCEMENT=PASS trong matrix đã chạy. **RELEASE_PLATFORM_ENFORCEMENT=FAIL_API37_IMPLICIT_LOCALHOST**. FULL_APPLICATION_E2E=NOT_RUN; ROUTING_UI=NOT_IMPLEMENTED; F06=OPEN; STRICT_ROUTING_DISABLED.

## 2. Repository Baseline

Android: /home/vantri/AndroidStudioProjects/bai1. Backend READ ONLY: /home/vantri/AndroidStudioProjects/canh-bao-thien-tai-backend-app.

Đã kiểm tra pwd, git status --short, git diff --check và AGENTS.md ở ancestors/repo (không có instruction áp dụng). Đã đọc/đối chiếu các báo cáo NET, CACHE, runtime regression và related audit. Baseline latest NET: full503/routing462/security90, APK/config PASS; platform chưa instrumented ở task trước. Không sửa lại báo cáo lịch sử để che các khác biệt device vừa tìm thấy.

Snapshot SHA-256/mtime_ns trước sửa: Android225 files, Backend677 files; bao gồm untracked source. Exclude path parts .git/.gradle/build/dist/node_modules/.idea/.kotlin. Repository dirty từ trước: giữ nguyên toàn bộ baseline source/config/tests/docs. Không dùng git HEAD thay baseline và không reset/clean/restore.

Môi trường đã tự kiểm tra: Java/javac25.0.4.1 tại /usr/lib/jvm/java-25-openjdk-amd64; wrapper Gradle9.7.1, AGP9.4.0 sẵn có. JAVA_HOME/PATH chỉ đặt cho process kiểm thử; mọi build dùng --offline --no-daemon. Không tải/cài dependency hay SDK và không đổi versions/toolchain của app.

## 3. Emulator/ADB Inventory

Đã chạy các lệnh sau, exit0 sau khi dùng ADB ngoài sandbox socket restriction:

```bash
/home/vantri/Android/Sdk/platform-tools/adb devices -l
/home/vantri/Android/Sdk/emulator/emulator -list-avds
/home/vantri/Android/Sdk/platform-tools/adb -s emulator-5554 emu avd name
/home/vantri/Android/Sdk/platform-tools/adb -s emulator-5554 shell getprop sys.boot_completed
/home/vantri/Android/Sdk/platform-tools/adb -s emulator-5554 shell getprop ro.build.version.sdk
```

| Trạng thái | Kết quả |
|---|---|
| AVD_EXISTS | true — Pixel_6_Pro |
| AVD_RUNNING | true — emulator-5554 |
| DEVICE_CONNECTED | true — device, sdk_gphone16k_x86_64 |
| BOOT_COMPLETED | true — 1, xác nhận lại cuối task |
| ANDROID_API_LEVEL | 37 |
| DEVICE_TESTS_EXECUTED | true — real instrumentation |
| FULL_APPLICATION_E2E | NOT_RUN |

Không boot/wipe/reset/thay AVD. Emulator có thể chứa tài khoản/data thật: không giả định máy sạch, không đọc tài khoản hoặc private storage và không xóa dữ liệu app khác. Lần ADB đầu trong sandbox bị Operation not permitted tại socket5037; ADB ngoài sandbox đã nhận thiết bị, đây không phải lỗi emulator.

## 4. Firebase/SOS/Auth Isolation Audit

Safety gate hoàn thành trước install/launch:

| Đường production đã kiểm tra | Kết luận |
|---|---|
| Main manifest/Application | Không custom Application, nhưng merged manifest có FirebaseInitProvider/component registrars, analytics và SDK components; không an toàn chỉ thay Activity |
| MainActivity | onCreate khởi tạo AuthAppDependencies, SosAppDependencies, RescueMissionAppDependencies và RoutingAppDependencies |
| Auth | AuthViewModel init gọi restoreSession; SharedPreferencesTokenStorage có thể chứa session cũ; restore gọi Backend /me/refresh và Firebase custom-token synchronization |
| Firebase | AndroidFirebaseAuthSession dùng FirebaseAuth.getInstance; provider tự khởi tạo SDK trước Activity |
| SOS | FirebaseSosRepository/FirebaseSosRealtimeDataSource có cloud auth/database; realtime collector bắt đầu khi observe được gọi; production SOS transport có khả năng gửi dữ liệu thật |
| Maps/navigation | AppNavGraph dùng Auth state và nhiều screen liên quan cloud/location; chưa có isolation cho toàn app |
| Background | Không thấy custom WorkManager enqueue/service-start trong source đã search; merged SDK providers/services/receivers vẫn đủ để chặn normal app install/launch |
| Existing instrumentation | ExampleInstrumentedTest chỉ kiểm package-name; runner mặc định vẫn khởi tạo target Application/providers, không chứng minh isolation/full E2E |

Không xác nhận production app startup chắc chắn không có network. Không thấy đường tự submit SOS ngay trong MainActivity, nhưng không dùng điều đó làm chứng nhận cloud isolation. Không cài/mở production APK hoặc dùng stored/production credentials.

Giải pháp: standalone test-only project, target package **com.example.bai1.p5e01.isolated**, test package thêm .test. Manifest chỉ bare android.app.Application + FixtureActivity không exported, allowBackup=false; INTERNET và ACCESS_LOCAL_NETWORK, không location/camera, provider/service/receiver/meta-data. APK class audit và device reflection checks xác nhận Firebase, GMS, AuthRepository, MainActivity, RoutingAppDependencies, OkHttpSosApiClient không tồn tại.

Không có production endpoint/credential trong harness. Các request chỉ tới device loopback, host10.0.2.2 health fixture; negative host dùng DNS map về controlled loopback. Không gọi Internet, real Backend/R2/Firebase/Maps hoặc SOS. Không là packet-capture chứng nhận toàn emulator: bằng chứng isolation là dependency/class/component exclusion và bounded fixture destinations, không theo dõi traffic ứng dụng khác.

## 5. Test Harness Implementation

Standalone project ở tools/emulator-harness; không include vào settings hoặc đổi app build config/dependencies. Scope cho phép test-only harness được dùng vì app/src/androidTest hiện tại vẫn khởi tạo Firebase provider của app chính.

- Biên dịch trực tiếp bảy production routing directories: algorithm/model/network/download/parser/loader/repository; không duplicate/mock consumer, không sửa source của chúng.
- Exclude RoutingAppDependencies để tránh SharedPreferences/business initialization. Chỉ trích đúng declaration AccessTokenProvider từ source SosApiClient.kt, assert exact shape trước build; không compile SOS implementation kế bên. Provider trong tests chỉ giữ synthetic token trong memory.
- Copy actual main/debug network XML tại build time; không hardcode một policy khác cho test. ReleaseLike không debuggable, DEBUG=false, test-signed bằng debug signing config, dùng main/release policy nguyên bản.
- Loopback ServerSocket phục vụ synthetic status/GZIP; Python host fixture bind127.0.0.1 ephemeral port, chỉ static health path. Không forward/proxy Internet.
- Driver refuse overwrite nếu target/test namespace đã có; audit manifest, actual DEX class set và resource table trước install. APK optimized resource path được resolve từ aapt2 table, không giả định tên ZIP entry release còn nguyên.
- Install chỉ hai namespace riêng; cấp ACCESS_LOCAL_NETWORK cho đúng harness, không đổi app chính. Chỉ xóa owned test cache root; finally uninstall hai packages proven absent trước run, không clear-data hoặc chạm app khác.
- IsolatedInstrumentationRunner kế thừa AndroidJUnitRunner, chờ1.5s để attach logcat trước test execution; không override production initialization hoặc cấp authority.
- DeviceXmlListener xuất actual JUnit lifecycle events qua instrumentation status thành XML, kiểm junitRunCount bằng testcase count. Shell exit0 không thay thế assertions.

Cached dependencies được align chỉ trong standalone harness: OkHttp4.12.0/Gson2.10.1, Okio3.9.1, Kotlin stdlib2.2.10, coroutines-android1.9.0, AndroidX runner1.5.2/ext JUnit1.1.5, annotation-experimental1.4.1, Guava31.1-android, jsr3053.0.2; lifecycle-common old coordinate được substitute sang cached lifecycle-common-jvm2.11.0. Không download; không thay app dependency graph. Build setup đã sửa DSL/source registration theo local AGP9.4 source API, không sửa production logic để compile tests.

## 6. Debug/Release Device Network Policy

**Debug PASS:** Android NetworkSecurityPolicy cho true chỉ exact10.0.2.2/127.0.0.1/localhost trong matrix. Deny outside.invalid,10.0.2.3,localhost.outside.invalid,192.168.1.1,127.0.0.2,::1,ip6-localhost. Global policy false. Actual nonallowlisted hostname với DNS trỏ controlled loopback bị UnknownServiceException trước server request; không cần gọi Internet.

First device run17 tests có1failure: host10.0.2.2 connect timeout, còn16PASS. Local installed API37 SDK có ACCESS_LOCAL_NETWORK; thêm/cấp quyền **chỉ harness** làm cùng health path hoạt động; giữ initial failure XML trong evidence. Final debug18/18 bao gồm permission assertion. Điều này không chứng minh app chính (chưa có quyền đó) chạy fixture thành công trên API37; cần task riêng nếu muốn bật fixture cho app chính.

**Release-like FAIL về loopback:** compiled resource chỉ base cleartext=false, generated DEBUG=false và runtime ApplicationInfo không debuggable đều PASS. Runtime policy snapshot:

| Host | cleartext permitted |
|---|---|
| 10.0.2.2 | false |
| outside.invalid | false |
| 127.0.0.1 | true — trái kỳ vọng release |
| localhost | true — trái kỳ vọng release |
| 127.0.0.2 | true |
| ::1 | true |
| ip6-localhost | true |

Actual raw OkHttp request đến controlled127.0.0.1 server thành công: error=null, receivedRequests=1. Nonlocal hostname mapped loopback bị platform chặn. Production OkHttpRoutingApiClient vẫn reject HTTP127.0.0.1 trước token read (0 reads/0requests), do independent RoutingTransportPolicy.

Root-cause đối chiếu local SDK source android-37.0/android/security/net/config: XmlConfigSource.java tạo mLocalhostConfig khi không có explicit localhost domain; NetworkSecurityConfig.getLocalhostBuilder() đặt cleartext=true; Domain.isLocalhost nhận loopback literals và localhost names. Runtime match source behavior. Không phải evidence debug allowance bị copy sang release; compiled release XML không có domain allowlist. Base=false riêng lẻ không chặn implicit localhost của platform này.

Giữ nguyên hai release deny assertions FAIL; không đổi expectation thành allow để lấy PASS, không chỉnh production XML theo scope P5-E01. **Finding E01-NET-01 OPEN**, cần remediation riêng và rerun. Đây là kết quả thực thi FAIL, không ghi UNVERIFIED hoặc production release app PASS. Release-like harness xác minh shared policy behavior; chưa cài full production release app.

## 7. Instrumentation Tests and Results

Reproduce (Java25/SDK được driver tự đặt; offline):

```bash
cd /home/vantri/AndroidStudioProjects/bai1
python3 tools/emulator-harness/run_device_tests.py --variant debug --evidence-dir /tmp/p5e01-final-debug
python3 tools/emulator-harness/run_device_tests.py --variant releaseLike --evidence-dir /tmp/p5e01-final-release
```

Debug driver exit0, releaseLike driver exit1 do actual assertions. Harness build commands dùng wrapper với -p tools/emulator-harness --offline --no-daemon -PpolicyVariant=<variant> assemble<Variant> assemble<Variant>AndroidTest, cả hai exit0. Gate preinstall PASS cả hai. Actual runner command dạng sau, class/port do driver truyền:

```bash
/home/vantri/Android/Sdk/platform-tools/adb -s emulator-5554 shell am instrument -w -r \
  -e class com.example.bai1.fixture.DebugDeviceRuntimeTest \
  -e listener com.example.bai1.fixture.DeviceXmlListener \
  -e hostFixturePort <owned-ephemeral-port> \
  com.example.bai1.p5e01.isolated.test/com.example.bai1.fixture.IsolatedInstrumentationRunner
```

Release chạy ReleasePolicyDeviceRuntimeTest. Install -t không -r; không overwrite package. pm grant và uninstall chỉ namespace harness. Exact invocations/ports/artifact SHA có trong evidence results.json và driver.

Attempted standalone connectedDebugAndroidTest exit1 **trước device execution** vì offline cache thiếu com.android.tools.utp:gradle-work-action:32.4.0. Không tải UTP. Raw log /tmp/p5e01-gradle-connected-attempt.log. Không chạy :app:connectedDebugAndroidTest vì production Application/provider chưa cô lập. ADB fallback vẫn chạy AndroidJUnitRunner/JUnit thật, không thay bằng JVM; gradle-connected task không được báo PASS.

Final JUnit XML:

| Runtime suite | Tests | Failures | Errors | Skipped | Driver exit |
|---|---:|---:|---:|---:|---:|
| DebugDeviceRuntimeTest | 18 | 0 | 0 | 0 | 0 |
| ReleasePolicyDeviceRuntimeTest | 5 | 2 | 0 | 0 | 1 |

am instrument process exit0/INSTRUMENTATION_CODE=-1 có thể xuất hiện cả khi JUnitFAIL; driver dựa XML và trả nonzero khi failures. Không dùng package-only ExampleInstrumentedTest làm full E2E evidence. Chi tiết cases final:

| Variant | Runtime testcase | Kết quả |
|---|---|---|
| debug | `offlineErrorCannotPublishReady` | PASS |
| debug | `unverifiedGraphLoaderAndRouterRemainDisabled` | PASS |
| debug | `apiRedirectDoesNotForwardBearer` | PASS |
| debug | `cancellationAtPublicationBoundaryPreservesA` | PASS |
| debug | `graphRedirectIsNotFollowedAndHasNoBearer` | PASS |
| debug | `contentFailurePreservesPublishedGeneration` | PASS |
| debug | `invalidationDuringRealHttpCannotReviveOldSession` | PASS |
| debug | `localHarnessActivitySmokeOnly` | PASS |
| debug | `expiredLeaseMakesNoDeviceRequest` | PASS |
| debug | `rawGzipHashAndProductionParserRunOnAndroid` | PASS |
| debug | `hostEmulatorBridgeHttpFixtureActuallyConnects` | PASS |
| debug | `runnerApiAndIsolation` | PASS |
| debug | `actualSocketReadTimeoutRemainsFailClosed` | PASS |
| debug | `actualTransportRejectsNonAllowlistedHostWithoutInternet` | PASS |
| debug | `androidDebugPolicyExactAllowlist` | PASS |
| debug | `sharedClientCredentialsNeverReachDownloadSocket` | PASS |
| debug | `truncatedSocketResponseNeverPublishesGraph` | PASS |
| debug | `api37LocalNetworkPermissionIsGrantedOnlyToHarness` | PASS |
| release | `isolatedNonDebuggableReleaseLikeRuntime` | PASS |
| release | `releaseNonLocalHostIsBlockedUsingControlledLoopbackDns` | PASS |
| release | `releaseLikeActualSocketHttpBlockedBeforeAnyRequest` | FAIL |
| release | `productionRoutingClientRejectsHttpEvenWhenPlatformAllowsLoopback` | PASS |
| release | `releasePolicyDeniesEveryDebugHostAtRuntime` | FAIL |

## 8. UI Smoke Results

| Chức năng | Kết quả / giới hạn |
|---|---|
| Isolated FixtureActivity mở/render | PASS trên emulator, không crash |
| Harness error/logout label transitions | PASS — button/text fixture, không là auth/network UI production |
| Production app launch/navigation/screens | NOT_RUN — Auth/Firebase/Maps/SOS chưa cô lập |
| Production network-error UI | NOT_RUN |
| Production account login/logout UI | NOT_RUN; routing repository invalidation được kiểm riêng trên device |
| Map/Firebase/SOS UI | NOT_RUN |
| ROUTING_UI | NOT_IMPLEMENTED; không tạo routing UI trong task |
| Full application E2E/P5-I02 | NOT_RUN, chưa hoàn thành |

Không tạo screenshot giả và không suy UI PASS từ JVM hoặc package name. Chỉ harness Activity được mở bằng instrumentation; không khởi chạy MainActivity.

## 9. Logcat/Crash Findings

Thu logcat --pid=<owned target PID> -v threadtime -T <device timestamp trước instrumentation>; không clear/read global logcat hoặc logs app khác. Runner startup delay cho phép attach trước testcase. Chỉ log trong test window; files được kiểm trước lưu report.

debug: PID 15619, start 11-07 00:53:31.000, 119 lines. Không thấy FATAL EXCEPTION/ANR marker trong PID/window này hoặc synthetic token/raw signature marker.

release: PID 15267, start 11-07 00:53:12.000, 134 lines. Không thấy FATAL EXCEPTION/ANR marker trong PID/window này hoặc synthetic token/raw signature marker.

Release log chứa hai JUnit AssertionError đúng finding, không che thành crash/runtime PASS. Timeout test chờ socket read timeout thật của production API client và map TIMEOUT; truncated socket/cancellation/failed refresh không publish incomplete cache, giữ A. Không có parsing/networking exception bất ngờ trong debug final cases.

Đây không là system-wide ANR/leak detector hoặc traffic capture; không đọc system logs/app khác để tuyên bố không còn ANR toàn thiết bị. Không real JWT/presigned credential được dùng; archived outputs không chứa secret markers/production config keys. Raw local fixtures dùng synthetic metadata.

## 10. JVM Regression Results

Đã chạy đúng command yêu cầu (exit0; initially UP-TO-DATE), sau đó force fresh evidence:

```bash
./gradlew --offline --no-daemon :app:testDebugUnitTest
./gradlew --offline --no-daemon :app:testDebugUnitTest --rerun-tasks
```

Fresh exit0, BUILD SUCCESSFUL: **503 tests/26 suites; failures=errors=skipped=0** đọc từ actual JUnit XML, không đếm declarations. Không thay tests/source app để làm xanh. Existing F01–F05/F07/F08 routing/JVM guards giữ PASS; device platform finding là evidence mới, không nằm trong khả năng JVM enforcement tests trước đó.

Log /tmp/p5e01-full.log; XML snapshots /tmp/p5e01-full-results; actual app/build/test-results/testDebugUnitTest và HTML app/build/reports/tests/testDebugUnitTest/index.html. Summary archived evidence/jvm-build-results.json. Existing DerivedStateFlow StateFlow inheritance/UI deprecation warnings được giữ; không refactor ngoài scope.

## 11. APK Build Results

```bash
./gradlew --offline --no-daemon :app:assembleDebug
```

Exit0, BUILD SUCCESSFUL. APK production **chỉ build, không install/launch**:

`/home/vantri/AndroidStudioProjects/bai1/app/build/outputs/apk/debug/app-debug.apk` — 96,434,434 bytes, SHA-256 `b906a76df36f4b09fcfc7861ffe885dc68694131df22b9e9e8faace616e524dd`.

Standalone test target APKs (đã install/run/gỡ đúng owned namespaces):

| Variant | Bytes | SHA-256 |
|---|---:|---|
| debug | 5753682 | `35138dc2d53b5033f8199948aaf73f5924a99c0277968e65edbea8aaa4f6a512` |
| releaseLike | 4640864 | `e07e5a6321c5d215306a36a679dff9d7fe26fb8fe99c91b975a57e9fbfa88640` |

Không ký/phát hành release production, không deploy. ReleaseLike chỉ dùng local test signing và không debuggable; build success không được dùng thay release policy assertions.

## 12. Exact Files Changed

Không chỉnh bất kỳ225 baseline Android files nào. Chỉ thêm standalone harness/test/docs/evidence; app/src/main, app/src/test, app/build.gradle.kts, root settings/dependencies và tất cả báo cáo cũ giữ hash/mtime_ns. New files:

- `docs/PHASE5_P5E01_ANDROID_EMULATOR_TEST_REPORT.md`
- `tools/emulator-harness/.gitignore`
- `tools/emulator-harness/README.md`
- `tools/emulator-harness/build.gradle.kts`
- `tools/emulator-harness/evidence/INITIAL-debug-without-local-permission.xml`
- `tools/emulator-harness/evidence/debug/TEST-device-runtime.xml`
- `tools/emulator-harness/evidence/debug/compiled-policy.txt`
- `tools/emulator-harness/evidence/debug/owned-process-logcat.txt`
- `tools/emulator-harness/evidence/debug/results.json`
- `tools/emulator-harness/evidence/device-driver-exit-codes.json`
- `tools/emulator-harness/evidence/jvm-build-results.json`
- `tools/emulator-harness/evidence/release/TEST-device-runtime.xml`
- `tools/emulator-harness/evidence/release/compiled-policy.txt`
- `tools/emulator-harness/evidence/release/owned-process-logcat.txt`
- `tools/emulator-harness/evidence/release/results.json`
- `tools/emulator-harness/gradle.properties`
- `tools/emulator-harness/host_fixture.py`
- `tools/emulator-harness/run_device_tests.py`
- `tools/emulator-harness/settings.gradle.kts`
- `tools/emulator-harness/src/androidTest/java/com/example/bai1/fixture/DeviceRuntimeTest.kt`
- `tools/emulator-harness/src/androidTest/java/com/example/bai1/fixture/DeviceXmlListener.kt`
- `tools/emulator-harness/src/androidTest/java/com/example/bai1/fixture/IsolatedInstrumentationRunner.kt`
- `tools/emulator-harness/src/main/AndroidManifest.xml`
- `tools/emulator-harness/src/main/java/com/example/bai1/fixture/FixtureActivity.kt`

Build script/settings/properties/dependency alignments chỉ trong harness. FixtureActivity là local smoke screen; runner/listener/device tests/host server/driver phục vụ trực tiếp P5-E01. Evidence XML giữ cả initial fixture failure và final release failures.

## 13. Artifact Preservation

So baseline SHA-256/mtime_ns: **225/225 Android files unchanged,0modified/removed;677/677 Backend files unchanged,0new/modified/removed**, gồm20 JSON/GZIP graph/manifest outputs trong năm directories. New Android files chỉ ở mục12. Excluded generated/dependency/IDE trees không được claim immutable; .idea không nằm trong proof.

Snapshot baseline /tmp/p5e01-android-baseline.json và /tmp/p5e01-backend-baseline.json; final snapshots/comparisons /tmp/p5e01-{android,backend}-{final,comparison}.json. Không reset/clean/restore user source, không sửa graph artifact/qualification engine/Firebase config hoặc business Auth/SOS. git diff --check cuối task exit0.

Driver kiểm package namespace absent trước mỗi invocation; final results owned_packages_remaining=[] cho cả hai. Chỉ uninstall package vừa tạo (không -k), không xóa cache/data cá nhân, không factory reset/AVD wipe. Test cache được tạo riêng dưới cacheDir harness và cleanup sau mỗi test. Backend chỉ được hash/read.

## 14. Device Testing Limitations

Chỉ một Pixel_6_Pro API37/x86_64, không physical device hoặc API matrix. Platform release result FAIL có evidence thật, không thể tuyên bố release enforcement đã đạt từ resource compile. Không kiểm TLS handshake/real certificate/R2 connectivity, production credential/session/background/UI hoặc Maps. Main app còn cần review API37 local-network permission cho fixture path; không thêm quyền đó trong P5-E01.

Independent routing HTTPS boundary PASS không khắc phục implicit-loopback XML policy cho client khác chỉ dựa platform policy. Chưa chạy Auth/SOS HTTP trên device vì không chứa business clients trong harness. Không mở cloud để đánh giá rủi ro đó.

Không kiểm cache power-loss durability, hostile same-UID path race, retained-generation GC/quota hoặc all-phone RAM/latency; không mở rộng CACHE/NET assurances cũ. Android device graph test dùng synthetic small graph; valid large published artifact đã được kiểm JVM ở task NET, không claim device large graph benchmark.

AGP connected automation vẫn bị offline UTP dependency blocker; direct ADB runner là fallback đã thực thi và reproducible. Không cài SDK hoặc download workaround. Test harness code không bypass production auth/strict lock, không dùng fake qualification certificate.

## 15. Remaining F06 and Demo Gaps

F06 OPEN: authoritative hazard/field freshness và production strict qualification chưa tích hợp. Digest, ATTACHED hazard, manifest booleans, READY hoặc synthetic fixture không cấp rescue authority. Device tests chứng minh UNVERIFIED graph tải/parse được nhưng loader/router vẫn STRICT_ROUTING_DISABLED; invalidated/late session không hồi sinh state.

E01-NET-01 OPEN: API37 implicit localhost cleartext trong shared release XML; cần task sửa policy production được phê duyệt riêng. API37 main-app debug fixture permission/isolation, full UI/demo và AGP offline UTP orchestration cũng còn gaps. P5-P01/P5-I02/full application E2E chưa bắt đầu/hoàn thành. Không thực hiện F06 hoặc thêm routing UI.

## 16. Final Acceptance

**CONDITIONAL cho P5-E01.** Infrastructure/isolation/real instrumentation/debug18PASS/JVM503PASS/APK build/source preservation đã hoàn thành. ReleaseLike5 cases có2FAIL, nên **không PASS toàn bộ DEVICE HARNESS/network acceptance** và không quảng bá release policy đã an toàn tuyệt đối.

| Acceptance | Trạng thái |
|---|---|
| ADB/emulator/isolation/preinstall audits | PASS |
| Debug real device runtime/security matrix | PASS —18/18 |
| ReleaseLike source build + nondebuggable identity | PASS |
| ReleaseLike platform loopback deny | FAIL —2 retained assertions |
| ReleaseLike nonlocal deny + independent routing boundary | PASS |
| Full JVM + APK build | PASS |
| Production cloud/SOS traffic trong harness | Không có đường implementation/dependency để gọi; không real accounts/services |
| Backend/artifacts/source preservation | PASS |
| Production UI/full app E2E | NOT_RUN |
| Routing UI | NOT_IMPLEMENTED |
| Strict routing | DISABLED |

Không coi am instrument shell exit0 là PASS. Không disable test, sửa policy production hoặc thay expectation để che finding. Task dừng tại báo cáo và harness; remediation production nằm ngoài scope hiện tại.

## 17. Recommended Next Task

Đề xuất task riêng **P5-E01R / F08 platform remediation**: xác định explicit localhost-domain deny phù hợp Android37 trong production main/release XML, giữ exact debug fixture allowance, đánh giá local-network permission chỉ cho debug fixture nếu cần; rerun chính release assertions đang FAIL và debug18 tests. Cần scope rõ cho mọi thay đổi production policy; chưa thực hiện trong P5-E01.

Sau khi policy được remediation, chuẩn bị UI/Auth/Firebase/SOS isolation ở task riêng trước full app/device demo. UTP offline runner automation có thể xử lý riêng khi được phép cung cấp cached tool artifact. Không tự triển khai F06, P5-P01/P5-I02, production strict activation, commit/push hoặc deploy.
