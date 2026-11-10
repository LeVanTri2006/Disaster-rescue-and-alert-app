# P5-E03-USB — Real Android Phone Validation

Ngày thực thi: 2026-11-08, Asia/Bangkok. Kết luận **CONDITIONAL** cho phạm vi production UI subset; không nghiệm thu toàn bộ ứng dụng.

## 1. Executive Summary

Đã trực tiếp build, cài, mở và chạy instrumentation trên **OPPO CPH2159, Android 13 / API 33, USB**. Không dùng emulator trong task này. Lượt nghiệm thu cuối có **16 tests, failures=0, errors=0, skipped=0**, driver exit0, từng instrumentation invocation exit0; số liệu lấy từ JUnit execution XML.

```text
DEVICE_TYPE = PHYSICAL_USB
ADB_TRANSPORT = USB
DEVICE_SERIAL = REDACTED
PHYSICAL_DEVICE_CONNECTED = true
ADB_AUTHORIZED = true
BOOT_COMPLETED = true
ANDROID_API_LEVEL = 33
DEVICE_TESTS_EXECUTED = true
UI_TESTS_PASS = 16
UI_TESTS_FAIL = 0
FULL_APP_ISOLATION = BLOCKED
PRODUCTION_UI_E2E = NOT_RUN
FULL_APPLICATION_E2E = NOT_RUN
ROUTING_UI = NOT_IMPLEMENTED
F06 = OPEN
STRICT_ROUTING_DISABLED
FINAL_ACCEPTANCE = CONDITIONAL
```

16 là tổng các ca device instrumentation của subset, gồm cả policy/API/cache; không phải 16 màn hình. Không cộng kết quả API37 emulator cũ vào số liệu physical. Initial tooling failures được giữ và giải thích ở phần 16.

Bằng chứng: [physical results](../tools/ui-harness/evidence/p5e03-usb/physical-final/results.json), [aggregate JUnit](../tools/ui-harness/evidence/p5e03-usb/physical-final/TEST-production-ui.xml), [evidence index](../tools/ui-harness/evidence/p5e03-usb/evidence-index.json).

## 2. Laptop Disk Space Gate

Trước build, `df -h / /home`: `/dev/nvme0n1p6` mounted `/`, 37G total, 27G used, **7.8G available**; `/dev/nvme0n1p7` mounted `/home`, 57G total, 44G used, **11G available**. Gate **PASS**, vượt ngưỡng 2 GB. Driver cũng kiểm tra free bytes trước build. Sau test vẫn 7.8G và 11G tương ứng. Không dọn Docker/Snap/Gradle/SDK hoặc dữ liệu người dùng.

## 3. Physical Android Device Inventory

`adb devices -l` phát hiện đúng **một thiết bị USB authorized**, model CPH2159, product CPH2159T2, device OP4F25L1; không có emulator/offline/unauthorized hay thiết bị thứ hai. Serial được dùng thực tế trong mọi ADB device command, nhưng lưu bằng **REDACTED** trong evidence/report. Không đoán serial và không dùng emulator-5554 thay thế.

## 4. USB/ADB Connection Status

ADB state `device`, `sys.boot_completed=1`; `ro.kernel.qemu` không là 1. Mọi command cài/test/logcat/reverse/cleanup đều target serial USB được inventory chọn. Không bypass USB authorization, không có thao tác tự bấm hộp thoại hệ điều hành trên điện thoại. Điện thoại vẫn ở state `device` sau cleanup. [Device and cleanup metadata](../tools/ui-harness/evidence/p5e03-usb/device-and-cleanup.json).

## 5. Android Version and Permission Assessment

`ro.build.version.release=13`, `ro.build.version.sdk=33`. Không áp assertion hoặc cấp/revoke ACCESS_LOCAL_NETWORK của API37 lên API33. Năm ca API37 grant/deny/revoke/permission persistence được đánh dấu **NOT_APPLICABLE**, không đưa vào 16 ca PASS. Physical mode từ chối API37+ trước build/install vì chưa có workflow xác nhận quyền do người dùng thực hiện.

