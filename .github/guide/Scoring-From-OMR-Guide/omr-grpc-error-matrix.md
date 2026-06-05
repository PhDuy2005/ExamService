# OMR + gRPC Error Matrix

## 1. Phạm vi

Tài liệu này rà soát luồng chấm bài bằng OMR trong `ExamService`, tập trung vào:

- tạo `OmrScoringJob`
- kích hoạt worker async
- gọi gRPC sang `ScoringService` (SS)
- gọi gRPC sang `ManagementService` (MS)
- import dữ liệu OMR thành `ExamAttempt`
- chấm điểm và lưu kết quả

Ngoài các exception đã throw hoặc các log lỗi hiện có, tài liệu cũng chỉ ra những chỗ hiện chưa được handle đầy đủ.

---

## 2. Lưu ý quan trọng về revision hiện tại

Luồng hiện tại đã được nối lại theo hướng:

- `OmrScoringJobService` publish `OmrScoringJobCreatedEvent`
- `OmrScoringJobWorker` nhận event bằng `@TransactionalEventListener(phase = AFTER_COMMIT)`
- listener được đánh dấu `@Async`

Điều này có nghĩa là:

- job chỉ được kích hoạt sau khi transaction tạo job đã commit
- worker chạy ở thread async riêng
- nếu không thấy log `Processing OMR scoring job: ...`, cần kiểm tra lại listener wiring hoặc cấu hình async

---

## 3. Luồng thực thi chuẩn

Nếu nối event/worker đúng, luồng mong đợi là:

1. `OmrScoringJobService.createScoringJob(...)`
2. lưu `OmrScoringJob`
3. kích hoạt `OmrScoringJobWorker.processJob(...)`
4. worker gọi `ScoringService.readOmr(...)`
5. với mỗi page response:
   - validate response
   - gọi `ManagementService.resolveStudents(...)`
   - build `ReqOmrImportDTO`
   - gọi `OmrService.importOmrData(...)`
6. `OmrService`:
   - tìm `ExamPaper`
   - map đáp án OMR sang `questionOrder`
   - gọi `ExamAttemptService.importOmrAttempt(...)`
7. `ExamAttemptService`:
   - tạo `ExamAttempt`
   - lưu `StudentAnswer`
   - `finalizeAttempt(...)`
   - chấm điểm
8. worker lưu `OmrScoringJobResult`
9. hết stream thì worker set `OmrScoringJob = COMPLETED`

---

## 4. Error Matrix

