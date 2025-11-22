# A-Zero Contribution and Development Guidelines

**Version:** 1.0
**Status:** Authoritative

This document defines the engineering standards, project structure, and coding conventions for the **A-Zero** project. These guidelines are not suggestions; they are strict requirements designed to ensure the safety, maintainability, and modularity of the framework.

The key words "MUST", "MUST NOT", "REQUIRED", "SHALL", "SHALL NOT", "SHOULD", "SHOULD NOT", "RECOMMENDED", "MAY", and "OPTIONAL" in this document are to be interpreted as described in **RFC 2119**.

---

## 1. Project Structure & Maven Standards

The A-Zero repository is a **Maven Multi-Module** project. All contributions MUST adhere to the established hierarchy and build configuration.

### 1.1 Module Hierarchy
The project is organized into a strict hierarchy. You MUST NOT create flat modules at the root level.

*   **Root (`/`):** The root `pom.xml` is strictly an **aggregator**. It MUST NOT contain dependency logic or build configuration other than the `flatten-maven-plugin` and SCM settings.
*   **BOM (`pom-parent/`):** This module acts as the Bill of Materials. It MUST define all `<properties>`, `<dependencyManagement>`, and `<pluginManagement>`.
*   **Domain Groups:** Modules are grouped into folders based on domain (e.g., `core/`, `backtesting/`, `services/`). Each group MUST have its own intermediate parent POM.
*   **Leaf Modules:** The actual code resides here. Artifact IDs MUST be prefixed with `a0-` (e.g., `a0-backtester`).

### 1.2 Maven Configuration Rules
1.  **Version Management:**
    *   Child modules **MUST NOT** declare versions for external dependencies. All versions MUST be managed in `pom-parent`.
    *   Internal dependencies (sibling modules) MUST use `${project.version}`.
    *   The project uses **CI-Friendly Versioning**. The version is defined via the `${revision}` property in `pom-parent`.

2.  **Dependency Grouping:**
    *   Dependencies in `pom.xml` files MUST be visually grouped using the standard comment blocks:
    ```xml
    <!-- ============================================================= -->
    <!-- A-ZERO INTERNAL MODULES                                       -->
    <!-- ============================================================= -->
    <dependency>...</dependency>

    <!-- ============================================================= -->
    <!-- LOGGING                                                       -->
    <!-- ============================================================= -->
    <dependency>...</dependency>
    ```

3.  **Metadata:**
    *   Every module MUST define a `<name>` following the pattern: `A-Zero :: [Module Name]`.
    *   Every module MUST define a `<description>`.

4.  **Packaging:**
    *   **Libraries (Core/Shared):** MUST use standard JAR packaging.
    *   **Standalone CLIs (No Framework):** MUST use the `maven-shade-plugin` to create an executable uber-jar.
        *   You MUST configure `ManifestResourceTransformer` for the Main-Class.
        *   You MUST configure `ServicesResourceTransformer` to support SPI discovery.
    *   **Microservices (Spring Boot):** MUST use the `spring-boot-maven-plugin`.
        *   You MUST use the `repackage` goal.
        *   You MUST NOT use the shade plugin for Spring Boot applications, as it corrupts auto-configuration metadata.
---

## 2. Coding Standards (Java)

**Language Level:** The project uses **Java 21** (LTS). Code should leverage modern language features (Records, Pattern Matching, Virtual Threads) where appropriate.

**Style Guide:** Code should generally follow standard Java conventions, with specific overrides listed below.

### 2.1 Financial Precision
*   **Strict Prohibition:** You **MUST NOT** use `double` or `float` for any value representing money, prices, quantities, or percentages.
*   **Requirement:** You **MUST** use `java.math.BigDecimal`.
*   **Comparison:** Never use `.equals()` for BigDecimal. You MUST use `.compareTo(other) == 0`.

### 2.2 Lombok & Boilerplate
We use Lombok to reduce noise, but it must be used consistently.

*   **Logging:** Classes MUST use `@Slf4j`. Use parameterized logging (e.g., `log.info("Price: {}", price)`) instead of string concatenation.
*   **Data Classes:**
    *   Use Java **Records** (`record`) for immutable data carriers whenever possible.
    *   Annotate records with `@With` if copy-modification is needed.
    *   If a class cannot be a record but is a POJO, use `@Data` or `@Value`.
*   **Constructors:** Use `@RequiredArgsConstructor` for dependency injection.

### 2.3 Interfaces & Interaction Patterns
*   **Async by Design:** Interfaces involving I/O (e.g., `SentimentProvider`) **MUST** return `CompletableFuture<T>` instead of blocking.
*   **SPI Compatibility:** Interfaces intended for plugins MUST include:
    *   `String getName()`: For identification.
    *   `void init(Config config)`: For lifecycle management (avoid constructor logic).
*   **Documentation:** Interfaces MUST document their interaction pattern (e.g., "Fire-and-Reconcile") in the class-level Javadoc.

### 2.4 Concurrency
*   **Virtual Threads:** Use `Executors.newVirtualThreadPerTaskExecutor()` for high-concurrency, I/O-bound tasks (e.g., fetching data from external APIs).
*   **Bounded Pools:** Use `Executors.newFixedThreadPool(n)` when you need to throttle requests (e.g., to respect rate limits).