Trên API33, test shell hiển thị `not-applicable`, nút yêu cầu local permission bị disable. Actual loopback request qua reverse thành công mà không có thao tác grant/revoke. Đây là kết quả của API33 + USB loopback, không chứng minh quyền LAN trên API37 hoặc LAN/Wi-Fi. Actual NetworkSecurityPolicy: base cleartext=false, 127.0.0.1/localhost=true theo debug policy; example.invalid/192.0.2.1=false.

## 6. Repository Baseline

Đã đọc bốn báo cáo P5-E02, P5-E01R, P5-A01R-NET và P5-A01R-CACHE. Kiểm tra ancestor/repository AGENTS.md không tìm thấy instruction áp dụng. Đã chạy `pwd`, `git status --short`, `git diff --check` cho hai repo; baseline có nhiều modified/untracked files từ các task trước và được giữ nguyên.

Trước sửa: snapshot SHA-256 và `mtime_ns` **328 Android files**, **677 Backend files**. Loại generated/cache/VCS directories `.git`, `.gradle`, `build`, `dist`, `node_modules`, `.idea`, `.kotlin`. Baseline snapshots và before/after của edited files được lưu trong evidence. Android source/config/test cũ ngoài đúng năm harness files không thay đổi. Không reset/clean/restore, commit/push/deploy.

## 7. Firebase/Auth/SOS Isolation Audit

MainActivity khởi tạo AuthAppDependencies, SosAppDependencies, RescueMissionAppDependencies, RoutingAppDependencies trước AppNavGraph. Auth DI dùng saved token storage và Firebase session; AuthViewModel init gọi restoreSession. LoginScreen trực tiếp tạo CredentialManager và có Firebase sign-in. Production merged manifest vẫn có FirebaseInitProvider trước Activity startup. SOS dependency graph chứa production API và Firebase realtime provider; Maps/location phụ thuộc Google/FusedLocation. Vì vậy **không cài/mở APK production**.

Standalone target dùng bare android.app.Application, một non-exported IsolatedUiActivity, allowBackup=false; pre-install gate kiểm manifest không provider/service/receiver/meta-data tự khởi tạo. DEX gate loại Firebase/GMS/CredentialManager, MainActivity/AppNavGraph/AuthRepository/Auth DI/SOS HTTP client; xác nhận actual production Register/Onboarding/About/Auth client tồn tại. Đối chiếu **12 generated Kotlin copies byte-for-byte với originals**, lưu hashes. Token fixture chỉ ở memory; không production session/JWT, location/SOS hoặc real account.

Gate **PASS cho subset**. `FULL_APP_ISOLATION=BLOCKED` giữ nguyên.

## 8. Exact Files Changed

Đúng năm baseline files được sửa, tất cả thuộc standalone harness:

- `tools/ui-harness/run_ui_tests.py`: physical USB selection/API/disk gates, selective cases, reverse lifecycle, serial redaction, timing evidence và cleanup.
- `tools/ui-harness/host_fixture.py`: chọn owned loopback download origin, reset fault và digest-matched malformed JSON GZIP.
- `tools/ui-harness/src/main/java/com/example/bai1/uiisolated/IsolatedUiActivity.kt`: USB loopback pinning, API-aware test-only permission UI, synthetic Auth login seam.
- `tools/ui-harness/src/androidTest/java/com/example/bai1/fixture/ProductionSubsetUiTest.kt`: physical scenarios, scoped native swipe, startup/graph metrics; giữ các assertion production trước.
- `tools/ui-harness/README.md`: cách chạy USB và phạm vi/giới hạn.

Mới: báo cáo này và evidence dưới `tools/ui-harness/evidence/p5e03-usb/`. [Evidence index](../tools/ui-harness/evidence/p5e03-usb/evidence-index.json) liệt kê từng evidence file và SHA-256; index không tự hash chính nó. Không sửa production UI/business/configuration, build dependencies hoặc Backend. [Exact before/after hashes and mtimes](../tools/ui-harness/evidence/p5e03-usb/android-preservation.json).

