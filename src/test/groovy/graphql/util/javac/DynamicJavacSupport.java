package graphql.util.javac;

import javax.tools.DiagnosticCollector;
import javax.tools.FileObject;
import javax.tools.ForwardingJavaFileManager;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static java.util.Objects.requireNonNull;


/**
 * This utility allows is to dynamically create Java classes and place them into
 * floating class loaders.  This will allow us to test class loader challenges
 * <p>
 * Proprs to https://www.baeldung.com/java-string-compile-execute-code where
 * most of this code came from.
 */
public class DynamicJavacSupport {

    private final JavaCompiler compiler;
    private final InMemoryFileManager manager;

    public DynamicJavacSupport(ClassLoader parentClassLoader) {
        compiler = ToolProvider.getSystemJavaCompiler();
        StandardJavaFileManager standardFileManager = compiler.getStandardFileManager(null, null, null);
        manager = new InMemoryFileManager(parentClassLoader, standardFileManager);
    }


    public <T> Class<T> compile(String qualifiedClassName, String sourceCode) throws ClassNotFoundException {

        List<JavaFileObject> sourceFiles = Collections.singletonList(new JavaSourceFromString(qualifiedClassName, sourceCode));

        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        JavaCompiler.CompilationTask task = compiler.getTask(null, manager, diagnostics, null, null, sourceFiles);

        boolean result = task.call();

        if (!result) {
            diagnostics.getDiagnostics()
                    .forEach(d -> System.out.printf("dyna-javac : %s\n", d));
            throw new IllegalStateException("Could not compile " + qualifiedClassName + " as a class");
        } else {
            ClassLoader classLoader = manager.getClassLoader(null);
            Class<?> clazz = classLoader.loadClass(qualifiedClassName);
            return (Class<T>) clazz;
        }
    }

    static class InMemoryFileManager extends ForwardingJavaFileManager<JavaFileManager> {
        private final InMemoryClassLoader loader;
        private final Map<String, JavaClassAsBytes> compiledClasses;

        InMemoryFileManager(ClassLoader parentClassLoader, StandardJavaFileManager standardManager) {
            super(standardManager);
            this.compiledClasses = new ConcurrentHashMap<>();
            this.loader = new InMemoryClassLoader(parentClassLoader, this);
        }

        @Override
        public JavaFileObject getJavaFileForOutput(Location location,
                                                   String className, JavaFileObject.Kind kind, FileObject sibling) {

            JavaClassAsBytes classAsBytes = new JavaClassAsBytes(className, kind);
            compiledClasses.put(className, classAsBytes);

            return classAsBytes;
        }

        public Map<String, JavaClassAsBytes> getBytesMap() {
            return compiledClasses;
        }

        @Override
        public ClassLoader getClassLoader(Location location) {
            return loader;
        }
    }

    static class InMemoryClassLoader extends ClassLoader {

        private InMemoryFileManager manager;

        InMemoryClassLoader(ClassLoader parentClassLoader, InMemoryFileManager manager) {
            super(parentClassLoader);
            this.manager = requireNonNull(manager, "manager must not be null");
        }

        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {

            Map<String, JavaClassAsBytes> compiledClasses = manager.getBytesMap();

            if (compiledClasses.containsKey(name)) {
                byte[] bytes = compiledClasses.get(name).getBytes();
                return defineClass(name, bytes, 0, bytes.length);
            } else {
                throw new ClassNotFoundException();
            }
        }

    }

    static class JavaSourceFromString extends SimpleJavaFileObject {


        private String sourceCode;

        JavaSourceFromString(String name, String sourceCode) {
            super(URI.create("string:///" + name.replace('.', '/') + Kind.SOURCE.extension),
                    Kind.SOURCE);
            this.sourceCode = requireNonNull(sourceCode, "sourceCode must not be null");
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return sourceCode;
        }

    }

    static class JavaClassAsBytes extends SimpleJavaFileObject {


        protected ByteArrayOutputStream bos =
                new ByteArrayOutputStream();

        JavaClassAsBytes(String name, Kind kind) {
            super(URI.create("string:///" + name.replace('.', '/')
                    + kind.extension), kind);
        }

        public byte[] getBytes() {
            return bos.toByteArray();
        }

        @Override
        public OutputStream openOutputStream() {
            return bos;
        }

    }


}
