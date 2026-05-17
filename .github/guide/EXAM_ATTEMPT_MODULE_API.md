# Exam Attempt Module API

## 1. Mục đích module

Module `Exam Attempt` dùng để phục vụ luồng học sinh làm bài, bao gồm:

- bắt đầu làm bài
- lấy chi tiết bài đang làm hoặc đã nộp
- lưu đáp án trong quá trình làm bài
- nộp bài
- lấy danh sách các lần làm bài của học sinh

Module này cũng là nơi xử lý các nghiệp vụ quan trọng:

- random câu hỏi theo `question group` khi bắt đầu attempt
- lưu lịch sử thay đổi đáp án
- chấm điểm theo đáp án cuối cùng
- auto-submit khi hết thời gian

---

## 2. Quy ước response chung

Tất cả API thành công của module này đều được bọc bởi `RestResponse`.

### Quy ước xác thực

- frontend cần gửi:
  - `Authorization: Bearer <access_token>`
- `access token` được cấp từ `Management Service`
- backend xác định học sinh hiện tại từ claim:
  - `user.id`
- field `studentUuid` trong response là id của user hiện tại được đọc từ `access token`

### Format thành công chung

```json
{
  "statusCode": 200,
  "message": "Call API success",
  "data": {}
}
```

Ví dụ:

```json
{
  "statusCode": 200,
  "message": "Start exam attempt",
  "data": {}
}
```

### Format lỗi chung

```json
{
  "statusCode": 400,
  "message": "Bad Request",
  "error": "Nội dung lỗi",
  "data": null
}
```

---

## 3. DTO chính của module

### 3.1. Input lưu đáp án

```json
{
  "questionUuid": "uuid",
  "rawAnswer": "string | null"
}
```

### 3.2. Response chi tiết attempt

```json
{
  "attemptUuid": "uuid",
  "examUuid": "uuid",
  "examName": "string",
  "studentUuid": "uuid",
  "attemptNo": 1,
  "startedAt": "2026-05-17T10:00:00Z",
  "submittedAt": null,
  "timeSpentSeconds": null,
  "status": "IN_PROGRESS | SUBMITTED | SCORED | ANSWER_RELEASED | CANCELLED",
  "score": null,
  "isAutoSubmitted": false,
  "questions": []
}
```

### 3.3. Cấu trúc từng câu trong attempt

```json
{
  "questionOrder": 1,
  "questionUuid": "uuid",
  "questionType": "MCQ | TFQ | SAQ",
  "questionContent": "string",
  "questionTopic": "string | null",
  "score": 1.0,
  "fromQuestionGroup": false,
  "groupUuid": "uuid | null",
  "groupName": "string | null",
  "mcOptions": [],
  "tfStatements": [],
  "currentRawAnswer": "string | null",
  "currentNormalizedAnswer": "string | null",
  "answerChangeCount": 0
}
```

### 3.4. Response summary của danh sách attempt

```json
{
  "attemptUuid": "uuid",
  "examUuid": "uuid",
  "examName": "string",
  "attemptNo": 1,
  "startedAt": "2026-05-17T10:00:00Z",
  "submittedAt": null,
  "timeSpentSeconds": null,
  "status": "IN_PROGRESS | SUBMITTED | SCORED | ANSWER_RELEASED | CANCELLED",
  "score": null,
  "isAutoSubmitted": false
}
```

---

## 4. Rule dữ liệu quan trọng

### 4.1. Rule bắt đầu attempt

- chỉ được start attempt khi `Exam.status = PUBLISHED`
- nếu `startTime` có giá trị, không được start trước thời điểm đó
- nếu `endTime` có giá trị, không được start sau thời điểm đó
- phải tôn trọng `numberOfAttempt`
- `studentUuid` được lấy từ claim `user.id` trong `access token`

### 4.2. Rule random câu hỏi theo group

Khi học sinh bắt đầu attempt:

- câu hỏi lẻ từ `ExamQuestion` được đưa vào nguyên trạng
- với mỗi `ExamQuestionGroup`, backend random `pickQuestionCount` câu từ pool
- sau đó backend snapshot toàn bộ cấu trúc câu hỏi vào `ExamAttempt.questionSnapshotJson`

Điều này có nghĩa là:

- mỗi học sinh có thể nhận tập câu hỏi khác nhau nếu đề có group random
- bộ câu hỏi của một attempt sẽ không bị đổi giữa chừng

### 4.3. Rule lưu đáp án

- mỗi lần học sinh đổi đáp án, backend insert thêm một dòng `StudentAnswer`
- không update đè lên bản ghi cũ
- khi submit, backend tạo thêm bản ghi `isFinalAnswer = true`
- khi chấm điểm, hệ thống dùng đáp án cuối cùng

