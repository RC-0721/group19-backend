# group19-backend

第19组后端仓库。

## Runtime

- JDK 17
- Maven 3.8+
- Spring Boot 2.7.x

## Local Run

```bash
mvn spring-boot:run
```

Health check:

```bash
curl http://127.0.0.1:8080/api/health
```

Expected response:

```json
{
  "code": "0",
  "message": "success",
  "data": {
    "status": "UP",
    "service": "teaching-sys-backend"
  }
}
```

## Package

```bash
mvn clean package
```

The jar file is generated under `target/` and must not be committed.

## Standalone Deploy From Repository

Clone this repository on the target machine:

```bash
git clone https://github.com/RC-0721/group19-backend.git
cd group19-backend
```

Prepare MySQL. The application reads database connection settings from environment variables.

```bash
mysql -u root -p
```

```sql
CREATE DATABASE teaching_sys DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'teaching_user'@'localhost' IDENTIFIED BY '<password>';
GRANT ALL PRIVILEGES ON teaching_sys.* TO 'teaching_user'@'localhost';
FLUSH PRIVILEGES;
```

Initialize tables and seed data:

```bash
mysql -u teaching_user -p teaching_sys < src/main/resources/db/init-auth.sql
mysql -u teaching_user -p teaching_sys < src/main/resources/db/init-business.sql
```

Configure runtime variables:

```bash
export MYSQL_HOST=127.0.0.1
export MYSQL_PORT=3306
export MYSQL_DATABASE=teaching_sys
export MYSQL_USERNAME=teaching_user
export MYSQL_PASSWORD=<password>
export TEACHING_UPLOAD_DIR=/data/teaching-sys/uploads
```

Build and run:

```bash
mvn clean package
sudo mkdir -p /data/teaching-sys/logs /data/teaching-sys/uploads
java -Xms128m -Xmx512m -jar target/teaching-sys-backend-0.0.1-SNAPSHOT.jar
```

For background execution:

```bash
nohup java -Xms128m -Xmx512m -jar target/teaching-sys-backend-0.0.1-SNAPSHOT.jar > /data/teaching-sys/logs/app.log 2>&1 &
```

Verify the local API:

```bash
curl http://127.0.0.1:8080/api/health
```

## Auth Login

Required environment variables for runtime database access:

```bash
MYSQL_HOST=127.0.0.1
MYSQL_PORT=3306
MYSQL_DATABASE=teaching_sys
MYSQL_USERNAME=teaching_user
MYSQL_PASSWORD=<password>
```

Login request:

```bash
curl -X POST http://127.0.0.1:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"account":"student001","password":"123456","role":"STUDENT"}'
```

Seed accounts from `src/main/resources/db/init-auth.sql`:

| Account | Password | Role |
| --- | --- | --- |
| `student001` | `123456` | `STUDENT` |
| `teacher001` | `123456` | `TEACHER` |
| `admin001` | `123456` | `EDU_ADMIN` |

## Database Init

Use these scripts to initialize a standalone deployment database. Run them against the target MySQL database before starting the application.

```bash
mysql -u <user> -p <database> < src/main/resources/db/init-auth.sql
mysql -u <user> -p <database> < src/main/resources/db/init-business.sql
```

Recommended runtime database variables:

```bash
export MYSQL_HOST=127.0.0.1
export MYSQL_PORT=3306
export MYSQL_DATABASE=teaching_sys
export MYSQL_USERNAME=<user>
export MYSQL_PASSWORD=<password>
```

Then start the application and verify the local API:

```bash
mvn spring-boot:run
curl http://127.0.0.1:8080/api/health
```

Notes:

- `init-auth.sql` creates the user table and seed accounts.
- `init-business.sql` creates business tables and seed data.
- Run the scripts on a new or disposable database for first-time setup. Back up existing data before rerunning them on a non-empty database.

## Backup And Restore

```powershell
$env:MYSQL_PASSWORD = "<password>"
.\scripts\backup_teaching_sys.ps1 -DbHost 127.0.0.1 -DbName teaching_sys -DbUser teaching_user
.\scripts\restore_teaching_sys_backup.ps1 -SqlFile data/backups/teaching_sys_YYYYMMDD_HHMMSS.sql -ConfirmRestore
```
