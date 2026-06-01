---
title: Exam Service (ES) - Database Schema Specification

---

# Exam Service (ES) - Database Schema Specification

Tài liệu này mô tả schema cơ sở dữ liệu cho **Exam Service (ES)** theo phiên bản hiện tại đã chốt ở mức đồ án.

Phạm vi chính của ES gồm:
- Quản lí ngân hàng câu hỏi
- Tạo và quản lí đề kiểm tra / bài tập
- Giao đề cho học sinh
- Ghi nhận quá trình làm bài
- Lưu đáp án học sinh
- Chấm điểm theo đáp án cuối cùng
- Ghi nhận một số sự kiện giám sát kiểm tra cơ bản

---

# 1. Quy ước chung

## 1.1. Quy ước đặt tên
- Tên bảng viết hoa theo dạng `SNAKE_CASE`
- Khóa chính dùng hậu tố `_uuid`
- Các trường thời gian dùng hậu tố `_at`, `_time`
- Các trường trạng thái/loại dùng enum ở mức ứng dụng hoặc DB

## 1.2. Quy ước dữ liệu đáp án
Hệ thống lưu song song 2 dạng đáp án:

- `raw_answer`: đáp án ở dạng người dùng nhìn thấy / gửi lên từ giao diện hoặc từ luồng import
- `normalized_answer`: đáp án đã được chuẩn hóa để chấm tự động

### Gợi ý chuẩn hóa
- **MCQ**: chuỗi các option key đã sắp xếp tăng dần, ví dụ: `A`, `AC`, `ABCD`
- **TFQ**:
  - đối với `QUESTION_ANSWER_KEY.normalized_answer`: chuỗi 4 ký tự dùng `D`, `S`, `N`, ví dụ: `DSDN`
  - đối với `STUDENT_ANSWER.normalized_answer`: chuỗi 4 ký tự dùng `D`, `S`, `B`
- **SAQ**: chuỗi số/chuỗi đã chuẩn hóa theo rule của hệ thống

### Quy ước TFQ
- `D`: nhận định đúng
- `S`: nhận định sai
- `N`: nhận định không có tính đúng/sai rõ ràng trong đáp án chuẩn
- `B`: học sinh bỏ trống nhận định đó

### Quy ước MCQ cho luồng OMR
- `M`: học sinh tô nhiều hơn 1 lựa chọn cho cùng một câu `MCQ`
- `M` chỉ dùng cho `STUDENT_ANSWER.normalized_answer` trong ngữ cảnh `OMR_IMPORT`
- nếu `normalized_answer = M` thì câu `MCQ` được tính là sai

### Rule chấm TFQ
- nếu đáp án chuẩn là `D`, học sinh phải trả lời `D` mới được tính đúng ý đó
- nếu đáp án chuẩn là `S`, học sinh phải trả lời `S` mới được tính đúng ý đó
- nếu đáp án chuẩn là `N`, học sinh chỉ cần có trả lời, tức là ký tự khác `B`, thì được tính đúng ý đó

> Ghi chú: Rule chuẩn hóa chi tiết cho `SAQ` chưa được định nghĩa đầy đủ, bạn cần tự chốt thêm.

### Quy ước SAQ
- một đáp án `SAQ` là một chuỗi có độ dài từ `1` đến `4` ký tự
- các ký tự hợp lệ chỉ nằm trong tập: `0 1 2 3 4 5 6 7 8 9`, dấu `-`, dấu `,`
- dấu `-` chỉ được phép xuất hiện ở vị trí thứ `1`
- dấu `,` chỉ được phép xuất hiện ở vị trí thứ `2` hoặc `3`
- nếu có nhiều hơn `1` đáp án đúng, phân cách các đáp án đúng bằng dấu `;`

### Quy ước chuẩn hóa SAQ cho chấm điểm
- hệ thống xem đáp án `SAQ` theo mô hình `4 cột` cố định
- với đáp án chuẩn, nếu chuỗi ngắn hơn `4` ký tự thì thêm ký tự `_` vào bên phải cho đủ `4` ký tự
- với đáp án học sinh:
  - không loại bỏ dấu cách ở đầu hoặc cuối
  - thay mọi dấu cách bằng ký tự `_`
  - nếu chuỗi ngắn hơn `4` ký tự thì thêm `_` vào bên phải cho đủ `4` ký tự
