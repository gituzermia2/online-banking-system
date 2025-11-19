import java.sql.*;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * BankApp.java
 *
 * Simple demo of an online banking backend using JDBC + H2 (embedded).
 * Features:
 *  - Creates schema if not exists
 *  - Create users, create accounts
 *  - Show account overview
 *  - Perform internal transfer with ACID transaction handling
 *
 * How to run: see instructions in the message.
 */
public class BankApp {

    // H2 embedded file DB (creates ./bankdb.mv.db)
    private static final String JDBC_URL = "jdbc:h2:./bankdb;AUTO_SERVER=TRUE";
    private static final String DB_USER = "sa";
    private static final String DB_PASS = "";

    public static void main(String[] args) {
        try (Connection conn = DriverManager.getConnection(JDBC_URL, DB_USER, DB_PASS)) {
            System.out.println("Connected to DB.");
            createSchema(conn);

            // Demo flow
            long alice = createUser(conn, "Alice Mehta", "alice@example.com", "CUSTOMER");
            long bob   = createUser(conn, "Bob Sharma", "bob@example.com", "CUSTOMER");

            createAccount(conn, alice, "ACCT1000001", "SAVINGS", 15000.00);
            createAccount(conn, alice, "ACCT1000002", "SAVINGS", 5000.00);
            createAccount(conn, bob,   "ACCT2000001", "SAVINGS", 2000.00);

            System.out.println("\n--- Before transfer ---");
            printAccounts(conn, alice);
            printAccounts(conn, bob);

            System.out.println("\nAttempting transfer of 3000.00 from ACCT1000001 -> ACCT2000001");
            boolean ok = transfer(conn, "ACCT1000001", "ACCT2000001", 3000.00, alice);
            System.out.println("Transfer success? " + ok);

            System.out.println("\n--- After transfer ---");
            printAccounts(conn, alice);
            printAccounts(conn, bob);

            System.out.println("\nTransactions table sample:");
            printTransactions(conn);

            System.out.println("\nAudit logs sample:");
            printAudit(conn);

        } catch (SQLException ex) {
            ex.printStackTrace();
        }
    }

    // Create minimal schema (users, accounts, transactions, audit_logs)
    private static void createSchema(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement()) {
            // users
            st.executeUpdate(
                "CREATE TABLE IF NOT EXISTS users (" +
                " user_id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                " full_name VARCHAR(200) NOT NULL," +
                " email VARCHAR(255) UNIQUE NOT NULL," +
                " role VARCHAR(50) NOT NULL," +
                " created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                ")"
            );

            // accounts
            st.executeUpdate(
                "CREATE TABLE IF NOT EXISTS accounts (" +
                " account_id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                " user_id BIGINT NOT NULL," +
                " account_number VARCHAR(50) UNIQUE NOT NULL," +
                " type VARCHAR(50) NOT NULL," +
                " balance DECIMAL(18,2) DEFAULT 0.00," +
                " status VARCHAR(20) DEFAULT 'ACTIVE'," +
                " created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                " FOREIGN KEY (user_id) REFERENCES users(user_id)" +
                ")"
            );

            // transactions (simple ledger)
            st.executeUpdate(
                "CREATE TABLE IF NOT EXISTS transactions (" +
                " txn_id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                " txn_uuid VARCHAR(36) NOT NULL," +
                " from_account VARCHAR(50)," +
                " to_account VARCHAR(50)," +
                " amount DECIMAL(18,2) NOT NULL," +
                " currency CHAR(3) DEFAULT 'INR'," +
                " txn_type VARCHAR(50) NOT NULL," +
                " status VARCHAR(50) NOT NULL," +
                " initiated_by BIGINT," +
                " initiated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                " remarks VARCHAR(500)" +
                ")"
            );

