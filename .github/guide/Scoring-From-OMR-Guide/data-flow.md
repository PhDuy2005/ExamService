# Luồng Chấm OMR Qua Scoring Service

## 1. Quyết định nghiệp vụ

- User upload file `.pdf` lên `ExamService`.
- Người upload file thường là giáo viên/manager, không nhất thiết là học sinh.
- Một file `.pdf` có thể chứa nhiều bài làm của nhiều học sinh trong cùng một exam.
- Các bài làm trong cùng file có thể cùng `paperCode` hoặc khác `paperCode`.
- `ExamService` xử lý bất đồng bộ, không chờ chấm xong trong request đầu tiên.
- `attemptUuid` chỉ được tạo sau khi `ExamService` đã nhận đủ extracted data từ `ScoringService`.
- Trước khi có `attemptUuid`, frontend theo dõi tiến trình bằng `jobUuid`.
- `ExamService` là gRPC client.
- `ScoringService` là gRPC server.
- Giao tiếp ES -> SS dùng server streaming: ES gửi 1 request, SS stream n response.
- `ScoringService` có quyền ghi trực tiếp vào storage chung.
- `ExamService` và `ScoringService` phải thống nhất storage path hoặc storage key.

---

## 2. Data Flow Tổng Quát

```text
User
 ↓
POST /api/v1/omr/scoring-jobs
multipart/form-data: file(.pdf), examUuid
 ↓
ES validate request
 ↓
ES validate file là PDF
 ↓
ES lưu PDF gốc vào storage
 ↓
ES đọc số trang PDF
 ↓
ES tạo OmrScoringJob(status=PENDING/PROCESSING)
Lưu: jobUuid, examUuid, rawImageUrl/rawFileUrl, pageCount
Chưa tạo ExamAttempt
 ↓
ES trả 202 Accepted
Response: jobUuid, status, pageCount, resultCount=0, results=[]
 ↓
ES xử lý async job
 ↓
ES gọi SS qua gRPC server streaming
Request: jobUuid, examUuid, rawFileUrl/rawFileKey, pageCount
 ↓
SS đọc PDF gốc từ storage
 ↓
SS xử lý OMR theo từng trang hoặc từng phần
 ↓
SS ghi ảnh/result đã xử lý trực tiếp vào storage
 ↓
SS stream extracted result về ES
 ↓
ES nhận n response từ SS
 ↓
ES gom extracted data thành nhiều submission, mỗi submission có paperCode, studentCode, sections.mcq, sections.tfq, sections.saq
 ↓
ES resolve studentCode + schoolYear sang userUuid và fullname qua MS gRPC
 ↓
ES cập nhật OmrScoringJob(status=EXTRACTED/IMPORTING)
 ↓
ES gọi logic import OMR nội bộ
 ↓
ES tạo một ExamAttempt(submitSource=OMR_IMPORT) cho từng submission hợp lệ
 ↓
ES lưu StudentAnswer
 ↓
ES chấm điểm
 ↓
ES cập nhật ExamAttempt.score, rawImageUrl, scoredImageUrl cho từng submission
 ↓
ES lưu OmrImport audit log
 ↓
ES tạo/cập nhật OmrScoringJobResult cho từng submission, sau đó cập nhật OmrScoringJob(status=COMPLETED)
 ↓
User polling:
GET /api/v1/omr/scoring-jobs/{jobUuid}
 ↓
ES trả trạng thái job, pageCount, rawImageUrl và danh sách kết quả từng bài làm
```

---

## 3. Request Đầu Tiên Từ User

### API đề xuất

```http
POST /api/v1/omr/scoring-jobs
Content-Type: multipart/form-data
```

### Form-data

```text
file: bài làm OMR dạng .pdf
examUuid: uuid
```

`paperCode` và `studentCode` không gửi ở bước này vì file PDF là một batch nhiều bài làm. SS sẽ extract các thông tin này cho từng bài làm/trang. `schoolYear` không do SS biết; ES lấy từ `Exam.schoolYear` theo `examUuid` và lưu snapshot vào `OmrScoringJob`.