- vì vậy:
  - đáp án chuẩn `12` sẽ được chuẩn hóa thành `12__`
  - nếu học sinh chọn `12` thì được hiểu là cột 1 = `1`, cột 2 = `2`, cột 3 và 4 để trống, nên chuẩn hóa thành `12__`
  - nếu học sinh tô thêm ở cột 3 hoặc cột 4 thì đáp án chuẩn hóa sẽ khác `12__` và bị tính sai
- việc thừa dấu cách ở phía sau đáp án học sinh không làm học sinh bị mất điểm, vì các dấu cách đó sẽ được thay bằng `_`

### Ví dụ SAQ hợp lệ
- `7`
- `37`
- `0,37`
- `14,7`
- `-1,2`
- `1825`

## 1.3. Ghi chú về snapshot
Thiết kế hiện tại **không snapshot đầy đủ nội dung câu hỏi/đáp án tại thời điểm thi**.
Hệ thống chỉ lưu lịch sử trả lời của học sinh thông qua bảng `STUDENT_ANSWER`.

Điều này có nghĩa là nếu nội dung câu hỏi hoặc đáp án đúng trong ngân hàng bị sửa sau khi học sinh làm bài, dữ liệu xem lại/chấm lại có thể bị ảnh hưởng.

> Đây là trade-off kỹ thuật đã được chấp nhận trong phạm vi đồ án.

---

# 2. Danh sách bảng

1. `QUESTION`
2. `QUESTION_MC_OPTION`
3. `QUESTION_TRUE_FALSE_STATEMENT`
4. `QUESTION_ANSWER_KEY`
5. `EXAM`
6. `EXAM_QUESTION`
7. `EXAM_QUESTION_GROUP`
8. `EXAM_QUESTION_GROUP_ITEM`
9. `EXAM_ASSIGNMENT`
10. `EXAM_ATTEMPT`
11. `STUDENT_ANSWER`
12. `EXAM_PROCTORING_EVENT`

---

# 3. Đặc tả chi tiết từng bảng

---

## 3.1. `QUESTION`

Lưu thông tin câu hỏi trong ngân hàng câu hỏi.

### Mục đích
- Là thực thể gốc đại diện cho một câu hỏi
- Mỗi câu hỏi thuộc một khối/lớp (`grade_id`)
- Mỗi câu hỏi có đúng một loại: trắc nghiệm nhiều lựa chọn, đúng/sai, hoặc trả lời ngắn

### Thuộc tính

| Tên thuộc tính         | Kiểu gợi ý           | Bắt buộc | Mô tả                                       |
| ---------------------- | -------------------- | -------: | ------------------------------------------- |
| `question_uuid`        | UUID                 |       Có | Khóa chính của câu hỏi                      |
| `grade_id`             | BIGINT / LONG        |       Có | Mã khối/lớp mà câu hỏi thuộc về             |
| `question_content`     | TEXT                 |       Có | Nội dung stem / đề bài chung của câu hỏi    |
| `question_topic`       | VARCHAR              |    Không | Chủ đề câu hỏi                              |
| `question_type`        | VARCHAR(10)          |       Có | Loại câu hỏi                                |
| `created_by_user_uuid` | UUID                 |       Có | Người tạo câu hỏi                           |
| `created_at`           | DATETIME / TIMESTAMP |       Có | Thời điểm tạo                               |
| `updated_at`           | DATETIME / TIMESTAMP |       Có | Thời điểm cập nhật gần nhất                 |
| `is_active`            | BOOLEAN              |       Có | Đánh dấu câu hỏi còn được sử dụng hay không |

### Enum đề xuất
`question_type`:
- `MCQ`: Multiple Choice Question
- `TFQ`: True/False Question
- `SAQ`: Short Answer Question

### Ghi chú
- `question_content` là phần nội dung chung của câu hỏi
- Với `MCQ`, phần lựa chọn được lưu ở `QUESTION_MC_OPTION`
- Với `TFQ`, các nhận định được lưu ở `QUESTION_TRUE_FALSE_STATEMENT`
- Với `SAQ`, không cần bảng con cho lựa chọn

---

## 3.2. `QUESTION_MC_OPTION`

Lưu các lựa chọn cho câu hỏi loại `MCQ`.

### Thuộc tính

