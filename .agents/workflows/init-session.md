---
description: 
---

<workflow_definition>
    <trigger_command>/init-session</trigger_command>
    <description>
        Initializes a new development session by forcing the agent to ingest the current project state, recent history, and Git status, ensuring perfect alignment before any code is written.
    </description>

    <execution_phases>
        <phase name="1. Context Ingestion (The Brain Merge)">
            <action>Utilize file-read tools to explicitly read the complete contents of `docs/PROJECT_STATE.md`.</action>
            <action>Utilize file-read tools to read the most recent entry (only the latest) in `JOURNAL.md` to grasp the "soft context" and any loose threads.</action>
        </phase>

        <phase name="2. Environment Audit">
            <action>Open the integrated terminal and execute `git status` to determine if we are starting with a clean working tree or if there are uncommitted changes from a previous interrupted session.</action>
        </phase>

        <phase name="3. The Session Briefing">
            <action>Output a concise, structured briefing to the user in the chat view. Use this format:
                - **Workspace Status:** [Clean / Uncommitted changes present in X files]
                - **Current Epic:** [From PROJECT_STATE.md]
                - **The \"Soft\" Context:** [One-sentence summary of the last Journal entry's struggles/loose threads]
                - **Imminent Priority:** [The exact next step defined in PROJECT_STATE.md]
            </action>
            <action>Conclude the briefing by asking: *"Shall we begin the Imminent Priority, or are we pivoting to a new task?"*</action>
            <constraint>DO NOT begin writing code or generating Task Briefs until the user responds to the briefing.</constraint>
        </phase>
    </execution_phases>
</workflow_definition>