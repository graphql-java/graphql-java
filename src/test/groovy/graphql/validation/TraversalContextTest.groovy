package graphql.validation

import graphql.language.Field
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

    def "field tracks type and fieldDefinition"(){
        given:
        def parentType = droidType
        traversalContext.parentTypeStack.add(parentType)
        Field field = new Field("id")

        when:
        traversalContext.enter(field)

        then:
        traversalContext.getType() == droidType.getFieldDefinition("id").getType()
        traversalContext.getFieldDef() == droidType.getFieldDefinition("id")

        when:
        traversalContext.leave(field)

        then:
        traversalContext.getType() == null
        traversalContext.getFieldDef() == null

    }

}
