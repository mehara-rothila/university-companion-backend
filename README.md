<div align="center">

# 🌐 https://athena.rothila.com

### ▶ LIVE APP

</div>

# Smart University Companion — Backend

Spring Boot REST API for the Smart University Companion (L3 Individual Project, University of Moratuwa). Provides auth, Lost & Found, Financial Aid, Notifications, Emergency alerts, Achievements, an AI chatbot (Google Gemini), weather chat, Stripe payments, and AWS S3 image/file storage.

- **Live API:** `https://athena001-535225bb557e.herokuapp.com`
- **Frontend:** https://athena.rothila.com (Netlify)

## Tech Stack

| Area | Technology |
|---|---|
| Language / Framework | Java 17, Spring Boot 3.2, Maven |
| Security | Spring Security 6, JWT (`io.jsonwebtoken` 0.11.5), BCrypt |
| Database | PostgreSQL 15+, Spring Data JPA / Hibernate 6, HikariCP |
| Storage | AWS S3 (AWS SDK) |
| Realtime | Spring WebSocket + STOMP (in-memory broker) |
| AI Chatbot | Google Gemini (`generateContent`), default `gemini-3.5-flash` |
| Other | Stripe (payments), Spring Mail (Brevo SMTP), Apache PDFBox, SpringDoc/OpenAPI 3 |

## API Endpoints

All protected routes require `Authorization: Bearer <jwt>`. `userId` is derived from the JWT, not request bodies.

### Auth — `/api/auth`
| Method | Path | Notes |
|---|---|---|
| POST | `/signin` | `{ email, password }` → `{ token, type, id, email }` |
| POST | `/signup` | `{ email, password, ... }` → email verification sent |
| POST | `/verify-email` | `{ email, code }` |
| POST | `/resend-verification` | `{ email }` |
| POST | `/oauth/register` | Google OAuth register/login bridge |
| POST | `/forgot-password` / `/reset-password` | OTP-based reset |

### Lost & Found — `/api/lost-found`
| Method | Path | Notes |
|---|---|---|
| GET | `/items` | Filters: `type, category, location, search, status` |
| GET | `/items/{id}` | Single item |
| POST | `/items` | Create (body below) |
| PUT | `/items/{id}` | Update |
| DELETE | `/items/{id}` | Delete |
| PUT | `/items/{id}/status?status=RESOLVED` | Status change |
| GET | `/items/user/{userId}` | A user's items |
| GET | `/stats` | Counts, categories, locations |

### Image / File Upload — `/api/upload`
| Method | Path | Notes |
|---|---|---|
| POST | `/image` | `multipart/form-data` `file` → `{ imageUrl }` |
| GET | `/image/serve?url=<s3Url>` | Backend proxy → binary image |
| DELETE | `/image?imageUrl=<s3Url>` | Delete from S3 |

### Admin — `/api/admin`
`GET /dashboard/stats` · `GET /users?page&size` · `GET|PUT|DELETE /users/{id}` · `PATCH /users/{id}/toggle-status` · `PATCH /users/{id}/reset-password` · `POST /users/bulk-action`

### Financial Aid — `/api/financial-aid`
`GET /applications?status&type` · `GET /applications/{id}` · `GET /applications/user/{userId}` · `POST /applications` · `PUT /applications/{id}` · `DELETE /applications/{id}` · `GET /stats` · `GET /donations` (public)
Admin: `POST /admin/financial-aid/applications/{id}/review` · `GET /admin/financial-aid/applications?page&size`

### Notifications — `/api/notifications`
`GET /user/{userId}?page&size` · `GET /user/{userId}/unread/count` · `PUT /{id}/read` · `PUT /user/{userId}/read-all` · `DELETE /{id}` · `DELETE /user/{userId}/read` · `POST /` (admin)

### Achievements — `/api/achievements`
`GET /approved` (public feed) · `GET /pending/{adminId}` · `GET /student/{studentId}` · `POST /` · `PUT /{id}/approve/{adminId}` · `PUT /{id}/reject/{adminId}` · `POST /{id}/like` · `DELETE /{id}/unlike`

### AI Chatbot (Gemini) — `/api/chatbot`
| Method | Path | Notes |
|---|---|---|
| POST | `/chat` | `{ message, imageUrls?, pdfUrls? }` — text + vision + PDF, JWT required |
| POST | `/uploads` | Track a chatbot file upload |
| GET | `/health` | Public health check |

### Weather Chat — `/api/weather/chat`
`POST /` → `{ message }` (Gemini answer with current weather context).

### Other
`GET /api/users/profile`, `GET|PUT|DELETE /api/users/{id}` · Payments via `PaymentController` (Stripe) · `POST /api/setup/init` (bootstrap admin) · `GET /api/health` · WebSocket: `CONNECT /ws`, `SUBSCRIBE /topic/notifications/{userId}`, `SEND /app/notifications`.

### Representative payloads
```jsonc
// POST /api/lost-found/items
{ "type": "LOST", "title": "iPhone 14 Pro", "description": "Black, blue case",
  "category": "Electronics", "location": "Main Library", "imageUrl": "https://.../img.jpg",
  "reward": 100.0, "contactMethod": "DIRECT", "priority": "HIGH", "tags": ["phone","apple"] }

// POST /api/financial-aid/applications
{ "aidType": "SCHOLARSHIP", "amount": 5000.00, "reason": "Tuition assistance",
  "academicYear": "2024/2025", "cgpa": 3.75, "familyIncome": 50000.00, "documents": ["doc1.pdf"] }

// POST /api/financial-aid/admin/applications/{id}/review
{ "status": "APPROVED", "reviewNotes": "Meets criteria", "approvedAmount": 5000.00 }
```

