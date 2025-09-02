# University Companion Backend

Spring Boot REST API backend for the University Companion application - L3 Individual Project.

## Features

- JWT-based authentication system
- User registration and login endpoints
- Spring Security integration
- H2 in-memory database
- RESTful API design
- CORS configuration for frontend integration

## Tech Stack

- Java 17+
- Spring Boot 3.x
- Spring Security
- Spring Data JPA
- H2 Database
- JWT Authentication
- Maven

## API Endpoints

### Authentication
- `POST /api/auth/signin` - User login
- `POST /api/auth/signup` - User registration

### Health Check
- `GET /api/health` - Application health status

### User Management
- `GET /api/users/profile` - Get user profile (requires JWT token)

## Getting Started

### Prerequisites
- Java 17 or higher
- Maven 3.6+

### Running the Application

```bash
./mvnw spring-boot:run
```

On Windows:
```bash
mvnw.cmd spring-boot:run
```

The API will be available at `http://localhost:8080`

## Configuration

Database and application settings are configured in `src/main/resources/application.properties`

## Project Structure

```
src/
├── main/
│   ├── java/com/smartcampus/
│   │   ├── SmartCampusApplication.java
│   │   ├── config/
│   │   │   └── SecurityConfig.java
│   │   ├── controller/
│   │   │   ├── AuthController.java
│   │   │   ├── HealthController.java
│   │   │   └── UserController.java
│   │   ├── dto/
│   │   │   ├── JwtResponse.java
│   │   │   ├── LoginRequest.java
│   │   │   └── SignupRequest.java
│   │   ├── model/
│   │   │   └── User.java
│   │   ├── repository/
│   │   │   └── UserRepository.java
│   │   ├── security/
│   │   │   └── JwtUtils.java
│   │   └── service/
│   │       ├── UserDetailsServiceImpl.java
│   │       └── UserPrincipal.java
│   └── resources/
│       └── application.properties
└── test/
```