### 4.4. Rule auto-submit

Backend auto-submit theo 2 lớp:

- scheduler nền quét các attempt quá hạn
- khi học sinh gọi `getAttempt` hoặc `saveAnswer`, nếu đã quá hạn thì auto-submit ngay

Deadline hiện tại được tính theo:

- `min(startedAt + durationMinutes, exam.endTime)`

Nếu `exam.endTime` là `null`, hệ thống dùng:

- `startedAt + durationMinutes`

### 4.5. Rule chuẩn hóa đáp án

#### MCQ

- backend normalize về chuỗi option key, ví dụ `A`, `B`, `AD`
- với `WEB`, nếu đáp án chuẩn là `AD` thì học sinh chọn `A` hoặc `D` vẫn được tính đúng
- với `OMR_IMPORT`, nếu học sinh tô nhiều hơn 1 đáp án thì có thể biểu diễn bằng `M` và bị tính sai

#### TFQ

- đáp án chuẩn dùng `D/S/N`
- đáp án học sinh dùng `D/S/B`
- `B` là bỏ trống
- nếu đáp án chuẩn là `N`, học sinh chỉ cần trả lời khác `B` là được tính đúng ý đó

#### SAQ

- backend hỗ trợ chuẩn hóa theo mô hình 4 cột
- có hỗ trợ `rawAnswer` kiểu nhập thường
- có hỗ trợ `rawAnswer` kiểu OMR dùng dấu `|`
- ví dụ:
  - `12` -> `12__`
  - `|23|,|7` -> `_M,7`

---

## 5. API chi tiết

## 5.1. Bắt đầu làm bài

### Đường dẫn

`POST /api/v1/student/exams/{examUuid}/attempts`

### Mô tả luồng

Học sinh bắt đầu làm bài -> lấy `studentUuid` từ claim `user.id` trong `access token` -> kiểm tra đề có tồn tại và đang ở trạng thái `PUBLISHED` không -> kiểm tra thời gian mở/đóng đề -> kiểm tra số lần làm tối đa -> random câu hỏi từ các group nếu có -> snapshot bộ câu hỏi vào attempt -> lưu `ExamAttempt` -> trả dữ liệu chi tiết attempt cho frontend

### Input format

- `examUuid`: `UUID`
- không có body

### Output format

```json
{
  "statusCode": 200,
  "message": "Start exam attempt",
  "data": {
    "attemptUuid": "uuid",
    "examUuid": "uuid",
    "examName": "Đề kiểm tra Toán 15 phút",
    "studentUuid": "uuid",
    "attemptNo": 1,
    "startedAt": "2026-05-17T10:00:00Z",
    "submittedAt": null,
    "timeSpentSeconds": null,
    "status": "IN_PROGRESS",
    "score": null,
    "isAutoSubmitted": false,
    "questions": [
      {
        "questionOrder": 1,
        "questionUuid": "uuid",
        "questionType": "MCQ",
        "questionContent": "string",
        "questionTopic": "string | null",
        "score": 1.0,
        "fromQuestionGroup": false,
        "groupUuid": null,
        "groupName": null,
        "mcOptions": [],
        "tfStatements": [],
        "currentRawAnswer": null,
        "currentNormalizedAnswer": null,
        "answerChangeCount": 0
      }
    ]
  }
}
```

### Exception có thể trả về

#### `400 Bad Request`

- `User id is missing from JWT`
- `Exam not found with id: {examUuid}`
- `Exam is not available for attempt`
- `Exam has not started yet`
- `Exam is already closed`
- `Student has reached the maximum number of attempts for this exam`
- `Failed to serialize attempt question snapshot`

#### `403 Forbidden`

- khi `access token` không hợp lệ hoặc không đủ quyền truy cập

#### `500 Internal Server Error`

- lỗi không mong muốn từ backend

---

## 5.2. Lấy chi tiết attempt

### Đường dẫn

`GET /api/v1/student/attempts/{attemptUuid}`

### Mô tả luồng

Nhận `attemptUuid` -> lấy attempt theo id -> kiểm tra quyền sở hữu -> nếu attempt đã hết thời gian thì auto-submit trước -> đọc snapshot câu hỏi -> lấy options, statements, answer history -> build response chi tiết -> trả kết quả

### Input format

- `attemptUuid`: `UUID`

### Output format

Trả `ResExamAttemptDTO`, ví dụ rút gọn:

```json
{
  "statusCode": 200,
  "message": "Get exam attempt",
  "data": {
    "attemptUuid": "uuid",
    "examUuid": "uuid",
    "examName": "Đề kiểm tra Toán 15 phút",
    "studentUuid": "uuid",
    "attemptNo": 1,
    "startedAt": "2026-05-17T10:00:00Z",
    "submittedAt": null,
    "timeSpentSeconds": null,
    "status": "IN_PROGRESS",
    "score": null,
    "isAutoSubmitted": false,
    "questions": [
      {
        "questionOrder": 3,
        "questionUuid": "uuid",
        "questionType": "SAQ",
        "questionContent": "Điền kết quả vào ô trả lời",
        "questionTopic": "Số học",
        "score": 1.0,
        "fromQuestionGroup": true,
        "groupUuid": "uuid",
        "groupName": "Điền đáp án ngắn",
        "mcOptions": [],
        "tfStatements": [],
        "currentRawAnswer": "|23|,|7",
        "currentNormalizedAnswer": "_M,7",
        "answerChangeCount": 1
      }
    ]
  }
}
```

### Exception có thể trả về

#### `400 Bad Request`

- `Attempt not found with id: {attemptUuid}`
- `You do not have permission to access this attempt`
- `Exam not found with id: {examUuid}`
- `Failed to read attempt question snapshot`

#### `403 Forbidden`

- khi `access token` không hợp lệ hoặc không đủ quyền truy cập

#### `500 Internal Server Error`

- lỗi không mong muốn từ backend

---

## 5.3. Lấy danh sách attempt của học sinh

### Đường dẫn

`GET /api/v1/student/attempts`

### Mô tả luồng

Nhận các tham số filter -> lấy `studentUuid` từ claim `user.id` trong `access token` -> query danh sách attempt của học sinh -> nếu có `examUuid` thì lọc theo đề -> auto-submit mềm các attempt đã quá hạn trước khi build summary -> trả kết quả phân trang

### Input format

- `examUuid`: `UUID`, không bắt buộc
- `page`: `int`, không bắt buộc
- `size`: `int`, không bắt buộc
- `sort`: `String`, không bắt buộc

Ví dụ:

`GET /api/v1/student/attempts?examUuid=018f4a60-12ab-7a11-a9d1-7c5d5b5b0001&page=0&size=20&sort=createdAt,desc`

### Output format

Do backend đang trả `Page<ResExamAttemptSummaryDTO>`, dữ liệu trong `data` là object phân trang của Spring.

Ví dụ item summary:

```json
{
  "attemptUuid": "uuid",
  "examUuid": "uuid",
  "examName": "Đề kiểm tra Toán 15 phút",
  "attemptNo": 1,
  "startedAt": "2026-05-17T10:00:00Z",
  "submittedAt": null,
  "timeSpentSeconds": null,
  "status": "IN_PROGRESS",
  "score": null,
  "isAutoSubmitted": false
}
```

### Exception có thể trả về

#### `400 Bad Request`

- `User id is missing from JWT`
- `Exam not found with id: {examUuid}`

#### `403 Forbidden`

- khi `access token` không hợp lệ hoặc không đủ quyền truy cập

#### `500 Internal Server Error`

- lỗi không mong muốn từ backend

---

## 5.4. Lưu đáp án của học sinh

### Đường dẫn

`POST /api/v1/student/attempts/{attemptUuid}/answers`

### Mô tả luồng

Nhận `attemptUuid` và `rawAnswer` -> kiểm tra quyền sở hữu attempt -> nếu đã hết giờ thì auto-submit trước -> kiểm tra attempt còn `IN_PROGRESS` không -> kiểm tra `questionUuid` có thuộc snapshot của attempt không -> normalize đáp án theo `questionType` -> insert thêm một dòng `StudentAnswer` mới -> trả lại response chi tiết attempt sau khi lưu

### Input format

- `attemptUuid`: `UUID`

```json
{
  "questionUuid": "uuid",
  "rawAnswer": "A"
}
```

Ví dụ `TFQ`:

```json
{
  "questionUuid": "uuid",
  "rawAnswer": "DSBD"
}
```

Ví dụ `SAQ OMR`:

```json
{
  "questionUuid": "uuid",
  "rawAnswer": "|23|,|7"
}
```

### Output format

Trả `ResExamAttemptDTO` giống API lấy chi tiết attempt.

### Exception có thể trả về

#### `400 Bad Request`

- `Attempt not found with id: {attemptUuid}`
- `You do not have permission to access this attempt`
- `Attempt is not in progress`
- `Question id is required`
- `Question does not belong to this attempt: {questionUuid}`
- `Exam not found with id: {examUuid}`
- `Failed to read attempt question snapshot`

#### `403 Forbidden`

- khi `access token` không hợp lệ hoặc không đủ quyền truy cập

#### `500 Internal Server Error`

- lỗi không mong muốn từ backend

---

## 5.5. Nộp bài

### Đường dẫn

`POST /api/v1/student/attempts/{attemptUuid}/submit`

### Mô tả luồng