| Bước | Vị trí code | Trigger | Exception / log hiện tại | Ảnh hưởng | Đã handle chưa |
|---|---|---|---|---|---|
| Tạo OMR job | `OmrScoringJobService.validatePdfFile` | file null/rỗng | `StorageException("PDF file is required")` + log `warn` | chưa tạo job | Có |
| Tạo OMR job | `OmrScoringJobService.validatePdfFile` | file không phải `.pdf` | `StorageException("Only PDF file is allowed")` + log `warn` | chưa tạo job | Có |
| Tạo OMR job | `OmrScoringJobService.validatePdfFile` | MIME type sai | `StorageException("Invalid file type based on MIME type. Only application/pdf is allowed")` + log `warn` | chưa tạo job | Có |
| Tạo OMR job | `OmrScoringJobService.createScoringJob` | thiếu `examUuid` | `StorageException("Exam id is required")` + log `warn` | chưa tạo job | Có |
| Tạo OMR job | `OmrScoringJobService.createScoringJob` | exam không tồn tại | `IdInvalidException("Exam not found with id: ...")` | chưa tạo job | Có |
| Tạo OMR job | `OmrScoringJobService.createScoringJob` | exam thiếu `schoolYear` | `IdInvalidException("Exam school year is required for OMR scoring job")` + log `warn` | chưa tạo job | Có |
| Tạo OMR job | `OmrScoringJobService.countPdfPages` | đọc bytes PDF lỗi | `IOException` | chưa tạo job | Chưa bọc riêng |
| Tạo OMR job | `OmrScoringJobService.countPdfPages` | không xác định được page count | `StorageException("Cannot read PDF page count")` + log `warn` | chưa tạo job | Có |
| Sau khi tạo job | `OmrScoringJobService.createScoringJob` + `OmrScoringJobWorker.handleJobCreated` | listener event không được đăng ký, `@EnableAsync` không hoạt động, hoặc thread async không chạy | thường không throw ra request hiện tại, có thể chỉ thấy job đứng `PROCESSING` | job có thể đứng `PROCESSING` | Cần kiểm tra wiring/runtime |
| Worker bắt đầu | `OmrScoringJobWorker.processJob` | job không tồn tại | `IllegalArgumentException("OMR scoring job not found with id: ...")` | job không update status | Chưa handle đúng |
| Gọi SS | `OmrScoringJobWorker.processJob` | SS timeout / unavailable / gRPC lỗi | vào `catch (Exception)` -> log `error("Processing OMR scoring job failed...")` | job -> `FAILED` | Có, nhưng generic |
| Response từ SS | `OmrScoringJobWorker.handleResponse` | `success=false` | log `warn("OMR scoring result rejected by scoring service...")` | page result -> `FAILED` | Có |
| Response từ SS | `OmrScoringJobWorker.handleResponse` | thiếu payload | log `warn("Scoring response data is missing")` | page result -> `FAILED` | Có |
| Build import request | `OmrScoringJobWorker.resolveStudentIdentity` | thiếu `studentCode` | `IllegalArgumentException("Student code is required")` | page result -> `FAILED` | Có, do `handleResponse` catch |
| Build import request | `OmrScoringJobWorker.resolveStudentIdentity` | thiếu `schoolYear` | `IllegalArgumentException("School year is required")` | page result -> `FAILED` | Có |
| Gọi MS | `OmrScoringJobWorker.resolveStudentIdentity` | MS timeout / unavailable / gRPC lỗi | runtime exception từ stub | page result -> `FAILED` | Có, nhưng generic |
| Resolve student | `OmrScoringJobWorker.resolveStudentIdentity` | student không resolve được | `IllegalArgumentException("Student code could not be resolved: ...")` | page result -> `FAILED` | Có |
| Resolve student | `OmrScoringJobWorker.parseUserUuid` | MS trả thiếu `userUuid` | `IllegalArgumentException("Resolved student user uuid is missing: ...")` | page result -> `FAILED` | Có |
| Parse scannedAt | `OmrScoringJobWorker.parseInstant` | format thời gian sai | `DateTimeParseException` | page result -> `FAILED` | Có, nhưng generic |
| Import OMR | `OmrService.importOmrData` | trùng `externalSubmissionId` | `IdInvalidException("OMR submission already imported: ...")` | page result -> `FAILED` | Có |
| Import OMR | `OmrService.normalizePaperCode` | `paperCode == null` | `NullPointerException` từ `trim()` | page result -> `FAILED` | Chưa handle |
| Import OMR | `OmrService.importOmrData` | không có `ExamPaper` | `IdInvalidException("Exam paper not found with code: ...")` | page result -> `FAILED` | Có |
| Đọc snapshot paper | `OmrService.deserializeSnapshots` | JSON snapshot lỗi | `IdInvalidException("Failed to read exam paper question snapshot", ex)` | page result -> `FAILED` | Có |
| Map OMR sections | `OmrService.buildRawAnswerMap` | `sections == null` | `NullPointerException` | page result -> `FAILED` | Chưa handle |
| Map OMR sections | `OmrService.buildRawAnswerMap` | snapshot paper có duplicate `sectionQuestionNumber` trong cùng `QuestionType` | `IllegalStateException` từ `Collectors.toMap(...)` | page result -> `FAILED` | Chưa handle |
| Map OMR sections | `OmrService.buildRawAnswerMap` | không có đáp án nào map được | `IdInvalidException("OMR sections must contain at least one answer")` | page result -> `FAILED` | Có |
| Map OMR sections | `OmrService.addSectionAnswers` | trùng `sectionQuestionNumber` trong request của cùng section | `IdInvalidException("Section question number must be unique in OMR section ...")` | page result -> `FAILED` | Có |
| Tạo attempt OMR | `ExamAttemptService.importOmrAttempt` | exam không tồn tại | `IdInvalidException("Exam not found with id: ...")` | page result -> `FAILED` | Có |
| Tạo attempt OMR | `ExamAttemptService.deserializeSnapshots` | snapshot attempt lỗi | `IdInvalidException("Failed to read attempt question snapshot", ex)` | page result -> `FAILED` | Có |
| Tạo attempt OMR | `ExamAttemptService.importOmrAttempt` | `questionOrder` không thuộc paper | `IdInvalidException("Question order does not belong to this paper: ...")` | page result -> `FAILED` | Có |
| Lưu answer thô OMR | `ExamAttemptService.importOmrAttempt` | duplicate key / constraint DB | `DataIntegrityViolationException` hoặc exception DB tương đương | page result -> `FAILED` | Có, nhưng chỉ bị catch generic ở worker |
| Finalize attempt | `ExamAttemptService.finalizeAttempt` | query flush ra lỗi insert đang chờ | `DataIntegrityViolationException` / `ConstraintViolationException` | page result -> `FAILED` | Có, nhưng generic |
| Lưu final answers | `ExamAttemptService.finalizeAttempt` | duplicate key / constraint DB | exception DB runtime | page result -> `FAILED` | Có, nhưng generic |
| Lưu failed page result | `OmrScoringJobWorker.saveFailedResult` | DB lỗi khi save `OmrScoringJobResult` | exception DB runtime | có thể làm rơi cả async flow | Chưa handle |
| Update job FAILED | `OmrScoringJobWorker.processJob` trong block `catch` | DB lỗi khi save job status | exception DB runtime | job có thể không chuyển `FAILED` | Chưa handle |

