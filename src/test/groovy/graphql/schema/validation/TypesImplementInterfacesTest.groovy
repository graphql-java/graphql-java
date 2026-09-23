package graphql.schema.validation

import graphql.TestUtil
import graphql.schema.GraphQLCodeRegistry
import graphql.schema.GraphQLInterfaceType
import graphql.schema.GraphQLObjectType
import graphql.schema.GraphQLSchema
import graphql.schema.GraphQLTypeReference
import spock.lang.Specification
import spock.lang.Unroll

import static SchemaValidationErrorType.ObjectDoesNotImplementItsInterfaces
import static graphql.Scalars.GraphQLBoolean
import static graphql.Scalars.GraphQLInt
import static graphql.Scalars.GraphQLString
import static graphql.schema.GraphQLArgument.newArgument
import static graphql.schema.GraphQLFieldDefinition.newFieldDefinition
import static graphql.schema.GraphQLInterfaceType.newInterface
import static graphql.schema.GraphQLList.list
import static graphql.schema.GraphQLNonNull.nonNull
import static graphql.schema.GraphQLObjectType.newObject
import static graphql.schema.GraphQLUnionType.newUnionType

class TypesImplementInterfacesTest extends Specification {

    GraphQLInterfaceType InterfaceType = newInterface()
            .name("Interface")

            .field(newFieldDefinition().name("name").type(GraphQLString))
            .field(newFieldDefinition().name("friends").type(list(GraphQLString)))
            .field(newFieldDefinition().name("age").type(GraphQLInt))
            .field(newFieldDefinition().name("address").type(list(GraphQLString)))

            .field(newFieldDefinition().name("argField1").type(GraphQLString)
            .argument(newArgument().name("arg1").type(GraphQLString))
            .argument(newArgument().name("arg2").type(GraphQLInt))
            .argument(newArgument().name("arg3").type(GraphQLBoolean))
            .argument(newArgument().name("arg4").type(GraphQLString).defaultValueProgrammatic("ABC"))
    )

            .field(newFieldDefinition().name("argField2").type(GraphQLString)
            .argument(newArgument().name("arg1").type(GraphQLString))
            .argument(newArgument().name("arg2").type(GraphQLInt))
            .argument(newArgument().name("arg3").type(GraphQLBoolean))
    )
            .build()

    def "objects implement interfaces"() {
        given:

        SchemaValidationErrorCollector errorCollector = new SchemaValidationErrorCollector()
        GraphQLObjectType objType = newObject()
                .name("obj")
                .withInterface(InterfaceType)
                .field(newFieldDefinition().name("name").type(GraphQLString))
                .field(newFieldDefinition().name("missing").type(list(GraphQLString)))
                .field(newFieldDefinition().name("age").type(GraphQLString))
                .field(newFieldDefinition().name("address").type(list(nonNull(GraphQLString))))

                .field(newFieldDefinition().name("argField1").type(GraphQLString)
                .argument(newArgument().name("arg1").type(GraphQLInt))
                .argument(newArgument().name("arg2").type(GraphQLInt))
                .argument(newArgument().name("arg3").type(GraphQLInt))
                .argument(newArgument().name("arg4").type(GraphQLString).defaultValueProgrammatic("XYZ"))
        )

                .field(newFieldDefinition().name("argField2").type(GraphQLString)
                .argument(newArgument().name("arg1").type(GraphQLString))
        )

                .build()

        when:
        new TypesImplementInterfaces().check(objType, errorCollector)

        then:

        errorCollector.containsValidationError(ObjectDoesNotImplementItsInterfaces)
        def errors = errorCollector.getErrors()
        errors.size() == 6
        errors.contains(new SchemaValidationError(ObjectDoesNotImplementItsInterfaces,
                "object type 'obj' does not implement interface 'Interface' because field 'friends' is missing"))
        errors.contains(new SchemaValidationError(ObjectDoesNotImplementItsInterfaces,
                "object type 'obj' does not implement interface 'Interface' because field 'age' is defined as 'String' type and not as 'Int' type"))
        errors.contains(new SchemaValidationError(ObjectDoesNotImplementItsInterfaces,
                "object type 'obj' does not implement interface 'Interface' because field 'argField1' argument 'arg1' is defined differently"))
        errors.contains(new SchemaValidationError(ObjectDoesNotImplementItsInterfaces,
                "object type 'obj' does not implement interface 'Interface' because field 'argField1' argument 'arg3' is defined differently"))
        errors.contains(new SchemaValidationError(ObjectDoesNotImplementItsInterfaces,
                "object type 'obj' does not implement interface 'Interface' because field 'argField1' argument 'arg4' is defined differently"))
        errors.contains(new SchemaValidationError(ObjectDoesNotImplementItsInterfaces,
                "object type 'obj' does not implement interface 'Interface' because field 'argField2' is missing argument(s): 'arg2, arg3'"))
    }

