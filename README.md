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

## Server Deploy

Build the jar locally:

```bash
mvn clean package
```

Upload the jar to the server:

```bash
scp -i <private-key.pem> target/teaching-sys-backend-0.0.1-SNAPSHOT.jar <user>@<server-ip>:/tmp/teaching-sys.jar
```

Move it into the deploy directory:

```bash
sudo mkdir -p /opt/teaching-sys /data/teaching-sys/logs /data/teaching-sys/uploads
sudo mv /tmp/teaching-sys.jar /opt/teaching-sys/teaching-sys.jar
```

Run it with limited memory:

```bash
cd /opt/teaching-sys
nohup java -Xms128m -Xmx512m -jar teaching-sys.jar > /data/teaching-sys/logs/app.log 2>&1 &
```

Verify locally on the server:

```bash
curl http://127.0.0.1:8080/api/health
```

If Nginx proxies `/api/` to `127.0.0.1:8080`, verify from a browser or local terminal:

```bash
curl http://<server-ip>/api/health
```

## Auth Login

Initialize the minimal user table before testing login against MySQL:

```bash
mysql -u <user> -p <database> < src/main/resources/db/init-auth.sql
```

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

Demo accounts inserted by `init-auth.sql`:

| Account | Password | Role |
| --- | --- | --- |
| `student001` | `123456` | `STUDENT` |
| `teacher001` | `123456` | `TEACHER` |
| `admin001` | `123456` | `EDU_ADMIN` |

