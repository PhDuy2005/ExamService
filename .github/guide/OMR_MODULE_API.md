# OMR Module API

## 1. Mục đích module

Module `OMR` phục vụ luồng chấm bài giấy:

- tạo bản in đề có `paperCode`
- lưu snapshot câu hỏi đúng với bản in
- nhận file PDF bài làm từ user và tạo job chấm OMR bất đồng bộ
- nhận dữ liệu scan từ `ScoringService`
- tự tạo `ExamAttempt` dạng `OMR_IMPORT`
- lưu đáp án và chấm điểm mà không cần client học sinh start attempt

---

## 2. Quy ước xác thực

- frontend hoặc service gọi API cần gửi:
  - `Authorization: Bearer <access_token>`
- `access token` được cấp từ `Management Service`
- khi tạo `ExamPaper`, backend đọc user hiện tại từ claim:
  - `user.id`

---

## 3. API tạo exam paper

### Đường dẫn

`POST /api/v1/omr/exam-papers`

### Mô tả luồng

Nhận `examUuid` và `paperCode` -> kiểm tra đề tồn tại -> kiểm tra mã đề chưa bị trùng trong cùng đề -> random câu hỏi từ các group theo `pickQuestionCount` -> tạo snapshot câu hỏi cố định cho bản in -> sinh file PDF đề thi -> lưu PDF vào storage -> lưu `ExamPaper` cùng `pdfUrl` -> trả danh sách câu hỏi theo `questionOrder` nội bộ.

PDF chứa thông tin đề, nội dung câu hỏi, lựa chọn `MCQ`, mệnh đề `TFQ`, vùng trả lời `SAQ` và ảnh câu hỏi nếu `imagePath` là URL nội bộ `/storage/...`. Bản in được gom theo thứ tự phần `MCQ -> TFQ -> SAQ`, dùng `sectionQuestionNumber` làm số câu hiển thị và không chứa đáp án đúng. Xem chi tiết tại [PDF Module API](D:/DoAn/DoAn1/ExamService/ExamService/.github/guide/PDF_MODULE_API.md).

### Input format

```json
{
  "examUuid": "018f4a60-12ab-7a11-a9d1-7c5d5b5b0001",
  "paperCode": "M001"
}
```

### Output format

```json
{
  "statusCode": 200,
  "message": "Create OMR exam paper",
  "data": {
    "paperUuid": "uuid",
    "examUuid": "uuid",
    "paperCode": "M001",
    "generatedAt": "2026-05-25T10:00:00Z",
    "generatedByUserUuid": "uuid",
    "pdfUrl": "/storage/exam-papers/exam-uuid/M001.pdf",
    "questions": [
      {
        "questionOrder": 1,
        "sectionQuestionNumber": 1,
        "questionUuid": "uuid",
        "questionType": "MCQ",
        "imagePath": "/storage/questions/example.png",
        "score": 0.25,
        "fromQuestionGroup": true,
        "groupUuid": "uuid",
        "groupName": "Tích phân"
      }
    ]
  }
}
```

### Exception có thể trả về

- `Exam id is required`
- `Paper code must not be blank`
- `Exam not found with id: {examUuid}`
- `Exam paper already exists with code: {paperCode}`
- `Exam paper must contain at least one question`
- `User id is missing from JWT`
- `Failed to serialize exam paper question snapshot`
- `Exam paper font file not found: {fontPath}`
- `Failed to generate exam paper PDF`
- `Failed to store exam paper PDF`

---

## 3.1. API lấy danh sách exam paper theo exam

### Đường dẫn

`GET /api/v1/omr/exams/{examUuid}/exam-papers`

### Mô tả luồng

Nhận `examUuid` -> kiểm tra đề tồn tại -> lấy toàn bộ `ExamPaper` của đề và sắp xếp tăng dần theo `paperCode` -> đọc snapshot câu hỏi của từng mã đề -> trả danh sách mã đề cùng câu hỏi.

Một exam có thể có nhiều `ExamPaper`, vì vậy API trả về một danh sách.

### Input format

- `examUuid`: `UUID`

### Output format

