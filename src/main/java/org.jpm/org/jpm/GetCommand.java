package org.jpm;

import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.module.ModuleDescriptor.Version;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.regex.Pattern;

import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPReply;

public class GetCommand {
    private String _module;
    private String _version;
    private Project _project = new Project();

    private Pattern _modulePattern = Pattern.compile("(.*)-(\\d+\\.\\d+\\.\\d+)\\.jar");

    private String _libType;

    public GetCommand(String module, String version, String libType) {
        _module = module;
        _version = version;
        _libType = libType;
    }

    public void run() {
        boolean error = false;
        var ftp = new FTPClient();
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

            var selectedVersion = _version;

            if (_version == null) {
                var files = ftp.listFiles("jpm");
                Version latestVersion = null;
                for (var file: files) {
                    if (file.isDirectory()) {
                        continue;
                    }
                    String fileName = file.getName();
                    var matcher = _modulePattern.matcher(fileName);
                    if (!matcher.matches()) {
                        continue;
                    }
                    String module = matcher.group(1);
                    if (!module.equals(_module)) {
                        continue;
                    }
                    String versionRaw = matcher.group(2);
                    var version = Version.parse(versionRaw);
                    if (latestVersion == null || version.compareTo(latestVersion) > 0) {
                        latestVersion = version;
                    }
                }
                selectedVersion = latestVersion.toString();
            }

            var jarName = _module + "-" + selectedVersion + ".jar";
            var remoteFileName = "jpm/" + jarName;
            var localFile = _project.getLibraryPath().resolve(_libType).resolve(jarName).toFile();
            System.out.println("Downloading " + remoteFileName + " as " + localFile);
            try (var os = new BufferedOutputStream(new FileOutputStream(localFile))) {
                ftp.retrieveFile(remoteFileName, os);
            }

            ftp.logout();
        } catch (IOException e) {
            error = true;
            e.printStackTrace();
        } finally {
            if(ftp.isConnected()) {
                try {
                    ftp.disconnect();
                } catch(IOException ioe) {
                    // do nothing
                }
            }
        }
    }
}
