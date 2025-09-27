# Smart Campus Companion - Backend

A comprehensive Spring Boot REST API backend for the Smart Campus Companion application - L3 Individual Project at University of Moratuwa.

## 🚀 Features

### 🔐 Authentication & Security
- **JWT-based Authentication** - Secure token-based user authentication
- **Spring Security Integration** - Role-based access control
- **User Registration & Login** - Complete user management system
- **Protected Endpoints** - Authorization required for sensitive operations
- **CORS Configuration** - Cross-origin resource sharing for frontend integration

### 📚 Lost & Found System (Production Ready)
- **Complete CRUD Operations** - Create, read, update, delete lost/found items
- **Advanced Search & Filtering** - Search by type, category, location, status, keywords
- **User-specific Management** - Users can manage their own posted items
- **Real-time Statistics** - Dynamic item counts and metrics
- **Status Management** - Item status tracking (Active, Resolved, Expired)
- **Priority System** - High, Medium, Low priority classification

### 🖼️ Image Management
- **AWS S3 Integration** - Secure cloud storage for item images
- **File Upload Validation** - Type, size, and format verification
- **Image Serving Proxy** - Backend proxy for secure image delivery
- **Image Deletion** - Complete lifecycle management

### 🗄️ Database Integration
- **PostgreSQL Database** - Production-ready relational database
- **JPA/Hibernate ORM** - Object-relational mapping
- **Connection Pooling** - Optimized database connections
- **Data Persistence** - Reliable data storage and retrieval

## 🛠️ Tech Stack

### Core Framework
- **Java**: 17+ (LTS version)
- **Spring Boot**: 3.2.0 (Latest stable)
- **Spring Framework**: 6.x
- **Maven**: 3.9+ (Build tool)

### Security & Authentication
- **Spring Security**: 6.x
- **JWT (JSON Web Tokens)**: io.jsonwebtoken 0.11.5
- **BCrypt Password Encoding**: Built-in Spring Security

### Database & Persistence
- **PostgreSQL**: 15+ (Primary database)
- **Spring Data JPA**: Object-relational mapping
- **Hibernate**: 6.x (JPA implementation)
- **Connection Pooling**: HikariCP (default)

### Cloud Services
- **AWS SDK**: 2.20.56 (S3 integration)
- **AWS S3**: Image storage and management

### Development Tools
- **Maven Wrapper**: Included for platform independence
- **Spring Boot DevTools**: Development-time features
- **Validation**: Bean validation with Hibernate Validator
- **Actuator**: Application monitoring and management

### API Documentation
- **OpenAPI 3**: Swagger/OpenAPI documentation
- **SpringDoc**: 2.3.0 (API documentation generation)

## 📡 API Endpoints

### 🔐 Authentication Endpoints
```http
POST /api/auth/signin
Content-Type: application/json
{
  "email": "user@example.com",
  "password": "password123"
}
Response: {
  "token": "jwt_token_here",
  "type": "Bearer",
  "id": 1,
  "email": "user@example.com"
}
```

```http
POST /api/auth/signup
Content-Type: application/json
{
  "email": "newuser@example.com", 
  "password": "password123"
}
Response: {
  "message": "User registered successfully!"
}
```

### 📚 Lost & Found Endpoints

#### Get All Items (with filtering)
```http
GET /api/lost-found/items?type=LOST&category=Electronics&location=Main Library&search=iPhone&status=ACTIVE
Response: [
  {
    "id": 1,
    "type": "LOST",
    "title": "iPhone 14 Pro",
    "description": "Black iPhone with blue case",
    "category": "Electronics",
    "location": "Main Library",
    "dateReported": "2024-01-15T10:30:00",
    "imageUrl": "https://s3-bucket/image.jpg",
    "reward": 100.0,
    "contactMethod": "DIRECT",
    "status": "ACTIVE",
    "postedBy": "John Doe",
    "postedByUserId": 1,
    "priority": "HIGH",
    "tags": ["phone", "apple"],
    "createdAt": "2024-01-15T10:30:00",
    "updatedAt": "2024-01-15T10:30:00"
  }
]
```

#### Get Single Item
```http
GET /api/lost-found/items/{id}
Authorization: Bearer {jwt_token}
```

