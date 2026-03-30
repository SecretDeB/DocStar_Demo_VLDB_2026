# AGENTS.md

This file provides guidance to WARP (warp.dev) when working with code in this repository.

## Project Overview

DocStar is a **keyword-based document indexing middleware** that uses **Shamir's Secret Sharing** and a **multi-server architecture** to provide privacy-preserving document search and retrieval. The middleware sits between a frontend client (React) and 3-4 backend secret-sharing servers, orchestrating cryptographic protocols for secure keyword search, file upload, and access control.

## Build and Run Commands

The project uses Maven with the wrapper (`mvnw`/`mvnw.cmd`). All commands should be run from the `middleware/` directory.

- **Build**: `.\mvnw.cmd package -DskipTests`
- **Run tests**: `.\mvnw.cmd test`
- **Run single test**: `.\mvnw.cmd test -Dtest=ClassName#methodName`
- **Run the application**: `.\mvnw.cmd spring-boot:run` (or `java -Xms1g -Xmx16g -jar target/docstar_middleware.jar`)
- **Clean build**: `.\mvnw.cmd clean package -DskipTests`

The output JAR is `middleware/target/docstar_middleware.jar`. JVM is configured with `-Xms1g -Xmx16g` for large dataset handling.

## Prerequisites

- **Java 21**
- **MySQL** on localhost:3306 with database `docstar` (user: `root`, password: `docstar`). Run `INITIALIZE.sql` at the repo root to set up schema.
- **3-4 secret-sharing servers** must be running on ports 8881-8884 (configured in `src/main/resources/config/Common.properties`). The middleware will fail to start without them because `DBOCache` and `DocumentService` connect at `@PostConstruct` time.

## Architecture

### Three-Phase Cryptographic Search Protocol

The core search flow operates in three phases between the middleware and the secret-sharing servers:

1. **Phase 1 (Keyword Lookup)**: The search keyword is converted to a numeric representation, padded, then split into Shamir shares. Shares are sent to 3 servers, which return their shares of the Access Control Table (ACT). The middleware interpolates results to find the keyword's index. This also checks if the client has access.

2. **Phase 2 (File ID Retrieval)**: A keyword vector (one-hot at the keyword index) is created, shared, and sent to servers. Servers return shares of the inverted index (ADDR table + OPT inverted index). Interpolation yields the file IDs matching the keyword.

3. **Phase 3 (File Retrieval)**: File vectors are created for requested file IDs, shared, and sent to servers. The returned encrypted file content shares are interpolated and decrypted (numeric string → plaintext).

### Two Distinct Roles: DBO vs SearchingClient

- **`DBO`** (Database Owner): A Spring `@Service` that manages the index — adding files, adding keywords, granting/revoking access, resizing data structures. Connects to servers via `DBOConnection` (sends `DBORequest` wrappers). Operates as an admin channel.

- **`SearchingClient`**: Instantiated per-connection in a pool of 10 (managed by `DocumentService`). Handles the 3-phase search protocol for end users. Connects to servers via `ServerConnection` (sends `ClientRequest` wrappers). Each has its own persistent socket connections.

Both use Shamir's Secret Sharing and Lagrange interpolation but have separate connection and request types.

### Service Layer

- **`DBOService`**: Orchestrates admin operations (file upload, keyword management, access control) through the `DBO` instance. Manages temporary file storage (`ConcurrentHashMap`) during the multi-step upload workflow (extract → stats → finalize).
- **`DBOCache`**: In-memory cache initialized at startup via `@PostConstruct`. Caches client-keyword access map, keyword list, and document/client/keyword counts. Must stay in sync with server-side state.
- **`DocumentService`**: Manages the `SearchingClient` pool with round-robin assignment. Maps sessions to clients for stateful multi-step searches.
- **`AuthService`**: Handles login/signup, distinguishing between `Client` and `Admin` roles. On signup, calls `dbo.addNewClient()` to register the new client on the secret-sharing servers.

### Key Configuration Files

- `src/main/resources/config/Common.properties`: Server IPs/ports, thread count, optRow/optCol dimensions, verification flags. This is the primary tuning file.
- `src/main/resources/config/Outsource.properties`: Parameters for the initial data outsourcing/indexing (checkpoint sizes, keyword max frequency, bin counts).
- `src/main/resources/config/DBO.properties`: DBO seed value.
- `src/main/resources/application.properties`: Spring Boot config (MySQL, server port 8080, file upload limits up to 1GB/2GB).

### Keyword Processing Pipeline

Keywords are extracted via `ExtractKeywordsUtil`, which tokenizes text, filters by length (4-19 chars, alpha-only), removes stopwords (loaded from `stopwords.txt` via `StopwordLoader`), and stems using Snowball/Porter stemmer. The `Constant` class defines all crypto and indexing parameters (mod values, hash block sizes, padding, etc.).

### REST API Routes

- `/auth/**` — Login (`/login`, `/google`), signup (`/signup`)
- `/documents/**` — Keyword search with access check (`GET /`), file retrieval (`GET /{fileID}/fetch`)
- `/dbo/**` — Admin operations: stats, client/keyword management, file upload workflow (`/upload/extract` → `/upload/stats` → `/upload/finalize`), access grant/revoke, server reset

## Important Patterns

- **Lombok** is used throughout models and `DBOCache` (`@Data`, `@Builder`, `@AllArgsConstructor`, etc.).
- The upload workflow is multi-step and stateful: files are held in `DBOService.tempStorage` between extract and finalize calls.
- `DBO` and `SearchingClient` use heavy multithreading internally (configurable via `numThreads` in Common.properties) for share creation and interpolation.
- Server verification (4 servers) vs no verification (3 servers) changes the protocol behavior — controlled by `serverVerification` and `clientVerification` flags.
