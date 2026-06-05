# Kế hoạch implement: Proctoring Events (Ghi nhận vi phạm thi)

## 1. Tổng quan

Hệ thống ghi nhận vi phạm trong khi thi hoạt động theo mô hình **frontend chủ động gửi batch**. Backend chỉ nhận và lưu — không tự detect, không tự gửi thay frontend.

---

## 2. Các loại vi phạm cần detect

| `eventType` | Khi nào detect |
|-------------|---------------|
| `TAB_SWITCH` | `document.addEventListener('visibilitychange')` |
| `FULLSCREEN_EXIT` | `document.addEventListener('fullscreenchange')` |
| `WINDOW_BLUR` | `window.addEventListener('blur')` |
| `COPY_PASTE` | `document.addEventListener('copy')` / `'paste'` |
| `NETWORK_LOST` | `window.addEventListener('offline')` |

---

## 3. Phân chia trách nhiệm

### Frontend xử lý (Backend không làm)

| Việc | Mô tả |
|------|-------|
| Detect sự kiện | Lắng nghe browser events |
| Quản lý queue | Gom events vào local array trước khi gửi |
| Flush khi đủ batch | Gửi khi queue ≥ batch size |
| Flush theo timer | Gửi định kỳ nếu queue không rỗng |
| Flush khi nộp bài | Gửi trước khi gọi submit API |
| Flush khi hết giờ | Gửi khi đồng hồ về 0, trước khi server auto-submit |
| Retry khi mất mạng | Lưu queue vào `localStorage`, retry khi có mạng |

### Backend xử lý

| Việc | Mô tả |
|------|-------|
| Nhận batch | `POST .../proctoring-events/batch` (tối đa 100 events) |
| Validate ownership | Học sinh chỉ ghi được cho attempt của chính mình |
| Lưu DB | Persist vào bảng `exam_proctoring_event` |
| Trả kết quả | `acceptedCount` + danh sách events đã lưu |

---

## 4. API

### 4.1. Gửi vi phạm (batch)

```
POST /api/v1/student/attempts/{attemptUuid}/proctoring-events/batch
Authorization: Bearer <token>
```

**Request:**
```json
{
  "events": [
    {
      "eventType": "TAB_SWITCH",
      "eventTime": "2026-06-02T08:05:12Z",
      "eventPayload": { "visibilityState": "hidden" }
    }
  ]
}
```

> - `eventType`: bắt buộc
> - `eventTime`: tuỳ chọn — bỏ qua thì server tự gán thời gian hiện tại
> - `eventPayload`: tuỳ chọn — JSON tự do để ghi thêm chi tiết
> - Tối đa **100 events/request**

**Response:**
```json
{
  "statusCode": 200,
  "message": "Create proctoring events",
  "data": {
    "attemptUuid": "...",
    "acceptedCount": 1,
    "events": [ { "eventUuid": "...", "eventType": "TAB_SWITCH", ... } ]
  }
}
```

### 4.2. Lấy danh sách vi phạm

```
GET /api/v1/student/attempts/{attemptUuid}/proctoring-events
Authorization: Bearer <token>
```

Trả về toàn bộ events của attempt, **sắp xếp tăng dần theo `eventTime`**.

---

## 5. Logic frontend: Khi nào gửi API

```
Vi phạm xảy ra
      ↓
  push vào eventQueue[]
      ↓
  queue.length >= BATCH_SIZE? ──── CÓ ──→ flush ngay
      ↓ KHÔNG
  (chờ timer hoặc trigger khác)


Timer (mỗi 5-10s):
  queue.length > 0? ──── CÓ ──→ flush

Học sinh bấm nộp bài:
  queue.length > 0? ──── CÓ ──→ flush trước → rồi mới gọi submit API

Đồng hồ đếm ngược về 0:
  queue.length > 0? ──── CÓ ──→ flush ngay
                                 (server sẽ auto-submit trong ≤ 30s)
```

### Pseudo-code tham khảo

```js
const BATCH_SIZE = 10
const FLUSH_INTERVAL_MS = 5000
const eventQueue = []

// Khi phát hiện vi phạm
function onViolationDetected(type, payload) {
  eventQueue.push({ eventType: type, eventTime: new Date().toISOString(), eventPayload: payload })
  if (eventQueue.length >= BATCH_SIZE) flushEvents()
}

// Timer định kỳ
setInterval(() => {
  if (eventQueue.length > 0) flushEvents()
}, FLUSH_INTERVAL_MS)

// Flush
async function flushEvents() {
  const batch = eventQueue.splice(0)
  try {
    await postBatch(batch)
  } catch {
    // Nếu thất bại: đẩy lại vào đầu queue hoặc lưu localStorage
    eventQueue.unshift(...batch)
  }
}

// Khi nộp bài
async function submitExam() {
  if (eventQueue.length > 0) await flushEvents()
  await callSubmitAPI()
}

// Khi đồng hồ về 0
function onTimerExpired() {
  if (eventQueue.length > 0) flushEvents()
  // server tự auto-submit sau ≤ 30s
}
```

---

## 6. Xử lý mất mạng

Backend **không có** cơ chế retry hay offline queue cho proctoring events. Frontend phải tự xử lý.

**Khuyến nghị:**

```
Mất mạng phát hiện (NETWORK_LOST event)
      ↓
  Dừng timer flush
  Lưu eventQueue vào localStorage
  Ghi lại event NETWORK_LOST vào queue

Có mạng lại (online event)
      ↓
  Khôi phục queue từ localStorage
  flush ngay
  Khởi động lại timer
```

> Lưu ý: Nếu mất mạng đến hết giờ và không kịp flush trước khi server auto-submit, các events trong queue sẽ **mất vĩnh viễn**. Dữ liệu vi phạm sau khi attempt chuyển sang `SCORED` không còn giá trị xử lý.

---

## 7. Luồng tổng thể

```
Bắt đầu thi (startAttempt)
      │
      ▼
  Khởi tạo eventQueue = []
  Đăng ký browser event listeners
  Khởi động flush timer
      │
      ▼
  [Trong khi thi]
  Vi phạm → queue → flush khi đủ / theo timer
      │
      ├── Hết giờ ──→ flush queue → server auto-submit (≤30s)
      │
      └── Nộp bài ──→ flush queue → POST submit
```
