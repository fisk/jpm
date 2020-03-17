package org.jpm;

import static java.util.stream.Collectors.toList;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.lang.reflect.UndeclaredThrowableException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.tools.ToolProvider;

import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.ExportsTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.ModuleTree;
import com.sun.source.tree.ModuleTree.ModuleKind;
import com.sun.source.tree.ProvidesTree;
import com.sun.source.tree.RequiresTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.TreeVisitor;
import com.sun.source.tree.UsesTree;
import com.sun.source.util.JavacTask;

public class ModuleInfoParser {
    static final int ACC_OPEN = 0x0020;
    static final int ACC_TRANSITIVE = 0x0020;
    static final int ACC_STATIC = 0x0040;

    public interface ModuleVisitor {
        public void visitModule(int modifiers, String name);
        public void visitRequires(int modifiers, String module);
        public void visitExports(String packaze, List<String> toModules);
        public void visitOpens(String packaze, List<String> toModules);
        public void visitUses(String service);
        public void visitProvides(String service, List<String> providers);
    }

    static class ModuleHandler {
        ModuleVisitor _moduleVisitor;

        ModuleHandler(ModuleVisitor moduleVisitor) {
            _moduleVisitor = moduleVisitor;
        }

        private static void accept(TreeVisitor<?, ?> visitor, Tree node) {
            node.accept(visitor, null);
        }

        private static String qualifiedString(Tree tree) {
            switch (tree.getKind()) {
            case IDENTIFIER:
                return ((IdentifierTree) tree).getName().toString();
            case MEMBER_SELECT:
                var select = (MemberSelectTree) tree;
                return qualifiedString(select.getExpression()) + '.' + select.getIdentifier().toString();
            default:
                throw new AssertionError(tree.toString());
            }
        }

        private static List<String> asList(List<? extends ExpressionTree> trees) {
            if (trees == null) {
                return Collections.<String>emptyList();
            }
            return trees.stream().map(ModuleHandler::qualifiedString).collect(toList());
        }

        @SuppressWarnings("static-method")
        public void visitCompilationUnit(CompilationUnitTree node, TreeVisitor<?, ?> visitor) {
            for (var decl: node.getTypeDecls()) {
                if (!(decl instanceof ModuleTree)) {
                    continue;
                }
                accept(visitor, decl);
            }
        }

        public void visitModule(ModuleTree node, TreeVisitor<?, ?> visitor) {
            int modifiers = node.getModuleType() == ModuleKind.OPEN ? ACC_OPEN : 0;
            _moduleVisitor.visitModule(modifiers, qualifiedString(node.getName()));
            node.getDirectives().forEach(n -> accept(visitor, n));
        }

        public void visitRequires(RequiresTree node, @SuppressWarnings("unused") TreeVisitor<?, ?> __) {
            int modifiers = (node.isStatic()? ACC_STATIC: 0) | (node.isTransitive()? ACC_TRANSITIVE: 0);
            _moduleVisitor.visitRequires(modifiers, qualifiedString(node.getModuleName()));
        }

        public void visitExports(ExportsTree node, @SuppressWarnings("unused") TreeVisitor<?, ?> __) {
            _moduleVisitor.visitExports(qualifiedString(node.getPackageName()), asList(node.getModuleNames()));
        }

        public void visitUses(UsesTree node, @SuppressWarnings("unused") TreeVisitor<?, ?> __) {
            _moduleVisitor.visitUses(qualifiedString(node.getServiceName()));
        }

        public void visitProvides(ProvidesTree node, @SuppressWarnings("unused") TreeVisitor<?, ?> __) {
            _moduleVisitor.visitProvides(qualifiedString(node.getServiceName()), asList(node.getImplementationNames()));
        }

        interface Visitee {
            void visit(ModuleHandler handler, Tree node, TreeVisitor<?, ?> visitor);

            static Visitee of(Method method) {
                return (handler, node, visitor) -> {
                    try {
                        method.invoke(handler, node, visitor);
                    } catch (IllegalAccessException e) {
                        throw new AssertionError(e);
                    } catch (InvocationTargetException e) {
                        Throwable cause = e.getCause();
                        if (cause instanceof RuntimeException) {
                            throw (RuntimeException)cause;
                        }
                        if (cause instanceof Error) {
                            throw (Error)cause;
                        }
                        throw new UndeclaredThrowableException(cause);
                    }
                };
            }
        }

        static final Map<String, Visitee> METHOD_MAP;
        static {
            METHOD_MAP = Arrays.stream(ModuleHandler.class.getMethods())
                .filter(m -> m.getDeclaringClass() == ModuleHandler.class)
                .collect(Collectors.toMap(Method::getName, ModuleHandler.Visitee::of));
        }
    }

    public static void parse(Path moduleInfoPath, ModuleVisitor moduleVisitor) throws IOException {
        var compiler = ToolProvider.getSystemJavaCompiler();
        try(var fileManager = compiler.getStandardFileManager(null, null, null)) {
            var compilationUnits = fileManager.getJavaFileObjects(moduleInfoPath);
            var task = compiler.getTask(null, fileManager, null, null, null, compilationUnits);
            var javacTask = (JavacTask)task;
            var units = javacTask.parse();
            var unit = units.iterator().next();

            var moduleHandler = new ModuleHandler(moduleVisitor);
            var visitor = (TreeVisitor<?,?>)Proxy.newProxyInstance(TreeVisitor.class.getClassLoader(), new Class<?>[]{ TreeVisitor.class },
                                                                   (proxy, method, args) -> {
                                                                       ModuleHandler.METHOD_MAP
                                                                       .getOrDefault(method.getName(), (handler, node, v) -> { 
                                                                               throw new AssertionError("Invalid node: " + node);
                                                                           })
                                                                       .visit(moduleHandler, (Tree)args[0], (TreeVisitor<?,?>)proxy);
                                                                       return null;
                                                                   });
            unit.accept(visitor, null);
        }
    }
}
