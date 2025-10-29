# Logs Directory

This directory contains application log files for Hiyori.

## Log Files

- `hiyori.log` - Current log file
- `hiyori.log.1`, `hiyori.log.2`, etc. - Rotated log files

## Logging Configuration

Hiyori uses SLF4J with Logback for logging (via `slf4j-nop` in runtime, which suppresses logs in production).

### Development Logging

During development, if you want to see detailed logs:

1. Replace `slf4j-nop` with `logback-classic` in `build.gradle.kts`:
   ```kotlin
   // Remove: runtimeOnly("org.slf4j:slf4j-nop:2.0.13")
   // Add: runtimeOnly("ch.qos.logback:logback-classic:1.4.14")
   ```

2. Create `src/main/resources/logback.xml`:
   ```xml
   <configuration>
       <appender name="FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
           <file>logs/hiyori.log</file>
           <rollingPolicy class="ch.qos.logback.core.rolling.TimeBasedRollingPolicy">
               <fileNamePattern>logs/hiyori.log.%d{yyyy-MM-dd}.gz</fileNamePattern>
               <maxHistory>30</maxHistory>
               <totalSizeCap>100MB</totalSizeCap>
           </rollingPolicy>
           <encoder>
               <pattern>%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n</pattern>
           </encoder>
       </appender>
       
       <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
           <encoder>
               <pattern>%d{HH:mm:ss.SSS} %-5level %logger{36} - %msg%n</pattern>
           </encoder>
       </appender>
       
       <root level="INFO">
           <appender-ref ref="FILE" />
           <appender-ref ref="CONSOLE" />
       </root>
       
       <!-- More verbose logging for specific packages -->
       <logger name="app.db" level="DEBUG" />
   </configuration>
   ```

## Log Levels

The application uses the following log levels:

- **ERROR** - Critical errors that require attention
- **WARN** - Warning messages about potential issues
- **INFO** - General informational messages
- **DEBUG** - Detailed debugging information

## Log Rotation

When using Logback with the configuration above:
- Logs rotate daily
- Compressed after rotation (`.gz`)
- Keep 30 days of history
- Maximum total size: 100MB

## Viewing Logs

To view recent logs:
```bash
tail -f logs/hiyori.log
```

To search logs:
```bash
grep "ERROR" logs/hiyori.log
```

To view compressed logs:
```bash
zcat logs/hiyori.log.2024-01-15.gz
```

## Troubleshooting

If logs are not being created:
1. Check that the `logs/` directory exists and is writable
2. Check logback configuration in `src/main/resources/logback.xml`
3. Verify that `logback-classic` is included in dependencies (not `slf4j-nop`)

## Development

For development, this directory is ignored by Git (see `.gitignore`).
Only `README.md` is tracked to preserve the directory structure.
