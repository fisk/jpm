package org.jpm;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;
import java.util.TimeZone;
import java.util.regex.Pattern;

public class JpmDatabase implements AutoCloseable {
    private Connection _connection;

    public JpmDatabase(String url, Properties info) {
        connect(url, info);
    }

    private void connect(String url, Properties info) {
        try {
            if (info == null) {
                _connection = DriverManager.getConnection(url);
            } else {
                _connection = DriverManager.getConnection(url, info);
            }
            try (Statement stmt = _connection.createStatement()) {
                stmt.execute("CREATE TABLE IF NOT EXISTS ARTIFACTS (\n" +
                             "    JPM_NAME varchar(255) NOT NULL,\n" +
                             "    VERSION_MAJOR integer,\n" +
                             "    VERSION_MINOR integer,\n" +
                             "    VERSION_PATCH integer,\n" +
                             "    CONSTRAINT PK_JPM_NAME_VERSION PRIMARY KEY (JPM_NAME, VERSION_MAJOR, VERSION_MINOR, VERSION_PATCH)\n" +
                             ");");
                stmt.execute("CREATE TABLE IF NOT EXISTS DEPENDENCIES (\n" + 
                            "    FROM_JPM_NAME varchar(255) NOT NULL,\n" + 
                            "    FROM_VERSION_MAJOR integer,\n" + 
                            "    FROM_VERSION_MINOR integer,\n" + 
                            "    FROM_VERSION_PATCH integer,\n" + 
                            "    TO_JPM_NAME varchar(255) NOT NULL,\n" + 
                            "    TO_VERSION_MAJOR integer,\n" + 
                            "    TO_VERSION_MINOR integer,\n" + 
                            "    TO_VERSION_PATCH integer,\n" + 
                            "    CONSTRAINT PK_FROM_TO_JPM_NAME_VERSION PRIMARY KEY (\n" + 
                            "        FROM_JPM_NAME, FROM_VERSION_MAJOR, FROM_VERSION_MINOR, FROM_VERSION_PATCH,\n" + 
                            "        TO_JPM_NAME, TO_VERSION_MAJOR, TO_VERSION_MINOR, TO_VERSION_PATCH\n" + 
                            "    ),\n" + 
                            "    CONSTRAINT FK_FROM_JPM_NAME_VERSION FOREIGN KEY (\n" + 
                            "        FROM_JPM_NAME, FROM_VERSION_MAJOR, FROM_VERSION_MINOR, FROM_VERSION_PATCH\n" + 
                            "    ) REFERENCES ARTIFACTS(\n" + 
                            "        JPM_NAME, VERSION_MAJOR, VERSION_MINOR, VERSION_PATCH\n" + 
                            "    ),\n" + 
                            "    CONSTRAINT FK_TO_JPM_NAME_VERSION FOREIGN KEY (\n" + 
                            "        TO_JPM_NAME, TO_VERSION_MAJOR, TO_VERSION_MINOR, TO_VERSION_PATCH\n" + 
                            "    ) REFERENCES ARTIFACTS(\n" + 
                            "        JPM_NAME, VERSION_MAJOR, VERSION_MINOR, VERSION_PATCH\n" + 
                            "    )\n" + 
                            ");");
            }
        } catch (SQLException e) {
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
        String sql = "INSERT INTO ARTIFACTS(JPM_NAME, VERSION_MAJOR, VERSION_MINOR, VERSION_PATCH) VALUES(?, ?, ?, ?);";
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
            String dependencySql = "INSERT INTO DEPENDENCIES(FROM_JPM_NAME, FROM_VERSION_MAJOR, FROM_VERSION_MINOR, FROM_VERSION_PATCH,\n" +
                                    "TO_JPM_NAME, TO_VERSION_MAJOR, TO_VERSION_MINOR, TO_VERSION_PATCH)\n" +
                                    "VALUES(?, ?, ?, ?, ?, ?, ?, ?);";
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
        var url = "jdbc:sqlite:" + repo.getRepositoryPath().resolve("jpm.db");
        return new JpmDatabase(url, null);
    }

    public static JpmDatabase remoteDatabase() {
        var path = "jdbc:mysql://" + MySQLConfig._host + "/" + MySQLConfig._database + "?user=" + MySQLConfig._user + "&password=" + MySQLConfig._password;
        var info = new Properties();
        info.put("serverTimezone", TimeZone.getDefault().getDisplayName(false, TimeZone.SHORT));
        return new JpmDatabase(path, info);
    }

    public JpmFile getJpm(String name, String version) {
        var pattern = Pattern.compile("(\\d+)\\.(\\d+)\\.(\\d+)");
        int major = 0;
        int minor = 0;
        int patch = 0;
        JpmFile jpmFile = null;
        var matcher = pattern.matcher(version);
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
        String sql = "SELECT JPM_NAME, VERSION_MAJOR, VERSION_MINOR, VERSION_PATCH FROM ARTIFACTS " +
                     "WHERE JPM_NAME=? AND VERSION_MAJOR=? AND VERSION_MINOR=? AND VERSION_PATCH=?;";
        try (PreparedStatement pstmt = _connection.prepareStatement(sql)) {
            pstmt.setString(1, name);
            pstmt.setInt(2, major);
            pstmt.setInt(3, minor);
            pstmt.setInt(4, patch);
            ResultSet rs = pstmt.executeQuery();
            if (!rs.first()) {
                return null;
            }
            String resultName = rs.getString(1);
            int resultMajor = rs.getInt(2);
            int resultMinor = rs.getInt(3);
            int resultPatch = rs.getInt(4);
            jpmFile = JpmFile.createJpmFile(resultName, "" + resultMajor + "." + resultMinor + "." + resultPatch);
        } catch (SQLException e) {
            System.out.println(e);
        }
        sql = "SELECT TO_JPM_NAME, TO_VERSION_MAJOR, TO_VERSION_MINOR, TO_VERSION_PATCH FROM DEPENDENCIES " +
              "WHERE FROM_JPM_NAME=? AND FROM_VERSION_MAJOR=? AND FROM_VERSION_MINOR=? AND FROM_VERSION_PATCH=?;";
        try (PreparedStatement pstmt = _connection.prepareStatement(sql)) {
            pstmt.setString(1, name);
            pstmt.setInt(2, major);
            pstmt.setInt(3, minor);
            pstmt.setInt(4, patch);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                String resultName = rs.getString(1);
                int resultMajor = rs.getInt(2);
                int resultMinor = rs.getInt(3);
                int resultPatch = rs.getInt(4);
                var jpmFileDep = new JpmFile.JpmReference(resultName, "" + resultMajor + "." + resultMinor + "." + resultPatch);
                jpmFile.getMainDependencies().add(jpmFileDep);
            }
        } catch (SQLException e) {
            System.out.println(e);
        }
        return jpmFile;
    }
    
    public JpmFile getJpm(String name) {
        String sql = "SELECT VERSION_MAJOR, VERSION_MINOR, VERSION_PATCH FROM ARTIFACTS " +
                     "WHERE JPM_NAME=? ORDER BY VERSION_MAJOR DESC, VERSION_MINOR DESC, VERSION_PATCH DESC;";
        try (PreparedStatement pstmt = _connection.prepareStatement(sql)) {
            pstmt.setString(1, name);
            ResultSet rs = pstmt.executeQuery();
            if (!rs.first()) {
                return null;
            }
            int resultMajor = rs.getInt(1);
            int resultMinor = rs.getInt(2);
            int resultPatch = rs.getInt(3);
            return getJpm(name, "" + resultMajor + "." + resultMinor + "." + resultPatch);
        } catch (SQLException e) {
            System.out.println(e);
        }
        return null;
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
