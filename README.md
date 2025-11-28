# Smart University Companion - Backend

A comprehensive Spring Boot REST API backend for the Smart University Companion application - L3 Individual Project at University of Moratuwa.

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

### 💰 Financial Aid System (Production Ready)
- **Application Management** - Complete CRUD operations for financial aid applications
- **Multiple Aid Types** - Scholarships, grants, loans, emergency aid, work-study
- **Admin Panel** - Review, approve, reject applications
- **Status Tracking** - Pending, under review, approved, rejected, disbursed
- **Document Support** - Attach supporting documents to applications
- **Donation System** - Community donations for financial aid programs
- **Statistics & Analytics** - Track application metrics and funding

### 🔔 Notification System (Production Ready)
- **Real-time Notifications** - WebSocket-based instant notifications
- **Multiple Types** - System, financial aid, lost & found, academic alerts
- **Priority Management** - High, medium, low priority notifications
- **Read/Unread Tracking** - User notification status management
- **Bulk Operations** - Mark all as read, delete all read notifications
- **User-specific Notifications** - Targeted notifications per user

### 🚨 Emergency Notification System (Production Ready)
- **Emergency Alerts** - Critical notifications for campus-wide emergencies
- **Real-time Broadcasting** - WebSocket-based instant emergency alerts
- **Acknowledgment Tracking** - Track which users have seen and acknowledged alerts
- **Admin Dashboard** - Statistics on emergency notification reach and engagement
- **Seen/Dismissed Tracking** - Separate tracking for views and dismissals
- **Expiration Management** - Auto-expire emergency notifications
- **Targeted Alerts** - Send to all students or specific user groups
- **Browser Notifications** - Native browser notification support

### 👥 Admin Management System (Production Ready)
- **User Management** - Complete CRUD operations for users
- **Dashboard Statistics** - Real-time metrics and analytics
- **Bulk Operations** - Enable/disable/delete multiple users
- **Password Management** - Admin password reset functionality
- **Role-based Access** - Admin and user role management
- **User Status Control** - Enable/disable user accounts

### 🗄️ Database Integration
- **PostgreSQL Database** - Production-ready relational database
- **JPA/Hibernate ORM** - Object-relational mapping
- **Connection Pooling** - Optimized database connections
- **Data Persistence** - Reliable data storage and retrieval

### 🌐 WebSocket Support
- **Real-time Communication** - WebSocket integration for live updates
- **STOMP Protocol** - Message broker for pub/sub messaging
- **Notification Broadcasting** - Real-time notification delivery

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

### Real-time & Messaging
- **Spring WebSocket**: Real-time bidirectional communication
- **STOMP**: Simple Text Oriented Messaging Protocol
- **Message Broker**: In-memory broker for pub/sub

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

### 👥 Admin Management Endpoints

#### Get Dashboard Statistics
```http
GET /api/admin/dashboard/stats
Authorization: Bearer {jwt_token}
Response: {
  "totalUsers": 150,
  "activeUsers": 120,
  "inactiveUsers": 30,
  "adminUsers": 5,
  "regularUsers": 145
}
```

#### Get All Users
```http
GET /api/admin/users?page=0&size=10
Authorization: Bearer {jwt_token}
```

#### Get User by ID
```http
GET /api/admin/users/{id}
Authorization: Bearer {jwt_token}
```

#### Update User
```http
PUT /api/admin/users/{id}
Authorization: Bearer {jwt_token}
Content-Type: application/json
{
  "firstName": "John",
  "lastName": "Doe",
  "email": "john@example.com",
  "role": "ADMIN"
}
```

#### Delete User
```http
DELETE /api/admin/users/{id}
Authorization: Bearer {jwt_token}
```

#### Toggle User Status
```http
PATCH /api/admin/users/{id}/toggle-status
Authorization: Bearer {jwt_token}
```

#### Reset User Password
```http
PATCH /api/admin/users/{id}/reset-password
Authorization: Bearer {jwt_token}
Content-Type: application/json
{
  "newPassword": "newPassword123"
}
```

