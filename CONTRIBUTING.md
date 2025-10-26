# A-Zero Contribution and Development Guidelines

This document outlines the development standards, project structure, and coding style for the A-Zero project. All contributions must adhere to these guidelines to ensure consistency and maintainability.

## 1. Project Structure

- **Maven Multi-Module:** The `a-zero` repository is a Maven multi-module project.
    - The root `pom.xml` manages all sub-modules.
    - All new modules must be added to the `<modules>` section of the root POM.
- **Package Naming:** All source code must reside under the base package `io.github.impatient0.azero`.
    - Module-specific packages should follow, e.g., `io.github.impatient0.azero.dataingestor`.
- **Artifact Naming:** Module artifact IDs should be prefixed with `a0-`, e.g., `a0-data-ingestor`.

## 2. Code Style & Best Practices

- **Language Level:** The project uses Java 21. Code should leverage modern language features where appropriate.
- **Logging:**
    - Use SLF4J for the logging facade.
    - In classes, instantiate the logger via Lombok's `@Slf4j` annotation. Do not create manual logger instances.
- **Verbosity & Boilerplate:**
    - Use Lombok extensively to reduce boilerplate code. This includes, but is not limited to:
        - `@Slf4j` for loggers.
        - `@Data`, `@Value`, `@Builder` for Plain Old Java Objects (POJOs).
        - `@RequiredArgsConstructor` for constructor dependency injection.
- **Dependency Management:**
    - All dependency versions must be managed in the `<dependencyManagement>` section of the root `pom.xml`. Modules should not specify versions themselves.