---

## 5. Các exception/log đã được handle ở mức nào

## 5.1. Được handle tốt ở mức nghiệp vụ

- file upload không hợp lệ
- exam không tồn tại
- thiếu `schoolYear`
- `ScoringService` trả `success=false`
- `ScoringService` trả thiếu payload
- student code không resolve được từ `ManagementService`
- paper không tồn tại
- request OMR không map được đáp án nào
- `questionOrder` không thuộc paper

Các case này ít nhất đã có:

- message rõ ràng
- hoặc `warn/error` log
- hoặc được chuyển thành `FAILED result`

## 5.2. Được handle nhưng còn generic

- lỗi gRPC từ `ScoringService`
- lỗi gRPC từ `ManagementService`
- lỗi DB khi import attempt / save answer / finalize attempt
- lỗi parse `scannedAt`

Các lỗi này hiện đa số bị gom vào:

- `catch (Exception ex)` trong worker
- log `error(...)`
- lưu `OmrScoringJobResult` dạng `FAILED`

Nhược điểm:

- chưa phân loại được lỗi hạ tầng với lỗi dữ liệu
- chưa có retry
- chưa có message nghiệp vụ riêng theo từng nguyên nhân

## 5.3. Chưa được handle đầy đủ

- listener event/async có thể không hoạt động ở runtime dù code đã khai báo
- `findByJobUuid(...)` nằm ngoài `try/catch` của `processJob`
- `paperCode == null`
- `sections == null`
- snapshot paper bị trùng `sectionQuestionNumber`
- DB fail khi lưu `OmrScoringJobResult`
- DB fail khi save job trong chính block `catch`

---

## 6. Các exception hiện chưa được handle đúng hoặc chưa được bảo vệ

### 6.1. Worker không được kích hoạt vì event/async không chạy ở runtime

Hiện trạng có thể xảy ra:

- service publish `OmrScoringJobCreatedEvent`
- listener không được Spring đăng ký
- `@EnableAsync` không hoạt động
- executor async gặp vấn đề hoặc task không được schedule

Hệ quả:

- job có thể được tạo nhưng không chạy
- không có gRPC call nào sang SS/MS

### 6.2. `job not found` rơi ra ngoài async method

Hiện trạng:

- `findByJobUuid(...)` đang nằm trước `try`

