# Kế hoạch triển khai UI/UX cho Antigravity

## 1. Mục tiêu

Xây dựng giao diện FRONT-END hoàn chỉnh cho ứng dụng Android **“CẢNH BÁO THIÊN TAI”** – ứng dụng cảnh báo bão, lũ, sạt lở và kết nối lực lượng hỗ trợ cứu hộ.

Hình ảnh UI/UX tham chiếu được xem là **REFERENCE CHÍNH** về bố cục, phong cách, màu sắc, khoảng cách, card, icon, typography, bottom navigation và cách trình bày thông tin.

Giai đoạn này **chưa cần backend**. Sử dụng mock data / local fake data để hoàn thiện toàn bộ flow giao diện và navigation trước. Tất cả nút chính phải có tương tác, không để màn hình chết hoặc TODO cho các thành phần UI chính.

> Nguồn yêu cầu gốc: tài liệu prompt UI/UX đã cung cấp, trong đó yêu cầu bám sát hình tham chiếu và xây dựng toàn bộ flow giao diện. fileciteturn0file0L3-L10

---

## 2. Công nghệ bắt buộc

- Android
- Kotlin
- Jetpack Compose
- Material 3
- Navigation Compose
- MVVM
- Compose State
- Repository pattern ở mức cơ bản
- Gradle Kotlin DSL
- Android Studio project chuẩn

### Nguyên tắc khi project đã tồn tại

- Không phá cấu trúc hiện tại.
- Giữ dependency đang hoạt động.
- Chỉ bổ sung những dependency cần thiết.

### Nếu project chưa có

- Tạo Android project bằng Kotlin + Jetpack Compose.

---

## 3. Định hướng sản phẩm

### Tên tạm thời

**CẢNH BÁO THIÊN TAI**

### Slogan

**Kết nối · Cảnh báo · Hành động**

### Nhóm người dùng

#### Người dùng thông thường

- Nhận cảnh báo bão.
- Nhận cảnh báo lũ.
- Xem bản đồ thiên tai.
- Xem khu vực nguy hiểm.
- Nhận thông báo khẩn cấp.
- Gửi SOS.
- Xem điểm cứu hộ.
- Xem điểm sơ tán.

#### Nhà hoạt động cứu hộ

- Đăng ký trở thành người cứu hộ.
- Khai báo kỹ năng.
- Khai báo phương tiện.
- Chọn khu vực hoạt động.
- Nhận nhiệm vụ cứu hộ.
- Xem chi tiết nhiệm vụ.
- Tham gia nhiệm vụ.
- Chat với đội cứu hộ.
- Cập nhật trạng thái nhiệm vụ.
- Xem lịch sử cứu hộ.

---

## 4. Design System

### Phong cách

- Modern
- Clean
- Minimal
- Professional
- Emergency / Safety
- Dễ nhìn trong tình huống khẩn cấp
- Ưu tiên thông tin quan trọng
- Không quá nhiều chữ
- Hành động quan trọng phải nổi bật

### Màu chủ đạo

| Vai trò | Màu |
|---|---|
| Primary | `#1976D2` |
| Dark Blue | `#0D47A1` |
| Emergency Red | `#E53935` |
| Warning Orange | `#FB8C00` |
| Success Green | `#43A047` |
| Background | `#F5F8FC` |
| White | `#FFFFFF` |
| Text | `#1E293B` |
| Secondary text | `#64748B` |

### Quy tắc UI

- Rounded corners.
- Card bo góc.
- Shadow rất nhẹ.
- Spacing rộng và sạch.
- Icon rõ ràng.
- Button lớn, dễ chạm.
- Typography dễ đọc.
- Không lạm dụng gradient.
- Không làm UI quá màu mè.

### Spacing / Shape

- Spacing: `4 / 8 / 12 / 16 / 20 / 24 / 32dp`
- Corner radius: `8 / 12 / 16 / 20 / 24dp`
- Button height: `48–52dp`
- Card radius: `16dp`

---

## 5. Responsive & Accessibility

Phải hỗ trợ tốt:

- Điện thoại nhỏ.
- Điện thoại trung bình.
- Điện thoại lớn.
- Portrait.
- Tablet cơ bản nếu có thể.

Không được có:

- Text overflow.
- Button bị tràn.
- Card bị cắt.
- Icon lệch.
- Bottom navigation bị che.
- Nội dung nằm ngoài màn hình.