    def "types must explicitly implement transitive interfaces"() {
        given:
        def baseInterface = newInterface()
                .name("BaseInterface")
                .field(newFieldDefinition().name("name").type(GraphQLString))
                .build()
        def childInterface = newInterface()
                .name("ChildInterface")
                .field(newFieldDefinition().name("name").type(GraphQLString))
                .withInterface(baseInterface)
                .build()
        def incompleteImplementation = newObject()
                .name("IncompleteImplementation")
                .field(newFieldDefinition().name("name").type(GraphQLString))
                .withInterface(childInterface)
                .build()
        def completeImplementation = newObject()
                .name("CompleteImplementation")
                .field(newFieldDefinition().name("name").type(GraphQLString))
                .withInterface(baseInterface)
                .withInterface(childInterface)
                .build()
        def missingErrorCollector = new SchemaValidationErrorCollector()
        def completeErrorCollector = new SchemaValidationErrorCollector()

        when:
        new TypesImplementInterfaces().check(incompleteImplementation, missingErrorCollector)
        new TypesImplementInterfaces().check(completeImplementation, completeErrorCollector)

        then:
        missingErrorCollector.getErrors()*.description == [
                "object type 'IncompleteImplementation' must implement 'BaseInterface' because it is implemented by 'ChildInterface'"
        ]
        completeErrorCollector.getErrors().isEmpty()
    }

    def "interfaces cannot circularly implement each other"() {
        given:
        def firstInterface = newInterface()
                .name("FirstInterface")
                .field(newFieldDefinition().name("name").type(GraphQLString))
                .withInterface(GraphQLTypeReference.typeRef("SecondInterface"))
                .build()
        def secondInterface = newInterface()
                .name("SecondInterface")
                .field(newFieldDefinition().name("name").type(GraphQLString))
                .withInterface(firstInterface)
                .build()
        def query = newObject()
                .name("Query")
                .field(newFieldDefinition().name("find").type(secondInterface))
                .build()
        def codeRegistry = GraphQLCodeRegistry.newCodeRegistry()
                .typeResolver(firstInterface, { null })
                .typeResolver(secondInterface, { null })
                .build()

        when:
        GraphQLSchema.newSchema()
                .query(query)
                .codeRegistry(codeRegistry)
                .build()

        then:
        def exception = thrown(InvalidSchemaException)
        exception.getErrors()*.description as Set == [
                "interface type 'FirstInterface' cannot implement 'SecondInterface' because that would result on a circular reference",
                "interface type 'SecondInterface' cannot implement 'FirstInterface' because that would result on a circular reference"
        ] as Set
    }

    def "field is object implementing interface"() {
        given:
        def person = newInterface()
                .name("Person")
                .field(newFieldDefinition().name("name").type(GraphQLString).build())
                .build()

        def actor = newObject()
                .name("Actor")
                .field(newFieldDefinition().name("name").type(GraphQLString).build())
                .withInterface(person)
                .build()

        def prop = newObject()
                .name("Prop")
                .field(newFieldDefinition().name("name").type(GraphQLString).build())
                .build()

        GraphQLInterfaceType interfaceType = newInterface()
                .name("TestInterface")
                .field(newFieldDefinition().name("field").type(person).build())
                .build()

        GraphQLObjectType goodImpl = newObject()
                .name("GoodImpl")
                .field(newFieldDefinition().name("field").type(actor).build())
                .withInterface(interfaceType)
                .build()

        GraphQLObjectType badImpl = newObject()
                .name("BadImpl")
                .field(newFieldDefinition().name("field").type(prop).build())
                .withInterface(interfaceType)
                .build()

        SchemaValidationErrorCollector goodErrorCollector = new SchemaValidationErrorCollector()
        SchemaValidationErrorCollector badErrorCollector = new SchemaValidationErrorCollector()

        when:
        new TypesImplementInterfaces().check(goodImpl, goodErrorCollector)
        new TypesImplementInterfaces().check(badImpl, badErrorCollector)

        then:
        goodErrorCollector.getErrors().isEmpty()
        !badErrorCollector.getErrors().isEmpty()
    }

