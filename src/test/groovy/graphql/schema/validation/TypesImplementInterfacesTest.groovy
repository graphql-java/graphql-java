package graphql.schema.validation

import graphql.TestUtil
import graphql.schema.GraphQLCodeRegistry
import graphql.schema.GraphQLInterfaceType
import graphql.schema.GraphQLObjectType
import graphql.schema.GraphQLOutputType
import graphql.schema.GraphQLSchemaElement
import graphql.schema.GraphQLUnionType
import graphql.schema.TypeResolver
import spock.lang.Specification

import static graphql.Scalars.GraphQLBoolean
import static graphql.Scalars.GraphQLInt
import static graphql.Scalars.GraphQLString
import static graphql.schema.GraphQLArgument.newArgument
import static graphql.schema.GraphQLFieldDefinition.newFieldDefinition
import static graphql.schema.GraphQLInterfaceType.newInterface
import static graphql.schema.GraphQLList.list
import static graphql.schema.GraphQLNonNull.nonNull
import static graphql.schema.GraphQLObjectType.newObject
import static graphql.schema.GraphQLSchema.newSchema
import static graphql.schema.GraphQLUnionType.newUnionType

class TypesImplementInterfacesTest extends Specification {

    // each implementing type gets a schema of its own so its errors can be checked in isolation
    static Set<String> errorsFor(GraphQLObjectType implementingType) {
        def query = newObject().name("Query")
                .field(newFieldDefinition().name("impl").type(implementingType))
                .build()
        try {
            newSchema().query(query).codeRegistry(codeRegistryWithTypeResolvers(query)).build()
            return [] as Set
        } catch (InvalidSchemaException e) {
            return e.errors.collect { it.description } as Set
        }
    }

    static GraphQLCodeRegistry codeRegistryWithTypeResolvers(GraphQLObjectType queryType) {
        def codeRegistry = GraphQLCodeRegistry.newCodeRegistry()
        Set<GraphQLSchemaElement> seen = Collections.newSetFromMap(new IdentityHashMap<>())
        Deque<GraphQLSchemaElement> stack = new ArrayDeque<>([queryType])
        while (!stack.isEmpty()) {
            def element = stack.pop()
            if (!seen.add(element)) {
                continue
            }
            if (element instanceof GraphQLInterfaceType || element instanceof GraphQLUnionType) {
                codeRegistry.typeResolver(element.name, { null } as TypeResolver)
            }
            stack.addAll(element.children)
        }
        codeRegistry.build()
    }

    def "field argument defaults do not affect interface implementation validity"() {
        given:
        def interfaceArgument = newArgument().name("limit").type(GraphQLInt)
        def implementingArgument = newArgument().name("limit").type(GraphQLInt)
        if (interfaceDefault != null) {
            interfaceArgument.defaultValueProgrammatic(interfaceDefault)
        }
        if (implementingDefault != null) {
            implementingArgument.defaultValueProgrammatic(implementingDefault)
        }
        def account = newInterface()
                .name("Account")
                .field(newFieldDefinition().name("memberOf").type(GraphQLString).argument(interfaceArgument))
                .build()
        def host = newObject()
                .name("Host")
                .withInterface(account)
                .field(newFieldDefinition().name("memberOf").type(GraphQLString).argument(implementingArgument))
                .build()

        expect:
        errorsFor(host).isEmpty()

        where:
        interfaceDefault | implementingDefault
        100              | 150
        100              | null
        null             | 150
    }

