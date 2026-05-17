# Tổng Kết Công Việc Đã Triển Khai Cho ExamService

## 1. Mục tiêu đã thực hiện

Mình đã triển khai phần lõi của `ExamService` theo đúng hướng microservice thi cử, bao gồm:

- dựng nền Spring Boot service
- cấu hình security theo mô hình `OAuth2 Resource Server`
- xây dựng module `Question`
- xây dựng module `Exam`
- xây dựng module `ExamAttempt`
- hỗ trợ random câu hỏi theo `QuestionGroup`
- hỗ trợ lưu lịch sử đáp án
- hỗ trợ chấm điểm
- hỗ trợ auto-submit khi hết thời gian

---

## 2. Phần nền project

Đã setup các thành phần nền sau:

- chuẩn hóa cấu trúc service cho `ExamService`
- dùng `OAuth2 Resource Server` để validate JWT
- không tự tạo token trong service này
- backend hiện nhận `access token` từ `Management Service`
- user hiện tại được đọc từ claim `user.id`
- bật JPA auditing
- dựng response wrapper và exception handling
- externalize cấu hình bằng biến môi trường `EXAMSERVICE_*`

Các file tiêu biểu:

- [ExamServiceApplication.java](D:/DoAn/DoAn1/ExamService/ExamService/src/main/java/com/DoAn1/examservice/ExamServiceApplication.java)
- [SecurityConfiguration.java](D:/DoAn/DoAn1/ExamService/ExamService/src/main/java/com/DoAn1/examservice/config/SecurityConfiguration.java)
- [GlobalExceptionHandler.java](D:/DoAn/DoAn1/ExamService/ExamService/src/main/java/com/DoAn1/examservice/exception/GlobalExceptionHandler.java)
- [application.properties](D:/DoAn/DoAn1/ExamService/ExamService/src/main/resources/application.properties)

---

## 3. Domain schema

Đã tạo nền entity và repository cho các bảng chính:

- `Question`
- `QuestionMcOption`
- `QuestionTrueFalseStatement`
- `QuestionAnswerKey`
- `Exam`
- `ExamQuestion`
- `ExamQuestionGroup`
- `ExamQuestionGroupItem`
- `ExamAssignment`
- `ExamAttempt`
- `StudentAnswer`
- `ExamProctoringEvent`

---

## 4. Module Question

### Chức năng đã làm

- tạo câu hỏi
- lấy chi tiết câu hỏi
- lấy danh sách câu hỏi có filter và pagination
- cập nhật câu hỏi
- bật hoặc tắt trạng thái hoạt động

### Validation nghiệp vụ đã có

- `MCQ`
  - đúng 4 lựa chọn `A/B/C/D`
- `TFQ`
  - đúng 4 mệnh đề
- `SAQ`
  - không có options hoặc true/false statements
- validate đáp án đúng theo từng loại câu hỏi
- normalize đáp án trước khi lưu

### File chính

- [QuestionController.java](D:/DoAn/DoAn1/ExamService/ExamService/src/main/java/com/DoAn1/examservice/controller/QuestionController.java)
- [QuestionService.java](D:/DoAn/DoAn1/ExamService/ExamService/src/main/java/com/DoAn1/examservice/service/QuestionService.java)

---

## 5. Module Exam

### Chức năng đã làm

- tạo đề thi
- lấy chi tiết đề thi
- lấy danh sách đề thi có filter và pagination
- cập nhật đề thi
- cập nhật trạng thái đề

### Cách nạp câu hỏi vào đề

Hệ thống hiện hỗ trợ 2 cách:

- nạp câu hỏi lẻ qua `ExamQuestion`
- nạp câu hỏi theo nhóm qua `ExamQuestionGroup`

Ngoài ra, `question group` hiện đã được tách thành module riêng:

- có thể tạo group độc lập để tái sử dụng
- khi tạo đề, user có thể:
  - dùng `question group` có sẵn
  - hoặc tạo mới `question group` ngay trong payload tạo đề
- backend sẽ snapshot group được chọn vào `ExamQuestionGroup` và `ExamQuestionGroupItem` của đề

### Quy tắc đã hỗ trợ

- một `Exam` có thể có nhiều group
- cùng một loại câu hỏi như `MCQ` có thể có nhiều group khác nhau
- một `Exam` có thể vừa có câu hỏi lẻ vừa có câu hỏi trong group
- mỗi group có:
  - `poolQuestionCount`
  - `pickQuestionCount`
  - `scorePerQuestion`

### Response cho frontend

Response của `Exam` đã được tổ chức theo:

- `MCQ`
- `TFQ`
- `SAQ`

Mỗi loại có:

- tổng số câu
- danh sách câu hỏi lẻ
- danh sách group
- thông tin tóm tắt và chi tiết câu hỏi

### File chính

