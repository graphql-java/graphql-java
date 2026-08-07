package graphql.schema.universe

import graphql.AssertException
import graphql.Directives
import graphql.GraphQLContext
import graphql.Scalars
import graphql.TestUtil
import graphql.execution.CoercedVariables
import graphql.execution.OneOfTooManyKeysException
import graphql.execution.RawVariables
import graphql.execution.TypeFromAST
import graphql.execution.ValuesResolver
import graphql.execution.values.InputInterceptor
import graphql.execution.values.legacycoercing.LegacyCoercingInputInterceptor
import graphql.language.AstPrinter
import graphql.language.Field
import graphql.language.ListType
import graphql.language.NonNullType
import graphql.language.OperationDefinition
import graphql.language.TypeName
import graphql.language.Value
import graphql.normalized.NormalizedInputValue
import graphql.schema.GraphQLArgument
import graphql.schema.GraphQLEnumType
import graphql.schema.GraphQLFieldDefinition
import graphql.schema.GraphQLInputObjectField
import graphql.schema.GraphQLInputObjectType
import graphql.schema.GraphQLInputType
import graphql.schema.GraphQLList
import graphql.schema.GraphQLNonNull
import graphql.schema.GraphQLObjectType
import graphql.schema.GraphQLScalarType
import graphql.schema.GraphQLSchema
import graphql.schema.SchemaEnum
import graphql.schema.SchemaField
import graphql.schema.universe.view.SUExecutableSchema
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

class SUExecutableSchemaValuesResolverTest extends Specification {

    @Shared
    GraphQLSchema graphQLSchema

    @Shared
    SUExecutableSchema universeSchema

    def setupSpec() {
        graphQLSchema = schema()
        def imported = new SchemaUniverse()
                .importSchema("values_resolver", graphQLSchema)
        universeSchema = SUExecutableSchema.fromGraphQLSchema(
                imported,
                graphQLSchema)
    }

    def "variable coercion and normalization match GraphQLSchema"() {
        given:
        def operation = operation('''
            query Resolve(
                $filter: Filter!
                $modes: [Mode!]!
                $custom: Custom!
                $choice: Choice = { id: "default" }
            ) {
                resolve(
                    filter: $filter
                    modes: $modes
                    custom: $custom
                    choice: $choice
                )
            }
        ''')
        def rawVariables = RawVariables.of([
                filter: [
                        required: 3,
                        numbers : [4, 5],
                        choice  : [name: "Ada"]
                ],
                modes : ["FAST", "SLOW"],
                custom: 6
        ])
        def context = GraphQLContext.newContext()
                .put(
                        InputInterceptor.class,
                        LegacyCoercingInputInterceptor.migratesValues())
                .build()

        when:
        def graphQLValues = ValuesResolver.coerceVariableValues(
                graphQLSchema,
                operation.variableDefinitions,
                rawVariables,
                context,
                Locale.ENGLISH)
        def universeValues = ValuesResolver.coerceVariableValues(
                universeSchema,
                operation.variableDefinitions,
                rawVariables,
                context,
                Locale.ENGLISH)

        then:
        universeValues.toMap() == graphQLValues.toMap()
        universeValues.toMap() == [
                filter: [
                        required      : 3,
                        mode          : 22,
                        legacyMode    : 11,
                        numbers       : [4, 5],
                        choice        : [name: "Ada"],
                        legacyNumbers : [7, 8],
                        legacyChoice  : [id: "legacy"],
                        legacyRequired: 10,
                        legacyNull    : null
                ],
                modes : [11, 22],
                custom: 6,
                choice: [id: "default"]
        ]

        when:
        def graphQLNormalized = ValuesResolver.getNormalizedVariableValues(
                graphQLSchema,
                operation.variableDefinitions,
                rawVariables,
                context,
                Locale.ENGLISH)
        def universeNormalized = ValuesResolver.getNormalizedVariableValues(
                universeSchema,
                operation.variableDefinitions,
                rawVariables,
                context,
                Locale.ENGLISH)

        then:
        normalizedSnapshot(universeNormalized.toMap()) ==
                normalizedSnapshot(graphQLNormalized.toMap())
        universeNormalized.toMap().filter.value.keySet() ==
                ["required", "numbers", "choice"] as Set
    }

