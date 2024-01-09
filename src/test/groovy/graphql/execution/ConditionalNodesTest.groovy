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
import spock.lang.Specification

class ConditionalNodesTest extends Specification {

    def "should include false for skip = true"() {
        given:
        def variables = new LinkedHashMap<String, Object>()
        ConditionalNodes conditionalNodes = new ConditionalNodes()

        def directives = directive("skip", ifArg(true))

        expect:
        !conditionalNodes.shouldInclude(mkField(directives), variables, null, GraphQLContext.getDefault())
    }

    def "should include true for skip = false"() {
        given:
        def variables = new LinkedHashMap<String, Object>()
        ConditionalNodes conditionalNodes = new ConditionalNodes()

        def directives = directive("skip", ifArg(false))

        expect:
        conditionalNodes.shouldInclude(mkField(directives), variables, null, GraphQLContext.getDefault())
    }

    def "should include false for include = false"() {
        given:
        def variables = new LinkedHashMap<String, Object>()
        ConditionalNodes conditionalNodes = new ConditionalNodes()

        def directives = directive("include", ifArg(false))

        expect:
        !conditionalNodes.shouldInclude(mkField(directives), variables, null, GraphQLContext.getDefault())
    }

    def "should include true for include = true"() {
        given:
        def variables = new LinkedHashMap<String, Object>()
        ConditionalNodes conditionalNodes = new ConditionalNodes()

        def directives = directive("include", ifArg(true))

        expect:
        conditionalNodes.shouldInclude(mkField(directives), variables, null, GraphQLContext.getDefault())
    }

    def "no directives means include"() {
        given:
        def variables = new LinkedHashMap<String, Object>()
        ConditionalNodes conditionalNodes = new ConditionalNodes()

        expect:
        conditionalNodes.shouldInclude(mkField([]), variables, null, GraphQLContext.getDefault())
    }


    def "allows a custom implementation to check conditional nodes"() {
        given:
        def variables = ["x": "y"]
        def graphQLSchema = TestUtil.schema("type Query { f : String} ")
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
                assert env.graphQlSchema == graphQLSchema
                assert env.graphQLContext.get("assert") != null
                return false
            }
        }
        graphQLContext.put(ConditionalNodeDecision.class, conditionalDecision)
        graphQLContext.put("assert", true)
        expect:

        !conditionalNodes.shouldInclude(field, variables, graphQLSchema, graphQLContext)
        called == true
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

                    def arguments = env.getGraphQlSchema().getDirective("featureFlag")
                            .getArguments()
                    Map<String, Object> argumentValues = ValuesResolver.getArgumentValues(
                            arguments, foundDirective.getArguments(),
                            env.variables, env.graphQLContext, Locale.getDefault())
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
}
