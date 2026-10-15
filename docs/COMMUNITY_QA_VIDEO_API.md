# API Hoi dap va Video

Base URL: `http://localhost:8081`. Swagger: `/swagger-ui/index.html`.
Tat ca endpoint dung envelope `ApiResponse` cua du an. `data` la doi tuong
hoac `PageResponse`: `items`, `page`, `size`, `totalElements`, `totalPages`,
`first`, `last`. Trang bat dau tu 0; `size` tu 1 den 50.

## Phan quyen

- Khach: doc cau hoi APPROVED, video PUBLISHED va binh luan cua video cong khai.
- Thanh vien ACTIVE: gui cau hoi, xem cau hoi cua minh, binh luan va luu video.
- Admin ACTIVE: duyet/tu choi, tra loi, xoa cau hoi; CRUD video; xoa binh luan vi pham.
- Quyen va trang thai tai khoan duoc kiem tra lai tu database. Token cu cua
  tai khoan da bi khoa/ha quyen khong cho phep ghi noi dung hay quan tri.
- API yeu cau dang nhap dung `Authorization: Bearer <accessToken>` cua he thong
  hien tai. Khong dung Basic Auth, khong tao them he thong dang nhap.

## Hoi dap

| Method | Endpoint | Quyen / chuc nang |
| --- | --- | --- |
| GET | `/api/questions` | Cong khai; `keyword`, `answered=true/false`, `page`, `size` |
| GET | `/api/questions/{id}` | Chi tiet cau hoi da duyet va cau tra loi |
| POST | `/api/questions` | Thanh vien gui cau hoi, tra HTTP 201 |
| GET | `/api/questions/mine` | Cau hoi cua toi; `status=PENDING/APPROVED/REJECTED`, `page`, `size` |
| GET | `/api/questions/mine/{id}` | Chi tiet cua chinh nguoi gui, kem ly do tu choi |
| GET | `/api/admin/questions` | Admin; `status`, `keyword`, `page`, `size` |
| GET | `/api/admin/questions/{id}` | Admin xem tat ca trang thai |
| PUT | `/api/admin/questions/{id}/moderation` | Duyet hoac tu choi |
| PUT | `/api/admin/questions/{id}/answer` | Tao/cap nhat cau tra loi cua admin |
| DELETE | `/api/admin/questions/{id}?version=...` | Xoa cau hoi |

Gui cau hoi:

```json
{"content":"Lam sao nau dau xanh nhanh mem?"}
```

Cau hoi luon duoc luu voi trang thai `PENDING`. Server lay tac gia tu token,
khong nhan `authorId`, `status`, `answer` do nguoi gui tu gan.
Chi sau khi admin duyet, moi nguoi moi thay cau hoi va cau tra loi.
Admin co the tra loi truoc khi duyet; hanh dong tra loi khong tu xuat ban.

Duyet:

```json
{"decision":"APPROVE","version":0}
```

Tu choi (bat buoc ly do):

```json
{"decision":"REJECT","reason":"Cau hoi khong lien quan chu de.","version":0}
```

Tra loi:

```json
{"content":"Ban co the ngam dau truoc khi nau.","version":1}
```

Cau hoi dang APPROVED van co the duoc admin chuyen sang REJECTED de an lai.
Ly do, nguoi duyet va thoi gian duyet chi hien cho tac gia/admin, khong nam
trong phan hoi cong khai. Khong tra email, passwordHash hay ho so suc khoe.

## Video

| Method | Endpoint | Quyen / chuc nang |
| --- | --- | --- |
| GET | `/api/videos` | Cong khai; `keyword`, `category`, `page`, `size` |
| GET | `/api/videos/{id}` | Chi tiet video PUBLISHED |
| GET | `/api/admin/videos` | Admin; `status=DRAFT/PUBLISHED`, `keyword`, `category`, `page`, `size` |
| GET | `/api/admin/videos/{id}` | Admin xem chi tiet, ke ca ban nhap |
| POST | `/api/admin/videos` | Tao video, tra HTTP 201 |
| PUT | `/api/admin/videos/{id}` | Thay noi dung video; bat buoc `version` |
| DELETE | `/api/admin/videos/{id}?version=...` | Xoa video va binh luan/yeu thich lien quan |
| GET | `/api/videos/{id}/comments` | Cong khai; `page`, `size`; cu nhat truoc |
| POST | `/api/videos/{id}/comments` | Thanh vien binh luan, tra HTTP 201 |
| PUT | `/api/videos/{id}/comments/{commentId}` | Tac gia sua binh luan; bat buoc `version` |
| DELETE | `/api/videos/{id}/comments/{commentId}?version=...` | Tac gia hoac admin xoa |
| GET | `/api/videos/favorites` | Danh sach video yeu thich cua toi; `page`, `size` |
| GET | `/api/videos/{id}/favorite` | Kiem tra toi da luu hay chua |
| PUT | `/api/videos/{id}/favorite` | Luu video; khong can body; goi lai khong tao trung |
| DELETE | `/api/videos/{id}/favorite` | Bo luu video; khong can body |