### Validate tại ES

- `file` bắt buộc.
- `file` phải là `.pdf`.
- MIME type nên là `application/pdf`.
- `examUuid` bắt buộc.
- `Exam` phải tồn tại theo `examUuid`.
- `Exam.schoolYear` phải có giá trị vì ES dùng `studentCode + schoolYear` để resolve học sinh.
- ES đọc được số trang trong PDF.

---

## 4. Response Đầu Tiên

Vì xử lý bất đồng bộ, response đầu tiên không trả điểm và không trả `attemptUuid`.

```json
{
  "statusCode": 202,
  "message": "Create OMR scoring job",
  "data": {
    "jobUuid": "uuid",
    "examUuid": "uuid",
    "schoolYear": "2025-2026",
    "status": "PROCESSING",
    "pageCount": 4,
    "rawImageUrl": "/storage/omr/raw/job-uuid.pdf",
    "scoredImageUrl": null,
    "resultCount": 0,
    "completedCount": 0,
    "failedCount": 0,
    "results": [],
    "createdAt": "2026-06-01T10:00:00Z",
    "updatedAt": "2026-06-01T10:00:00Z"
  }
}
```

### Ghi chú

- `pageCount` là số trang của file `.pdf` user upload.
- `results` ban đầu rỗng vì lúc này ES chưa có extracted data.
- `attemptUuid` chỉ xuất hiện trong từng result sau khi SS stream kết quả về và ES import/chấm xong bài làm tương ứng.

---

## 5. gRPC ES -> SS

### Kiểu giao tiếp

```text
Unary request -> server streaming response
```

ES gửi 1 request sang SS, SS trả nhiều response theo tiến trình hoặc theo từng page/section.

### Proto đang dùng

```text
D:\DoAn\DoAn1\proto\scoring\v1\scoring_normal.proto
```

Từ project `ExamService\ExamService`, đường dẫn tương đối là:

```text
..\..\proto\scoring\v1\scoring_normal.proto
```

Service:

```proto
service ScoringNormalService {
  rpc ReadOmr(ReadOmrRequest) returns (stream ReadOmrResponse);
}
```

### Request ES gửi sang SS

```text
jobUuid
examUuid
rawFileUrl/rawFileKey
pageCount
```

### Response SS stream về ES

Mỗi response có thể là:

- progress update
- extracted data của một bài làm/trang
- extracted data của một section trong bài làm
- scored image URL
- final result
- error

Ví dụ logic:

```text
EXTRACTION_STARTED
PAGE_EXTRACTED(page=1)
PAGE_EXTRACTED(page=2)
SCORED_IMAGE_READY
EXTRACTION_COMPLETED
```

---

## 6. Dữ Liệu Extracted ES Cần Gom Được

Sau khi nhận stream từ SS, ES cần gom được nhiều submission. Mỗi submission tương đương input import OMR hiện tại:

```json
{
  "examUuid": "uuid",
  "paperCode": "M001",
  "studentCode": "12345",
  "externalSubmissionId": "jobUuid hoặc id từ SS",
  "rawImageUrl": "/storage/omr/raw/job-uuid.pdf",
  "scoredImageUrl": "/storage/omr/scored/job-uuid.pdf",
  "sections": {
    "mcq": [
      {
        "sectionQuestionNumber": 1,
        "rawAnswer": "A"
      }
    ],
    "tfq": [
      {
        "sectionQuestionNumber": 1,
        "rawAnswer": "DSBD"
      }
    ],
    "saq": [
      {
        "sectionQuestionNumber": 1,
        "rawAnswer": "|23|,|7"
      }
    ]
  }
}
```

Từ bước này, ES dùng lại logic giống `POST /api/v1/omr/imports`.

---

## 7. Polling Kết Quả

### API đề xuất

```http
GET /api/v1/omr/scoring-jobs/{jobUuid}
```

### Khi đang xử lý

