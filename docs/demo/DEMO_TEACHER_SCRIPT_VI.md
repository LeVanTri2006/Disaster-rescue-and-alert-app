# Kịch bản trình bày 3–5 phút — ngày 10/11/2026

**Phạm vi trung thực:** demo diagnostic và SOS fixture. Không có tuyến cứu hộ đã xác minh. Trên điện thoại, lời gọi định tuyến thật dừng tại safety gate; không tuyên bố vòng lặp tìm kiếm UCS đã chạy trong app.

## 0:00–0:40 — Mở và đăng nhập

Mở **P5-E09 Demo Chẩn đoán** sau khi chạy phiên USB trong runbook. Bấm **Đăng nhập demo**.

> Đây là APK riêng, dùng tài khoản tổng hợp và máy chủ fixture trên laptop. Demo không đọc GPS, không dùng Firebase production và không gửi yêu cầu cứu hộ thật. Dòng cảnh báo trên màn hình luôn phân biệt mô phỏng với tuyến đã xác minh.

## 0:40–1:30 — Đồ thị và thuật toán

Chỉ vào A, B, các giao lộ và H trên graph. Bấm **Phân tích đường đi**.

> Mạng đường được biểu diễn bằng đồ thị có hướng. Mỗi cạnh mang chiều dài, tình trạng đường và metadata. Thuật toán trong source Android là Uniform Cost Search, hay UCS: dùng priority queue lấy trạng thái có tổng chi phí nhỏ nhất. Trạng thái còn lưu cạnh đi vào và trạng thái hạn chế rẽ, để xử lý luật rẽ đúng.
>
> Hàm mục tiêu trong kernel là tổng chiều dài cạnh nhân hệ số rủi ro. Nó không tối ưu thời gian đến nơi. Ví dụ trong unit test hiện có, một cạnh synthetic dài 150 có cost 150. Test chọn nhiều tuyến so sánh A–B–D có cost 10 + 15 = 25, A–C–D có cost 5 + 30 = 35 và A–E–D có cost 12 + 10 = 22; kernel chọn A–E–D vì tổng 22 nhỏ nhất, dù cạnh đầu A–C có cost 5 thấp hơn cạnh A–E có cost 12. Các dữ liệu đủ điều kiện trong test chỉ tồn tại ở môi trường unit test, không phải xác minh thực địa.

Có thể mở XML và source hai test `testDirectClearRoute`, `testMultipleRoutesLowestCostSelection` trên laptop để đối chiếu assertions và kết quả chạy mới. Helper kiểm toán private kernel chỉ nằm trong `src/test`; không được đóng gói thành nút mở khóa trong demo APK.

## 1:30–2:30 — Thay đổi điều kiện và giải thích kết quả thật

Bấm **Chặn AC**, rồi **Phân tích đường đi**. Bấm **Cấm rẽ**, phân tích lại. Nếu cần, cuộn vùng kết quả. Bấm **Graph lỗi** để xem parser từ chối; bấm **Gốc** để trở lại dữ liệu hợp lệ.

> Dữ liệu trên graph thay đổi thật. Đoạn AC bị chặn được tô đỏ. Hazard overlay này chỉ thuộc adapter test-only: parser base graph hiện khởi tạo CLEAR, nên adapter chủ động đặt UNKNOWN khi chưa có thông tin; không tuyên bố đây là hazard pipeline production. TurnRestrictionIndex thật chuyển kiểm tra AC→CB từ ALLOW sang DENY_PROHIBITED trong tình huống cấm rẽ. ALLOW ở đây chỉ là kết quả kiểm tra luật rẽ, không phải quyền đi trên đường.
>
> Kết quả định tuyến public vẫn là STRICT_ROUTING_DISABLED. Hiện chưa có qualification authority nên app dừng trước vòng lặp UCS. Vì vậy màn hình ghi path rỗng và cost N/A; em không vẽ đường cứng rồi gọi đó là đường thuật toán tính ra. Đổi đích sang H cũng không được phép nói app đã tính NO_PATH: safety gate dừng trước bước tìm kiếm.

## 2:30–3:40 — Yêu cầu cứu hộ mô phỏng

Bấm **Cứu hộ fixture**, **Thêm người**, rồi **Gửi tới fixture** ở chế độ **HTTP OK**. Chỉ vào `FIXTURE_RECEIVED`, event ID bắt đầu `SYNTHETIC-E09-`, trạng thái `ACTIVE`. Quay về **Map / UCS** để xem điểm tím từ tọa độ phản hồi.

> Form này là test-only. Nó gọi ApiSosTransport và OkHttpSosApiClient thật của ứng dụng tới fixture local. Tọa độ lấy từ graph synthetic, không phải vị trí điện thoại. ACTIVE chỉ là trạng thái phản hồi fixture; không có đội cứu hộ được điều phối, push, email hay SMS.

Có thể chọn **HTTP 403** hoặc **Offline** rồi gửi để minh họa lỗi được phản ánh trên UI. Không dùng lỗi mạng để khẳng định yêu cầu thật chắc chắn chưa được tiếp nhận.

## 3:40–4:20 — Kết luận

> Kết quả hiện có chứng minh graph/parser, luật rẽ, kiểm tra an toàn và transport SOS đã nối được vào demo Android trên điện thoại thật. Kernel UCS có kiểm thử tính toán, nhưng app chưa được phép xuất tuyến cứu hộ. Còn thiếu authority, hazard/freshness đáng tin cậy và phần tích hợp đường tính được vào bản đồ production. Vì vậy đây là demo diagnostic có điều kiện, không phải full production rescue navigation.

## Phương án dự phòng

- Mất USB/fixture: tại màn hình đầu bấm **Chẩn đoán offline**; graph và lời gọi router public vẫn chạy, SOS bị vô hiệu hóa khi chưa đăng nhập.
- Không chạy được UI: mở `evidence/physical-final/TEST-demo.xml`, `results.json`, phần `actual_outputs`, và các XML JVM trong `evidence/jvm-build/`.
- Không nói đã hoàn thiện Google Maps, định tuyến tối ưu trên điện thoại, mission accept/complete, GPS, cloud hay F06.
