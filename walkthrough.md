# Visual Audit & Polish Walkthrough: Phase 1 – 3

## 1. Màn hình đã review và đối chiếu (Phase 1–3)

### Phase 1: Foundation & Shared Components
- [Theme.kt](file:///home/vantri/AndroidStudioProjects/bai1/app/src/main/java/com/example/bai1/ui/theme/Theme.kt), [Color.kt](file:///home/vantri/AndroidStudioProjects/bai1/app/src/main/java/com/example/bai1/ui/theme/Color.kt), [Type.kt](file:///home/vantri/AndroidStudioProjects/bai1/app/src/main/java/com/example/bai1/ui/theme/Type.kt)
- [AppBottomNavigation.kt](file:///home/vantri/AndroidStudioProjects/bai1/app/src/main/java/com/example/bai1/presentation/components/AppBottomNavigation.kt)
- [EmergencyCards.kt](file:///home/vantri/AndroidStudioProjects/bai1/app/src/main/java/com/example/bai1/presentation/components/EmergencyCards.kt)
- [CommonComponents.kt](file:///home/vantri/AndroidStudioProjects/bai1/app/src/main/java/com/example/bai1/presentation/components/CommonComponents.kt)
- [MapMockupCanvas.kt](file:///home/vantri/AndroidStudioProjects/bai1/app/src/main/java/com/example/bai1/presentation/components/MapMockupCanvas.kt)

### Phase 2: Onboarding & Auth
- **1. Splash**: [SplashScreen.kt](file:///home/vantri/AndroidStudioProjects/bai1/app/src/main/java/com/example/bai1/presentation/auth/SplashScreen.kt)
- **2, 3, 4. Onboarding (3 slides)**: [OnboardingScreen.kt](file:///home/vantri/AndroidStudioProjects/bai1/app/src/main/java/com/example/bai1/presentation/auth/OnboardingScreen.kt)
- **5. Login**: [LoginScreen.kt](file:///home/vantri/AndroidStudioProjects/bai1/app/src/main/java/com/example/bai1/presentation/auth/LoginScreen.kt)
- **6. Register**: [RegisterScreen.kt](file:///home/vantri/AndroidStudioProjects/bai1/app/src/main/java/com/example/bai1/presentation/auth/RegisterScreen.kt)
- **7. Role Selection**: [RoleSelectionScreen.kt](file:///home/vantri/AndroidStudioProjects/bai1/app/src/main/java/com/example/bai1/presentation/auth/RoleSelectionScreen.kt)

### Phase 3: Core Disaster Discovery & Warning
- **8. Home**: [HomeScreen.kt](file:///home/vantri/AndroidStudioProjects/bai1/app/src/main/java/com/example/bai1/presentation/home/HomeScreen.kt)
- **9. Disaster Map**: [DisasterMapScreen.kt](file:///home/vantri/AndroidStudioProjects/bai1/app/src/main/java/com/example/bai1/presentation/map/DisasterMapScreen.kt)
- **10. Warning List**: [WarningListScreen.kt](file:///home/vantri/AndroidStudioProjects/bai1/app/src/main/java/com/example/bai1/presentation/warning/WarningListScreen.kt)
- **11. Storm Detail**: [StormDetailScreen.kt](file:///home/vantri/AndroidStudioProjects/bai1/app/src/main/java/com/example/bai1/presentation/warning/StormDetailScreen.kt)
- **12. Flood Detail**: [FloodDetailScreen.kt](file:///home/vantri/AndroidStudioProjects/bai1/app/src/main/java/com/example/bai1/presentation/warning/FloodDetailScreen.kt)

---

## 2. Những UI đã chỉnh sửa và hoàn thiện

1. **Bản đồ GIS / Weather Radar (`MapMockupCanvas.kt`)**:
   - Khắc họa địa hình bờ biển miền Trung Việt Nam chân thực: Đầm phá Tam Giang - Cầu Hai (Thừa Thiên Huế), đèo Hải Vân, bán đảo Sơn Trà và vịnh Đà Nẵng vươn ra biển Đông, đảo Cù Lao Chàm và lưu vực sông Thu Bồn / Vu Gia (Quảng Nam).
   - Đường đẳng sâu biển Đông (Bathymetry lines) và nền gradient biển sâu tự nhiên.
   - Vòng xoáy radar tâm bão YAGI với dải phản hồi vô tuyến (radar reflectivity bands) đa tầng (`#FF1744`, `#FF9100`, `#00E5FF`).
   - Đường dự báo hướng bão (dashed forecast track) kèm các mốc dự báo thời gian (`+24h`, `+48h`).
   - Vùng trũng ngập lụt Thu Bồn (Đại Lộc - Điện Bàn) với đường biên phân giới nét đứt.
   - Điểm GPS vị trí hiện tại Đà Nẵng với vòng tròn định vị pulsing accuracy.
   - Ghim trạm cứu hộ chữ thập xanh lá.
   - Card thông tin bão và card cảnh báo ngập lụt nổi có thể bấm trực tiếp để chuyển tới chi tiết bão / lũ.
   - Nút zoom in, zoom out, vị trí hiện tại, lớp bản đồ hoạt động mượt mà.

2. **Emergency Alert & Disaster Category Grid (`EmergencyCards.kt`)**:
   - `DisasterCategoryGrid`: 4 category item được gán `Modifier.weight(1f)` chia đều 1 hàng ngang linh hoạt, không bị vỡ hàng hay wrap xấu trên màn hình hẹp 360dp.
   - `EmergencyAlertCard`: Thiết kế banner đỏ chuẩn `#E53935`, badge cấp độ khẩn cấp nổi bật nhất trên Home, thông số gió và vị trí tương phản rõ nét.
   - `NewsCard`: Bổ sung Canvas radar xoáy mây vệ tinh thu nhỏ giúp thumbnail bài báo nhìn trực quan, hiện đại như bản tin thời tiết thật.

3. **Storm Detail Screen (`StormDetailScreen.kt`)**:
   - Bổ sung màn hình mây vệ tinh hồng ngoại (IR1) bằng Canvas gradient nhiều lớp mô phỏng mắt bão và hoàn lưu mây bão YAGI.
   - Thay thế `TabRow` cũ bằng `PrimaryTabRow` Material 3 hiện đại, triệt tiêu toàn bộ deprecation warning.
   - Danh sách khuyến cáo bullet card rõ ràng, dễ đọc.
   - Nút CTA "Theo dõi" / "Đang theo dõi ✓" sticky ở đáy kèm `navigationBarsPadding()`.

4. **Flood Detail Screen (`FloodDetailScreen.kt`)**:
   - Card hình ảnh ngập lụt được vẽ Canvas bề mặt nước dâng kèm tag đo độ sâu ngập (1.5m, 0.9m).
   - Tab video camera giám sát trực tiếp (Cầu Ái Nghĩa) có nút Play và badge "TRỰC TIẾP".
   - Chuyển `TabRow` sang `PrimaryTabRow`.
   - Nút CTA "Xem khu vực ngập trên bản đồ" sticky cố định ở đáy, chuyển trực tiếp sang màn hình Bản đồ.

5. **Register Screen (`RegisterScreen.kt`)**:
   - Thay thế cụm Radio Button nằm ngang cũ bằng 2 card chọn "Cá nhân" / "Cứu hộ" dùng `Modifier.weight(1f)`, border bo góc 12dp. Hoàn toàn không bị tràn lề hay xuống dòng cụt chữ trên thiết bị 360dp.

6. **Role Selection & Splash (`RoleSelectionScreen.kt`, `SplashScreen.kt`)**:
   - Bổ sung `verticalScroll(rememberScrollState())` và `systemBarsPadding()`, đảm bảo trên các thiết bị có chiều cao khiêm tốn hoặc khi bật bàn phím, giao diện không bị cắt xén các nút hành động.
   - Thêm subtle wave canvas trên Splash screen mang lại cảm giác đại dương/thiên nhiên cao cấp.

---

## 3. Vấn đề Responsive đã kiểm tra và khắc phục

| Vấn đề trước audit | Thiết bị ảnh hưởng | Cách khắc phục |
| :--- | :--- | :--- |
| Row chọn "Cá nhân" vs "Nhà hoạt động cứu hộ" tràn chiều rộng 312dp khả dụng | 360dp width | Tách thành 2 `Surface` card bo góc chia tỉ lệ `weight(1f)`, rút gọn label thành "Cá nhân" và "Cứu hộ". |
| 4 Card category ở Home có thể bị wrap hoặc lệch lề | 360dp – 390dp width | Dùng `Row` với `Modifier.weight(1f)` cho từng cột giúp cân đối tuyệt đối. |
| Legend bản đồ có thể che khuất hoặc đè lên phím điều hướng bản đồ | 360dp width | Đặt `widthIn(max = 210.dp)`, `bottom = 16.dp` góc dưới trái, các nút zoom ở giữa bên phải. |
| Role Selection và Splash có nguy cơ clip nút tiếp theo trên màn hình ngắn | Chiều cao < 700dp | Tích hợp `verticalScroll` linh hoạt và `systemBarsPadding()`. |

---

## 4. Kết quả Build

- **Lệnh thực thi**:
  ```bash
  export JAVA_HOME=/snap/android-studio/current/jbr
  export PATH=$JAVA_HOME/bin:$PATH
  ./gradlew assembleDebug
  ```
- **Trạng thái**: `BUILD SUCCESSFUL in 2s`
- **Warnings**: `0 compiler warnings`, `0 errors`
- **File APK tạo thành**: `app/build/outputs/apk/debug/app-debug.apk` (69 MB)
