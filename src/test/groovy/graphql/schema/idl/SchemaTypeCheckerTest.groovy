package graphql.schema.idl

import graphql.GraphQLError
import graphql.TypeResolutionEnvironment
import graphql.language.FieldDefinition
import graphql.language.InterfaceTypeDefinition
import graphql.language.UnionTypeDefinition
import graphql.schema.DataFetcher
import graphql.schema.GraphQLObjectType
import graphql.schema.TypeResolver
import graphql.schema.idl.errors.SchemaMissingError
import spock.lang.Specification

class SchemaTypeCheckerTest extends Specification {

    TypeDefinitionRegistry parse(String spec) {
        new SchemaParser().parse(spec)
    }

    def resolver = new TypeResolver() {
        @Override
        GraphQLObjectType getType(TypeResolutionEnvironment env) {
            throw new UnsupportedOperationException("Not implemented")
        }
    }

    class NamedWiringFactory implements WiringFactory {
        List<String> names

        NamedWiringFactory(List<String> names) {
            this.names = names
        }

        @Override
        boolean providesTypeResolver(TypeDefinitionRegistry registry, InterfaceTypeDefinition definition) {
            return names.contains(definition.getName())
        }

        @Override
        boolean providesTypeResolver(TypeDefinitionRegistry registry, UnionTypeDefinition definition) {
            return names.contains(definition.getName())
        }

        @Override
        TypeResolver getTypeResolver(TypeDefinitionRegistry registry, UnionTypeDefinition definition) {
            resolver
        }

        @Override
        TypeResolver getTypeResolver(TypeDefinitionRegistry registry, InterfaceTypeDefinition definition) {
            resolver
        }

        @Override
        boolean providesDataFetcher(TypeDefinitionRegistry registry, FieldDefinition definition) {
            false
        }

        @Override
        DataFetcher getDataFetcher(TypeDefinitionRegistry registry, FieldDefinition definition) {
            throw new UnsupportedOperationException("Not implemented")
        }
    }


