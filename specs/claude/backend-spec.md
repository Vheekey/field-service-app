# Backend Spec — FieldOps Spring Boot 3

---

## Project Structure (Maven Multi-Module)

```
fieldops-backend/
├── pom.xml                         (parent)
├── fieldops-api/                   (REST + WebSocket controllers, DTOs)
│   └── src/main/java/io/fieldops/api/
│       ├── auth/
│       │   ├── AuthController.java
│       │   └── JwtService.java
│       ├── task/
│       │   ├── TaskController.java
│       │   └── TaskWebSocketController.java
│       ├── shift/
│       │   └── ShiftController.java
│       ├── worker/
│       │   └── WorkerController.java
│       └── common/
│           ├── GlobalExceptionHandler.java
│           ├── ErrorResponse.java
│           └── PageResponse.java
├── fieldops-domain/                (Entities, repositories, services)
│   └── src/main/java/io/fieldops/domain/
│       ├── task/
│       │   ├── Task.java
│       │   ├── TaskRepository.java
│       │   ├── TaskService.java
│       │   └── TaskStatusHistoryService.java
│       ├── shift/
│       │   ├── Shift.java
│       │   ├── ShiftRepository.java
│       │   └── ShiftService.java
│       ├── worker/
│       │   ├── Worker.java
│       │   ├── WorkerRepository.java
│       │   └── WorkerService.java
│       └── common/
│           ├── BaseEntity.java
│           └── ConflictResolutionService.java
├── fieldops-infrastructure/        (WebSocket config, Redis, email, scheduling)
│   └── src/main/java/io/fieldops/infra/
│       ├── websocket/
│       │   ├── WebSocketConfig.java
│       │   └── TaskEventPublisher.java
│       ├── security/
│       │   ├── SecurityConfig.java
│       │   └── JwtAuthFilter.java
│       ├── cache/
│       │   └── RedisConfig.java
│       └── scheduling/
│           └── LocationPingCleanupJob.java
└── fieldops-migration/             (Flyway SQL scripts)
    └── src/main/resources/db/migration/
```

---

## Key Dependencies (pom.xml)

```xml
<!-- Core -->
spring-boot-starter-web
spring-boot-starter-websocket
spring-boot-starter-data-jpa
spring-boot-starter-security
spring-boot-starter-validation
spring-boot-starter-actuator

<!-- Database -->
postgresql
flyway-core

<!-- Auth -->
jjwt-api + jjwt-impl + jjwt-jackson

<!-- Caching -->
spring-boot-starter-data-redis
spring-boot-starter-cache

<!-- Rate limiting -->
bucket4j-spring-boot-starter

<!-- Metrics -->
micrometer-registry-prometheus

<!-- Testing -->
spring-boot-starter-test
testcontainers (postgresql, redis modules)
```

---

## Task Entity (JPA)

```java
@Entity
@Table(name = "tasks")
@EntityListeners(AuditingEntityListener.class)
public class Task {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_type_id", nullable = false)
    private TaskType taskType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vehicle_id")
    private Vehicle vehicle;

    @Column(nullable = false)
    private String title;

    private String description;

    @Column(nullable = false)
    private Integer urgency;  // 1–5

    @Column(nullable = false)
    private BigDecimal lat;

    @Column(nullable = false)
    private BigDecimal lng;

    private String addressHint;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TaskStatus status = TaskStatus.PENDING;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_to")
    private Worker assignedTo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shift_id")
    private Shift shift;

    private OffsetDateTime dueBy;
    private OffsetDateTime completedAt;

    @Version
    private Long version;              // Optimistic locking

    @CreatedBy
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private Worker createdBy;

    @CreatedDate
    private OffsetDateTime createdAt;

    @LastModifiedDate
    private OffsetDateTime updatedAt;

    private OffsetDateTime deletedAt;
}
```

---

## TaskService — Core Business Logic