    def "objects implement interfaces"() {
        given:
        def interfaceType = newInterface()
                .name("Interface")
                .field(newFieldDefinition().name("name").type(GraphQLString))
                .field(newFieldDefinition().name("friends").type(list(GraphQLString)))
                .field(newFieldDefinition().name("age").type(GraphQLInt))
                .field(newFieldDefinition().name("address").type(list(GraphQLString)))
                .field(newFieldDefinition().name("argField1").type(GraphQLString)
                        .argument(newArgument().name("arg1").type(GraphQLString))
                        .argument(newArgument().name("arg2").type(GraphQLInt))
                        .argument(newArgument().name("arg3").type(GraphQLBoolean))
                        .argument(newArgument().name("arg4").type(GraphQLString).defaultValueProgrammatic("ABC")))
                .field(newFieldDefinition().name("argField2").type(GraphQLString)
                        .argument(newArgument().name("arg1").type(GraphQLString))
                        .argument(newArgument().name("arg2").type(GraphQLInt))
                        .argument(newArgument().name("arg3").type(GraphQLBoolean)))
                .build()
        def objType = newObject()
                .name("obj")
                .withInterface(interfaceType)
                .field(newFieldDefinition().name("name").type(GraphQLString))
                .field(newFieldDefinition().name("missing").type(list(GraphQLString)))
                .field(newFieldDefinition().name("age").type(GraphQLString))
                .field(newFieldDefinition().name("address").type(list(nonNull(GraphQLString))))
                .field(newFieldDefinition().name("argField1").type(GraphQLString)
                        .argument(newArgument().name("arg1").type(GraphQLInt))
                        .argument(newArgument().name("arg2").type(GraphQLInt))
                        .argument(newArgument().name("arg3").type(GraphQLInt))
                        .argument(newArgument().name("arg4").type(GraphQLString).defaultValueProgrammatic("XYZ")))
                .field(newFieldDefinition().name("argField2").type(GraphQLString)
                        .argument(newArgument().name("arg1").type(GraphQLString)))
                .build()

        when:
        def errors = errorsFor(objType)

        then:
        errors == [
                "object type 'obj' does not implement interface 'Interface' because field 'friends' is missing",
                "object type 'obj' does not implement interface 'Interface' because field 'age' is defined as 'String' type and not as 'Int' type",
                "object type 'obj' does not implement interface 'Interface' because field 'argField1' argument 'arg1' is defined differently",
                "object type 'obj' does not implement interface 'Interface' because field 'argField1' argument 'arg3' is defined differently",
                "object type 'obj' does not implement interface 'Interface' because field 'argField2' is missing argument(s): 'arg2, arg3'",
        ] as Set
    }

    def "field type compatibility: #scenario"() {
        given:
        def (interfaceFieldType, goodFieldType, badFieldType) = fieldTypes(scenario)
        def interfaceType = newInterface()
                .name("TestInterface")
                .field(newFieldDefinition().name("field").type(interfaceFieldType))
                .build()
        def goodImpl = newObject()
                .name("GoodImpl")
                .field(newFieldDefinition().name("field").type(goodFieldType))
                .withInterface(interfaceType)
                .build()
        def badImpl = newObject()
                .name("BadImpl")
                .field(newFieldDefinition().name("field").type(badFieldType))
                .withInterface(interfaceType)
                .build()

        expect:
        errorsFor(goodImpl).isEmpty()
        !errorsFor(badImpl).isEmpty()

        where:
        scenario << [
                "object implementing interface",
                "list of objects implementing interface",
                "list of interfaces implementing interface",
                "member of union",
                "non-null",
        ]
    }

    // returns the interface field type, a compatible implementing field type and an incompatible one
    static List<GraphQLOutputType> fieldTypes(String scenario) {
        def person = newInterface().name("Person").field(newFieldDefinition().name("name").type(GraphQLString)).build()
        def prop = newObject().name("Prop").field(newFieldDefinition().name("name").type(GraphQLString)).build()
        switch (scenario) {
            case "object implementing interface":
                return [person, actorImplementing(person), prop]
            case "list of objects implementing interface":
                return [list(person), list(actorImplementing(person)), list(prop)]
            case "list of interfaces implementing interface":
                def actor = newInterface().name("Actor").field(newFieldDefinition().name("name").type(GraphQLString)).withInterface(person).build()
                return [list(person), list(actor), list(prop)]
            case "member of union":
                def actor = newObject().name("Actor").field(newFieldDefinition().name("name").type(GraphQLString)).build()
                def director = newObject().name("Director").field(newFieldDefinition().name("name").type(GraphQLString)).build()
                def union = newUnionType().name("Person").possibleType(actor).possibleType(director).build()
                return [union, actor, prop]
            case "non-null":
                return [GraphQLString, nonNull(GraphQLString), nonNull(GraphQLInt)]
        }
        throw new IllegalArgumentException(scenario)
    }

    static GraphQLObjectType actorImplementing(GraphQLInterfaceType person) {
        newObject().name("Actor").field(newFieldDefinition().name("name").type(GraphQLString)).withInterface(person).build()
    }