| Tên thuộc tính   | Kiểu gợi ý | Bắt buộc | Mô tả                                   |
| ---------------- | ---------- | -------: | --------------------------------------- |
| `option_uuid`    | UUID       |       Có | Khóa chính của lựa chọn                 |
| `question_uuid`  | UUID       |       Có | Tham chiếu đến `QUESTION.question_uuid` |
| `option_key`     | CHAR(1)    |       Có | Ký hiệu lựa chọn                        |
| `option_content` | TEXT       |       Có | Nội dung lựa chọn                       |

### Ràng buộc
- `unique(question_uuid, option_key)`

### Enum/giá trị đề xuất
`option_key`:
- `A`
- `B`
- `C`
- `D`

### Ghi chú
- Thiết kế hiện tại giả định một câu `MCQ` có 4 lựa chọn A/B/C/D
- Nếu sau này muốn hỗ trợ nhiều hơn 4 lựa chọn, cần nới ràng buộc nghiệp vụ

---

## 3.3. `QUESTION_TRUE_FALSE_STATEMENT`

Lưu các nhận định của câu hỏi loại đúng/sai.

### Thuộc tính

| Tên thuộc tính      | Kiểu gợi ý | Bắt buộc | Mô tả                                   |
| ------------------- | ---------- | -------: | --------------------------------------- |
| `statement_uuid`    | UUID       |       Có | Khóa chính của nhận định                |
| `question_uuid`     | UUID       |       Có | Tham chiếu đến `QUESTION.question_uuid` |
| `statement_order`   | INT        |       Có | Thứ tự nhận định                        |
| `statement_content` | TEXT       |       Có | Nội dung nhận định                      |

### Ràng buộc
- `unique(question_uuid, statement_order)`

### Ghi chú
- Theo đặc tả hiện tại, một câu `TFQ` có **4 nhận định**
- Nên có validation nghiệp vụ để đảm bảo `statement_order` thuộc tập `{1,2,3,4}`

> Nếu bạn muốn enforce ở DB, cần tự bổ sung check constraint tùy hệ quản trị CSDL.

---

## 3.4. `QUESTION_ANSWER_KEY`

Lưu đáp án đúng của câu hỏi.

### Mục đích
- Mỗi câu hỏi có một đáp án đúng hiện hành dùng để chấm điểm
- Thiết kế hiện tại chưa thể hiện rõ versioning của đáp án

### Thuộc tính

| Tên thuộc tính       | Kiểu gợi ý           | Bắt buộc | Mô tả                                   |
| -------------------- | -------------------- | -------: | --------------------------------------- |
| `answer_key_uuid`    | UUID                 |       Có | Khóa chính của bản ghi đáp án           |
| `question_uuid`      | UUID                 |       Có | Tham chiếu đến `QUESTION.question_uuid` |
| `correct_answer_raw` | TEXT / VARCHAR       |       Có | Đáp án đúng ở dạng người dùng nhập      |
| `normalized_answer`  | VARCHAR / TEXT       |       Có | Đáp án đúng đã chuẩn hóa để chấm        |
| `created_at`         | DATETIME / TIMESTAMP |       Có | Thời điểm tạo đáp án                    |

### Ghi chú
- Hiện tại chưa có các trường như `updated_at`, `version_no`, `is_active`
- Nếu bạn muốn hỗ trợ lịch sử thay đổi đáp án, cần tự bổ sung
- Với câu hỏi MCQ, TFQ, chỉ chấp nhận 1 đáp án duy nhất
- Với câu hỏi SAQ, nếu có nhiều hơn 1 đáp án, liệt kê tất cả các đáp án, phân cách bằng kí tự `;`. Vì vậy, mỗi câu hỏi chỉ có 1 record đáp án duy nhất


### Khuyến nghị tối thiểu
- Nếu không cần versioning, nên đảm bảo ở tầng ứng dụng mỗi `question_uuid` chỉ có 1 đáp án hiện hành

---

## 3.5. `EXAM`

Lưu thông tin đề kiểm tra / bài tập.

### Thuộc tính

