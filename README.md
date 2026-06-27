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