    def "list and non-list types are not interchangeable: #scenario"() {
        given:
        def interfaceType = newInterface()
                .name("Interface")
                .field(newFieldDefinition().name("field").type(interfaceFieldType))
                .field(newFieldDefinition().name("withArg").type(GraphQLString)
                        .argument(newArgument().name("arg").type(interfaceArgType)))
                .build()
        def objType = newObject()
                .name("Object")
                .withInterface(interfaceType)
                .field(newFieldDefinition().name("field").type(objectFieldType))
                .field(newFieldDefinition().name("withArg").type(GraphQLString)
                        .argument(newArgument().name("arg").type(objectArgType)))
                .build()

        expect:
        errorsFor(objType) == expected as Set

        where:
        scenario               | interfaceFieldType   | objectFieldType | interfaceArgType     | objectArgType || expected
        "field list vs scalar" | list(GraphQLString)  | GraphQLString   | GraphQLString        | GraphQLString || ["object type 'Object' does not implement interface 'Interface' because field 'field' is defined as 'String' type and not as '[String]' type"]
        "argument list vs scalar" | GraphQLString     | GraphQLString   | list(GraphQLString)  | GraphQLString || ["object type 'Object' does not implement interface 'Interface' because field 'withArg' argument 'arg' is defined differently"]
    }

    def "field is a non null object"() {
        given:
        def memberInterface = newInterface()
                .name("TestMemberInterface")
                .field(newFieldDefinition().name("field").type(GraphQLString))
                .build()
        def memberInterfaceImpl = newObject()
                .name("TestMemberInterfaceImpl")
                .field(newFieldDefinition().name("field").type(GraphQLString))
                .withInterface(memberInterface)
                .build()
        def testInterface = newInterface()
                .name("TestInterface")
                .field(newFieldDefinition().name("field").type(nonNull(memberInterface)))
                .build()
        def testInterfaceImpl = newObject()
                .name("TestInterfaceImpl")
                .field(newFieldDefinition().name("field").type(nonNull(memberInterfaceImpl)))
                .withInterface(testInterface)
                .build()

        expect:
        errorsFor(testInterfaceImpl).isEmpty()
    }

    def "type can declare extra optional field arguments"() {
        given:
        def interfaceType = newInterface()
                .name("Interface")
                .field(newFieldDefinition().name("argField").type(GraphQLString))
                .build()
        def objType = newObject()
                .name("Object")
                .withInterface(interfaceType)
                .field(newFieldDefinition().name("argField").type(GraphQLString)
                        .argument(newArgument().name("arg1").type(GraphQLInt)))
                .build()

        expect:
        errorsFor(objType).isEmpty()
    }

    def "type should declare all arguments present in implemented interface"() {
        given:
        def interfaceType = newInterface()
                .name("Interface")
                .field(newFieldDefinition().name("argField").type(GraphQLString)
                        .argument(newArgument().name("arg1").type(GraphQLInt))
                        .argument(newArgument().name("arg2").type(GraphQLInt)))
                .build()
        def objType = newObject()
                .name("Object")
                .withInterface(interfaceType)
                .field(newFieldDefinition().name("argField").type(GraphQLString)
                        .argument(newArgument().name("argX").type(GraphQLInt))
                        .argument(newArgument().name("argZ").type(GraphQLInt)))
                .build()

        when:
        def errors = errorsFor(objType)

        then:
        errors == ["object type 'Object' does not implement interface 'Interface' because field 'argField' is missing argument(s): 'arg1, arg2'"] as Set
    }

    def "type cannot declare extra non-null field arguments"() {
        given:
        def interfaceType = newInterface()
                .name("Interface")
                .field(newFieldDefinition().name("argField").type(GraphQLString))
                .build()
        def objType = newObject()
                .name("Object")
                .withInterface(interfaceType)
                .field(newFieldDefinition().name("argField").type(GraphQLString)
                        .argument(newArgument().name("arg1").type(nonNull(GraphQLInt))))
                .build()

        when:
        def errors = errorsFor(objType)

        then:
        errors == ["object type 'Object' field 'argField' defines an additional non-optional argument 'arg1' which is not allowed because field is also defined in interface 'Interface'"] as Set
    }

    def "type can change order of fields"() {
        given:
        def interfaceType = newInterface()
                .name("Interface")
                .field(newFieldDefinition().name("argField1").type(GraphQLString))
                .field(newFieldDefinition().name("argField2").type(GraphQLString))
                .build()
        def objType = newObject()
                .name("Object")
                .withInterface(interfaceType)
                .field(newFieldDefinition().name("argField2").type(GraphQLString))
                .field(newFieldDefinition().name("argField1").type(GraphQLString))
                .build()

        expect:
        errorsFor(objType).isEmpty()
    }

    def "interfaces declaring their transitive interfaces are valid"() {
        when:
        TestUtil.schema('''
            type Query { find: Image }
            interface Node { id: ID! }
            interface Resource implements Node { id: ID! url: String }
            interface Image implements Resource & Node { id: ID! url: String thumbnail: String }
        ''')

        then:
        noExceptionThrown()
    }
}