```java
@Service
@Transactional
public class TaskService {

    // Status transition guard
    private static final Map<TaskStatus, Set<TaskStatus>> ALLOWED_TRANSITIONS = Map.of(
        TaskStatus.PENDING,      Set.of(ASSIGNED, CANCELLED),
        TaskStatus.ASSIGNED,     Set.of(IN_PROGRESS, PENDING, CANCELLED),
        TaskStatus.IN_PROGRESS,  Set.of(COMPLETED, ISSUE),
        TaskStatus.ISSUE,        Set.of(ASSIGNED, CANCELLED),
        TaskStatus.COMPLETED,    Set.of(),
        TaskStatus.CANCELLED,    Set.of()
    );

    public Task transitionStatus(UUID taskId, PatchStatusRequest req, Worker actor) {
        Task task = taskRepository.findById(taskId)
            .orElseThrow(() -> new NotFoundException("Task not found"));

        // Optimistic lock check is handled by JPA @Version —
        // if req.version != task.version, JPA throws OptimisticLockException → 409

        validateTransition(task.getStatus(), req.getStatus());
        validateActorPermission(task, req.getStatus(), actor);

        TaskStatus prev = task.getStatus();
        task.setStatus(req.getStatus());

        if (req.getStatus() == TaskStatus.COMPLETED) {
            task.setCompletedAt(OffsetDateTime.now());
        }

        Task saved = taskRepository.save(task);

        // Write audit history
        statusHistoryService.record(saved, prev, req.getStatus(), actor, req.getNote(),
                                    req.getLat(), req.getLng());

        // Push WebSocket event
        eventPublisher.publishStatusChanged(saved, actor);

        return saved;
    }

    public Task assignTask(UUID taskId, UUID workerId, Worker dispatcher) {
        // Pessimistic lock for assignment to prevent double-assignment
        Task task = taskRepository.findByIdWithLock(taskId)  // SELECT ... FOR UPDATE
            .orElseThrow(() -> new NotFoundException("Task not found"));

        if (task.getAssignedTo() != null && !task.getAssignedTo().getId().equals(workerId)) {
            throw new AlreadyAssignedException("Task already assigned to another worker");
        }

        Worker worker = workerRepository.findById(workerId)
            .orElseThrow(() -> new NotFoundException("Worker not found"));

        task.setAssignedTo(worker);
        task.setStatus(TaskStatus.ASSIGNED);

        Task saved = taskRepository.save(task);

        // Push to worker
        eventPublisher.publishTaskAssigned(saved);

        return saved;
    }
}
```

**Note on locking strategy**:
- **Assignment** uses a short-lived **pessimistic lock** (`SELECT FOR UPDATE`) because the window for a race condition is tight (two dispatchers clicking assign simultaneously) and must be atomic.
- **Task updates** use **optimistic locking** (`@Version`) because conflicts are infrequent and the penalty of a retry is acceptable.

---

## WebSocket Configuration

```java
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // Use Redis-backed broker relay for multi-instance support
        registry.enableStompBrokerRelay("/topic", "/queue")
                .setRelayHost("redis")
                .setRelayPort(6379);
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")
                .withSockJS();
    }
}
```

```java
@Component
public class TaskEventPublisher {

    private final SimpMessagingTemplate messaging;

    public void publishTaskAssigned(Task task) {
        TaskEvent event = TaskEvent.builder()
            .event("task.assigned")
            .taskId(task.getId())
            .payload(TaskSummaryDto.from(task))
            .timestamp(OffsetDateTime.now())
            .build();

        // Send to specific worker's personal queue
        messaging.convertAndSendToUser(
            task.getAssignedTo().getId().toString(),
            "/queue/tasks",
            event
        );

        // Also broadcast to dispatcher topic
        messaging.convertAndSend("/topic/dispatch", event);
    }
}
```

---

## JWT Security

```java
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            chain.doFilter(request, response);
            return;
        }

        String token = header.substring(7);
        try {
            Claims claims = jwtService.parseAccessToken(token);
            UsernamePasswordAuthenticationToken auth = buildAuth(claims);
            SecurityContextHolder.getContext().setAuthentication(auth);
        } catch (JwtException e) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        chain.doFilter(request, response);
    }
}
```

WebSocket JWT validation on STOMP CONNECT:
```java
@Component
public class WebSocketAuthInterceptor implements ChannelInterceptor {
    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            String token = accessor.getFirstNativeHeader("Authorization");
            // Validate token and set principal
            Principal principal = jwtService.validateAndExtract(token);
            accessor.setUser(principal);
        }
        return message;
    }
}
```

---

## Redis Caching