## 9. Isolated APK Install/Launch Results

Reuse applicationId **com.example.bai1.p5e02.uiisolated** và `.test`, khác production com.example.bai1. Mỗi lượt driver xác nhận cả hai package chưa tồn tại trước khi install, không dùng install -r. Cài với `adb install -t`, chạy actual AndroidJUnitRunner; source-copy/manifest/DEX/policy gate xong trước cài.

Final standalone build `assembleDebug assembleDebugAndroidTest` exit0, 75 actionable tasks, 9 executed; actual instrumentation chạy mới, không lấy build UP-TO-DATE làm device PASS. Target APK SHA-256 `ce617fb3db4ad8c25191b84a8955ca323bcb6fd56791e456ead658a306fa529c`. Production APK chỉ build trên laptop, không cài điện thoại. Sau test đúng hai task-created packages được uninstall; path checks rỗng, cleanup_errors=[] và owned_packages_remaining=[].

## 10. ADB Reverse USB Backend Fixture Test

Owned ThreadingHTTPServer bind **laptop 127.0.0.1**, OS cấp ephemeral port **35087** theo final run command metadata (xem results.json để lấy giá trị authoritative). Kiểm tra existing reverse mappings trước tạo, từ chối collision; mapping `tcp:<owned-port>` tới đúng laptop port. Phone requests dùng `http://127.0.0.1:<owned-port>`; không dùng 10.0.2.2 cho physical và không mở LAN listener.

Actual GET /health hai lần qua USB trả đúng marker `P5E02_LOCAL_ONLY`, kể cả sau Activity recreate. Auth/routing/download requests được ghi bằng method/path/mode/bearer-present, không body hoặc secret. Client pin exact scheme/host/port và path allowlist, no proxy/redirect/automatic retry, không có production fallback.

`usbMissingReverseFailsClosed`: remove đúng owned mapping, actual client thất bại, không token/routing status và host không nhận request. Mapping được restore cho các ca sau. Final cleanup reverse list rỗng; không dùng --remove-all và không rút cáp hay can thiệp quyền ADB. Server shutdown/close sau test. Reverse support **PASS**.

## 11. Production UI Component Coverage

| Thành phần | Kết quả physical |
|---|---|
| Actual OnboardingScreen, skip callback | PASS |
| Actual RegisterScreen, native field input + submit | PASS |
| Actual AboutAppScreen, back callback | PASS |
| Actual LoadingState/EmptyState/ErrorState | PASS |
| MainActivity/AppNavGraph/LoginScreen | BLOCKED / NOT_RUN |
| AuthRepository/ViewModel saved account lifecycle | NOT_RUN |
| SOS/Maps/location/rescue UI | NOT_RUN |
| Routing UI | NOT_IMPLEMENTED |

Test shell/NavHost và session controls là adapter riêng, không phải production AppNavGraph. Physical screenshot case **NOT_RUN** để tránh thu status bar/notification cá nhân; không dùng screenshot emulator làm bằng chứng phone.

## 12. Authentication Fixture Test Results

Actual production OkHttpAuthApiClient và RegisterScreen: synthetic registration success, HTTP401/403/500 mapped error text, offline, actual socket timeout, slow/loading **PASS**. Fixture logout/expiry clear memory token và production routing status, Activity recreation không restore fixture session. Những ca này không chứng minh production AuthRepository logout/refresh/storage.

Actual client `/auth/login` success và 401 **PASS ở API level**, không có LoginScreen hoặc Google/Firebase login. Chỉ synthetic fixture identity, không đăng nhập tài khoản thật. Reports/logs không chứa password/token literal.

**Validation limitation:** negative form-validation tests **NOT_RUN / chưa nghiệm thu**. Static inspection cho thấy RegisterScreen submit chuyển bốn fields vào callback; confirmPassword/agreeTerms không tham gia điều kiện submit. Harness không thêm validation giả để che behavior này; cần audit/remediation riêng cùng Auth isolation. Không suy từ valid registration PASS rằng mismatch/terms/blank validation hoạt động đúng.

