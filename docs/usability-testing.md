# Usability testing and retrospective

## Primary moderated task

Participant:

A university student who recently hesitated between at least two campus support sources because their responsibilities seemed unclear or overlapping. Include at least one keyboard, screen-reader, or large-text user in the study.

Scenario:

> You need help with an assessment extension but are unsure whether it belongs with a lecturer, programme tutor, Academic Skills & Extensions, or Student Support. Use the portal to decide which source fits, then book it.

Success:

- The participant identifies Academic Skills & Extensions.
- Before booking, they explain the choice using scope, examples, provider, eligibility, response, or access information.
- They can review and, when asked, cancel the booking.

Critical error:

- choosing from a familiar label or rating alone;
- being unable to distinguish plausible sources;
- booking without noticing relevant eligibility or preparation information;
- believing the Support Guide has provided a medical diagnosis;
- being unable to complete the task with keyboard or assistive technology.

Observe:

- words used in search or chat;
- first click and options compared;
- information opened, used, or ignored;
- pauses, backtracking, and repeated explanations;
- whether the participant notices model uncertainty and alternatives;
- whether focus and status updates remain understandable.

Probes:

1. What separated this option from the other plausible sources?
2. Which information resolved the uncertainty, and what was still missing?
3. What made you trust or distrust the Support Guide's recommendation?
4. If the first service could not help, what would you expect to happen next?

Interpretation:

Compare the explanation with the pages opened, controls used, and fields inspected. Reaching the intended facility without understanding the boundary is not sufficient.

## Focused chatbot task

Scenario:

> You do not know the university's support categories. Tell the Support Guide in your own words that you are stressed about a deadline, compare its suggested sources, and ask it to book the option you prefer.

Check whether the assistant clarifies the request, uses current database facts, explains overlap, asks for confirmation, and acts only on the current user's records.

## Accessibility sessions

Test the complete flow with:

- keyboard-only navigation at standard and large-font settings;
- VoiceOver or NVDA reading order and control names;
- 200% browser zoom and narrow viewport reflow;
- reduced-motion preference;
- axe as a regression check after each route change.

## Retrospective

What changed:

- The first prototype expected students to choose a support category themselves.
- Teacher feedback identified that students may not know the category and may want immediate guidance before speaking to a person.
- The project added an accessible chat entry point, database-grounded DeepSeek guidance, and scoped appointment tools.

What worked:

- The refined research question created a clear angle: choosing between overlapping sources, not just locating a generic help centre.
- Evidence T08–T15 translated directly into comparable database fields and interface content.
- Server-rendering reduced route inconsistency and made accessibility testing straightforward.
- Issue-sized commits preserved the design history.

What still needs evidence:

- whether students understand facility scope language;
- whether the chatbot reduces hesitation or adds another unclear layer;
- which tie-breaker is most useful in real situations;
- whether privacy explanations are visible early enough;
- how participants recover when no listed facility fits.

Next iteration:

Run moderated sessions, code failures by evidence/theme, update one page-level wireframe at a time, and link each implementation commit to the relevant issue and wiki entry.