### 2.5 Testability Patterns in Production Code
To ensure code is testable without complex DI frameworks, follow the **"Subclass and Override"** pattern:

*   **Protected Factories:** Do not instantiate heavy dependencies (like `ServiceLoader` or HTTP clients) directly in the constructor or main logic. Wrap them in `protected` methods.
    ```java
    // ALLOWED
    protected SentimentProvider loadProvider(String name) {
        return ServiceLoader.load(...).findFirst()...;
    }
    ```
    *This allows tests to subclass the component and inject mocks by overriding the protected method.*

### 2.6 CLI Applications
*   **Framework:** Use **Picocli**.
*   **Structure:** The main class MUST implement `Callable<Integer>` and be annotated with `@Command`.
*   **Exit Codes:** Return `0` for success and `1` for failure.

### 2.7 Javadoc
*   **Mandatory:** All public Classes, Interfaces, and Records MUST have Javadoc.
*   **Format:**
    *   First sentence: Concise summary.
    *   Body: Use HTML tags (`<p>`, `<ul>`, `<li>`) for formatting.
    *   References: Use `{@link ClassName}` extensively.

## 3. Testing Standards (The "A-Zero Style")

We do not write "happy path only" tests. Our testing strategy is rigorous, structured, and descriptive. All contributions MUST adhere to the following conventions using **JUnit 5** and **Mockito**.

### 3.1 Test Structure & Visibility
*   **Visibility:** Test classes and methods MUST be **package-private** (default visibility). Do not use `public`.
*   **Grouping:** You MUST use `@Nested` inner classes to group tests by **Context** or **State** (e.g., `SpotTests`, `MarginTests`, `InitializationTests`). Flat test classes with dozens of unrelated methods are prohibited.

### 3.2 Naming Conventions (Gherkin Style)
We use a Gherkin-inspired naming convention to make test reports readable as documentation.

*   **Inner Classes:** MUST use `@DisplayName` starting with "GIVEN".
    *   *Example:* `@DisplayName("GIVEN AccountMode is SPOT_ONLY")`
*   **Test Methods:** MUST use `@DisplayName` following the pattern: `"WHEN [action], THEN [expected result]"`.
    *   *Example:* `@DisplayName("WHEN a buy order exceeds cash, THEN it should be ignored")`
*   **Method Names:** SHOULD use snake_case or descriptive mixedCase to summarize the scenario.

### 3.3 The "AAA" Pattern
Every test method MUST be visually divided into three sections using specific comments:

```java
@Test
@DisplayName("WHEN input is valid, THEN return result")
void handleValidInput() {
    // --- ARRANGE ---
    // Setup mocks, data, and configuration
    
    // --- ACT ---
    // Execute the method under test
    
    // --- ASSERT ---
    // Verify results and state
}
```

### 3.4 Specific Testing Rules
1.  **BigDecimal Assertions:** You **MUST NOT** use `assertEquals(expected, actual)` for `BigDecimal` because it checks scale equality. You MUST use:
    ```java
    assertEquals(0, expected.compareTo(actual));
    ```
2.  **Async Testing:** When testing `CompletableFuture`, use `.join()` to block and assert results synchronously.
3.  **Legacy/CLI Testing:** To test classes with hard dependencies (like `ServiceLoader`), use the **Subclass and Override** pattern:
    ```java
    // In Test Class
    MyCli cli = new MyCli() {
        @Override
        protected SentimentProvider loadProvider(String name) {
            return mockProvider; // Inject mock via override
        }
    };
    ```

---

## 4. Documentation Guidelines

Documentation is not an afterthought; it is a requirement for the "Definition of Done." A Pull Request is incomplete without updated documentation.

### 4.1 Module READMEs
Every leaf module (e.g., `a0-backtester`) MUST have a `README.md` following this standard template:

1.  **Title:** `A-Zero :: [Module Name]`
2.  **Overview:** A concise description of the module's responsibility.
3.  **Core Components:** Technical breakdown of key interfaces, records, or engines.
4.  **Usage & Configuration:**
    *   **Environment Variables:** Tabular format (`Variable`, `Required`, `Description`).
    *   **CLI Options:** Tabular format (`Option`, `Default`, `Description`).
    *   **Examples:** Concrete code snippets or shell commands.
5.  **Future Roadmap & Known Limitations:** A specific section acknowledging technical debt or planned features (e.g., "Automatic Schema Generation").

### 4.2 Architecture Documentation
The `ARCHITECTURE.md` file in the root is the single source of truth for the system design.

*   **New Modules:** If you add a module, you MUST add a "Module Specification" entry in `ARCHITECTURE.md` defining its **Status**, **Responsibility**, and **Inputs/Outputs**.
*   **Data Contracts:** Changes to core interfaces (`Strategy`, `TradingContext`) or data schemas (YAML) MUST be reflected in the "Data Contracts" section.

### 4.3 Root README
The root `README.md` tracks the high-level project status.
*   **Roadmap:** Update the checklist (`[x]`) as features are completed.
*   **Getting Started:** Ensure CLI examples remain copy-pasteable and functional.