| Tên thuộc tính         | Kiểu gợi ý           | Bắt buộc | Mô tả                                  |
| ---------------------- | -------------------- | -------: | -------------------------------------- |
| `exam_uuid`            | UUID                 |       Có | Khóa chính của đề                      |
| `exam_name`            | VARCHAR              |       Có | Tên đề kiểm tra / bài tập              |
| `grade_id`             | BIGINT / LONG        |       Có | Khối/lớp mà đề áp dụng                 |
| `school_year`          | VARCHAR(20)          |       Có | Năm học của đề, dùng để resolve học sinh từ `student_code` khi import OMR |
| `exam_type`            | VARCHAR(20)          |       Có | Loại đề                                |
| `start_time`           | DATETIME / TIMESTAMP |    Không | Thời điểm bắt đầu cho phép làm bài     |
| `end_time`             | DATETIME / TIMESTAMP |    Không | Thời điểm kết thúc cho phép làm bài    |
| `duration_minutes`     | INT                  |       Có | Thời lượng làm bài (phút)              |
| `total_score`          | DECIMAL / NUMERIC    |       Có | Tổng điểm của đề                       |
| `number_of_attemp`     | INT                  |       Có | Số lần làm bài tối đa của một học sinh |
| `status`               | VARCHAR              |       Có | Trạng thái của đề                      |
| `created_by_user_uuid` | UUID                 |       Có | Người tạo đề                           |
| `created_at`           | DATETIME / TIMESTAMP |       Có | Thời điểm tạo                          |
| `updated_at`           | DATETIME / TIMESTAMP |       Có | Thời điểm cập nhật gần nhất            |
| `tf_correct_1_pct`     | DECIMAL / INT        |       Có | Tỉ lệ điểm khi đúng 1 nhận định TFQ    |
| `tf_correct_2_pct`     | DECIMAL / INT        |       Có | Tỉ lệ điểm khi đúng 2 nhận định TFQ    |
| `tf_correct_3_pct`     | DECIMAL / INT        |       Có | Tỉ lệ điểm khi đúng 3 nhận định TFQ    |
| `tf_correct_4_pct`     | DECIMAL / INT        |       Có | Tỉ lệ điểm khi đúng 4 nhận định TFQ    |

### Enum đề xuất
`exam_type`:
- `QUIZ`
- `HOMEWORK`
- `MOCK_TEST`
- `OFFICIAL_TEST`

`status`:
- `DRAFT`
- `PUBLISHED`
- `CLOSED`
- `ARCHIVED`

### Ghi chú
- Bộ 4 trường `tf_correct_i_pct` dùng cho chấm câu đúng/sai theo số nhận định đúng
- Giá trị mặc định theo đặc tả hiện tại là: `10`, `25`, `50`, `100`
- `start_time`, `end_time` có thể để null nếu muốn đề không khóa theo lịch
- `duration_minutes = 0` trong trường hợp không giới hạn thời gian; các trường hợp còn lại ràng buộc `duration_minutes > 0`
- Nếu `number_of_attemp = 0`, học sinh được làm vô số lần

---

## 3.6. `EXAM_QUESTION`

Lưu danh sách câu hỏi thuộc một đề.

### Mục đích
- Xác định câu hỏi nào nằm trong đề nào
- Xác định thứ tự hiển thị và điểm của từng câu
- Phản ánh cấu trúc đề đã được tạo ra

### Thuộc tính

| Tên thuộc tính       | Kiểu gợi ý        | Bắt buộc | Mô tả                                   |
| -------------------- | ----------------- | -------: | --------------------------------------- |
| `exam_question_uuid` | UUID              |       Có | Khóa chính của dòng câu hỏi trong đề    |
| `exam_uuid`          | UUID              |       Có | Tham chiếu đến `EXAM.exam_uuid`         |
| `question_uuid`      | UUID              |       Có | Tham chiếu đến `QUESTION.question_uuid` |
| `question_order`     | INT               |       Có | Thứ tự câu hỏi trong đề                 |
| `score`              | DECIMAL / NUMERIC |       Có | Điểm của câu hỏi trong đề               |
| `section_type`       | VARCHAR(10)       |       Có | Phần/loại câu hỏi trong đề              |
| `source_type`        | VARCHAR(20)       |       Có | Nguồn gốc câu hỏi khi thêm vào đề       |

### Ràng buộc
- `unique(exam_uuid, question_order)`

### Enum đề xuất
`section_type`:
- `MCQ`
- `TFQ`
- `SAQ`

`source_type`:
- `MANUAL`
- `QUESTION_BANK`
- `IMPORTED`