#### Create New Item
```http
POST /api/lost-found/items
Authorization: Bearer {jwt_token}
Content-Type: application/json
{
  "type": "LOST",
  "title": "iPhone 14 Pro",
  "description": "Black iPhone with blue case",
  "category": "Electronics",
  "location": "Main Library",
  "imageUrl": "https://s3-bucket/image.jpg",
  "reward": 100.0,
  "contactMethod": "DIRECT",
  "priority": "HIGH",
  "tags": ["phone", "apple"]
}
```

#### Update Item
```http
PUT /api/lost-found/items/{id}
Authorization: Bearer {jwt_token}
Content-Type: application/json
```

#### Delete Item
```http
DELETE /api/lost-found/items/{id}
Authorization: Bearer {jwt_token}
```

#### Update Item Status
```http
PUT /api/lost-found/items/{id}/status?status=RESOLVED
Authorization: Bearer {jwt_token}
```

#### Get User's Items
```http
GET /api/lost-found/items/user/{userId}
Authorization: Bearer {jwt_token}
```

#### Get Statistics
```http
GET /api/lost-found/stats
Response: {
  "totalItems": 150,
  "lostItems": 80,
  "foundItems": 70,
  "resolvedItems": 25,
  "categories": ["Electronics", "Personal Items", "Books"],
  "locations": ["Main Library", "Student Union", "Gym"]
}
```

### 🖼️ Image Upload Endpoints

#### Upload Image to S3
```http
POST /api/upload/image
Content-Type: multipart/form-data
Form data: file=<image_file>
Response: {
  "imageUrl": "https://s3-bucket/image.jpg",
  "message": "Image uploaded successfully"
}
```

#### Serve Image (Proxy)
```http
GET /api/upload/image/serve?url=<s3_image_url>
Response: Binary image data with appropriate Content-Type
```

#### Delete Image
```http
DELETE /api/upload/image?imageUrl=<s3_image_url>
Authorization: Bearer {jwt_token}
Response: "Image deleted successfully"
```

### 👤 User Management Endpoints
```http
GET /api/users/profile
Authorization: Bearer {jwt_token}

GET /api/users/{id}
Authorization: Bearer {jwt_token}

PUT /api/users/{id}
Authorization: Bearer {jwt_token}

DELETE /api/users/{id}
Authorization: Bearer {jwt_token}
```

### 🔍 Health Check
```http
GET /api/health
Response: {
  "status": "UP",
  "timestamp": "2024-01-15T10:30:00Z"
}
```

## 🚀 Getting Started

### 📋 Prerequisites
- **Java**: 17 or higher (LTS recommended)
- **Maven**: 3.6+ (or use included Maven Wrapper)
- **PostgreSQL**: 12+ (for production database)
- **AWS Account**: For S3 image storage (optional for development)
- **Git**: For version control

### ⚡ Quick Start

#### 1. Clone Repository
```bash
git clone <repository-url>
cd university-companion-backend
```

#### 2. Database Setup
**Option A: PostgreSQL (Recommended)**
```sql
-- Create database
CREATE DATABASE smart_campus_db;
CREATE USER postgres WITH ENCRYPTED PASSWORD 'your_password';
GRANT ALL PRIVILEGES ON DATABASE smart_campus_db TO postgres;
```

**Option B: Docker PostgreSQL**
```bash
docker run --name postgres-campus \
  -e POSTGRES_DB=smart_campus_db \
  -e POSTGRES_USER=postgres \
  -e POSTGRES_PASSWORD=rothila \
  -p 5433:5432 \
  -d postgres:15
```

#### 3. Configuration Setup
Create `src/main/resources/application.properties` from template:
```properties
# Server Configuration
server.port=8080
spring.application.name=smart-campus-backend

# Database Configuration
spring.datasource.url=jdbc:postgresql://localhost:5433/smart_campus_db
spring.datasource.driverClassName=org.postgresql.Driver
spring.datasource.username=postgres
spring.datasource.password=rothila

# JPA Configuration
spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect
spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=true
spring.jpa.properties.hibernate.jdbc.lob.non_contextual_creation=true

# CORS Configuration
spring.web.cors.allowed-origins=http://localhost:3000,http://localhost:3001
spring.web.cors.allowed-methods=GET,POST,PUT,DELETE,OPTIONS
spring.web.cors.allowed-headers=*

# JWT Configuration
jwt.secret=your_jwt_secret_key_here_change_in_production
jwt.expiration=86400

# AWS S3 Configuration (Optional)
aws.access-key-id=YOUR_AWS_ACCESS_KEY
aws.secret-access-key=YOUR_AWS_SECRET_KEY
aws.region=us-east-1
aws.s3.bucket-name=your-bucket-name
```

