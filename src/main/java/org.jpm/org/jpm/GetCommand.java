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

    public GetCommand(String module, String version) {
        _module = module;
        _version = version;
    }

    public void run() {
        boolean error = false;
        var ftp = new FTPClient();
        try {
            String server = "localhost"; // For now...
            int port = 21;
            String user = "ftp_user";
            String pass = "jpm";
            ftp.connect(server, port);
            ftp.login(user, pass);
            ftp.enterLocalPassiveMode();
            ftp.setFileType(FTP.BINARY_FILE_TYPE);
            System.out.println("Connected to " + server + ".");
            System.out.print(ftp.getReplyString());
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
                for (var file : files) {
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
                    System.out.println("Module: " + module + ", version: " + versionRaw);
                    var version = Version.parse(versionRaw);
                    if (latestVersion == null || version.compareTo(latestVersion) > 0) {
                        latestVersion = version;
                    }
                }
                selectedVersion = latestVersion.toString();
            }

            var jarName = _module + "-" + selectedVersion + ".jar";
            var remoteFileName = "jpm/" + jarName;
            var localFile = _project.getLibraryPath().resolve(jarName).toFile();
            var os = new BufferedOutputStream(new FileOutputStream(localFile));
            boolean success = ftp.retrieveFile(remoteFileName, os);
            os.close();

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
            System.out.println("Disconnected");
        }
    }
}