    def "field is list of objects implementing interface"() {
        given:
        def person = newInterface()
                .name("Person")
                .field(newFieldDefinition().name("name").type(GraphQLString).build())
                .build()

        def actor = newObject()
                .name("Actor")
                .field(newFieldDefinition().name("name").type(GraphQLString).build())
                .withInterface(person)
                .build()

        def prop = newObject()
                .name("Prop")
                .field(newFieldDefinition().name("name").type(GraphQLString).build())
                .build()

        GraphQLInterfaceType interfaceType = newInterface()
                .name("TestInterface")
                .field(newFieldDefinition().name("field").type(list(person)).build())
                .build()

        GraphQLObjectType goodImpl = newObject()
                .name("GoodImpl")
                .field(newFieldDefinition().name("field").type(list(actor)).build())
                .withInterface(interfaceType)
                .build()

        GraphQLObjectType badImpl = newObject()
                .name("BadImpl")
                .field(newFieldDefinition().name("field").type(list(prop)).build())
                .withInterface(interfaceType)
                .build()

        SchemaValidationErrorCollector goodErrorCollector = new SchemaValidationErrorCollector()
        SchemaValidationErrorCollector badErrorCollector = new SchemaValidationErrorCollector()

        when:
        new TypesImplementInterfaces().check(goodImpl, goodErrorCollector)
        new TypesImplementInterfaces().check(badImpl, badErrorCollector)

        then:
        goodErrorCollector.getErrors().isEmpty()
        !badErrorCollector.getErrors().isEmpty()
    }

    def "field is list of interfaces implementing interface" () {
        given:
        def person = newInterface()
                .name("Person")
                .field(newFieldDefinition().name("name").type(GraphQLString).build())
                .build()

        def actor = newInterface()
                .name("Actor")
                .field(newFieldDefinition().name("name").type(GraphQLString).build())
                .withInterface(person)
                .build()

        def prop = newObject()
                .name("Prop")
                .field(newFieldDefinition().name("name").type(GraphQLString).build())
                .build()

        GraphQLInterfaceType interfaceType = newInterface()
                .name("TestInterface")
                .field(newFieldDefinition().name("field").type(list(person)).build())
                .build()

        GraphQLObjectType goodImpl = newObject()
                .name("GoodImpl")
                .field(newFieldDefinition().name("field").type(list(actor)).build())
                .withInterface(interfaceType)
                .build()

        GraphQLObjectType badImpl = newObject()
                .name("BadImpl")
                .field(newFieldDefinition().name("field").type(list(prop)).build())
                .withInterface(interfaceType)
                .build()

        SchemaValidationErrorCollector goodErrorCollector = new SchemaValidationErrorCollector()
        SchemaValidationErrorCollector badErrorCollector = new SchemaValidationErrorCollector()

        when:
        new TypesImplementInterfaces().check(goodImpl, goodErrorCollector)
        new TypesImplementInterfaces().check(badImpl, badErrorCollector)

        then:
        goodErrorCollector.getErrors().isEmpty()
        !badErrorCollector.getErrors().isEmpty()
    }

    def "interface and list fields are incompatible with unrelated field types"() {
        given:
        def interfaceType = newInterface()
                .name("Interface")
                .field(newFieldDefinition().name("field").type(GraphQLString))
                .build()
        def validator = new TypesImplementInterfaces()

        expect:
        !validator.isCompatible(interfaceType, GraphQLString)
        !validator.isCompatible(list(GraphQLString), GraphQLString)
    }

    def "field is member of union"() {
        given:
        def actor = newObject()
                .name("Actor")
                .field(newFieldDefinition().name("name").type(GraphQLString).build())
                .build()

        def director = newObject()
                .name("Director")
                .field(newFieldDefinition().name("name").type(GraphQLString).build())
                .build()

        def person = newUnionType()
                .name("Person")
                .possibleType(actor)
                .possibleType(director)
                .build()

        def prop = newObject()
                .name("Prop")
                .field(newFieldDefinition().name("name").type(GraphQLString).build())
                .build()

        GraphQLInterfaceType interfaceType = newInterface()
                .name("TestInterface")
                .field(newFieldDefinition().name("field").type(person).build())
                .build()

        GraphQLObjectType goodImpl = newObject()
                .name("GoodImpl")
                .field(newFieldDefinition().name("field").type(actor).build())
                .withInterface(interfaceType)
                .build()

        GraphQLObjectType badImpl = newObject()
                .name("BadImpl")
                .field(newFieldDefinition().name("field").type(prop).build())
                .withInterface(interfaceType)
                .build()

        SchemaValidationErrorCollector goodErrorCollector = new SchemaValidationErrorCollector()
        SchemaValidationErrorCollector badErrorCollector = new SchemaValidationErrorCollector()

        when:
        new TypesImplementInterfaces().check(goodImpl, goodErrorCollector)
        new TypesImplementInterfaces().check(badImpl, badErrorCollector)

        then:
        goodErrorCollector.getErrors().isEmpty()
        !badErrorCollector.getErrors().isEmpty()
    }

