# P5-E10-M01 — Google Maps SDK integration

Ngày09/11/2026, Asia/Bangkok; demo dự kiến10/11/2026.

**BLOCKED tại phase B — chưa có API key được phép dùng cho package/certificate demo.** Đã audit production/E09/E10A, build APK cấu hình riêng, kiểm manifest/dependencies/DEX và lấy SHA-1 từ APK thật. Chưa cài APK/chạy SDK trên OPPO. Không có bằng chứng tiles hoặc markers Google Maps; không dùng Canvas thay thế.

## 1. Trả lời10câu hỏi nghiệm thu

| Câu hỏi | Kết quả thực tế |
|---|---|
| Maps SDK đã tích hợp chưa? | App production đã khai báo Maps SDK19.0.0 + Maps Compose4.4.1 và dùng GoogleMap. Module mới link/đóng gói đúng hai dependency này; mới là APK cấu hình, chưa có GoogleMap demo screen |
| Maps thật hiển thị trên OPPO chưa? | NOT_RUN; chưa khởi tạo SDK/MapView/GoogleMap trên thiết bị |
| Đã có key hợp lệ cho demo chưa? | BLOCKED: không có key được xác nhận cho applicationId/SHA-1 mới. Production local.properties có giá trị key, không sao chép/sử dụng |
| Có lỗi restrictions/billing/key không? | UNKNOWN/NOT_TESTED, chưa có SDK authorization request/Cloud access; không suy diễn lỗi hoặc khẳng định billing đã bật |
| Rescuer marker hiển thị chưa? | NOT_RUN; tọa độ yêu cầu16.0500,108.2020 chưa được render bởi Maps |
| SOS marker hiển thị chưa? | NOT_RUN; tọa độ yêu cầu16.0510,108.2030 chưa được render bởi Maps |
| Có real GPS không? | Không gọi GPS/FusedLocationProvider, không location permissions; không install/run app |
| Cô lập Firebase/production Backend chưa? | Static configuration artifact gatePASS: không import main app/auth/SOS/rescue/DI/routing, không Firebase SDK/startup. Maps runtime isolation còn NOT_RUN |
| Bao nhiêu physical tests PASS? | **0 executed,0 PASS**; cả M01-T01…T15 NOT_RUN do stop rule phaseB. Preflight USB/GMS không tính là Maps physical PASS |
| Có APK/lệnh mở demo không? | Có APK CONFIGURATION ONLY thật, signed/hashed. Chưa có APK Google Maps demo đủ điều kiện hoặc startup Maps; script chỉ in BLOCKED/tuple và không install |

## 2. Audit actual source

Đã đọc DEMO_MAP_ALGORITHM_RESCUE_REPORT.md, P5_E10A_SOS_MAP_REPORT.md và p5e10a/RUNBOOK_USB.md. Audit app/build.gradle.kts, gradle/libs.versions.toml, MainActivity, AppNavGraph, ProductionAppNavScreens, DisasterMapScreen/ViewModel, map Composables, main/debug manifests/network policy và E10A harness.

- Production dependencies: `com.google.maps.android:maps-compose:4.4.1`, `com.google.android.gms:play-services-maps:19.0.0`; cả AAR có sẵn trong host Gradle cache. Không đoán phiên bản, không nâng Gradle/AGP/Kotlin/Maps của app.
- Production Map dùng GoogleMap thật, camera DaNang16.0544,108.2022/zoom12; Marker từ ViewModel, onMapLoaded cập nhật flag. Không dùng MapMockupCanvas để chứng minh SDK.
- Screen mặc định khởi tạo AndroidLocationProvider, location permission launcher, có MyLocation theo permission; factory dùng SosAppDependencies và RescueMissionAppDependencies. ViewModel observe SOS, REST active/nearby mỗi4s. Không thể tái sử dụng nguyên screen trong scope M01 mà vẫn cô lập các dependencies này.
- MainActivity cast Application→MainAppHost/initialize và actual AppNavGraph map route được auth guard bảo vệ. Không đóng gói các startup/navigation này vào module cấu hình.
- Main manifest có DisasterApplication, camera/location permissions, FileProvider và API key metadata `${MAPS_API_KEY}`. Main Gradle đọc key từ local.properties, dùng manifestPlaceholders; có secrets plugin. Production key value không được in/copy vào source mới/APK/report/log.
- Cached sources Maps Compose4.4.1 xác nhận GoogleMap tự tạo MapView và có onMapLoaded; MapProperties.isMyLocationEnabled mặc địnhfalse. Vì vậy minimal GoogleMap độc lập là phương án khả thi về cấu trúc, nhưng **chưa được triển khai/chạy** sau barrier key.
- Root/home đều trên2GB trước build; Java25, Gradle9.7.1, AGP9.4.0, Kotlin plugin2.2.10 giữ nguyên. [Tool versions](gmaps01/evidence/tool-versions.log).

