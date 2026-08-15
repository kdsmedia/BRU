Saya ingin menambahkan AI User otomatis ke aplikasi existing.

ATURAN UTAMA

JANGAN membuat aplikasi baru.

JANGAN mengubah framework existing.

JANGAN menghapus fitur existing.

JANGAN membuat admin panel.

JANGAN membuat dashboard AI.

JANGAN membuat sistem posting, komentar, like, atau user baru jika sistem existing dapat digunakan.

AI harus menjadi fitur backend yang berjalan otomatis.

---

1. AUDIT APLIKASI

Sebelum coding, periksa terlebih dahulu:

- framework
- struktur project
- database
- tabel users
- tabel posts
- tabel comments
- tabel likes
- tabel follows
- autentikasi
- backend/API
- feed
- notification

Tentukan titik integrasi AI yang paling aman tanpa mengganggu sistem existing.

---

2. AI USER

Gunakan tabel "users" existing.

AI User harus mempunyai struktur yang sama dengan user biasa.

Tambahkan metadata:

is_ai = true

User biasa:

is_ai = false

Jangan membuat sistem user terpisah.

AI User tetap menggunakan:

- username
- display name
- avatar
- bio
- followers
- following
- posts
- comments
- likes

---

3. AI PERSONA

Buat beberapa AI User dengan personality berbeda.

Contoh:

Andi:

- santai
- ramah
- suka teknologi
- topik Android dan aplikasi

Sari:

- ceria
- komunikatif
- suka musik dan film

Budi:

- humoris
- suka game
- suka teknologi

Persona harus memengaruhi gaya bahasa, topik, dan cara AI berinteraksi.

---

4. AI POSTING

AI dapat membuat posting otomatis.

Posting harus menggunakan tabel "posts" existing.

AI menentukan topik berdasarkan:

- persona
- interest
- waktu
- posting terbaru
- topik yang sedang aktif

Gunakan variasi konten:

- pertanyaan
- opini
- cerita pendek
- pengalaman
- informasi
- hiburan
- topik sesuai interest

Jangan membuat posting identik atau berulang.

---

5. AI COMMENT

AI dapat membaca posting yang tersedia kemudian memilih posting yang relevan.

Sebelum membuat komentar:

ambil posting
↓
baca isi posting
↓
baca komentar
↓
analisis relevansi
↓
generate komentar
↓
validasi
↓
simpan

Komentar harus relevan dengan posting.

Hindari komentar generik yang sama berulang kali.

---

6. AI REPLY

Jika pengguna membalas komentar AI:

AI membaca:

- posting utama
- komentar AI
- reply pengguna
- percakapan sebelumnya

Kemudian memberikan jawaban yang relevan.

AI harus mempertahankan konteks percakapan.

---

7. AI LIKE

AI dapat memberikan like pada posting yang relevan.

Jangan like semua posting.

Gunakan probabilitas dan interest AI.

---

8. AI FOLLOW

AI dapat mengikuti user berdasarkan:

- kesamaan interest
- interaksi
- posting menarik
- relevansi persona

Jangan melakukan follow secara massal.

---

9. MEMORY

Setiap AI User memiliki memory sendiri.

Simpan hanya informasi yang diperlukan, misalnya:

- topik percakapan
- ringkasan percakapan
- user yang pernah berinteraksi
- konteks percakapan

AI yang berbeda harus mempunyai memory terpisah.

---

10. AUTOMATIC SCHEDULER

Tidak ada admin panel.

AI berjalan otomatis melalui backend/scheduler.

Contoh:

Scheduler
↓
Pilih AI User
↓
Periksa waktu aktif
↓
Periksa cooldown
↓
Periksa batas aktivitas
↓
Ambil feed
↓
Tentukan aktivitas
↓
Generate AI
↓
Moderate
↓
Publish

---

11. KONFIGURASI INTERNAL

Semua konfigurasi ditentukan langsung di backend/environment variable.

Contoh:

AI_ENABLED=true

