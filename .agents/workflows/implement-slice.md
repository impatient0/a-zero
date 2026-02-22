---
description: 
---

<workflow_definition>
    <trigger_command>/implement-slice</trigger_command>
    <description>
        Automates the end-to-end implementation of a single Task Brief (slice), forcing an autonomous ReAct (Reason + Act) loop via the integrated terminal.
    </description>

    <execution_phases>
        <phase name="1. Ingestion and Mapping">
            <action>Read the provided Task Brief (either pasted in chat or linked as a markdown file).</action>
            <action>Use file-read tools to analyze the current state of all target files mentioned in the brief.</action>
            <action>Identify any missing dependencies or imports required before writing code.</action>
        </phase>

        <phase name="2. Code Implementation">
            <action>Execute the required edits and create new files as specified in the Task Brief.</action>
            <constraint>You MUST adhere strictly to all active rules, including `editing-safety.md` and `defensive-coding.md`.</constraint>
            <action>Write the associated unit/integration tests for the new logic, ensuring compliance with `testing-sanctity.md`.</action>
        </phase>

        <phase name="3. Autonomous Verification (The Build Loop)">
            <action>Open the integrated terminal and execute: `mvn clean test`</action>
            <condition if="Build Fails (Red)">
                <action>1. Read the stack trace from the terminal output.</action>
                <action>2. Identify the compilation error or failing test.</action>
                <action>3. Edit the offending file to fix the issue.</action>
                <action>4. Re-run `mvn test`.</action>
                <action>5. Repeat this loop autonomously until the build passes. If stuck in a loop for more than 3 attempts, invoke the Stop Protocol and ask the user for help.</action>
            </condition>
            <condition if="Build Passes (Green)">
                <action>Proceed immediately to Phase 4.</action>
            </condition>
        </phase>

        <phase name="4. Staging and Review">
            <action>Run `git status` to verify changed files.</action>
            <action>Run `git add .` to stage the changes.</action>
            <action>Output a concise summary to the user: "Implementation complete and tests are green. Files staged. Please review the diff. Shall I commit with message: '[Proposed Commit Message]'?"</action>
            <constraint>DO NOT execute the git commit until the user explicitly replies with "Yes" or "Commit".</constraint>
        </phase>
    </execution_phases>
</workflow_definition>