## 3. OPPO preflight

Physical USB authorized OPPO CPH2159, Android13/API33, boot1, không emulator. Serial chọn động/redacted. Google Play Services package installed/enabled; dumpsys có bản cập nhật26.43.30 và bản system23.37.17. Đây là kiểm tra prerequisites, **không chứng minh Maps renderer tương thích hay tiles tải thành công**. Không kiểm tài khoản/dữ liệu cá nhân/không thay settings/permissions. [Environment](gmaps01/evidence/environment.json).

## 4. Package và signing certificate cần đăng ký

```text
Application ID: com.example.bai1.p5e10maps.demo
Certificate SHA-1: E4:EA:13:1A:81:21:D3:34:86:B8:3B:EA:64:F1:A4:1C:68:F0:80:07
```

SHA-1 lấy bằng Android SDK apksigner verify --print-certs từ chính APK mới đã ký, không đoán từ keystore hoặc APK E09/E10A. [Certificate verification](gmaps01/evidence/signing-certificate.txt).

Key demo riêng chưa được cung cấp/xác nhận; không có GMAPS01_API_KEY environment. Không truy cập Cloud project/billing/key management, không tạo key hoặc thay restrictions. Key production trong local.properties không được sử dụng. Không thể biết package restrictions/billing/quota chỉ từ giá trị key local.

**Áp dụng yêu cầu mục V:** “Nếu key chưa cho phép package/SHA-1 demo: 1. Dừng ở bước cấu hình.” Vì chưa có key được phép dùng cho tuple trên, dừng sau cấu hình, chưa phaseC–E. Không ghi nhận authorization failure giả hoặc dùng key production để thử may rủi.

