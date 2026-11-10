# PHASE 5 – P5-E01R: Android Localhost Policy Remediation Report

Ngày nghiệm thu: 2026-11-07, Asia/Bangkok. Finding: E01-NET-01. Không commit/push/deploy, không chuyển task khác.

## 1. Executive Summary

**PASS — P5-E01R cho E01-NET-01 trên isolated Android API37 runtime và shared production network policy.** Hai failures P5-E01 chuyển thành PASS nhờ sửa XML production, không thay expected=false thành true hoặc bỏ assertions.

| Kiểm tra | Kết quả |
|---|---|
| Debug device original scenarios | 18/18 PASS |
| Release-like nondebuggable device | 6/6 PASS (5 cases cũ +1 actual socket matrix) |
| Local network permission denied | 2/2 PASS |
| Local network permission revoked | 2/2 PASS |
| Fresh targeted security/cache JVM | 90/90 PASS |
| Routing JVM | 462/462 PASS |
| Full Android JVM | 503/503 PASS |
| Debug APK / release manifest/resources | BUILD SUCCESSFUL |

Mỗi suite:0failures/errors/skipped; mọi driver/build command cuối exit0. Device counts là28 testcase executions trên4run (permission suite chạy ở hai trạng thái), không cộng các JVM suites chồng lấp làm tổng độc lập.

Chỉ production main XML được sửa; debug allowlist/JWT/HTTPS endpoints/business logic giữ nguyên. Release platform policy deny localhost,127.0.0.1,127.0.0.2,::1,ip6-localhost,10.0.2.2 và nonlocal host trong matrix. IPv6 socket thực đã chạy, không ghi PASS chỉ từ policy lookup. STRICT_ROUTING_DISABLED; F06 OPEN. Full app UI/Auth/Firebase/SOS E2E chưa chạy.

## 2. Baseline Preservation

Android repo: /home/vantri/AndroidStudioProjects/bai1. Backend READ ONLY: /home/vantri/AndroidStudioProjects/canh-bao-thien-tai-backend-app.

Đã kiểm tra pwd/git status --short/git diff --check, ancestor/repo AGENTS.md (không instruction áp dụng), chụp SHA-256/mtime_ns trước sửa và đối chiếu báo cáo P5-E01/NET. Baseline Android249 files và Backend677 files ngoài .git/.gradle/build/dist/node_modules/.idea/.kotlin. Snapshot bao gồm untracked source; không dùng git HEAD thay dirty baseline. Không reset/clean/restore user source.

Kết quả cuối: Android244/249 baselinefiles unchanged, đúng5files trong scope thay đổi,0removed; thêm19evidencefiles mới và báo cáo này. Backend677/677 unchanged,0new/removed/modified. .idea/generated outputs không nằm trong preservation proof.

Môi trường: Java25.0.4.1, Gradle9.7.1, AGP9.4.0 giữ nguyên. JAVA_HOME=/usr/lib/jvm/java-25-openjdk-amd64 và PATH chỉ đặt cho process; java -version và ./gradlew --offline --no-daemon --version đã chạy exit0. Không cài SDK/dependency, không đổi versions/build configuration của app.

Emulator xác nhận thực tế: Pixel_6_Pro/emulator-5554 online, model sdk_gphone16k_x86_64, BOOT_COMPLETED=1, ANDROID_API_LEVEL=37. Không wipe/factory reset hoặc đọc/xóa dữ liệu cá nhân.

## 3. Root Cause E01-NET-01

P5-E01 release-like dùng actual production XML chỉ có base-config cleartext=false, nhưng API37 tự thêm permissive localhost config. Global policy false và release DEBUG=false không bảo đảm localhost deny. Actual raw OkHttp HTTP127.0.0.1 đã tới fixture trước remediation; hai assertions phát hiện đúng khoảng trống này.

