# Research and UX rationale

## Research question

How do university students choose a support source when campus support categories are unclear or overlapping, and what information helps them make that choice?

The source Research Plan labels the available evidence as a simulated pilot. The current design is therefore a research direction to test, not a claim that the interface has already solved the problem.

## Evidence-to-feature chain

### Students use familiar sources as a bridge (T01–T03)

Students begin with peers, broad search, or their own words before approaching formal services.

Design response:

- the dashboard says “Start with your situation, not the university structure”;
- the support search accepts natural phrases such as “deadline stress”;
- category keywords expand own-language search;
- the Support Guide asks what is happening before asking for a category.

### Overlapping ownership makes several sources plausible (T04–T07)

An extension might appear to belong to a lecturer, tutor, Academic Skills, or general Student Support. Institutional labels can cause redirection and repeated explanation.

Design response:

- facilities can belong to more than one category;
- results show several plausible options instead of silently selecting one;
- each result exposes provider and scope before booking;
- DeepSeek is prompted to explain why a source fits and how it differs from alternatives.

### Practical access information breaks ties (T08–T11)

Reply time, immediacy, channel, location, and written record influence the final choice.

Design response:

- results expose distance, response time, access mode, and rating;
- facility detail shows how to access the service;
- search and category filters reduce the comparison set;
- appointment availability comes from the database rather than a static screen.

Rating is deliberately secondary: T15 says a rating does not establish whether a service owns the problem.

### Concrete scope examples clarify boundaries (T12–T15, T19)

Students need clear scope, examples, provider role, eligibility, preparation, and privacy before sharing a situation.

Design response:

- facility details show “What to know before you choose”;
- searchable summaries and tags contain concrete help examples;
- the page exposes eligibility and preparation before the optional note;
- the assistant prompt asks the model to surface privacy limits and avoid requesting unnecessary sensitive information.

### Confirmation and fallback reduce the cost of uncertainty (T16–T18)

After choosing, students want confirmation, status, and a next-best route instead of restarting.

Design response:

- appointment booking produces a server-rendered confirmation;
- My Appointments preserves service, time, note, and status;
- cancellation returns the slot to availability and confirms the new state;
- the assistant can review, book, or cancel after explicit confirmation;
- the prompt asks the guide to offer a fallback when the first source cannot help.

## Information architecture

The earlier homework proposed four groups:

- **Find Help:** own-language search, broad need areas, recommendations, filters.
- **Compare Options:** scope, examples, provider, eligibility, response, access, privacy.
- **Book or Contact:** availability, appointment details, optional summary, cancellation.
- **My Request:** appointments, confirmation, status, and fallback.

The current routes preserve that logic without turning every group into a top navigation item. Search and comparison share `/support`, booking sits in the facility context, and records sit in `/appointments`.

## Research prototype question

Can students distinguish between two plausible support sources using scope examples, provider role, response time, and access information before booking?

The Support Guide adds a related question from teacher feedback:

Can students who do not know their category use immediate conversational guidance to describe the issue, understand the overlap, and reach a suitable source without being misrouted?

## Limits

- Evidence T01–T19 is simulated-pilot material and must be validated with real participants.
- Facilities, ratings, descriptions, and appointment slots are fictional.
- AI guidance may still be wrong; the interface must show its reasoning and alternatives rather than treating it as diagnosis.
- Automated axe results do not substitute for participant testing with assistive technologies.