## 13. Navigation Test Results

Actual onboarding skip → RegisterScreen, test-nav → AboutAppScreen và actual About back callback → Register **PASS**. Fixture authenticated/unauthenticated states, logout, expiry và Activity restart **PASS** trong adapter. Full production nav graph, Login/role selection/forgot/reset/Home/Settings/Maps/SOS navigation **NOT_RUN** do DI/cloud blockers. Không tạo routing UI.

## 14. Network Errors and Permission Results

| Trường hợp | Kết quả |
|---|---|
| HTTP200 health marker qua USB | PASS |
| Synthetic registration success envelope | PASS |
| HTTP401 / 403 / 500 UI errors | PASS |
| Actual socket read timeout | PASS |
| Server connection shutdown/reset | PASS — error, token/status absent |
| Controlled offline before wire | PASS — no host request |
| Owned reverse removed | PASS — fail closed, no host request |
| Slow request loading + production feedback | PASS |
| Actual API33 debug cleartext policy | PASS |
| ACCESS_LOCAL_NETWORK API37 matrix | NOT_APPLICABLE |
| Physical release-like/API37 runtime policy | NOT_RUN |

Không tắt Wi-Fi/mobile data, đổi global networking, giả lập/bypass OS prompt hoặc dùng Backend production. Runtime policy trên API33 không thay thế verification API37.

## 15. Routing Safety Regression

Physical actual downloader nhận synthetic GZIP/JSON hai nodes, một UNVERIFIED edge. Parser result nodes/edges và returned SHA-256 phải khớp fixture digest do host truyền vào instrumentation. Fresh download → cached reuse phải giữ generation path; sửa JSON trong **owned app cache** cùng length làm cache invalid; download lại tạo generation khác và hợp lệ. Các assertion **PASS**.

Digest/size-matched valid GZIP chứa malformed JSON bị production parser từ chối với GRAPH_VALIDATION_FAILED; không chỉ dùng size mismatch để làm test xanh. Backend fixture readiness false và repository strictRoutingReady/canNavigateSafely false; fixture logout/expiry revoke routing memory. Không graph integrity, fixture session hoặc device PASS nào tạo production qualification.

Fresh routing JVM **462/462, 13 suites**, failures/errors/skipped=0, exit0; gồm strict UCS/context denial, parser/cache/downloader/repository/network tests. Production hard lock và main/debug NSC giữ SHA/mtime baseline. Invariant: **NO PRODUCTION-AUTHORITATIVE QUALIFICATION => STRICT_ROUTING_DISABLED**.

## 16. Device Crash/ANR/Performance Findings

Final own-PID/time-window log review: **0 FATAL EXCEPTION / ANR markers**, 0 fixture secret literal leaks. Không thu global logcat, không xóa global logs, không đọc private storage/logs app khác. Đây là kết quả trong cửa sổ test, không là chứng minh mọi crash/ANR trên điện thoại đều không tồn tại.

Startup metric là ActivityScenario.launch tới khi shell visible: **17 launches, min 726 ms, median 1662 ms, max 1792 ms**; gồm một second launch trong cùng process của API login test. Không phải cold-process startup benchmark. Tiny synthetic USB graph download + parse **67 ms**. Loading/error feedback được assertion thực tế; per-case end-to-end durations lưu riêng, gồm overhead instrumentation/UI input, không được gọi là API latency. Memory pressure/large graph benchmarking **NOT_MEASURED / NOT_RUN**. Không suy rộng hiệu năng cho mọi điện thoại.

Initial tooling runs được giữ ở `initial-tooling/`:

| Run | Completed XML cases | Failures | Driver exit |
|---|---:|---:|---:|
| p5e03-usb | 4 | 2 | 130 (interrupted to remediate) |
| p5e03-usb-final (intermediate name only) | 6 | 4 | 130 |
| p5e03-scroll-check | 2 | 1 | 1 |
| p5e03-scroll-settle | 2 | 2 | 1 |
| p5e03-native-swipe | 2 | 0 | 0 |

Failure là `Register submit visible`: API33 accessibility scroll không đưa nút vào tree ổn định trên màn hình phone. Hide IME, loại editable scroll targets và chờ animation riêng lẻ chưa đủ. Remediation cuối dùng native touchscreen swipe **chỉ trong bounds ScrollView và khi active package đúng owned target**; chờ Compose settle. Không tự bấm permission/system UI, không thay RegisterScreen hoặc giảm expected error/session/routing assertions. Hai focused cases sau sửa PASS, rồi chạy mới toàn bộ 16 cases; kết quả ban đầu không bị xóa hoặc gộp thành final PASS.

## 17. Full Android JVM Regression

Toolchain hiện có: Java/javac25.0.4.1, Gradle9.7.1, Android SDK có sẵn; offline/no-daemon, không download/nâng dependency. [Actual commands, counts and exit codes](../tools/ui-harness/evidence/p5e03-usb/jvm-build/results.json).

```sh
./gradlew --offline --no-daemon :app:testDebugUnitTest --rerun-tasks --tests 'com.example.bai1.rescue.routing.*'
./gradlew --offline --no-daemon :app:testDebugUnitTest
```

Targeted routing **462 tests /13 suites**; full **503 tests /26 suites**; cả hai failures=errors=skipped=0, exit0. Counts lấy actual XML riêng của từng invocation. Full test task thực thi sau đổi filter, không lấy cached UP-TO-DATE test count. Không cộng overlapping 462+503 thành 965 independent tests. XML snapshots và logs trong `jvm-build/`. JVM/main app sources không đổi sau các lần này.

## 18. Android APK Build Results

`./gradlew --offline --no-daemon :app:assembleDebug`: **BUILD SUCCESSFUL, exit0**, 39 actionable tasks, 3 executed. Main APK `94,998,123` bytes, SHA-256 `53731de2f40306f487d4ef558b8e4d5246a6a091a0aaca30c65c081433bb3765`. File `app/build/outputs/apk/debug/app-debug.apk`. Không cài/mở production APK trên điện thoại; main-app runtime vẫn BLOCKED.

Standalone target/test APK build và manifest/DEX/source-copy/compiled policy gate cũng PASS trước final physical install. Không sửa release settings để làm test xanh. [Compiled debug policy](../tools/ui-harness/evidence/p5e03-usb/physical-final/compiled-policy.txt).

## 19. Backend/Graph Artifact Preservation

**677/677 Backend baseline files giữ nguyên SHA-256 và mtime_ns**, không xóa file; bao gồm **20 graph/manifest JSON/GZIP artifacts** ở output directories. Không chạy Backend production/server/database, không chỉnh qualification engine, artifact hay strict authorization.

Android **323/328 baseline files giữ nguyên**, đúng năm changed harness files đã liệt kê ở phần8, không baseline file mất. Production source/config/test, historical reports/evidence và main/debug network policies giữ nguyên. [Backend preservation](../tools/ui-harness/evidence/p5e03-usb/backend-preservation.json) và [Android preservation](../tools/ui-harness/evidence/p5e03-usb/android-preservation.json). Generated Gradle/APK outputs không thuộc immutable source snapshot.

## 20. Personal Device Data Preservation

Chỉ đọc model/OS/API/boot/disk metadata: `/data` khoảng108G total,100G used,8.4G free trước test. Không IMEI/account/contact/photo inventory, root/unlock/flash/wipe/reset/pm clear/global configuration/Play Protect changes. Không cấp location/camera/microphone/SMS; physical mode không grant/revoke runtime permissions.

