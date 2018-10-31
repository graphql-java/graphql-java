package graphql.execution

import graphql.ExecutionInput
import graphql.GraphQL
import graphql.TestUtil
import graphql.language.Argument
import graphql.language.Directive
import graphql.language.Field
import graphql.language.StringValue
import graphql.schema.DataFetcher
import graphql.schema.DataFetchingEnvironment
import spock.lang.Specification

class DirectivesResolverTest extends Specification {

    class DirectiveCapturingDataFetcher implements DataFetcher {
        @Override
        Object get(DataFetchingEnvironment environment) throws Exception {
            def directives = environment.getDirectives()
            def outputStr = ""
            directives.eachWithIndex { name, directive, dIndex ->
                def arguments = directive.getArguments()
                def argStr = ""
                if (arguments.size() > 0) {
                    argStr += "("
                }
                arguments.withIndex().collect(
                        { arg, index -> argStr += arg.name + ":" + arg.getValue() + (index == arguments.size() - 1 ? "" : ",") }
                )
                if (arguments.size() > 0) {
                    argStr += ")"
                }
                def isLast = dIndex == directives.size() - 1
                outputStr += "@" + name + argStr + (isLast ? "" : ",")
            }
            return outputStr
        }
    }

    def spec = '''

            directive @fieldDirective1(
                value: String = "defaultValueOfFieldDirective1"
            ) on FIELD

            directive @fieldDirective2(
                value: String = "defaultValueOfFieldDirective2"
            ) on FIELD
            
            directive @fieldDirective3(
                value: ComplexInput = { strVal : "defaultStr", intVal : 666, boolVal : true }
            ) on FIELD

            directive @fieldDirective4(
                value: String 
            ) on FIELD

            directive @fieldDefDirective(
                value: String 
            ) on FIELD_DEFINITION

            type Query {
               field(arg : String) : String
            }
            
            input ComplexInput {
                strVal : String
                intVal : Int
                boolVal : Boolean
            }
            
        '''

    def schema = TestUtil.schema(spec, ["Query": ["field": new DirectiveCapturingDataFetcher()]])

    def "nothing is collected if the directive is not known about"() {
        Field f = Field.newField("field").directives([Directive.newDirective().name("unknownDirective").build()]).build()

        when:
        def directives = DirectivesResolver.getFieldDirectives(f, schema, [:])
        then:
        directives.isEmpty()

    }

    def "nothing is collected if the directive is not a field one"() {
        Field f = Field.newField("field").directives([Directive.newDirective().name("fieldDefDirective").build()]).build()

        when:
        def directives = DirectivesResolver.getFieldDirectives(f, schema, [:])
        then:
        directives.isEmpty()

    }

    def "an argument without a default value is not added unless its in the AST"() {
        Field f = Field.newField("field").directives([Directive.newDirective().name("fieldDirective4").build()]).build()

        when:
        def directives = DirectivesResolver.getFieldDirectives(f, schema, [:])
        then:
        directives['fieldDirective4'].getArguments().isEmpty()

    }

    def "an argument with a value is added when its in the AST"() {
        Field f = Field.newField("field")
                .directives([Directive.newDirective().name("fieldDirective4")
                                     .arguments([Argument.newArgument("value", new StringValue("s")).build()])
                                     .build()]).build()

        when:
        def directives = DirectivesResolver.getFieldDirectives(f, schema, [:])
        then:
        directives['fieldDirective4'].getArgument("value").getValue() == "s"
    }



    def "integration test of field directives"() {

        def graphQL = GraphQL.newGraphQL(schema).build()
        def query = ''' 
            query hasPartialVarsAndAstMixed($strVar : String) {
                field(arg : "argValue") 
                    @fieldDirective1(value:"fromQueryOnDirective1") 
                    @fieldDirective2 
                    @fieldDirective3(value : {strVal : $strVar, boolVal : false })
                    @fieldDirective4
            }
        '''
        when:
        def executionResult = graphQL.execute(ExecutionInput.newExecutionInput().query(query).variables([strVar: "fromVariables"]).build())
        then:
        executionResult.errors.isEmpty()
        executionResult.data == [field: "" +
                "@fieldDirective1(value:fromQueryOnDirective1)," +
                "@fieldDirective2(value:defaultValueOfFieldDirective2)," +
                "@fieldDirective3(value:[strVal:fromVariables, boolVal:false])," +
                "@fieldDirective4"
        ]
    }

}