Nhận `attemptUuid` -> kiểm tra quyền sở hữu -> kiểm tra attempt còn `IN_PROGRESS` không -> lấy toàn bộ snapshot câu hỏi -> lấy answer history của từng câu -> tạo `final answer` cho từng câu -> lấy `QuestionAnswerKey` -> chấm điểm theo từng loại câu hỏi -> cập nhật `submittedAt`, `timeSpentSeconds`, `status`, `score`, `isAutoSubmitted` -> trả kết quả

### Input format

- `attemptUuid`: `UUID`
- không có body

### Output format

Trả `ResExamAttemptDTO`, ví dụ rút gọn:

```json
{
  "statusCode": 200,
  "message": "Submit exam attempt",
  "data": {
    "attemptUuid": "uuid",
    "examUuid": "uuid",
    "examName": "Đề kiểm tra Toán 15 phút",
    "studentUuid": "uuid",
    "attemptNo": 1,
    "startedAt": "2026-05-17T10:00:00Z",
    "submittedAt": "2026-05-17T10:14:58Z",
    "timeSpentSeconds": 898,
    "status": "SCORED",
    "score": 8.5,
    "isAutoSubmitted": false,
    "questions": []
  }
}
```

### Exception có thể trả về

#### `400 Bad Request`

- `Attempt not found with id: {attemptUuid}`
- `You do not have permission to access this attempt`
- `Attempt is not in progress`
- `Exam not found with id: {examUuid}`
- `Failed to read attempt question snapshot`

#### `403 Forbidden`

- khi `access token` không hợp lệ hoặc không đủ quyền truy cập

#### `500 Internal Server Error`

- lỗi không mong muốn từ backend

---

## 6. Luồng liên quan module khác

### 6.1. Liên quan `Exam Module`

`Exam Attempt Module` dùng dữ liệu từ `Exam` để:

- kiểm tra đề có đang mở không
- kiểm tra `numberOfAttempt`
- lấy câu hỏi lẻ
- lấy các group câu hỏi của đề
- random câu hỏi theo `pickQuestionCount`

### 6.2. Liên quan `Question Module`

`Exam Attempt Module` dùng dữ liệu từ `Question` để:

- hiển thị `questionContent`
- hiển thị `questionTopic`
- lấy `mcOptions`
- lấy `tfStatements`
- lấy `QuestionAnswerKey`
- chấm điểm

### 6.3. Liên quan `Question Group`

Sau khi đề đã được tạo, `Attempt` không đọc trực tiếp group gốc nữa.

Nó chỉ làm việc với snapshot group đã được lưu trong:

- `ExamQuestionGroup`
- `ExamQuestionGroupItem`

---

## 7. Ghi chú cho frontend

### 7.1. Khi bắt đầu làm bài

Frontend nên dùng response từ:

- `POST /api/v1/student/exams/{examUuid}/attempts`

để render ngay toàn bộ đề mà học sinh được nhận.

### 7.2. Khi lưu đáp án

Frontend nên gọi:

- `POST /api/v1/student/attempts/{attemptUuid}/answers`

theo kiểu autosave hoặc debounce.

Nếu muốn chống mất dữ liệu khi mất mạng:

- nên lưu tạm đáp án ở local
- retry khi có mạng lại

### 7.3. Convention `TFQ`

- đáp án học sinh nên gửi đủ 4 ký tự
- dùng `B` cho ý bỏ trống

Ví dụ:

- `DSBD`

### 7.4. Convention `SAQ`

- nhập thường:
  - `12`
  - `-1,2`
- kiểu OMR:
  - `|23|,|7`

Frontend có thể đọc lại:

- `currentRawAnswer`
- `currentNormalizedAnswer`

để debug dữ liệu OMR hoặc khôi phục trạng thái màn hình.

---

## 8. Gợi ý kiểm thử frontend

### 8.1. Bắt đầu làm bài

- start attempt hợp lệ
- start trước `startTime`
- start sau `endTime`
- start vượt `numberOfAttempt`

### 8.2. Lưu đáp án

- lưu `MCQ`
- lưu `TFQ` với `B`
- lưu `SAQ` nhập thường
- lưu `SAQ OMR`
- lưu cho câu không thuộc attempt

### 8.3. Nộp bài

- submit thủ công trước khi hết giờ
- submit khi chưa trả lời câu nào
- submit lặp lại khi attempt không còn `IN_PROGRESS`

### 8.4. Auto-submit

- để quá thời gian rồi gọi `getAttempt`
- để quá thời gian rồi gọi `saveAnswer`
- kiểm tra `isAutoSubmitted = true`

### 8.5. Danh sách attempt

- lấy toàn bộ attempt của học sinh
- filter theo `examUuid`
- kiểm tra `status`, `score`, `submittedAt`, `isAutoSubmitted`