## Getting Started

**Prerequisites:** Java 17+, Maven 3.6+ (or the bundled wrapper), PostgreSQL 12+, AWS account (S3, optional for local).

```bash
git clone <repository-url>
cd university-companion-backend

# Run (uses src/main/resources/application.properties; copy from the .template)
./mvnw spring-boot:run          # or: mvnw.cmd spring-boot:run  (Windows)

# Build / run jar
./mvnw clean package
java -jar target/smart-university-backend-1.0.0.jar

# Verify
curl http://localhost:8080/api/health      # {"status":"UP", ...}
```

Local DB with Docker:
```bash
docker run --name postgres-university -e POSTGRES_DB=smart_university_db \
  -e POSTGRES_USER=postgres -e POSTGRES_PASSWORD=<password> -p 5433:5432 -d postgres:15
```

## Configuration

`application.properties` and `application-local.properties` are **gitignored** (hold secrets). Every value reads from an env var with a sensible default — `${VAR:default}`. Key settings:

```properties
server.port=8080
spring.datasource.url=${DATABASE_URL:jdbc:postgresql://localhost:5432/smart_campus_db}
spring.datasource.username=${DB_USERNAME:postgres}
spring.datasource.password=${DB_PASSWORD:postgres}
spring.jpa.hibernate.ddl-auto=validate            # local profile overrides to "update"

jwt.secret=${JWT_SECRET:}                          # must be >= 256 bits (HS256)
jwt.expiration=${JWT_EXPIRATION:86400}

aws.access-key-id=${AWS_ACCESS_KEY_ID:}
aws.secret-access-key=${AWS_SECRET_ACCESS_KEY:}
aws.region=${AWS_REGION:us-east-1}
aws.s3.bucket-name=${AWS_S3_BUCKET:thirdyearproject}

# AI chatbot (active provider = Gemini)
gemini.api.key=${GEMINI_API_KEY:}
gemini.api.model=${GEMINI_API_MODEL:gemini-3.5-flash}
gemini.api.base-url=${GEMINI_API_BASE_URL:https://generativelanguage.googleapis.com/v1beta}
gemini.api.thinking-budget=${GEMINI_THINKING_BUDGET:0}   # 0 = no thinking (flash); -1 = model decides (pro)
# Kimi/Moonshot config remains but is legacy/unwired

weather.api.key=${WEATHER_API_KEY:}
stripe.secret-key=${STRIPE_SECRET_KEY:}
stripe.webhook-secret=${STRIPE_WEBHOOK_SECRET:}
spring.mail.* (Brevo SMTP)    app.mail.from=${MAIL_FROM:noreply@athena.rothila.com}
```

**Profiles:** `default` (base `application.properties`), `local` (Neon Postgres + `ddl-auto=update`), `heroku` (production).

## Project Structure

```
src/main/java/com/smartuniversity/
├── SmartUniversityApplication.java
├── config/         # SecurityConfig (JWT, CORS), PaymentConfig (Stripe), ...
├── controller/     # Auth, Admin, LostFound, ImageUpload, FinancialAid(+Admin),
│                   # Notification, Emergency, Achievement, GeneralChatbot,
│                   # WeatherChat, ChatbotUpload, Payment, User, Setup, Health
├── dto/            # Request/response payloads
├── model/          # JPA entities (User, LostFoundItem, FinancialAid, Notification,
│                   # StudentAchievement, ChatbotUpload, TokenTransaction, ...)
├── repository/     # Spring Data JPA repositories
├── security/       # JwtUtils, JwtAuthenticationFilter
├── service/        # GeminiChatService (active), KimiChatService (legacy), S3Service,
│                   # TokenService, WeatherService, PaymentService, ...
└── util/           # AuthUtils (current user from JWT)
src/main/resources/ # application[-local|-heroku].properties (+ .template)
```

## Deployment (Heroku — app `athena001`)

Production runs with `SPRING_PROFILES_ACTIVE=heroku`. Build is auto-detected (Java/Maven).

- **Procfile:** `web: java -Dserver.port=$PORT -Dspring.profiles.active=heroku -jar target/*.jar`
- **system.properties:** `java.runtime.version=17`
- **Required config vars:** `DATABASE_URL`, `JWT_SECRET`, `CORS_ALLOWED_ORIGINS`, `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`, `AWS_REGION`, `AWS_S3_BUCKET`, `GEMINI_API_KEY`, `WEATHER_API_KEY`, `STRIPE_SECRET_KEY`, `MAIL_FROM`, SMTP creds, `SPRING_PROFILES_ACTIVE=heroku`.

```bash
heroku logs --tail --app athena001
curl https://athena001-535225bb557e.herokuapp.com/api/health
```

## Troubleshooting

- **Won't start / port 8080 busy:** `netstat -ano | findstr :8080` then `taskkill /PID <pid> /F` (Win) · `lsof -ti:8080 | xargs kill -9` (Unix).
- **JWT errors:** `JWT_SECRET` must be set and ≥ 256 bits, or `Keys.hmacShaKeyFor` throws.
- **DB connection:** `psql -h localhost -p 5433 -U postgres -d smart_university_db`; check the URL/credentials match your profile.
- **S3 upload fails:** verify AWS keys and bucket; `aws s3 ls s3://<bucket>`.
- **CORS:** ensure the frontend origin is in `CORS_ALLOWED_ORIGINS`.

---

*Developed for academic coursework at the University of Moratuwa.* 🎓