    def "field is non-null"() {
        given:
        GraphQLInterfaceType interfaceType = newInterface()
                .name("TestInterface")
                .field(newFieldDefinition().name("field").type(GraphQLString).build())
                .build()

        GraphQLObjectType goodImpl = newObject()
                .name("GoodImpl")
                .field(newFieldDefinition().name("field").type(nonNull(GraphQLString)).build())
                .withInterface(interfaceType)
                .build()

        GraphQLObjectType badImpl = newObject()
                .name("BadImpl")
                .field(newFieldDefinition().name("field").type(nonNull(GraphQLInt)).build())
                .withInterface(interfaceType)
                .build()

        SchemaValidationErrorCollector goodErrorCollector = new SchemaValidationErrorCollector()
        SchemaValidationErrorCollector badErrorCollector = new SchemaValidationErrorCollector()

        when:
        new TypesImplementInterfaces().check(goodImpl, goodErrorCollector)
        new TypesImplementInterfaces().check(badImpl, badErrorCollector)

        then:
        goodErrorCollector.getErrors().isEmpty()
        !badErrorCollector.getErrors().isEmpty()
    }

    @Unroll
    def "implementing field can strengthen #kind nullability from #interfaceFieldType to #implementingFieldType"() {
        when:
        TestUtil.schema("""
            schema { query: B }

            enum E {
                VALUE
            }

            interface J {
                value: String
            }

            type A implements J {
                value: String
            }

            union U = A

            interface I {
                value: $interfaceFieldType
            }

            type B implements I {
                value: $implementingFieldType
            }
        """)

        then:
        noExceptionThrown()

        where:
        kind                | interfaceFieldType | implementingFieldType
        "scalar"            | "String"           | "String!"
        "enum"              | "E"                | "E!"
        "object"            | "A"                | "A!"
        "interface"         | "J"                | "J!"
        "union"             | "U"                | "U!"
        "list"              | "[String]"         | "[String]!"
        "list item"         | "[String]"         | "[String!]"
        "list and item"     | "[String]"         | "[String!]!"
        "nested list"       | "[[String]]"       | "[[String!]!]!"
        "list of unions"    | "[U]"              | "[U!]!"
        "list of interfaces" | "[J]"              | "[J!]!"
    }

    def "field is a non null object"() {
        given:
        GraphQLInterfaceType memberInterface = newInterface()
                .name("TestMemberInterface")
                .field(newFieldDefinition().name("field").type(GraphQLString).build())
                .build()

        GraphQLObjectType memberInterfaceImpl = newObject()
                .name("TestMemberInterfaceImpl")
                .field(newFieldDefinition().name("field").type(GraphQLString).build())
                .withInterface(memberInterface)
                .build()

        GraphQLInterfaceType testInterface = newInterface()
                .name("TestInterface")
                .field(newFieldDefinition().name("field").type(nonNull(memberInterface)).build())
                .build()

        GraphQLObjectType testInterfaceImpl = newObject()
                .name("TestInterfaceImpl")
                .field(newFieldDefinition().name("field").type(nonNull(memberInterfaceImpl)).build())
                .withInterface(testInterface)
                .build()

        SchemaValidationErrorCollector goodErrorCollector = new SchemaValidationErrorCollector()

        when:
        new TypesImplementInterfaces().check(testInterfaceImpl, goodErrorCollector)

        then:
        goodErrorCollector.getErrors().isEmpty()
    }

    def "type can declare extra optional field arguments"() {
        given:

        GraphQLInterfaceType InterfaceType = newInterface()
                .name("Interface")
                .field(newFieldDefinition().name("argField").type(GraphQLString))
                .build()

        SchemaValidationErrorCollector errorCollector = new SchemaValidationErrorCollector()

        GraphQLObjectType objType = newObject()
                .name("Object")
                .withInterface(InterfaceType)
                .field(newFieldDefinition().name("argField").type(GraphQLString)
                        .argument(newArgument().name("arg1").type(GraphQLInt))
                )
                .build()

        when:
        new TypesImplementInterfaces().check(objType, errorCollector)

        then:
        def errors = errorCollector.getErrors()
        errors.isEmpty()
    }

