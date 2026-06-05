# PDF Module API

## 1. Mục đích module

Module PDF tạo và cung cấp file đề thi tương ứng với từng `ExamPaper`.

- file PDF được tự động sinh khi tạo `ExamPaper`
- mỗi file gắn với một `examUuid` và `paperCode`
- PDF chứa nội dung đề thi nhưng không chứa đáp án đúng
- frontend dùng `pdfUrl` trong response `ExamPaper` để tải hoặc mở file

Hiện tại module PDF không có API tạo PDF hoặc tạo lại PDF riêng. Quá trình render được gọi bên trong API tạo `ExamPaper`.

---

## 2. Quy ước xác thực

Tất cả request liên quan đến tạo, lấy thông tin hoặc tải file PDF đều cần:

```http
Authorization: Bearer <access_token>
```

Đường dẫn `/storage/**` hiện cũng được bảo vệ bởi JWT. Vì vậy frontend không thể mở trực tiếp `pdfUrl` bằng `window.open(pdfUrl)` nếu trình duyệt không gửi Bearer Token.

Frontend được CORS cho phép từ:

- `http://localhost:3000`
- `http://localhost:5173`

---

## 3. Luồng tạo file PDF

### API kích hoạt tạo PDF

`POST /api/v1/omr/exam-papers`

### Input format

```json
{
  "examUuid": "018f4a60-12ab-7a11-a9d1-7c5d5b5b0001",
  "paperCode": "M001"
}
```

### Luồng xử lý

1. Kiểm tra `Exam` tồn tại.
2. Chuẩn hóa `paperCode` bằng cách trim và chuyển thành chữ hoa.
3. Kiểm tra `paperCode` chưa tồn tại trong cùng `Exam`.
4. Tạo `questionSnapshotJson` cố định cho mã đề.
5. Render nội dung snapshot thành file PDF.
6. Lưu file vào storage.
7. Lưu `ExamPaper` cùng `pdfUrl`.
8. Trả thông tin `ExamPaper`.

Nếu quá trình render hoặc lưu file PDF thất bại, transaction tạo `ExamPaper` thất bại và paper không được lưu vào database.

### Output liên quan tới PDF

```json
{
  "statusCode": 200,
  "message": "Create OMR exam paper",
  "data": {
    "paperUuid": "uuid",
    "examUuid": "018f4a60-12ab-7a11-a9d1-7c5d5b5b0001",
    "paperCode": "M001",
    "pdfUrl": "/storage/exam-papers/018f4a60-12ab-7a11-a9d1-7c5d5b5b0001/M001.pdf"
  }
}
```

`pdfUrl` là relative path, không phải full URL.

Ví dụ full URL khi Exam Service chạy tại `http://localhost:8080`:

```text
http://localhost:8080/storage/exam-papers/018f4a60-12ab-7a11-a9d1-7c5d5b5b0001/M001.pdf
```

---

## 4. API lấy file PDF

### Đường dẫn

`GET /storage/exam-papers/{examUuid}/{paperCode}.pdf`

Đây là static-resource endpoint, không phải endpoint controller trả response JSON.

### Request mẫu

```http
GET /storage/exam-papers/018f4a60-12ab-7a11-a9d1-7c5d5b5b0001/M001.pdf HTTP/1.1
Host: localhost:8080
Authorization: Bearer <access_token>
```

### Response thành công

- body: dữ liệu nhị phân của file PDF
- frontend nên đọc response dưới dạng `Blob`

### Ví dụ tải file bằng Fetch API

```javascript
async function downloadExamPaper(baseUrl, examPaper, accessToken) {
  const response = await fetch(`${baseUrl}${examPaper.pdfUrl}`, {
    headers: {
      Authorization: `Bearer ${accessToken}`,
    },
  });

  if (!response.ok) {
    throw new Error(`Cannot download exam paper PDF: ${response.status}`);
  }

  const blob = await response.blob();
  const objectUrl = URL.createObjectURL(blob);
  const link = document.createElement("a");

  link.href = objectUrl;
  link.download = `${examPaper.paperCode}.pdf`;
  link.click();

  URL.revokeObjectURL(objectUrl);
}
```

### Ví dụ mở PDF trong tab mới

```javascript
async function openExamPaper(baseUrl, pdfUrl, accessToken) {
  const response = await fetch(`${baseUrl}${pdfUrl}`, {
    headers: {
      Authorization: `Bearer ${accessToken}`,
    },
  });

  if (!response.ok) {
    throw new Error(`Cannot open exam paper PDF: ${response.status}`);
  }

  const blob = await response.blob();
  const objectUrl = URL.createObjectURL(blob);
  window.open(objectUrl, "_blank", "noopener,noreferrer");

  setTimeout(() => URL.revokeObjectURL(objectUrl), 60_000);
}
```

Không revoke `objectUrl` ngay sau `window.open`, vì tab mới cần thời gian để đọc dữ liệu PDF.

---

## 5. API lấy `pdfUrl` của các mã đề

### Đường dẫn

`GET /api/v1/omr/exams/{examUuid}/exam-papers`

