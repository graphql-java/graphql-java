package graphql.execution

import graphql.ExecutionResult
import graphql.GraphQL
import graphql.StarWarsSchema
import graphql.language.Field
import graphql.schema.GraphQLObjectType
import spock.lang.Specification

class ExecutionIdTest extends Specification {

    class CaptureIdStrategy extends SimpleExecutionStrategy {
        ExecutionId executionId = null

        @Override
        ExecutionResult execute(ExecutionContext executionContext, Path currentPath, GraphQLObjectType parentType, Object source, Map<String, List<Field>> fields) {
            executionId = executionContext.executionId
            return super.execute(executionContext, currentPath, parentType, source, fields)
        }
    }

    def 'Ensures that an execution identifier is present'() {
        given:
        def query = """
        query HeroNameAndFriendsQuery {
            hero {
                id
            }
        }
        """

        when:

        CaptureIdStrategy idStrategy = new CaptureIdStrategy()

        new GraphQL(StarWarsSchema.starWarsSchema, idStrategy).execute(query).data

        then:

        idStrategy.executionId != null
    }
}