    def "field argument defaults must be set in both the interface and implementing type"() {
        given:
        GraphQLInterfaceType interfaceType = newInterface()
                .name("Interface")
                .field(newFieldDefinition().name("argField").type(GraphQLString)
                        .argument(newArgument().name("interfaceDefault").type(GraphQLString).defaultValueProgrammatic("default"))
                        .argument(newArgument().name("objectDefault").type(GraphQLString)))
                .build()
        GraphQLObjectType objectType = newObject()
                .name("Object")
                .withInterface(interfaceType)
                .field(newFieldDefinition().name("argField").type(GraphQLString)
                        .argument(newArgument().name("interfaceDefault").type(GraphQLString))
                        .argument(newArgument().name("objectDefault").type(GraphQLString).defaultValueProgrammatic("default")))
                .build()
        SchemaValidationErrorCollector errorCollector = new SchemaValidationErrorCollector()

        when:
        new TypesImplementInterfaces().check(objectType, errorCollector)

        then:
        errorCollector.getErrors()*.description as Set == [
                "object type 'Object' does not implement interface 'Interface' because field 'argField' argument 'interfaceDefault' is defined differently",
                "object type 'Object' does not implement interface 'Interface' because field 'argField' argument 'objectDefault' is defined differently"
        ] as Set
    }

    def "type should declare all arguments present in implemented interface"() {
        given:

        GraphQLInterfaceType InterfaceType = newInterface()
                .name("Interface")
                .field(newFieldDefinition().name("argField").type(GraphQLString)
                        .argument(newArgument().name("arg1").type(GraphQLInt))
                        .argument(newArgument().name("arg2").type(GraphQLInt))
                )
                .build()

        SchemaValidationErrorCollector errorCollector = new SchemaValidationErrorCollector()

        GraphQLObjectType objType = newObject()
                .name("Object")
                .withInterface(InterfaceType)
                .field(newFieldDefinition().name("argField").type(GraphQLString)
                        .argument(newArgument().name("argX").type(GraphQLInt))
                        .argument(newArgument().name("argZ").type(GraphQLInt))
                )
                .build()

        when:
        new TypesImplementInterfaces().check(objType, errorCollector)

        then:
        def errors = errorCollector.getErrors()
        errors.size() == 1
        errors.iterator().next().description == "object type 'Object' does not implement interface 'Interface' because field 'argField' is missing argument(s): 'arg1, arg2'"
    }

    def "type cannot declare extra non-null field arguments"() {
        given:

        GraphQLInterfaceType InterfaceType = newInterface()
                .name("Interface")
                .field(newFieldDefinition().name("argField").type(GraphQLString))
                .build()

        SchemaValidationErrorCollector errorCollector = new SchemaValidationErrorCollector()

        GraphQLObjectType objType = newObject()
                .name("Object")
                .withInterface(InterfaceType)
                .field(newFieldDefinition().name("argField").type(GraphQLString)
                        .argument(newArgument().name("arg1").type(nonNull(GraphQLInt)))
                )
                .build()

        when:
        new TypesImplementInterfaces().check(objType, errorCollector)

        then:
        def errors = errorCollector.getErrors()
        errors.size() == 1
        errors.iterator().next().description == "object type 'Object' field 'argField' defines an additional non-optional argument 'arg1' which is not allowed because field is also defined in interface 'Interface'"
    }

    def "type can change order of field arguments"() {
        given:

        GraphQLInterfaceType InterfaceType = newInterface()
                .name("Interface")
                .field(newFieldDefinition().name("argField1").type(GraphQLString))
                .field(newFieldDefinition().name("argField2").type(GraphQLString))
                .build()

        SchemaValidationErrorCollector errorCollector = new SchemaValidationErrorCollector()

        GraphQLObjectType objType = newObject()
                .name("Object")
                .withInterface(InterfaceType)
                .field(newFieldDefinition().name("argField2").type(GraphQLString))
                .field(newFieldDefinition().name("argField1").type(GraphQLString))
                .build()

        when:
        new TypesImplementInterfaces().check(objType, errorCollector)

        then:
        def errors = errorCollector.getErrors()
        errors.isEmpty()
    }
}