```json
{
  "statusCode": 200,
  "message": "Get OMR scoring job",
  "data": {
    "jobUuid": "uuid",
    "status": "PROCESSING",
    "pageCount": 4,
    "rawImageUrl": "/storage/omr/raw/job-uuid.pdf",
    "scoredImageUrl": null,
    "resultCount": 0,
    "completedCount": 0,
    "failedCount": 0,
    "results": []
  }
}
```

### Khi hoàn tất

```json
{
  "statusCode": 200,
  "message": "Get OMR scoring job",
  "data": {
    "jobUuid": "uuid",
    "status": "COMPLETED",
    "pageCount": 4,
    "rawImageUrl": "/storage/omr/raw/job-uuid.pdf",
    "scoredImageUrl": "/storage/omr/scored/job-uuid.pdf",
    "resultCount": 2,
    "completedCount": 2,
    "failedCount": 0,
    "results": [
      {
        "jobResultUuid": "uuid",
        "pageNumber": 1,
        "paperCode": "M001",
        "studentCode": "12345",
        "studentFullname": "Nguyễn Văn A",
        "studentUuid": "uuid",
        "attemptUuid": "uuid",
        "status": "COMPLETED",
        "score": 8.5,
        "rawImageUrl": "/storage/omr/raw/page-1.pdf",
        "scoredImageUrl": "/storage/omr/scored/page-1.jpg",
        "errorMessage": null
      },
      {
        "jobResultUuid": "uuid",
        "pageNumber": 2,
        "paperCode": "M002",
        "studentCode": "54321",
        "studentFullname": "Nguyễn Văn A",
        "studentUuid": "uuid",
        "attemptUuid": "uuid",
        "status": "COMPLETED",
        "score": 7.25,
        "rawImageUrl": "/storage/omr/raw/page-2.pdf",
        "scoredImageUrl": "/storage/omr/scored/page-2.jpg",
        "errorMessage": null
      }
    ]
  }
}
```

### Khi lỗi

```json
{
  "statusCode": 200,
  "message": "Get OMR scoring job",
  "data": {
    "jobUuid": "uuid",
    "status": "FAILED",
    "pageCount": 4,
    "rawImageUrl": "/storage/omr/raw/job-uuid.pdf",
    "scoredImageUrl": null,
    "resultCount": 0,
    "completedCount": 0,
    "failedCount": 0,
    "results": [],
    "errorMessage": "Cannot extract OMR markers"
  }
}
```

---

## 8. Trạng Thái Job Đề Xuất

```text
PENDING
PROCESSING
EXTRACTED
IMPORTING
COMPLETED
FAILED
```

Ý nghĩa:

- `PENDING`: job vừa được tạo, chưa gọi SS.
- `PROCESSING`: ES đang gọi SS hoặc SS đang xử lý.
- `EXTRACTED`: ES đã nhận đủ extracted data.
- `IMPORTING`: ES đang tạo attempt, lưu answer và chấm điểm.
- `COMPLETED`: đã tạo attempt và chấm điểm xong.
- `FAILED`: có lỗi trong upload, extract, import hoặc scoring.

---

## 9. API Gateway

Vì client chỉ gọi REST API của ES, API Gateway không cần hỗ trợ gRPC nếu luồng là:

```text
User -> API Gateway -> ES REST
ES -> SS gRPC nội bộ
```

API Gateway cần chú ý:

- Cho phép upload multipart file `.pdf`.
- Tăng giới hạn request body size nếu PDF lớn.
- Route `POST /api/v1/omr/scoring-jobs` về ES.
- Route `GET /api/v1/omr/scoring-jobs/{jobUuid}` về ES.
- Route `/storage/**` về nơi serve static file nếu frontend cần xem file qua gateway.

API Gateway chỉ cần hỗ trợ gRPC nếu có luồng:

```text
Client -> Gateway -> SS gRPC
```

hoặc:

```text
Client -> Gateway -> ES gRPC
```

Trong thiết kế hiện tại, không cần 2 luồng này.


