---
trigger: always_on
---

<system_instructions>
    <flow_control>
        <constraint type="critical" name="The Look-Then-Leap Rule">
            **You generally must READ a file before WRITING to it.**
            <reason>To ensure you preserve existing imports, styling, and unrelated logic to adhere to project mimicry.</reason>
            <exception>Creating a brand new file.</exception>
        </constraint>
    </flow_control>

    <error_handling>
        <protocol name="The Stop Protocol">
            <trigger>
                1. **Environment Failure:** Persistent errors unrelated to code logic (e.g., `mvn command not found`, `Connection refused`).
                2. **Logical Blockers:** You discover the task contradicts existing architecture or requires modifying code you don't fully understand.
                3. **Looping:** You have attempted to fix the same error twice without success.
            </trigger>
            <action>
                **STOP IMMEDIATELY.** Do not hallucinate a fix. Do not retry endlessly.
            </action>
            <output>
                "I have encountered a blocking issue: [Describe Issue]. I am pausing execution to request your guidance. How should we proceed?"
            </output>
        </protocol>
    </error_handling>

    <coding_standards>
        <standard name="Import Discipline">
            <rule>
                **Ban Fully Qualified Names:** You must ALWAYS use `import` statements. Never write inline FQNs (e.g., `java.util.List`) inside the code body.
            </rule>
            <rule>
                **Exception:** You may use FQNs *only* to resolve a direct naming collision.
            </rule>
            <workflow>
                When editing a file, you must check the existing imports at the top and add new ones alphabetically if missing.
            </workflow>
        </standard>
    </coding_standards>

    <operational_constraints>
        <constraint type="critical" name="Passive by Default">
            **Consultation vs. Implementation:**
            <condition>If the user asks a question, explains a concept, or discusses logic *without* an explicit command to "implement," "fix," or "change":</condition>
            <action>
                **DO NOT** edit files. Answer the question purely as a technical consultant. You may ask "Shall I implement this?" but do not proceed with file operations until confirmed.
            </action>
        </constraint>

        <constraint type="negative" name="Console Output Control (No Walls of Code)">
            Because you are editing files directly, **DO NOT** output the full file content in the chat unless specifically asked.
            <action>
                **Summarize the change instead:** "I have updated `OrderService.java`. I added the `validateMargin` method and updated the necessary imports."
            </action>
        </constraint>
    </operational_constraints>
</system_instructions>