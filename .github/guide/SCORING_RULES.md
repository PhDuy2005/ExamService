# Scoring Rules

## 1. Mục đích

Tài liệu này mô tả riêng nghiệp vụ chấm điểm của `ExamService` theo logic backend hiện tại.

Mục tiêu của tài liệu:

- làm rõ hệ thống chấm điểm như thế nào
- thống nhất cách hiểu giữa backend, frontend và QA
- mô tả rõ các rule theo từng loại câu hỏi:
  - `MCQ`
  - `TFQ`
  - `SAQ`

Tài liệu này bám sát logic đang được triển khai trong:

- [ExamAttemptService.java](D:/DoAn/DoAn1/ExamService/ExamService/src/main/java/com/DoAn1/examservice/service/ExamAttemptService.java)

---

## 2. Tổng quan luồng chấm điểm

### 2.1. Khi nào hệ thống chấm điểm

Hệ thống chấm điểm khi:

- học sinh gọi `POST /api/v1/student/attempts/{attemptUuid}/submit`
- hoặc attempt bị `auto-submit` khi hết thời gian

### 2.2. Luồng chấm điểm tổng quát

Nộp bài hoặc auto-submit -> lấy snapshot câu hỏi của attempt -> lấy toàn bộ lịch sử đáp án của từng câu -> chọn đáp án mới nhất của từng câu -> tạo thêm bản ghi `final answer` -> lấy `QuestionAnswerKey` -> chấm từng câu theo `questionType` -> cộng tổng điểm -> cập nhật `ExamAttempt.score`, `submittedAt`, `timeSpentSeconds`, `status`, `isAutoSubmitted`

### 2.3. Hàm chính liên quan

- `finalizeAttempt(...)`
- `calculateQuestionScore(...)`
- `scoreMcqQuestion(...)`
- `scoreTrueFalseQuestion(...)`
- `scoreShortAnswerQuestion(...)`

---

## 3. Quy tắc chung

### 3.1. Đáp án nào được dùng để chấm

Backend chấm theo **đáp án cuối cùng** của học sinh trong mỗi câu.

Cụ thể:

- mỗi lần học sinh đổi đáp án, hệ thống insert một dòng `StudentAnswer` mới
- khi nộp bài, backend tạo thêm một dòng `StudentAnswer` với:
  - `isFinalAnswer = true`
- dữ liệu dùng để chấm là đáp án mới nhất của câu đó tại thời điểm submit

### 3.2. Khi nào một câu chắc chắn được tính 0 điểm

Một câu sẽ được tính `0` nếu:

- học sinh không có đáp án
- `QuestionAnswerKey` không tồn tại
- `normalizedAnswer` của học sinh không hợp lệ hoặc rỗng

### 3.3. Cách cộng điểm

Điểm toàn bài được tính bằng:

- tổng điểm của tất cả câu trong snapshot attempt

Không có cơ chế trừ điểm âm trong logic hiện tại.

---

## 4. Chuẩn hóa đáp án trước khi chấm

Trước khi chấm, backend chuẩn hóa đáp án học sinh theo từng loại câu hỏi.

### 4.1. Chuẩn hóa `MCQ`

- lấy `rawAnswer`
- loại bỏ các ký tự không thuộc `A-D`
- loại bỏ trùng
- sắp xếp tăng dần

Ví dụ:

- `A` -> `A`
- `DA` -> `AD`
- `A A` -> `A`

### 4.2. Chuẩn hóa `TFQ`

- chỉ giữ lại các ký tự `D`, `S`, `B`
- chỉ hợp lệ khi có đúng `4` ký tự

Ví dụ:

- `DSBD` -> `DSBD`
- `D S B D` -> `DSBD`
- `DSD` -> không hợp lệ

### 4.3. Chuẩn hóa `SAQ`

Backend hỗ trợ 2 kiểu:

#### Kiểu nhập thường

- không chấm theo biểu thức toán học
- không trim theo kiểu thông thường để bảo toàn quy ước 4 cột
- với logic hiện tại:
  - thay dấu cách bằng `_`
  - pad bên phải bằng `_` cho đủ `4` ký tự
  - áp dụng rule ký tự hợp lệ của `SAQ`

