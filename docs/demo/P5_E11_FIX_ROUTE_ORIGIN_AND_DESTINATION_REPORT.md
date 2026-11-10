# BÁO CÁO NGHIỆM THU P5-E11: SỬA TUYẾN ĐƯỜNG UCS TỪ CỨU HỘ VIÊN ĐẾN NGƯỜI GỬI SOS
## DYNAMIC RESCUER ORIGIN & DYNAMIC SOS DESTINATION VERIFICATION

- **Dự án**: CẢNH BÁO THIÊN TAI – DISASTER RESCUE SYSTEM
- **Mã nhiệm vụ**: P5-E11 (Fix Route Origin and Destination)
- **Hệ điều hành kiểm thử**: Android 13 (OPPO Reno5 – CPH2159, Serial `63f1ec7`)
- **Ngày thực hiện**: 09/11/2026
- **Trạng thái**: **HOÀN THÀNH TOÀN DIỆN (100% PASS & VERIFIED ON PHYSICAL DEVICE)**

---

## 1. TỔNG QUAN VÀ NGUYÊN NHÂN LỖI (ROOT CAUSE ANALYSIS)

### 1.1. Hiện trạng trước khi sửa
Khi người dùng vào màn hình **Bản đồ cứu hộ** (`DisasterMapScreen`), chọn một thẻ SOS và nhấn **"TÌM ĐƯỜNG ĐẾN SOS (UCS)"**, hệ thống gặp các vấn đề sau:
1. **Điểm xuất phát bị gán cứng (Hardcoded Fallback)**:
   - Trong `DisasterMapViewModel.kt`, nếu `rescuerLocation == null` (do chưa có GPS hoặc GPS chưa cập nhật), router âm thầm fallback về `N_BASE (16.0500, 108.2000)`.
   - Trong `DisasterMapScreen.kt`, biến `myLocation` không được tự động kích hoạt khi mở màn hình, dẫn đến việc người cứu hộ luôn bị xem là ở `N_BASE`.
2. **Bán kính snap quá lớn (Unrealistic Snapping Radius)**:
   - Router áp dụng `maxRadiusMeters = 20000.0` (20 km), cho phép snap vào bất kỳ nút nào trong bán kính 20 km mà không kiểm tra bounding box phạm vi bao phủ đồ thị, làm sai lệch điểm bắt đầu và kết thúc.
3. **Đồ thị định tuyến thử nghiệm (Toy Diagnostic Graph)**:
   - Hệ thống runtime chỉ nạp fixture 8 nút (`DiagnosticGraphProvider.getDaNangGraph()`), dẫn đến việc tìm đường giữa bất kỳ cặp điểm nào trong trung tâm cũng luôn trả về cùng một lộ trình mẫu ~284m nối giữa `N_BASE` và `N_BACH_DANG`.

### 1.2. Mục tiêu kỹ thuật
- **Điểm xuất phát động (Dynamic Origin)**: Bắt buộc lấy từ vị trí thực tế của cứu hộ viên (qua GPS) hoặc cho phép người kiểm thử chọn trạm cứu hộ xuất phát mô phỏng trong danh sách chuẩn hóa. Tuyệt đối không fallback ngầm về `N_BASE`.
- **Điểm đích động (Dynamic Destination)**: Lấy chính xác từ tọa độ `marker.latitude`, `marker.longitude` và `eventId` của tín hiệu SOS được chọn.
- **Ranh giới bao phủ đồ thị (Graph Coverage Enforcement)**: Kiểm tra WGS84 bounding box của đồ thị. Nếu điểm xuất phát hoặc điểm đích nằm ngoài phạm vi đồ thị, từ chối định tuyến fail-closed với mã lỗi `GRAPH_COVERAGE_MISSING`. Giảm bán kính snap xuống tối đa `1000m`.
- **Tích hợp đồ thị OSM thật (Real OSM Road Graph)**: Đóng gói và giải nén đồ thị đường bộ trung tâm Đà Nẵng (`output-danang-261103/BaseRoadGraph.json.gz` gồm 7.294 nút và 16.651 cạnh) vào APK assets.
- **Xác thực thiết bị thật OPPO Reno5**: Chứng minh bằng hình ảnh thực tế Case 1 và Case 2 với điểm xuất phát khác nhau, điểm đến khác nhau, quãng đường và chi phí UCS hoàn toàn khác biệt.

---

## 2. CHI TIẾT CÁC BẢN VÁ MÃ NGUỒN (CODE CHANGES)

