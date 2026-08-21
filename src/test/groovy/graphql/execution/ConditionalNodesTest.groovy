package graphql.execution

import graphql.ExecutionInput
import graphql.GraphQLContext
import graphql.TestUtil
import graphql.execution.conditional.ConditionalNodeDecision
import graphql.execution.conditional.ConditionalNodeDecisionEnvironment
import graphql.execution.conditional.ConditionalNodes
import graphql.language.Argument
import graphql.language.BooleanValue
import graphql.language.Directive
import graphql.language.Field
import graphql.language.NodeUtil
import graphql.schema.DataFetcher
import graphql.schema.DataFetchingEnvironment
import graphql.schema.ExecutableSchema
import graphql.schema.GraphQLSchema
import graphql.schema.universe.SchemaUniverse
import graphql.schema.universe.view.SUExecutableSchema
import spock.lang.Specification

class ConditionalNodesTest extends Specification {

    def "should include false for skip = true [#schemaKind]"() {
        given:
        def variables = new LinkedHashMap<String, Object>()
        ConditionalNodes conditionalNodes = new ConditionalNodes()
        def schema = executableSchema(schemaKind)

        def directives = directive("skip", ifArg(true))

        expect:
        !conditionalNodes.shouldInclude(
                mkField(directives),
                variables,
                schema,
                GraphQLContext.getDefault())

        where:
        schemaKind << schemaKinds()
    }

    def "should include true for skip = false [#schemaKind]"() {
        given:
        def variables = new LinkedHashMap<String, Object>()
        ConditionalNodes conditionalNodes = new ConditionalNodes()
        def schema = executableSchema(schemaKind)

        def directives = directive("skip", ifArg(false))

        expect:
        conditionalNodes.shouldInclude(
                mkField(directives),
                variables,
                schema,
                GraphQLContext.getDefault())

        where:
        schemaKind << schemaKinds()
    }

    def "should include false for include = false [#schemaKind]"() {
        given:
        def variables = new LinkedHashMap<String, Object>()
        ConditionalNodes conditionalNodes = new ConditionalNodes()
        def schema = executableSchema(schemaKind)

        def directives = directive("include", ifArg(false))

        expect:
        !conditionalNodes.shouldInclude(
                mkField(directives),
                variables,
                schema,
                GraphQLContext.getDefault())

        where:
        schemaKind << schemaKinds()
    }

    def "should include true for include = true [#schemaKind]"() {
        given:
        def variables = new LinkedHashMap<String, Object>()
        ConditionalNodes conditionalNodes = new ConditionalNodes()
        def schema = executableSchema(schemaKind)

        def directives = directive("include", ifArg(true))

        expect:
        conditionalNodes.shouldInclude(
                mkField(directives),
                variables,
                schema,
                GraphQLContext.getDefault())

        where:
        schemaKind << schemaKinds()
    }

    def "no directives means include [#schemaKind]"() {
        given:
        def variables = new LinkedHashMap<String, Object>()
        ConditionalNodes conditionalNodes = new ConditionalNodes()
        def schema = executableSchema(schemaKind)

        expect:
        conditionalNodes.shouldInclude(
                mkField([]),
                variables,
                schema,
                GraphQLContext.getDefault())

        where:
        schemaKind << schemaKinds()
    }


    def "allows a custom implementation to check conditional nodes [#schemaKind]"() {
        given:
        def variables = ["x": "y"]
        def graphQLSchema = TestUtil.schema("type Query { f : String} ")
        def schema = executableSchema(graphQLSchema, schemaKind)
        ConditionalNodes conditionalNodes = new ConditionalNodes()

        def graphQLContext = GraphQLContext.getDefault()

        def directives = directive("featureFlag", ifArg(true))
        def field = mkField(directives)

        def called = false
        ConditionalNodeDecision conditionalDecision = new ConditionalNodeDecision() {
            @Override
            boolean shouldInclude(ConditionalNodeDecisionEnvironment env) {
                called = true
                assert env.variables.toMap() == variables
                assert env.directivesContainer == field
                assert env.schema.is(schema)
                assert env.graphQLContext.get("assert") != null
                return false
            }
        }
        graphQLContext.put(ConditionalNodeDecision.class, conditionalDecision)
        graphQLContext.put("assert", true)
        expect:

        !conditionalNodes.shouldInclude(
                field,
                variables,
                schema,
                graphQLContext)
        called == true

        where:
        schemaKind << schemaKinds()
    }

