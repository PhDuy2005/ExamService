# Exam Module API

## 1. Mục đích module

Module `Exam` dùng để quản lý đề thi / bài tập trong `ExamService`, bao gồm:

- tạo đề mới
- xem chi tiết đề
- tìm kiếm danh sách đề
- cập nhật cấu trúc đề
- cập nhật trạng thái đề

Module này hỗ trợ đồng thời 2 cách đưa câu hỏi vào đề:

- câu hỏi lẻ qua `examQuestions`
- nhóm câu hỏi qua `examQuestionGroups`

Riêng với `examQuestionGroups`, backend hiện hỗ trợ 2 mode:

- dùng `question group` có sẵn bằng `questionGroupUuid`
- tạo mới `question group` ngay trong payload bằng `newQuestionGroup`

Khi gắn vào đề, backend sẽ snapshot group sang:

- `ExamQuestionGroup`
- `ExamQuestionGroupItem`

---

## 2. Quy ước response chung

Tất cả API thành công của module này đều được bọc bởi `RestResponse`.

### Quy ước xác thực

- frontend cần gửi:
  - `Authorization: Bearer <access_token>`
- `access token` được cấp từ `Management Service`
- khi backend cần xác định người tạo hoặc người cập nhật đề, backend đọc từ claim:
  - `user.id`

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
  "message": "Create exam",
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

Hoặc với lỗi validation:

```json
{
  "statusCode": 400,
  "message": "Bad Request",
  "error": "Validation failed",
  "data": {
    "fieldName": "Chi tiết lỗi"
  }
}
```

---

## 3. DTO chính của module

### 3.1. Cấu trúc response chi tiết đề

```json
{
  "examUuid": "uuid",
  "examName": "string",
  "gradeId": 12,
  "examType": "QUIZ | HOMEWORK | MOCK_TEST | OFFICIAL_TEST",
  "startTime": "2026-05-17T10:00:00Z | null",
  "endTime": "2026-05-17T11:00:00Z | null",
  "durationMinutes": 45,
  "totalScore": 10.0,
  "numberOfAttempt": 1,
  "status": "DRAFT | PUBLISHED | CLOSED | ARCHIVED",
  "createdByUserUuid": "uuid",
  "tfCorrect1Pct": 10,
  "tfCorrect2Pct": 25,
  "tfCorrect3Pct": 50,
  "tfCorrect4Pct": 100,
  "questionSummary": {
    "mcqCount": 10,
    "tfqCount": 4,
    "saqCount": 2
  },
  "questionSections": [],
  "createdAt": "2026-05-17T10:00:00Z",
  "updatedAt": "2026-05-17T10:00:00Z",
  "createdBy": "string | null",
  "updatedBy": "string | null"
}
```

### 3.2. Cấu trúc `questionSections`

`questionSections` luôn được tổ chức theo từng loại câu hỏi:

- `MCQ`
- `TFQ`
- `SAQ`

Mỗi section có format:

```json
{
  "questionType": "MCQ | TFQ | SAQ",
  "totalQuestionCount": 5,
  "standaloneQuestions": [],
  "groups": []
}
```

### 3.3. Cấu trúc câu hỏi lẻ trong đề

```json
{
  "examQuestionUuid": "uuid",
  "questionUuid": "uuid",
  "questionOrder": 1,
  "score": 1.0,
  "sectionType": "MCQ | TFQ | SAQ",
  "sourceType": "MANUAL | QUESTION_BANK | IMPORTED",
  "questionDetail": {
    "questionUuid": "uuid",
    "questionContent": "string",
    "questionTopic": "string | null",
    "questionType": "MCQ | TFQ | SAQ"
  }
}
```

### 3.4. Cấu trúc nhóm câu hỏi trong đề

