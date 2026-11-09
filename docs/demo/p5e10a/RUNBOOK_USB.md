# P5-E10A — Shared SOS fixture qua USB

APK/namespace riêng `com.example.bai1.p5e10a.demo`. Không thay APK E09 hoặc app production. Chỉ dữ liệu synthetic, không GPS/cloud/dispatch. Graph diagnostic E09 offline; không dẫn đường.

## Mở demo

```bash
cd /home/vantri/AndroidStudioProjects/bai1
df -h / /home
/home/vantri/Android/Sdk/platform-tools/adb devices -l
(cd docs/demo/p5e10a/artifacts && sha256sum -c SHA256SUMS)
python3 tools/sos-map-harness/demo_session.py --install --keep-demo
```

`--keep-demo` là lựa chọn rõ của người chạy để giữ app mới trên điện thoại. Bỏ cờ này nếu muốn script gỡ đúng app vừa cài khi kết thúc phiên. Script không ghi đè/gỡ app đã có; chỉ mở app có SHA đúng artifact. Giữ cáp USB và terminal fixture trong toàn bộ demo. Không chạy instrumentation cùng một phiên trình bày đang mở.

## Kịch bản 2–3 phút

1. **Người dân TEST: 1** → **Người dân demo** → **SOS synthetic**.
2. Giữ mô tả synthetic hoặc chỉnh nội dung bắt đầu bằng `SYNTHETIC_TEST_DATA`; không nhập dữ liệu cá nhân. **Thêm người** nếu muốn. Tọa độ B/H là tọa độ TEST, không GPS.
3. **HTTP OK** → **Gửi SOS fixture**. Thấy `FIXTURE_STORED <UUID> status=ACTIVE`. Ghi nhớ UUID.
4. **Đăng xuất** → **Đội cứu hộ demo** → **Refresh SOS**. Marker tím xuất hiện từ `GET /sos/active`. Bấm marker xem UUID, số người, mô tả, tọa độ và ACTIVE.
5. Muốn thêm yêu cầu: đăng xuất, bấm **Người dân TEST: 1** để chọn **2**, đăng nhập dân, SOS synthetic, đổi **Tọa độ TEST: B** sang H, gửi. Đăng xuất → cứu hộ → refresh: hai UUID, hai marker.
6. Refresh thêm không tạo SOS. Gửi lại cùng công dân đang có ACTIVE trả409, không tạo bản ghi mới.
7. Có thể thử403/500/Timeout/Offline bằng một công dân chưa có SOS. Sau đó chọn HTTP OK. Lỗi không hiển thị thành công. Lỗi GET xóa marker cũ và báo GET_ERROR; refresh OK đọc lại dữ liệu server.

Trùng tọa độ: các nút marker giữ UUID riêng và xếp lệch16dp để bấm; đây là offset hiển thị, không đổi tọa độ chi tiết. Refresh thủ công, không realtime/polling/GPS. Không có nút nhận nhiệm vụ/UCS/hoàn thành.

## Phiên và lỗi

Activity recreation yêu cầu đăng nhập lại rồi refresh; fixture còn chạy vẫn giữ cùng UUID. Logout xóa token/form/marker/selection trong app; không xóa yêu cầu trên server, để vai trò khác đọc. Ctrl+C dừng fixture, xóa đúng reverse mapping của phiên. Dữ liệu server chỉ tồn tại trong RAM của phiên; mở phiên mới là kho mới. APK giữ trên laptop.

Mỗi công dân synthetic có tối đa một ACTIVE, giống giới hạn Backend đã audit. Timeout fixture là lỗi cố ý **trước commit**; lỗi timeout thực tế có thể đã ghi server. Client không tự retry POST, không tuyên bố timeout hủy được request đã gửi. Nếu chưa biết kết quả, dùng rescuer refresh để đối chiếu UUID; demo không có idempotency API mới.

Khi mất kết nối: kiểm tra USB authorized, terminal fixture và cổng local bằng script. Không trỏ sang Backend production; không đổi permission/location/settings của điện thoại.

## Build/test lại

Chỉ build lớn khi root còn ít nhất2GB và đúng một điện thoại USB authorized. Runner từ chối hai namespace E10A đã có; không tự gỡ app cài từ trước.

```bash
export JAVA_HOME=/usr/lib/jvm/java-25-openjdk-amd64
export ANDROID_HOME=/home/vantri/Android/Sdk
export PATH="$JAVA_HOME/bin:$PATH"
./gradlew -p tools/sos-map-harness --offline --no-daemon assembleDebug assembleDebugAndroidTest
python3 tools/sos-map-harness/run_demo_tests.py --evidence-dir docs/demo/p5e10a/evidence/rehearsal-new
python3 tools/sos-map-harness/test_fixture.py docs/demo/p5e10a/evidence/TEST-host-new.xml
```

Evidence dir và tên XML nên là tên mới. Runner kiểm manifest/DEX/config/source/cleartext trước install, chỉ install-t app mới, dùng owned localhost/reverse, lưu XML thực và log ownPID, tự gỡ đúng hai app của lần test. Không emulator, không cài main app, không sửa E09/Backend. `F06 OPEN`, `STRICT_ROUTING_DISABLED=true`.