#### Bulk User Actions
```http
POST /api/admin/users/bulk-action
Authorization: Bearer {jwt_token}
Content-Type: application/json
{
  "action": "DELETE",
  "userIds": [1, 2, 3]
}
```

### 💰 Financial Aid Endpoints

#### Get All Applications
```http
GET /api/financial-aid/applications?status=PENDING&type=SCHOLARSHIP
Authorization: Bearer {jwt_token}
```

#### Get User's Applications
```http
GET /api/financial-aid/applications/user/{userId}
Authorization: Bearer {jwt_token}
```

#### Get Single Application
```http
GET /api/financial-aid/applications/{id}
Authorization: Bearer {jwt_token}
```

#### Submit Application
```http
POST /api/financial-aid/applications
Authorization: Bearer {jwt_token}
Content-Type: application/json
{
  "aidType": "SCHOLARSHIP",
  "amount": 5000.00,
  "reason": "Need financial assistance for tuition",
  "academicYear": "2024/2025",
  "cgpa": 3.75,
  "familyIncome": 50000.00,
  "documents": ["document1.pdf", "document2.pdf"]
}
```

#### Update Application
```http
PUT /api/financial-aid/applications/{id}
Authorization: Bearer {jwt_token}
Content-Type: application/json
```

#### Cancel Application
```http
DELETE /api/financial-aid/applications/{id}
Authorization: Bearer {jwt_token}
```

#### Get Statistics
```http
GET /api/financial-aid/stats
Response: {
  "totalApplications": 200,
  "pendingApplications": 50,
  "approvedApplications": 100,
  "rejectedApplications": 30,
  "totalAmountRequested": 500000.00,
  "totalAmountApproved": 300000.00
}
```

#### Admin: Review Application
```http
POST /api/financial-aid/admin/applications/{id}/review
Authorization: Bearer {jwt_token}
Content-Type: application/json
{
  "status": "APPROVED",
  "reviewNotes": "Application meets all criteria",
  "approvedAmount": 5000.00
}
```

#### Admin: Get All Applications
```http
GET /api/financial-aid/admin/applications?page=0&size=20
Authorization: Bearer {jwt_token}
```

### 🔔 Notification Endpoints

#### Get User Notifications
```http
GET /api/notifications/user/{userId}?page=0&size=20
Authorization: Bearer {jwt_token}
Response: [
  {
    "id": 1,
    "userId": 1,
    "type": "FINANCIAL_AID",
    "title": "Application Approved",
    "message": "Your scholarship application has been approved",
    "priority": "HIGH",
    "read": false,
    "createdAt": "2024-01-15T10:30:00"
  }
]
```

#### Get Unread Count
```http
GET /api/notifications/user/{userId}/unread/count
Authorization: Bearer {jwt_token}
Response: 5
```

#### Mark Notification as Read
```http
PUT /api/notifications/{id}/read
Authorization: Bearer {jwt_token}
```

#### Mark All as Read
```http
PUT /api/notifications/user/{userId}/read-all
Authorization: Bearer {jwt_token}
```

#### Delete Notification
```http
DELETE /api/notifications/{id}
Authorization: Bearer {jwt_token}
```

#### Delete All Read Notifications
```http
DELETE /api/notifications/user/{userId}/read
Authorization: Bearer {jwt_token}
```

#### Create Notification (Admin)
```http
POST /api/notifications
Authorization: Bearer {jwt_token}
Content-Type: application/json
{
  "userId": 1,
  "type": "SYSTEM",
  "title": "System Maintenance",
  "message": "Scheduled maintenance on Sunday",
  "priority": "HIGH"
}
```

### 🌐 WebSocket Endpoints
```
CONNECT /ws
SUBSCRIBE /topic/notifications/{userId}
SEND /app/notifications
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

### 🔧 Setup Endpoints
```http
POST /api/setup/init
Content-Type: application/json
{
  "adminEmail": "admin@example.com",
  "adminPassword": "securePassword123"
}
Response: "Admin user created successfully"
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
CREATE DATABASE smart_university_db;
CREATE USER postgres WITH ENCRYPTED PASSWORD 'your_password';
GRANT ALL PRIVILEGES ON DATABASE smart_university_db TO postgres;
```

**Option B: Docker PostgreSQL**
```bash
docker run --name postgres-university \
  -e POSTGRES_DB=smart_university_db \
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
spring.application.name=smart-university-backend

