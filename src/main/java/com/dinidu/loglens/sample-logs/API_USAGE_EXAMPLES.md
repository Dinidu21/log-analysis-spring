# Log Analysis API Usage Examples

## Authentication
The API uses OAuth2 authentication with Google and GitHub. Users must authenticate before accessing any endpoints.

### Login URLs:
- Google: `GET /oauth2/authorization/google`
- GitHub: `GET /oauth2/authorization/github`

## Core Endpoints

### 1. Upload Log File
```bash
POST /api/logs/upload
Content-Type: multipart/form-data

curl -X POST "http://localhost:8080/api/logs/upload" \
  -H "Content-Type: multipart/form-data" \
  -F "file=@application.log" \
  --cookie "JSESSIONID=your-session-id"
```

**Response:**
```json
{
  "stats": {
    "fileName": "application.log",
    "fileSize": 2048,
    "totalLines": 45,
    "processedLines": 45,
    "anomaliesDetected": 8,
    "errorCount": 0,
    "startTime": "2024-01-15T10:30:00",
    "endTime": "2024-01-15T10:30:15",
    "processingDuration": "PT15S",
    "anomalyPercentage": 17.78,
    "successRate": 100.0
  },
  "logEntries": [...],
  "success": true
}
```

### 2. Get All Logs (Paginated)
```bash
GET /api/logs?page=0&size=20

curl "http://localhost:8080/api/logs?page=0&size=20" \
  --cookie "JSESSIONID=your-session-id"
```

### 3. Get Anomalies Only
```bash
GET /api/logs/anomalies?page=0&size=10

curl "http://localhost:8080/api/logs/anomalies?page=0&size=10" \
  --cookie "JSESSIONID=your-session-id"
```

**Response:**
```json
{
  "content": [
    {
      "id": 123,
      "timestamp": "2024-01-15T14:22:15",
      "logMessage": "Database connection timeout after 30 seconds",
      "embedding": [0.1, 0.2, 0.3, ...],
      "isAnomaly": true,
      "explanation": "This error indicates a critical database connectivity issue that could lead to system downtime. The timeout suggests network problems or database overload.",
      "similarityScore": 0.15,
      "logLevel": "ERROR"
    }
  ],
  "totalElements": 8,
  "totalPages": 1,
  "size": 10,
  "number": 0
}
```

### 4. Get User Statistics
```bash
GET /api/logs/stats

curl "http://localhost:8080/api/logs/stats" \
  --cookie "JSESSIONID=your-session-id"
```

**Response:**
```json
{
  "totalLogs": 150,
  "totalAnomalies": 12,
  "anomalyPercentage": 8.0
}
```

### 5. Get Specific Log Entry
```bash
GET /api/logs/{id}

curl "http://localhost:8080/api/logs/123" \
  --cookie "JSESSIONID=your-session-id"
```

### 6. Delete Log Entry
```bash
DELETE /api/logs/{id}

curl -X DELETE "http://localhost:8080/api/logs/123" \
  --cookie "JSESSIONID=your-session-id"
```

### 7. Delete All User Logs
```bash
DELETE /api/logs/all

curl -X DELETE "http://localhost:8080/api/logs/all" \
  --cookie "JSESSIONID=your-session-id"
```

## Health Check Endpoints

### Application Health
```bash
GET /api/health
```

### AI Service Health
```bash
GET /api/ai/health
```

### User Authentication Status
```bash
GET /api/auth/status
GET /api/auth/user
```

## Configuration Parameters

### Application Properties
- `log.processing.anomaly-threshold=0.2` - Cosine similarity threshold for anomaly detection
- `log.processing.batch-size=50` - Number of logs processed in each batch
- `log.processing.max-similar-logs=5` - Maximum similar logs used for explanation context
- `ai.service.base-url=http://localhost:8001` - Python AI service URL
- `ai.service.timeout=30000` - AI service request timeout in milliseconds

### File Upload Limits
- Maximum file size: 10MB
- Maximum request size: 10MB
- Supported formats: Text files (.log, .txt, etc.)

## Error Handling

The API returns standardized error responses:

```json
{
  "error": "ERROR_CODE",
  "message": "Human readable error message",
  "timestamp": "2024-01-15T10:30:00",
  "suggestion": "Suggested action for user"
}
```

### Common Error Codes:
- `AI_SERVICE_ERROR` - AI service unavailable or error
- `NETWORK_ERROR` - Network connectivity issues
- `LOG_PROCESSING_ERROR` - Error processing log file
- `VALIDATION_ERROR` - Request validation failed
- `INVALID_ARGUMENT` - Invalid parameters provided

## Processing Flow

1. User uploads log file via `/api/logs/upload`
2. System parses log file line by line
3. For each log entry:
    - Extracts timestamp, log level, and message
    - Calls AI service to generate embedding
    - Compares with baseline embeddings for anomaly detection
    - If anomaly detected, calls AI service for explanation
    - Stores processed entry in database
4. Returns processing statistics and results

## Sample Log Formats Supported

The system supports various log formats:
- Standard: `2024-01-15 10:30:45 [INFO] Message`
- ISO: `2024-01-15T10:30:45Z [INFO] Message`
- Syslog: `Jan 15 10:30:45 [INFO] Message`
- Simple: `[INFO] Message`

## Security Features

- OAuth2 authentication with Google and GitHub
- Session-based authentication
- User isolation (users can only see their own logs)
- CORS configuration for frontend integration
- Input validation and sanitization