#### 4. Install Dependencies
```bash
# Using Maven Wrapper (recommended)
./mvnw clean install

# Using system Maven
mvn clean install
```

#### 5. Run Application
```bash
# Development mode (with auto-restart)
./mvnw spring-boot:run

# Windows
mvnw.cmd spring-boot:run

# Production build
./mvnw clean package
java -jar target/smart-campus-backend-1.0.0.jar
```

#### 6. Verify Installation
```bash
# Health check
curl http://localhost:8080/api/health

# Expected response
{"status":"UP","timestamp":"2024-01-15T10:30:00Z"}
```

### 🔧 Development Setup

#### IDE Configuration
**IntelliJ IDEA:**
1. Import as Maven project
2. Set Java SDK to 17+
3. Enable annotation processing
4. Install Spring Boot plugin

**VS Code:**
1. Install Extension Pack for Java
2. Install Spring Boot Extensions
3. Configure Java runtime to 17+

#### Database Development Tools
- **pgAdmin**: GUI for PostgreSQL management
- **DBeaver**: Universal database tool
- **H2 Console**: For development/testing (if using H2)

### 🌍 Environment Configurations

#### Development Environment
```properties
spring.profiles.active=dev
spring.jpa.hibernate.ddl-auto=create-drop  # Reset DB on restart
spring.jpa.show-sql=true
logging.level.com.smartcampus=DEBUG
```

#### Production Environment
```properties
spring.profiles.active=prod
spring.jpa.hibernate.ddl-auto=validate     # Don't auto-modify schema
spring.jpa.show-sql=false
logging.level.root=WARN
logging.level.com.smartcampus=INFO
```

### 📊 Application Monitoring
Access these endpoints for monitoring:
- **Health**: `http://localhost:8080/actuator/health`
- **Metrics**: `http://localhost:8080/actuator/metrics`
- **Info**: `http://localhost:8080/actuator/info`

## 📁 Project Structure

```
src/
├── main/
│   ├── java/com/smartcampus/
│   │   ├── SmartCampusApplication.java    # Main Spring Boot application
│   │   ├── config/
│   │   │   └── SecurityConfig.java        # Security & CORS configuration
│   │   ├── controller/                    # REST API Controllers
│   │   │   ├── AuthController.java        # Authentication endpoints
│   │   │   ├── HealthController.java      # Health check endpoint
│   │   │   ├── ImageUploadController.java # S3 image upload/serve
│   │   │   ├── LostFoundController.java   # Lost & Found CRUD operations
│   │   │   └── UserController.java        # User management
│   │   ├── dto/                          # Data Transfer Objects
│   │   │   ├── JwtResponse.java          # JWT login response
│   │   │   ├── LoginRequest.java         # Login request payload
│   │   │   ├── LostFoundItemRequest.java # Item creation request
│   │   │   ├── LostFoundItemResponse.java # Item response format
│   │   │   └── SignupRequest.java        # Registration request
│   │   ├── model/                        # JPA Entity Models
│   │   │   ├── LostFoundItem.java        # Lost & Found item entity
│   │   │   └── User.java                 # User entity
│   │   ├── repository/                   # Data Access Layer
│   │   │   ├── LostFoundItemRepository.java # Lost & Found data access
│   │   │   └── UserRepository.java          # User data access
│   │   ├── security/                     # Security Components
│   │   │   └── JwtUtils.java            # JWT token utilities
│   │   └── service/                      # Business Logic Layer
│   │       ├── S3Service.java           # AWS S3 operations
│   │       ├── UserDetailsServiceImpl.java # Spring Security user service
│   │       └── UserPrincipal.java       # User security principal
│   └── resources/
│       ├── application.properties        # Main configuration
│       └── application.properties.template # Configuration template
└── target/                              # Compiled artifacts
    └── smart-campus-backend-1.0.0.jar  # Executable JAR
```

