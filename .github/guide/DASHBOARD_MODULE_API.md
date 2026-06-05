# Dashboard Module API

## 1. Mục đích module

Module `Dashboard` phục vụ xem kết quả, thống kê và xếp hạng theo `examUuid`.

Tất cả API đều hỗ trợ:

- `exportXlsx=false`: trả JSON.
- `exportXlsx=true`: trả file `.xlsx`.

File Excel dùng tiếng Việt và luôn có sheet `Thông tin chung` gồm:

- `Năm học`
- `Tên bài kiểm tra`
- `Ngày mở bài kiểm tra`
- `Ngày đóng bài kiểm tra`
- `Người tạo bài kiểm tra`

---

## 2. API fetch kết quả bài thi

### Đường dẫn

`GET /api/v1/dashboard/exams/{examUuid}/results`

### Input format

- `examUuid`: `UUID`
- `exportXlsx`: `boolean`, mặc định `false`

Ví dụ:

`GET /api/v1/dashboard/exams/{examUuid}/results`

`GET /api/v1/dashboard/exams/{examUuid}/results?exportXlsx=true`

### Mô tả luồng

Nhận `examUuid` -> lấy exam -> lấy các attempt đã có điểm -> đọc snapshot câu hỏi và final answer -> tính tổng điểm và điểm theo từng section `MCQ`, `TFQ`, `SAQ` -> nếu export thì tạo file Excel -> trả kết quả.

### Output JSON

```json
{
  "examUuid": "uuid",
  "schoolYear": "2025-2026",
  "examName": "Đề kiểm tra Toán",
  "startTime": "2026-06-01T01:00:00Z",
  "endTime": "2026-06-01T02:00:00Z",
  "createdBy": "teacher@example.com",
  "students": [
    {
      "studentId": "10013",
      "fullname": "Nguyen Van A",
      "userUuid": "uuid",
      "submitSource": "WEB",
      "paperCode": null,
      "totalScore": 8.5,
      "violationCount": 2,
      "sectionScores": {
        "MCQ": 3.0,
        "TFQ": 2.5,
        "SAQ": 3.0
      }
    }
  ]
}
```

### Output XLSX

Có các sheet:

- `Thông tin chung`
- `Kết quả`
- `Chi tiết đáp án`

Sheet `Kết quả` có các cột:

- `SID`
- `Họ tên`
- `User UUID`
- `Nguồn`
- `Mã đề giấy`
- `Tổng điểm`
- `Số vi phạm`
- `Điểm MCQ`
- `Điểm TFQ`
- `Điểm SAQ`

Sheet `Chi tiết đáp án` có các cột:

- `SID`
- `Họ tên`
- `User UUID`
- `Nguồn`
- `Mã đề giấy`
- `Phần`
- `STT câu`
- `Nội dung câu hỏi`
- `Đáp án học sinh chọn`
- `Đáp án đúng`
- `Điểm câu`

Với học sinh làm trên web, cột `Mã đề giấy` để trống.

---

## 3. API get exam stat

### Đường dẫn

`GET /api/v1/dashboard/exams/{examUuid}/stats`

### Input format

- `examUuid`: `UUID`
- `exportXlsx`: `boolean`, mặc định `false`

### Mô tả luồng

Nhận `examUuid` -> lấy các attempt đã có điểm -> tính điểm từng section cho từng attempt -> tính `averageScore`, `meanScore`, `standardDeviationScore` theo section -> thống kê số lượt chọn từng đáp án theo từng câu hỏi -> nếu export thì tạo file Excel -> trả kết quả.

### Output JSON

```json
{
  "examUuid": "uuid",
  "schoolYear": "2025-2026",
  "examName": "Đề kiểm tra Toán",
  "sections": [
    {
      "sectionType": "MCQ",
      "averageScore": 3.5,
      "meanScore": 3.5,
      "standardDeviationScore": 1.25,
      "questions": [
        {
          "questionOrder": 1,
          "questionUuid": "uuid",
          "questionType": "MCQ",
          "questionContent": "Nội dung câu hỏi | A. ... | B. ...",
          "correctAnswer": "D",
          "answerCounts": {
            "A": 10,
            "B": 20,
            "C": 0,
            "D": 1,
            "M": 0,
            "Bỏ trống": 0
          }
        }
      ]
    }
  ]
}
```

### Output XLSX

Có các sheet:

- `Thông tin chung`
- `Thống kê phần`
- `Thống kê câu hỏi`

API này không có trường riêng theo từng học sinh trong file Excel.

---

## 4. API get student score ranking

### Đường dẫn

`GET /api/v1/dashboard/exams/{examUuid}/rankings`

### Input format

- `examUuid`: `UUID`
- `n`: số học sinh muốn lấy top, mặc định `10`
- `exportXlsx`: `boolean`, mặc định `false`

Ví dụ:

`GET /api/v1/dashboard/exams/{examUuid}/rankings?n=10`

`GET /api/v1/dashboard/exams/{examUuid}/rankings?n=10&exportXlsx=true`

### Mô tả luồng

Nhận `examUuid` và `n` -> lấy các attempt đã có điểm -> nhóm attempt web riêng -> nhóm attempt OMR theo từng `paperCode` -> sắp xếp theo điểm giảm dần -> lấy top `n` kèm các học sinh đồng điểm với người thứ `n` -> nếu export thì tạo file Excel -> trả kết quả.

### Output JSON

```json
{
  "examUuid": "uuid",
  "schoolYear": "2025-2026",
  "examName": "Đề kiểm tra Toán",
  "requestedTopN": 10,
  "paperRankings": [
    {
      "paperCode": "M001",
      "submitSource": "OMR_IMPORT",
      "students": [
        {
          "rank": 1,
          "studentId": "10013",
          "fullname": "Nguyen Van A",
          "userUuid": "uuid",
          "score": 9.0
        }
      ]
    }
  ],
  "webRanking": {
    "paperCode": null,
    "submitSource": "WEB",
    "students": []
  }
}
```

### Output XLSX

- Mỗi mã đề giấy là một sheet riêng.
- Sheet cuối là `Web`.
- Mỗi sheet có các cột:
  - `Hạng`
  - `SID`
  - `Họ tên`
  - `User UUID`
  - `Điểm`

---

## 5. Exception có thể trả về

- `Exam not found with id: {examUuid}`
- `Failed to read attempt question snapshot`
- `Failed to export exam results`
- `Failed to export exam stats`
- `Failed to export exam ranking`
