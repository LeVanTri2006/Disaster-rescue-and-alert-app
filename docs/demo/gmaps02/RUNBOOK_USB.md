# M02 — Google Maps + shared SOS fixture trên OPPO USB

Standalone package `com.example.bai1.p5e10m02.demo`. APK `artifacts/gmaps02-debug.apk`. Maps SDK19.0.0 / Maps Compose4.4.1. Không cài đè M01/E10A/E09/main, không GPS/dispatch/Firebase/UCS/mission.

## Mở bằng một lệnh

```bash
cd /home/vantri/AndroidStudioProjects/bai1
python3 tools/google-sos-map-harness/demo_session.py --install --keep-demo
```

Mở khóa OPPO CPH2159, cắm USB authorized, giữ terminal và cáp trong cả phiên; điện thoại cần mạng để Google Maps tải tiles. Script chọn đúng physical USB, kiểm SHA256SUMS, chỉ install-t nếu package chưa có. Package có sẵn phải trùng SHA artifact; khác SHA thì dừng, không tự gỡ/ghi đè. Script mở fixture127.0.0.1:cổng OS cấp và đúng một reverse mapping của phiên.

READY_LOGIN nghĩa là Activity đã mở tại màn đăng nhập, chưa chứng minh tiles. Chọn cứu hộ, chờ LOADED_CALLBACK **và quan sát tiles đường phố/nhãn/logo Google**. Không coi callback đơn lẻ là tiles PASS.

Ctrl+C kết thúc fixture và chỉ xóa reverse của phiên. `--keep-demo` giữ APK mới trên phone; không có cờ này thì chỉ gỡ APK do script vừa cài. APK có sẵn trùng SHA luôn được giữ. Store RAM hết khi kết thúc fixture. Logout/recreation không xóa server state khi fixture còn chạy. Không chạy instrumentation song song với phiên trình bày.

## Hai vai trò / nhiều SOS

1. Người dân TEST:1 → Người dân demo → SOS synthetic. Giữ mô tả bắt đầu SYNTHETIC_TEST_DATA, không nhập thông tin cá nhân. Chọn B16.0510,108.2030 hoặc H16.0520,108.2030; Thêm người nếu muốn.
2. HTTP OK → Gửi SOS fixture → FIXTURE_STORED UUID status=ACTIVE. Đây là UUID server trả về; chưa tạo marker local.
3. Đăng xuất → Đội cứu hộ demo. Trước Refresh không có SOS pin. Refresh SOS → GET_OK1SOS; pin đỏ từ GET. Pin xanh RESCUER TEST16.0500,108.2020 là vị trí mô phỏng cố định.
4. Bấm pin đỏ: native info window + panel UUID/status/count/description/lat/lon. Panel bên dưới cuộn được; native info window có thể rút gọn chuỗi dài, panel giữ đầy đủ.
5. Đăng xuất, chọn Người dân TEST2, gửi tại H; đăng xuất/cứu hộ/refresh thấy hai UUID. Một ACTIVE/account, gửi lặp trả409. Refresh không POST hay thêm trùng.
6. Hai SOS cùng B vẫn giữ đúng LatLng; cuộn danh sách nút SOS UUID để chọn từng bản ghi và hiện info window, không dịch tọa độ.

## Lỗi / lifecycle

403/500/Offline/Timeout là fault test-only trên business HTTP tới fixture, không tắt mạng/settings điện thoại hoặc Maps SDK. Chọn HTTP OK để phục hồi. GET_ERROR xóa SOS markers, không báo đồng bộ thành công với dữ liệu cũ. Dữ liệu server còn nguyên.

Timeout thông thường fixture lỗi trước commit. **Timeout sau commit** cố ý lưu record rồi trì hoãn phản hồi: client báo lỗi, không biết chắc kết quả, không tự retry; đăng xuất/cứu hộ/HTTP OK/refresh để đối chiếu. Không bấm gửi lại mù quáng và không tuyên bố timeout rollback SOS. Đây không thêm idempotency API production.

Logout xóa token/role/form/marker/selection/client ID; server vẫn giữ SOS. Activity recreation hoặc restart yêu cầu login lại, không khôi phục auth. Chỉ cổng fixture được lưu trong own preferences. Phiên fixture mới có store mới. Không đọc/xóa dữ liệu app khác.

## Build/test có chủ đích

```bash
df -h / /home
export JAVA_HOME=/usr/lib/jvm/java-25-openjdk-amd64
export ANDROID_HOME=/home/vantri/Android/Sdk
export PATH="$JAVA_HOME/bin:$PATH"
./gradlew -p tools/google-sos-map-harness --offline --no-daemon assembleDebug assembleDebugAndroidTest
python3 tools/google-sos-map-harness/gate_runtime.py
python3 tools/google-sos-map-harness/test_fixture.py docs/demo/gmaps02/evidence/TEST-host-rehearsal.xml
python3 tools/google-sos-map-harness/run_tests.py --evidence-dir docs/demo/gmaps02/evidence/physical-rehearsal-new
```

Root cần ít nhất2GB. Runner từ chối namespace M02/test đã có, không-r; không tự gỡ bản trình bày đang giữ. Dùng tên evidence mới. Runner chỉ gỡ package vừa cài, dừng fixture và xóa reverse của nó. Không reset/clean/commit/push/deploy.

Key lấy từ root local.properties bị Git ignore, Java Properties.load lấy khai báo MAPS_API_KEY cuối; GMAPS01_API_KEY env override như M01. Không in/copy key, không dùng command arguments chứa key, không dump raw merged manifests. `runtime_common.log_command` lọc key/token trước ghi log; gate export manifest redacted. APK chứa key theo SDK và được Git ignore. Không thay Cloud restrictions/billing/quota; không chuyển key sang fixture/Backend.

## Nếu tiles trắng

SDK_READY chưa đủ; watchdog30s hiện ERROR_OR_TIMEOUT nếu chưa callback. Kiểm ownPID log đã che key, authorization/network thực tế. Không suy diễn billing/quota chỉ từ key. Không reset Google Play Services, không đổi phone settings/GPS/Cloud. GMS Phenotype warnings không tự đồng nghĩa Maps authorization failure.

F06 OPEN / STRICT_ROUTING_DISABLED=true. Không nhận nhiệm vụ, tính UCS, polyline hay GPS tracking.