### 📋 Key Components Explanation

#### Controllers (REST API Layer)
- **AuthController**: JWT authentication, login/register endpoints
- **LostFoundController**: Complete CRUD operations for lost & found items
- **ImageUploadController**: S3 image upload, serving, and deletion
- **UserController**: User profile management
- **HealthController**: Application health monitoring

#### Models (Data Layer)
- **User**: User entity with authentication fields
- **LostFoundItem**: Complete lost & found item with relationships

#### Services (Business Logic)
- **S3Service**: AWS S3 integration for image management
- **UserDetailsServiceImpl**: Spring Security user authentication

#### Security
- **SecurityConfig**: JWT authentication, CORS, endpoint protection
- **JwtUtils**: Token generation, validation, and extraction

#### DTOs (Data Transfer)
- **Request DTOs**: Input validation and data binding
- **Response DTOs**: Structured API responses

## 🔧 Configuration Management

### Application Properties Structure
```properties
# Server & Application
server.port=8080
spring.application.name=smart-campus-backend

# Database (PostgreSQL)
spring.datasource.*

# JPA & Hibernate
spring.jpa.*

# Security (JWT)
jwt.secret=<secret-key>
jwt.expiration=86400

# AWS S3
aws.access-key-id=<access-key>
aws.secret-access-key=<secret-key>
aws.region=us-east-1
aws.s3.bucket-name=<bucket-name>

# CORS
spring.web.cors.*
```

### Security Configuration
- **JWT Authentication**: Stateless authentication with tokens
- **Password Encoding**: BCrypt for secure password storage
- **CORS**: Configured for frontend integration
- **Endpoint Protection**: Role-based access control

### Database Configuration
- **Primary**: PostgreSQL for production
- **Connection Pooling**: HikariCP for optimal performance
- **JPA**: Hibernate ORM with automatic schema management

## 🧪 Available Scripts

```bash
# Development
./mvnw spring-boot:run              # Start development server
./mvnw spring-boot:run -Dspring.profiles.active=dev

# Build & Package
./mvnw clean compile                # Compile source code
./mvnw clean package                # Create JAR file
./mvnw clean install                # Install to local repository

# Testing
./mvnw test                         # Run unit tests
./mvnw integration-test             # Run integration tests
./mvnw verify                       # Run all tests and checks

# Code Quality
./mvnw spotbugs:check              # Static analysis
./mvnw checkstyle:check            # Code style verification

# Database
./mvnw flyway:migrate              # Run database migrations
./mvnw jpa:schema-export           # Export database schema

# Maintenance
./mvnw dependency:tree             # Show dependency tree
./mvnw versions:display-updates    # Check for updates
./mvnw clean                       # Clean build artifacts
```

## 🐛 Troubleshooting

### Common Issues and Solutions

#### 1. Application Won't Start
```bash
# Check Java version
java -version
# Should be 17 or higher

# Check if port 8080 is in use
netstat -an | grep 8080
# or on Windows
netstat -an | findstr 8080

# Kill process on port 8080
# On Unix/Mac
lsof -ti:8080 | xargs kill -9
# On Windows
netstat -ano | findstr :8080
taskkill /PID <PID> /F
```

#### 2. Database Connection Issues
```bash
# Test PostgreSQL connection
psql -h localhost -p 5433 -U postgres -d smart_campus_db

# Check database is running
docker ps  # if using Docker
# or
pg_ctl status  # if using local PostgreSQL
```

#### 3. JWT Token Issues
```bash
# Check JWT configuration
# Ensure jwt.secret is set and sufficiently long (>256 bits)
# Verify jwt.expiration is set correctly
```

#### 4. S3 Upload Failures
```bash
# Verify AWS credentials
aws configure list
# or check application.properties

# Test S3 connectivity
aws s3 ls s3://your-bucket-name
```

#### 5. CORS Issues
```bash
# Check CORS configuration in application.properties
# Ensure frontend URL is in allowed-origins
# Verify allowed-methods include required HTTP methods
```

#### 6. Memory Issues
```bash
# Increase JVM memory
java -Xms512m -Xmx2g -jar target/smart-campus-backend-1.0.0.jar

# Or set JAVA_OPTS
export JAVA_OPTS="-Xms512m -Xmx2g"
./mvnw spring-boot:run
```

