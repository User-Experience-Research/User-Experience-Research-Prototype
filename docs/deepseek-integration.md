# DeepSeek V4 Flash integration

The support guide uses DeepSeek's OpenAI-compatible chat-completions endpoint.

## Environment

```text
DEEPSEEK_API_KEY=required for live AI
DEEPSEEK_MODEL=deepseek-v4-flash
DEEPSEEK_BASE_URL=https://api.deepseek.com/v1
```

The secret is read only by the Ktor server. The web page calls `/api/support-chat`; it never receives the API key.

## Prompt

The English prompt at `src/main/resources/prompts/support-assistant-system.txt` gives the assistant a helpful, calm tone and the following operating rules:

- accept the student's wording instead of requiring an official category;
- clarify the situation before choosing between plausible services;
- compare scope, provider, examples, eligibility, response time, channel, location, preparation, and privacy;
- use database tools rather than inventing facilities or appointment status;
- request explicit confirmation before appointment mutations;
- avoid diagnosis and switch to urgent safety guidance when necessary;
- be transparent when information is missing.

## Tools

| Tool | Purpose | User boundary |
|---|---|---|
| `search_facilities` | Search current facilities and overlapping categories | Public taxonomy only |
| `get_facility` | Retrieve scope and access details | Public facility record |
| `list_user_appointments` | Review current bookings | Server injects session user |
| `get_available_slots` | Retrieve live prototype availability | Facility-scoped |
| `book_appointment` | Book a selected available slot | Requires confirmation; server injects user |
| `cancel_appointment` | Cancel a booked appointment | Requires confirmation; server checks ownership |

## Model loop

1. Ktor sends the system prompt, recent conversation, and tool schemas.
2. DeepSeek either returns guidance or one or more tool requests.
3. The server validates and executes each request.
4. Tool results are returned to DeepSeek for a short student-facing explanation.
5. The final response and recommendations are saved to the current conversation.

Non-thinking mode is used for the tool loop to keep this prototype responsive and predictable. The model name is configurable so the integration can be updated without changing source code.

## No-key behavior

If `DEEPSEEK_API_KEY` is absent, `AssistantFactory` selects `RuleBasedSupportAssistant`. It searches the same repository and can display current bookings, but it directs appointment changes to the rendered interface. This keeps the prototype demonstrable without committing a secret.
