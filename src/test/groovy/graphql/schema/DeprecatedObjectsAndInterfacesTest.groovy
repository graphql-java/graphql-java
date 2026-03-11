package graphql.schema

import graphql.TestUtil
import graphql.introspection.IntrospectionQuery
import graphql.introspection.IntrospectionQueryBuilder
import graphql.schema.idl.SchemaPrinter
import spock.lang.Specification

import static graphql.Scalars.GraphQLString
import static graphql.schema.GraphQLFieldDefinition.newFieldDefinition
import static graphql.schema.GraphQLInterfaceType.newInterface
import static graphql.schema.GraphQLObjectType.newObject

class DeprecatedObjectsAndInterfacesTest extends Specification {

    def "object type can be deprecated programmatically"() {
        when:
        def objectType = newObject().name("Foo")
                .deprecate("Use Bar instead")
                .field(newFieldDefinition().name("id").type(GraphQLString))
                .build()

        then:
        objectType.isDeprecated()
        objectType.getDeprecationReason() == "Use Bar instead"
    }

    def "object type is not deprecated by default"() {
        when:
        def objectType = newObject().name("Foo")
                .field(newFieldDefinition().name("id").type(GraphQLString))
                .build()

        then:
        !objectType.isDeprecated()
        objectType.getDeprecationReason() == null
    }

    def "object type deprecation can be removed via transform"() {
        given:
        def objectType = newObject().name("Foo")
                .deprecate("Use Bar instead")
                .field(newFieldDefinition().name("id").type(GraphQLString))
                .build()

        when:
        def transformed = objectType.transform({ builder -> builder.deprecate(null) })

        then:
        objectType.isDeprecated()
        objectType.getDeprecationReason() == "Use Bar instead"
        !transformed.isDeprecated()
        transformed.getDeprecationReason() == null
    }

    def "interface type can be deprecated programmatically"() {
        when:
        def interfaceType = newInterface().name("Foo")
                .deprecate("Use Bar instead")
                .field(newFieldDefinition().name("id").type(GraphQLString))
                .build()

        then:
        interfaceType.isDeprecated()
        interfaceType.getDeprecationReason() == "Use Bar instead"
    }

    def "interface type is not deprecated by default"() {
        when:
        def interfaceType = newInterface().name("Foo")
                .field(newFieldDefinition().name("id").type(GraphQLString))
                .build()

        then:
        !interfaceType.isDeprecated()
        interfaceType.getDeprecationReason() == null
    }

    def "interface type deprecation can be removed via transform"() {
        given:
        def interfaceType = newInterface().name("Foo")
                .deprecate("Use Bar instead")
                .field(newFieldDefinition().name("id").type(GraphQLString))
                .build()

        when:
        def transformed = interfaceType.transform({ builder -> builder.deprecate(null) })

        then:
        interfaceType.isDeprecated()
        interfaceType.getDeprecationReason() == "Use Bar instead"
        !transformed.isDeprecated()
        transformed.getDeprecationReason() == null
    }

    def "deprecated object type can be built from SDL"() {
        def spec = '''
            type Query {
                foo : Foo
            }
            type Foo @deprecated(reason: "Use Bar instead") {
                id : String
            }
        '''

        when:
        def schema = TestUtil.schema(spec)
        def fooType = schema.getObjectType("Foo")

        then:
        fooType.isDeprecated()
        fooType.getDeprecationReason() == "Use Bar instead"
    }

    def "deprecated object type with default reason from SDL"() {
        def spec = '''
            type Query {
                foo : Foo
            }
            type Foo @deprecated {
                id : String
            }
        '''

        when:
        def schema = TestUtil.schema(spec)
        def fooType = schema.getObjectType("Foo")

        then:
        fooType.isDeprecated()
        fooType.getDeprecationReason() == "No longer supported"
    }