### 2.1. Nâng cấp Thuật toán UCS (`UniformCostSearchRouter.kt`)
- Bổ sung hàm tính toán và trích xuất Bounding Box WGS84 của đồ thị:
  ```kotlin
  fun getGraphBoundingBox(roadGraph: RoadGraph): RoutingBoundingBox?
  ```
- Bổ sung hàm kiểm tra tọa độ nằm trong vùng bao phủ đồ thị (với ngưỡng đệm `bufferMeters = 1000.0`):
  ```kotlin
  fun isPointWithinCoverage(roadGraph: RoadGraph, point: GeoPoint, bufferMeters: Double = 1000.0): Boolean
  ```
- Kiểm tra tính hợp lệ trước khi snap:
  - Nếu điểm xuất phát hoặc điểm đích nằm ngoài phạm vi đồ thị -> Trả về lỗi `GRAPH_COVERAGE_MISSING`.
  - Giới hạn bán kính tìm nút snap gần nhất xuống `1000.0` mét.

### 2.2. Đóng gói & Nạp Đồ thị OSM thật (`DiagnosticGraphProvider.kt` & `build.gradle.kts`)
- Sao chép đồ thị thực tế Đà Nẵng vào `app/src/main/assets/routing/BaseRoadGraph.json.gz` (7.294 nodes, 16.651 edges, phiên bản `dn_central_real_v1`).
- Cấu hình AAPT không nén file gzip:
  ```kotlin
  androidResources {
      noCompress += "gz"
  }
  ```
- Viết cơ chế nạp thông minh `getRealOrDiagnosticGraph(context)`: tự động nhận diện asset `.json.gz` hoặc `.json` (khi AAPT giải nén), nạp toàn bộ 7.294 nút và 16.651 cạnh trong ~400ms trên thiết bị thật.

### 2.3. Cải tiến ViewModel (`DisasterMapViewModel.kt` & Factory)
- Thêm cờ `isSimulatedRescuerLocation: Boolean` vào `RouteUiState`.
- Nâng cấp `computeRouteToMarker(marker, rescuerLocation, isSimulatedRescuerLocation)`:
  - Loại bỏ hoàn toàn fallback về `N_BASE`. Nếu `rescuerLocation == null` hoặc tọa độ `(0.0, 0.0)`, trả về thông báo lỗi rõ ràng: `"Chưa xác định được vị trí của cứu hộ viên. Vui lòng bật GPS hoặc chọn vị trí mô phỏng."`
  - Tự động nạp đồ thị đầy đủ trong background thread (`Dispatchers.IO`) khi có Android Context.
  - Ghép nối tọa độ chính xác: `[startLatLng] + pathGeometry + [destLatLng]`.
- Nâng cấp `DisasterMapViewModelFactory` và `ProductionAppNavScreens` truyền `applicationContext` cho ViewModel.

### 2.4. Cải tiến Giao diện Bản đồ (`DisasterMapScreen.kt`)
- **Tự động yêu cầu GPS**: Khi vào màn hình, tự động yêu cầu cập nhật vị trí thiết bị qua `AndroidLocationProvider`.
- **Nút chọn Trạm Cứu hộ (FAB)**: Bổ sung Floating Action Button với icon Location (`content-desc="Chọn vị trí cứu hộ viên"`).
- **Hộp thoại chọn Vị trí Cứu hộ**:
  - Tùy chọn 1: Lấy vị trí GPS thực tế thiết bị.
  - Tùy chọn 2: Trạm Cứu hộ Hải Châu (Trung tâm) `(16.0500, 108.2000)`.
  - Tùy chọn 3: Trạm Cứu hộ Thanh Khê (Tây Bắc) `(16.0650, 108.1850)`.
  - Tùy chọn 4: Trạm Cứu hộ Sơn Trà (Đông Bắc) `(16.0620, 108.2350)`.
  - Tùy chọn 5: Trạm Cứu hộ Cẩm Lệ (Nam) `(16.0380, 108.2050)`.
- **Hiển thị Marker Cứu hộ viên**: Marker màu Azure phân biệt rõ giữa GPS thật và Trạm cứu hộ mô phỏng.
- **Route Info Card cải tiến**: Hiển thị quãng đường UCS (km và m), chi phí $g(n)$, nút mạng OSM xuất phát, nút mạng OSM đích đến, số lượng nút/cạnh, phiên bản đồ thị và nút "Đóng lộ trình".

---

## 3. KẾT QUẢ KIỂM THỬ TỰ ĐỘNG (AUTOMATED TEST SUITES)

