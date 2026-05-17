# Question Module API

## 1. Mục đích module

Module `Question` dùng để quản lý ngân hàng câu hỏi trong `ExamService`, bao gồm:

- tạo câu hỏi mới
- xem chi tiết câu hỏi
- tìm kiếm danh sách câu hỏi
- cập nhật nội dung câu hỏi
- bật hoặc tắt trạng thái sử dụng của câu hỏi

Các loại câu hỏi hiện đang hỗ trợ:

- `MCQ`
- `TFQ`
- `SAQ`

---

## 2. Quy ước response chung

Tất cả API thành công của module này đều được bọc bởi `RestResponse`.

### Quy ước xác thực

- frontend cần gửi:
  - `Authorization: Bearer <access_token>`
- `access token` được cấp từ `Management Service`
- khi backend cần xác định người tạo hoặc người cập nhật dữ liệu, backend đọc từ claim:
  - `user.id`

### Format thành công chung

```json
{
  "statusCode": 200,
  "message": "Call API success",
  "data": {}
}
```

Với các API trong controller hiện tại, `message` thực tế sẽ lấy theo `@ApiMessage`.

Ví dụ:

```json
{
  "statusCode": 200,
  "message": "Get question by id",
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

### 3.1. Cấu trúc response chi tiết câu hỏi

```json
{
  "questionUuid": "uuid",
  "gradeId": 10,
  "questionContent": "string",
  "questionTopic": "string | null",
  "questionType": "MCQ | TFQ | SAQ",
  "createdByUserUuid": "uuid",
  "isActive": true,
  "correctAnswerRaw": "string",
  "normalizedAnswer": "string",
  "mcOptions": [],
  "tfStatements": [],
  "createdAt": "2026-05-17T10:00:00Z",
  "updatedAt": "2026-05-17T10:00:00Z",
  "createdBy": "string | null",
  "updatedBy": "string | null"
}
```

### 3.2. Rule dữ liệu theo loại câu hỏi

#### MCQ

- phải có đúng 4 lựa chọn
- các `optionKey` phải là `A`, `B`, `C`, `D`
- không được có `tfStatements`
- `normalizedAnswer` được chuẩn hóa từ tập đáp án đúng, ví dụ:
  - `A`
  - `AD`

#### TFQ

- phải có đúng 4 nhận định
- `statementOrder` phải là `1`, `2`, `3`, `4`
- không được có `mcOptions`
- `correctAnswerRaw` và `normalizedAnswer` dùng 4 ký tự `D`, `S`, `N`

#### SAQ

- không được có `mcOptions`
- không được có `tfStatements`
- đáp án đúng hỗ trợ nhiều phương án, phân cách bằng `;`
- mỗi đáp án `SAQ`:
  - có độ dài từ `1` đến `4` ký tự
  - chỉ dùng `0-9`, `-`, `,`
  - `-` chỉ ở vị trí đầu tiên
  - `,` chỉ ở vị trí thứ `2` hoặc `3`
- backend chuẩn hóa mỗi đáp án đúng về mô hình `4 cột` bằng cách pad `_` bên phải

---

## 4. API chi tiết

## 4.1. Tạo câu hỏi

### Đường dẫn

`POST /api/v1/questions`

### Mô tả luồng

Tạo câu hỏi mới -> validate dữ liệu đầu vào theo loại câu hỏi -> chuẩn hóa đáp án đúng -> lưu bản ghi `Question` -> lưu dữ liệu con tương ứng (`QuestionMcOption` hoặc `QuestionTrueFalseStatement`) -> lưu `QuestionAnswerKey` -> trả kết quả chi tiết câu hỏi vừa tạo

### Input format

#### Trường chung

```json
{
  "gradeId": 10,
  "questionContent": "string",
  "questionTopic": "string | null",
  "questionType": "MCQ | TFQ | SAQ",
  "isActive": true,
  "mcOptions": [],
  "tfStatements": [],
  "answerKey": {
    "correctAnswerRaw": "string"
  }
}
```

#### Ví dụ MCQ

```json
{
  "gradeId": 12,
  "questionContent": "Tích phân của x dx là gì?",
  "questionTopic": "Tích phân",
  "questionType": "MCQ",
  "isActive": true,
  "mcOptions": [
    {
      "optionKey": "A",
      "optionContent": "x^2 / 2 + C"
    },
    {
      "optionKey": "B",
      "optionContent": "2x + C"
    },
    {
      "optionKey": "C",
      "optionContent": "ln(x) + C"
    },
    {
      "optionKey": "D",
      "optionContent": "x^3 / 3 + C"
    }
  ],
  "answerKey": {
    "correctAnswerRaw": "A"
  }
}
```

#### Ví dụ TFQ

```json
{
  "gradeId": 12,
  "questionContent": "Xác định đúng/sai cho các nhận định sau.",
  "questionTopic": "Nguyên hàm",
  "questionType": "TFQ",
  "isActive": true,
  "tfStatements": [
    {
      "statementOrder": 1,
      "statementContent": "Đạo hàm của x^2 là 2x"
    },
    {
      "statementOrder": 2,
      "statementContent": "Nguyên hàm của 1/x là x"
    },
    {
      "statementOrder": 3,
      "statementContent": "Nguyên hàm của 0 là hằng số"
    },
    {
      "statementOrder": 4,
      "statementContent": "Đạo hàm của sin(x) là cos(x)"
    }
  ],
  "answerKey": {
    "correctAnswerRaw": "DSDN"
  }
}
```

#### Ví dụ SAQ

```json
{
  "gradeId": 12,
  "questionContent": "Điền kết quả vào ô trả lời.",
  "questionTopic": "Số học",
  "questionType": "SAQ",
  "isActive": true,
  "answerKey": {
    "correctAnswerRaw": "12;12,5;-1,2"
  }
}
```

### Output format

```json
{
  "statusCode": 200,
  "message": "Create question",
  "data": {
    "questionUuid": "uuid",
    "gradeId": 10,
    "questionContent": "string",
    "questionTopic": "string | null",
    "questionType": "MCQ | TFQ | SAQ",
    "createdByUserUuid": "uuid",
    "isActive": true,
    "correctAnswerRaw": "string",
    "normalizedAnswer": "string",
    "mcOptions": [],
    "tfStatements": [],
    "createdAt": "2026-05-17T10:00:00Z",
    "updatedAt": "2026-05-17T10:00:00Z",
    "createdBy": "string | null",
    "updatedBy": "string | null"
  }
}
```

### Exception có thể trả về

#### `400 Bad Request`

- `Grade id is required`
- `Question content must not be blank`
- `Question type is required`
- `Answer key is required`
- `Correct answer raw must not be blank`
- `MCQ question must contain exactly 4 options A, B, C, D`
- `MCQ question must contain option keys A, B, C, D exactly once`
- `MCQ question must not contain true/false statements`
- `TFQ question must contain exactly 4 statements`
- `TFQ statement order must be 1, 2, 3, 4 exactly once`
- `TFQ question must not contain MCQ options`
- `SAQ question must not contain MCQ options`
- `SAQ question must not contain true/false statements`
- `MCQ answer key must only contain A, B, C, D`
- `TFQ answer key must contain exactly 4 characters using D, S, or N`
- `SAQ answer key must contain at least one valid answer`
- `SAQ answer must contain from 1 to 4 characters`
- `SAQ answer may only contain '-' at the first position`
- `SAQ answer may only contain ',' at the second or third position`
- `SAQ answer may only contain digits, '-', and ','`

#### `403 Forbidden`

- khi `access token` không hợp lệ hoặc không đủ quyền truy cập

#### `500 Internal Server Error`

- lỗi không mong muốn từ backend

---

## 4.2. Lấy chi tiết câu hỏi

### Đường dẫn

`GET /api/v1/questions/{questionUuid}`

### Mô tả luồng

Nhận `questionUuid` -> tìm câu hỏi theo id -> nếu tồn tại thì truy vấn dữ liệu chi tiết và dữ liệu con -> ghép response -> trả kết quả

### Input format

#### Path variable

- `questionUuid`: `UUID`

### Output format

```json
{
  "statusCode": 200,
  "message": "Get question by id",
  "data": {
    "questionUuid": "uuid",
    "gradeId": 10,
    "questionContent": "string",
    "questionTopic": "string | null",
    "questionType": "MCQ | TFQ | SAQ",
    "createdByUserUuid": "uuid",
    "isActive": true,
    "correctAnswerRaw": "string",
    "normalizedAnswer": "string",
    "mcOptions": [],
    "tfStatements": [],
    "createdAt": "2026-05-17T10:00:00Z",
    "updatedAt": "2026-05-17T10:00:00Z",
    "createdBy": "string | null",
    "updatedBy": "string | null"
  }
}
```

### Exception có thể trả về

#### `400 Bad Request`

- `Question not found with id: {questionUuid}`

#### `403 Forbidden`

- khi `access token` không hợp lệ hoặc không đủ quyền truy cập

#### `500 Internal Server Error`

- lỗi không mong muốn từ backend

---

## 4.3. Lấy danh sách câu hỏi

### Đường dẫn

`GET /api/v1/questions`

### Mô tả luồng

Nhận các tham số filter -> dựng điều kiện truy vấn động theo `gradeId`, `topic`, `content`, `type`, `isActive` -> chạy query phân trang -> build chi tiết từng câu hỏi trong trang hiện tại -> trả kết quả dạng page

### Input format

#### Query params

- `gradeId`: `Long`, không bắt buộc
- `topic`: `String`, không bắt buộc
- `content`: `String`, không bắt buộc
- `type`: `String`, không bắt buộc
- `isActive`: `Boolean`, không bắt buộc
- `page`: `int`, không bắt buộc
- `size`: `int`, không bắt buộc
- `sort`: `String`, không bắt buộc

#### Ví dụ

`GET /api/v1/questions?gradeId=12&type=MCQ&isActive=true&page=0&size=20&sort=createdAt,desc`

### Output format

Do backend đang trả `Page<ResQuestionDTO>`, dữ liệu trong `data` sẽ là object phân trang của Spring, ví dụ:

```json
{
  "statusCode": 200,
  "message": "Get questions",
  "data": {
    "content": [
      {
        "questionUuid": "uuid",
        "gradeId": 10,
        "questionContent": "string",
        "questionTopic": "string | null",
        "questionType": "MCQ | TFQ | SAQ",
        "createdByUserUuid": "uuid",
        "isActive": true,
        "correctAnswerRaw": "string",
        "normalizedAnswer": "string",
        "mcOptions": [],
        "tfStatements": [],
        "createdAt": "2026-05-17T10:00:00Z",
        "updatedAt": "2026-05-17T10:00:00Z",
        "createdBy": "string | null",
        "updatedBy": "string | null"
      }
    ],
    "pageable": {},
    "last": true,
    "totalPages": 1,
    "totalElements": 1,
    "size": 20,
    "number": 0,
    "sort": {},
    "first": true,
    "numberOfElements": 1,
    "empty": false
  }
}
```

### Exception có thể trả về

#### `400 Bad Request`

- `Invalid question type: {type}`

#### `403 Forbidden`

- khi `access token` không hợp lệ hoặc không đủ quyền truy cập

#### `500 Internal Server Error`

- lỗi không mong muốn từ backend

---

## 4.4. Cập nhật câu hỏi

### Đường dẫn

`PUT /api/v1/questions/{questionUuid}`

### Mô tả luồng

Nhận `questionUuid` và payload mới -> kiểm tra câu hỏi có tồn tại không -> validate payload theo loại câu hỏi -> cập nhật bản ghi `Question` -> xóa dữ liệu con cũ -> tạo lại dữ liệu con mới -> cập nhật `QuestionAnswerKey` -> trả kết quả chi tiết câu hỏi sau cập nhật

### Input format

#### Path variable

- `questionUuid`: `UUID`

#### Body

Cấu trúc body giống `POST /api/v1/questions`

### Output format

```json
{
  "statusCode": 200,
  "message": "Update question",
  "data": {
    "questionUuid": "uuid",
    "gradeId": 10,
    "questionContent": "string",
    "questionTopic": "string | null",
    "questionType": "MCQ | TFQ | SAQ",
    "createdByUserUuid": "uuid",
    "isActive": true,
    "correctAnswerRaw": "string",
    "normalizedAnswer": "string",
    "mcOptions": [],
    "tfStatements": [],
    "createdAt": "2026-05-17T10:00:00Z",
    "updatedAt": "2026-05-17T10:05:00Z",
    "createdBy": "string | null",
    "updatedBy": "string | null"
  }
}
```

### Exception có thể trả về

#### `400 Bad Request`

- `Question not found with id: {questionUuid}`
- toàn bộ lỗi validation giống API tạo câu hỏi

#### `403 Forbidden`

- khi `access token` không hợp lệ hoặc không đủ quyền truy cập

#### `500 Internal Server Error`

- lỗi không mong muốn từ backend

---

## 4.5. Cập nhật trạng thái active của câu hỏi

### Đường dẫn

`PATCH /api/v1/questions/{questionUuid}/activation`

### Mô tả luồng

Nhận `questionUuid` và cờ `isActive` -> kiểm tra câu hỏi có tồn tại không -> cập nhật trạng thái sử dụng -> lưu xuống database -> trả lại dữ liệu chi tiết câu hỏi

### Input format

#### Path variable

- `questionUuid`: `UUID`

#### Body

```json
{
  "isActive": true
}
```

### Output format

```json
{
  "statusCode": 200,
  "message": "Update question activation",
  "data": {
    "questionUuid": "uuid",
    "gradeId": 10,
    "questionContent": "string",
    "questionTopic": "string | null",
    "questionType": "MCQ | TFQ | SAQ",
    "createdByUserUuid": "uuid",
    "isActive": false,
    "correctAnswerRaw": "string",
    "normalizedAnswer": "string",
    "mcOptions": [],
    "tfStatements": [],
    "createdAt": "2026-05-17T10:00:00Z",
    "updatedAt": "2026-05-17T10:10:00Z",
    "createdBy": "string | null",
    "updatedBy": "string | null"
  }
}
```

### Exception có thể trả về

#### `400 Bad Request`

- `Question not found with id: {questionUuid}`
- `Active flag is required`

#### `403 Forbidden`

- khi `access token` không hợp lệ hoặc không đủ quyền truy cập

#### `500 Internal Server Error`

- lỗi không mong muốn từ backend

---

## 5. Luồng liên quan module khác

Hiện tại `Question Module` chưa gọi sang module nghiệp vụ khác theo kiểu tạo dữ liệu chéo như `ExamAttempt` hay `ExamAssignment`.

Tuy nhiên, dữ liệu câu hỏi của module này sẽ được dùng trực tiếp bởi:

- `Exam Module`
  - để thêm câu hỏi vào đề
  - để validate `questionType` khi tạo đề
- `Exam Attempt Module`
  - để hiển thị nội dung câu hỏi cho học sinh
  - để lấy đáp án đúng và chấm điểm

Vì vậy, mọi thay đổi về rule chuẩn hóa đáp án trong `Question Module` sẽ ảnh hưởng trực tiếp đến:

- việc tạo đề
- việc làm bài
- việc chấm điểm

---

## 6. Ghi chú cho frontend

### 6.1. Với `MCQ`

- frontend nên gửi đủ 4 lựa chọn `A/B/C/D`
- `correctAnswerRaw` có thể là:
  - `A`
  - `AD`

### 6.2. Với `TFQ`

- frontend phải gửi đủ 4 statement
- `correctAnswerRaw` phải có đúng 4 ký tự `D/S/N`

### 6.3. Với `SAQ`

- frontend nhập đáp án đúng theo rule 1 đến 4 ký tự
- nếu có nhiều đáp án đúng, dùng `;`
- ví dụ:
  - `12`
  - `12;12,5`
  - `-1,2`

---

## 7. Gợi ý kiểm thử frontend

### 7.1. MCQ

- tạo câu hỏi hợp lệ với 4 options
- tạo câu hỏi thiếu 1 option
- tạo câu hỏi có option key trùng

### 7.2. TFQ

- tạo câu hỏi đủ 4 statements
- tạo câu hỏi thiếu statement
- tạo câu hỏi có `statementOrder` sai

### 7.3. SAQ

- tạo câu hỏi với 1 đáp án đúng
- tạo câu hỏi với nhiều đáp án đúng bằng `;`
- tạo câu hỏi với đáp án sai format như:
  - `12,34`
  - `1-2`
  - `12345`