Ưu tiên:

- Adaptive layout.
- `LazyColumn` / `LazyRow`.
- `Box` / `Column` / `Row`.
- ConstraintLayout/Compose layout khi cần.
- WindowInsets.
- Touch target khoảng `48dp` trở lên.
- Độ tương phản tốt.
- Không dùng icon là tín hiệu duy nhất cho cảnh báo.
- Không dùng animation quá mạnh.

---

## 6. Component dùng chung

Tạo các component tái sử dụng:

- `AppTopBar`
- `AppBottomNavigation`
- `EmergencyAlertCard`
- `WarningCard`
- `DisasterTypeCard`
- `NewsCard`
- `RescueMissionCard`
- `RescueMissionStatus`
- `PrimaryButton`
- `SecondaryButton`
- `EmergencyButton`
- `SOSButton`
- `SearchBar`
- `FilterChip`
- `StatusChip`
- `ProfileHeader`
- `InfoRow`
- `SectionHeader`
- `EmptyState`
- `LoadingState`
- `ErrorState`
- `ConfirmationDialog`
- `BottomSheet`
- `MapLegend`
- `NotificationItem`
- `VolunteerCard`
- `TeamMemberCard`

Không copy/paste UI giữa các màn hình nếu có thể tạo component dùng chung.

---

## 7. Navigation tổng thể

### Auth Flow

`Splash → Onboarding → Login → Register → Role Selection → Home`

### Bottom Navigation

1. Trang chủ
2. Bản đồ
3. SOS
4. Nhiệm vụ
5. Cá nhân

SOS nằm ở giữa và nổi bật hơn các tab còn lại.

### Theo vai trò

#### Người dùng thường

- Trang chủ
- Bản đồ
- SOS
- Thông báo
- Cá nhân

#### Nhà hoạt động cứu hộ

- Trang chủ
- Bản đồ
- SOS
- Nhiệm vụ
- Cá nhân

---

# 8. PHÂN PHASE THỰC HIỆN

## QUY ƯỚC QUAN TRỌNG

### PHASE 1–3: ĐỂ RIÊNG CHO PROMPT ĐẶC BIỆT

**Không triển khai theo các prompt 5 màn hình ở phần dưới.**

Phase 1–3 sẽ được viết thành **một prompt riêng chuyên dùng cho Antigravity**, tập trung dựng thật sát hình tham chiếu:

- Phase 1: nền tảng project + design system + navigation + reusable components.
- Phase 2: Splash + Onboarding + Login + Register + Role Selection.
- Phase 3: Home + Warning List + Storm Detail + Flood Detail + Map.

Mục tiêu của prompt đặc biệt này là tạo nền móng và phần UI cốt lõi sát hình tham chiếu nhất có thể.

> **Khi cần, sẽ yêu cầu prompt riêng cho Phase 1–3 trong một lần, thay vì chia 5 màn hình.**

---

# 9. CÁC PHASE CÒN LẠI: MỖI LẦN CHỈ VIẾT 5 MÀN HÌNH

Sau Phase 1–3, các phần còn lại sẽ **không yêu cầu Antigravity code hàng loạt quá lớn trong một prompt**.

Mỗi lần sẽ tạo **1 prompt = 5 màn hình**, kèm:

- mục tiêu UI
- thành phần cần có
- navigation
- mock data
- interaction
- state
- yêu cầu responsive
- yêu cầu code quality
- build/test sau batch

Cách đánh số dưới đây chỉ dùng để quản lý tiến độ. Khi người dùng yêu cầu “viết 5 màn hình tiếp theo”, lấy đúng batch kế tiếp.

---

## PHASE 4 – NHÓM CỨU HỘ / NHIỆM VỤ / CHAT

### Batch 4A – 5 màn hình đầu tiên

1. Volunteer Registration – Step 1
2. Volunteer Registration – Step 2
3. Volunteer Registration – Step 3
4. Rescue Mission List
5. Rescue Mission Detail

### Batch 4B – 5 màn hình tiếp theo

6. Rescue Chat
7. Volunteer / Rescue Team List
8. Rescue Team Detail
9. Mission Confirmation / Join Mission
10. Mission Status / Update Progress

> Các màn hình 7–10 là phần bổ sung UX cần thiết để flow cứu hộ thực tế hoàn chỉnh; khi triển khai phải giữ cùng Design System và không làm lệch giao diện tham chiếu.