    List<GraphQLError> check(String spec) {
        def types = parse(spec)


        NamedWiringFactory wiringFactory = new NamedWiringFactory(["InterfaceType"])

        def wiring = RuntimeWiring.newRuntimeWiring()
                .wiringFactory(wiringFactory)
                .type(TypeRuntimeWiring.newTypeWiring("InterfaceType1").typeResolver(resolver))
                .type(TypeRuntimeWiring.newTypeWiring("InterfaceType2").typeResolver(resolver))
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
            
            # no schema defined and hence we cant proceed
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

    def "test missing schema is ok with Query type"() {

        def spec = """ 
            type Query {
                id : ID!
            }
            
            # no schema defined but its named ok
        """

        def result = check(spec)

        expect:

        result.isEmpty()
    }

    def "test missing schema is not ok with standard named Mutation and Subscription types"() {

        def spec = """ 
            type Mutation {
                id : ID!
            }
            type Subscription {
                id : ID!
            }
            
            # no schema defined but its named ok
        """

        def result = check(spec)

        expect:

        result.get(0) instanceof SchemaMissingError
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

    def "test object when redefining interface field"() {

        def spec = """                        
            
            interface InterfaceType {
                fieldA : String 
            }

            type BaseType implements InterfaceType {
                fieldA : Int
            }

            schema {
              query : BaseType
            }
        """

        def result = check(spec)

        expect:

        result.get(0).getMessage().contains("has tried to redefine field 'fieldA' defined via interface 'InterfaceType'")
    }

    def "test type extension when redefining interface field"() {

        def spec = """                        
            
            interface InterfaceType {
                fieldA : String 
            }

            type BaseType {
                fieldX : Int
            }

            extend type BaseType implements InterfaceType {
                fieldA : Int
            }

            schema {
              query : BaseType
            }
        """

        def result = check(spec)

        expect:

        result.get(0).getMessage().contains("has tried to redefine field 'fieldA' defined via interface 'InterfaceType'")
    }

    def "test object when missing interface field"() {

        def spec = """                        
            
            interface InterfaceType {
                fieldA : String 
            }

            type BaseType implements InterfaceType {
                fieldX : Int
            }

            schema {
              query : BaseType
            }
        """

        def result = check(spec)

        expect:

        result.get(0).getMessage().contains("does not have a field 'fieldA' required via interface 'InterfaceType'")
    }

    def "test type extension when missing interface field"() {

        def spec = """                        
            
            interface InterfaceType {
                fieldA : String 
            }

            type BaseType {
                fieldX : Int
            }

            extend type BaseType implements InterfaceType {
                fieldY : String
            }

            schema {
              query : BaseType
            }
        """

        def result = check(spec)

        expect:

        result.get(0).getMessage().contains("does not have a field 'fieldA' required via interface 'InterfaceType'")
    }

    def "test field arguments on object must match the interface"() {
        def spec = """    
            interface InterfaceType {
                fieldA(arg1 : Int) : Int 
                fieldB(arg1 : String = "defaultVal", arg2 : String, arg3 : Int) : String 
            }

            type BaseType {
                fieldX : Int
            }

            extend type BaseType implements InterfaceType {
                fieldA : Int
                fieldB(arg1 : String = "defaultValX", arg2 : String!, arg3 : String) : String 
            }

            schema {
              query : BaseType
            }
        """

        def result = check(spec)

        expect:

        result.get(0).getMessage().contains("field 'fieldA' does not have the same number of arguments as specified via interface 'InterfaceType'")
        result.get(1).getMessage().contains("has tried to redefine field 'fieldB' arguments defined via interface 'InterfaceType'")
        result.get(2).getMessage().contains("has tried to redefine field 'fieldB' arguments defined via interface 'InterfaceType'")
        result.get(3).getMessage().contains("has tried to redefine field 'fieldB' arguments defined via interface 'InterfaceType'")

    }

    def "test field arguments on objects must match the interface"() {
        def spec = """    
            interface InterfaceType {
                fieldA(arg1 : Int) : Int 
                fieldB(arg1 : String = "defaultVal", arg2 : String, arg3 : Int) : String 
            }

            type BaseType implements InterfaceType {
                fieldA : Int
                fieldB(arg1 : String = "defaultValX", arg2 : String!, arg3 : String) : String 
            }

            schema {
              query : BaseType
            }
        """

        def result = check(spec)

        expect:

        result.get(0).getMessage().contains("field 'fieldA' does not have the same number of arguments as specified via interface 'InterfaceType'")
        result.get(1).getMessage().contains("has tried to redefine field 'fieldB' arguments defined via interface 'InterfaceType'")
        result.get(2).getMessage().contains("has tried to redefine field 'fieldB' arguments defined via interface 'InterfaceType'")
        result.get(3).getMessage().contains("has tried to redefine field 'fieldB' arguments defined via interface 'InterfaceType'")
    }

    def "test field arguments on object type extensions must match the interface"() {
        def spec = """    
            interface InterfaceType {
                fieldA(arg1 : Int) : Int 
                fieldB(arg1 : String = "defaultVal", arg2 : String, arg3 : Int) : String 
            }

            type BaseType {
                fieldX : Int
            }

            extend type BaseType implements InterfaceType {
                fieldA : Int
                fieldB(arg1 : String = "defaultValX", arg2 : String!, arg3 : String) : String 
            }

            schema {
              query : BaseType
            }
        """

        def result = check(spec)

        expect:

        result.get(0).getMessage().contains("field 'fieldA' does not have the same number of arguments as specified via interface 'InterfaceType'")
        result.get(1).getMessage().contains("has tried to redefine field 'fieldB' arguments defined via interface 'InterfaceType'")
        result.get(2).getMessage().contains("has tried to redefine field 'fieldB' arguments defined via interface 'InterfaceType'")
        result.get(3).getMessage().contains("has tried to redefine field 'fieldB' arguments defined via interface 'InterfaceType'")
    }

    def "test object interface is all ok"() {

        def spec = """                        
            
            interface InterfaceType1 {
                fieldA : String 
            }

            interface InterfaceType2 {
                fieldB : Int 
            }

            type BaseType implements InterfaceType1 {
                fieldA : String
            }

            extend type BaseType implements InterfaceType2 {
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
