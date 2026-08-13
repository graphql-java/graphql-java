package graphql.schema.transform

import graphql.TestUtil
import graphql.schema.GraphQLDirectiveContainer
import graphql.schema.GraphQLInterfaceType
import graphql.schema.GraphQLObjectType
import graphql.schema.GraphQLSchema
import graphql.schema.validation.InvalidSchemaException
import spock.lang.Specification

class FieldVisibilitySchemaTransformationInterfaceImplementationTest extends Specification {

    def "existing constructor retains interface implementation relationships"() {
        given:
        GraphQLSchema schema = TestUtil.schema('''
            type Query {
                node: Node
            }

            interface Node {
                id: ID!
            }

            type Account implements Node {
                id: ID!
            }
        ''')
        def transformation = new FieldVisibilitySchemaTransformation({ true })

        when:
        GraphQLSchema transformedSchema = transformation.apply(schema)

        then:
        def account = transformedSchema.getType("Account") as GraphQLObjectType
        def node = transformedSchema.getType("Node") as GraphQLInterfaceType
        account.interfaces*.name == ["Node"]
        transformedSchema.getImplementations(node)*.name == ["Account"]
    }

    def "removes an interface and its relationship when all interface fields are hidden"() {
        given:
        GraphQLSchema schema = TestUtil.schema('''
            directive @private on FIELD_DEFINITION

            type Query {
                account: Account
            }

            interface Node {
                id: ID! @private
            }

            type Account implements Node {
                id: ID!
            }
        ''')
        VisibleFieldPredicate fieldPredicate = { environment ->
            def field = environment.schemaElement as GraphQLDirectiveContainer
            field.getAppliedDirective("private") == null
        }
        def transformation = new FieldVisibilitySchemaTransformation(fieldPredicate)

        when:
        GraphQLSchema transformedSchema = transformation.apply(schema)

        then:
        def account = transformedSchema.getType("Account") as GraphQLObjectType
        account.interfaces.empty
        transformedSchema.getType("Node") == null
    }

    def "removes an object interface relationship independently of field visibility"() {
        given:
        GraphQLSchema schema = TestUtil.schema('''
            type Query {
                account: Account
                node: Node
            }

            interface Node {
                id: ID!
            }

            type Account implements Node {
                id: ID!
                name: String
            }
        ''')
        def transformation = transformationWith({ environment ->
            environment.implementingType.name != "Account" || environment.interfaceType.name != "Node"
        })

        when:
        GraphQLSchema transformedSchema = transformation.apply(schema)

        then:
        def account = transformedSchema.getType("Account") as GraphQLObjectType
        def node = transformedSchema.getType("Node") as GraphQLInterfaceType
        account.interfaces.empty
        account.fieldDefinitions*.name as Set == ["id", "name"] as Set
        transformedSchema.getImplementations(node).empty
    }

    def "removes one object interface relationship while retaining another"() {
        given:
        GraphQLSchema schema = TestUtil.schema('''
            type Query {
                account: Account
                restricted: Restricted
            }

            interface Public {
                id: ID!
            }

            interface Restricted {
                secret: String
            }

            type Account implements Public & Restricted {
                id: ID!
                secret: String
            }
        ''')
        def transformation = transformationWith({ environment ->
            environment.interfaceType.name != "Restricted"
        })

        when:
        GraphQLSchema transformedSchema = transformation.apply(schema)

        then:
        def account = transformedSchema.getType("Account") as GraphQLObjectType
        def restricted = transformedSchema.getType("Restricted") as GraphQLInterfaceType
        account.interfaces*.name == ["Public"]
        transformedSchema.getImplementations(restricted).empty
    }

    def "removes an interface interface relationship"() {
        given:
        GraphQLSchema schema = TestUtil.schema('''
            type Query {
                child: Child
            }

            interface Parent {
                id: ID!
            }

            interface Child implements Parent {
                id: ID!
            }

            type Item implements Child & Parent {
                id: ID!
            }
        ''')
        def transformation = transformationWith({ environment ->
            environment.implementingType.name != "Child" || environment.interfaceType.name != "Parent"
        })

        when:
        GraphQLSchema transformedSchema = transformation.apply(schema)

        then:
        def child = transformedSchema.getType("Child") as GraphQLInterfaceType
        def item = transformedSchema.getType("Item") as GraphQLObjectType
        child.interfaces.empty
        item.interfaces*.name as Set == ["Child", "Parent"] as Set
    }

    def "removes a field and its interface relationship atomically"() {
        given:
        GraphQLSchema schema = TestUtil.schema('''
            directive @private on FIELD_DEFINITION

            type Query {
                account: Account
            }

            interface Node {
                id: ID!
            }

            type Account implements Node {
                id: ID! @private
                name: String
            }
        ''')
        VisibleFieldPredicate fieldPredicate = { environment ->
            def field = environment.schemaElement as GraphQLDirectiveContainer
            field.getAppliedDirective("private") == null
        }
        VisibleInterfaceImplementationPredicate relationshipPredicate = { environment ->
            environment.implementingType.name != "Account" || environment.interfaceType.name != "Node"
        }
        def transformation = new FieldVisibilitySchemaTransformation(fieldPredicate, relationshipPredicate)

        when:
        GraphQLSchema transformedSchema = transformation.apply(schema)

        then:
        def account = transformedSchema.getType("Account") as GraphQLObjectType
        account.interfaces.empty
        account.fieldDefinitions*.name == ["name"]
        transformedSchema.getType("Node") == null
    }

    def "removes an interface type when its removed relationship was the only path to it"() {
        given:
        GraphQLSchema schema = TestUtil.schema('''
            type Query {
                account: Account
            }

            interface Node {
                id: ID!
            }

            type Account implements Node {
                id: ID!
            }
        ''')
        def transformation = transformationWith({ false })

        when:
        GraphQLSchema transformedSchema = transformation.apply(schema)

        then:
        (transformedSchema.getType("Account") as GraphQLObjectType).interfaces.empty
        transformedSchema.getType("Node") == null
    }

    def "rejects removal required by a retained transitive relationship"() {
        given:
        GraphQLSchema schema = TestUtil.schema('''
            type Query {
                item: Item
            }

            interface Parent {
                id: ID!
            }

            interface Child implements Parent {
                id: ID!
            }

            type Item implements Child & Parent {
                id: ID!
            }
        ''')
        def transformation = transformationWith({ environment ->
            environment.implementingType.name != "Item" || environment.interfaceType.name != "Parent"
        })

        when:
        transformation.apply(schema)

        then:
        def exception = thrown(InvalidSchemaException)
        exception.message.contains("object type 'Item' must implement 'Parent' because it is implemented by 'Child'")
    }

    def "runs lifecycle hooks with interface relationship visibility"() {
        given:
        GraphQLSchema schema = TestUtil.schema('''
            type Query {
                value: String
            }
        ''')
        def events = []
        def transformation = new FieldVisibilitySchemaTransformation(
                { true } as VisibleFieldPredicate,
                { true } as VisibleInterfaceImplementationPredicate,
                { events.add("before") },
                { events.add("after") })

        when:
        transformation.apply(schema)

        then:
        events == ["before", "after"]
    }

    private FieldVisibilitySchemaTransformation transformationWith(VisibleInterfaceImplementationPredicate predicate) {
        return new FieldVisibilitySchemaTransformation({ true } as VisibleFieldPredicate, predicate)
    }
}