Tao video:

```json
{
  "title":"Cach nau canh rau cu chay",
  "description":"Nguyen lieu va cac buoc nau mon canh chay.",
  "videoUrl":"https://www.youtube.com/watch?v=VIDEO_ID",
  "thumbnailUrl":"https://example.com/canh-rau-cu.jpg",
  "category":"MON_CHAY",
  "durationSeconds":300,
  "status":"PUBLISHED"
}
```

`title`, `videoUrl`, `status` bat buoc. `durationSeconds` neu co phai tu 1
den 86400. URL phai la HTTP(S) tuyet doi, khong chua username/password.
Co the luu URL YouTube/Vimeo/video truc tiep. Backend khong tai URL tu xa,
khong nhan iframe/HTML va khong upload/transcode file video.

PUT video dung cung body nhu POST va them `version` tu phan hoi moi nhat.
`DRAFT` chi admin xem duoc. Khi chuyen video ve DRAFT, video va binh luan
khong con cong khai, video cung tam an khoi danh sach yeu thich. Nguoi dung
van co the bo luu video da an neu con giu ID.

Gui binh luan:

```json
{"content":"Cam on ban, mon nay rat de nau."}
```

Sua binh luan dung `{"content":"Noi dung moi","version":0}`.
Tac gia lay tu token; khong the sua/xoa binh luan cua nguoi khac. Admin
duoc xoa binh luan cua bat ky ai qua cung endpoint DELETE.
Danh sach yeu thich rieng theo tai khoan; cap video/user co unique constraint.

## Tim kiem va tinh nhat quan

- Tim kiem khong phan biet hoa/thuong; `%`, `_`, `\` duoc tim nhu ky tu
  thuong, khong bi bien thanh wildcard SQL. `keyword` toi da 200 ky tu.
- Hoi dap tim trong noi dung va cau tra loi; video tim trong tieu de/mo ta.
- Video/cau hoi sap xep moi nhat truoc, co ID lam khoa sap xep phu on dinh.
- Moi phan hoi noi dung co `version`. Sau moi lan cap nhat phai dung version
  moi; version cu tra `409`. Thieu version khi ghi/xoa tra `400`.
- Nguoi dung khac truy cap cau hoi rieng hoac binh luan khong thuoc quyen
  cua minh nhan `404`. Tai nguyen chua duyet/ban nhap cung tra `404` cong khai.
- `401`: chua dang nhap; `403`: khong du quyen/tai khoan khong ACTIVE;
  `400`: payload/phan trang/UUID/URL khong hop le.
- Noi dung cau hoi, tra loi, mo ta va binh luan la plain text. FE phai render
  bang text node/React escaping, khong dung `innerHTML` voi noi dung nay.

## Database va kiem thu

Bang moi: `community_questions`, `community_videos`, `video_comments`,
`video_favorites`. Cau hinh `spring.jpa.hibernate.ddl-auto=update` hien tai
tao bang/index khi khoi dong, khong xoa bang cu. Khong seed du lieu mau
vao database that va khong sao chep noi dung tu website tham khao.

```powershell
$env:JAVA_HOME = 'C:\Program Files\Microsoft\jdk-21.0.12.101-hotspot'
.\mvnw.cmd -Pstandalone -Dtest=CommunityApiTest test
```

Test dung MockMvc + H2, token JWT cua tai khoan fixture va rollback moi test;
khong dang nhap admin that, khong goi Gemini, SMTP hay xoa du lieu production.
Bao phu luong gui/duyet/tra loi/xoa, CRUD video, tim kiem/phan trang,
binh luan, yeu thich, ownership, tai khoan bi khoa/ha quyen va version cu.

Tham khao chuc nang:
[Hoi & Dap](https://ducankitchen.com/hoi-dap/),
[Video](https://ducankitchen.com/category/video/).
