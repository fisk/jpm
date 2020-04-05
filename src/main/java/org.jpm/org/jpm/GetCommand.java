package org.jpm;

import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Pattern;

import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPReply;

public class GetCommand {
    private Project _project = new Project();
    private List<JpmFile> _jpms;

    public GetCommand(List<JpmFile> jpms) {
        _jpms = jpms;
    }
    
    public GetCommand(String module, String version) {
        try (var db = JpmDatabase.remoteDatabase()) {
            _jpms = new ArrayList<>(1);
            JpmFile jpm = null;
            if (version == null) {
                jpm = db.getJpm(module);
            } else {
                jpm = db.getJpm(module, version);
            }
            if (jpm == null) {
                System.out.println("Unknown jpm: " + module + "-" + version);
                throw new RuntimeException("Unknown jpm: " + module + "-" + version);
            }
            _jpms.add(jpm);
        }
    }

    public void run() {
        pruneDownloadedJpms();
        downloadJpms();
    }
    
    private void pruneDownloadedJpms() {
        var deps = new DependencyDetector(_project);
        var jpms = new ArrayList<JpmFile>();
        for (var jpm: _jpms) {
            var dep = deps.getDependency(jpm.getMain().getName());
            if (!dep.isSystem() && dep.getBinaryPath() == null) {
                jpms.add(jpm);
            }
        }
        _jpms = jpms;
    }

    private static Pattern _versionPattern = Pattern.compile("(\\d+)\\.(\\d+)\\.(\\d+)");
    
    private static int versionCompareTo(String v1, String v2) {
        try {
            int v1_major = 0;
            int v1_minor = 0;
            int v1_patch = 0;
            int v2_major = 0;
            int v2_minor = 0;
            int v2_patch = 0;
            var matcher = _versionPattern.matcher(v1);
            if (matcher.matches()) {
                v1_major = Integer.parseInt(matcher.group(1));
                v1_minor = Integer.parseInt(matcher.group(2));
                v1_patch = Integer.parseInt(matcher.group(3));
            } else {
                throw new RuntimeException("Cant find version");
            }
            matcher = _versionPattern.matcher(v2);
            if (matcher.matches()) {
                v2_major = Integer.parseInt(matcher.group(1));
                v2_minor = Integer.parseInt(matcher.group(2));
                v2_patch = Integer.parseInt(matcher.group(3));
            } else {
                throw new RuntimeException("Cant find version");
            }
            
            if (v1_major < v2_major) {
                return -2;
            }
            if (v1_major > v2_major) {
                return 2;
            }
            if (v1_minor < v2_minor) {
                return -1;
            }
            if (v1_minor > v2_minor) {
                return 1;
            }
            if (v1_patch < v2_patch) {
                return -1;
            }
            if (v1_patch > v2_patch) {
                return 1;
            }
            return 0;
        } catch (Exception e) {
            throw new RuntimeException("Cant find version");
        }
    }
    
    private void downloadJpms() {
        if (_jpms.isEmpty()) {
            return;
        }
        
        // Traverse dependencies
        var deps = new HashSet<JpmFile>();
        var tdeps = new HashSet<JpmFile>();
        var stack = new LinkedList<JpmFile>();
        try (var db = JpmDatabase.remoteDatabase()) {
            for (var jpmFile: _jpms) {
                deps.add(jpmFile);
                for (var jpmRef: jpmFile.getMainDependencies()) {
                    var jpmDep = db.getJpm(jpmRef.getName(), jpmRef.getVersion());
                    stack.add(jpmDep);
                }
            }
            while (!stack.isEmpty()) {
                var jpmFile = stack.pop();
                if (deps.contains(jpmFile) || tdeps.contains(jpmFile)) {
                    continue;
                }
                tdeps.add(jpmFile);
                for (var jpmRef: jpmFile.getMainDependencies()) {
                    var jpmDep = db.getJpm(jpmRef.getName(), jpmRef.getVersion());
                    stack.add(jpmDep);
                }
            }
        }
        
        // Figure out version collisions
        var jpmMap = new HashMap<String, JpmFile>();
        var jpmDeletes = new ArrayList<JpmFile>();
        for (var jpm: deps) {
            jpmMap.put(jpm.getMain().getName(), jpm);
        }
        for (var jpm: tdeps) {
            var otherJpm = jpmMap.get(jpm.getMain().getName());
            if (otherJpm == null) {
                jpmMap.put(jpm.getMain().getName(), jpm);
            } else {
                int comparison = versionCompareTo(jpm.getMain().getVersion(), otherJpm.getMain().getVersion());
                if (comparison == 2 || comparison == -2) {
                    throw new RuntimeException("Incompatible JPM versions in transitive dependencies");
                }
                if (comparison == 1) {
                    jpmMap.put(jpm.getMain().getName(), jpm);
                    jpmDeletes.add(otherJpm);
                }
            }
        }
        for (var delJpm: jpmDeletes) {
            var otherJpm = jpmMap.get(delJpm.getMain().getName());
            if (deps.contains(delJpm)) {
                deps.remove(delJpm);
                tdeps.remove(delJpm);
                deps.add(otherJpm);
            } else {
                tdeps.remove(delJpm);
            }
        }
        
        // Download selected dependencies
        for (var jpm: deps) {
            downloadJpm(jpm, "main");
        }
        for (var jpm: tdeps) {
            downloadJpm(jpm, "transitive");
        }
    }
    
    private void downloadJpm(JpmFile jpm, String libType) {
        var ftp = new FTPClient();
        String jpmName = jpm.getMain().getName();
        String jpmVersion = jpm.getMain().getVersion();
        try {
            ftp.connect(FTPConfig._host, FTPConfig._port);
            ftp.login(FTPConfig._user, FTPConfig._password);
            ftp.enterLocalPassiveMode();
            ftp.setFileType(FTP.BINARY_FILE_TYPE);
            int reply = ftp.getReplyCode();
            if (!FTPReply.isPositiveCompletion(reply)) {
                ftp.disconnect();
                System.err.println("FTP server refused connection.");
                return;
            }

            var jarName = jpmName + "-" + jpmVersion + ".jar";
            var remoteFileName = "jpm/" + jarName;
            var localFile = _project.getLibraryPath().resolve(libType).resolve(jarName).toFile();
            System.out.println("Downloading " + remoteFileName + " as " + localFile);
            try (var os = new BufferedOutputStream(new FileOutputStream(localFile))) {
                ftp.retrieveFile(remoteFileName, os);
            }

            ftp.logout();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (ftp.isConnected()) {
                try {
                    ftp.disconnect();
                } catch (IOException ioe) {
                    // do nothing
                }
            }
        }
    }
}
