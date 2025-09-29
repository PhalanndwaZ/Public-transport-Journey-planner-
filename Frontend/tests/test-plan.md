# Frontend and Backend Test Plan

## Backend Regression Summary
- Command: `mvn -f Backend/pom.xml test`
- Last run: 2025-09-29 06:06 SAST
- Result: 41 tests passed, 0 failed, 0 skipped
- Log: `../Backend/test-results.log`
- Notable coverage additions:
  - CSA walking-budget handling
  - RAPTOR walking edges within/beyond limits
  - DataLoader name/nearest stop helpers
  - TransitSystem day-type resolution and invalid inputs

## Manual Frontend Test Cases
### HomePage.html
1. Load page and ensure search form renders with origin/destination inputs.
2. Submit empty form; verify validation message appears.
3. Enter valid stops, submit, expect navigation to `Search.html` with query params.

### Search.html
1. Verify date/time pickers default to current day/time.
2. Change transport preferences and submit; ensure request payload updates via network tab.
3. Trigger search with invalid time (e.g., `25:00`) and confirm client-side validation prevents submission.

### Results.html
1. Load with known query params; ensure itinerary list renders with legs and summary.
2. Toggle between itinerary cards; confirm details update without errors.
3. Click export/print button if available; check new window/tab and formatting.

### AdminPortal.html
1. Attempt login with unlisted email; expect error message.
2. Trigger code request with valid email and check inline code display in dev mode.
3. Upload schedule file via admin form (use sample CSV) and verify success/error toast.

### Cross-Page Accessibility Checks
1. Keyboard-tab through primary controls on each page; focus order should be logical.
2. Use screen-reader (NVDA/VoiceOver) to confirm main headings are announced.
3. Validate colour contrast using browser dev tools (WCAG AA minimum).