```json
{
  "eqgUuid": "uuid",
  "questionGroupUuid": "uuid",
  "groupName": "string",
  "questionType": "MCQ | TFQ | SAQ",
  "questionTopic": "string | null",
  "poolQuestionCount": 10,
  "pickQuestionCount": 4,
  "scorePerQuestion": 0.5,
  "displayOrder": 1,
  "items": [
    {
      "eqgiUuid": "uuid",
      "questionUuid": "uuid",
      "questionDetail": {
        "questionUuid": "uuid",
        "questionContent": "string",
        "questionTopic": "string | null",
        "questionType": "MCQ | TFQ | SAQ"
      }
    }
  ]
}
```

---

## 4. Rule dữ liệu quan trọng

### 4.1. Rule chung của đề

- `gradeId` có kiểu `Long`
- `endTime` phải sau `startTime` nếu cả hai cùng có giá trị
- `durationMinutes` phải lớn hơn hoặc bằng `0`
- `totalScore` phải lớn hơn `0`
- `numberOfAttempt` phải lớn hơn hoặc bằng `0`
- `status` là bắt buộc

### 4.2. Rule phần trăm chấm `TFQ`

- `tfCorrect1Pct <= tfCorrect2Pct <= tfCorrect3Pct <= tfCorrect4Pct`

Nếu không truyền, backend sẽ gán mặc định:

- `10`
- `25`
- `50`
- `100`

### 4.3. Rule câu hỏi lẻ `examQuestions`

- `questionOrder` phải unique trong cùng một đề
- `questionUuid` phải tồn tại trong ngân hàng câu hỏi
- `sectionType` phải khớp với `questionType` của câu hỏi gốc

### 4.4. Rule nhóm câu hỏi `examQuestionGroups`

- `displayOrder` phải unique trong cùng một đề
- mỗi phần tử `examQuestionGroups` phải chọn đúng 1 trong 2 cách:
  - truyền `questionGroupUuid`
  - hoặc truyền `newQuestionGroup`
- `pickQuestionCount` phải nhỏ hơn hoặc bằng `questionCount` của group thực tế
- `scorePerQuestion` là thuộc tính của đề, không phải thuộc tính của group gốc
- `displayOrder` là thuộc tính của đề, không phải thuộc tính của group gốc

### 4.5. Ý nghĩa `questionCount` và `pickQuestionCount`

- `questionCount`: số câu hiện có trong pool của `question group`
- `pickQuestionCount`: số câu sẽ được random ra khi học sinh bắt đầu `attempt`

Ví dụ:

- group có `10` câu trong pool
- chỉ lấy `4` câu khi học sinh bắt đầu làm bài

thì:

- `questionCount = 10`
- `pickQuestionCount = 4`

---

## 5. API chi tiết

## 5.1. Tạo đề

### Đường dẫn

`POST /api/v1/exams`

### Mô tả luồng

Tạo đề mới -> validate dữ liệu chung của đề -> validate phần trăm chấm `TFQ` -> validate danh sách câu hỏi lẻ -> resolve từng phần tử `examQuestionGroups` theo mode `questionGroupUuid` hoặc `newQuestionGroup` -> nếu là `newQuestionGroup` thì tạo `question group` riêng trước -> kiểm tra toàn bộ `questionUuid` có tồn tại và đúng loại hay không -> lưu `Exam` xuống database -> lưu `ExamQuestion` -> snapshot group vào `ExamQuestionGroup` và `ExamQuestionGroupItem` -> build `questionSummary` và `questionSections` -> trả kết quả

### Input format