Chủ tài khoản cần chọn key được phép dùng, bật Maps SDK for Android/billing/quota phù hợp, cấu hình Android app restriction cho tuple trên và API restriction phù hợp; giữ nguyên mọi restriction production. Không đưa key lên chat/Git/report. [Hướng dẫn key/restrictions chính thức](https://developers.google.com/maps/documentation/android-sdk/get-api-key), [Cloud setup](https://developers.google.com/maps/documentation/android-sdk/cloud-setup).

## 5. APK cấu hình và build

New standalone module `tools/google-maps-harness`, package riêng; không sửa module/source/config cũ. ConfigurationActivity Java chỉ ghi BLOCKED/không có tiles và F06/strict lock; **không gọi GoogleMap/MapView/SDK init**. API key metadata cố ý vắng mặt. Không chứa Canvas/ảnh bản đồ hoặc marker giả. Artifact này phục vụ đăng ký certificate, không phải demo Maps đã hoạt động.

Giữ Maps4.4.1/19.0.0 và toolchain cũ. Build offline ban đầu thiếu một số transitive version; đã căn riêng module mới theo26version có artifact thật trong cache, cộng Guava31.1-android đã dùng ở E10A để cung cấp ListenableFuture. Không sửa dependency global/main/E10A và không tải package mới. [Cache alignment](gmaps01/evidence/cache-alignment.json), [dependency graph](gmaps01/evidence/dependency-graph.txt). Hai build logs lỗi giữ nguyên để đối chiếu; [final build exit0](gmaps01/evidence/build-final.log).

Artifact: [gmaps01-configuration.apk](gmaps01/artifacts/gmaps01-configuration.apk), [SHA256SUMS](gmaps01/artifacts/SHA256SUMS). Lệnh kiểm một bước, **exit2 có chủ đích vì BLOCKED**:

```bash
cd /home/vantri/AndroidStudioProjects/bai1
python3 tools/google-maps-harness/configuration_status.py
```

[Runbook USB/key registration/build/recovery](gmaps01/RUNBOOK_USB.md). Chưa xuất lệnh mở Maps giả: ConfigurationActivity không phải GoogleMap demo. Sau khi chủ tài khoản cấu hình key được phép dùng, tiếp tục phaseC–E trong M01; chưa chuyển M02.

## 6. Isolation policy và static gate

Cho phép chính xác Maps SDK/Compose và transitive GMS Maps/base/basement/tasks, AndroidX/Kotlin/Guava hạ tầng. Khi runtime được phép, Maps SDK network phục vụ map tiles sẽ được chấp nhận; không giữ quy tắc cấm toàn bộ GMS của E10A. Google Maps dynamic modules/runtime traffic chưa kiểm ở bước này.

Gate APK cấu hình PASS:

- Own namespace, default Application, allowBackupfalse/no sharedUID; INTERNET, ACCESS_NETWORK_STATE và AndroidX own signature permission. Không location/camera/microphone/contacts/SMS/notification permissions.
- Một own launcher exported và GoogleApiActivity nonexported của GMS; không providers/receivers/services, startup metadata chỉ GMS version. Không API key metadata/FirebaseInitProvider/production Application.
- DEX có MapView và GoogleMapKt thật; không Firebase Auth/Database SDK, saved-credential SDK, FusedLocationProviderClient hoặc app auth/SOS/rescue/routing classes.
- Dependency graph không có com.google.firebase modules. **GMS basement18.9.0 tự chứa3compatibility exceptions trong namespace com.google.firebase:** FirebaseException, FirebaseApiNotAvailableException, FirebaseExceptionMapper. Đã xác minh trực tiếp classes.jar của AAR; chỉ allow3type này, không coi chúng là Firebase Auth/Database/startup. Không dùng blanket allow cho Firebase.
- Quét tất cả APK zip-entry bytes cho production key values trong UTF8/UTF16,0matches; giá trị chỉ kiểm in-memory, không xuất vào evidence. Không secrets/google-services plugin hoặc main source imports trong module mới.
- NSC cleartextfalse, explicitlocalhostfalse cho API37; không local fixture/reverse trong Maps stage này. Chưa áp hạn chế domain đoán mò có thể làm hỏng Maps; runtime network audit sẽ đi với phaseE.

[Configuration gate](gmaps01/evidence/configuration-gate.json), [merged manifest](gmaps01/evidence/configuration-manifest.xml), [compiled policy](gmaps01/evidence/compiled-policy.txt). Đây là **PASS_CONFIGURATION_ONLY**, không phải security/runtime acceptance của Google Maps.

## 7. Tests/evidence và preservation

M01-T01…T15 đều NOT_RUN; số physical Maps tests executed/PASS=0. [Device results](gmaps01/evidence/device-results.json). Không instrumentation execution nên không tạo JUnit XML hoặc ownPID logcat/screenshot giả; đây là deliverables chưa có do phaseB stop. Không tính JVM/build/preflight hay15Canvas tests E10A thành Maps physical PASS. Khi unblocked, tiles cần onMapLoaded **và kiểm trực quan trên OPPO**; onMapReady/marker-only/nền trống không đủ. [Callback chính thức](https://developers.google.com/android/reference/com/google/android/gms/maps/GoogleMap.OnMapLoadedCallback).

Không cài/mở/gỡ APK nào trong M01; không adb reverse, không đọc GPS hoặc dữ liệu cá nhân, không sửa Cloud/Backend. Existing Android files/E09/E10A artifacts và toàn bộ Backend giữ nguyên SHA256+mtime, cả graph artifacts. Inheriteddirty giữ nguyên; gitdiffcheck0; không reset/clean/commit/push/deploy. [Preservation và file mới](gmaps01/evidence/preservation.json).

File mới: tools/google-maps-harness build/settings/properties/.gitignore, ConfigurationActivity.java, manifest/NSC, audit_configuration.py, configuration_status.py, finalize_evidence.py; report này và docs/demo/gmaps01 (runbook/artifacts/evidence). [Evidence index](gmaps01/evidence/evidence-index.json).

## 8. Final status và stop

```text
GOOGLE_MAPS_DEPENDENCY = PASS_CACHED_AND_LINKED
GOOGLE_MAPS_API_KEY = BLOCKED_NO_AUTHORIZED_DEMO_KEY
GOOGLE_MAPS_TILE_RENDER = NOT_RUN
GOOGLE_MAPS_RESRCUER_MARKER = NOT_RUN
GOOGLE_MAPS_SOS_MARKER = NOT_RUN
GOOGLE_MAPS_PHYSICAL_USB = NOT_RUN (0 executed / 0 PASS)
GOOGLE_MAPS_DEMO_READY = BLOCKED
GOOGLE_MAPS_DEMO_SCREEN = NOT_IMPLEMENTED_PHASE_B_STOP
STATIC_CONFIGURATION_GATE = PASS
MAPS_RUNTIME_SECURITY_GATE = NOT_RUN

UCS_COMPUTATION = NOT_IN_SCOPE
UCS_POLYLINE = NOT_IN_SCOPE
MISSION_ACCEPTANCE = NOT_IN_SCOPE
F06 = OPEN
STRICT_ROUTING_DISABLED = true
```

Không có claim Google Maps PASS/CONDITIONAL khi SDK chưa chạy. Dừng đúng phaseB chờ key/tuple được chủ tài khoản cho phép. Không tự làm M02, UCS/polyline/mission/GPS/SOS production hay mở strict routing.
