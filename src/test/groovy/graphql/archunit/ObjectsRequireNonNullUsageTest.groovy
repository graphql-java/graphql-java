package graphql.archunit

import com.tngtech.archunit.core.domain.JavaClasses
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import spock.lang.Specification

/**
 * Ensures that Objects.requireNonNull() is not used.
 * Use graphql.Assert instead for consistent assertion behavior.
 */
class ObjectsRequireNonNullUsageTest extends Specification {

    private static final JavaClasses importedClasses = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("graphql")

    private static final Set<String> EXEMPTION_LIST = [
            "graphql.analysis.values.ValueTraverser\$InputElements",
            "graphql.execution.AsyncExecutionStrategy",
            "graphql.execution.AsyncSerialExecutionStrategy",
            "graphql.execution.Execution",
            "graphql.execution.ExecutionStrategy",
            "graphql.execution.ResolveType",
            "graphql.execution.SubscriptionExecutionStrategy",
            "graphql.execution.instrumentation.fieldvalidation.SimpleFieldValidation",
            "graphql.execution.reactive.ReactiveSupport\$PublisherToCompletableFuture",
            "graphql.introspection.Introspection",
            "graphql.language.Document",
            "graphql.language.NodeUtil\$DirectivesHolder",
            "graphql.language.PrettyAstPrinter",
            "graphql.language.SelectionSet",
            "graphql.normalized.ExecutableNormalizedField",
            "graphql.normalized.ExecutableNormalizedOperationFactory\$ExecutableNormalizedOperationFactoryImpl",
            "graphql.normalized.nf.NormalizedDocumentFactory\$NormalizedDocumentFactoryImpl",
            "graphql.normalized.nf.NormalizedField",
            "graphql.schema.SchemaElementChildrenContainer",
            "graphql.schema.SchemaTransformer",
            "graphql.schema.diff.DiffCtx",
            "graphql.schema.diff.SchemaDiff",
            "graphql.schema.diffing.DiffImpl",
            "graphql.schema.diffing.SchemaDiffing",
            "graphql.schema.diffing.ana.SchemaDifference\$DirectiveModification",
            "graphql.schema.diffing.ana.SchemaDifference\$EnumModification",
            "graphql.schema.diffing.ana.SchemaDifference\$InputObjectModification",
            "graphql.schema.diffing.ana.SchemaDifference\$InterfaceModification",
            "graphql.schema.diffing.ana.SchemaDifference\$ObjectModification",
            "graphql.schema.diffing.ana.SchemaDifference\$ScalarModification",
            "graphql.schema.diffing.ana.SchemaDifference\$UnionModification",
            "graphql.schema.idl.SchemaGeneratorAppliedDirectiveHelper",
            "graphql.schema.idl.SchemaGeneratorHelper",
            "graphql.schema.idl.SchemaParser",
            "graphql.schema.idl.SchemaTypeChecker",
            "graphql.schema.idl.TypeDefinitionRegistry",
            "graphql.schema.impl.GraphQLTypeCollectingVisitor",
            "graphql.schema.transform.FieldVisibilitySchemaTransformation",
            "graphql.util.Anonymizer",
            "graphql.util.CyclicSchemaAnalyzer\$FindCyclesImpl",
            "graphql.util.FpKit",
            "graphql.util.NodeMultiZipper",
            "graphql.util.TraverserState",
            "graphql.util.TreeTransformer",
            "graphql.util.querygenerator.QueryGenerator",
            "graphql.util.querygenerator.QueryGeneratorFieldSelection",
    ] as Set

    def "should not use Objects.requireNonNull - use graphql.Assert instead"() {
        when:
        def violatingClasses = importedClasses.stream()
                .filter { javaClass ->
                    javaClass.getAccessesFromSelf().any { access ->
                        access.targetOwner.fullName == "java.util.Objects" &&
                                access.target.name == "requireNonNull"
                    }
                }
                .map { it.name }
                .filter { !EXEMPTION_LIST.contains(it) }
                .sorted()
                .collect()

        then:
        if (!violatingClasses.isEmpty()) {
            throw new AssertionError("""The following classes use Objects.requireNonNull():
${violatingClasses.join("\n")}

Use graphql.Assert instead of Objects.requireNonNull(). See coding-guidelines.md.
If a class must use Objects.requireNonNull(), add it to the exemption list in ${ObjectsRequireNonNullUsageTest.class.simpleName}.groovy.""")
        }
    }

    def "exempted classes should actually use Objects.requireNonNull"() {
        when:
        def exemptedButClean = EXEMPTION_LIST.findAll { exemptedClassName ->
            def javaClass = importedClasses.stream()
                    .filter { it.name == exemptedClassName }
                    .findFirst()
            if (!javaClass.isPresent()) return true
            !javaClass.get().getAccessesFromSelf().any { access ->
                access.targetOwner.fullName == "java.util.Objects" &&
                        access.target.name == "requireNonNull"
            }
        }.sort()

        then:
        if (!exemptedButClean.isEmpty()) {
            throw new AssertionError("""The following classes are in the exemption list but no longer use Objects.requireNonNull():
${exemptedButClean.join("\n")}

Please remove them from the exemption list in ${ObjectsRequireNonNullUsageTest.class.simpleName}.groovy.""")
        }
    }
}
