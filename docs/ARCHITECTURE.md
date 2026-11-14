# NEXORA architecture

1. Android sends a user instruction to `POST /tasks`.
2. Server stores task + status before returning the task ID.
3. Worker claims queued tasks, creates a plan, runs provider stages, persists every checkpoint.
4. Providers work in parallel for independent analysis, then a synthesis/review stage reconciles results.
5. A bounded retry policy handles transient errors; rate limits and missing credentials become visible blockers.
6. Android polls `GET /tasks/:id` and can pause/resume/cancel.

A task is complete only when the completion evaluator confirms all acceptance criteria. The worker is
not an infinite loop: it has configurable step, time and cost ceilings to prevent runaway API usage.
Human approval is required for external side effects (publishing, purchases, deleting data, etc.).

## Provider roles
- OpenAI: task decomposition and synthesis
- Gemini: independent analysis and verification
- Grok: alternative approaches / research
- Claude: critique, code review and quality gate

Roles are configurable; no provider is assumed to be available until credentials and connectivity pass checks.