### 📊 Performance Optimization

#### Database Optimization
```properties
# Connection pool tuning
spring.datasource.hikari.maximum-pool-size=20
spring.datasource.hikari.minimum-idle=5
spring.datasource.hikari.connection-timeout=20000

# JPA optimization
spring.jpa.properties.hibernate.jdbc.batch_size=25
spring.jpa.properties.hibernate.order_inserts=true
spring.jpa.properties.hibernate.order_updates=true
```

#### JVM Tuning
```bash
# Production JVM settings
-server
-Xms1g
-Xmx2g
-XX:+UseG1GC
-XX:MaxGCPauseMillis=200
-XX:+UseStringDeduplication
```

## 🚀 Deployment

### 📦 Building for Production

#### Create Production JAR
```bash
./mvnw clean package -Dmaven.test.skip=true
# Creates: target/smart-campus-backend-1.0.0.jar
```

#### Docker Deployment
```dockerfile
FROM openjdk:17-jre-slim

COPY target/smart-campus-backend-1.0.0.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app.jar"]
```

```bash
# Build Docker image
docker build -t smart-campus-backend .

# Run container
docker run -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=prod \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://host:5432/db \
  smart-campus-backend
```

#### Environment Variables for Production
```bash
# Required environment variables
export SPRING_PROFILES_ACTIVE=prod
export SPRING_DATASOURCE_URL=jdbc:postgresql://prod-host:5432/smart_campus_db
export SPRING_DATASOURCE_USERNAME=postgres
export SPRING_DATASOURCE_PASSWORD=secure_password
export JWT_SECRET=very_long_and_secure_jwt_secret_key_for_production
export AWS_ACCESS_KEY_ID=your_aws_access_key
export AWS_SECRET_ACCESS_KEY=your_aws_secret_key
```

### 🔒 Security Best Practices

#### Production Security Checklist
- [ ] Change default JWT secret
- [ ] Use environment variables for sensitive data
- [ ] Enable HTTPS/TLS in production
- [ ] Configure proper CORS origins
- [ ] Set up database connection encryption
- [ ] Use strong database passwords
- [ ] Enable security headers
- [ ] Configure proper logging levels
- [ ] Set up monitoring and alerting

#### Security Headers Configuration
```properties
# Security headers (add to SecurityConfig)
security.headers.frame-options=DENY
security.headers.content-type-options=nosniff
security.headers.xss-protection=1; mode=block
security.headers.hsts=max-age=31536000; includeSubDomains
```

## 📈 Monitoring & Logging

### Health Checks
```bash
# Application health
curl http://localhost:8080/actuator/health

# Detailed health (if enabled)
curl http://localhost:8080/actuator/health/details
```

### Logging Configuration
```properties
# Logging levels
logging.level.root=INFO
logging.level.com.smartcampus=DEBUG
logging.level.org.springframework.security=DEBUG

# Log file
logging.file.name=logs/smart-campus.log
logging.pattern.file=%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n
```

### Metrics & Monitoring
```bash
# Application metrics
curl http://localhost:8080/actuator/metrics
curl http://localhost:8080/actuator/metrics/jvm.memory.used
curl http://localhost:8080/actuator/metrics/http.server.requests
```

## 🤝 Contributing

### Development Workflow
1. Create feature branch from `main`
2. Follow naming convention: `feature/description`
3. Write tests for new functionality
4. Ensure all tests pass
5. Update documentation as needed
6. Create pull request with clear description

### Code Style Guidelines
- Follow Java naming conventions
- Use meaningful variable and method names
- Add JavaDoc for public methods
- Keep methods focused and concise
- Use Spring Boot best practices

### Testing Guidelines
- Write unit tests for service layer
- Create integration tests for controllers
- Use test profiles for testing configuration
- Mock external dependencies
- Maintain high test coverage

## 📄 License

This project is developed for academic purposes as part of university coursework at University of Moratuwa.

## 👥 Support & Contact

For issues and questions related to this academic project:
- Create GitHub issue for bugs/features
- Contact development team for urgent issues
- Follow university guidelines for academic support

---

**Smart Campus Companion Backend** - Enhancing university life through technology 🎓