AI_POST_PROBABILITY=20
AI_COMMENT_PROBABILITY=35
AI_REPLY_PROBABILITY=60
AI_LIKE_PROBABILITY=55
AI_FOLLOW_PROBABILITY=10

AI_MAX_POSTS_PER_DAY=5
AI_MAX_COMMENTS_PER_HOUR=10
AI_MAX_REPLIES_PER_HOUR=10
AI_MAX_LIKES_PER_HOUR=30
AI_MAX_FOLLOWS_PER_DAY=5

Tidak perlu UI untuk mengubah konfigurasi tersebut.

---

12. NATURAL ACTIVITY

Jangan membuat semua AI aktif bersamaan.

Gunakan variasi:

- waktu aktivitas
- jumlah aktivitas
- interval
- jenis aktivitas
- panjang posting
- panjang komentar
- topik
- gaya bahasa

AI tidak boleh memiliki pola aktivitas yang mudah ditebak.

---

13. ANTI-SPAM

Implementasikan:

- cooldown
- rate limit
- duplicate detection
- similarity detection
- target cooldown
- daily limit

AI tidak boleh:

- spam posting
- spam komentar
- like semua posting
- follow banyak user sekaligus
- mengulang komentar identik

---

14. DATABASE

Gunakan tabel existing.

Jika benar-benar diperlukan, buat:

ai_agents
ai_memory
ai_activity_logs

Jangan membuat tabel duplicate untuk:

- users
- posts
- comments
- likes
- follows

Gunakan foreign key ke tabel existing.

---

15. ACTIVITY LOG

Simpan aktivitas AI secara internal:

ai_user_id
activity_type
target_post_id
target_user_id
generated_content
status
error
created_at

Activity:

POST
COMMENT
REPLY
LIKE
FOLLOW

Activity log hanya untuk debugging/developer, bukan admin panel.

---

16. AI SERVICE

Buat satu abstraction layer:

AIService

Dengan fungsi:

generatePost()
generateComment()
generateReply()
chooseActivity()
analyzePost()
moderateContent()

Dengan demikian provider AI dapat diganti tanpa mengubah seluruh aplikasi.

---

17. KEAMANAN API KEY

API key AI tidak boleh berada di APK atau frontend.

Gunakan:

Android
   ↓
Supabase
   ↓
Edge Function / Backend
   ↓
AI API

Simpan API key sebagai secret/environment variable backend.

---

18. MODERATION

Semua konten AI:

Generate
↓
Validate
↓
Moderate
↓
Publish

Jangan langsung memasukkan output AI ke database tanpa validasi.

---

19. IDENTITAS AI

AI menggunakan sistem user existing dan dapat memakai UI profil/post/comment yang sama.

Simpan:

is_ai = true

sebagai metadata internal dan sediakan pengungkapan identitas AI yang sesuai di aplikasi.

Tidak perlu membuat UI atau panel administrasi khusus AI.

---

20. ERROR HANDLING

Jika AI API gagal:

- jangan membuat post kosong
- jangan membuat comment kosong
- jangan membuat duplicate
- simpan error
- retry terbatas
- gunakan cooldown
- aplikasi utama tetap berjalan normal

Jika AI dimatikan, aplikasi harus tetap berfungsi normal untuk seluruh user manusia.

---

21. HASIL AKHIR

Setelah implementasi:

1. Tampilkan file yang dibuat.
2. Tampilkan file yang diubah.
3. Berikan SQL migration jika diperlukan.
4. Berikan environment variables.
5. Berikan konfigurasi scheduler.
6. Berikan cara menjalankan AI.
7. Berikan cara membuat AI User.
8. Berikan cara mengatur persona.
9. Berikan cara testing.
10. Jelaskan cara mematikan AI melalui environment variable.

Jangan membuat admin panel.

Jangan membuat dashboard.

Jangan membuat aplikasi baru.

Fokus hanya pada integrasi AI User otomatis ke aplikasi media sosial yang sudah ada.
