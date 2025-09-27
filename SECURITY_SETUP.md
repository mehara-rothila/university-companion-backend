# Security Setup

## Database Configuration

1. Copy `application.properties.template` to `application.properties`
2. Update the following values in `application.properties`:
   - `DB_PASSWORD`: Your PostgreSQL password
   - `JWT_SECRET`: A secure random string for JWT signing

## Environment Variables (Optional)

You can also set these as environment variables:

```bash
export DB_USERNAME=postgres
export DB_PASSWORD=your_secure_password
export JWT_SECRET=your_very_secure_random_string_here
```

## Important Security Notes

- **NEVER** commit `application.properties` to git
- Use strong passwords for database connections
- Generate a cryptographically secure JWT secret
- Change default credentials in production

## Local Development

For local development, the template file has safe defaults.
Simply copy it to `application.properties` and update the password.