package graphql.validation

import graphql.language.OperationDefinition
import graphql.language.SelectionSet
import spock.lang.Specification

import static graphql.StarWarsSchema.queryType
import static graphql.StarWarsSchema.starWarsSchema
import static graphql.language.OperationDefinition.Operation.QUERY


class TraversalContextTest extends Specification {

    TraversalContext traversalContext = new TraversalContext(starWarsSchema)

    def "enter operation definition"(){
        given:
        SelectionSet selectionSet = new SelectionSet([])
        OperationDefinition operationDefinition = new OperationDefinition(queryType.getName(),QUERY,selectionSet)

        when:
        traversalContext.enter(operationDefinition)

        then:
        traversalContext.getType() == queryType
    }

    def "leave operation definition"(){
        given:
        SelectionSet selectionSet = new SelectionSet([])
        OperationDefinition operationDefinition = new OperationDefinition(queryType.getName(),QUERY,selectionSet)

        when:
        traversalContext.enter(operationDefinition)
        traversalContext.leave(operationDefinition)

        then:
        traversalContext.getType() == null
    }
}