Ví dụ:

- `12` -> `12__`
- `12  ` -> `12__`
- `-1,2` -> `-1,2`

#### Kiểu OMR

- `rawAnswer` dùng dấu `|` để biểu diễn 4 cột
- mỗi segment là một cột
- backend chuyển sang `normalizedAnswer`

Ví dụ:

- `|23|,|7` -> `_M,7`

Ý nghĩa:

- `_`: bỏ trống
- `M`: tô nhiều hơn 1 ký tự trong cùng 1 cột

---

## 5. Rule chấm `MCQ`

## 5.1. Ý tưởng chung

`MCQ` được chấm khác nhau tùy theo `submitSource`:

- `WEB`
- `OMR_IMPORT`

### 5.2. Rule cho `WEB`

Điều kiện:

- đáp án học sinh phải có đúng `1` ký tự
- nếu đáp án chuẩn chứa nhiều lựa chọn đúng, học sinh chỉ cần chọn trùng **một trong các lựa chọn đúng** là được tính đúng

Ví dụ:

- đáp án chuẩn: `AD`
- học sinh chọn: `A`
- kết quả: đúng

- đáp án chuẩn: `AD`
- học sinh chọn: `D`
- kết quả: đúng

- đáp án chuẩn: `AD`
- học sinh chọn: `C`
- kết quả: sai

### 5.3. Rule cho `OMR_IMPORT`

Điều kiện:

- đáp án học sinh phải có đúng `1` ký tự
- đáp án học sinh phải bằng đúng đáp án chuẩn

Ví dụ:

- đáp án chuẩn: `A`
- học sinh chọn: `A`
- kết quả: đúng

- đáp án chuẩn: `A`
- học sinh chọn: `B`
- kết quả: sai

### 5.4. Ký tự `M`

Trong luồng `OMR_IMPORT`, nếu học sinh tô nhiều hơn `1` lựa chọn cho cùng một câu `MCQ`, backend có thể biểu diễn đáp án chuẩn hóa bằng:

- `M`

Rule hiện tại:

- nếu `studentAnswer = M`
- câu đó bị tính `0` điểm

### 5.5. Tóm tắt `MCQ`

- `WEB`: đúng nếu chọn đúng `1` đáp án và đáp án đó nằm trong đáp án chuẩn
- `OMR_IMPORT`: đúng nếu chọn đúng `1` đáp án và bằng chính xác đáp án chuẩn
- có `M` -> sai

---

## 6. Rule chấm `TFQ`

## 6.1. Ký tự sử dụng

### Đáp án chuẩn

- `D`: đúng
- `S`: sai
- `N`: nhận định không có tính đúng/sai rõ ràng

### Đáp án học sinh

- `D`: học sinh chọn đúng
- `S`: học sinh chọn sai
- `B`: học sinh bỏ trống

### 6.2. Cách đếm số ý đúng

Backend duyệt từng vị trí trong chuỗi 4 ký tự:

- nếu đáp án chuẩn là `D`
  - học sinh phải trả lời `D` mới được tính đúng ý đó
- nếu đáp án chuẩn là `S`
  - học sinh phải trả lời `S` mới được tính đúng ý đó
- nếu đáp án chuẩn là `N`
  - học sinh chỉ cần trả lời khác `B` là được tính đúng ý đó

### 6.3. Quy đổi số ý đúng sang phần trăm

Sau khi đếm số ý đúng, backend lấy phần trăm từ `Exam`:

- đúng `1` ý -> `tfCorrect1Pct`
- đúng `2` ý -> `tfCorrect2Pct`
- đúng `3` ý -> `tfCorrect3Pct`
- đúng `4` ý -> `tfCorrect4Pct`
- đúng `0` ý -> `0%`

### 6.4. Công thức tính điểm

```text
Điểm câu TFQ = điểm của câu * phần trăm tương ứng / 100
```

Ví dụ:

- điểm câu: `2.0`
- đúng `3` ý
- `tfCorrect3Pct = 50`
- điểm nhận được: `2.0 * 50 / 100 = 1.0`