```json
{
  "examName": "Đề kiểm tra Toán 15 phút",
  "gradeId": 12,
  "examType": "QUIZ",
  "startTime": "2026-05-18T01:00:00Z",
  "endTime": "2026-05-18T01:15:00Z",
  "durationMinutes": 15,
  "totalScore": 10,
  "numberOfAttempt": 1,
  "status": "DRAFT",
  "tfCorrect1Pct": 10,
  "tfCorrect2Pct": 25,
  "tfCorrect3Pct": 50,
  "tfCorrect4Pct": 100,
  "examQuestions": [
    {
      "questionUuid": "018f4a70-1111-7c11-8aa1-7c5d5b5b0101",
      "questionOrder": 1,
      "score": 1.0,
      "sectionType": "MCQ",
      "sourceType": "QUESTION_BANK"
    }
  ],
  "examQuestionGroups": [
    {
      "questionGroupUuid": "018f4a80-2222-7c11-8aa1-7c5d5b5b0301",
      "pickQuestionCount": 2,
      "scorePerQuestion": 0.5,
      "displayOrder": 1
    },
    {
      "newQuestionGroup": {
        "groupName": "Nguyên hàm",
        "questionType": "MCQ",
        "questionTopic": "Nguyên hàm",
        "questionCount": 5,
        "items": [
          {
            "questionUuid": "018f4a70-2222-7c11-8aa1-7c5d5b5b0201"
          },
          {
            "questionUuid": "018f4a70-2222-7c11-8aa1-7c5d5b5b0202"
          },
          {
            "questionUuid": "018f4a70-2222-7c11-8aa1-7c5d5b5b0203"
          },
          {
            "questionUuid": "018f4a70-2222-7c11-8aa1-7c5d5b5b0204"
          },
          {
            "questionUuid": "018f4a70-2222-7c11-8aa1-7c5d5b5b0205"
          }
        ]
      },
      "pickQuestionCount": 2,
      "scorePerQuestion": 0.5,
      "displayOrder": 2
    }
  ]
}
```

### Output format

```json
{
  "statusCode": 200,
  "message": "Create exam",
  "data": {
    "examUuid": "uuid",
    "examName": "Đề kiểm tra Toán 15 phút",
    "gradeId": 12,
    "examType": "QUIZ",
    "startTime": "2026-05-18T01:00:00Z",
    "endTime": "2026-05-18T01:15:00Z",
    "durationMinutes": 15,
    "totalScore": 10,
    "numberOfAttempt": 1,
    "status": "DRAFT",
    "createdByUserUuid": "uuid",
    "tfCorrect1Pct": 10,
    "tfCorrect2Pct": 25,
    "tfCorrect3Pct": 50,
    "tfCorrect4Pct": 100,
    "questionSummary": {
      "mcqCount": 5,
      "tfqCount": 0,
      "saqCount": 0
    },
    "questionSections": [
      {
        "questionType": "MCQ",
        "totalQuestionCount": 5,
        "standaloneQuestions": [],
        "groups": []
      }
    ],
    "createdAt": "2026-05-17T10:00:00Z",
    "updatedAt": "2026-05-17T10:00:00Z",
    "createdBy": "string | null",
    "updatedBy": "string | null"
  }
}
```

### Exception có thể trả về

#### `400 Bad Request`

- `Exam name must not be blank`
- `Grade id is required`
- `Exam type is required`
- `Duration minutes is required`
- `Total score is required`
- `Number of attempt is required`
- `Status is required`
- `End time must be after start time`
- `TF scoring percentages must be non-decreasing from 1 to 4 correct statements`
- `Question order must be unique within the exam`
- `Display order must be unique within exam question groups`
- `Each exam question group must provide either questionGroupUuid or newQuestionGroup`
- `Question not found with id: {questionUuid}`
- `Exam question section type must match question type for question id: {questionUuid}`
- `Question group not found with id: {questionGroupUuid}`
- `Question group must contain at least one item`
- `Question ids in a question group must be unique`
- `Question count must match the number of group items`
- `Pick question count must be less than or equal to pool size for group: {groupName}`
- `Question group type must match item question type for question id: {questionUuid}`
- `Question group topic must match item question topic for question id: {questionUuid}`
- `Group question type must match item question type for question id: {questionUuid}`
- `Group question topic must match item question topic for question id: {questionUuid}`

#### `403 Forbidden`

- khi `access token` không hợp lệ hoặc không đủ quyền truy cập

#### `500 Internal Server Error`

- lỗi không mong muốn từ backend

---

## 5.2. Lấy chi tiết đề

### Đường dẫn

`GET /api/v1/exams/{examUuid}`

### Mô tả luồng

Nhận `examUuid` -> tìm đề theo id -> lấy danh sách câu hỏi lẻ -> lấy danh sách group snapshot của đề -> lấy toàn bộ `Question` liên quan -> build `questionSummary` -> build `questionSections` theo `MCQ/TFQ/SAQ` -> trả kết quả