```json
{
  "statusCode": 200,
  "message": "Get OMR exam papers by exam id",
  "data": [
    {
      "paperUuid": "uuid",
      "examUuid": "uuid",
      "paperCode": "M001",
      "generatedAt": "2026-05-25T10:00:00Z",
      "generatedByUserUuid": "uuid",
      "pdfUrl": "/storage/exam-papers/exam-uuid/M001.pdf",
      "questions": [
        {
          "questionOrder": 1,
          "sectionQuestionNumber": 1,
          "questionUuid": "uuid",
          "questionType": "MCQ",
          "imagePath": "/storage/questions/example.png",
          "score": 0.25,
          "fromQuestionGroup": true,
          "groupUuid": "uuid",
          "groupName": "Tích phân"
        }
      ]
    }
  ]
}
```

Nếu exam tồn tại nhưng chưa có mã đề, `data` là danh sách rỗng.

Frontend có thể dùng `pdfUrl` để mở hoặc tải file PDF đề thi.

### Exception có thể trả về

- `Exam not found with id: {examUuid}`
- `Failed to read exam paper question snapshot`

---

## 3.2. Cấu hình sinh PDF đề thi

- tài liệu đầy đủ về tạo, tải và render PDF:
  - [PDF Module API](D:/DoAn/DoAn1/ExamService/ExamService/.github/guide/PDF_MODULE_API.md)
- thư mục lưu file: `/storage/exam-papers/{examUuid}/{paperCode}.pdf`
- font Unicode được cấu hình bằng:
  - `EXAMSERVICE_EXAM_PAPER_FONT_PATH`
  - hoặc property `examservice.exam-paper.font-path`
- giá trị mặc định trên môi trường Windows hiện tại:
  - `C:/Windows/Fonts/arial.ttf`
- khi deploy Linux/container, cần cấu hình đường dẫn tới một file font Unicode `.ttf`, ví dụ DejaVu Sans hoặc Noto Sans
- nếu ảnh câu hỏi không tồn tại hoặc `imagePath` không phải URL nội bộ `/storage/...`, PDF vẫn được tạo nhưng bỏ qua ảnh đó

---

## 4. API tạo OMR scoring job từ PDF

### Đường dẫn

`POST /api/v1/omr/scoring-jobs`

### Mô tả luồng

User upload file PDF chứa nhiều bài làm trong cùng một exam -> ES validate `examUuid` tồn tại -> validate file là PDF -> lưu file vào storage -> đọc số trang PDF -> tạo `OmrScoringJob` trạng thái `PROCESSING` -> trả `202 Accepted` với `jobUuid`, `pageCount`, danh sách kết quả rỗng -> ES xử lý async và sẽ gọi SS qua gRPC server streaming khi proto/stub được tích hợp.

### Input format

`multipart/form-data`

```text
file: file PDF bài làm OMR
examUuid: 018f4a60-12ab-7a11-a9d1-7c5d5b5b0001
```

`paperCode` và `studentCode` không nằm ở request tạo job vì một file PDF có thể chứa nhiều bài làm của nhiều học sinh, mỗi bài có thể dùng mã đề khác nhau. Các thông tin này sẽ được lấy từ kết quả extract của `ScoringService` cho từng bài làm. `schoolYear` không do SS extract; ES lấy từ `Exam.schoolYear` theo `examUuid` và snapshot vào `OmrScoringJob`.