API trả danh sách các `ExamPaper` của exam, bao gồm `paperCode` và `pdfUrl`.

```json
{
  "statusCode": 200,
  "message": "Get OMR exam papers by exam id",
  "data": [
    {
      "paperUuid": "uuid",
      "examUuid": "uuid",
      "paperCode": "M001",
      "pdfUrl": "/storage/exam-papers/exam-uuid/M001.pdf"
    }
  ]
}
```

Nếu `pdfUrl` là `null`, mã đề chưa có file PDF khả dụng.

---

## 6. Nội dung và thứ tự in

PDF in câu hỏi theo ba phần cố định:

1. `MCQ`: `Phần 1 - Thí sinh chọn một trong bốn đáp án`
2. `TFQ`: `Phần 2 - Thí sinh chọn đúng hoặc sai cho mỗi mệnh đề trong câu hỏi`
3. `SAQ`: `Phần 3 - Thí sinh trả lời các câu hỏi sau`

Một phần không có câu hỏi sẽ không được in.

Trong từng phần:

- câu hỏi được sắp xếp theo `questionOrder` trong snapshot
- số câu hiển thị dùng `sectionQuestionNumber`
- `sectionQuestionNumber` bắt đầu lại từ `1` cho từng loại câu hỏi

`questionOrder` vẫn được giữ trong snapshot để backend ánh xạ và chấm điểm, nhưng không phải số câu hiển thị trên PDF sau khi câu hỏi được gom theo phần.

PDF còn chứa:

- tên đề và thông tin đầu đề
- `paperCode`
- điểm của từng câu
- các lựa chọn MCQ
- các mệnh đề TFQ
- vùng trả lời SAQ
- ảnh câu hỏi hợp lệ
- footer trên mỗi trang:
  - bên trái: `Mã đề: {paperCode}`
  - bên phải: `Trang {i}/{totalPage}`

PDF không chứa:

- đáp án đúng
- đáp án của học sinh
- kết quả chấm điểm

---

## 7. Quy ước LaTeX

Nội dung câu hỏi, lựa chọn MCQ và mệnh đề TFQ hỗ trợ công thức LaTeX nằm giữa:

```text
$...$
```

hoặc:

```text
$$...$$
```

Ví dụ:

```text
Cho biết công thức $$\frac{A}{B} = 10$$, trong đó $$B = 5$$.
```

Khi render:

- phần chữ thông thường được in bằng font PDF đã cấu hình
- phần LaTeX được render thành công thức và đặt xen kẽ với chữ
- nội dung tự động xuống dòng theo chiều rộng trang
- nếu công thức LaTeX không hợp lệ, hệ thống in nguyên chuỗi có delimiter thay vì làm thất bại toàn bộ quá trình tạo PDF

---

## 8. Quy ước ảnh câu hỏi

PDF chỉ render ảnh khi `Question.imagePath`:

- có giá trị
- bắt đầu bằng `/storage/`
- trỏ tới file thực sự tồn tại trong storage của Exam Service

Ví dụ:

```text
/storage/questions/example.png
```

Nếu ảnh không tồn tại hoặc `imagePath` không hợp lệ, PDF vẫn được tạo nhưng bỏ qua ảnh đó.

---

## 9. Cấu hình

### Thư mục storage

Property:

```properties
examservice.storage.root-path=D:/DoAn/DoAn1_storage
```

File PDF được lưu vật lý theo cấu trúc:

```text
{storage-root}/exam-papers/{examUuid}/{paperCode}.pdf
```

URL được lưu trong database:

```text
/storage/exam-papers/{examUuid}/{paperCode}.pdf
```

### Font Unicode

Property:

```properties
examservice.exam-paper.font-path=C:/Windows/Fonts/arial.ttf
```

Khi deploy Linux hoặc container, cần cấu hình đường dẫn tới file font Unicode `.ttf`, ví dụ:

- Noto Sans
- DejaVu Sans

Nếu file font không tồn tại, quá trình tạo PDF thất bại.

---

## 10. Lỗi có thể gặp

### Khi tạo ExamPaper và PDF

- `Exam not found with id: {examUuid}`
- `Exam paper already exists with code: {paperCode}`
- `Exam paper must contain at least one question`
- `Exam paper font file not found: {fontPath}`
- `Failed to read exam paper snapshot for PDF generation`
- `Failed to generate exam paper PDF`
- `Failed to store exam paper PDF`

### Khi tải file

- `401 Unauthorized`: request không có JWT hoặc token không hợp lệ
- `404 Not Found`: `pdfUrl` không tồn tại trong storage
- lỗi CORS: frontend không chạy từ origin được cấu hình cho phép

---

## 11. Giới hạn hiện tại

- chưa có API tạo lại PDF cho một `ExamPaper` đã tồn tại
- chưa có API xóa riêng file PDF
- thay đổi nội dung `Question` sau khi tạo `ExamPaper` không tự động tạo lại PDF
- `pdfUrl` chỉ là relative path; frontend hoặc API Gateway cần ghép thêm base URL

