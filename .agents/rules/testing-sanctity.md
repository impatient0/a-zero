---
trigger: glob
globs: *Test.java, *IT.java
---

<system_instructions>
    <domain_context>
        <philosophy>Rigorous, structured, and descriptive testing. "Happy path only" is strictly forbidden.</philosophy>
        <frameworks>JUnit 5, Mockito</frameworks>
    </domain_context>

    <structural_standards>
        <constraint type="mandatory" name="Visibility and Grouping">
            <rule>Test classes and methods MUST be package-private (default visibility). Do not use `public`.</rule>
            <rule>You MUST use `@Nested` inner classes to group tests by Context or State (e.g., `SpotTests`, `MarginTests`). Flat test classes are prohibited.</rule>
        </constraint>
        
        <constraint type="mandatory" name="The AAA Pattern">
            Every test method MUST be visually divided into three sections using these exact comments:
            ```java
            // --- ARRANGE ---
            // --- ACT ---
            // --- ASSERT ---
            ```
        </constraint>
    </structural_standards>

    <naming_conventions>
        <constraint type="mandatory" name="Gherkin Style Display Names">
            <rule>Inner Classes (`@Nested`): MUST use `@DisplayName` starting with "GIVEN ". (e.g., `@DisplayName("GIVEN AccountMode is SPOT_ONLY")`)</rule>
            <rule>Test Methods: MUST use `@DisplayName` following the pattern: "WHEN [action], THEN [expected result]". (e.g., `@DisplayName("WHEN a buy order exceeds cash, THEN it should be ignored")`)</rule>
            <rule>Method Names: SHOULD use snake_case or descriptive mixedCase to summarize the scenario.</rule>
        </constraint>
    </naming_conventions>

    <assertion_and_mocking_rules>
        <constraint type="critical" name="BigDecimal Assertions">
            You **MUST NOT** use `assertEquals(expected, actual)` for `BigDecimal` because it checks scale equality.
            <action>You MUST use `assertEquals(0, expected.compareTo(actual));`</action>
        </constraint>

        <constraint type="technical" name="Async and Legacy Handlers">
            <rule>Async: When testing `CompletableFuture`, use `.join()` to block and assert results synchronously.</rule>
            <rule>Legacy/CLI: To test classes with hard dependencies (like `ServiceLoader`), use the Subclass and Override pattern to inject mocks instead of complex reflection.</rule>
        </constraint>
    </assertion_and_mocking_rules>
</system_instructions>