# API Guide 0 - Tổng Quan Module

## 1. Mục đích

Tài liệu này dùng để:

- liệt kê toàn bộ module API của `ExamService`
- chỉ ra file guide chi tiết tương ứng cho từng module
- làm điểm bắt đầu cho frontend khi cần tra cứu API

Theo yêu cầu hiện tại:

- `API Guide 0` chỉ đóng vai trò tổng quan
- mỗi module sẽ có một file `.md` riêng
- nội dung chi tiết từng module sẽ được viết tiếp theo từng yêu cầu riêng, không gộp toàn bộ trong một lần

---

## 2. Danh sách module

### 2.1. Question Module

- Mục đích:
  - quản lý ngân hàng câu hỏi
  - tạo, sửa, xem chi tiết, tìm kiếm câu hỏi
- File guide chi tiết:
  - [.github/guide/QUESTION_MODULE_API.md](D:/DoAn/DoAn1/ExamService/ExamService/.github/guide/QUESTION_MODULE_API.md)
- Trạng thái:
  - đã có API

### 2.2. Exam Module

- Mục đích:
  - tạo đề thi
  - sửa đề thi
  - xem chi tiết đề
  - tìm kiếm danh sách đề
- File guide chi tiết:
  - [.github/guide/EXAM_MODULE_API.md](D:/DoAn/DoAn1/ExamService/ExamService/.github/guide/EXAM_MODULE_API.md)
- Trạng thái:
  - đã có API

### 2.3. Question Group Module

- Mục đích:
  - tạo nhóm câu hỏi dùng lại được
  - xem chi tiết nhóm câu hỏi
  - tìm kiếm danh sách nhóm câu hỏi
- File guide chi tiết:
  - [.github/guide/QUESTION_GROUP_MODULE_API.md](D:/DoAn/DoAn1/ExamService/ExamService/.github/guide/QUESTION_GROUP_MODULE_API.md)
- Trạng thái:
  - đã có API

### 2.4. Exam Assignment Module

- Mục đích:
  - giao đề cho đối tượng được phép làm bài
  - phục vụ các rule kiểm soát ai được quyền vào đề
- File guide chi tiết:
  - [.github/guide/EXAM_ASSIGNMENT_MODULE_API.md](D:/DoAn/DoAn1/ExamService/ExamService/.github/guide/EXAM_ASSIGNMENT_MODULE_API.md)
- Trạng thái:
  - chưa có API hoàn chỉnh trong backend hiện tại

### 2.5. Exam Attempt Module

- Mục đích:
  - học sinh bắt đầu làm bài
  - lưu đáp án trong quá trình làm
  - xem chi tiết attempt
  - nộp bài
  - xem danh sách các lần làm bài
- File guide chi tiết:
  - [.github/guide/EXAM_ATTEMPT_MODULE_API.md](D:/DoAn/DoAn1/ExamService/ExamService/.github/guide/EXAM_ATTEMPT_MODULE_API.md)
- Trạng thái:
  - đã có API

### 2.6. System / Health Module

- Mục đích:
  - kiểm tra trạng thái sống của service
  - phục vụ monitoring hoặc kiểm tra kết nối cơ bản
- File guide chi tiết:
  - [.github/guide/SYSTEM_HEALTH_MODULE_API.md](D:/DoAn/DoAn1/ExamService/ExamService/.github/guide/SYSTEM_HEALTH_MODULE_API.md)
- Trạng thái:
  - đã có API

### 2.7. Scoring Rules

- Mục đích:
  - mô tả riêng nghiệp vụ chấm điểm
  - làm tài liệu thống nhất cho backend, frontend và QA
- File guide chi tiết:
  - [.github/guide/SCORING_RULES.md](D:/DoAn/DoAn1/ExamService/ExamService/.github/guide/SCORING_RULES.md)
- Trạng thái:
  - đã có tài liệu

---

## 3. Thứ tự đề xuất để viết guide chi tiết

Nếu viết tiếp theo mức độ ưu tiên cho frontend, mình đề xuất:

1. `Question Module`
2. `Exam Module`
3. `Question Group Module`
4. `Exam Attempt Module`
5. `Exam Assignment Module`
6. `System / Health Module`
7. `Scoring Rules`

---

## 4. Quy ước chung khi viết guide chi tiết cho từng module

Mỗi file guide module sẽ có:

- đường dẫn API
- input format
- output format
- các exception có thể trả về
- mô tả luồng ngắn gọn của từng API

Ví dụ phong cách mô tả luồng:

- nhận request
- validate dữ liệu đầu vào
- gọi các service liên quan
- lưu hoặc truy vấn dữ liệu
- xử lý logic liên quan đến module khác nếu có
- trả response

---

## 5. Ghi chú hiện tại

- Tài liệu này mới là phần khung tổng quan
- Nội dung chi tiết của từng module sẽ được bổ sung dần theo yêu cầu tiếp theo của bạn