### Output format

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
    "rawImageUrl": "/storage/omr/raw/1717250000000-scan.pdf",
    "scoredImageUrl": null,
    "resultCount": 0,
    "completedCount": 0,
    "failedCount": 0,
    "results": [],
    "errorMessage": null,
    "createdAt": "2026-06-01T10:00:00Z",
    "updatedAt": "2026-06-01T10:00:00Z"
  }
}
```

### Exception có thể trả về

- `PDF file is required`
- `Only PDF file is allowed`
- `Invalid file type based on MIME type. Only application/pdf is allowed`
- `Cannot read PDF page count`
- `Exam id is required`
- `Exam not found with id: {examUuid}`
- `Exam school year is required for OMR scoring job`
- các lỗi lưu file từ `FileService`

---

## 5. API lấy OMR scoring job

### Đường dẫn

`GET /api/v1/omr/scoring-jobs/{jobUuid}`

### Mô tả luồng

Frontend dùng `jobUuid` để polling -> ES tìm `OmrScoringJob` -> trả trạng thái hiện tại, `pageCount`, file URL và danh sách kết quả con. Mỗi kết quả con tương ứng một bài làm được SS extract, có `paperCode`, `studentCode`, `studentFullname`, `schoolYear`, `studentUuid`, `attemptUuid`, `score` riêng. Trong đó `schoolYear` lấy từ `OmrScoringJob/Exam`, còn `studentUuid` chỉ có sau khi ES resolve `studentCode + schoolYear` sang `userUuid` qua Management Service.

### Output format

```json
{
  "statusCode": 200,
  "message": "Get OMR scoring job",
  "data": {
    "jobUuid": "uuid",
    "examUuid": "uuid",
    "schoolYear": "2025-2026",
    "status": "PROCESSING | EXTRACTED | IMPORTING | COMPLETED | FAILED",
    "pageCount": 4,
    "rawImageUrl": "/storage/omr/raw/1717250000000-scan.pdf",
    "scoredImageUrl": null,
    "resultCount": 2,
    "completedCount": 1,
    "failedCount": 0,
    "results": [
      {
        "jobResultUuid": "uuid",
        "pageNumber": 1,
        "paperCode": "M001",
        "studentCode": "12345",
        "studentFullname": "Nguyễn Văn A",
        "schoolYear": "2025-2026",
        "studentUuid": "uuid",
        "attemptUuid": "uuid",
        "status": "COMPLETED",
        "score": 8.5,
        "rawImageUrl": "/storage/omr/raw/page-1.pdf",
        "scoredImageUrl": "/storage/omr/scored/page-1.jpg",
        "errorMessage": null
      }
    ],
    "errorMessage": null,
    "createdAt": "2026-06-01T10:00:00Z",
    "updatedAt": "2026-06-01T10:00:00Z"
  }
}
```

### Exception có thể trả về

- `OMR scoring job not found with id: {jobUuid}`

---

## 6. API import OMR data

### Đường dẫn

`POST /api/v1/omr/imports`

### Mô tả luồng

Nhận dữ liệu OMR từ `ScoringService` -> tìm `ExamPaper` theo `examUuid + paperCode` -> kiểm tra `externalSubmissionId` nếu có để chống import trùng -> map từng `sectionQuestionNumber` trong từng section về `questionUuid` trong snapshot của mã đề -> tạo `ExamAttempt` với `submitSource = OMR_IMPORT` -> lưu đáp án vào `StudentAnswer` -> tạo final answer -> chấm điểm -> lưu log `OmrImport` -> trả kết quả import.

### Input format

```json
{
  "examUuid": "018f4a60-12ab-7a11-a9d1-7c5d5b5b0001",
  "paperCode": "M001",
  "studentUuid": "018f4a61-22cd-7b11-9fd2-7c5d5b5b0002",
  "studentId": "10013",
  "studentFullname": "Nguyen Van A",
  "externalSubmissionId": "scoring-service-omr-0001",
  "rawImageUrl": "https://storage.example.com/omr/raw/scan-0001.jpg",
  "scoredImageUrl": "https://storage.example.com/omr/scored/scan-0001.jpg",
  "scannedAt": "2026-05-25T10:05:00Z",
  "sections": {
    "mcq": [
      {
        "sectionQuestionNumber": 1,
        "rawAnswer": "A"
      },
      {
        "sectionQuestionNumber": 2,
        "rawAnswer": "AD"
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

### Output format

```json
{
  "statusCode": 200,
  "message": "Import OMR data",
  "data": {
    "omrImportUuid": "uuid",
    "examUuid": "uuid",
    "paperUuid": "uuid",
    "paperCode": "M001",
    "studentUuid": "uuid",
    "attemptUuid": "uuid",
    "externalSubmissionId": "scoring-service-omr-0001",
    "status": "IMPORTED",
    "score": 8.5,
    "importedAt": "2026-05-25T10:06:00Z"
  }
}
```

### Exception có thể trả về

- `Exam id is required`
- `Paper code must not be blank`
- `Student id is required`
- `Sections are required`
- `OMR sections must contain at least one answer`
- `Section question number is required`
- `Exam paper not found with code: {paperCode}`
- `OMR submission already imported: {externalSubmissionId}`
- `Section question number must be unique in OMR section {questionType}: {sectionQuestionNumber}`
- `Failed to serialize OMR import payload`
- các lỗi chấm bài kế thừa từ `ExamAttemptService`

---

## 7. Quy ước sections

`sections` chia dữ liệu OMR thành 3 phần đúng với layout phiếu:

- `mcq`: danh sách đáp án trắc nghiệm
- `tfq`: danh sách đáp án đúng/sai
- `saq`: danh sách đáp án ngắn

Mỗi section dùng `sectionQuestionNumber` riêng, bắt đầu từ `1`.

Ví dụ:

- `sections.mcq[0].sectionQuestionNumber = 1` nghĩa là câu MCQ số 1 trên phiếu
- `sections.tfq[0].sectionQuestionNumber = 1` nghĩa là câu TFQ số 1 trên phiếu
- `sections.saq[0].sectionQuestionNumber = 1` nghĩa là câu SAQ số 1 trên phiếu

Backend sẽ map theo cặp:

```text
questionType + sectionQuestionNumber -> questionOrder nội bộ của ExamPaper -> questionUuid
```

`questionOrder` vẫn tồn tại trong `ExamPaper` để backend lưu snapshot theo thứ tự toàn cục, nhưng `ScoringService` không cần gửi `questionOrder` khi import OMR.

Nếu `ScoringService` gửi dư câu không thuộc snapshot của mã đề, backend sẽ bỏ qua câu đó.

Ví dụ đề chỉ có `MCQ` từ câu `1` đến câu `12`, nhưng `ScoringService` gửi `MCQ` từ câu `1` đến câu `40`, backend chỉ xử lý câu `1` đến câu `12` và bỏ qua câu `13` đến câu `40`, kể cả khi các câu dư có `rawAnswer`.

Ví dụ:

```json
{
  "sections": {
    "mcq": [
      {
        "sectionQuestionNumber": 1,
        "rawAnswer": "AD"
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

---

## 8. Quy ước rawAnswer

### MCQ

- `A`, `B`, `C`, `D`: học sinh tô đúng 1 lựa chọn
- nếu học sinh tô nhiều hơn 1 lựa chọn, gửi nguyên các lựa chọn đã tô
- ví dụ học sinh tô `A` và `D` thì gửi `AD`
- `null` hoặc rỗng: bỏ trống

### TFQ

- luôn gửi 4 ký tự
- `D`: đúng
- `S`: sai
- `B`: bỏ trống

Ví dụ:

```json
{
  "sectionQuestionNumber": 2,
  "rawAnswer": "DSBD"
}
```

### SAQ

Với OMR, `rawAnswer` dùng dấu `|` để ngăn cách 4 cột.

Ví dụ:

- `1|2||` -> normalize thành `12__`
- `|23|,|7` -> normalize thành `_M,7`
- `-|1|,|2` -> normalize thành `-1,2`

Quy ước từng cột:

- cột rỗng -> `_`
- cột có đúng 1 ký tự hợp lệ -> giữ nguyên
- cột có nhiều hơn 1 ký tự -> `M`

Nếu `normalizedAnswer` có `M`, câu `SAQ` thực tế sẽ không khớp đáp án chuẩn và bị tính sai.

---

## 9. Ghi chú nghiệp vụ

- OMR không dùng `POST /api/v1/student/exams/{examUuid}/attempts`
- `attemptUuid` được `ExamService` tự tạo trong lúc import OMR
- `rawImageUrl` và `scoredImageUrl` nếu có sẽ được lưu vào `ExamAttempt` để frontend xem lại ảnh scan gốc và ảnh đã chấm
- `ExamPaper` là snapshot bản in, giúp `sectionQuestionNumber` trong từng section map chính xác về `questionUuid`
- nếu đề có group random, random xảy ra khi tạo `ExamPaper`, không xảy ra khi import OMR
- `OmrImport` lưu lại payload scan và attempt được tạo để phục vụ audit/debug






