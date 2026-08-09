package graphql

import spock.lang.IgnoreIf
import spock.lang.Specification

import java.util.jar.JarFile
import java.util.regex.Pattern

/**
 * Guards #4436: published jar must not ship shaded Guava classfiles whose
 * RuntimeInvisibleAnnotations still point at annotation types that are absent
 * from the jar (breaks consumers compiling with -Xlint:classfile -Werror).
 *
 * Runs only when the published jar has already been built (shadowJar/buildNewJar).
 */
class ShadedJarAnnotationRefsTest extends Specification {

    private static final Pattern ANNOTATION_DESC = Pattern.compile(
            'L(graphql/)?((?:com/google/(?:common/annotations|errorprone/annotations|j2objc/annotations)|org/checkerframework|javax/annotation)[^;]*);'
    )

    private static File findPublishedJar() {
        def libs = new File("build/libs")
        if (!libs.directory) {
            return null
        }
        def jars = libs.listFiles({ dir, name ->
            name.startsWith("graphql-java-") && name.endsWith(".jar") &&
                    !name.contains("sources") && !name.contains("javadoc") &&
                    !name.contains("tmp")
        } as FilenameFilter)
        if (jars == null || jars.length == 0) {
            return null
        }
        return jars.toList().sort { -it.lastModified() }.first()
    }

    @IgnoreIf({ ShadedJarAnnotationRefsTest.findPublishedJar() == null })
    def "shaded Guava classes do not reference missing annotation types"() {
        given:
        def jar = findPublishedJar()
        def present = new HashSet<String>()
        def problems = new LinkedHashSet<String>()

        when:
        new JarFile(jar).withCloseable { jarFile ->
            jarFile.entries().each { entry ->
                if (entry.name.endsWith(".class")) {
                    present.add(entry.name.substring(0, entry.name.length() - 6).replace('/', '.'))
                }
            }
            jarFile.entries().each { entry ->
                if (!entry.name.startsWith("graphql/com/google/") || !entry.name.endsWith(".class")) {
                    return
                }
                def bytes = jarFile.getInputStream(entry).bytes
                def text = new String(bytes, "ISO-8859-1")
                def matcher = ANNOTATION_DESC.matcher(text)
                while (matcher.find()) {
                    def relocatedPrefix = matcher.group(1)
                    def typePath = matcher.group(2).replace('/', '.')
                    if (relocatedPrefix == null) {
                        problems.add("${entry.name} -> ${typePath} (not relocated)")
                        continue
                    }
                    def typeName = "graphql." + typePath
                    if (!present.contains(typeName)) {
                        problems.add("${entry.name} -> ${typeName} (class missing from jar)")
                    }
                }
            }
        }

        then:
        problems.isEmpty()
    }
}