### Ghi chú
- `section_type` nên nhất quán với `QUESTION.question_type`
- Thiết kế hiện tại không lưu snapshot nội dung câu hỏi ở mức `EXAM_QUESTION`

---

## 3.7. `EXAM_QUESTION_GROUP`

Lưu nhóm câu hỏi tĩnh của đề.

### Mục đích
- Hỗ trợ gom câu hỏi theo nhóm phục vụ tạo đề
- Thiết kế hiện tại chấp nhận **nhóm tĩnh**, không sinh động theo rule runtime

### Thuộc tính

| Tên thuộc tính   | Kiểu gợi ý  | Bắt buộc | Mô tả                                                       |
| ---------------- | ----------- | -------: | ----------------------------------------------------------- |
| `eqg_uuid`       | UUID        |       Có | Khóa chính của nhóm câu hỏi                                 |
| `exam_uuid`      | UUID        |       Có | Tham chiếu đến `EXAM.exam_uuid`                             |
| `group_name`     | VARCHAR     |       Có | Tên nhóm câu hỏi                                            |
| `question_type`  | VARCHAR(10) |       Có | Loại câu hỏi của nhóm                                       |
| `question_topic` | VARCHAR     |    Không | Chủ đề của nhóm                                             |
| `question_count` | INT         |       Có | Số lượng câu hỏi trong nhóm được sử dụng trong bài kiểm tra |
| `display_order`  | INT         |       Có | Thứ tự hiển thị nhóm                                        |

### Enum đề xuất
`question_type`:
- `MCQ`
- `TFQ`
- `SAQ`

### Ghi chú
- `question_count` có thể là số câu mong muốn hoặc số câu thực tế của nhóm, bạn cần chốt cách hiểu
- Vì đây là **nhóm tĩnh**, nhóm sẽ đi kèm danh sách phần tử thật ở bảng `EXAM_QUESTION_GROUP_ITEM`

> **Chưa định nghĩa rõ:**

> - Có cho phép `question_topic` null hoàn toàn hay không

---

## 3.8. `EXAM_QUESTION_GROUP_ITEM`

Lưu từng câu hỏi thuộc một nhóm câu hỏi tĩnh.

### Thuộc tính

| Tên thuộc tính  | Kiểu gợi ý | Bắt buộc | Mô tả                                         |
| --------------- | ---------- | -------: | --------------------------------------------- |
| `eqgi_uuid`     | UUID       |       Có | Khóa chính của phần tử nhóm                   |
| `eqg_uuid`      | UUID       |       Có | Tham chiếu đến `EXAM_QUESTION_GROUP.eqg_uuid` |
| `question_uuid` | UUID       |       Có | Tham chiếu đến `QUESTION.question_uuid`       |

### Ghi chú
- Đây là bảng liên kết giữa nhóm câu hỏi và câu hỏi
- Nên cân nhắc thêm unique `(eqg_uuid, question_uuid)` để tránh một câu bị thêm lặp trong cùng một nhóm

> Ràng buộc unique này chưa có trong bản schema gốc, bạn nên tự quyết định có thêm hay không.

---

## 3.9. `EXAM_ASSIGNMENT`

Lưu thông tin giao đề.

### Mục đích
- Gắn đề thi với đối tượng được giao làm bài
- Thiết kế hiện tại giao theo `grade_id`

### Thuộc tính

| Tên thuộc tính          | Kiểu gợi ý           | Bắt buộc | Mô tả                           |
| ----------------------- | -------------------- | -------: | ------------------------------- |
| `assignment_uuid`       | UUID                 |       Có | Khóa chính của đợt giao đề      |
| `exam_uuid`             | UUID                 |       Có | Tham chiếu đến `EXAM.exam_uuid` |
| `grade_id`              | BIGINT / LONG        |       Có | Khối/lớp được giao đề           |
| `assigned_at`           | DATETIME / TIMESTAMP |       Có | Thời điểm giao đề               |
| `assigned_by_user_uuid` | UUID                 |       Có | Người thực hiện giao đề         |

### Ghi chú
- Thiết kế hiện tại chưa hỗ trợ giao đề theo danh sách học sinh hoặc theo lớp học cụ thể ngoài `grade_id`

> **Chưa định nghĩa rõ:**
> - Một đề có được giao nhiều lần cho cùng một `grade_id` hay không
> - Có cần unique `(exam_uuid, grade_id)` hay không

