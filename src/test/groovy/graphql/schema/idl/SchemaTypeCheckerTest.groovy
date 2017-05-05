package graphql.schema.idl

import graphql.GraphQLError
import graphql.TypeResolutionEnvironment
import graphql.schema.GraphQLObjectType
import graphql.schema.TypeResolver
import graphql.schema.idl.errors.SchemaMissingError
import spock.lang.Specification

class SchemaTypeCheckerTest extends Specification {

    TypeDefinitionRegistry compile(String spec) {
        new SchemaCompiler().compile(spec)
    }

    List<GraphQLError> check(String spec) {
        def types = compile(spec)

        def resolver = new TypeResolver() {
            @Override
            GraphQLObjectType getType(TypeResolutionEnvironment env) {
                throw new UnsupportedOperationException("Not implemented")
            }
        }
        def wiring = RuntimeWiring.newRuntimeWiring().
                type(TypeRuntimeWiring.newTypeWiring("InterfaceType").typeResolver(resolver))
                .build()
        return new SchemaTypeChecker().checkTypeRegistry(types, wiring)
    }

    def "test missing type in object"() {

        def spec = """ 
          type Post {
              id: Int!
              title: String
              votes: Int
              author: Author ### not defined
            }

        """

        def result = check(spec)

        expect:

        result.get(0).getMessage().contains("The field type 'Author' is not present when resolving type 'Post'")
    }

    def "test missing type in interface"() {

        def spec = """ 
          interface Post {
              id: Int!
              title: String
              votes: Int
              author: Author  ### not defined
            }

        """

        def result = check(spec)

        expect:

        result.get(0).getMessage().contains("The field type 'Author' is not present when resolving type 'Post'")
    }

    def "test missing type in union type"() {

        def spec = """ 

            type Bar {
                id : ID!
            }
            
            union FooBar = Bar | Foo ### not defined
        """

        def result = check(spec)

        expect:

        result.get(0).getMessage().contains("The union member type 'Foo' is not present when resolving type 'FooBar'")
    }

    def "test missing type in input type"() {

        def spec = """ 

            input ListUsersInput {
                id: ID
                limit: Int
                author : Author ### not defined
            }
        """

        def result = check(spec)

        expect:

        result.get(0).getMessage().contains("The input value type 'Author' is not present when resolving type 'ListUsersInput'")
    }


    def "test missing type in extension type"() {

        def spec = """ 
            type Query {
                bars: [Bar]!
            }
            type Bar {
                id : ID!
            }
            
            extend type Query {
                foos: [Foo]!    ### not defined
            }
        """

        def result = check(spec)

        expect:

        result.get(0).getMessage().contains("The field type 'Foo' is not present when resolving type 'Query'")
    }

    def "test missing schema"() {

        def spec = """ 
            type Bar {
                id : ID!
            }
            
            # no schema defined and hence we can proceed
        """

        def result = check(spec)

        expect:

        result.get(0) instanceof SchemaMissingError
    }

    def "test missing schema operation types"() {

        def spec = """ 
            schema {
              query : MissingType
            }
        """

        def result = check(spec)

        expect:

        result.get(0).getMessage().contains("The operation type 'MissingType' is not present when resolving type 'query'")
    }

    def "test operation type is not an object"() {

        def spec = """                       

            schema {
              query : Int
            }
        """

        def result = check(spec)

        expect:

        result.get(0).getMessage().contains("The operation type 'query' MUST have a object type as its definition")
    }

    def "test ext type redefines fields in their base type"() {

        def spec = """                       

            type BaseType {
                fieldA : String
            }
            
            extend type BaseType {
                fieldB : String
                fieldA : Int
            }

            schema {
              query : BaseType
            }
        """

        def result = check(spec)

        expect:

        result.get(0).getMessage().contains("tried to redefine field 'fieldA'")
    }

    def "test ext type redefines fields in their base type with null semantics"() {

        def spec = """                       

            type BaseType {
                fieldA : String
            }
            
            extend type BaseType {
                fieldB : String
                fieldA : String!
            }

            schema {
              query : BaseType
            }
        """

        def result = check(spec)

        expect:

        result.get(0).getMessage().contains("tried to redefine field 'fieldA'")
    }

    def "test ext type redefines fields in their base type with list semantics"() {

        def spec = """                       

            type BaseType {
                fieldA : String
            }
            
            extend type BaseType {
                fieldB : String
                fieldA : [String]
            }

            schema {
              query : BaseType
            }
        """

        def result = check(spec)

        expect:

        result.get(0).getMessage().contains("tried to redefine field 'fieldA'")
    }

    def "test ext type can redefine fields in their base type of the same type"() {

        def spec = """                       

            type BaseType {
                fieldA : String
            }
            
            extend type BaseType {
                fieldB : String
                fieldA : String
            }

            schema {
              query : BaseType
            }
        """

        def result = check(spec)

        expect:

        result.isEmpty()
    }

    def "test ext type redefines fields in their peer types"() {

        def spec = """                       

            type BaseType {
                fieldA : String
            }
            
            extend type BaseType {
                fieldB : String
            }

            extend type BaseType {
                fieldB : Int
            }

            schema {
              query : BaseType
            }
        """

        def result = check(spec)

        expect:

        result.get(0).getMessage().contains("tried to redefine field 'fieldB'")
    }

    def "test ext type redefines fields in their peer types of the same type is ok"() {

        def spec = """                       

            type BaseType {
                fieldA : String
            }
            
            extend type BaseType {
                fieldB : String
            }

            extend type BaseType {
                fieldB : String
            }

            schema {
              query : BaseType
            }
        """

        def result = check(spec)

        expect:

        result.isEmpty()
    }

    def "test ext type is missing the base type"() {

        def spec = """                       

            extend type BaseType {
                fieldB : String
                fieldA : Int
            }

            schema {
              query : BaseType
            }
        """

        def result = check(spec)

        expect:

        result.get(0).getMessage().contains("is missing its base object type")
    }

    def "test object interface is missing"() {

        def spec = """                       

            type BaseType implements Missing {
                fieldA : Int
            }

            schema {
              query : BaseType
            }
        """

        def result = check(spec)

        expect:

        result.get(0).getMessage().contains("The interface type 'Missing' is not present when resolving type 'BaseType'")
    }

    def "test ext type interface is missing"() {

        def spec = """                       

            type BaseType {
                fieldA : Int
            }

            extend type BaseType implements Missing {
                fieldB : Int
            }

            schema {
              query : BaseType
            }
        """

        def result = check(spec)

        expect:

        result.get(0).getMessage().contains("The interface type 'Missing' is not present when resolving type 'BaseType'")
    }


    def "test object interface is missing because its the wrong type"() {

        def spec = """                        
            
            type IsNotAnInterface {
                field : String 
            }

            type BaseType implements IsNotAnInterface {
                fieldA : Int
            }

            schema {
              query : BaseType
            }
        """

        def result = check(spec)

        expect:

        result.get(0).getMessage().contains("The interface type 'IsNotAnInterface' is not present when resolving type 'BaseType'")
    }

    def "test object interface is all ok"() {

        def spec = """                        
            
            interface InterfaceType {
                field : String 
            }

            type BaseType implements InterfaceType {
                fieldA : Int
            }

            extend type BaseType implements InterfaceType {
                fieldB : Int
            }

            schema {
              query : BaseType
            }
        """

        def result = check(spec)

        expect:

        result.isEmpty()
    }
}