### Input format

- `examUuid`: `UUID`

### Output format

Backend trả `ResExamDTO`, trong đó mỗi group có cả:

- `eqgUuid`: id snapshot group trong đề
- `questionGroupUuid`: id group gốc nếu group này được tạo từ module `Question Group`

Ví dụ rút gọn:

```json
{
  "statusCode": 200,
  "message": "Get exam by id",
  "data": {
    "examUuid": "uuid",
    "examName": "string",
    "gradeId": 12,
    "questionSummary": {
      "mcqCount": 10,
      "tfqCount": 4,
      "saqCount": 2
    },
    "questionSections": [
      {
        "questionType": "MCQ",
        "totalQuestionCount": 10,
        "standaloneQuestions": [],
        "groups": [
          {
            "eqgUuid": "uuid",
            "questionGroupUuid": "uuid",
            "groupName": "Tích phân",
            "poolQuestionCount": 10,
            "pickQuestionCount": 4,
            "scorePerQuestion": 0.5,
            "displayOrder": 1,
            "items": []
          }
        ]
      }
    ]
  }
}
```

### Exception có thể trả về

#### `400 Bad Request`

- `Exam not found with id: {examUuid}`

#### `403 Forbidden`

- khi `access token` không hợp lệ hoặc không đủ quyền truy cập

#### `500 Internal Server Error`

- lỗi không mong muốn từ backend

---

## 5.3. Lấy danh sách đề

### Đường dẫn

`GET /api/v1/exams`

### Mô tả luồng

Nhận các tham số filter -> dựng điều kiện truy vấn động theo `gradeId`, `name`, `type`, `status` -> chạy query phân trang -> build chi tiết từng đề trong trang hiện tại -> trả kết quả dạng page

### Input format

- `gradeId`: `Long`, không bắt buộc
- `name`: `String`, không bắt buộc
- `type`: `String`, không bắt buộc
- `status`: `String`, không bắt buộc
- `page`: `int`, không bắt buộc
- `size`: `int`, không bắt buộc
- `sort`: `String`, không bắt buộc

Ví dụ:

`GET /api/v1/exams?gradeId=12&type=QUIZ&status=PUBLISHED&page=0&size=20&sort=createdAt,desc`

### Output format

Do backend đang trả `Page<ResExamDTO>`, dữ liệu trong `data` là object phân trang của Spring.

### Exception có thể trả về

#### `400 Bad Request`

- `Invalid exam type: {type}`
- `Invalid exam status: {status}`

#### `403 Forbidden`

- khi `access token` không hợp lệ hoặc không đủ quyền truy cập

#### `500 Internal Server Error`

- lỗi không mong muốn từ backend

---

## 5.4. Cập nhật đề

### Đường dẫn

`PUT /api/v1/exams/{examUuid}`

### Mô tả luồng

Nhận `examUuid` và payload mới -> kiểm tra đề có tồn tại không -> validate toàn bộ dữ liệu giống API tạo đề -> cập nhật bản ghi `Exam` -> xóa toàn bộ snapshot câu hỏi cũ của đề -> tạo lại snapshot mới từ payload -> build response -> trả kết quả

### Input format

- `examUuid`: `UUID`
- `body`: cùng cấu trúc với `POST /api/v1/exams`

### Output format

Trả `ResExamDTO` giống API tạo đề.

### Exception có thể trả về

#### `400 Bad Request`

- `Exam not found with id: {examUuid}`
- toàn bộ lỗi validation giống API tạo đề

#### `403 Forbidden`

- khi `access token` không hợp lệ hoặc không đủ quyền truy cập

#### `500 Internal Server Error`

- lỗi không mong muốn từ backend

---

## 5.5. Cập nhật trạng thái đề

### Đường dẫn

`PATCH /api/v1/exams/{examUuid}/status`

### Mô tả luồng

Nhận `examUuid` và `status` mới -> kiểm tra đề có tồn tại không -> cập nhật trạng thái của đề -> lưu xuống database -> build lại response chi tiết -> trả kết quả