---

## 3.10. `EXAM_ATTEMPT`

Lưu thông tin một lần làm bài của học sinh.

### Mục đích
- Mỗi bản ghi tương ứng với một lần học sinh bắt đầu làm một đề
- Hỗ trợ nhiều lần làm bài cho cùng một học sinh trên cùng một đề

### Thuộc tính

| Tên thuộc tính       | Kiểu gợi ý           | Bắt buộc | Mô tả                                         |
| -------------------- | -------------------- | -------: | --------------------------------------------- |
| `attempt_uuid`       | UUID                 |       Có | Khóa chính của lần làm bài                    |
| `exam_uuid`          | UUID                 |       Có | Tham chiếu đến `EXAM.exam_uuid`               |
| `student_uuid`       | UUID                 |       Có | Học sinh thực hiện lần làm bài                |
| `attempt_no`         | INT                  |       Có | Số thứ tự lần làm bài của học sinh đối với đề |
| `started_at`         | DATETIME / TIMESTAMP |    Không | Thời điểm bắt đầu làm bài                     |
| `submitted_at`       | DATETIME / TIMESTAMP |    Không | Thời điểm nộp bài                             |
| `time_spent_seconds` | INT                  |    Không | Tổng thời gian làm bài (giây)                 |
| `status`             | VARCHAR              |       Có | Trạng thái lần làm bài                        |
| `score`              | DECIMAL / NUMERIC    |    Không | Điểm đạt được                                 |
| `is_auto_submitted`  | BOOLEAN              |       Có | Có phải hệ thống tự nộp bài hay không         |
| `submit_source`      | VARCHAR(20)          |       Có | Nguồn nộp bài                                 |
| `created_at`         | DATETIME / TIMESTAMP |       Có | Thời điểm tạo attempt                         |

### Ràng buộc
- `unique(exam_uuid, student_uuid, attempt_no)`

### Enum đề xuất
`status`:
- `IN_PROGRESS`
- `SUBMITTED`
- `SCORED`
- `ANSWER_RELEASED`
- `CANCELLED`

`submit_source`:
- `WEB`
- `OMR_IMPORT`

### Ghi chú
- `time_spent_seconds` có thể được tính tại thời điểm submit hoặc cập nhật dần trong quá trình làm bài
- `score` có thể null trước khi chấm xong

> **Chưa định nghĩa rõ:**
> - Có cho phép tạo attempt mà `started_at` còn null hay không
> - Khi `status = SUBMITTED` thì `submitted_at` có bắt buộc khác null hay không
> - Khi `status = SCORED` thì `score` có bắt buộc khác null hay không

Bạn nên tự chốt các rule này để service xử lý nhất quán.

---

## 3.11. `STUDENT_ANSWER`

Lưu lịch sử các lần trả lời của học sinh cho từng câu trong một lần làm bài.

### Mục đích
- Mỗi lần học sinh chọn/đổi đáp án sẽ tạo một bản ghi mới
- Hệ thống chấm theo **đáp án cuối cùng**
- Bảng này đồng thời đóng vai trò bảng log thay đổi đáp án

### Thuộc tính

| Tên thuộc tính            | Kiểu gợi ý           | Bắt buộc | Mô tả                                           |
| ------------------------- | -------------------- | -------: | ----------------------------------------------- |
| `student_answer_uuid`     | UUID                 |       Có | Khóa chính của bản ghi đáp án                   |
| `attempt_uuid`            | UUID                 |       Có | Tham chiếu đến `EXAM_ATTEMPT.attempt_uuid`      |
| `question_uuid`           | UUID                 |       Có | Tham chiếu đến `QUESTION.question_uuid`         |
| `raw_answer`              | TEXT / VARCHAR       |    Không | Đáp án học sinh nhìn thấy/gửi lên               |
| `normalized_answer`       | TEXT / VARCHAR       |    Không | Đáp án đã chuẩn hóa để chấm                     |
| `answered_at`             | DATETIME / TIMESTAMP |       Có | Thời điểm ghi nhận đáp án                       |
| `question_attempt_number` | INT                  |       Có | Số thứ tự lần trả lời của câu đó trong attempt  |
| `is_final_answer`         | BOOLEAN              |       Có | Có phải đáp án cuối cùng dùng để chấm hay không |

### Ràng buộc
- `unique(attempt_uuid, question_uuid, question_attempt_number)`

