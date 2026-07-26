# Page-level wireframes

These are low-fidelity black-and-white line drawings of the implemented Pebble routes. Each page is kept separate so a later issue can update only the affected wireframe and its written rationale.

## Current route set

1. [Login](login-wireframe.svg) — demonstration sign-in and persistent large-font setting.
2. [Shared Mobile Navigation](mobile-navigation-wireframe.svg) — closed and open drawer states used by every authenticated route.
3. [Dashboard](dashboard-wireframe.svg) — institutional service directory with a problem-first Navigator entry.
4. [Support Search](support-search-wireframe.svg) — own-language search, need-area filter, and comparable source cards.
5. [Facility Detail and Booking](facility-wireframe.svg) — scope boundaries, access facts, availability, and optional booking.
6. [My Appointments](appointments-wireframe.svg) — confirmation, status, review, and cancellation.
7. [Support Guide](support-chat-wireframe.svg) — neutral opening, clarification, recommendations, and confirmed appointment actions.
8. [End-to-end flow](system-flow-wireframe.svg) — route and evidence relationship.

The drawings use only black strokes, black text, and white backgrounds. They describe layout and interaction rather than the visual styling of the deployed portal.

## Update rule

When one page changes:

1. update only that page's SVG;
2. annotate the changed control and intended response;
3. add the reason and related research tag or usability result to this page or the Wiki;
4. link the corresponding commit (and an issue only when the team chooses to use one);
5. rerun the page-level axe check.