# Database Configuration
spring.datasource.url=jdbc:postgresql://localhost:5433/smart_university_db
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
java -jar target/smart-university-backend-1.0.0.jar
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
logging.level.com.smartuniversity=DEBUG
```

#### Production Environment
```properties
spring.profiles.active=prod
spring.jpa.hibernate.ddl-auto=validate     # Don't auto-modify schema
spring.jpa.show-sql=false
logging.level.root=WARN
logging.level.com.smartuniversity=INFO
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
│   ├── java/com/smartuniversity/
│   │   ├── SmartUniversityApplication.java    # Main Spring Boot application
│   │   ├── config/
│   │   │   └── SecurityConfig.java        # Security & CORS configuration
│   │   ├── controller/                    # REST API Controllers
│   │   │   ├── AdminController.java       # Admin user management
│   │   │   ├── AuthController.java        # Authentication endpoints
│   │   │   ├── FinancialAidController.java          # Financial aid applications
│   │   │   ├── FinancialAidAdminController.java     # Financial aid admin panel
│   │   │   ├── HealthController.java      # Health check endpoint
│   │   │   ├── ImageUploadController.java # S3 image upload/serve
│   │   │   ├── LostFoundController.java   # Lost & Found CRUD operations
│   │   │   ├── NotificationController.java # Notification management
│   │   │   ├── SetupController.java       # Initial setup endpoint
│   │   │   └── UserController.java        # User management
│   │   ├── dto/                          # Data Transfer Objects
│   │   │   ├── JwtResponse.java          # JWT login response
│   │   │   ├── LoginRequest.java         # Login request payload
│   │   │   ├── LostFoundItemRequest.java # Item creation request
│   │   │   ├── LostFoundItemResponse.java # Item response format
│   │   │   └── SignupRequest.java        # Registration request
│   │   ├── model/                        # JPA Entity Models
│   │   │   ├── FinancialAid.java         # Financial aid application entity
│   │   │   ├── FinancialAidDonation.java # Donation entity
│   │   │   ├── LostFoundItem.java        # Lost & Found item entity
│   │   │   ├── Notification.java         # Notification entity
│   │   │   └── User.java                 # User entity
│   │   ├── repository/                   # Data Access Layer
│   │   │   ├── FinancialAidRepository.java      # Financial aid data access
│   │   │   ├── FinancialAidDonationRepository.java # Donation data access
│   │   │   ├── LostFoundItemRepository.java # Lost & Found data access
│   │   │   ├── NotificationRepository.java  # Notification data access
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
    └── smart-university-backend-1.0.0.jar  # Executable JAR
```

### 📋 Key Components Explanation

#### Controllers (REST API Layer)
- **AdminController**: Admin user management, dashboard statistics
- **AuthController**: JWT authentication, login/register endpoints
- **FinancialAidController**: User financial aid applications
- **FinancialAidAdminController**: Admin review and approval of applications
- **LostFoundController**: Complete CRUD operations for lost & found items
- **ImageUploadController**: S3 image upload, serving, and deletion
- **NotificationController**: Real-time notification management
- **SetupController**: Initial system setup and admin creation
- **UserController**: User profile management
- **HealthController**: Application health monitoring

#### Models (Data Layer)
- **User**: User entity with authentication fields and roles
- **LostFoundItem**: Complete lost & found item with relationships
- **FinancialAid**: Financial aid application with status tracking
- **FinancialAidDonation**: Community donation records
- **Notification**: User notifications with priority and type

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
spring.application.name=smart-university-backend

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
psql -h localhost -p 5433 -U postgres -d smart_university_db

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
java -Xms512m -Xmx2g -jar target/smart-university-backend-1.0.0.jar

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

### 🌐 Production Environment

#### Production Stack
- **Platform**: Heroku (Cloud PaaS)
- **Database**: PostgreSQL (Heroku Postgres)
- **Storage**: AWS S3
- **Java Runtime**: OpenJDK 17
- **Profile**: `spring.profiles.active=heroku`

#### Heroku Environment Variables
```bash
# Required production environment variables
CORS_ALLOWED_ORIGINS=<your_frontend_url>
DATABASE_URL=<postgres_connection_string>
AWS_ACCESS_KEY_ID=<your_aws_access_key>
AWS_SECRET_ACCESS_KEY=<your_aws_secret_key>
AWS_REGION=<your_aws_region>
AWS_S3_BUCKET_NAME=<your_bucket_name>
JWT_SECRET=<your_production_jwt_secret>
JWT_EXPIRATION=86400
```

#### Deployment Process
```bash
# 1. Ensure code is committed
git add .
git commit -m "Update backend"