Chỉ cài và uninstall hai package riêng đã chứng minh absent; không ghi đè app có sẵn. Cache mutation chỉ trong private cache của newly installed target, sau đó owned package cleanup. Log PID/time scoped; không screenshot phone cá nhân. Cleanup đã kiểm chứng both package paths và reverse list rỗng, ADB vẫn authorized/connected. Không có forensic proof byte-for-byte cho toàn bộ personal data vì task chủ động không đọc dữ liệu đó; preservation được bảo đảm trong phạm vi thao tác task và cleanup receipts.

## 21. Remaining Full-App Isolation Blockers

Main manifest Firebase automatic startup; Auth saved session/Firebase bridge; Login CredentialManager/Google/Firebase coupling; production DI/API dependencies; SOS realtime lifecycle; Maps/location SDK. Cần dependency seams/isolated app variant và explicit lifecycle controls trước khi cài main app trên personal phone. Negative registration validation, production account restore/refresh/logout và toàn bộ production navigation chưa nghiệm thu. Không thực hiện refactor lớn trong task USB.

## 22. Remaining F06/Parity/P5-I02 Work

**F06 OPEN**, không sửa hazard semantics. Không làm parity/P5-P01/P5-I02, không tạo Routing UI hoặc production-authoritative qualification. Device/cache/digest thành công không phải chứng nhận tuyến cứu hộ an toàn ngoài thực địa. Strict routing tiếp tục DISABLED.

## 23. Final Acceptance

**CONDITIONAL — P5-E03-USB.** Physical USB subset, safe install/cleanup, actual fixture requests, UI/policy/graph/cache scenarios **PASS 16/16**; fresh routing/full JVM và debug APK build PASS. Backend/artifacts/personal-device action scope được bảo toàn. Không còn failing case trong final acceptance run.

MainActivity/AppNavGraph/Login/AuthRepository/SOS full isolation vẫn **BLOCKED** và full application E2E **NOT_RUN**, nên không tuyên bố toàn bộ app PASS. API37-only permission assertions **NOT_APPLICABLE** trên API33; physical release-like suite và full form-validation matrix **NOT_RUN**. Initial automation failures đã remediation, có bằng chứng riêng.

Final actual JUnit cases:

| Test method | Result | ADB exit |
|---|---|---:|
| actualProductionOnboardingAndAboutNavigationRenderWithoutCloud | PASS | 0 |
| productionRegisterUsesActualApiClientAgainstSyntheticFixtureThenLogoutClearsRouting | PASS | 0 |
| syntheticSessionExpiryDoesNotRestoreRoutingOnRestart | PASS | 0 |
| actualRegisterRenders401FixtureError | PASS | 0 |
| actualRegisterRenders403FixtureError | PASS | 0 |
| actualRegisterRenders500FixtureError | PASS | 0 |
| actualRegisterRendersOfflineFixtureError | PASS | 0 |
| actualRegisterRendersActualSocketTimeout | PASS | 0 |
| actualRegisterLoadingStateAndProductionFeedbackComponentsRender | PASS | 0 |
| usbActualPlatformNetworkPolicy | PASS | 0 |
| usbSyntheticLoginApiSuccessAndFailure | PASS | 0 |
| usbGraphCacheGenerationAndTamperDetection | PASS | 0 |
| usbLoopbackMarkerAndActivityRestart | PASS | 0 |
| usbMissingReverseFailsClosed | PASS | 0 |
| usbResetConnectionFailsClosed | PASS | 0 |
| usbInvalidGraphRejected | PASS | 0 |

## 24. Recommended Next Task

Task riêng để cô lập production Auth/MainActivity/AppNavGraph: inject HTTP/session/Firebase/credential dependencies, chặn automatic production initialization trong isolated variant, kiểm tra negative register validation và actual LoginScreen/navigation/restore/logout bằng fixture trước khi mở rộng personal-phone runtime. Giữ strict routing khóa. Nếu cần API37 assurance, lập user-led physical permission workflow và chạy trên thiết bị API37; không suy từ API33.

Dừng tại P5-E03-USB; không tự thực hiện các task tiếp theo, commit/push/deploy hay liên hệ production services.
