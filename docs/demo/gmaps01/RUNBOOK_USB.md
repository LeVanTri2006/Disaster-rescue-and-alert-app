# P5-E10-M01 — Google Maps thật trên OPPO USB

Namespace `com.example.bai1.p5e10maps.demo`; SDK19.0.0/Compose4.4.1. GoogleMap thật, hai marker static TEST. Không production login/Firebase/Backend/SOS, không GPS/UCS/Polyline/mission. E09/E10A giữ nguyên.

## Mở demo bằng một lệnh

```bash
cd /home/vantri/AndroidStudioProjects/bai1
/home/vantri/Android/Sdk/platform-tools/adb devices -l
(cd docs/demo/gmaps01/artifacts && sha256sum -c SHA256SUMS)
python3 tools/google-maps-harness/demo_session.py --install --keep-demo
```

Chỉ dùng đúng OPPO physical USB authorized, màn hình mở khóa. Giữ cáp USB; điện thoại cần truy cập mạng của Google Maps. Maps demo **không cần adb reverse/local fixture**. Script kiểm SHA, chỉ install-t khi package chưa tồn tại; nếu đã có, chỉ mở khi SHA APK đang cài trùng artifact. Không cài đè/gỡ app khác SHA. Không đụng main/E09/E10A.

`--keep-demo` giữ app mới trên điện thoại sau Ctrl+C; không có cờ này thì script gỡ đúng app nó vừa cài. Package có sẵn trùng SHA luôn được giữ. Key không xuất ra console/log. Script chỉ thu ownPID logs đã redaction và nhận onMapLoaded, không tự gọi callback.

APK dùng trình bày: **gmaps01-debug.apk**. `gmaps01-configuration.apk` là artifact phaseB lịch sử, không có map. `gmaps01-negative.apk` chỉ dành kiểm thử key giả/lỗi API; không dùng trình bày. APK chứa key theo cơ chế Maps SDK nên các APK được Git ignore, không commit.

## Thao tác trên OPPO

1. Thấy GOOGLE MAPS DEMO, DỮ LIỆU VỊ TRÍ MÔ PHỎNG, F06 OPEN/STRICT_ROUTING_DISABLED=true.
2. Chờ SDK: LOADED_CALLBACK **và quan sát tiles thật**: đường phố, nhãn/POI ở Đà Nẵng, logo Google. Callback đơn lẻ/nền trắng không đủ.
3. Pin xanh **RESCUER TEST** tại16.0500,108.2020; pin đỏ **SOS TEST** tại16.0510,108.2030. Bấm từng pin: native info window và footer hiển thị đúng nhãn/tọa độ.
4. Bấm +/− hoặc double tap để zoom; kéo trong map để pan. Đây chỉ là thao tác bản đồ, không tracking/dẫn đường.
5. Không bật My Location; app không xin location permissions. Không nhập dữ liệu cá nhân. Các POI/biểu tượng nền là tiles Google, không thêm SOS thật.

Camera khởi đầu ở TEST16.0505,108.2025/zoom15.5; dùng zoom/pan để xem lại hai pin. Recreation đã được kiểm không crash/tải lại SDK, không dùng như lệnh reset camera. Không có route polyline/nút nhận nhiệm vụ.

## Key và build

Người dùng đã xác nhận key hiện có được phép dùng, Application restrictions=None, API restrictions có Maps SDK for Android. Harness không sửa Cloud/restrictions/billing.

Build mặc định dùng Java Properties.load từ **Android root local.properties**, lấy MAPS_API_KEY hiệu lực; nếu trùng tên, khai báo cuối ghi đè. GMAPS01_API_KEY environment override được ưu tiên nếu được đặt. Không hardcode key, không dùng Gradle -P chứa key, không gửi key vào chat/Git.

```bash
df -h / /home
export JAVA_HOME=/usr/lib/jvm/java-25-openjdk-amd64
export ANDROID_HOME=/home/vantri/Android/Sdk
export PATH="$JAVA_HOME/bin:$PATH"
./gradlew -p tools/google-maps-harness --offline --no-daemon assembleDebug assembleDebugAndroidTest assembleNegative
./gradlew -p tools/google-maps-harness --offline --no-daemon -PnegativeTests=true assembleNegativeAndroidTest
python3 tools/google-maps-harness/gate_runtime.py
```

Root phải còn ít nhất2GB. Không dùng --debug/--info hay dump merged manifest nguyên bản ra terminal: build intermediates có key. `gate_runtime.py` chỉ xuất manifest redacted. Dùng `runtime_common.log_command` nếu ghi build logs tự động để lọc key trước khi lưu.

Package: `com.example.bai1.p5e10maps.demo`.
Certificate SHA-1: `E4:EA:13:1A:81:21:D3:34:86:B8:3B:EA:64:F1:A4:1C:68:F0:80:07`.

Restriction hiện tại do người dùng xác nhận; agent không nới/chỉnh Cloud. Về lâu dài, chủ project có thể cấu hình Android application/API restrictions cho tuple phù hợp; không thay entry production tự động.

## Chạy lại instrumentation

```bash
python3 tools/google-maps-harness/run_maps_tests.py --evidence-dir docs/demo/gmaps01/evidence/runtime/rehearsal-new
```

Evidence dir phải mới. Runner từ chối namespace đã có, kể cả APK demo đang giữ để trình bày. Không chạy khi cần giữ app đang mở; chỉ gỡ app owned theo quyết định của người chạy. Test dùng main/test namespace và biến thể `.negative`/`.negative.test`, tất cả phải vắng mặt trước install. Không-r, không emulator, không thay mạng/settings/GPS cá nhân. Runner tự gỡ các package vừa cài sau test; SDK negative dùng key giả hardcoded nonsensitive và không ghi đè main.

JUnit XML xuất từ actual RunListener, screenshots chụp chỉ khi đúng owned app foreground, logs theo ownPID/time window, key redacted trước khi ghi. SDK callback và ảnh được kiểm riêng để xác nhận tiles. Không dùng unit/JVM tests thay physical Maps.

## Nếu trắng/lỗi/timeout

SDK_READY chỉ có nghĩa MapView/GoogleMap đã khởi tạo. Sau30s chưa onMapLoaded, UI hiện ERROR_OR_TIMEOUT, không vẽ tiles/marker giả. Đây không tự chứng minh nguyên nhân billing/quota.

Kiểm redacted ownPID log và trạng thái SDK; nếu có Authorization failure, kiểm key nguồn, API enabled, package/SHA và quyền sử dụng. Không in key. Kiểm kết nối điện thoại bằng người dùng, không tự đổi Wi-Fi/mobile/settings. Chỉ kết luận billing/quota nếu có lỗi runtime cụ thể hỗ trợ; tiles đã render chỉ chứng minh phiên runtime được phục vụ, không xác minh toàn bộ cấu hình/quota Cloud.

Có thể thấy GMS Phenotype/GoogleApiManager DEVELOPER_ERROR nội bộ dù Maps vẫn tải được; phân biệt với lỗi Maps authorization và crash thực. Không reset Google Play Services hoặc xóa dữ liệu điện thoại.

## Kết thúc

Ctrl+C dừng log session. App mới chỉ được giữ khi có --keep-demo; app giữ trên điện thoại có thể đóng bằng Back/Home như ứng dụng bình thường. FLAG_KEEP_SCREEN_ON chỉ hiệu lực lúc Activity demo foreground, không đổi setting hệ thống. Không global logcatclear/permission reset/uninstall production/clear dữ liệu app khác. F06 OPEN, strict routing vẫn DISABLED; chỉ M01.