### Ghi chú quan trọng
- Với cùng một cặp `(attempt_uuid, question_uuid)`, có thể tồn tại nhiều dòng dữ liệu
- Hệ thống chấm theo bản ghi được xem là đáp án cuối cùng

### Rule nghiệp vụ khuyến nghị
1. `question_attempt_number` nên bắt đầu từ `1`
2. Mỗi lần học sinh đổi đáp án thì tăng `question_attempt_number` thêm `1`
3. Trong mỗi cặp `(attempt_uuid, question_uuid)`, chỉ nên có **tối đa 1 dòng** với `is_final_answer = true`
4. Nếu chưa có dòng final, có thể fallback lấy dòng có `question_attempt_number` lớn nhất
5. Với `TFQ`, khuyến nghị frontend luôn gửi đủ 4 ký tự và dùng `B` để biểu diễn nhận định học sinh bỏ trống

> **Chưa định nghĩa rõ:**
> - `raw_answer` có được phép null khi học sinh xóa trắng câu trả lời hay không
> - `normalized_answer` có được phép null khi đáp án không parse được hay không
> - Khi submit bài, hệ thống có insert thêm 1 bản ghi final mới hay chỉ update bản ghi mới nhất thành final
>
> Bạn nên tự chốt để thống nhất logic service.

### Rủi ro đã chấp nhận
- Do không dùng `attempt_question_uuid`, việc join để lấy đúng thứ tự câu trong đề sẽ phải thông qua `EXAM_ATTEMPT -> EXAM -> EXAM_QUESTION`
- Nếu một câu hỏi bị chỉnh sửa trong ngân hàng sau khi học sinh làm bài, bản ghi `STUDENT_ANSWER` không tự bảo toàn ngữ cảnh cũ của câu hỏi

---

## 3.12. `EXAM_PROCTORING_EVENT`

Lưu các sự kiện giám sát trong quá trình làm bài.

### Mục đích
- Theo dõi một số hành vi có thể liên quan đến gian lận hoặc gián đoạn trong quá trình làm bài
- Hỗ trợ review attempt sau khi nộp

### Thuộc tính

| Tên thuộc tính  | Kiểu gợi ý           | Bắt buộc | Mô tả                                      |
| --------------- | -------------------- | -------: | ------------------------------------------ |
| `event_uuid`    | UUID                 |       Có | Khóa chính của sự kiện                     |
| `attempt_uuid`  | UUID                 |       Có | Tham chiếu đến `EXAM_ATTEMPT.attempt_uuid` |
| `event_time`    | DATETIME / TIMESTAMP |       Có | Thời điểm xảy ra sự kiện                   |
| `event_type`    | VARCHAR(30)          |       Có | Loại sự kiện                               |
| `event_payload` | JSON                 |    Không | Dữ liệu bổ sung của sự kiện                |

### Enum đề xuất
`event_type`:
- `TAB_SWITCH`
- `FULLSCREEN_EXIT`
- `WINDOW_BLUR`
- `COPY_PASTE`
- `NETWORK_LOST`

### Ghi chú
- `event_payload` có thể lưu dữ liệu như số lần vi phạm, thông tin trình duyệt, trạng thái mạng, v.v.

> **Chưa định nghĩa rõ:**
> - Có cần giới hạn schema của `event_payload` theo từng `event_type` hay không
> - Có cần lưu thêm `severity` hoặc `detected_by` hay không

---

# 4. Tổng hợp enum

## 4.1. `QUESTION.question_type`
- `MCQ`
- `TFQ`
- `SAQ`

## 4.2. `EXAM.exam_type`
- `QUIZ`
- `HOMEWORK`
- `MOCK_TEST`
- `OFFICIAL_TEST`

## 4.3. `EXAM.status`
- `DRAFT`
- `PUBLISHED`
- `CLOSED`
- `ARCHIVED`

## 4.4. `EXAM_QUESTION.section_type`
- `MCQ`
- `TFQ`
- `SAQ`

## 4.5. `EXAM_QUESTION.source_type`
- `MANUAL`
- `QUESTION_BANK`
- `IMPORTED`

## 4.6. `EXAM_ATTEMPT.status`
- `IN_PROGRESS`
- `SUBMITTED`
- `SCORED`
- `ANSWER_RELEASED`
- `CANCELLED`

