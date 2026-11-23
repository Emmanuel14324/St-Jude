# St Jude Central Hospital — UI Refresh Notes

## What changed

The refresh is focused on the shared JavaFX stylesheet at:

`src/main/resources/ug/sjch/styles/application.css`

The existing Java controllers, FXML view structure, Oracle database configuration, SQL scripts, authentication logic, and role-based workflows have been left unchanged.

## Visual direction

- Deep navy-to-blue branding for the login brand panel and department navigation.
- A consistent hospital blue for primary actions, active navigation, chart accents, and selected states.
- Light blue highlights for metric icons and informational elements.
- Soft blue-grey workspace backgrounds and white content panels.
- More consistent corner radii, spacing, typography, borders, and restrained shadows.
- More readable table headers, row spacing, hover states, and selection states.
- Shared styling for text fields, combo boxes, date pickers, checkboxes, buttons, and scrollbars.
- Department-specific sidebar colors and overly colorful metric-card backgrounds are unified to strengthen the hospital-wide identity. Warning, error, and success colors remain available for status meaning.

## Suggested verification

1. Open the project in IntelliJ IDEA, NetBeans, or another Java 21-compatible IDE.
2. Set the Oracle connection environment variables required by `DatabaseConfig`: `SJCH_DB_URL`, `SJCH_DB_USER`, and `SJCH_DB_PASSWORD`.
3. Run the application and inspect login plus each role dashboard at the normal and minimum supported window sizes.
4. Check tables, dialogs, forms, notifications, and navigation using the relevant demo accounts and database seed scripts.
5. Run `mvn test` from the project root. The source archive's earlier report documented one JavaFX view-loading test failure; confirm whether that remains after the refresh.

## Validation limitation

The source files were reviewed and the stylesheet was updated, but Maven tests could not be run in the editing environment because Maven is not installed there. This refresh changes CSS only; it does not claim to fix the previously reported JavaFX test failure.