    def "integration test showing conditional nodes can be custom included"() {

        def sdl = """

            directive @featureFlag(flagName: String!) repeatable on FIELD
            
            type Query {
                in : String
                out : String
                pet : Pet
            }
            
            type Pet {
                name: String
                favouriteSnack: String
            }
        """
        DataFetcher df = { DataFetchingEnvironment env -> env.getFieldDefinition().name }
        def graphQL = TestUtil.graphQL(sdl, [
                Query: ["in": df, "out": df, "pet": (DataFetcher<Map>) { [ : ] } ],
                Pet: ["name": df, "favouriteSnack": df]]).build()
        ConditionalNodeDecision customDecision = new ConditionalNodeDecision() {
            @Override
            boolean shouldInclude(ConditionalNodeDecisionEnvironment env) {

                Directive foundDirective = NodeUtil.findNodeByName(env.getDirectives(), "featureFlag")
                if (foundDirective != null) {

                    def arguments = env.schema
                            .getDirective("featureFlag")
                            .getArguments()
                    Map<String, Object> argumentValues = ValuesResolver.getArgumentValues(
                            env.schema,
                            arguments,
                            foundDirective.getArguments(),
                            env.variables,
                            env.graphQLContext,
                            Locale.getDefault())
                    Object flagName = argumentValues.get("flagName")
                    return String.valueOf(flagName) == "ON"
                }
                return true
            }
        }

        def contextMap = [:]
        contextMap.put(ConditionalNodeDecision.class, customDecision)

        when:
        def ei = ExecutionInput.newExecutionInput()
                .graphQLContext(contextMap)
                .query("""
            query q {
                in
                out @featureFlag(flagName : "OFF")
            }
        """
                ).build()
        def er = graphQL.execute(ei)

        then:
        er["data"] == ["in": "in"]

        when:
        ei = ExecutionInput.newExecutionInput()
                .graphQLContext(contextMap)
                .query("""
            query q {
                in
                out @featureFlag(flagName : "ON")
            }
        """
                ).build()
        er = graphQL.execute(ei)

        then:
        er["data"] == ["in": "in", "out": "out"]

        when:
        ei = ExecutionInput.newExecutionInput()
                .graphQLContext(contextMap)
                .query('''
            query vars_should_work($v : String!) {
                in
                out @featureFlag(flagName : $v)
            }
        '''
                )
                .variables([v: "ON"])
                .build()
        er = graphQL.execute(ei)

        then:
        er["data"] == ["in": "in", "out": "out"]

        // A test for fields below the top level
        when:
        ei = ExecutionInput.newExecutionInput()
                .graphQLContext(contextMap)
                .query("""
            query q {
                in
                pet {
                  name
                  favouriteSnack @featureFlag(flagName : "OFF")
                } 
            }
        """
                ).build()
        er = graphQL.execute(ei)

        then:
        er["data"] == ["in": "in", "pet": ["name": "name"]]
    }

    private ArrayList<Directive> directive(String name, Argument argument) {
        [Directive.newDirective().name(name).arguments([argument]).build()]
    }

    private Argument ifArg(Boolean b) {
        Argument.newArgument("if", new BooleanValue(b)).build()
    }

    Field mkField(List<Directive> directives) {
        return Field.newField("name").directives(directives).build()
    }

    private static List<String> schemaKinds() {
        return [
                GraphQLSchema.simpleName,
                SUExecutableSchema.simpleName]
    }

    private static ExecutableSchema executableSchema(String schemaKind) {
        return executableSchema(
                TestUtil.schema("type Query { f: String }"),
                schemaKind)
    }

    private static ExecutableSchema executableSchema(
            GraphQLSchema graphQLSchema,
            String schemaKind) {
        if (schemaKind == GraphQLSchema.simpleName) {
            return graphQLSchema
        }
        def imported = new SchemaUniverse()
                .importSchema("conditional_nodes", graphQLSchema)
        return SUExecutableSchema.fromGraphQLSchema(
                imported,
                graphQLSchema)
    }
}
