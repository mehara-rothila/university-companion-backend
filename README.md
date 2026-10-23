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
| PDF Q&A (RAG) | `gemini-embedding-2` embeddings, cosine-similarity retrieval over PDF chunks, page-cited answers |
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

### Study Spaces — `/api/study-spaces`
| Method | Path | Notes |
|---|---|---|
| GET | `/` | List all study zones with real-time computed consensus occupancy |
| POST | `/{id}/vote` | Report crowd occupancy (`EMPTY`, `MODERATE`, `CROWDED`) |
| POST | `/admin/study-spaces` | Admin: Add a new study zone |
| DELETE | `/admin/study-spaces/{id}` | Admin: Delete an existing study zone |

### AI Chatbot (Gemini) — `/api/chatbot`
| Method | Path | Notes |
|---|---|---|
| POST | `/chat` | `{ message, imageUrls?, pdfUrls? }` — text + vision + PDF (answered from retrieved excerpts, see [How PDF questions work](#how-pdf-questions-work-rag)); can look things up first (see [How the assistant looks things up](#how-the-assistant-looks-things-up-tools)) and lists the tools that ran in `toolsUsed`; JWT required |
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

## How PDF questions work (RAG)

When a student attaches a PDF, Athena doesn't paste the whole file into the prompt. It answers from the passages that match the question:

1. **Index** (first question about a PDF only): read the PDF page by page, split each page into ~1,200-character chunks that overlap by 200, embed them with `gemini-embedding-2` (768 dimensions) and store them in `indexed_documents` and `document_chunks`.
2. **Retrieve**: embed the question, score every chunk of the attached PDFs by cosine similarity and keep the best 6, labelled with their page numbers.
3. **Answer**: Gemini answers from those excerpts and cites pages, like (p. 4).

If indexing or retrieval fails, the chat falls back to sending the PDF's text. Deleting a PDF also deletes its stored chunks, whether it's deleted from the chat, the My Uploads page, the library or the upload API. At startup `StaleIndexCleanup` also clears the chunks of uploads already marked deleted and of any PDF whose file is no longer in storage, so a cleanup that failed is retried. The code is in `service/rag/`.

**Measured** on a 40-question test set over four public PDFs (`src/test/resources/rag-eval/`, scored by `RagRetrievalEvalTest`). Each question is tied to the page that answers it and to phrases copied from that page.

| Document | Pages | Questions | Answer chunk in top 6 | MRR | Answer in old full-text prompt | Answer in RAG prompt | Prompt tokens saved |
|---|---|---|---|---|---|---|---|
| Attention Is All You Need | 15 | 10 | 10/10 | 0.95 | 10/10 | 10/10 | 6.1× |
| RFC 6749 (OAuth 2.0) | 76 | 10 | 10/10 | 0.90 | 2/10 | 10/10 | 6.5× |
| OSTEP ch. 7 (CPU scheduling) | 13 | 10 | 10/10 | 0.74 | 10/10 | 10/10 | 3.9× |
| NIST SP 800-63B | 80 | 10 | 9/10 | 0.81 | 3/10 | 9/10 | 7.2× |
| **All** | | **40** | **39/40** | **0.85** | **25/40** | **39/40** | **5.9×** |

- The old full-text prompt cut each PDF at 50,000 characters. On the two long documents it never contained most of the answers. RAG put 39 of the 40 answers in the prompt, with 5.9× fewer prompt tokens (454,039 down to 77,491).
- Hit@1 is 30/40 and hit@3 is 38/40.
- The one miss is a wording question: what earlier editions of NIST SP 800-63 called authenticators. The answer page ranked 16th, the kind of exact-wording match keyword search would add.
- To re-run it, see `src/test/resources/rag-eval/README.md`.

A first single-paper check with `PdfRagTokenComparisonTest` (a 15-page paper, five questions) found 6.2× fewer prompt tokens, with the answer passage retrieved for all five.

| Setting | Default | Notes |
|---|---|---|
| `rag.enabled` | `true` | `false` sends each PDF's full text instead (env var `RAG_ENABLED`) |
| `rag.top-k` | `6` | Chunks sent per question |
| `rag.chunk-size` / `rag.chunk-overlap` | `1200` / `200` | Characters |
| `rag.max-pages` / `rag.max-chunks` | `300` / `800` | Indexing limits per PDF |
| `gemini.embedding.model` / `gemini.embedding.dimensions` | `gemini-embedding-2` / `768` | Changing either re-indexes PDFs on their next question |

Production validates the schema instead of changing it (`ddl-auto=validate`), so the `heroku` profile creates the two tables from `db/rag-schema.sql` at startup. `RagSchemaPostgresTest` checks that script against a real PostgreSQL.

To reproduce the measurement with your own PDF and questions:

```bash
GEMINI_API_KEY=... RAG_EVAL_PDF=paper.pdf \
RAG_EVAL_QUESTIONS="What optimizer did they use?|How many layers are there?" \
RAG_EVAL_EXPECT="Adam|six" \
mvn test -Dtest=PdfRagTokenComparisonTest
```

## How the assistant looks things up (tools)

For signed-in students, the assistant can call tools: read-only lookups it asks for through Gemini function calling. The server runs each one for the user in the JWT and sends the result back, and the model answers from it.

| Tool | Returns |
|---|---|
| `find_upcoming_events` | Approved, visible events in the next 1–90 days (default 30), optionally narrowed by keyword |
| `find_competitions` | Approved competitions that haven't ended, with deadlines, prizes and how to enter |
| `find_study_spaces` | Study spaces, least crowded first; optionally only quiet ones, or one building |
| `search_lost_and_found` | Active LOST or FOUND posts that match a description, without who posted them |
| `get_my_financial_aid_applications` | The student's own applications: status, amounts and dates, without admin notes |
| `search_my_documents` | Excerpts from the student's 5 newest uploaded PDFs, with file names and pages |

Guardrails:

- Tools only read, and the user id comes from the JWT, never from the model's arguments.
- A question gets at most 3 rounds of up to 4 calls each. After that, function calling is set to `NONE`, so the model has to answer.
- Arguments are checked: text is trimmed and capped at 100 characters, numbers are clamped, and bad values come back as an error the model can correct.
- Results are capped at 8 items with long text clipped. The system prompt says tool results are data, not instructions.
- A failing tool returns an error instead of failing the chat. Tokens from every round count toward the student's quota.

The code is in `service/agent/`.

| Setting | Default | Notes |
|---|---|---|
| `agent.enabled` | `true` | `false` turns tools off, and the chat works as before (env var `AGENT_ENABLED`) |

`AssistantToolsLiveTest` checks tool calling against the real API with three questions: one needing a lookup, one needing two tools, and small talk that needs none. It passes on `gemini-3.5-flash`, `gemini-3.5-flash-lite` and `gemini-3.1-flash-lite`.

```bash
AGENT_EVAL=1 GEMINI_API_KEY=... GEMINI_MODEL=gemini-3.5-flash \
mvn test -Dtest=AssistantToolsLiveTest
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
│   └── rag/        # PDF Q&A: PdfPageReader, TextChunker, GeminiEmbeddingClient,
│                   # DocumentIndexService, DocumentRetriever
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
