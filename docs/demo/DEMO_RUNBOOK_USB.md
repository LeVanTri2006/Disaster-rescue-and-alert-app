# Runbook USB — P5-E09 Demo

Dùng ngày **10/11/2026**. APK riêng: `com.example.bai1.p5e09.demo`. Không cài/mở/ghi đè package production `com.example.bai1`.

## Chuẩn bị

```bash
cd /home/vantri/AndroidStudioProjects/bai1
df -h / /home
export JAVA_HOME=/usr/lib/jvm/java-25-openjdk-amd64
export PATH="$JAVA_HOME/bin:$PATH"
/home/vantri/Android/Sdk/platform-tools/adb devices -l
```

Kiểm SHA-256 artifact trong đúng thư mục:

```bash
(cd docs/demo/artifacts && sha256sum -c SHA256SUMS)
```

Chỉ tiếp tục khi có đúng một điện thoại USB authorized. Không dùng emulator. Nếu unauthorized, người dùng chấp nhận USB debugging trên điện thoại. Không thay đổi permission/location hay thiết lập hệ thống để làm demo.

## Cách nhanh — khuyến nghị

```bash
python3 tools/demo-harness/demo_session.py --install --keep-demo
```

**`--keep-demo` là lựa chọn rõ ràng của người chạy để giữ bản vừa cài trên điện thoại.** Script chỉ cài nếu package chưa tồn tại; không dùng `-r`. Nếu đã có package cùng tên, script chỉ mở khi SHA-256 APK đang cài khớp artifact; khác SHA thì dừng, không gỡ/cài đè.

Script tự chọn serial USB, mở fixture tại `127.0.0.1:<cổng OS cấp>`, tạo đúng một reverse mapping và mở Activity với cổng đó. Không hardcode serial/3000/10.0.2.2. Giữ terminal và cáp USB trong lúc trình bày. Nhấn Ctrl+C khi xong: dừng fixture, xóa đúng reverse riêng. Package có sẵn từ trước không bị gỡ; package mới chỉ được giữ nếu có `--keep-demo`.

Muốn diễn tập và tự gỡ package mới sau phiên:

```bash
python3 tools/demo-harness/demo_session.py --install
```

APK và evidence trên laptop luôn được giữ.

## Thao tác trên điện thoại

1. Bấm **Đăng nhập demo** (tài khoản synthetic được cấu hình trong test-only adapter; không nhập tài khoản thật).
2. Vào **Map / UCS**. Graph có 8 nút/8 cạnh; A xanh, B vàng, H tách rời.
3. Bấm **Phân tích đường đi**. Phải thấy `STRICT_ROUTING_DISABLED`, `Path: ∅`, `Cost: N/A` và `NAVIGATION ALLOWED = FALSE`.
4. Đổi **Điểm đi**, **Điểm đến**; bấm phân tích lại. Đổi **Chặn AC**, **Cấm rẽ** và xem kết quả kiểm tra luật rẽ. Cạnh đỏ là trạng thái input, không phải tuyến được tính.
5. Bấm **Graph lỗi**: parser phải từ chối. Bấm **Gốc** để khôi phục graph hợp lệ.
6. Vào **Cứu hộ fixture**, chọn số người/mô tả synthetic; **HTTP OK** → **Gửi tới fixture**. Chỉ dùng tọa độ TEST từ graph.
7. Xem `FIXTURE_RECEIVED SYNTHETIC-E09-* status=ACTIVE`, trở lại map xem marker tím. Đây không phải rescue dispatch.
8. Thử **HTTP 403**, **Timeout**, **Offline**. Sau đó chọn lại **HTTP OK**. Đăng xuất xóa phiên và marker trong bộ nhớ.

## Chạy thủ công nếu cần kiểm tra từng bước

Terminal 1, chọn tên state file mới chưa tồn tại:

```bash
python3 tools/demo-harness/fixture.py --state docs/demo/session-2026-11-10.json
```

Terminal 2:

```bash
ADB=/home/vantri/Android/Sdk/platform-tools/adb
SERIAL=$(python3 - <<'PY'
import subprocess
lines=subprocess.check_output(['/home/vantri/Android/Sdk/platform-tools/adb','devices','-l'],text=True).splitlines()[1:]
x=[s.split()[0] for s in lines if 'usb:' in s and not s.startswith('emulator')]
assert len(x)==1
print(x[0])
PY
)
PORT=$(python3 -c 'import json; print(json.load(open("docs/demo/session-2026-11-10.json"))["port"])')
"$ADB" -s "$SERIAL" reverse "tcp:$PORT" "tcp:$PORT"
"$ADB" -s "$SERIAL" shell pm path com.example.bai1.p5e09.demo
```

Chỉ nếu package vắng mặt mới cài:

```bash
"$ADB" -s "$SERIAL" install -t docs/demo/artifacts/p5e09-demo.apk
"$ADB" -s "$SERIAL" shell am start -W -n com.example.bai1.p5e09.demo/com.example.bai1.demo.DemoActivity --ei fixturePort "$PORT"
```

Nếu package đã tồn tại, ưu tiên script khuyến nghị để kiểm SHA; không cài đè. Sau phiên thủ công: Ctrl+C terminal 1, rồi xóa **đúng** mapping riêng, không remove-all:

```bash
"$ADB" -s "$SERIAL" reverse --remove "tcp:$PORT"
```

## Không thấy bản đồ / mất mạng

Đây là graph diagnostic offline, không cần map tiles/API key/Internet. Cần bấm **Map / UCS** hoặc **Chẩn đoán offline**, không tìm Google Maps. Có thể cuộn vùng điều khiển/kết quả. Nếu fixture không đăng nhập được: kiểm tra terminal fixture còn chạy, USB authorized và reverse port đúng; chạy lại `demo_session.py` để chọn cổng mới. Không trỏ sang Backend production.

Khi fixture tắt, app không crash và có chế độ graph offline; SOS cần phiên fixture. Activity/process restart yêu cầu đăng nhập lại; chỉ cổng local được lưu trong prefs riêng, không lưu token.

## Kiểm thử lại và build khi cần

```bash
export ANDROID_HOME=/home/vantri/Android/Sdk
./gradlew -p tools/demo-harness --offline --no-daemon assembleDebug assembleDebugAndroidTest
python3 tools/demo-harness/run_demo_tests.py --evidence-dir docs/demo/evidence/rehearsal-2026-11-10
```

Evidence dir phải chưa tồn tại; runner từ chối package có sẵn và tự cleanup đúng hai package nó mới cài. Không chạy instrumentation khi muốn giữ một demo đang cài; dùng phiên diễn tập ở trên hoặc xác nhận tự gỡ đúng bản demo của mình trước.

Fallback JVM:

```bash
./gradlew --offline --no-daemon :app:testDebugUnitTest --rerun-tasks --tests 'com.example.bai1.rescue.routing.*'
```

Không dùng kết quả private-kernel unit test để cấp quyền navigation cho app. Không tắt safety lock, không GPS/cloud/SOS thật.