    def "literal argument coercion matches GraphQLSchema"() {
        given:
        def operation = operation('''
            query Resolve($custom: Custom!) {
                resolve(
                    filter: {
                        required: 3
                        numbers: [4, 5]
                        choice: { id: "literal" }
                    }
                    modes: [FAST, SLOW]
                    custom: $custom
                    choice: { name: "Ada" }
                )
            }
        ''')
        Field field = operation.selectionSet.selections[0] as Field
        def variables = CoercedVariables.of([custom: 6])

        when:
        def graphQLValues = ValuesResolver.getArgumentValues(
                graphQLSchema,
                graphQLSchema.queryType.getFieldDefinition("resolve").arguments,
                field.arguments,
                variables,
                GraphQLContext.getDefault(),
                Locale.ENGLISH)
        SchemaField universeField = universeSchema.getField(
                universeSchema.queryType,
                "resolve")
        def universeValues = ValuesResolver.getArgumentValues(
                universeSchema,
                universeField.arguments,
                field.arguments,
                variables,
                GraphQLContext.getDefault(),
                Locale.ENGLISH)

        then:
        universeValues == graphQLValues
        universeValues == [
                filter  : [
                        required      : 3,
                        mode          : 22,
                        legacyMode    : 11,
                        numbers       : [4, 5],
                        choice        : [id: "literal"],
                        legacyNumbers : [7, 8],
                        legacyChoice  : [id: "legacy"],
                        legacyRequired: 10,
                        legacyNull    : null
                ],
                modes   : [11, 22],
                custom  : 6,
                choice  : [name: "Ada"],
                optional: 9
        ]
    }

    def "oneOf variable and literal failures match GraphQLSchema"() {
        given:
        def variableOperation = operation('''
            query Resolve($choice: Choice) {
                resolve(choice: $choice)
            }
        ''')

        when:
        ValuesResolver.coerceVariableValues(
                universeSchema,
                variableOperation.variableDefinitions,
                RawVariables.of([choice: [name: "Ada", id: "1"]]),
                GraphQLContext.getDefault(),
                Locale.ENGLISH)

        then:
        def universeVariableError = thrown(OneOfTooManyKeysException)

        when:
        ValuesResolver.coerceVariableValues(
                graphQLSchema,
                variableOperation.variableDefinitions,
                RawVariables.of([choice: [name: "Ada", id: "1"]]),
                GraphQLContext.getDefault(),
                Locale.ENGLISH)

        then:
        def graphQLVariableError = thrown(OneOfTooManyKeysException)
        universeVariableError.message == graphQLVariableError.message

        when:
        def literalOperation = operation('''
            {
                resolve(
                    filter: { required: 1 }
                    choice: { name: "Ada", id: "1" }
                )
            }
        ''')
        Field field = literalOperation.selectionSet.selections[0] as Field
        SchemaField universeField = universeSchema.getField(
                universeSchema.queryType,
                "resolve")
        ValuesResolver.getArgumentValues(
                universeSchema,
                universeField.arguments,
                field.arguments,
                CoercedVariables.emptyVariables(),
                GraphQLContext.getDefault(),
                Locale.ENGLISH)

        then:
        def universeLiteralError = thrown(OneOfTooManyKeysException)

        when:
        ValuesResolver.getArgumentValues(
                graphQLSchema,
                graphQLSchema.queryType.getFieldDefinition("resolve").arguments,
                field.arguments,
                CoercedVariables.emptyVariables(),
                GraphQLContext.getDefault(),
                Locale.ENGLISH)

        then:
        def graphQLLiteralError = thrown(OneOfTooManyKeysException)
        universeLiteralError.message == graphQLLiteralError.message
    }

