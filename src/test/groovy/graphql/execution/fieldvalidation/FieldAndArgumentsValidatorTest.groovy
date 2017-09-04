package graphql.execution.fieldvalidation

import graphql.ExecutionInput
import graphql.ExecutionResult
import graphql.GraphQL
import graphql.GraphQLError
import graphql.TestUtil
import graphql.execution.AsyncExecutionStrategy
import graphql.execution.Execution
import graphql.execution.ExecutionId
import graphql.execution.ExecutionPath
import graphql.execution.instrumentation.NoOpInstrumentation
import spock.lang.Specification

import java.util.concurrent.CompletableFuture

class FieldAndArgumentsValidatorTest extends Specification {
    def idl = """
            type Information {
                informationString(fmt1 : String = "defaultFmt1", fmt2 : String = "defaultFmt2" ) : String
                informationInt : Int
                informationLink : Information
            }
            
            input InputData {
                string: String
                int: Int
            }
            
            type Query {
                field1(inputData : InputData, id : ID) : Information
                field2(stringArg : String) : String
                field3(intArg : Int) : Int
                noArgField : Int
            }
        """

    def schema = TestUtil.schema(idl)

    def query = '''
        query VarTest($stringVar : String, $intVar : Int, $inputDataVar : InputData, $idVar : ID, $fmt1Var : String) {
            field1(inputData : $inputDataVar, id : $idVar) {
                informationString 
                informationLink {
                    informationString(fmt1 : "inlineFmt1")
                        informationLink {
                        informationString(fmt1 : $fmt1Var)
                    }
                }
            }
            field2(stringArg : $stringVar)
            field3(intArg : $intVar)
        }
        '''


    def "test_field_args_can_be_validated"() {
        given:

        def variables = [
                stringVar   : "stringValue",
                intVar      : 666,
                inputDataVar: [string: "string", int: 0],
                idVar       : "ID12345",
                fmt1Var     : "fmt1Value"
        ]

        def validator = new FieldAndArgumentsValidator() {
            @Override
            List<GraphQLError> validateFieldArguments(FieldAndArgumentsValidationEnvironment validationEnvironment) {
                Map<String, Object> values

                def fieldArguments = validationEnvironment.getFieldArguments()

                FieldAndArguments field1Args = fieldArguments.get(ExecutionPath.fromString("/field1"))

                values = field1Args.getFieldArgumentValues()

                assert values['inputData'] == [string: "string", int: 0]
                assert values['id'] == "ID12345"
                assert !values.containsKey('stringArg')
                assert !values.containsKey('intArg')


                values = fieldArguments.get(ExecutionPath.fromString("/field2"))
                        .getFieldArgumentValues()

                assert values['stringArg'] == "stringValue"

                values = fieldArguments.get(ExecutionPath.fromString("/field3"))
                        .getFieldArgumentValues()

                assert values['intArg'] == 666

                assert !fieldArguments.containsKey(ExecutionPath.fromString("/noArgField"))

                values = fieldArguments.get(ExecutionPath.fromString("/field1/informationLink/informationString"))
                        .getFieldArgumentValues()

                assert values['fmt1'] == "inlineFmt1" // inlined from query
                assert values['fmt2'] == "defaultFmt2" // defaulted from schema

                values = fieldArguments.get(ExecutionPath.fromString("/field1/informationLink/informationLink/informationString"))
                        .getFieldArgumentValues()


                assert values['fmt1'] == "fmt1Value" // from variables
                assert values['fmt2'] == "defaultFmt2" // defaulted from schema


                return [
                        validationEnvironment.mkError("Made up some error here", field1Args.getField(), field1Args.getPath())
                ]
            }
        }

        when:

        def result = setupExecution(validator, query, variables)

        then:
        result.get().getErrors().size() == 1
        result.get().getErrors()[0].getMessage() == "Made up some error here"
        result.get().getErrors()[0].getLocations().size() == 1
        result.get().getErrors()[0].getPath() == ["field1"]
    }

    def "test graphql from end to end"() {

        GraphQL graphql = GraphQL.newGraphQL(schema).fieldArgumentValidator(new FieldAndArgumentsValidator() {
            @Override
            List<GraphQLError> validateFieldArguments(FieldAndArgumentsValidationEnvironment validationEnvironment) {
                return [validationEnvironment.mkError("I was called", null, null)]
            }
        }).build()

        when:
        def result = graphql.execute("{ field2 }")

        then:
        result.getErrors()[0].getMessage() == "I was called"
    }

    def "test SimpleFieldAndArgumentsValidator"() {
        given:

        def variables = [
                stringVar   : "stringValue",
                intVar      : 666,
                inputDataVar: [string: "string", int: 0],
                idVar       : "ID12345",
                fmt1Var     : "fmt1Value"
        ]

        SimpleFieldAndArgumentsValidator validator = new SimpleFieldAndArgumentsValidator()
                .addRule(ExecutionPath.fromString("/field1"),
                { fieldAndArguments, env -> err("Not happy Jan!", env, fieldAndArguments) })
                .addRule(ExecutionPath.fromString("/field1/informationLink/informationLink/informationString"),
                { fieldAndArguments, env -> err("Also not happy Jan!", env, fieldAndArguments) })
                .addRule(ExecutionPath.fromString("/does/not/exist"),
                { fieldAndArguments, env -> err("Wont happen", env, fieldAndArguments) })


        when:
        def result = setupExecution(validator, query, variables)

        then:
        result.get().getErrors().size() == 2
        result.get().getErrors()[0].getMessage() == "Not happy Jan!"
        result.get().getErrors()[0].getLocations().size() == 1
        result.get().getErrors()[0].getPath() == ["field1"]

        result.get().getErrors()[1].getMessage() == "Also not happy Jan!"

    }

    static GraphQLError err(String msg, FieldAndArgumentsValidationEnvironment env, FieldAndArguments fieldAndArguments) {
        env.mkError(msg, fieldAndArguments.getField(), fieldAndArguments.getPath())
    }


    private CompletableFuture<ExecutionResult> setupExecution(FieldAndArgumentsValidator variableValidator, String query, Map<String, Object> variables) {
        def document = TestUtil.parseQuery(query)
        def strategy = new AsyncExecutionStrategy()
        def execution = new Execution(strategy, strategy, strategy, NoOpInstrumentation.INSTANCE, Optional.of(variableValidator))

        def executionInput = ExecutionInput.newExecutionInput().query(query).variables(variables).build()
        execution.execute(document, schema, ExecutionId.generate(), executionInput, NoOpInstrumentation.INSTANCE.createState())
    }

}