    def "deprecated interface type can be built from SDL"() {
        def spec = '''
            type Query {
                foo : Foo
            }
            interface Foo @deprecated(reason: "Use Bar instead") {
                id : String
            }
            type FooImpl implements Foo {
                id : String
            }
        '''

        when:
        def schema = TestUtil.schema(spec)
        def fooType = schema.getType("Foo") as GraphQLInterfaceType

        then:
        fooType.isDeprecated()
        fooType.getDeprecationReason() == "Use Bar instead"
    }

    def "deprecated object type is visible in introspection"() {
        def spec = '''
            type Query {
                foo : Foo
                bar : Bar
            }
            type Foo @deprecated(reason: "Use Bar instead") {
                id : String
            }
            type Bar {
                id : String
            }
        '''

        when:
        def graphQL = TestUtil.graphQL(spec).build()
        def introspectionQuery = IntrospectionQueryBuilder.build(IntrospectionQueryBuilder.Options.defaultOptions().typeDeprecation(true))
        def executionResult = graphQL.execute(introspectionQuery)

        then:
        executionResult.errors.isEmpty()

        def types = executionResult.data['__schema']['types'] as List
        def fooType = types.find { it['name'] == 'Foo' }
        fooType['isDeprecated'] == true
        fooType['deprecationReason'] == "Use Bar instead"

        def barType = types.find { it['name'] == 'Bar' }
        barType['isDeprecated'] == false
        barType['deprecationReason'] == null
    }

    def "deprecated interface type is visible in introspection"() {
        def spec = '''
            type Query {
                foo : Foo
            }
            interface Foo @deprecated(reason: "Use BarInterface instead") {
                id : String
            }
            type FooImpl implements Foo {
                id : String
            }
        '''

        when:
        def graphQL = TestUtil.graphQL(spec).build()
        def introspectionQuery = IntrospectionQueryBuilder.build(IntrospectionQueryBuilder.Options.defaultOptions().typeDeprecation(true))
        def executionResult = graphQL.execute(introspectionQuery)

        then:
        executionResult.errors.isEmpty()

        def types = executionResult.data['__schema']['types'] as List
        def fooType = types.find { it['name'] == 'Foo' }
        fooType['isDeprecated'] == true
        fooType['deprecationReason'] == "Use BarInterface instead"
    }

    def "non-deprecated types return null for isDeprecated in introspection"() {
        def spec = '''
            type Query {
                foo : String
            }
        '''

        when:
        def graphQL = TestUtil.graphQL(spec).build()
        def introspectionQuery = IntrospectionQueryBuilder.build(IntrospectionQueryBuilder.Options.defaultOptions().typeDeprecation(true))
        def executionResult = graphQL.execute(introspectionQuery)

        then:
        executionResult.errors.isEmpty()

        def types = executionResult.data['__schema']['types'] as List
        def stringType = types.find { it['name'] == 'String' }
        stringType['isDeprecated'] == null
        stringType['deprecationReason'] == null
    }

    def "deprecated object type is printed in SDL"() {
        def spec = '''
            type Query {
                foo : Foo
            }
            type Foo @deprecated(reason: "Use Bar instead") {
                id : String
            }
        '''

        when:
        def schema = TestUtil.schema(spec)
        def printed = new SchemaPrinter(SchemaPrinter.Options.defaultOptions().includeDirectiveDefinitions(false)).print(schema)

        then:
        printed.contains('type Foo @deprecated(reason : "Use Bar instead")')
    }

    def "deprecated interface type is printed in SDL"() {
        def spec = '''
            type Query {
                foo : Foo
            }
            interface Foo @deprecated(reason: "Use Bar instead") {
                id : String
            }
            type FooImpl implements Foo {
                id : String
            }
        '''

        when:
        def schema = TestUtil.schema(spec)
        def printed = new SchemaPrinter(SchemaPrinter.Options.defaultOptions().includeDirectiveDefinitions(false)).print(schema)

        then:
        printed.contains('interface Foo @deprecated(reason : "Use Bar instead")')
    }
}