# 2. Push to GitHub (triggers Heroku deployment)
git push origin main

# 3. Heroku automatically:
#    - Detects Java/Maven project
#    - Runs Maven build
#    - Uses Procfile to start app with production profile
#    - Loads environment variables
```

#### Heroku Configuration Files
- **Procfile**: `web: java -Dserver.port=$PORT -Dspring.profiles.active=heroku -jar target/*.jar`
- **system.properties**: `java.runtime.version=17`
- **application-heroku.properties**: Production-specific Spring configuration

### 📦 Building for Production

#### Create Production JAR
```bash
./mvnw clean package -Dmaven.test.skip=true
# Creates: target/smart-university-backend-1.0.0.jar
```

#### Docker Deployment
```dockerfile
FROM openjdk:17-jre-slim

COPY target/smart-university-backend-1.0.0.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app.jar"]
```

```bash
# Build Docker image
docker build -t smart-university-backend .

# Run container
docker run -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=prod \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://host:5432/db \
  smart-university-backend
```

#### Environment Variables for Production
```bash
# Required environment variables
export SPRING_PROFILES_ACTIVE=heroku
export DATABASE_URL=<postgres_connection_string>
export CORS_ALLOWED_ORIGINS=<your_frontend_url>
export JWT_SECRET=<very_long_and_secure_jwt_secret_key>
export JWT_EXPIRATION=86400
export AWS_ACCESS_KEY_ID=<your_aws_access_key>
export AWS_SECRET_ACCESS_KEY=<your_aws_secret_key>
export AWS_REGION=<your_aws_region>
export AWS_S3_BUCKET_NAME=<your_bucket_name>
```

### 🔍 Production Monitoring

#### Health Check
```bash
# Check production API status
curl <your_production_api_url>/api/health

# Expected response
{"status":"UP","timestamp":"2024-01-15T10:30:00Z"}
```

#### CORS Verification
```bash
# Test CORS headers
curl -H "Origin: <your_frontend_url>" \
     -H "Access-Control-Request-Method: POST" \
     -H "Access-Control-Request-Headers: Content-Type" \
     -X OPTIONS \
     <your_production_api_url>/api/auth/signin -v

# Should include:
# Access-Control-Allow-Origin: <your_frontend_url>
# Access-Control-Allow-Credentials: true
```

#### View Heroku Logs
```bash
# Real-time logs
heroku logs --tail --app <your_app_name>

# Last 100 lines
heroku logs -n 100 --app <your_app_name>

# Filter by source
heroku logs --source app --app <your_app_name>
```

### 💰 Production Costs (Estimated)
- **Heroku Dyno**: Free tier or Eco ($5/month)
- **PostgreSQL Database**: $0-10/month depending on plan
- **AWS S3**: Pay-as-you-go (~$0.50-$2/month for typical usage)
- **Total Estimated**: $5-15/month depending on configuration

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
logging.level.com.smartuniversity=DEBUG
logging.level.org.springframework.security=DEBUG

# Log file
logging.file.name=logs/smart-university.log
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

**Smart University Companion Backend** - Enhancing university life through technology 🎓