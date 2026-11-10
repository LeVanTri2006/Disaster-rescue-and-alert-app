# Kịch bản trình bày M02 — 3–5 phút

“Đây là ứng dụng demo riêng. Tất cả tài khoản, thông tin và tọa độ đều mô phỏng. Google Maps là SDK thật; SOS chỉ tới máy chủ fixture trên laptop, không điều động cứu hộ thật.”

1. Mở phiên theo RUNBOOK_USB, giữ cáp và terminal. Chọn Người dân TEST1 → Người dân demo → SOS synthetic. Chọn B, thêm người để có2 người, giữ mô tả synthetic, HTTP OK → Gửi. Chỉ vào UUID và trạng thái ACTIVE do fixture trả về.
2. Đăng xuất. “Ứng dụng xóa phiên người dân, nhưng yêu cầu vẫn được giữ tại fixture để đội cứu hộ đọc.”
3. Đội cứu hộ demo → chờ tiles Google thật. “Pin xanh là đội cứu hộ TEST cố định; không dùng GPS.” Trước refresh chưa có SOS pin. Bấm Refresh SOS: pin đỏ xuất hiện từ GET /sos/active.
4. Bấm pin đỏ. Đọc đúng UUID, ACTIVE,2người, mô tả và16.051/108.203 trong panel. “Marker dùng chính tọa độ server response, không tạo từ danh sách hardcode.” Native info window có thể rút gọn UUID/mô tả; panel hiển thị đầy đủ.
5. Đăng xuất, chọn TEST2 → gửi tại H → đăng xuất/cứu hộ/refresh. Chỉ ra hai UUID và hai vị trí. Refresh lần nữa vẫn hai, không gửi POST mới. Có thể zoom/pan; dữ liệu SOS không đổi.
6. Nếu hai bản ghi cùng tọa độ, chọn từng nút UUID trong danh sách bên dưới map. Tọa độ địa lý vẫn nguyên, không dời pin.
7. Tùy thời gian: HTTP500 → refresh hiện GET_ERROR và bỏ pin cũ. HTTP OK → refresh phục hồi từ fixture. Không nói request timeout chắc chắn thất bại: server có thể đã lưu; không tự retry.

“Phạm vi hôm nay là tạo SOS và chia sẻ marker giữa hai vai trò. Chưa nhận nhiệm vụ, chưa thuật toán UCS/tuyến đường hoặc GPS trực tiếp. F06 OPEN và strict routing vẫn disabled.”

Ctrl+C kết thúc fixture/reverse của phiên. Không đụng app production/M01/E10A/E09. Phiên mới có store RAM mới; không dùng dữ liệu cá nhân.