## 4.7. `EXAM_ATTEMPT.submit_source`
- `WEB`
- `OMR_IMPORT`

## 4.8. `EXAM_PROCTORING_EVENT.event_type`
- `TAB_SWITCH`
- `FULLSCREEN_EXIT`
- `WINDOW_BLUR`
- `COPY_PASTE`
- `NETWORK_LOST`

---

# 5. Các chỗ bạn cần tự định nghĩa thêm

Dưới đây là các điểm còn mở mà schema hiện tại chưa chốt hoàn toàn:

## 5.1. Đáp án đúng hiện hành của câu hỏi
Bạn cần tự định nghĩa rõ:
- mỗi `question_uuid` có bao nhiêu dòng trong `QUESTION_ANSWER_KEY`
- nếu có nhiều dòng thì dòng nào là dòng hiện hành
- có cần versioning đáp án hay không

## 5.2. Rule chuẩn hóa `SAQ`
Bạn cần tự chốt:
- chuẩn hóa dấu `,` / `.`
- chuẩn hóa số âm
- có loại bỏ khoảng trắng hay không
- có làm tròn số hay không
- có chấp nhận nhiều biểu diễn tương đương hay không

## 5.3. Rule final answer trong `STUDENT_ANSWER`
Bạn cần tự chốt:
- submit bài có tạo thêm bản ghi mới hay không
- hay chỉ cập nhật dòng cuối cùng thành `is_final_answer = true`
- nếu học sinh bỏ trống câu trả lời thì lưu như thế nào

## 5.4. Rule giao đề
Bạn cần tự chốt:
- một đề có thể giao nhiều lần cho cùng một `grade_id` hay không
- có cần chặn trùng assignment hay không

## 5.5. Rule trạng thái
Bạn cần tự chốt rõ quan hệ giữa trạng thái và dữ liệu:
- `SUBMITTED` có bắt buộc `submitted_at` khác null không
- `SCORED` có bắt buộc `score` khác null không
- `CANCELLED` có bắt buộc lý do hủy không

## 5.6. Rule nhóm câu hỏi tĩnh
Bạn cần tự chốt:
- `question_count` là số lượng yêu cầu hay số lượng thực tế
- có cần unique `(eqg_uuid, question_uuid)` hay không

---

# 6. Khuyến nghị triển khai

## 6.1. Nên có validation ở tầng service
Vì schema hiện tại chấp nhận một số trade-off, service nên validate thêm:
- tính hợp lệ của `normalized_answer`
- chỉ 1 final answer cho mỗi câu trong 1 attempt
- không cho thêm câu khác loại vào nhóm sai loại
- `EXAM_QUESTION.section_type` phải khớp `QUESTION.question_type`

## 6.2. Nên bổ sung index
Gợi ý index:
- `QUESTION(grade_id, question_type, question_topic)`
- `EXAM(grade_id, status, start_time, end_time)`
- `EXAM_QUESTION(exam_uuid, question_order)`
- `EXAM_ATTEMPT(exam_uuid, student_uuid)`
- `STUDENT_ANSWER(attempt_uuid, question_uuid, is_final_answer)`
- `EXAM_PROCTORING_EVENT(attempt_uuid, event_time)`

## 6.3. Nên mô tả rule chấm điểm bằng tài liệu riêng
Đặc biệt với:
- MCQ nhiều đáp án đúng
- TFQ theo phần trăm số nhận định đúng
- SAQ chuẩn hóa chuỗi/số

Việc này giúp tránh mơ hồ khi code scoring logic.

---

# 7. Kết luận

Schema hiện tại phù hợp với phạm vi đồ án và các trade-off kỹ thuật đã chấp nhận:
- không snapshot đầy đủ câu hỏi ở thời điểm thi
- lưu lịch sử trả lời thông qua `STUDENT_ANSWER`
- chấm theo đáp án cuối cùng
- dùng nhóm câu hỏi tĩnh

Điểm cần chú ý nhất khi triển khai là:
- thống nhất rule `is_final_answer`
- thống nhất chuẩn hóa `normalized_answer`
- thống nhất cách xác định đáp án đúng hiện hành trong `QUESTION_ANSWER_KEY`

Khi ba rule trên được chốt rõ, schema này đủ tốt để chuyển sang bước thiết kế API, ERD chi tiết và DDL.
