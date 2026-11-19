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

## Multi-agent gateway fleet

The core task loop remains anchored by direct OpenAI planning/tool execution and Gemini review.
Optional OpenAI-compatible gateways add independent review lanes:

- OmniRoute: `OMNIROUTE_BASE_URL`, `OMNIROUTE_API_KEY`, `OMNIROUTE_MODEL`
- FreeLLMAPI: `FREELLMAPI_BASE_URL`, `FREELLMAPI_API_KEY`, `FREELLMAPI_MODEL`
- 9Router: `NINEROUTER_BASE_URL`, `NINEROUTER_API_KEY`, `NINEROUTER_MODEL`

Each base URL must be reachable from the APEX server and include the `/v1` path. APEX calls
`POST /chat/completions` on that base URL. Set a model id supported by the gateway; `auto`
is the default, but not every gateway/deployment guarantees that alias.

Configured gateways are called concurrently as independent reviewer agents. Their findings are
added to the task transcript and passed to the final GPT decision stage. A gateway failure is
recorded as a warning and does not stop the core task. The existing step and time limits still
apply. More gateways mean more requests, latency, and potentially more cost; APEX does not
claim that free-tier quotas are unlimited.

### Important deployment boundary

A local gateway URL such as `http://127.0.0.1:20128/v1` works only when the APEX server runs
on the same device/network namespace. If APEX is hosted on Render, Render cannot reach the
Termux `localhost` on the phone. Use a secured, reachable HTTPS endpoint or a private tunnel
with authentication; never expose a router dashboard or API key publicly.

### Agent workflow

1. GPT receives the immutable user instruction and executes the task using approved tools.
2. Gemini independently checks the output.
3. Every configured gateway runs a separate review request in parallel.
4. GPT synthesizes the reviews, updates the checkpoint, and decides whether to continue,
   complete, or block for human approval.
5. External side effects still require explicit human approval. Gateway review agents do not
   receive independent write access to GitHub in this phase.