```java
@Service
public class TaskService {

    @Cacheable(value = "tasks", key = "#workerId + ':' + #shiftId")
    public Page<TaskSummaryDto> getTasksForWorker(UUID workerId, UUID shiftId, Pageable pageable) {
        // DB query
    }

    @CacheEvict(value = "tasks", allEntries = true)
    public Task transitionStatus(...) {
        // Evict all task caches on any mutation
    }
}
```

Cache TTL: 10 seconds (configured in `application.yml`). Short enough for near-real-time freshness, long enough to absorb burst reads.

---

## Rate Limiting (Bucket4j)

```java
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private final LoadingCache<String, Bucket> buckets = Caffeine.newBuilder()
        .expireAfterAccess(1, TimeUnit.HOURS)
        .build(key -> Bucket.builder()
            .addLimit(Bandwidth.classic(100, Refill.greedy(100, Duration.ofMinutes(1))))
            .build());

    @Override
    protected void doFilterInternal(HttpServletRequest req, ...) {
        String key = extractWorkerIdOrIp(req);
        Bucket bucket = buckets.get(key);
        if (bucket.tryConsume(1)) {
            chain.doFilter(req, response);
        } else {
            response.setStatus(429);
            response.getWriter().write("{\"error\":{\"code\":\"RATE_LIMITED\"}}");
        }
    }
}
```

---

## Exception Handling

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleOptimisticLock(Exception ex) {
        return ResponseEntity.status(409)
            .body(ErrorResponse.of("TASK_CONFLICT",
                "Task was modified by another user. Please reload."));
    }

    @ExceptionHandler(InvalidStatusTransitionException.class)
    public ResponseEntity<ErrorResponse> handleInvalidTransition(InvalidStatusTransitionException ex) {
        return ResponseEntity.unprocessableEntity()
            .body(ErrorResponse.of("INVALID_STATUS_TRANSITION", ex.getMessage()));
    }

    @ExceptionHandler(AlreadyAssignedException.class)
    public ResponseEntity<ErrorResponse> handleAlreadyAssigned(AlreadyAssignedException ex) {
        return ResponseEntity.status(409)
            .body(ErrorResponse.of("ALREADY_ASSIGNED", ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        // Collect field errors
        return ResponseEntity.badRequest()
            .body(ErrorResponse.of("VALIDATION_ERROR", buildDetails(ex)));
    }
}
```

---

## Location Ping Cleanup Job

```java
@Component
public class LocationPingCleanupJob {

    @Scheduled(cron = "0 0 2 * * *")   // 2am daily
    @Transactional
    public void purgeOldPings() {
        OffsetDateTime cutoff = OffsetDateTime.now().minusDays(30);
        locationRepository.deleteByPingedAtBefore(cutoff);
    }
}
```

---

## Key application.yml Settings

```yaml
spring:
  datasource:
    url: jdbc:postgresql://${DB_HOST}:5432/fieldops
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      connection-timeout: 20000
  jpa:
    open-in-view: false              # Always disable — avoid N+1 via lazy loading in view
    properties:
      hibernate:
        default_batch_fetch_size: 20 # Batch lazy loads
  cache:
    type: redis
  data:
    redis:
      host: ${REDIS_HOST}
      port: 6379

jwt:
  secret: ${JWT_SECRET}
  access-token-expiry-seconds: 900
  refresh-token-expiry-days: 7

management:
  endpoints:
    web:
      exposure:
        include: health, prometheus, info
  metrics:
    export:
      prometheus:
        enabled: true
```

---

## Docker Compose (local dev)

```yaml
version: '3.9'
services:
  postgres:
    image: postgres:16
    environment:
      POSTGRES_DB: fieldops
      POSTGRES_USER: fieldops
      POSTGRES_PASSWORD: dev_secret
    ports:
      - "5432:5432"
    volumes:
      - pg_data:/var/lib/postgresql/data

  redis:
    image: redis:7-alpine
    ports:
      - "6379:6379"

  app:
    build: .
    depends_on: [postgres, redis]
    environment:
      DB_HOST: postgres
      REDIS_HOST: redis
      JWT_SECRET: dev_jwt_secret_change_in_prod
    ports:
      - "8080:8080"

volumes:
  pg_data:
```