### 6.5. Ví dụ

- đáp án chuẩn: `DSDN`
- học sinh: `DSBD`

Phân tích:

- vị trí 1: `D` vs `D` -> đúng
- vị trí 2: `S` vs `S` -> đúng
- vị trí 3: `D` vs `B` -> sai
- vị trí 4: `N` vs `D` -> đúng vì khác `B`

Tổng:

- đúng `3` ý

---

## 7. Rule chấm `SAQ`

## 7.1. Ý tưởng chung

`SAQ` hiện tại được chấm theo:

- **exact match sau khi normalize**

Nếu có nhiều đáp án đúng:

- backend tách theo dấu `;`
- học sinh chỉ cần khớp với **một trong các đáp án đúng** là được tính đúng

### 7.2. Dữ liệu đáp án đúng

`QuestionAnswerKey.normalizedAnswer` có thể chứa nhiều đáp án đúng, ví dụ:

- `12__;12,5;-1,2`

Backend sẽ split theo `;` và lấy danh sách accepted answers.

### 7.3. Điều kiện đúng

Nếu `studentAnswer` nằm trong danh sách accepted answers:

- học sinh được trọn điểm của câu

Nếu không:

- `0` điểm

### 7.4. Ví dụ

Đáp án chuẩn:

- `12__;12,5;-1,2`

Học sinh:

- `12__` -> đúng
- `12,5` -> đúng nếu normalize ra đúng chuỗi chuẩn tương ứng
- `-1,2` -> đúng
- `_M,7` -> sai

### 7.5. Hệ quả của OMR với `SAQ`

Nếu đáp án học sinh có cột bị tô nhiều hơn một ký tự, normalized answer sẽ chứa:

- `M`

Vì không thể khớp exact với đáp án chuẩn hợp lệ, câu đó sẽ bị tính sai.

---

## 8. Auto-submit và chấm điểm

## 8.1. Khi auto-submit xảy ra

Backend auto-submit khi:

- scheduler phát hiện attempt quá hạn
- hoặc học sinh gọi `getAttempt`
- hoặc học sinh gọi `saveAnswer`

và attempt đã vượt deadline

## 8.2. Auto-submit có chấm điểm không

Có.

Auto-submit dùng chung luồng:

- `finalizeAttempt(...)`

nên:

- vẫn tạo `final answer`
- vẫn chấm điểm
- vẫn cập nhật `score`
- vẫn cập nhật `status = SCORED`
- đặt `isAutoSubmitted = true`

---

## 9. Những gì backend hiện chưa làm trong scoring

### 9.1. `SAQ` chưa có fuzzy matching

Backend hiện chưa hỗ trợ:

- so khớp gần đúng
- đồng nghĩa
- bỏ dấu tiếng Việt
- so khớp biểu thức toán học tương đương
- làm tròn số nâng cao

### 9.2. Chưa có partial score cho `MCQ`

`MCQ` hiện là:

- đúng thì trọn điểm
- sai thì `0`

### 9.3. `SAQ` đang chấm theo exact match

Điều này rất rõ ràng và dễ kiểm thử, nhưng cũng chặt hơn một số nghiệp vụ thực tế.

---

## 10. Gợi ý kiểm thử QA

### 10.1. `MCQ`

- `WEB` với 1 đáp án đúng
- `WEB` với nhiều đáp án đúng
- `OMR_IMPORT` với tô nhiều đáp án
- `OMR_IMPORT` với đáp án đúng 1 ký tự

### 10.2. `TFQ`

- đúng `0` ý
- đúng `1` ý
- đúng `2` ý
- đúng `3` ý
- đúng `4` ý
- case có `N`
- case có `B`

### 10.3. `SAQ`

- 1 đáp án đúng
- nhiều đáp án đúng phân cách bằng `;`
- đáp án chuẩn ngắn hơn 4 ký tự
- đáp án OMR có `_`
- đáp án OMR có `M`

### 10.4. Auto-submit

- để quá thời gian rồi submit tự động
- kiểm tra `score`
- kiểm tra `isAutoSubmitted = true`
- kiểm tra `status = SCORED`
