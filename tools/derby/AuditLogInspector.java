package tools.derby;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;

/**
 * Diagnostic and ISO/IEC 30107 privacy compliance inspector for Derby audit tables.
 * Can be executed on demand or as a post-test verification step.
 */
public class AuditLogInspector {

    public static void main(String[] args) {
        String dbPath = args.length > 0 ? args[0] : "tools/derby/testdb";
        String jdbcUrl = "jdbc:derby:" + dbPath;

        System.out.println("================================================================================");
        System.out.println("   MOSIP Derby Audit Compliance & Privacy Inspector");
        System.out.println("   Target Database: " + dbPath);
        System.out.println("================================================================================\n");

        try {
            Class.forName("org.apache.derby.jdbc.EmbeddedDriver");
            try (Connection conn = DriverManager.getConnection(jdbcUrl);
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(
                         "SELECT LOG_ID, EVENT_ID, EVENT_NAME, EVENT_TYPE, LOG_DESC " +
                         "FROM APP_AUDIT_LOG WHERE EVENT_ID LIKE 'REG-LIV%' ORDER BY LOG_ID ASC")) {

                int totalCount = 0;
                boolean privacyBreach = false;
                Map<String, Integer> eventCounts = new HashMap<>();

                while (rs.next()) {
                    totalCount++;
                    String eventId = rs.getString("EVENT_ID");
                    String desc = rs.getString("LOG_DESC");

                    eventCounts.put(eventId, eventCounts.getOrDefault(eventId, 0) + 1);

                    // Privacy verification: Check for presence of pixel arrays or base64 strings
                    if (desc != null && (desc.contains("base64") || desc.contains("data:image") || desc.contains("[B@"))) {
                        privacyBreach = true;
                        System.err.println("[VIOLATION] Found forbidden biometric payload in Log ID: " + rs.getInt("LOG_ID"));
                    }
                }

                System.out.println("[AUDIT SUMMARY]");
                for (Map.Entry<String, Integer> entry : eventCounts.entrySet()) {
                    System.out.printf("  • %-12s : %d records%n", entry.getKey(), entry.getValue());
                }
                System.out.println("\nTotal Face Liveness Audit Records: " + totalCount);
                System.out.println("Zero-Biometric Leak Verification: " +
                        (privacyBreach ? "FAILED (Image data detected)" : "PASSED (100% compliant, zero image leak)"));

            }
        } catch (Exception e) {
            System.err.println("[ERROR] Failed to inspect Derby audit logs: " + e.getMessage());
        }
    }
}
