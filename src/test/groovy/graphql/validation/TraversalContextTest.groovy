package graphql.validation

import graphql.language.OperationDefinition
import graphql.language.SelectionSet
import graphql.schema.GraphQLNonNull
import spock.lang.Specification

import static graphql.StarWarsSchema.*
import static graphql.language.OperationDefinition.Operation.QUERY

class TraversalContextTest extends Specification {

    TraversalContext traversalContext = new TraversalContext(starWarsSchema)

    def "operation definition"(){
        given:
        SelectionSet selectionSet = new SelectionSet([])
        OperationDefinition operationDefinition = new OperationDefinition(queryType.getName(),QUERY,selectionSet)

        when:
        traversalContext.enter(operationDefinition)

        then:
        traversalContext.getType() == queryType

        when:
        traversalContext.leave(operationDefinition)

        then:
        traversalContext.getType() == null
    }

    def "SelectionSet tracks current type as parent"(){
        given:
        SelectionSet selectionSet = new SelectionSet()
        traversalContext.typeStack.add(new GraphQLNonNull(droidType))

        when:
        traversalContext.enter(selectionSet)

        then:
        traversalContext.getParentType() == droidType

        when:
        traversalContext.leave(selectionSet)

        then:
        traversalContext.getParentType() == null


    }
}