- [ExamController.java](D:/DoAn/DoAn1/ExamService/ExamService/src/main/java/com/DoAn1/examservice/controller/ExamController.java)
- [ExamService.java](D:/DoAn/DoAn1/ExamService/ExamService/src/main/java/com/DoAn1/examservice/service/ExamService.java)
- [ResExamDTO.java](D:/DoAn/DoAn1/ExamService/ExamService/src/main/java/com/DoAn1/examservice/domain/responseDTO/exam/ResExamDTO.java)
- [ResExamQuestionTypeSectionDTO.java](D:/DoAn/DoAn1/ExamService/ExamService/src/main/java/com/DoAn1/examservice/domain/responseDTO/exam/ResExamQuestionTypeSectionDTO.java)

---

## 6. Random câu hỏi theo group

Đã triển khai theo đúng hướng đã chốt:

- group chứa một pool câu hỏi
- tại thời điểm học sinh bắt đầu attempt, hệ thống random ra `n` câu từ pool `m`
- `n` được lưu ở `pickQuestionCount`
- snapshot câu hỏi được lưu vào attempt để tránh thay đổi giữa chừng

Các file liên quan:

- [ExamQuestionGroup.java](D:/DoAn/DoAn1/ExamService/ExamService/src/main/java/com/DoAn1/examservice/domain/entity/ExamQuestionGroup.java)
- [ExamAttempt.java](D:/DoAn/DoAn1/ExamService/ExamService/src/main/java/com/DoAn1/examservice/domain/entity/ExamAttempt.java)

---

## 7. Module ExamAttempt

### API đã có

- `POST /api/v1/student/exams/{examUuid}/attempts`
- `GET /api/v1/student/attempts/{attemptUuid}`
- `GET /api/v1/student/attempts`
- `POST /api/v1/student/attempts/{attemptUuid}/answers`
- `POST /api/v1/student/attempts/{attemptUuid}/submit`

### Chức năng đã làm

- bắt đầu làm bài
- lấy chi tiết attempt
- lưu đáp án từng lần thay đổi
- nộp bài
- lấy danh sách attempt của học sinh
- filter danh sách attempt theo `examUuid`

### Rule nghiệp vụ đã có

- chỉ cho start attempt khi đề ở trạng thái `PUBLISHED`
- kiểm tra thời gian mở và đóng đề
- kiểm tra số lần làm tối đa
- lấy `studentUuid` từ claim `user.id` trong `access token`
- mỗi lần đổi đáp án đều insert thêm một bản ghi `StudentAnswer`
- khi submit, tạo `final answer`
- chấm điểm theo `final answer`
- response detail attempt trả về cả:
  - `currentRawAnswer`
  - `currentNormalizedAnswer`
  để frontend vừa hiển thị trạng thái làm bài, vừa debug dữ liệu OMR nếu cần

### File chính

- [ExamAttemptController.java](D:/DoAn/DoAn1/ExamService/ExamService/src/main/java/com/DoAn1/examservice/controller/ExamAttemptController.java)
- [ExamAttemptService.java](D:/DoAn/DoAn1/ExamService/ExamService/src/main/java/com/DoAn1/examservice/service/ExamAttemptService.java)
- [ResExamAttemptDTO.java](D:/DoAn/DoAn1/ExamService/ExamService/src/main/java/com/DoAn1/examservice/domain/responseDTO/attempt/ResExamAttemptDTO.java)
- [ResExamAttemptSummaryDTO.java](D:/DoAn/DoAn1/ExamService/ExamService/src/main/java/com/DoAn1/examservice/domain/responseDTO/attempt/ResExamAttemptSummaryDTO.java)

---

## 8. Cơ chế lưu bài làm

Đã hỗ trợ lưu dần từng lần học sinh thay đổi đáp án:

- mỗi lần gọi API lưu đáp án, hệ thống insert thêm một bản ghi `StudentAnswer`
- backend có thể phục hồi lịch sử trả lời theo từng câu
- khi submit, hệ thống sinh `final answer` để dùng cho chấm điểm

Điều này giúp:

- không bị mất toàn bộ bài khi học sinh đã lưu một phần
- có lịch sử thao tác để phục vụ audit hoặc phân tích sau này

---

## 9. Chấm điểm

### MCQ

- với luồng `WEB`, nếu đáp án chuẩn vô tình chứa nhiều phương án đúng như `AD`, học sinh chọn `A` hoặc `D` vẫn được tính đúng
- với luồng `OMR_IMPORT`, học sinh phải có đúng 1 lựa chọn hợp lệ mới được xét đúng
- với luồng `OMR_IMPORT`, nếu học sinh tô nhiều hơn 1 đáp án cho cùng một câu, hệ thống có thể biểu diễn bằng ký tự `M` và tính câu đó là sai

### TFQ

