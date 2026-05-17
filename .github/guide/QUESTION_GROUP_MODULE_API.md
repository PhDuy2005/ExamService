# Question Group Module API

## 1. Mục đích module

Module `Question Group` dùng để quản lý nhóm câu hỏi tái sử dụng được, phục vụ cho việc:

- gom câu hỏi theo chủ đề
- chuẩn bị pool câu hỏi dùng nhiều lần
- gắn nhóm câu hỏi vào đề thi sau này

Hiện tại module này hỗ trợ:

- tạo `question group`
- lấy chi tiết `question group`
- tìm kiếm danh sách `question group`

---

## 2. Quy ước response chung

Tất cả API thành công của module này đều được bọc bởi `RestResponse`.

### Quy ước xác thực

- frontend cần gửi:
  - `Authorization: Bearer <access_token>`
- `access token` được cấp từ `Management Service`
- khi backend cần xác định người tạo group, backend đọc từ claim:
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
  "message": "Create question group",
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

### 3.1. Input tạo question group

```json
{
  "groupName": "Tích phân",
  "questionType": "MCQ | TFQ | SAQ",
  "questionTopic": "Tích phân | null",
  "questionCount": 5,
  "items": [
    {
      "questionUuid": "uuid"
    }
  ]
}
```

### 3.2. Response chi tiết question group

```json
{
  "questionGroupUuid": "uuid",
  "groupName": "string",
  "questionType": "MCQ | TFQ | SAQ",
  "questionTopic": "string | null",
  "questionCount": 5,
  "createdByUserUuid": "uuid",
  "items": [
    {
      "questionGroupItemUuid": "uuid",
      "questionUuid": "uuid",
      "questionDetail": {
        "questionUuid": "uuid",
        "questionContent": "string",
        "questionTopic": "string | null",
        "questionType": "MCQ | TFQ | SAQ"
      }
    }
  ],
  "createdAt": "2026-05-17T10:00:00Z",
  "updatedAt": "2026-05-17T10:00:00Z",
  "createdBy": "string | null",
  "updatedBy": "string | null"
}
```

---

## 4. Rule dữ liệu quan trọng

- `groupName` là bắt buộc
- `questionType` là bắt buộc
- `questionCount` là bắt buộc và phải lớn hơn hoặc bằng `1`
- `items` là bắt buộc
- group phải có ít nhất 1 item
- `questionCount` phải bằng đúng số phần tử trong `items`
- các `questionUuid` trong cùng một group không được trùng nhau
- toàn bộ `questionUuid` phải tồn tại trong ngân hàng câu hỏi
- `questionType` của group phải khớp với `questionType` của mọi câu trong group
- nếu group có `questionTopic`, tất cả câu trong group phải có cùng topic đó

---

## 5. API chi tiết

## 5.1. Tạo question group

### Đường dẫn

`POST /api/v1/question-groups`

### Mô tả luồng

Tạo `question group` mới -> validate dữ liệu đầu vào -> kiểm tra danh sách `questionUuid` có tồn tại không -> kiểm tra trùng id trong cùng group -> kiểm tra `questionType` của group có khớp với từng câu hỏi không -> kiểm tra `questionTopic` nếu có -> lưu `QuestionGroup` -> lưu `QuestionGroupItem` -> build response chi tiết -> trả kết quả

### Input format

```json
{
  "groupName": "Tích phân",
  "questionType": "MCQ",
  "questionTopic": "Tích phân",
  "questionCount": 3,
  "items": [
    {
      "questionUuid": "018f4a70-2222-7c11-8aa1-7c5d5b5b0201"
    },
    {
      "questionUuid": "018f4a70-2222-7c11-8aa1-7c5d5b5b0202"
    },
    {
      "questionUuid": "018f4a70-2222-7c11-8aa1-7c5d5b5b0203"
    }
  ]
}
```

### Output format

