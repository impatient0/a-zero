---
trigger: glob
globs: *.java, *.yaml, *.yml
---

<system_instructions>
    <domain_context>
        <project>A-Zero Crypto Trading System</project>
        <philosophy>Safety and correctness above all else. Financial bugs are catastrophic.</philosophy>
    </domain_context>

    <financial_safety>
        <constraint type="critical" name="Precision Math (No Floats)">
            **Never use `double` or `float` for financial calculations, prices, or quantities.**
            <rule>You MUST strictly use `java.math.BigDecimal` for all monetary and crypto-asset values to prevent floating-point precision loss.</rule>
            <rule>Always specify `RoundingMode` when performing division with `BigDecimal`.</rule>
        </constraint>

        <constraint type="critical" name="Zero-Trust External Calls">
            **Assume all external APIs (Binance, exchanges) and infrastructure (Kafka, DB) will fail.**
            <rule>Wrap all external network calls in robust `try-catch` or reactive error-handling blocks.</rule>
            <rule>Must implement retry mechanisms with exponential backoff to handle HTTP 429 (Too Many Requests) and HTTP 500 network timeouts.</rule>
        </constraint>
    </financial_safety>

    <security_protocols>
        <protocol name="Strict Secrets Management">
            <action>NEVER hardcode API keys, wallet secrets, or database credentials in the source code.</action>
            <action>Always inject credentials via Spring `@Value`, `Environment`, or Docker environment variables.</action>
        </protocol>
    </security_protocols>

    <audit_and_logging>
        <standard name="Financial Audit Trails">
            <rule>Every state-changing trading decision (Signal Generation, Order Placement, Order Execution) MUST be accompanied by comprehensive logging.</rule>
            <rule>Include contextual data in logs: Timestamp, Asset Pair, Order Type, Quantity, Calculated Price, and explicit Reason for action.</rule>
            <rule>Log at the `INFO` level for business events, and `ERROR` level for failed API interactions.</rule>
        </standard>
    </audit_and_logging>
</system_instructions>