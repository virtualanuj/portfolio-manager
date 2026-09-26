# Portfolio Manager

Personal, single-user portfolio tracker (stocks/ETFs, funds, crypto). Phase 1 runs locally. See `docs/` for intent, spec, plan and standards.

## Prerequisites
- JDK 21 (`JAVA_HOME=/opt/homebrew/opt/openjdk@21` on this Mac; `/usr/bin/java` is only a stub)
- Node 22 LTS via nvm (`nvm use` reads `.nvmrc`)
- Docker Desktop running
- Gradle is provided by the wrapper (`./gradlew`)

## Commands
```
source scripts/env.sh        # JDK 21 + Node 22
docker compose up -d db      # Postgres 16 on 127.0.0.1:5432
```

## Spring Boot 4 note
The API pins Spring Boot 4.1.1. Boot 4 uses split starters (for example `spring-boot-starter-webmvc`, `spring-boot-starter-flyway`) and Jackson 3 (`tools.jackson.*`). Boot 3 examples may not apply.