### 3.1. Android Unit & Integration Tests (`bai1`)
- Mở rộng bộ kiểm thử `UcsGoogleMapsIntegrationTest.kt` lên **23 bài test** (từ UCS-T01 đến UCS-T17, bao gồm UCS-T16-A đến UCS-T16-F):
  - Kiểm tra từ chối khi thiếu vị trí cứu hộ (không âm thầm fallback).
  - Kiểm tra định tuyến với vị trí GPS động.
  - Kiểm tra định tuyến với các trạm cứu hộ mô phỏng (Hải Châu, Thanh Khê, Sơn Trà, Cẩm Lệ).
  - Kiểm tra từ chối tọa độ ngoài phạm vi đồ thị (`GRAPH_COVERAGE_MISSING`).
  - Kiểm tra độ dài polyline thay đổi tương ứng theo điểm xuất phát khác nhau.
- Kết quả chạy toàn bộ test suite Android:
  ```
  BUILD SUCCESSFUL in 7s
  34 test suites passed
  582 tests passed, 0 failed
  ```

### 3.2. Backend Test Suite (`canh-bao-thien-tai-backend-app`)
- Chạy toàn bộ bộ test NestJS / Jest:
  ```
  Test Suites: 43 passed, 43 total
  Tests:       1074 passed, 1074 total
  Snapshots:   0 total
  Time:        5.233 s
  ```

---

## 4. XÁC MINH TRÊN THIẾT BỊ THẬT OPPO RENO5 (PHYSICAL DEVICE VALIDATION)

Thiết bị kiểm thử: **OPPO Reno5 (CPH2159)**, Android 13, độ phân giải 1080x2400, kết nối USB ADB `63f1ec7`.

### Bảng so sánh kiểm thử thực tế trên thiết bị:

| Tiêu chí | CASE 1 (Trạm Hải Châu -> SOS Bạch Đằng) | CASE 2 (Trạm Thanh Khê -> SOS Nguyễn Văn Linh) |
| :--- | :--- | :--- |
| **Điểm xuất phát** | Trạm Cứu hộ Hải Châu (Trung tâm) `(16.0500, 108.2000)` | Trạm Cứu hộ Thanh Khê (Tây Bắc) `(16.0650, 108.1850)` |
| **Nút snap xuất phát** | `node_osm_13676913515` (OSM thật) | `node_osm_6040677083` (OSM thật) |
| **Điểm đến (SOS)** | SOS Bạch Đằng `(16.0544, 108.2022)` | SOS Nguyễn Văn Linh `(16.0685, 108.2215)` |
| **Nút snap điểm đến** | `node_osm_13671828206` (OSM thật) | `node_osm_420249568` (OSM thật) |
| **Khoảng cách UCS** | **0.59 km (593 m)** | **3.95 km (3946 m)** |
| **Chi phí UCS $g(n)$** | **593.4** | **3946.2** |
| **Quy mô tuyến đường** | **8 nút \| 7 đoạn** | **43 nút \| 42 đoạn** |
| **Phiên bản đồ thị** | `dn_central_real_v1` (7.294 nodes) | `dn_central_real_v1` (7.294 nodes) |
| **Độ phủ Bounding Box** | Hợp lệ (Nằm trong lõi Đà Nẵng) | Hợp lệ (Nằm trong lõi Đà Nẵng) |
| **Hình ảnh xác thực** | `docs/demo/screenshots/oppo_route_case1_verified.png` | `docs/demo/screenshots/oppo_route_case2_verified.png` |

### 4.1. Chi tiết Case 1: Trạm Hải Châu đến SOS Bạch Đằng
- **Ghi nhận UI thật**:
  - Header: `"TUYẾN UCS MÔ PHỎNG — KHÔNG DÙNG ĐỂ ĐIỀU HƯỚNG CỨU HỘ THỰC TẾ"`
  - Dòng 1: `"Khoảng cách UCS: 0.59 km (593 m)"`
  - Dòng 2: `"Xuất phát: Mô phỏng (Trạm Cứu hộ Hải Châu (Trung tâm)) [node_osm_13676913515]"`
  - Dòng 3: `"Đích đến: SOS [node_osm_13671828206]"`
  - Dòng 4: `"Chi phí UCS: 593.4 (g(n) cost)"`
  - Dòng 5: `"Lộ trình: 8 nút | 7 đoạn | dn_central_real_v1"`
