package graphql.archunit

import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import org.jspecify.annotations.Nullable
import spock.lang.Specification

import java.lang.reflect.AnnotatedType
import java.lang.reflect.Executable
import java.lang.reflect.Modifier
import java.lang.reflect.TypeVariable

/**
 * This test ensures that @NullMarked public API classes and interfaces with unbounded
 * type parameters use {@code <T extends @Nullable Object>} instead of bare {@code <T>}.
 *
 * <p>Under JSpecify's @NullMarked semantics, a bare {@code <T>} is equivalent to
 * {@code <T extends @NonNull Object>}, which prevents Kotlin callers from using
 * nullable type arguments. This is almost always wrong for generic container types
 * and interfaces.
 *
 * @see <a href="https://github.com/graphql-java/graphql-java/issues/4364">Issue #4364</a>
 */
class NullableTypeParameterBoundsCheck extends Specification {

    /**
     * Exemptions for class-level type parameters that are intentionally non-null.
     * Format: "fully.qualified.ClassName"
     */
    private static final Set<String> CLASS_LEVEL_EXEMPTIONS = [
    ] as Set

    /**
     * Exemptions for method-level type parameters that are intentionally non-null.
     * Format: "fully.qualified.ClassName#methodName"
     */
    private static final Set<String> METHOD_LEVEL_EXEMPTIONS = [
    ] as Set

    def "NullMarked public API classes should use @Nullable bounds on unbounded type parameters"() {
        given:
        def archClasses = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("graphql")
                .stream()
                .filter { it.isAnnotatedWith("org.jspecify.annotations.NullMarked") }
                .filter { it.isAnnotatedWith("graphql.PublicApi") || it.isAnnotatedWith("graphql.ExperimentalApi") || it.isAnnotatedWith("graphql.PublicSpi") }
                .collect()

        when:
        def violations = []

        for (archClass in archClasses) {
            Class<?> clazz
            try {
                clazz = Class.forName(archClass.name)
            } catch (ClassNotFoundException ignored) {
                continue
            }

            // Check class-level type parameters
            if (!CLASS_LEVEL_EXEMPTIONS.contains(clazz.name)) {
                for (TypeVariable<?> typeParam : clazz.typeParameters) {
                    if (isUnboundedWithoutNullable(typeParam)) {
                        violations.add("${clazz.name}<${typeParam.name}> - class-level type parameter needs 'extends @Nullable Object'")
                    }
                }
            }

            // Check method-level type parameters on public/protected methods
            for (Executable method : (clazz.declaredMethods + clazz.declaredConstructors)) {
                if (!Modifier.isPublic(method.modifiers) && !Modifier.isProtected(method.modifiers)) {
                    continue
                }
                String methodKey = "${clazz.name}#${method.name}"
                if (METHOD_LEVEL_EXEMPTIONS.contains(methodKey)) {
                    continue
                }
                for (TypeVariable<?> typeParam : method.typeParameters) {
                    if (isUnboundedWithoutNullable(typeParam)) {
                        violations.add("${clazz.name}#${method.name}<${typeParam.name}> - method-level type parameter needs 'extends @Nullable Object'")
                    }
                }
            }
        }

        then:
        if (!violations.isEmpty()) {
            throw new AssertionError("""The following @NullMarked public API type parameters are missing '@Nullable Object' bounds:
${violations.sort().join("\n")}

Under @NullMarked, a bare <T> means <T extends @NonNull Object>, which prevents Kotlin callers from using nullable type arguments.
Change <T> to <T extends @Nullable Object> for type parameters that should accept nullable types.
See https://github.com/graphql-java/graphql-java/issues/4364""")
        }
    }

    /**
     * Checks if a type parameter is effectively unbounded (bound is just Object)
     * and does NOT have a @Nullable annotation on that bound.
     */
    private static boolean isUnboundedWithoutNullable(TypeVariable<?> typeParam) {
        AnnotatedType[] annotatedBounds = typeParam.annotatedBounds
        if (annotatedBounds.length != 1) {
            return false
        }
        AnnotatedType bound = annotatedBounds[0]
        // Only flag unbounded params (bound is Object)
        if (bound.type != Object.class) {
            return false
        }
        // Check if the bound has @Nullable
        return !bound.isAnnotationPresent(Nullable.class)
    }
}
