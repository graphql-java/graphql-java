package graphql.schema

import graphql.TestUtil
import graphql.schema.idl.RuntimeWiring
import graphql.schema.idl.SchemaGenerator
import graphql.schema.idl.SchemaParser
import graphql.schema.idl.TestMockedWiringFactory
import spock.lang.Specification

import static graphql.Scalars.GraphQLString
import static graphql.schema.GraphQLFieldDefinition.newFieldDefinition
import static graphql.schema.GraphQLObjectType.newObject

/**
 * Comparison tests for FastBuilder vs standard Builder.
 *
 * This is the PRIMARY way to verify FastBuilder correctness - by comparing schemas
 * built with FastBuilder against identical schemas built via SDL parsing with the
 * standard Builder. This ensures FastBuilder maintains semantic equivalence with
 * real-world schema construction.
 *
 * The asymmetry is intentional: SDL → SchemaParser → standard Builder vs direct
 * FastBuilder calls. This verifies FastBuilder produces the same result as the
 * production SDL parsing path.
 */
class FastBuilderComparisonTest extends Specification {

    /**
     * Builds a schema from SDL using the standard path:
     * SDL → SchemaParser → SchemaGenerator → standard Builder
     */
    GraphQLSchema buildSchemaFromSDL(String sdl) {
        def typeRegistry = new SchemaParser().parse(sdl)
        def wiring = RuntimeWiring.newRuntimeWiring()
                .wiringFactory(new TestMockedWiringFactory())
                .build()
        def options = SchemaGenerator.Options.defaultOptions()
        return new SchemaGenerator().makeExecutableSchema(options, typeRegistry, wiring)
    }

    /**
     * Builds a schema using FastBuilder with programmatically created types.
     *
     * @param queryType The query root type (required)
     * @param mutationType The mutation root type (optional)
     * @param subscriptionType The subscription root type (optional)
     * @param additionalTypes Additional types to add to schema
     * @param additionalDirectives Additional directives to add to schema
     * @param codeRegistry The code registry builder (optional, creates new if null)
     * @return The built GraphQLSchema
     */
    GraphQLSchema buildSchemaWithFastBuilder(
            GraphQLObjectType queryType,
            GraphQLObjectType mutationType = null,
            GraphQLObjectType subscriptionType = null,
            List<GraphQLNamedType> additionalTypes = [],
            List<GraphQLDirective> additionalDirectives = [],
            GraphQLCodeRegistry.Builder codeRegistry = null
    ) {
        def registry = codeRegistry ?: GraphQLCodeRegistry.newCodeRegistry()

        def builder = new GraphQLSchema.FastBuilder(registry, queryType, mutationType, subscriptionType)

        additionalTypes.each { type ->
            if (type != null) {
                builder.addType(type)
            }
        }

        additionalDirectives.each { directive ->
            if (directive != null) {
                builder.additionalDirective(directive)
            }
        }

        return builder.build()
    }

    /**
     * Built-in scalar type names that may differ between FastBuilder and standard Builder.
     */
    private static final Set<String> BUILT_IN_SCALARS = [
        "String", "Int", "Float", "Boolean", "ID"
    ].toSet()

    /**
     * Filters out introspection types (types starting with "__") and built-in scalars
     * from a type name set.
     */
    private Set<String> filterSystemTypes(Set<String> typeNames) {
        typeNames.findAll { !it.startsWith("__") && !BUILT_IN_SCALARS.contains(it) }.toSet()
    }

    /**
     * Asserts that two schemas are semantically equivalent.
     *
     * This checks:
     * - Type map keys match (excluding introspection types and built-in scalars)
     * - Interface implementations match (as lists, order matters - alphabetically sorted)
     * - Core directive names are present (allows experimental directives to differ)
     * - Root types match
     *
     * Note: additionalTypes is NOT compared because FastBuilder and standard Builder have
     * different semantics - FastBuilder includes all non-root types, while standard Builder
     * includes only types not reachable from roots.
     */
    void assertSchemasEquivalent(GraphQLSchema fastSchema, GraphQLSchema standardSchema) {
        // Check type map keys match (excluding introspection types and built-in scalars which may differ)
        def fastTypes = filterSystemTypes(fastSchema.typeMap.keySet())
        def standardTypes = filterSystemTypes(standardSchema.typeMap.keySet())
        assert fastTypes == standardTypes,
                "Type map keys differ:\n" +
                "FastBuilder types: ${fastTypes}\n" +
                "Standard types: ${standardTypes}"

        // Note: additionalTypes is NOT compared - see method Javadoc for explanation

        // Check interface implementations (order matters - should be alphabetically sorted)
        // Only check user-defined interfaces (not introspection interfaces)
        def interfaces = fastSchema.allTypesAsList.findAll {
            it instanceof GraphQLInterfaceType && !it.name.startsWith("__")
        }
        interfaces.each { GraphQLInterfaceType iface ->
            def fastImpls = fastSchema.getImplementations(iface)*.name
            def standardImpls = standardSchema.getImplementations(iface)*.name
            assert fastImpls == standardImpls,
                    "Interface '${iface.name}' implementations differ:\n" +
                    "FastBuilder: ${fastImpls}\n" +
                    "Standard: ${standardImpls}"
        }

        // Check directive names match (as sets - order may vary)
        // Note: We check that core directives are present, but allow experimental directives to differ
        def coreDirectives = ["include", "skip", "deprecated", "specifiedBy"].toSet()
        def fastDirectiveNames = fastSchema.directives*.name.toSet()
        def standardDirectiveNames = standardSchema.directives*.name.toSet()

        assert fastDirectiveNames.containsAll(coreDirectives),
                "FastBuilder missing core directives: ${coreDirectives - fastDirectiveNames}"
        assert standardDirectiveNames.containsAll(coreDirectives),
                "Standard builder missing core directives: ${coreDirectives - standardDirectiveNames}"

        // Check that FastBuilder directives are a subset of standard directives
        // (standard may have additional experimental directives)
        assert standardDirectiveNames.containsAll(fastDirectiveNames),
                "FastBuilder has directives not in standard builder: ${fastDirectiveNames - standardDirectiveNames}"

        // Check root types
        assert fastSchema.queryType?.name == standardSchema.queryType?.name,
                "Query types differ: ${fastSchema.queryType?.name} vs ${standardSchema.queryType?.name}"

        if (fastSchema.mutationType || standardSchema.mutationType) {
            assert fastSchema.mutationType?.name == standardSchema.mutationType?.name,
                    "Mutation types differ: ${fastSchema.mutationType?.name} vs ${standardSchema.mutationType?.name}"
        }

        if (fastSchema.subscriptionType || standardSchema.subscriptionType) {
            assert fastSchema.subscriptionType?.name == standardSchema.subscriptionType?.name,
                    "Subscription types differ: ${fastSchema.subscriptionType?.name} vs ${standardSchema.subscriptionType?.name}"
        }
    }

    def "trivial schema with one String field matches between FastBuilder and standard builder"() {
        given: "SDL for a trivial schema"
        def sdl = """
            type Query {
                value: String
            }
        """

        and: "programmatically created query type"
        def queryType = newObject()
                .name("Query")
                .field(newFieldDefinition()
                        .name("value")
                        .type(GraphQLString))
                .build()

        when: "building with both approaches"
        def standardSchema = buildSchemaFromSDL(sdl)
        def fastSchema = buildSchemaWithFastBuilder(queryType)

        then: "schemas are equivalent"
        assertSchemasEquivalent(fastSchema, standardSchema)
    }
}