### Input format

- `examUuid`: `UUID`

```json
{
  "status": "PUBLISHED"
}
```

### Output format

Trả `ResExamDTO` giống API lấy chi tiết đề.

### Exception có thể trả về

#### `400 Bad Request`

- `Exam not found with id: {examUuid}`
- `Status is required`

#### `403 Forbidden`

- khi `access token` không hợp lệ hoặc không đủ quyền truy cập

#### `500 Internal Server Error`

- lỗi không mong muốn từ backend

---

## 6. Luồng liên quan module khác

### 6.1. Liên quan `Question Module`

Khi tạo hoặc cập nhật đề, backend truy vấn sang dữ liệu câu hỏi để:

- kiểm tra `questionUuid` có tồn tại không
- kiểm tra `sectionType` có khớp `questionType` không
- kiểm tra topic của câu hỏi có khớp topic group không
- lấy `questionContent`, `questionTopic`, `questionType` để build response chi tiết

### 6.2. Liên quan `Question Group Module`

`Exam Module` hiện không còn coi group là dữ liệu chỉ sống bên trong đề.

Thay vào đó:

- có thể tham chiếu group có sẵn bằng `questionGroupUuid`
- có thể tạo mới group bằng `newQuestionGroup`
- khi gắn vào đề, backend snapshot sang `ExamQuestionGroup` và `ExamQuestionGroupItem`

### 6.3. Liên quan `Exam Attempt Module`

`Exam Module` không random câu hỏi ngay khi tạo đề.

Thay vào đó:

- `ExamQuestion` là câu hỏi lẻ cố định
- `ExamQuestionGroup` là pool câu hỏi của đề
- `pickQuestionCount` sẽ được dùng ở `Exam Attempt Module`
- khi học sinh bắt đầu `attempt`, hệ thống mới random câu trong từng group

---

## 7. Ghi chú cho frontend

### 7.1. Về `gradeId`

- `gradeId` là `Long`
- không dùng `UUID`

### 7.2. Về `questionSummary`

`questionSummary` là tổng số câu thực tế theo từng loại mà frontend nên hiển thị ở mức tổng quan.

Riêng với câu hỏi trong group:

- backend tính theo `pickQuestionCount`
- không tính theo toàn bộ pool `questionCount`

### 7.3. Về `questionSections`

Frontend nên render theo từng block:

- `MCQ`
- `TFQ`
- `SAQ`

Trong mỗi block:

- `standaloneQuestions` là câu hỏi cố định
- `groups` là nhóm câu hỏi

### 7.4. Về group câu hỏi

Một đề có thể:

- chỉ có câu hỏi lẻ
- chỉ có group
- hoặc có cả hai

Một loại như `MCQ` có thể có nhiều group khác nhau, ví dụ:

- `Tích phân`
- `Nguyên hàm`

---

## 8. Gợi ý kiểm thử frontend

### 8.1. Tạo đề cơ bản

- tạo đề chỉ có câu hỏi lẻ
- tạo đề chỉ dùng group có sẵn
- tạo đề chỉ dùng `newQuestionGroup`
- tạo đề có cả câu hỏi lẻ và group

### 8.2. Kiểm thử validation

- `endTime` nhỏ hơn `startTime`
- `questionOrder` bị trùng
- `displayOrder` bị trùng
- cùng lúc truyền cả `questionGroupUuid` và `newQuestionGroup`
- không truyền cả `questionGroupUuid` lẫn `newQuestionGroup`
- `pickQuestionCount > questionCount`
- question trong group sai `questionType`
- question trong group sai `questionTopic`

### 8.3. Kiểm thử filter

- filter theo `gradeId`
- filter theo `type`
- filter theo `status`
- filter theo `name`

### 8.4. Kiểm thử response structure

- kiểm tra `questionSummary`
- kiểm tra `questionSections`
- kiểm tra `standaloneQuestions`
- kiểm tra `groups`
- kiểm tra `questionGroupUuid`
- kiểm tra `poolQuestionCount` và `pickQuestionCount`
