# Hướng Dẫn Đóng Góp (Contributing Guide)

Cảm ơn bạn đã quan tâm đến **HealthApp**! Chúng tôi hoan nghênh mọi sự đóng góp để làm dự án tốt hơn.

## Cách thức đóng góp

1.  **Fork** dự án này về tài khoản GitHub của bạn.
2.  **Clone** repo về máy: `git clone https://github.com/YOUR_USERNAME/HeathApp.git`
3.  Tạo một **Branch** mới cho tính năng bạn muốn làm: `git checkout -b feature/Ten-Tinh-Nang`
4.  Thực hiện thay đổi và **Commit**: `git commit -m "Thêm tính năng X"`
5.  **Push** lên fork của bạn: `git push origin feature/Ten-Tinh-Nang`
6.  Tạo **Pull Request (PR)** từ branch của bạn vào nhánh `main` của repo gốc.

## Quy ước Code (Coding Conventions)

* Sử dụng **Kotlin** và tuân thủ [Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html).
* Kiến trúc: Tuân thủ mô hình **MVVM** và **Clean Architecture** đã có sẵn.
* Đặt tên biến/hàm rõ nghĩa bằng tiếng Anh.

## Báo lỗi (Bug Report)

Nếu bạn tìm thấy lỗi, hãy tạo một [Issue](https://github.com/PQ-Thinh/HeathApp/issues) mới và mô tả chi tiết các bước để tái hiện lỗi đó.