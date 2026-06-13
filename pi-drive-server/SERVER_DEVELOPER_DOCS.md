# Pi-Drive Server — Developer Documentation

> **Audience:** A developer rewriting this server in Python Flask who wants to understand the existing design, its intentional choices, its known limitations, and where it was headed.

---

## Table of Contents

1. [Project Purpose](#project-purpose)
2. [Technology Stack](#technology-stack)
3. [Source Code Layout](#source-code-layout)
4. [Architecture Overview](#architecture-overview)
5. [API Reference](#api-reference)
6. [Data Models](#data-models)
7. [InfluxDB Schema Design](#influxdb-schema-design)
8. [Key Design Decisions (and their tradeoffs)](#key-design-decisions-and-their-tradeoffs)
9. [Modularity: What Exists vs. What Was Planned](#modularity-what-exists-vs-what-was-planned)
10. [Known Bugs and TODOs](#known-bugs-and-todos)
11. [Configuration and Deployment](#configuration-and-deployment)
12. [Rewrite Guidance for Flask](#rewrite-guidance-for-flask)

---

## Project Purpose

Pi-Drive is a car telemetry logging system. A Raspberry Pi reads OBD-II data from a vehicle over Bluetooth, buffers it locally, and then syncs it to this server when the Pi is on the home WiFi. The server's only jobs are:

1. Maintain a registry of known cars.
2. Accept telemetry data for a registered car and write it to InfluxDB.
3. Expose that data (via InfluxDB → Grafana) for visualization and vehicle health monitoring.

The server is intentionally minimal. It is not a data analysis engine — Grafana handles visualization directly against InfluxDB.

---

## Technology Stack

| Layer | Technology |
|---|---|
| Language | Java 23 |
| Framework | Spring Boot 3.3.5 |
| REST Hypermedia | Spring HATEOAS (HAL format) |
| Database | InfluxDB v2 (via `influxdb-client-java 7.2.0`) |
| Serialization | Jackson (with custom deserializers) |
| Build | Gradle |
| Runtime | Docker |
| Reverse Proxy (production) | Traefik |

The H2 in-memory database is listed as a dependency but is **not used for actual persistence**. It was pulled in because `Car` and `Telemetry` use JPA `@Entity` annotations (a leftover from the Spring Initializr template). No SQL database is active at runtime. All persistent state lives in InfluxDB.

---

## Source Code Layout

```
server/src/main/java/ghart/space/server/
│
├── TelemetryApplication.java       # Entry point. Starts InfluxDB factory then Spring.
├── InfluxDBConnectionFactory.java  # Singleton config/factory for all InfluxDB connections.
│
├── apiController/
│   ├── APIController.java          # All HTTP route handlers (the only @RestController).
│   └── CarModelAssembler.java      # Adds HAL _links to Car responses.
│
├── car/
│   ├── Car.java                    # Car entity + custom JSON deserializer.
│   ├── CarDBHelper.java            # ALL database access: car registry AND telemetry writes.
│   ├── CarJsonComponent.java       # Empty file, placeholder.
│   ├── CarNotFoundAdvice.java      # Global exception handler: 404 for CarNotFoundException.
│   └── CarNotFoundException.java   # Checked exception for missing car lookups.
│
└── telemetry/
    └── Telemetry.java              # Telemetry entity + custom JSON deserializer.
```

---

## Architecture Overview

```
HTTP Request
     │
     ▼
APIController          (routes, validates car exists, delegates)
     │
     ▼
CarDBHelper            (all InfluxDB read/write logic)
     │
     ▼
InfluxDBConnectionFactory  (creates InfluxDBClient from env vars)
     │
     ▼
InfluxDB               (sole persistent datastore)
```

There is no service layer between the controller and the database helper. The controller calls `CarDBHelper` directly. This is the most significant structural gap to address in a rewrite.

### Request/Response Flow: Register a Car

```
POST /cars  {make, model, year}
  → Car.Deserializer parses JSON → new Car(make, model, year)
  → Car.hashCode() generates deterministic integer ID
  → CarDBHelper.saveNewCar() writes a placeholder InfluxDB point (rpm=0.0) with car tags
  → CarModelAssembler wraps Car in HAL EntityModel with self + collection links
  → HTTP 201 Created, body: HAL Car JSON
```

### Request/Response Flow: Log Telemetry

```
POST /cars/{id}/telemetry  {timeStamp, tags:[...], fields:[...]}
  → APIController looks up car by id (CarDBHelper.findById)
  → If not found → CarNotFoundException → 404
  → Telemetry.Deserializer parses JSON
  → CarDBHelper.logTelemetry() builds InfluxDB line protocol string and writes it
  → HTTP 201 Created, body: HAL Car JSON for the parent car
```

---

## API Reference

The API follows REST Level 3 (Hypermedia / HAL). Every response includes a `_links` object with navigation URLs. This is intentional — the Pi client can discover endpoints dynamically rather than having them hardcoded.

### `GET /cars`

Returns all registered cars.

**Response `200 OK`:**
```json
{
  "_embedded": {
    "carList": [
      {
        "id": 123456789,
        "make": "Honda",
        "model": "Fit",
        "year": 2007,
        "_links": {
          "self": { "href": "http://host/cars/123456789" },
          "cars": { "href": "http://host/cars" }
        }
      }
    ]
  },
  "_links": {
    "self": { "href": "http://host/cars" }
  }
}
```

**Implementation note:** Queries InfluxDB for all unique `id` tag values within the last 1 year. This range is hardcoded — see [Known Bugs](#known-bugs-and-todos).

---

### `POST /cars`

Register a new car. The server generates the car's ID — do not send an `id` field.

**Request body:**
```json
{
  "make": "Honda",
  "model": "Fit",
  "year": 2007
}
```

**Response `201 Created`:** HAL Car object (same format as above), with `Location` header pointing to the new car's URL.

**ID generation:** `hash(make, model, year) & 0xfffffff` — a 28-bit positive integer. This means you cannot register two cars with the same make/model/year. See [Design Decisions](#key-design-decisions-and-their-tradeoffs).

**Implementation note:** Creates a placeholder InfluxDB data point with `rpm=0.0` to anchor the car's tags. There is no separate car registry table.

---

### `GET /cars/{id}`

Retrieve a single registered car by its integer ID.

**Response `200 OK`:** HAL Car object.

**Response `404 Not Found`:** Plain text error message string.

---

### `POST /cars/{id}/telemetry`

Log a telemetry reading for a car.

**Request body:**
```json
{
  "timeStamp": "1730343829",
  "tags": [
    { "name": "someTagKey", "value": "someTagValue" }
  ],
  "fields": [
    { "name": "rpm",         "type": "long", "value": 2000 },
    { "name": "throttlePos", "type": "float", "value": 0 }
  ]
}
```

**Field notes:**
- `timeStamp`: Unix epoch seconds as a string. If empty string `""`, server uses `Instant.now()`.
- `tags`: Optional extra InfluxDB tags. The car's `make`, `model`, `year`, and `id` are always added automatically from the registered car record.
- `fields`: InfluxDB field key-value pairs. **The `type` field is parsed from JSON but all values are cast to `long`.** Float values will be truncated. This is a known bug.

**Response `201 Created`:** HAL Car object for the parent car (not the telemetry point itself). The location header points to the car, not the telemetry. This is a limitation — there is no way to retrieve individual telemetry points via the API; that is left to Grafana.

**Response `404 Not Found`:** If the car `id` does not exist.

---

### Intentionally Omitted Endpoints

These were commented out deliberately:

- `PUT /cars/{id}` — Car entries are immutable once written. Make/model/year define the car's identity and are used as InfluxDB tags on every telemetry point. Allowing updates would corrupt historical data. If you want to fix a typo, delete and re-register the car.
- `DELETE /cars/{id}` — Deletion from InfluxDB requires deleting all points with that `id` tag. This was left as a future feature.
- `GET /cars/{id}/telemetry` — No query endpoint for telemetry exists. Grafana reads InfluxDB directly. This was an intentional scope decision.

---

## Data Models

### Car

| Field | Type | Notes |
|---|---|---|
| `id` | `int` | `hash(make+model+year) & 0xfffffff` |
| `make` | `String` | e.g. `"Honda"` |
| `model` | `String` | e.g. `"Fit"` |
| `yr` | `int` | Named `yr` in code because `year` is a reserved SQL keyword. Serialized as `year` in JSON. |

### Telemetry

| Field | Type | Notes |
|---|---|---|
| `timeStamp` | `String` | ISO 8601 or Unix epoch string |
| `tags` | `List<Tag>` | `Tag(name: String, value: String)` |
| `fields` | `List<Field>` | `Field(name: String, type: String, value: long)` |

The `Tag` and `Field` types map directly to InfluxDB's data model. The caller is responsible for knowing what tags and fields are valid OBD-II channels. The server does no schema enforcement on telemetry content.

---

## InfluxDB Schema Design

All data lives in a single measurement (configurable via `INFLUXDB_MEASUREMENT` env var, typically `carTelemetry`).

**Tags** (indexed, string, low-cardinality):
- `id` — car integer ID
- `make` — car make
- `model` — car model
- `year` — model year
- Any additional tags sent in the telemetry payload

**Fields** (unindexed, numeric, high-cardinality):
- `rpm`, `throttlePos`, and any other OBD-II channels

**Why one measurement?** Grouping all car telemetry in a single measurement makes cross-car queries simpler in Grafana. The `id` tag is the discriminator between vehicles.

**Why tags for car identity?** InfluxDB is designed for tag-based filtering. Storing `make`/`model`/`year`/`id` as tags means Grafana can filter dashboards by car without any joins. This is the correct InfluxDB schema approach.

**Car registration bootstrap:** Because InfluxDB only stores data points, a car is "registered" by writing a dummy point `(rpm=0.0)` with the car's tags. The car registry is reconstructed at query time by finding unique `id` tag values. There is no separate metadata store.

---

## Key Design Decisions (and their tradeoffs)

### 1. InfluxDB as the sole datastore

**Decision:** Store everything — including the car registry — in InfluxDB. No relational database.

**Why:** Simplifies the stack to one service. Avoids schema migrations. Car metadata is naturally attached to every telemetry point as InfluxDB tags anyway.

**Tradeoff:** Car lookups require a time-range query against InfluxDB, which is slower and more fragile than a key-value lookup. The `findAllCars` query has a hardcoded `-1y` range, meaning cars with no activity in over a year will silently disappear from the registry.

**Flask rewrite suggestion:** Add a lightweight SQLite database for the car registry. Keep InfluxDB for telemetry only. This is a clean separation and makes car lookups trivially fast.

---

### 2. Hash-based Car ID

**Decision:** Car ID = `hash(make, model, year) & 0xfffffff`.

**Why:** No separate auto-increment sequence needed. The ID is deterministic — if the Pi knows the make/model/year it can predict the car's ID without querying the server first.

**Tradeoff:** Only one car per make/model/year. Two 2007 Honda Fits cannot coexist. Hash collisions, while unlikely, are possible.

**Flask rewrite suggestion:** Use UUID4 for the car ID, or accept a VIN as the unique identifier. The Pi-side sync script would need a one-time lookup to get the car's ID before sending telemetry.

---

### 3. Telemetry Schema is Caller-Defined

**Decision:** The server does not know or enforce which OBD-II channels are valid. The Pi sends whatever fields it has, and the server writes them verbatim to InfluxDB.

**Why:** OBD-II support varies by vehicle. Not every car exposes every channel. A rigid schema would require updating the server every time the Pi records a new data channel.

**Tradeoff:** No validation. Typos in field names (`thrtlPos` vs `throttlePos`) will silently create separate InfluxDB series. The `type` field in the JSON payload is parsed but ignored — all values are stored as `long`.

**Flask rewrite suggestion:** Keep the flexible schema approach (it is correct for this use case), but fix the type handling. Support `long`, `float`, `string`, and `boolean` field types. Map the `type` string from the payload to the appropriate Python/InfluxDB type before writing.

---

### 4. HAL/HATEOAS Hypermedia

**Decision:** Responses include `_links` navigation per the HAL specification.

**Why:** True REST (Level 3 Richardson Maturity) means clients navigate the API via hyperlinks rather than hardcoded URLs. The Pi's sync script could, in theory, start from a root URL and discover all endpoints.

**Tradeoff:** Adds complexity and a library dependency. The current Pi sync script does not actually use the `_links` — it hardcodes the endpoint URLs. But the infrastructure is there.

**Flask rewrite suggestion:** Preserve the `_links` structure in responses. It is not hard to build manually in Flask, or use a library like `flask-hal` or simply construct the dict by hand. This keeps the API properly RESTful.

---

### 5. No Service Layer

**Decision:** `APIController` calls `CarDBHelper` directly. There is no intermediate service/business-logic layer.

**Why:** The application is simple enough that a service layer felt like over-engineering for an MVP.

**Tradeoff:** The controller is coupled to the database implementation. Testing requires mocking the DB. Adding caching, validation, or async writes means editing the controller.

**Flask rewrite suggestion:** Add a thin service layer (`car_service.py`, `telemetry_service.py`) even if each function is currently a one-liner. It gives you a clean place to add validation, caching, and business rules later without touching route handlers.

---

### 6. Line Protocol String Building for Telemetry Writes

**Decision:** `CarDBHelper.logTelemetry()` manually constructs an InfluxDB line protocol string via string concatenation and calls `writeRecord()`.

**Why:** Provides full control over the exact InfluxDB point structure, including ordering tags in a specific way and injecting the car's make/model/year alongside caller-provided tags.

**Tradeoff:** Fragile. String concatenation for a data write protocol is an injection vector and a maintenance burden. The InfluxDB Java client has a `Point` builder API (used correctly in `saveNewCar`) that should have been used here too.

**Flask rewrite suggestion:** Use the `influxdb-client-python` library's `Point` builder. Build the point object programmatically, add car tags first, then iterate over the payload's tags and fields. Never concatenate user-supplied values into a query or write string directly.

---

## Modularity: What Exists vs. What Was Planned

### What Exists

The code is split into three packages that reflect a clean conceptual separation:

```
apiController/   ← HTTP concerns only (routing, serialization, HAL)
car/             ← Car entity, car DB access, car error handling
telemetry/       ← Telemetry entity and deserialization
```

This is a reasonable module boundary. The `apiController` package does not know about InfluxDB. The `car` package does not know about HTTP.

### What Is Broken

**`CarDBHelper` does too much.** It handles both:
1. Car registry operations (`findAllCars`, `findById`, `saveNewCar`)
2. Telemetry write operations (`logTelemetry`)

The telemetry write logic belongs in a `TelemetryDBHelper` (or equivalent). The comment in `CarDBHelper` acknowledges this. As the system grows — adding more telemetry query endpoints, for example — this class would become a monolith.

### What Was Planned (inferred from TODOs and comments)

| Feature | Status | Notes |
|---|---|---|
| Query telemetry endpoint | Not started | `GET /cars/{id}/telemetry` with time range params |
| Delete car endpoint | Scaffolded (commented out) | `DELETE /cars/{id}` |
| VIN-based car ID | TODO in code | Replace hash with VIN |
| Float field support | TODO in code | `Field<T>` generic type was planned, never finished |
| Input sanitization | README TODO | Uniform casing, escape checking |
| Pi sync health check | README TODO | `GET /cars/{id}/telemetry/latest` for the Pi to determine sync offset |
| Spring config binding | TODO in code | Replace manual env var parsing in `InfluxDBConnectionFactory` with `@ConfigurationProperties` |
| InfluxDB connection pool | TODO in code | Currently opens/closes a new client per request |

---

## Known Bugs and TODOs

These are confirmed bugs identified in the source code:

1. **String comparison using `==`** — `CarDBHelper.logTelemetry()` uses `if(tag == "make")` and `telemetry.getTimeStamp() == ""`. In Java, `==` compares object identity, not value. These comparisons may silently fail. Use `.equals()`.

2. **All field values cast to `long`** — `Telemetry.Field.value` is typed as `long`. Float/double OBD-II readings (throttle position: `0.2`) will be truncated to `0`. The `type` field in the JSON is stored but not used during deserialization.

3. **`findAllCars` range is hardcoded to 1 year** — Cars not seen in over a year will not appear in `GET /cars`. The range should be configurable or `0` (all time).

4. **No InfluxDB connection pooling** — `InfluxDBConnectionFactory.create()` opens a new client on every request. Under load this will exhaust connections. The client should be a singleton or managed connection pool.

5. **`logTelemetry` calls `findById` internally** — When the controller calls `logTelemetry`, the car has already been looked up once. `logTelemetry` looks it up again to get make/model/year. Two InfluxDB round-trips per telemetry write.

6. **No validation on car registration** — Empty string make/model, year of `0`, or other nonsense values are silently accepted.

7. **Telemetry response returns parent car, not the telemetry point** — `POST /cars/{id}/telemetry` responds with the car's HAL entity, not a confirmation of what was written. There is no way to verify what was actually stored.

8. **`CarJsonComponent.java` is empty** — Dead file, can be deleted.

---

## Configuration and Deployment

### Environment Variables

All configuration is via environment variables (no `application.properties` entries beyond the app name):

| Variable | Required | Description |
|---|---|---|
| `INFLUXDB_TOKEN` | One of these two | InfluxDB API token as a plain string |
| `INFLUXDB_TOKEN_FILE` | One of these two | Path to a file containing the token (for Docker secrets) |
| `INFLUXDB_URL` | Yes | e.g. `http://influxdb:8086` |
| `INFLUXDB_ORG` | Yes | InfluxDB organization name |
| `INFLUXDB_BUCKET` | Yes | InfluxDB bucket name |
| `INFLUXDB_MEASUREMENT` | Yes | InfluxDB measurement name (e.g. `carTelemetry`) |

If any required variable is missing, the application prints an error and calls `System.exit(0)` before Spring starts.

### Docker

The `sample-compose-yml` is the reference deployment. It runs two containers:
- `pidrive-server` — the Spring Boot API on port 8080
- `influxdb` — InfluxDB on port 8086 with named volumes for persistence

The production `docker-compose.yml` connects to an external Traefik network for TLS termination and DNS routing. For local development, use `sample-compose-yml`.

### Build

```bash
cd server/server
./gradlew clean bootJar          # produces build/libs/*.jar
docker build . -t telemetry-server
```

The `Dockerfile` uses `eclipse-temurin:23-jre` and copies the pre-built jar. The `CMD` line in the Dockerfile (`gradlew.bat clean bootJar`) is a dead line — it is overridden by the `ENTRYPOINT`.

---

## Rewrite Guidance for Flask

### Recommended Module Structure

```
server/
├── app.py                    # Flask app factory, register blueprints
├── config.py                 # Load and validate env vars at startup
├── influxdb_client.py        # Singleton InfluxDB client wrapper
├── blueprints/
│   ├── cars.py               # /cars routes
│   └── telemetry.py          # /cars/<id>/telemetry routes
├── services/
│   ├── car_service.py        # Car business logic (create, find, list)
│   └── telemetry_service.py  # Telemetry write logic
├── models/
│   ├── car.py                # Car dataclass/schema
│   └── telemetry.py          # Telemetry dataclass/schema
└── errors.py                 # Error handlers (404, 400, etc.)
```

### HAL Responses in Flask

Build `_links` manually — it is just a dict:

```python
def car_to_hal(car: Car, base_url: str) -> dict:
    return {
        **car.__dict__,
        "_links": {
            "self": {"href": f"{base_url}/cars/{car.id}"},
            "cars": {"href": f"{base_url}/cars"},
        }
    }
```

### InfluxDB Python Client

```python
from influxdb_client import InfluxDBClient, Point, WritePrecision
from influxdb_client.client.write_api import SYNCHRONOUS

client = InfluxDBClient(url=INFLUXDB_URL, token=INFLUXDB_TOKEN, org=INFLUXDB_ORG)
write_api = client.write_api(write_options=SYNCHRONOUS)

# Write telemetry
point = (Point(MEASUREMENT)
    .tag("id", str(car_id))
    .tag("make", car.make)
    .tag("model", car.model)
    .tag("year", str(car.year)))

for tag in telemetry.tags:
    point = point.tag(tag["name"], tag["value"])

for field in telemetry.fields:
    value = cast_field_value(field["type"], field["value"])
    point = point.field(field["name"], value)

point = point.time(telemetry.timestamp, WritePrecision.S)
write_api.write(bucket=INFLUXDB_BUCKET, record=point)
```

### Priority Fixes for the Rewrite

1. **Fix field types** — map `"float"` → `float()`, `"long"` → `int()`, `"string"` → `str()` before writing.
2. **Fix the car registry** — use SQLite (via SQLAlchemy) so car lookups do not require InfluxDB queries.
3. **Add a `GET /cars/{id}/telemetry` endpoint** — with `?start=` and `?end=` query params. The Pi needs `GET /cars/{id}/telemetry/latest` to know the last synced timestamp.
4. **Add input validation** — use `marshmallow` or `pydantic` to validate request bodies.
5. **Fix the car ID** — use `uuid.uuid4()` or accept VIN as the primary key.
6. **Use the Point builder** — never concatenate user input into a write string.
