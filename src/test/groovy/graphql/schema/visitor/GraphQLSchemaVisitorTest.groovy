package graphql.schema.visitor

import graphql.TestUtil
import graphql.schema.GraphQLFieldDefinition
import graphql.schema.GraphQLObjectType
import graphql.schema.SchemaTraverser
import graphql.util.TraversalControl
import spock.lang.Specification

import static graphql.util.TraversalControl.CONTINUE

class GraphQLSchemaVisitorTest extends Specification {

    def uberSDL = '''
            type Query {
                object : ObjectTypeA
            }
            
            type ObjectTypeA {
                fieldA : String
            }
            
            
            
    '''

    def schema = TestUtil.schema(uberSDL)


    class CapturingSchemaVisitor implements GraphQLSchemaVisitor {

        def types = [:]
        def fields = [:]

        @Override
        TraversalControl visitGraphQLObjectType(GraphQLObjectType objectType, ObjectVisitorEnvironment environment) {
            types[objectType.getName()] = objectType
            return CONTINUE
        }

        @Override
        TraversalControl visitGraphQLFieldDefinition(GraphQLFieldDefinition fieldDefinition, FieldVisitorEnvironment environment) {
            fields[environment.fieldsContainer.getName() + "." + fieldDefinition.getName()] = fieldDefinition

            return CONTINUE
        }
    }

    def "will visit things"() {

        def visitor = new CapturingSchemaVisitor()

        when:
        new SchemaTraverser().depthFirstFullSchema(visitor.toTypeVisitor(), schema)

        then:
        visitor.types["ObjectTypeA"] instanceof GraphQLObjectType

        visitor.fields["ObjectTypeA.fieldA"] instanceof GraphQLFieldDefinition

    }
}