```json
{
  "statusCode": 200,
  "message": "Create question group",
  "data": {
    "questionGroupUuid": "uuid",
    "groupName": "Tích phân",
    "questionType": "MCQ",
    "questionTopic": "Tích phân",
    "questionCount": 3,
    "createdByUserUuid": "uuid",
    "items": [
      {
        "questionGroupItemUuid": "uuid",
        "questionUuid": "uuid",
        "questionDetail": {
          "questionUuid": "uuid",
          "questionContent": "string",
          "questionTopic": "Tích phân",
          "questionType": "MCQ"
        }
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

- `Group name must not be blank`
- `Question type is required`
- `Question count is required`
- `Question count must be at least 1`
- `Group items are required`
- `Question id is required`
- `Question group must contain at least one item`
- `Question ids in a question group must be unique`
- `Question count must match the number of group items`
- `Question not found with id: {questionUuid}`
- `Question group type must match item question type for question id: {questionUuid}`
- `Question group topic must match item question topic for question id: {questionUuid}`

#### `403 Forbidden`

- khi `access token` không hợp lệ hoặc không đủ quyền truy cập

#### `500 Internal Server Error`

- lỗi không mong muốn từ backend

---

## 5.2. Lấy chi tiết question group

### Đường dẫn

`GET /api/v1/question-groups/{questionGroupUuid}`

### Mô tả luồng

Nhận `questionGroupUuid` -> tìm group theo id -> lấy toàn bộ item của group -> lấy thông tin chi tiết các câu hỏi -> build response -> trả kết quả

### Input format

- `questionGroupUuid`: `UUID`

### Output format

Trả `ResQuestionGroupDTO`, ví dụ:

```json
{
  "statusCode": 200,
  "message": "Get question group by id",
  "data": {
    "questionGroupUuid": "uuid",
    "groupName": "Tích phân",
    "questionType": "MCQ",
    "questionTopic": "Tích phân",
    "questionCount": 3,
    "createdByUserUuid": "uuid",
    "items": [
      {
        "questionGroupItemUuid": "uuid",
        "questionUuid": "uuid",
        "questionDetail": {
          "questionUuid": "uuid",
          "questionContent": "string",
          "questionTopic": "Tích phân",
          "questionType": "MCQ"
        }
      }
    ]
  }
}
```

### Exception có thể trả về

#### `400 Bad Request`

- `Question group not found with id: {questionGroupUuid}`

#### `403 Forbidden`

- khi `access token` không hợp lệ hoặc không đủ quyền truy cập

#### `500 Internal Server Error`

- lỗi không mong muốn từ backend

---

## 5.3. Lấy danh sách question group

### Đường dẫn

`GET /api/v1/question-groups`

### Mô tả luồng

Nhận các tham số filter -> dựng điều kiện truy vấn động theo `name`, `type`, `topic` -> query phân trang -> build chi tiết từng group trong trang hiện tại -> trả kết quả dạng page

### Input format

- `name`: `String`, không bắt buộc
- `type`: `String`, không bắt buộc
- `topic`: `String`, không bắt buộc
- `page`: `int`, không bắt buộc
- `size`: `int`, không bắt buộc
- `sort`: `String`, không bắt buộc

Ví dụ:

`GET /api/v1/question-groups?name=tich&type=MCQ&topic=Tích phân&page=0&size=20&sort=createdAt,desc`

### Output format

Do backend đang trả `Page<ResQuestionGroupDTO>`, dữ liệu trong `data` là object phân trang của Spring.

### Exception có thể trả về

#### `400 Bad Request`

- `Invalid question type: {type}`

#### `403 Forbidden`

- khi `access token` không hợp lệ hoặc không đủ quyền truy cập

#### `500 Internal Server Error`

- lỗi không mong muốn từ backend

---

## 6. Luồng liên quan module khác

### 6.1. Liên quan `Question Module`

`Question Group Module` phụ thuộc trực tiếp vào dữ liệu câu hỏi để:

- kiểm tra `questionUuid` có tồn tại không
- kiểm tra `questionType`
- kiểm tra `questionTopic`
- build `questionDetail` trong response

### 6.2. Liên quan `Exam Module`

`Question Group Module` là nguồn group tái sử dụng cho `Exam Module`.

Khi tạo hoặc cập nhật đề:

- frontend có thể truyền `questionGroupUuid`
- backend lấy group gốc từ module này
- backend snapshot sang dữ liệu group riêng của đề

Điều này giúp:

- group gốc tái sử dụng được
- đề đã tạo không bị ảnh hưởng trực tiếp nếu group gốc bị sửa sau đó

---

## 7. Ghi chú cho frontend

### 7.1. Khi tạo group

Frontend nên truyền đúng:

- `groupName`
- `questionType`
- `questionCount`
- `items`

Nếu có `questionTopic`, nên đảm bảo topic của toàn bộ câu hỏi trong group thống nhất.

### 7.2. Khi dùng group trong Exam

Frontend có 2 lựa chọn:

- tạo sẵn group ở module này rồi lấy `questionGroupUuid`
- hoặc tạo group mới trực tiếp trong payload của `Exam`

### 7.3. Đây là pool tái sử dụng

`Question Group` hiện chỉ lưu:

- metadata của group
- danh sách câu hỏi thuộc group

Chưa lưu:

- `pickQuestionCount`
- `scorePerQuestion`
- `displayOrder`

Vì 3 field này thuộc ngữ cảnh của `Exam`, không phải thuộc ngữ cảnh của group gốc.

---

## 8. Gợi ý kiểm thử frontend

### 8.1. Tạo group hợp lệ

- tạo group `MCQ` với các câu cùng loại
- tạo group có `questionTopic`
- tạo group không có `questionTopic`

### 8.2. Kiểm thử validation

- thiếu `groupName`
- thiếu `questionType`
- `questionCount` nhỏ hơn `1`
- `items` rỗng
- `questionCount` không bằng số item
- trùng `questionUuid`
- group `MCQ` nhưng item là `TFQ`
- topic của item không khớp topic của group

### 8.3. Kiểm thử filter

- filter theo `name`
- filter theo `type`
- filter theo `topic`

### 8.4. Kiểm thử tích hợp với Exam

- tạo group riêng trước
- tạo đề dùng `questionGroupUuid`
- tạo đề dùng `newQuestionGroup`
- tạo đề dùng kết hợp cả 2 cách
