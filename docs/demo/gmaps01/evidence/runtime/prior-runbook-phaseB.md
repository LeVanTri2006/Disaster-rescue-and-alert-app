# P5-E10-M01 — cấu hình Google Maps demo

**BLOCKED tại phase B: chưa có key được phép dùng cho demo.** APK hiện tại chỉ là CONFIGURATION ONLY để lấy certificate từ APK thật; chưa có GoogleMap screen, không hiển thị tiles/markers. Không cài APK này để trình bày Google Maps. E10A vẫn là fallback Canvas, không phải bằng chứng Google Maps.

## 1. Chuẩn bị USB và kiểm tra artifact

```bash
cd /home/vantri/AndroidStudioProjects/bai1
df -h / /home
/home/vantri/Android/Sdk/platform-tools/adb devices -l
(cd docs/demo/gmaps01/artifacts && sha256sum -c SHA256SUMS)
python3 tools/google-maps-harness/configuration_status.py
```

Script chỉ kiểm SHA và in tuple đăng ký, không cài/mở app, không đọc/in key. Exit2 có nghĩa BLOCKED có chủ đích. Chỉ dùng physical OPPO USB authorized; không emulator. Root dưới2GB thì dừng build lớn.

## 2. Việc cần làm trong Google Cloud

Dùng Cloud project/key mà bạn được phép dùng cho demo. Không bỏ restrictions của key production, không gửi key vào chat/report/source/Git.

- Project phải bật **Maps SDK for Android**, có billing/quota phù hợp.
- Credentials → khóa được phép dùng → Application restrictions: **Android apps**.
- Thêm đúng package và SHA-1 dưới đây; giữ nguyên mọi restriction/entry production khác.
- API restrictions chỉ cho API cần thiết, gồm **Maps SDK for Android**. Việc cấu hình Cloud do chủ tài khoản thực hiện; agent không thay đổi Cloud/billing/key.

```text
Package: com.example.bai1.p5e10maps.demo
Certificate SHA-1: E4:EA:13:1A:81:21:D3:34:86:B8:3B:EA:64:F1:A4:1C:68:F0:80:07
```

SHA-1 được lấy bằng apksigner từ chính APK cấu hình đã ký, không đoán từ tên keystore. Đổi máy/keystore/certificate sẽ cần đăng ký tuple mới. [Bằng chứng certificate](evidence/signing-certificate.txt).

Sau khi hoàn tất, báo rằng tuple đã được cho phép và chỉ rõ cách cung cấp key riêng cho phiên build mà không đưa giá trị lên chat/Git/log. Module hiện tại **chưa đọc bất kỳ key nào**, kể cả local.properties production. Có key không tự biến APK cấu hình thành màn Maps: cần tiếp tục phase C–E trong task M01, chưa chuyển M02.

Tài liệu: [API key/restrictions](https://developers.google.com/maps/documentation/android-sdk/get-api-key), [Google Maps setup](https://developers.google.com/maps/documentation/android-sdk/cloud-setup).

## 3. Cài và mở — đang bị chặn

Chưa có APK Google Maps demo đủ điều kiện cài hoặc lệnh startup Maps. Không cung cấp lệnh mở ConfigurationActivity như thể đó là Google Maps. Sau khi có key được phép dùng, phase C–E phải:

1. Build GoogleMap screen thật trong namespace trên; camera TEST cố định, My Location false, không location permissions.
2. Gate merged manifest/dependency graph/DEX/startup; không Firebase Auth/Database, FusedLocationProvider, credentials/SOS/rescue/routing DI.
3. Chỉ cài APK khi namespace chưa tồn tại, install-t không-r. Nếu có sẵn, đối chiếu SHA; khác SHA thì dừng, không tự gỡ/cài đè. Không đụng E09/E10A/main app.
4. Mở actual Maps Activity qua script startup đã được smoke-test rồi mới xuất lệnh cụ thể.
5. Thấy RESCUER TEST tại16.0500,108.2020 và SOS TEST tại16.0510,108.2030; bấm từng marker kiểm nhãn/toạ độ, zoom/pan/recreate.
6. Chỉ PASS tiles khi actual onMapLoaded callback **và** ảnh/quan sát trực quan trên OPPO chứng minh tiles. Nền trắng/marker-only không PASS.
7. Thu ownPID logcat theo test window, JUnit XML thực, ảnh chỉ của owned demo. Kết thúc chỉ force-stop/gỡ package vừa cài nếu phiên sở hữu nó; không clear dữ liệu/settings/app khác. Maps demo không cần adb reverse.

## 4. Không thấy bản đồ

Hiện tại: chưa có GoogleMap, nên blocker cấu hình là nguyên nhân; không thử Canvas để đổi status.

Khi đã có Maps screen: kiểm mạng của điện thoại nhưng không tự đổi Wi-Fi/mobile/settings cá nhân; kiểm Maps SDK API enabled, billing/quota và package/SHA-1 đúng certificate APK đang chạy. Không xem onMapReady là bằng chứng tiles xong. Không in API key trong diagnostics/logcat; thông báo timeout/loading chỉ phản ánh trạng thái quan sát được, không tự kết luận lỗi billing nếu chưa có bằng chứng.

## 5. Build lại APK cấu hình hiện tại

```bash
export JAVA_HOME=/usr/lib/jvm/java-25-openjdk-amd64
export ANDROID_HOME=/home/vantri/Android/Sdk
export PATH="$JAVA_HOME/bin:$PATH"
./gradlew -p tools/google-maps-harness --offline --no-daemon assembleDebug
python3 tools/google-maps-harness/audit_configuration.py
```

Chỉ build cấu hình, không tự install. Maps Compose4.4.1/Maps SDK19.0.0 giữ nguyên, transitive alignment riêng module theo cache. Không UCS/polyline/mission/GPS/SOS production. F06OPEN, STRICT_ROUTING_DISABLED=true.