- chấm theo số lượng mệnh đề đúng
- dùng các mốc phần trăm:
  - `tfCorrect1Pct`
  - `tfCorrect2Pct`
  - `tfCorrect3Pct`
  - `tfCorrect4Pct`
- đã thống nhất quy ước:
  - đáp án chuẩn dùng `D/S/N`
  - đáp án học sinh dùng `D/S/B`
  - `B` là `Bỏ trống`
  - nếu đáp án chuẩn tại một vị trí là `N`, học sinh chỉ cần trả lời khác `B` thì được tính đúng ý đó

### SAQ

- chấm theo so khớp exact với đáp án đã normalize
- đã chốt quy ước lưu/chấm:
  - mỗi đáp án có độ dài từ `1` đến `4` ký tự
  - chỉ dùng các ký tự số, `-`, `,`
  - `-` chỉ ở vị trí đầu tiên
  - `,` chỉ ở vị trí thứ `2` hoặc `3`
  - nhiều đáp án đúng được phân cách bằng `;`
  - hệ thống chuẩn hóa theo mô hình `4 cột`
  - đáp án chuẩn được pad bên phải bằng `_` cho đủ `4` ký tự
  - đáp án học sinh thay dấu cách bằng `_` rồi pad bên phải cho đủ `4` ký tự
  - nếu trùng với một trong các đáp án đúng đã chuẩn hóa thì được tính đúng
  - backend hỗ trợ thêm luồng `OMR` cho `SAQ`:
    - `rawAnswer` có thể lưu theo dạng 4 cột dùng dấu `|` làm delimiter
    - ví dụ: `|23|,|7`
    - từ `rawAnswer` đó, backend chuẩn hóa sang `normalizedAnswer`, ví dụ: `_M,7`
  - với `SAQ OMR`, quy ước từng cột là:
    - cột rỗng -> `_`
    - cột có đúng 1 ký tự hợp lệ -> giữ nguyên
    - cột có nhiều hơn 1 ký tự -> `M`
  - nếu có ký tự `M` trong `normalizedAnswer` của học sinh thì câu đó thực tế sẽ không khớp đáp án chuẩn và bị tính sai

---

## 10. Auto-submit

Đã làm auto-submit theo 2 lớp:

- scheduler nền quét các attempt quá hạn
- khi học sinh gọi `getAttempt` hoặc `saveAnswer`, nếu đã hết giờ thì hệ thống auto-submit ngay

### Quy tắc deadline

Deadline của attempt hiện tại là:

- `min(startedAt + durationMinutes, exam.endTime)`

Nếu đề không có `endTime`, hệ thống dùng:

- `startedAt + durationMinutes`

### Kết quả sau auto-submit

- `status = SCORED`
- `isAutoSubmitted = true`

File liên quan:

- [ExamServiceApplication.java](D:/DoAn/DoAn1/ExamService/ExamService/src/main/java/com/DoAn1/examservice/ExamServiceApplication.java)
- [ExamAttemptService.java](D:/DoAn/DoAn1/ExamService/ExamService/src/main/java/com/DoAn1/examservice/service/ExamAttemptService.java)

---

## 11. Tối ưu và convention đã áp dụng

- dùng service class trực tiếp, không dùng `impl`
- `@PathVariable(name = "...")`
- `@RequestParam(name = "...")`
- với `Question`, đã tối ưu phần xóa detail con theo `questionUuid`
- đã bổ sung repository method theo hướng batch-friendly cho các bảng con của `Question`

---

## 12. Trạng thái kiểm chứng

Sau các mốc triển khai chính, mình đã chạy:

```powershell
.\gradlew.bat test --no-daemon
```

Kết quả ở các lần kiểm tra gần nhất:

- `BUILD SUCCESSFUL`

---

## 13. Các phần chưa làm hoặc mới ở mức nền

- `ExamAssignment` với rule nghiệp vụ đầy đủ
- chỉ học sinh được assign mới được làm bài
- luồng resume attempt đang làm dở ở mức chuyên biệt
- cơ chế chống trùng request lưu đáp án ở mức nâng cao
- `ExamProctoringEvent` thực tế
- test unit và integration chuyên sâu cho từng module
- websocket realtime
- fuzzy grading cho `SAQ`
- API import hoàn chỉnh cho luồng `OMR_IMPORT`

---

## 14. Kết luận hiện tại

Tính đến thời điểm này:

- `Question` đã ở mức dùng được
- `Exam` đã ở mức dùng được
- `ExamAttempt` đã ở mức dùng được
- đã hỗ trợ random câu hỏi theo group
- đã hỗ trợ lưu lịch sử đáp án
- đã hỗ trợ chấm điểm
- đã hỗ trợ auto-submit

Phần còn lại nên ưu tiên tiếp theo là:

- `ExamAssignment`
- resume attempt đang làm dở
- tăng độ bền cho autosave và reconnect
