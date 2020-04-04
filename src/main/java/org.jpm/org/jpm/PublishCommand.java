package org.jpm;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.module.ModuleDescriptor;
import java.lang.module.ModuleFinder;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Pattern;

import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPReply;

public class PublishCommand {
    private List<Path> _jars;

    public PublishCommand(List<Path> jars) {
        _jars = jars;
        if (jars.size() == 0) {
            var project = new Project();
            _jars.add(project.getBuildPath().resolve(project.getProjectJarName()));
        }
    }

    private Pattern _modulePattern = Pattern.compile("(.*)-(\\d+\\.\\d+\\.\\d+)\\.jar");

    static ModuleDescriptor getModuleDescriptor(Path jar) {
        ClassLoader cl = ClassLoader.getSystemClassLoader();
        try (JarFile jf = new JarFile(jar.toFile())) {
            JarEntry entry = jf.getJarEntry("module-info.class");
            try (InputStream in = jf.getInputStream(entry)) {
                return ModuleDescriptor.read(in);
            }
        } catch (IOException ioe) {
            throw new UncheckedIOException(ioe);
        }
    }

    public void run() {
        boolean error = false;
        var ftp = new FTPClient();
        try {
            ftp.connect(FTPConfig._host, FTPConfig._port);
            ftp.login(FTPConfig._user, FTPConfig._password);
            ftp.enterLocalPassiveMode();
            ftp.setFileType(FTP.BINARY_FILE_TYPE);
            System.out.println("Connected to " + FTPConfig._host + ".");
            System.out.print(ftp.getReplyString());
            int reply = ftp.getReplyCode();
            if (!FTPReply.isPositiveCompletion(reply)) {
                ftp.disconnect();
                System.err.println("FTP server refused connection.");
                return;
            }
            for (var jar: _jars) {
                var finder = ModuleFinder.of(jar);
                var module = finder.findAll().iterator().next();
                var descriptor = module.descriptor();
                var name = descriptor.name();
                var moduleVersion = descriptor.rawVersion();
                String version = null;
                if (moduleVersion.isPresent()) {
                    version = moduleVersion.get();
                } else {
                    var matcher = _modulePattern.matcher(jar.getFileName().toString());
                    if (matcher.matches()) {
                        version = matcher.group(2);
                        Cmd.run("jar --update --file " + jar + " --module-version " + version);
                    } else {
                        throw new RuntimeException("No version information available for jar");
                    }
                }
                var jarName = name + "-" + version + ".jar";
                var remoteFileName = "jpm/" + jarName;
                System.out.println("Uploading " + jar + " as " + jarName);
                try (InputStream input = new FileInputStream(jar.toFile())) {
                    ftp.storeFile(remoteFileName, input);
                }
                try (JpmDatabase db = JpmDatabase.remoteDatabase()) {
                    var jpmFile = JpmFile.fromJar(jar);
                    System.out.println("Adding jpm to remote database: " + jpmFile.getMain().getName() + "-" + jpmFile.getMain().getVersion());
                    db.addJpm(jpmFile);
                }
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
            System.out.println("Disconnected");
        }
    }
}
