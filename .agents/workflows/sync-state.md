---
description: 
---

<workflow_definition>
    <trigger_command>/handoff-journal</trigger_command>
    <description>
        Executes the session wrap-up protocols: audits documentation for drift, serializes the current state for the next session, and captures the "soft context" of the engineering process.
    </description>

    <execution_phases>
        <phase name="1. Documentation Hygiene (The Feedback Loop)">
            <action>Analyze the Git diff and recent file changes to identify if any high-level architectural documents (e.g., `ARCHITECTURE.md`, `README.md`) have drifted from the "Ground Truth" of the new code.</action>
            <action>If drift is detected, automatically apply surgical documentation updates to align the docs with the code.</action>
        </phase>

        <phase name="2. Project State Synchronization (The Dashboard)">
            <objective>Maintain a living document reflecting the exact current coordinate of the project to eliminate session initialization overhead.</objective>
            <action>Locate `docs/PROJECT_STATE.md`.</action>
            <action>Overwrite the file with the current state, updating these specific sections:
                - **Current Phase/Epic:** [High-level goal we are currently working towards]
                - **Recently Completed:** [Bullet points of what was just built and verified]
                - **Active Blockers/Debt:** [Known bugs, failing tests, or temporary hacks that need revisiting]
                - **Imminent Next Steps:** [Strict, actionable priorities for the very next coding session]
            </action>
            <constraint>Do NOT append. This file must be updated (and likely almost completely overwritten) to reflect ONLY the current present state.</constraint>
        </phase>

        <phase name="3. Engineering Journal (The Soft Context)">
            <objective>To capture reasoning, discarded ideas, and context lost in formal documentation.</objective>
            <action>Locate or create a `docs/JOURNAL.md` file in the project root.</action>
            <action>Append a new entry to `docs/JOURNAL.md` using this template:
                - **Entry Date:** [Current Date]
                - **Session Theme:** [High-level summary of the session]
                - **The "Real" Story:** [Narrative summary of struggles, blocking issues, or "why" things were built a certain way]
                - **Discarded Paths:** [Technical approaches that were rejected and WHY]
                - **Loose Threads:** [Random ideas or future scope questions]
            </action>
        </phase>
        
        <phase name="4. Final Review">
            <action>Run `git add docs/PROJECT_STATE.md docs/JOURNAL.md` (and any updated docs).</action>
            <action>Output to the user: "Handoff and Journal protocols complete. Documentation is synced. Ready to commit wrap-up files."</action>
        </phase>
    </execution_phases>
</workflow_definition>