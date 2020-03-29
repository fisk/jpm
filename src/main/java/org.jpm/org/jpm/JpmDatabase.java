package org.jpm;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.regex.Pattern;

public class JpmDatabase implements AutoCloseable {
    private Path _path;
    private Connection _connection;

    public JpmDatabase(Path path) {
        _path = path;
        connect();
    }

    private void connect() {
        try {
            String url = "jdbc:sqlite:" + _path.toString();
            _connection = DriverManager.getConnection(url);
            try (Statement stmt = _connection.createStatement()) {
                stmt.execute("CREATE TABLE IF NOT EXISTS ARTIFACTS (\n" +
                             "    NAME text NOT NULL,\n" +
                             "    VERSION_MAJOR integer,\n" +
                             "    VERSION_MINOR integer,\n" +
                             "    VERSION_PATCH integer,\n" +
                             "    CONSTRAINT PK_NAME_VERSION PRIMARY KEY (NAME, VERSION_MAJOR, VERSION_MINOR, VERSION_PATCH)\n" +
                             ");");
                stmt.execute("CREATE TABLE IF NOT EXISTS DEPENDENCIES (\n" +
                             "    FROM_NAME text NOT NULL,\n" +
                             "    FROM_VERSION_MAJOR integer,\n" +
                             "    FROM_VERSION_MINOR integer,\n" +
                             "    FROM_VERSION_PATCH integer,\n" +
                             "    TO_NAME text NOT NULL,\n" +
                             "    TO_VERSION_MAJOR integer,\n" +
                             "    TO_VERSION_MINOR integer,\n" +
                             "    TO_VERSION_PATCH integer,\n" +
                             "    CONSTRAINT PK_FROM_TO_NAME_VERSION PRIMARY KEY (\n" +
                             "        FROM_NAME, FROM_VERSION_MAJOR, FROM_VERSION_MINOR, FROM_VERSION_PATCH,\n" +
                             "        TO_NAME, TO_VERSION_MAJOR, TO_VERSION_MINOR, TO_VERSION_PATCH\n" +
                             "    )\n" +
                             "    CONSTRAINT FK_FROM_NAME_VERSION FOREIGN KEY (\n" +
                             "        FROM_NAME, FROM_VERSION_MAJOR, FROM_VERSION_MINOR, FROM_VERSION_PATCH\n" +
                             "    ) REFERENCES ARTIFACTS(\n" +
                             "        NAME, VERSION_MAJOR, VERSION_MINOR, VERSION_PATCH\n" +
                             "    )\n" +
                             "    CONSTRAINT FK_TO_NAME_VERSION FOREIGN KEY (\n" +
                             "        TO_NAME, TO_VERSION_MAJOR, TO_VERSION_MINOR, TO_VERSION_PATCH\n" +
                             "    ) REFERENCES ARTIFACTS(\n" +
                             "        NAME, VERSION_MAJOR, VERSION_MINOR, VERSION_PATCH\n" +
                             "    )" +
                             ");");
            }
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }

    public void addJpm(JpmFile jpm) {
        var main = jpm.getMain();
        var pattern = Pattern.compile("(\\d+)\\.(\\d+)\\.(\\d+)");
        int major = 0;
        int minor = 0;
        int patch = 0;
        var matcher = pattern.matcher(main.getVersion());
        if (!matcher.matches()) {
            throw new RuntimeException("Invalid version");
        }
        try {
            major = Integer.parseInt(matcher.group(1));
        } catch (Exception e) {
        }
        try {
            minor = Integer.parseInt(matcher.group(2));
        } catch (Exception e) {
        }
        try {
            patch = Integer.parseInt(matcher.group(3));
        } catch (Exception e) {
        }
        String sql = "INSERT INTO ARTIFACTS(NAME, VERSION_MAJOR, VERSION_MINOR, VERSION_PATCH) VALUES(?, ?, ?, ?)";
        try (PreparedStatement pstmt = _connection.prepareStatement(sql)) {
            pstmt.setString(1, main.getName());
            pstmt.setInt(2, major);
            pstmt.setInt(3, minor);
            pstmt.setInt(4, patch);
            pstmt.executeUpdate();
        } catch (SQLException e) {
        }
        for (var dep: jpm.getMainDependencies()) {
            String dependencyName = dep.getName();
            int dependencyMajor = 0;
            int dependencyMinor = 0;
            int dependencyPatch = 0;
            var dependencyMatcher = pattern.matcher(main.getVersion());
            if (!dependencyMatcher.matches()) {
                throw new RuntimeException("Invalid version");
            }
            try {
                dependencyMajor = Integer.parseInt(dependencyMatcher.group(1));
            } catch (Exception e) {
            }
            try {
                dependencyMinor = Integer.parseInt(dependencyMatcher.group(2));
            } catch (Exception e) {
            }
            try {
                dependencyPatch = Integer.parseInt(dependencyMatcher.group(3));
            } catch (Exception e) {
            }
            String dependencySql = "INSERT INTO DEPENDENCIES(FROM_NAME, FROM_VERSION_MAJOR, FROM_VERSION_MINOR, FROM_VERSION_PATCH,\n" +
                                   "                         TO_NAME, TO_VERSION_MAJOR, TO_VERSION_MINOR, TO_VERSION_PATCH)\n" +
                                   "            VALUES(?, ?, ?, ?, ?, ?, ?, ?)";
            try (PreparedStatement pstmt = _connection.prepareStatement(dependencySql)) {
                pstmt.setString(1, main.getName());
                pstmt.setInt(2, major);
                pstmt.setInt(3, minor);
                pstmt.setInt(4, patch);
                pstmt.setString(5, dependencyName);
                pstmt.setInt(6, dependencyMajor);
                pstmt.setInt(7, dependencyMinor);
                pstmt.setInt(8, dependencyPatch);
                pstmt.executeUpdate();
            } catch (SQLException e) {
            }
        }
    }

    public static JpmDatabase localDatabase() {
        Repository repo = new Repository();
        var path = repo.getRepositoryPath().resolve("jpm.db");
        return new JpmDatabase(path);
    }

    public void close() {
        try {
            if (_connection != null) {
                _connection.close();
            }
        } catch (SQLException e) {
        }
    }
}