---

## PHASE 5 – SOS / THÔNG BÁO / HỒ SƠ / CÀI ĐẶT

### Batch 5A – 5 màn hình đầu tiên

11. SOS Screen
12. SOS Confirmation / Countdown
13. Notification Screen
14. Profile Screen
15. Settings Screen

### Batch 5B – 5 màn hình tiếp theo

16. Notification Detail
17. Personal Information
18. Skills & Expertise
19. Activity History / Rescue History
20. Certificates / Achievements

---

## PHASE 6 – AUTH BỔ SUNG / HỆ THỐNG / THÔNG TIN ỨNG DỤNG

### Batch 6A – 5 màn hình đầu tiên

21. Forgot Password
22. OTP Verification
23. Reset Password / Create New Password
24. App About
25. Privacy / Terms Entry Screen

### Batch 6B – 5 màn hình tiếp theo

26. Help Center
27. Contact / Support
28. Privacy Settings
29. Location Permission / Explanation
30. Notification Permission / Explanation

---

## PHASE 7 – POLISH / EDGE CASE / HOÀN THIỆN

Phase 7 không tập trung tạo feature lớn mới mà tập trung đưa app về trạng thái có thể demo ổn định.

### Batch 7A – 5 hạng mục UI/UX

31. Loading State toàn hệ thống
32. Empty State toàn hệ thống
33. Error State / Offline State
34. Confirmation Dialogs / Bottom Sheets
35. Dark Mode polish

### Batch 7B – 5 hạng mục kiểm thử và hoàn thiện

36. Responsive check toàn bộ màn hình
37. Navigation audit toàn app
38. Overflow / layout audit
39. Accessibility / touch target audit
40. Build APK debug + sửa compile/runtime error cuối cùng

---

# 10. Bộ màn hình chính theo hình tham chiếu

Các màn hình cốt lõi ban đầu cần bám sát reference gồm:

1. Splash
2. Onboarding 1
3. Onboarding 2
4. Onboarding 3
5. Login
6. Register
7. Role Selection
8. Home
9. Disaster Map
10. Warning List
11. Storm Detail
12. Flood Detail
13. Volunteer Registration Step 1
14. Volunteer Registration Step 2
15. Volunteer Registration Step 3
16. Rescue Mission List
17. Rescue Mission Detail
18. Rescue Chat
19. SOS
20. Notifications
21. Profile
22. Settings
23. Forgot Password
24. OTP Verification
25. About App

Nguồn yêu cầu gốc đã xác định các nhóm màn hình trên, cùng yêu cầu interaction, mock data, dark mode, loading/empty/error state và quy trình build/test sau từng phase. fileciteturn0file0L237-L245 fileciteturn0file0L956-L971 fileciteturn0file0L1117-L1171

---

# 11. Mock Data cơ bản

### Storm

- Bão số 3 (YAGI)

### Flood

- Cảnh báo lũ tại Quảng Nam

### Rescue missions

- Hỗ trợ di dời người dân vùng ngập
- Tiếp tế lương thực, nước uống
- Kiểm tra an toàn khu vực

### Users

- Nguyễn Văn A
- Trần Văn B
- Lê Minh C
- Phạm Văn D

---

# 12. Map

Nếu chưa tích hợp Google Maps:

- Tạo map mockup bằng Box/Image/Canvas.
- Có marker.
- Có route.
- Có danger zone.
- Có rescue point.
- Có flood area.
- Có legend.
- Có zoom.
- Có nút vị trí hiện tại.

Cấu trúc code phải dễ thay bằng Google Maps API sau này.

---

# 13. SOS – yêu cầu an toàn UI

SOS là chức năng đặc biệt quan trọng.

Không được gửi SOS ngay khi chạm một lần.

Flow:

`Press & Hold → Confirmation → Countdown → Loading → Mock SOS Sent`

Phải hiển thị:

- Vị trí hiện tại
- Latitude
- Longitude
- Địa chỉ
- Nút gọi khẩn cấp 112

---

# 14. UI State bắt buộc

Mỗi màn hình quan trọng phải có khả năng thể hiện:

- Loading State
- Success State
- Empty State
- Error State

Ví dụ:

- Không có cảnh báo.
- Không có nhiệm vụ.
- Không có thông báo.
- Không có kết quả tìm kiếm.
- Không có kết nối mạng.