    @Unroll
    def "variable coercion failure matches GraphQLSchema for #scenario"() {
        given:
        def operation = operation('''
            query Resolve($filter: Filter) {
                resolve(filter: $filter)
            }
        ''')
        def variables = RawVariables.of([filter: filter])

        expect:
        failureSnapshot {
            ValuesResolver.coerceVariableValues(
                    universeSchema,
                    operation.variableDefinitions,
                    variables,
                    GraphQLContext.getDefault(),
                    Locale.ENGLISH)
        } == failureSnapshot {
            ValuesResolver.coerceVariableValues(
                    graphQLSchema,
                    operation.variableDefinitions,
                    variables,
                    GraphQLContext.getDefault(),
                    Locale.ENGLISH)
        }

        where:
        scenario                 | filter
        "unknown input field"    | [required: 1, unknown: true]
        "missing required field" | [numbers: [1]]
        "invalid enum name"      | [required: 1, mode: "UNKNOWN"]
        "invalid custom scalar"  | [required: 1, numbers: ["wrong"]]
        "null non-null element"  | [required: 1, numbers: [null]]
    }

    def "enum runtime values are view-specific and structurally persistent"() {
        given:
        SchemaEnum originalEnum = universeSchema.getType("Mode") as SchemaEnum
        def modeVertex = universeSchema.schema.getEnumType("Mode")
        def fastVertex = universeSchema.schema.getEnumValue(modeVertex, "FAST")
        def slowVertex = universeSchema.schema.getEnumValue(modeVertex, "SLOW")

        expect:
        universeSchema.getEnumRuntimeValue(
                originalEnum.getValue("FAST")) == 11
        universeSchema.getEnumRuntimeValue(
                originalEnum.getValue("SLOW")) == 22

        when:
        def changed = universeSchema.transform {
            it.enumRuntimeValue(modeVertex, "FAST", 99)
            it.enumRuntimeValue(modeVertex, "SLOW", "SLOW")
        }
        SchemaEnum changedEnum = changed.getType("Mode") as SchemaEnum

        then:
        universeSchema.getEnumRuntimeValue(
                originalEnum.getValue("FAST")) == 11
        universeSchema.getEnumRuntimeValue(
                originalEnum.getValue("SLOW")) == 22
        changed.getEnumRuntimeValue(changedEnum.getValue("FAST")) == 99
        changed.getEnumRuntimeValue(changedEnum.getValue("SLOW")) == "SLOW"
        changed.getEnumRuntimeValueById().get(fastVertex.id) == 99
        changed.getEnumRuntimeValueById().get(slowVertex.id) == null
    }

    def "enum runtime values reject null"() {
        given:
        def modeVertex = universeSchema.schema.getEnumType("Mode")

        when:
        universeSchema.transform {
            it.enumRuntimeValue(modeVertex, "FAST", null)
        }

        then:
        thrown(AssertException)
    }

    def "wrapped missing AST types remain unresolved"() {
        expect:
        TypeFromAST.getSchemaTypeFromAST(
                universeSchema,
                new ListType(new TypeName("Missing"))) == null
        TypeFromAST.getSchemaTypeFromAST(
                universeSchema,
                new NonNullType(new TypeName("Missing"))) == null
    }

    private static OperationDefinition operation(String query) {
        return TestUtil.parseQuery(query)
                .getDefinitionsOfType(OperationDefinition)[0]
    }

    private static Object normalizedSnapshot(Object value) {
        if (value instanceof NormalizedInputValue) {
            return [
                    type : value.typeName,
                    value: normalizedSnapshot(value.value)
            ]
        }
        if (value instanceof Value) {
            return AstPrinter.printAst(value)
        }
        if (value instanceof Map) {
            return value.collectEntries {
                [(it.key): normalizedSnapshot(it.value)]
            }
        }
        if (value instanceof List) {
            return value.collect {
                normalizedSnapshot(it)
            }
        }
        return value
    }

