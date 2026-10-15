# Local startup

Use JDK 21. Choose one backend on port 8081, not both Docker and Maven.

## Docker backend

When `vegeai-backend` is running, the API is already at http://localhost:8081.
Do not also run `mvnw spring-boot:run` on that port.

## Maven backend

Keep PostgreSQL and Redis running. Stop only the backend container:

```powershell
docker compose stop backend
$env:JAVA_HOME = 'C:\Program Files\Microsoft\jdk-21.0.12.101-hotspot'
.\mvnw.cmd -Pstandalone clean spring-boot:run
```

Configure local DB/Redis credentials and `GEMINI_API_KEY` in an ignored `.env`
or process environment. Never commit these values.

The `standalone` profile builds into `target/standalone`, separate from the
Java language server's `target/classes`. It also disables DevTools restart.
This avoids loading partial Lombok classes emitted while VS Code and Maven
compile the same files (`Invalid method Code length 0`, `User$UserBuilder`).

For an isolated verification build:

```powershell
.\mvnw.cmd -Pstandalone clean verify
```

If the IDE output itself is corrupted, run VS Code's Java: Clean Java Language
Server Workspace command. The standalone build does not depend on that output.