Hệ quả:

- nếu job không tìm thấy, exception không đi vào block:
  - set `job.status = FAILED`
  - save error message
- với `@Async void`, lỗi sẽ rơi ra `SimpleAsyncUncaughtExceptionHandler`

### 6.3. Thiếu validate null ở request OMR

Các điểm dễ nổ:

- `paperCode == null` tại `normalizePaperCode()`
- `sections == null` tại `buildRawAnswerMap()`

Hệ quả:

- nổ `NullPointerException`
- log hiện tại không nói rõ input nào bị thiếu

### 6.4. Lỗi cấu trúc snapshot paper chưa có message nghiệp vụ riêng

Hiện trạng:

- `Collectors.toMap(...)` trong `buildRawAnswerMap()` có thể ném `IllegalStateException`

Hệ quả:

- log chỉ thấy lỗi runtime
- khó hiểu với QA/dev nếu snapshot bị hỏng hoặc dữ liệu paper không nhất quán

### 6.5. Lỗi khi lưu `FAILED result` có thể che mất lỗi gốc

Hiện trạng:

- sau khi bắt exception trong `handleResponse`, code gọi `saveFailedResult(...)`

Hệ quả:

- nếu insert `OmrScoringJobResult` lại fail, lỗi mới có thể đè lên lỗi gốc
- việc điều tra nguyên nhân đầu tiên sẽ khó hơn

### 6.6. Lỗi khi save job trong block `catch` chưa có lớp bảo vệ thứ hai

Hiện trạng:

- trong `processJob.catch`, code set `FAILED` rồi `save(job)`

Hệ quả:

- nếu DB fail ở đây, job có thể không phản ánh đúng trạng thái
- async method có thể rơi luôn ra ngoài

---

## 7. Gợi ý ưu tiên xử lý

### Mức 1 - cần làm trước

1. xác nhận listener event và `@Async` thực sự chạy ở runtime
2. bọc toàn bộ `processJob()` trong `try/catch`, kể cả đoạn load job ban đầu
3. thêm validate null cho:
   - `paperCode`
   - `sections`

### Mức 2 - nên làm tiếp

1. handle riêng `StatusRuntimeException` cho SS/MS
2. handle riêng `DataIntegrityViolationException` khi import attempt
3. thêm message rõ hơn cho lỗi snapshot/paper không nhất quán

### Mức 3 - tăng độ an toàn vận hành

1. bọc `saveFailedResult(...)` bằng lớp bảo vệ thứ hai
2. bọc `save(job)` trong block `catch`
3. bổ sung retry hoặc circuit-breaker nếu cần cho gRPC

---

## 8. Tóm tắt ngắn cho debug

Nếu đang debug luồng OMR, nên kiểm tra theo thứ tự:

1. job có thực sự kích hoạt worker không
2. worker có gọi được `ScoringService` không
3. response từ SS có `success=true` và `data` không
4. `studentCode` có resolve được qua MS không
5. `paperCode`, `sections`, snapshot paper có hợp lệ không
6. `ExamAttempt` và `StudentAnswer` có vướng constraint DB không
7. khi fail, `OmrScoringJobResult` và `OmrScoringJob` có save được trạng thái lỗi không

### 8.1. Checklist nhanh cho case job đứng `PROCESSING`

1. Kiểm tra log tạo job có xuất hiện:
   - `OMR scoring job created successfully: ...`
2. Kiểm tra sau đó có xuất hiện:
   - `Processing OMR scoring job: ...`
3. Nếu không có log ở bước 2:
   - kiểm tra `OmrScoringJobWorker.handleJobCreated(...)` có được gọi không
   - kiểm tra `@EnableAsync` đã bật chưa
   - kiểm tra bean worker có được Spring scan không
4. Nếu có log ở bước 2 nhưng không có:
   - `Calling scoring service for OMR scoring job: ...`
   thì kiểm tra đoạn load job đầu vào
5. Nếu có log gọi scoring service nhưng job vẫn `PROCESSING` lâu:
   - nghi call gRPC sang SS đang treo hoặc stream chưa đóng
