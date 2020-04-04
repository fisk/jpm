module org.jpm {
    exports org.jpm;
    requires jdk.compiler;
    requires org.objectweb.asm;
    requires org.objectweb.asm.tree;
    requires org.objectweb.asm.util;
    requires commons.net;
    requires sqlite.jdbc;
    requires mysql.connector.java;
    requires java.sql;
}