    private static Map<String, Object> failureSnapshot(Closure<?> action) {
        try {
            action.call()
        } catch (RuntimeException exception) {
            return [
                    type   : exception.class,
                    message: exception.message
            ]
        }
        throw new AssertionError("Expected value coercion to fail")
    }

    private static GraphQLSchema schema() {
        GraphQLScalarType custom = GraphQLScalarType.newScalar()
                .name("Custom")
                .coercing(Scalars.GraphQLInt.coercing)
                .build()
        GraphQLEnumType mode = GraphQLEnumType.newEnum()
                .name("Mode")
                .value("FAST", 11)
                .value("SLOW", 22)
                .build()
        GraphQLInputObjectType choice = GraphQLInputObjectType
                .newInputObject()
                .name("Choice")
                .withAppliedDirective(
                        Directives.OneOfDirective.toAppliedDirective())
                .field(inputField("name", Scalars.GraphQLString))
                .field(inputField("id", Scalars.GraphQLID))
                .build()
        GraphQLInputObjectType filter = GraphQLInputObjectType
                .newInputObject()
                .name("Filter")
                .field(inputField(
                        "required",
                        GraphQLNonNull.nonNull(Scalars.GraphQLInt)))
                .field(GraphQLInputObjectField.newInputObjectField()
                        .name("mode")
                        .type(mode)
                        .defaultValueProgrammatic("SLOW"))
                .field(GraphQLInputObjectField.newInputObjectField()
                        .name("legacyMode")
                        .type(mode)
                        .defaultValue(11))
                .field(inputField(
                        "numbers",
                        GraphQLList.list(
                                GraphQLNonNull.nonNull(custom))))
                .field(inputField("choice", choice))
                .field(GraphQLInputObjectField.newInputObjectField()
                        .name("legacyNumbers")
                        .type(GraphQLList.list(custom))
                        .defaultValue([7, 8]))
                .field(GraphQLInputObjectField.newInputObjectField()
                        .name("legacyChoice")
                        .type(choice)
                        .defaultValue([id: "legacy"]))
                .field(GraphQLInputObjectField.newInputObjectField()
                        .name("legacyRequired")
                        .type(GraphQLNonNull.nonNull(custom))
                        .defaultValue(10))
                .field(GraphQLInputObjectField.newInputObjectField()
                        .name("legacyNull")
                        .type(Scalars.GraphQLString)
                        .defaultValue(null))
                .build()
        GraphQLFieldDefinition resolve = GraphQLFieldDefinition
                .newFieldDefinition()
                .name("resolve")
                .type(Scalars.GraphQLString)
                .argument(argument(
                        "filter",
                        GraphQLNonNull.nonNull(filter)))
                .argument(argument(
                        "modes",
                        GraphQLList.list(
                                GraphQLNonNull.nonNull(mode))))
                .argument(argument("custom", custom))
                .argument(argument("choice", choice))
                .argument(GraphQLArgument.newArgument()
                        .name("optional")
                        .type(Scalars.GraphQLInt)
                        .defaultValueProgrammatic(9))
                .build()
        GraphQLObjectType query = GraphQLObjectType.newObject()
                .name("Query")
                .field(resolve)
                .build()
        return GraphQLSchema.newSchema()
                .query(query)
                .build()
    }

    private static GraphQLInputObjectField inputField(
            String name,
            GraphQLInputType type) {
        return GraphQLInputObjectField.newInputObjectField()
                .name(name)
                .type(type)
                .build()
    }

    private static GraphQLArgument argument(
            String name,
            GraphQLInputType type) {
        return GraphQLArgument.newArgument()
                .name(name)
                .type(type)
                .build()
    }
}
