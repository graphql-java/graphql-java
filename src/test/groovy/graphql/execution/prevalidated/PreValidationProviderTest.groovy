package graphql.execution.prevalidated

import graphql.ExecutionInput
import graphql.language.Document
import graphql.schema.GraphQLSchema
import graphql.validation.ValidationError
import graphql.validation.ValidationErrorType
import spock.lang.Specification

import java.time.Duration
import java.util.function.Supplier

import static graphql.GraphQL.newGraphQL
import static graphql.StarWarsSchema.starWarsSchema

class PreValidationProviderTest extends Specification {

    def "can black list quickly via certain aspects"() {

        PreValidationProvider preValidationProvider = new PreValidationProvider() {
            @Override
            List<ValidationError> get(ExecutionInput executionInput, Document queryDocument, GraphQLSchema graphQLSchema, Supplier<List<ValidationError>> validationFunction) {
                def then = System.nanoTime()
                try {
                    if (executionInput.getQuery().contains("BAD")) {
                        return [new ValidationError(ValidationErrorType.Custom, "BADness detected")]
                    }
                    return validationFunction.get()
                }
                finally {
                    def nanos = System.nanoTime() - then
                    println "took " + nanos + " nanos or " + Duration.ofNanos(nanos).toMillis() + " ms"
                }
            }
        }

        def OK_QUERY = '''
            query OK {
                hero {
                    name
                    friends {
                        name
                        friends {
                            name
                            friends {
                                name
                            }
                            friends {
                                name
                            }
                        }
                    }
                }
            }
        '''

        def BAD_QUERY = '''
            query BAD {
                hero {
                    name
                }
            }
        '''

        def graphQL = newGraphQL(starWarsSchema).preValidationProvider(preValidationProvider).build()
        when:
        def er = graphQL.execute({ it.query(OK_QUERY) })
        then:
        er.errors.isEmpty()
        er.data != null

        when:

        for (int i = 0; i < 10000; i++) {
            graphQL.execute({ it.query(OK_QUERY) })
        }
        for (int i = 0; i < 10000; i++) {
            //graphQL.execute({ it.query(BAD_QUERY) })
        }

        er = graphQL.execute({ it.query(BAD_QUERY) })
        then:
        er.data == null

        !er.errors.isEmpty()
        er.errors[0].getMessage().contains("BADness detected")
    }
}
