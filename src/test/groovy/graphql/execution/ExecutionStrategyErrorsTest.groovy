package graphql.execution

import graphql.ExceptionWhileDataFetching
import graphql.ExecutionInput
import graphql.GraphQL
import graphql.SerializationError
import graphql.TestUtil
import graphql.TypeMismatchError
import graphql.execution.instrumentation.Instrumentation
import graphql.execution.instrumentation.InstrumentationContext
import graphql.execution.instrumentation.InstrumentationState
import graphql.execution.instrumentation.SimplePerformantInstrumentation
import graphql.execution.instrumentation.parameters.InstrumentationFieldCompleteParameters
import graphql.schema.DataFetcher
import spock.lang.Specification

/**
 * A test of errors that can happen inside a ES
 */
class ExecutionStrategyErrorsTest extends Specification {

    def "can capture certain errors"() {
        def sdl = '''
            type Query {
                notAList : [String]
                notAFloat : Float
                notAnProperObject : ImproperObject
            }
            
            
            type ImproperObject {
                name : String
                diceyListCall : [DiceyCall!]!
                diceyListCallAbort : [DiceyCall!]!
                diceyCall : DiceyCall
            }
            
            type DiceyCall {
                bang : String
                abort : String
                nonNull : String!
            }
        '''

        DataFetcher dfNotAList = { env -> "noAList" }
        DataFetcher dfNotAFloat = { env -> "noAFloat" }
        DataFetcher dfDiceyBang = {
            env -> throw new RuntimeException("dicey call")
        }
        DataFetcher dfDiceyAbort = {
            env -> throw new AbortExecutionException("abort abort")
        }
        DataFetcher dfDiceyListCall = {
            env -> ["x", null]
        }
        DataFetcher dfReturnsNull = {
            env -> null
        }

        def schema = TestUtil.schema(sdl, [
                Query         :
                        [notAList: dfNotAList, notAFloat: dfNotAFloat],
                ImproperObject:
                        [diceyListCall: dfDiceyListCall],
                DiceyCall     :
                        [bang: dfDiceyBang, abort: dfDiceyAbort, nonNull: dfReturnsNull],
        ]
        )

        Instrumentation instrumentation = new SimplePerformantInstrumentation() {
            @Override
            InstrumentationContext<Object> beginFieldListCompletion(InstrumentationFieldCompleteParameters parameters, InstrumentationState state) {
                if (parameters.field.name == "diceyListCallAbort") {
                    throw new AbortExecutionException("No lists for you")
                }
                return super.beginFieldListCompletion(parameters, state)
            }
        }
        def graphQL = GraphQL.newGraphQL(schema).instrumentation(instrumentation).build()


        when:
        def ei = ExecutionInput.newExecutionInput()
                .query("""
            query q {
                notAList
                notAFloat
                notAnProperObject {
                    name
                    diceyListCallAbort {
                        bang
                    }    
                    diceyListCall {
                        bang
                        abort
                        nonNull
                    }    
                }
            }
        """)
                .root([notAnProperObject: ["name"              : "make it drive errors",
                                           "diceyListCall"     : [[:]],
                                           "diceyListCallAbort": [[:]],
                                           "diceyCall"         : [:]
                ]])
                .build()
        def er = graphQL.execute(ei)

        then:
        er.errors.size() == 7
        er.errors[0] instanceof TypeMismatchError
        er.errors[0].path == ["notAList"]

        er.errors[1] instanceof SerializationError
        er.errors[1].path == ["notAFloat"]

        er.errors[2] instanceof ExceptionWhileDataFetching
        er.errors[2].path == ["notAnProperObject", "diceyListCall", 0, "bang"]
        ((ExceptionWhileDataFetching) er.errors[2]).exception.message == "dicey call"

        er.errors[3] instanceof ExceptionWhileDataFetching
        er.errors[3].path == ["notAnProperObject", "diceyListCall", 0, "abort"]
        ((ExceptionWhileDataFetching) er.errors[3]).exception.message == "abort abort"

        er.errors[4] instanceof NonNullableFieldWasNullError
        er.errors[4].path == ["notAnProperObject", "diceyListCall", 0, "nonNull"]

        er.errors[5] instanceof NonNullableFieldWasNullError
        er.errors[5].path == ["notAnProperObject", "diceyListCall", 1]  // the entry itself was null in a non null list entry

        er.errors[6] instanceof AbortExecutionException
    }
}