Tài liệu Android xác nhận API37 có implicit localhost config nếu ứng dụng chưa khai báo localhost; cấu hình mặc định cho phép cleartext. [Android Network Security Configuration — Localhost](https://developer.android.com/privacy-and-security/security-config#Localhost_configuration).

Production RoutingTransportPolicy đã chặn HTTP độc lập; giữ nguyên defense này. Sửa platform XML để các HTTP stacks tôn trọng NetworkSecurityPolicy cũng không dùng implicit localhost allowance.

## 4. Android API37 Platform Behavior

Đã đọc local SDK sources tại /home/vantri/Android/Sdk/sources/android-37.0/android/security/net/config:

- XmlConfigSource.java: duyệt domain configs, đặt isLocalhostDefined khi Domain.isLocalhost() đúng; chỉ tạo mLocalhostConfig khi chưa có localhost definition.
- NetworkSecurityConfig.java: getLocalhostBuilder() mặc định cleartext=true.
- Domain.java: nhận localhost/ip6-localhost và numeric loopback (có xử lý bracketedIPv6).
- ApplicationConfig.java: exact/most-specific domain matching trước; fallback implicit localhost nếu tồn tại, sau đó mới default/base.

Explicit localhost deny ngăn tạo implicit config; các loopback khác không có explicit match rơi về denying base. Đây là cơ chế đọc từ source, sau đó kiểm chứng đủ host bằng runtime và socket; không giả định một domain bất kỳ có tác dụng. 10.0.2.2 là emulator host bridge, không đồng nhất với localhost/loopback.

Scope platform enforcement: Android NetworkSecurityPolicy và OkHttp HTTP stack trên device này; raw java.net.Socket không tự diễn giải HTTP/NSC. Positive raw socket probes chỉ xác nhận fixture reachable, không chứng minh/bypass routing authority. Không tuyên bố kernel chặn mọi arbitrary raw bytes của process.

## 5. Exact Production XML Changes

File production duy nhất sửa: app/src/main/res/xml/routing_network_security_config.xml.

```xml
<?xml version="1.0" encoding="utf-8"?>
<network-security-config>
    <base-config cleartextTrafficPermitted="false" />
    <!-- An explicit localhost rule prevents API 37's implicit cleartext localhost config.
         Other loopback literals/names then inherit the denying base config. -->
    <domain-config cleartextTrafficPermitted="false">
        <domain includeSubdomains="false">localhost</domain>
    </domain-config>
</network-security-config>
```

Không wildcard/subdomain allow, custom CA, fake HTTPS, endpoint change hoặc JWT change. Main manifest vẫn usesCleartextTraffic=false/networkSecurityConfig trỏ đúng XML. Debug override XML được giữ nguyên hash/mtime_ns, chỉ cho exact10.0.2.2/127.0.0.1/localhost.

Bốn file harness/docs sửa trực tiếp phục vụ kiểm chứng:

| File | Nội dung |
|---|---|
| tools/emulator-harness/src/androidTest/java/com/example/bai1/fixture/DeviceRuntimeTest.kt | Giữ nguyên18debug scenarios và5release cases/assertions; thêm actual IPv4/IPv6/name/bridge socket matrix và permission suite |
| tools/emulator-harness/run_device_tests.py | Release preinstall check chấp nhận/bắt buộc explicit deny thay vì cấm mọi domain-config; vẫn reject true; thêm denied/revoked modes và health counter evidence |
| tools/emulator-harness/host_fixture.py | Đếm static health requests để xác nhận denial/revocation không tới host |
| tools/emulator-harness/README.md | Cách tái lập và kết quả mới; giữ lịch sử evidence cũ |

Wiring vẫn copy actual production variant resources, không nạp policy hardcode khác. Driver structural assertion cũ “không domain-config” được thay bằng “explicit localhost false và không true”; đây là cập nhật wiring cho remediation, không nới các device security expectations. Runner, production consumers/strict permission/cache semantics và app dependencies không sửa.

## 6. Debug vs Release Policy

| Host | Debug runtime | Release-like runtime | Socket evidence release |
|---|---|---|---|
| localhost | permitted | denied | Controlled DNS→127.0.0.1, reachable listener, HTTP blocked |
| 127.0.0.1 | permitted | denied | Original socket case + matrix PASS |
| 127.0.0.2 | denied | denied | Bound chính127.0.0.2, probe succeeds, HTTP blocked |
| ::1 | denied | denied | BoundIPv6::1, probe succeeds, HTTP blocked |
| ip6-localhost | denied | denied | Controlled DNS→::1, probe succeeds, HTTP blocked |
| 10.0.2.2 | permitted | denied | Release actual host-health HTTP blocked; host hits0 |
| outside.invalid | denied | denied | DNS maps controlled loopback; HTTP blocked trước fixture |

Global cleartext policy false ở cả hai. Debug còn deny10.0.2.3/192.168.1.1/localhost.outside.invalid. Compiled debug resource giữ3exact allow entries, includeSubdomains=false. Compiled release có base=false+localhost=false, không true/debug allowance. BuildConfig.DEBUG=false và runtime nondebuggable xác minh trên release-like; truyền RoutingTransportPolicy(true) cũng không mở release HTTP.

Tên case lịch sử productionRoutingClientRejectsHttpEvenWhenPlatformAllowsLoopback được giữ nguyên: sau fix, platform không còn allowloopback; case vẫn kiểm independent client từ chối HTTP trước0token reads/0requests. Không diễn giải tên test như trạng thái platform hiện tại.

## 7. API37 Local Network Permission Assessment

Actual app/harness targetSdk37. Tài liệu Android nêu local network protections bắt buộc cho target37 và dùng ACCESS_LOCAL_NETWORK runtime permission, riêng với cleartext policy. [Android local network permission](https://developer.android.com/privacy-and-security/local-network-permission#android-17-enforcement).

Merged production debug **và release đều không có ACCESS_LOCAL_NETWORK**. Không thêm quyền vào release hoặc tự cấp quyền app chính. MainActivity/Auth/Firebase/SOS initialization chưa cô lập cho main application, nên không install/launch để thử permission UI.

| Harness state | System action trước instrumentation | Runtime check | Actual10.0.2.2 health |
|---|---|---|---|
| granted | pm grant đúng package harness | GRANTED | PASS,1health request |
| denied | Fresh install, không grant | DENIED | IOException,0health requests |
| revoked | Grant rồi pm revoke, đều exit0 | DENIED | IOException,0health requests |

Trong hai denied/revoked runs, NetworkSecurityPolicy vẫn permit10.0.2.2 cho debug nhưng socket thất bại: tách permission gate khỏi cleartext gate. Grant/revoke chỉ com.example.bai1.p5e01.isolated; không production account/app. Revocation được kiểm trước request trong invocation riêng, không claim đã chứng minh cancellation của connection đang mở giữa chừng.

**BLOCKER_APP_FIXTURE_PERMISSION_UI:** app chính chưa có debug-only runtime permission flow được kiểm chứng/cô lập. Chỉ thêm manifest declaration không đủ làm fixture hoạt động; cần task riêng nếu muốn mở/test app chính. Vì không thể chạy UI an toàn trong scope, không thêm declaration/request business flow và không cấp quyền main app. Đây là giới hạn app/UI, không chặn nghiệm thu shared network-policy remediation bằng harness đã cô lập.

## 8. Device Instrumentation Results

Commands đã chạy (exit0; optional --permission-state granted có cùng default behavior):

```bash
python3 tools/emulator-harness/run_device_tests.py --variant debug --evidence-dir /tmp/p5e01r-debug
python3 tools/emulator-harness/run_device_tests.py --variant releaseLike --evidence-dir /tmp/p5e01r-release
python3 tools/emulator-harness/run_device_tests.py --variant debug --permission-state denied --evidence-dir /tmp/p5e01r-denied
python3 tools/emulator-harness/run_device_tests.py --variant debug --permission-state revoked --evidence-dir /tmp/p5e01r-revoked
```

Driver build: wrapper -p tools/emulator-harness --offline --no-daemon -PpolicyVariant=<variant> assemble<Variant> assemble<Variant>AndroidTest. Mọi build/gate/install/instrumentation/cleanup cuối exit0. Exact arguments/ephemeral ports trong evidence results.json; JUnit XML từ real runner events, không đếm declarations hoặc chỉ package-name test.

Actual runner: adb -s emulator-5554 shell am instrument -w -r với DebugDeviceRuntimeTest, ReleasePolicyDeviceRuntimeTest hoặc LocalPermissionDeniedDeviceRuntimeTest, DeviceXmlListener và IsolatedInstrumentationRunner. Dùng ADB fallback đã có do offline AGP UTP blocker của P5-E01; không download UTP hoặc claim Gradle connected task PASS. Không chạy :app:connectedDebugAndroidTest trên production app chưa cô lập.

| Run | JUnit tests | Failures/errors/skipped | Driver exit |
|---|---:|---|---:|
| Debug granted | 18 | 0/0/0 | 0 |
| ReleaseLike | 6 | 0/0/0 | 0 |
| Debug denied | 2 | 0/0/0 | 0 |
| Debug revoked | 2 | 0/0/0 | 0 |

Original debug scenarios vẫn bao gồm actual host bridge, redirect không forward Bearer, shared credentials bị loại khỏi download, identity rawGZIP/SHA/parser, expiry/session invalidation/offline/error/cancellation/cacheA và strict deny. Không thay production consumer bằng mock implementation, không fake Platform NetworkSecurityPolicy hoặc qualification certificate.

## 9. Original Failure-to-PASS Evidence

Historical XML giữ nguyên: tools/emulator-harness/evidence/release/TEST-device-runtime.xml (P5-E01:5tests,2failures). New XML: tools/emulator-harness/evidence/p5e01r/release/TEST-device-runtime.xml (6tests,0failures).

| Original case | P5-E01 | P5-E01R |
|---|---|---|
| releaseLikeActualSocketHttpBlockedBeforeAnyRequest | FAIL: actual=null,receivedRequests=1 | PASS: UnknownServiceException,0requests |
| releasePolicyDeniesEveryDebugHostAtRuntime | FAIL:127.0.0.1 policytrue | PASS: toàn bộ7host policyfalse |

Đã đối chiếu testcase names: cả5original release cases có trong XML mới và PASS; chỉ thêm1case actual socket matrix. Các original methods và expected-false assertions giữ nguyên; không @Ignore/skip/remove scenario. Thay đổi production XML là nguyên nhân remediation; không đổi DebugConfig/HTTP client để giả lập denial.

Baseline failure được tái hiện ở P5-E01 và giữ evidence nguyên hash/mtime; không chủ ý đưa production XML về bản lỗi chỉ để tạo failure mới trong task này. Runtime source hiện tại đã kiểm trực tiếp sau sửa.

## 10. Actual Loopback Socket Tests

New releaseActualSocketsDenyIpv4Ipv6AndLocalhostNames bind owned listeners trên127.0.0.1,127.0.0.2 và::1. Mỗi listener được positive probe bằng rawSocket, đọc HTTP200 và xác nhận1request; sau đó reset probe counter và dùng real OkHttp HTTP request với policy hiện tại. Kết quả mọi host: UnknownServiceException,0HTTP requests sau probe. Vì fixture reachable đã chứng minh, denial không bị nhầm với connection-refused/offline.

localhost alias dùng controlled DNS→127.0.0.1, ip6-localhost→::1, không phụ thuộc external resolver. IPv6 bind/connect/read thực hiện thành công: **IPV6_SOCKET_VERIFIED=true** cho::1 và alias có controlled resolution, không suy mọi IPv6interface hoặc default DNS mapping đã kiểm. Release host bridge HTTP10.0.2.2 cũng reject với UnknownServiceException trước health handler; debug cùng host fixture hoạt động khi có permission.

Nonlocal negative hostname cũng map về local fixture; không gọi Internet. RawSocket positive probe chỉ fixture test, không production transport bypass. No JWT trong probe và downloader không nhận production tokens.

## 11. Compiled/Merged Release Policy

App commands (exit0, BUILD SUCCESSFUL):

```bash
./gradlew --offline --no-daemon :app:generateReleaseBuildConfig \
  :app:processReleaseManifest :app:processReleaseResources
```

Kiểm tra actual merged outputs:

- app/build/intermediates/merged_manifests/debug/processDebugManifest/AndroidManifest.xml
- app/build/intermediates/merged_manifests/release/processReleaseManifest/AndroidManifest.xml
- app/build/generated/source/buildConfig/release/com/example/bai1/BuildConfig.java

Cả hai app manifest: target37, usesCleartextTraffic=false, networkSecurityConfig đúng reference; release không debuggable, BuildConfig.DEBUG=false; không local-network permission ở app release/debug. Không dump/copy production Maps keys hoặc unrelated cloud metadata vào evidence.

Dùng aapt2 dump resources để tìm actual compiled path, rồi aapt2 dump xmltree cho debug APK và release linked-resources-binary-format-release.ap_. Release XML: base=false, explicitlocalhost=false/includeSubdomains=false, không true. Debug XML: base=false,3exact local allows. Cùng kiểm tra với target harness APK trước install; generated policy inputs copy từ actual app variant source. Release resource optimizer có thể rename path nên không đoán ZIP entry.

Production-platform-config.json archived chứa relevant flags và compiled XML, không credentials. Runtime release-like nondebuggable dùng cùng policy đã PASS; full production release app không install hoặc launch. PLATFORM_ENFORCEMENT=PASS_TESTED_API37_SHARED_POLICY, không claim mọi API/device hoặc toàn app E2E.

## 12. Full Android JVM Results

Targeted fresh command:

```bash
./gradlew --offline --no-daemon :app:testDebugUnitTest --rerun-tasks \
  --tests '*RoutingNetworkSecurityTest' --tests '*GraphCacheSecurityTest' \
  --tests '*GraphDownloaderTest' --tests '*P5A01ConsumerAuditTest'
```

Exit0: **90tests/4suites**,0fail/errors/skips, thực sự rerun compile/test. Sau đó:

```bash
./gradlew --offline --no-daemon :app:testDebugUnitTest --tests 'com.example.bai1.rescue.routing.*'
./gradlew --offline --no-daemon :app:testDebugUnitTest
```

Routing exit0: **462tests/13suites**,0fail/errors/skips. Full exit0: **503tests/26suites**,0fail/errors/skips. Counts đọc từ JUnit XML sau từng phase, không giả định503 hoặc cộng overlap. Không thêm/bỏ JVM case trong task; các additions thuộc instrumentation.

F01–F05/F07/F08 invariants regression vẫn đạt. UCS/RescueRoutingContext strict lock, routing epoch/session invalidation, GZIP digest/binding, atomic publication/path containment và offline fail-closed không sửa. Existing DerivedStateFlow/StateFlow opt-in warning vẫn là warning cũ; không refactor hoặc nâng dependency để xử lý ngoài scope.

Logs /tmp/p5e01r-{fresh,routing,full}.log; XML snapshots /tmp/p5e01r-{fresh,routing,full}-results. Actual full XML/HTML vẫn tại app/build/test-results/testDebugUnitTest và app/build/reports/tests/testDebugUnitTest/index.html. Summary archived evidence/p5e01r/jvm-build-results.json.

## 13. APK Build Results

```bash
./gradlew --offline --no-daemon :app:assembleDebug
```

Exit0, BUILD SUCCESSFUL. App APK chỉ build, không install/launch:

`/home/vantri/AndroidStudioProjects/bai1/app/build/outputs/apk/debug/app-debug.apk`; 96,434,434 bytes; SHA-256 `b906a76df36f4b09fcfc7861ffe885dc68694131df22b9e9e8faace616e524dd`.

Isolated target APKs đã chạy và gỡ owned namespaces:

| Variant | Bytes | SHA-256 |
|---|---:|---|
| debug | 5753682 | `35138dc2d53b5033f8199948aaf73f5924a99c0277968e65edbea8aaa4f6a512` |
| release | 4640952 | `cfc8655086f1ae7d74923a96c8efef4c41cc6ec30e7770e37bcd2a3532441f56` |

Không build/sign/publish production release APK. ReleaseLike chỉ local test signing đã có, nondebuggable runtime. Không deploy. Logs /tmp/p5e01r-apk.log và /tmp/p5e01r-release-policy.log.

## 14. Isolated Harness Assurance

Trước mọi install: kiểm package/test namespace absent, targetmanifest bare android.app.Application + FixtureActivity không exported, allowBackup=false, không providers/services/receivers/metadata hoặc location/camera. DEX audit và runtime reflection xác nhận Firebase/GMS/AuthRepository/production MainActivity/SOS client/RoutingAppDependencies absent. Chỉ production routing directories và extracted token interface được compile; không cloud/account configuration.

Các fixture chỉ bind device loopback hoặc host127.0.0.1 ephemeral port cho10.0.2.2 bridge; không external network, real Backend/R2/Firebase/SOS hoặc production JWT. Không normal app startup/SDK providers. Test-only permission modes bị giới hạn debug, packagefixednamespace; không tạo production bypass flag.

Logcat chỉ --pid của owned target, từ device timestamp ngay trước instrumentation; runner chờ attach trước tests. Không clear/read logcat app khác. Sau scan không thấy FATAL EXCEPTION/ANR markers hoặc token/raw-presigned-signature markers trong window đã thu; không claim system-wide ANR/traffic certification. Chi tiết:

- debug: PID23371; start 11-07 01:06:32.000; 126log lines; owned packages remaining=[]; health hits=1.

- release: PID25562; start 11-07 01:09:33.000; 52log lines; owned packages remaining=[]; health hits=0.

- denied: PID23895; start 11-07 01:07:07.000; 42log lines; owned packages remaining=[]; health hits=0.

- revoked: PID24188; start 11-07 01:07:24.000; 42log lines; owned packages remaining=[]; health hits=0.

Driver finally uninstall chỉ target/test packages đã chứng minh absent trước install, không -r/clear-data/wipe hoặc user cache. Runtime test cache root riêng được cleanup; emulator data/app khác giữ nguyên. Full app UI/logout/account restore không được suy PASS từ harness labels hoặc routing repository tests.

## 15. Backend/Graph Artifact Preservation

Backend677/677 baselinefiles nguyên SHA-256/mtime_ns,0new/modified/removed, gồm20 JSON/GZIP graph/manifest artifacts trong năm outputdirectories. Không sửa service/controller/storage/qualification/fixtures outputs. Android244/249 baselinefiles nguyên fingerprint,5modified trong scope,0removed;20newfiles chỉ report+evidence.

Snapshots /tmp/p5e01r-{android,backend}-baseline.json và final/comparison counterparts. Git dirty/untracked từ trước được giữ; git diff --check cuối exit0. Old P5-E01/NET reports và historical failure evidence giữ nguyên. Exclude generated/IDE/dependencytrees đã nêu, không claim chúng unchanged.

Before/after fingerprints của5modified files:

| File | Before SHA-256 / mtime_ns | After SHA-256 / mtime_ns |
|---|---|---|
| `app/src/main/res/xml/routing_network_security_config.xml` | `dea35ebed76536c24c13f74036f02dc874f944147cb3a49c2ad0550f71e3e0c7 / 1793984051287230925` | `b441f04a9f8fba260e1986af77263d07c34df2ef5f27fe94b2ef17aaf2bc6324 / 1793988327996317134` |
| `tools/emulator-harness/README.md` | `7e607fdea3efc104d8c45ccf83aed46901648c762205d65b2401034ddc99a56d / 1793987669352290069` | `74482b4dd8e13b7d07024b120d58f0a37eb2d288046c2a2deb65874695755952 / 1793988454072224623` |
| `tools/emulator-harness/run_device_tests.py` | `2cd4eb5aaa0c847d51bd6018f6318d3d88fae199bedd56d453af99a02a6c48d2 / 1793987558427874675` | `c03c0ab0642c9f9caf71c36eb658877b86e7c99838f271f13d4423782364a2d5 / 1793988328020670319` |
| `tools/emulator-harness/host_fixture.py` | `b1bbfdb99b10c7b7cde9d9f2d4e47edd3a4eca825a22c83aa5b45086fd8bfc31 / 1793986608095145969` | `0e43d2753e41894bdf0c0d0ac2a2d39c941a22341cbba75a404fbe07f6415a67 / 1793988328020838306` |
| `tools/emulator-harness/src/androidTest/java/com/example/bai1/fixture/DeviceRuntimeTest.kt` | `3b26c6c19e06b6c2d27650fec25e9b6f43f5057291333f4917590f1f942d8e2d / 1793987394168937827` | `3e3452b4cc81b6b1c288d66b9b316d1e556bace2440a725ffa2326bee967bb69 / 1793988554459386255` |

New files: báo cáo này và19files dưới tools/emulator-harness/evidence/p5e01r:4variant/state directories (debug/release/denied/revoked), mỗi directory có TEST-device-runtime.xml/results.json/compiled-policy.txt/owned-process-logcat.txt; cùng device-driver-exit-codes.json, jvm-build-results.json, production-platform-config.json. Không overwrite evidence P5-E01.

## 16. Remaining F06 and Application UI Gaps

F06 OPEN: chưa có authoritative live hazard/field freshness hoặc production qualification. Digest, READY/VERIFIED_COMPATIBLE, ATTACHED hoặc fixture không cấp cứu hộ authority. Strict routing tiếp tục DISABLED.

Full application UI/Auth/Firebase/SOS/Maps/network demo chưa cô lập để launch; ROUTING_UI=NOT_IMPLEMENTED; main-app debug local-network permission request là blocker riêng ở mục7. Không thực hiện parity/Kotlin–TypeScript, Android–Backend full E2E, P5-P01/P5-I02 hoặc UI mới. Không dùng platform policy PASS để tuyên bố đường ngoài thực địa an toàn.

Chỉ API37 Pixel_6_Pro/x86_64 đã kiểm. Không physical device/olderAPI matrix, real TLS/R2 credentials, all-IPv6 routes/default DNS aliases hoặc kernel-wide cleartext filter assurance. Cache power-loss/sameUID race/retained-generation quota/RAM-latency limitations của tasks trước không được giải quyết hoặc báo PASS trong task này.

## 17. Final Acceptance

**PASS — P5-E01R/E01-NET-01 remediation trong tested scope.** Actual production XML deny làm cả hai release failures chuyểnPASS; release6/6,debug18/18,permissiondenied2/2,revoked2/2; freshsecurity90/routing462/full503 và APK/release resources đều PASS. IPv6 socket được thực thi thật. Release no debug allowance; independent RoutingTransportPolicy vẫn fail-closed trước token read. Mọi original security assertions được giữ.

Backend/artifacts và source ngoài scope bảo toàn; không production cloud/SOS traffic path trong harness, không install/launch app chính hoặc cấp quyền cho nó. No production-authoritative qualification => STRICT_ROUTING_DISABLED.

E01-NET-01=CLOSED_FOR_TESTED_API37_SHARED_POLICY. Full production app E2E và permission/UI flow=NOT_RUN/BLOCKED_APP_SCOPE như đã ghi; không là release deployment/device-fleet certification. Không commit/push/deploy.

## 18. Recommended Next Task

Task riêng để cô lập main app startup/Auth/Firebase/SOS/Maps và triển khai/kiểm chứng debug-only API37 runtime local-network permission flow trước UI/fixture demo. Nếu cần automated Gradle connected runner, xử lý cached UTP tooling riêng khi được phép, không download trong task này.

Sau đó có thể mở rộng API/device matrix theo yêu cầu; F06 cần contract/authority task riêng trước mọi strict activation. Chỉ đề xuất, chưa bắt đầu các task đó. Dừng đúng P5-E01R, không tự làm parity, routing UI, full E2E, F06 hoặc deploy.