- **Trực quan trên Google Maps**: Đường Polyline màu xanh đậm (PrimaryBlue, width 12f) men theo mạng lưới đường bộ thực tế từ trạm Hải Châu đến vị trí người gửi SOS ven sông Hàn.
- **File ảnh**: `docs/demo/screenshots/oppo_route_case1_verified.png`

### 4.2. Chi tiết Case 2: Trạm Thanh Khê đến SOS Nguyễn Văn Linh
- **Ghi nhận UI thật**:
  - Header: `"TUYẾN UCS MÔ PHỎNG — KHÔNG DÙNG ĐỂ ĐIỀU HƯỚNG CỨU HỘ THỰC TẾ"`
  - Dòng 1: `"Khoảng cách UCS: 3.95 km (3946 m)"`
  - Dòng 2: `"Xuất phát: Mô phỏng (Trạm Cứu hộ Thanh Khê (Tây Bắc)) [node_osm_6040677083]"`
  - Dòng 3: `"Đích đến: SOS [node_osm_420249568]"`
  - Dòng 4: `"Chi phí UCS: 3946.2 (g(n) cost)"`
  - Dòng 5: `"Lộ trình: 43 nút | 42 đoạn | dn_central_real_v1"`
- **Trực quan trên Google Maps**: Đường Polyline thay đổi hoàn toàn điểm xuất phát sang khu vực quận Thanh Khê, đi qua 42 đoạn đường thực tế nối liền về phía đông, quãng đường dài hơn 6.6 lần so với Case 1.
- **File ảnh**: `docs/demo/screenshots/oppo_route_case2_verified.png`

### 4.3. Kiểm thử từ chối điểm ngoài vùng phủ (Graph Coverage Missing)
- Khi chọn điểm SOS ở ngoài ranh giới bản đồ (ví dụ điểm SOS ở Điện Bàn `15.9759, 108.2122`), hệ thống lập tức thông báo lỗi:
  `"Vị trí người gửi SOS nằm ngoài phạm vi đồ thị đường bộ (GRAPH_COVERAGE_MISSING)"`
- Không vẽ đường giả lập xuyên biển hoặc xuyên núi; tuân thủ nguyên tắc an toàn cứu nạn fail-closed.

---

## 5. BẢO TOÀN AN TOÀN VÀ TÍNH NĂNG GỐC (SAFETY & INTEGRITY AUDIT)

1. **Bảo tồn toàn bộ hệ thống Authentication**:
   - Đăng nhập form email/mật khẩu, Đăng ký tài khoản, Google Login qua Credential Manager và phiên làm việc Firebase Auth hoạt động bình thường, không bị can thiệp.
2. **Bảo tồn Firebase Realtime Database & SOS**:
   - `FirebaseSosRealtimeDataSource` tiếp tục lắng nghe trực tiếp trên node `sos-events` của RTDB.
   - Thử nghiệm gửi dữ liệu RTDB cho thấy thiết bị OPPO nhận sự kiện `RTDB_CHILD_ADDED` và `RTDB_CHILD_CHANGED` ngay trong vòng 2ms và cập nhật số lượng tín hiệu khẩn cấp từ 2 lên 4 tín hiệu.
3. **Không gọi Google Directions / Routes API**:
   - 100% tọa độ hiển thị và polyline sinh ra từ thuật toán UCS nội bộ (`UniformCostSearchRouter`), tính toán cục bộ trên thiết bị bằng đồ thị OSM thật.
4. **Tuân thủ quy tắc an toàn Phase 5**:
   - Giữ nguyên cờ an toàn `F06 = OPEN`, `STRICT_ROUTING_DISABLED = true` cho chức năng tìm đường production.
   - Thông điệp cảnh báo bắt buộc luôn hiển thị rõ ràng trên thẻ lộ trình: `"TUYẾN UCS MÔ PHỎNG — KHÔNG DÙNG ĐỂ ĐIỀU HƯỚNG CỨU HỘ THỰC TẾ"`.

---

## 6. KẾT LUẬN

Nhiệm vụ **P5-E11 FIX UCS ORIGIN & DESTINATION** đã giải quyết triệt để lỗi gán cứng điểm xuất phát `N_BASE` và lỗi đồ thị đồ chơi 284m. Hệ thống hiện đã vận hành hoàn hảo với đồ thị OSM thực tế của Đà Nẵng, điểm xuất phát và điểm đích hoàn toàn động, hỗ trợ cả định vị GPS thật lẫn trạm cứu hộ mô phỏng, sẵn sàng 100% cho buổi demo ngày 10/11/2026.