            // audit logs
            st.executeUpdate(
                "CREATE TABLE IF NOT EXISTS audit_logs (" +
                " id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                " user_id BIGINT," +
                " action VARCHAR(500)," +
                " meta VARCHAR(2000)," +
                " created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                ")"
            );
        }
    }

    // Create user, return user_id
    private static long createUser(Connection conn, String name, String email, String role) throws SQLException {
        String sql = "INSERT INTO users (full_name, email, role) VALUES (?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name);
            ps.setString(2, email);
            ps.setString(3, role);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    long id = rs.getLong(1);
                    audit(conn, id, "CREATE_USER", "email=" + email + ";role=" + role);
                    System.out.println("Created user " + name + " (id=" + id + ")");
                    return id;
                }
            }
        }
        throw new SQLException("Failed to insert user");
    }

    // Create account for user
    private static long createAccount(Connection conn, long userId, String acctNumber, String type, double initialBalance) throws SQLException {
        String sql = "INSERT INTO accounts (user_id, account_number, type, balance) VALUES (?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, userId);
            ps.setString(2, acctNumber);
            ps.setString(3, type);
            ps.setBigDecimal(4, new java.math.BigDecimal(String.format("%.2f", initialBalance)));
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    long id = rs.getLong(1);
                    audit(conn, userId, "CREATE_ACCOUNT", "account_number=" + acctNumber + ";initial_balance=" + initialBalance);
                    System.out.println("Created account " + acctNumber + " (id=" + id + ")");
                    return id;
                }
            }
        }
        throw new SQLException("Failed to create account");
    }

    // Print accounts for a user
    private static void printAccounts(Connection conn, long userId) throws SQLException {
        String q = "SELECT account_number, type, balance, status, created_at FROM accounts WHERE user_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(q)) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                System.out.println("Accounts for user_id=" + userId + ":");
                boolean found = false;
                while (rs.next()) {
                    found = true;
                    String an = rs.getString("account_number");
                    String type = rs.getString("type");
                    String bal = rs.getBigDecimal("balance").toPlainString();
                    String status = rs.getString("status");
                    Timestamp ts = rs.getTimestamp("created_at");
                    System.out.printf("  %s | %s | %s | %s | created:%s%n", an, type, bal, status, ts);
                }
                if (!found) System.out.println("  (none)");
            }
        }
    }

    // Core: perform internal transfer with transactional safety
    // Returns true if success, false otherwise
    private static boolean transfer(Connection conn, String fromAccount, String toAccount, double amount, long initiatedBy) {
        String selectForUpdate = "SELECT balance FROM accounts WHERE account_number = ? FOR UPDATE";
        String updateBalance = "UPDATE accounts SET balance = ? WHERE account_number = ?";
        String insertTxn = "INSERT INTO transactions (txn_uuid, from_account, to_account, amount, txn_type, status, initiated_by, remarks) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        boolean success = false;
        try {
            conn.setAutoCommit(false); // begin transaction

            // lock source
            java.math.BigDecimal fromBal;
            try (PreparedStatement ps = conn.prepareStatement(selectForUpdate)) {
                ps.setString(1, fromAccount);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        insertTransactionRecord(conn, fromAccount, toAccount, amount, "TRANSFER", "FAILED", initiatedBy, "source_account_not_found");
                        conn.rollback();
                        return false;
                    }
                    fromBal = rs.getBigDecimal(1);
                }
            }

            if (fromBal.compareTo(new java.math.BigDecimal(String.format("%.2f", amount))) < 0) {
                // insufficient funds
                insertTransactionRecord(conn, fromAccount, toAccount, amount, "TRANSFER", "FAILED", initiatedBy, "insufficient_funds");
                conn.rollback();
                return false;
            }

            // lock destination
            java.math.BigDecimal toBal;
            try (PreparedStatement ps = conn.prepareStatement(selectForUpdate)) {
                ps.setString(1, toAccount);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        insertTransactionRecord(conn, fromAccount, toAccount, amount, "TRANSFER", "FAILED", initiatedBy, "destination_account_not_found");
                        conn.rollback();
                        return false;
                    }
                    toBal = rs.getBigDecimal(1);
                }
            }

            // perform updates
            java.math.BigDecimal newFrom = fromBal.subtract(new java.math.BigDecimal(String.format("%.2f", amount)));
            java.math.BigDecimal newTo   = toBal.add(new java.math.BigDecimal(String.format("%.2f", amount)));

            try (PreparedStatement ps = conn.prepareStatement(updateBalance)) {
                ps.setBigDecimal(1, newFrom);
                ps.setString(2, fromAccount);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement(updateBalance)) {
                ps.setBigDecimal(1, newTo);
                ps.setString(2, toAccount);
                ps.executeUpdate();
            }

            // insert transaction success record
            insertTransactionRecord(conn, fromAccount, toAccount, amount, "TRANSFER", "SUCCESS", initiatedBy, "Internal transfer");

            audit(conn, initiatedBy, "TRANSFER", "from=" + fromAccount + ";to=" + toAccount + ";amount=" + amount);

            conn.commit();
            success = true;

        } catch (SQLException ex) {
            try {
                conn.rollback();
            } catch (SQLException e2) {
                e2.printStackTrace();
            }
            ex.printStackTrace();
            success = false;
        } finally {
            try {
                conn.setAutoCommit(true);
            } catch (SQLException ignore) {}
        }
        return success;
    }

    // Helper to insert into transactions table (within same connection/transaction)
    private static void insertTransactionRecord(Connection conn, String fromAcct, String toAcct, double amount,
                                                String type, String status, long initiatedBy, String remarks) throws SQLException {
        String insertTxn = "INSERT INTO transactions (txn_uuid, from_account, to_account, amount, txn_type, status, initiated_by, remarks) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(insertTxn)) {
            ps.setString(1, UUID.randomUUID().toString());
            ps.setString(2, fromAcct);
            ps.setString(3, toAcct);
            ps.setBigDecimal(4, new java.math.BigDecimal(String.format("%.2f", amount)));
            ps.setString(5, type);
            ps.setString(6, status);
            ps.setLong(7, initiatedBy);
            ps.setString(8, remarks);
            ps.executeUpdate();
        }
    }

    // Audit log insert (simple)
    private static void audit(Connection conn, Long userId, String action, String meta) throws SQLException {
        String sql = "INSERT INTO audit_logs (user_id, action, meta) VALUES (?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            if (userId == null) ps.setNull(1, Types.BIGINT);
            else ps.setLong(1, userId);
            ps.setString(2, action);
            ps.setString(3, meta);
            ps.executeUpdate();
        }
    }

    // Print some transactions
    private static void printTransactions(Connection conn) throws SQLException {
        String q = "SELECT txn_id, txn_uuid, from_account, to_account, amount, txn_type, status, initiated_at FROM transactions ORDER BY initiated_at DESC LIMIT 10";
        try (PreparedStatement ps = conn.prepareStatement(q); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                System.out.printf("  txn_id=%d uuid=%s from=%s to=%s amount=%s type=%s status=%s time=%s%n",
                        rs.getLong("txn_id"),
                        rs.getString("txn_uuid"),
                        rs.getString("from_account"),
                        rs.getString("to_account"),
                        rs.getBigDecimal("amount").toPlainString(),
                        rs.getString("txn_type"),
                        rs.getString("status"),
                        rs.getTimestamp("initiated_at").toString());
            }
        }
    }

    // Print audit logs
    private static void printAudit(Connection conn) throws SQLException {
        String q = "SELECT id, user_id, action, meta, created_at FROM audit_logs ORDER BY created_at DESC LIMIT 10";
        try (PreparedStatement ps = conn.prepareStatement(q); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                System.out.printf("  audit_id=%d user=%s action=%s meta=%s time=%s%n",
                        rs.getLong("id"),
                        rs.getObject("user_id"),
                        rs.getString("action"),
                        rs.getString("meta"),
                        rs.getTimestamp("created_at").toString());
            }
        }
    }
}