---

# 15. Dark Mode

Hỗ trợ Dark Theme cơ bản.

Giữ được các màu semantic quan trọng:

- Emergency Red
- Warning Orange
- Success Green

Đồng thời thay đổi background và text cho phù hợp theme tối.

---

# 16. Interaction chính

Các flow quan trọng phải hoạt động:

- Home → Warning Detail
- Home → Map
- Map → Warning Detail
- Warning List → Detail
- Mission List → Mission Detail
- Mission Detail → Confirmation
- Profile → Rescue Registration
- Notification → Detail
- SOS → Press & Hold → Confirmation → Sent
- Settings → Dark Mode
- Login → Home
- Register → Role Selection

---

# 17. Kiến trúc code đề xuất

```text
com.example.disasteralert

├── data/
│   ├── model/
│   ├── repository/
│   └── local/
│
├── domain/
│   ├── model/
│   └── repository/
│
├── presentation/
│   ├── navigation/
│   ├── components/
│   ├── theme/
│   ├── auth/
│   ├── home/
│   ├── map/
│   ├── warning/
│   ├── rescue/
│   ├── notification/
│   ├── profile/
│   ├── settings/
│   └── sos/
│
└── ui/
```

Không viết toàn bộ ứng dụng vào một file.

---

# 18. Quy tắc cho mỗi prompt Antigravity sau này

Mỗi prompt batch 5 màn hình phải bắt đầu bằng các yêu cầu:

```text
- Đọc project hiện tại trước khi sửa.
- Không phá các màn hình/flow đã hoàn thành.
- Giữ nguyên Design System đã có.
- Bám sát hình reference.
- Tái sử dụng component.
- Không duplicate code.
- Tạo navigation cho 5 màn hình mới.
- Tạo mock data cần thiết.
- Tạo loading/empty/error state phù hợp.
- Build project sau khi hoàn thành.
- Sửa compile error và layout overflow trước khi kết thúc.
- Không để TODO cho UI chính.
```

---

# 19. Thứ tự yêu cầu prompt trong các lượt tiếp theo

### Lượt 1

**Prompt đặc biệt Phase 1–3:**

> Dựng nền tảng + Splash/Onboarding/Auth + Home/Map/Warning theo reference, ưu tiên visual fidelity.

### Lượt 2

**5 màn hình tiếp theo:** Batch 4A

### Lượt 3

**5 màn hình tiếp theo:** Batch 4B

### Lượt 4

**5 màn hình tiếp theo:** Batch 5A

### Lượt 5

**5 màn hình tiếp theo:** Batch 5B

### Lượt 6

**5 màn hình tiếp theo:** Batch 6A

### Lượt 7

**5 màn hình tiếp theo:** Batch 6B

### Lượt 8

**5 hạng mục hoàn thiện:** Batch 7A

### Lượt 9

**5 hạng mục kiểm thử / hoàn thiện cuối:** Batch 7B

---

# 20. Tiêu chí hoàn thành

Ứng dụng sau khi hoàn thiện phải có cảm giác như một app Android thật:

- Đăng nhập.
- Đăng ký.
- Chọn vai trò.
- Xem cảnh báo.
- Xem bản đồ.
- Xem bão.
- Xem lũ.
- Nhận thông báo.
- Gửi SOS.
- Đăng ký cứu hộ.
- Xem nhiệm vụ.
- Tham gia nhiệm vụ.
- Chat đội cứu hộ.
- Xem hồ sơ.
- Cài đặt.

Sau mỗi batch:

1. Build project.
2. Sửa compile error.
3. Kiểm tra navigation.
4. Kiểm tra responsive.
5. Kiểm tra layout overflow.
6. Xác nhận 5 màn hình mới chạy được trước khi chuyển sang batch tiếp theo.

---

## 21. Ghi chú triển khai

**Không cần viết prompt Phase 1–3 vào các batch 5 màn hình bên dưới.** Phase 1–3 được giữ lại để tạo một prompt chuyên biệt, chi tiết hơn, tối ưu cho việc dựng giao diện cốt lõi sát hình tham chiếu.

**Từ Phase 4 trở đi, mặc định mỗi lần chỉ tạo 5 màn hình/hạng mục.** Không gộp quá 5 màn hình vào một prompt trừ khi có yêu cầu cụ thể khác.