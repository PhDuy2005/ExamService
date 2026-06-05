# Walkthrough: Thêm violationCount vào Attempt Summary

## Bối cảnh

Admin cần biết học sinh nào có vi phạm khi xem danh sách bài nộp.
`GET /api/v1/student/attempts` hiện không trả về thông tin này.

**Phương án:** Tính `violationCount` bằng COUNT query từ bảng `exam_proctoring_event`
lúc fetch — không thêm cột vào DB, luôn chính xác.

---

## Luồng hoạt động

```
GET /api/v1/student/attempts
        │
        ▼
ExamAttemptService.getAttempts()
        │
        ├─ 1. Lấy danh sách attempts từ DB
        │
        ├─ 2. Batch COUNT query (1 câu duy nhất):
        │      SELECT attemptUuid, COUNT(*)
        │      FROM exam_proctoring_event
        │      WHERE attemptUuid IN (uuid1, uuid2, ...)
        │      GROUP BY attemptUuid
        │      → Map<UUID, Long> violationCountByAttemptUuid
        │
        └─ 3. Build response: gắn violationCount vào từng summary DTO
                (default 0 nếu không có event nào)
```

> Dùng **1 query batch** cho toàn bộ page — tránh N+1.

---

## Các file thay đổi

### 1. `ExamProctoringEventRepository.java`

Thêm batch COUNT query:

```java
@Query("SELECT e.attemptUuid, COUNT(e) FROM ExamProctoringEvent e " +
       "WHERE e.attemptUuid IN :attemptUuids GROUP BY e.attemptUuid")
List<Object[]> countGroupByAttemptUuid(@Param("attemptUuids") List<UUID> attemptUuids);
```

### 2. `ResExamAttemptSummaryDTO.java`

Thêm field:

```java
private Long violationCount;
```

### 3. `ExamAttemptService.java`

Inject thêm repository:

```java
private final ExamProctoringEventRepository examProctoringEventRepository;
```

Trong `getAttempts()`, build violation count map trước khi map sang DTO:

```java
List<UUID> attemptUuids = attempts.stream().map(ExamAttempt::getAttemptUuid).toList();
Map<UUID, Long> violationCountByAttemptUuid = examProctoringEventRepository
        .countGroupByAttemptUuid(attemptUuids)
        .stream()
        .collect(Collectors.toMap(row -> (UUID) row[0], row -> (Long) row[1]));
```

`buildAttemptSummaryResponse()` nhận thêm tham số `Long violationCount`:

```java
private ResExamAttemptSummaryDTO buildAttemptSummaryResponse(
        ExamAttempt attempt, Exam exam, Long violationCount) {
    return ResExamAttemptSummaryDTO.builder()
            ...
            .violationCount(violationCount)
            .build();
}
```

---

## Response sau thay đổi

```json
{
  "content": [
    {
      "attemptUuid": "abc-123",
      "examName": "Kiểm tra giữa kỳ",
      "score": 8.5,
      "status": "SCORED",
      "violationCount": 3
    },
    {
      "attemptUuid": "def-456",
      "examName": "Kiểm tra giữa kỳ",
      "score": 9.0,
      "status": "SCORED",
      "violationCount": 0
    }
  ]
}
```

> `violationCount = 0` — không có vi phạm nào được ghi nhận.

---

## Giới hạn hiện tại

| Giới hạn | Mô tả |
|----------|-------|
| Không có endpoint admin riêng | `GET /api/v1/student/attempts` yêu cầu ownership — admin không xem được attempt của học sinh khác |
| Không có chi tiết vi phạm | Summary chỉ trả về số lượng, không trả về từng event cụ thể |

**Hướng mở rộng tiếp theo (chưa implement):**
- `GET /api/v1/admin/attempts?examUuid=...` — admin xem tất cả attempts của một exam kèm `violationCount`
- `GET /api/v1/admin/attempts/{attemptUuid}/proctoring-events` — admin xem chi tiết từng vi phạm
