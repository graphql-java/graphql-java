package graphql.schema.idl

import graphql.GraphQLError
import graphql.TypeResolutionEnvironment
import graphql.language.StringValue
import graphql.schema.Coercing
import graphql.schema.CoercingParseLiteralException
import graphql.schema.DataFetcher
import graphql.schema.GraphQLObjectType
import graphql.schema.GraphQLScalarType
import graphql.schema.TypeResolver
import graphql.schema.idl.errors.DirectiveIllegalArgumentTypeError
import graphql.schema.idl.errors.DirectiveIllegalLocationError
import graphql.schema.idl.errors.DirectiveUndeclaredError
import graphql.schema.idl.errors.MissingTypeError
import graphql.schema.idl.errors.NonUniqueNameError
import graphql.schema.idl.errors.SchemaMissingError
import spock.lang.Specification
import spock.lang.Unroll

import static graphql.schema.idl.errors.DirectiveIllegalArgumentTypeError.DUPLICATED_KEYS_MESSAGE
import static graphql.schema.idl.errors.DirectiveIllegalArgumentTypeError.EXPECTED_ENUM_MESSAGE
import static graphql.schema.idl.errors.DirectiveIllegalArgumentTypeError.EXPECTED_LIST_MESSAGE
import static graphql.schema.idl.errors.DirectiveIllegalArgumentTypeError.EXPECTED_NON_NULL_MESSAGE
import static graphql.schema.idl.errors.DirectiveIllegalArgumentTypeError.EXPECTED_OBJECT_MESSAGE
import static graphql.schema.idl.errors.DirectiveIllegalArgumentTypeError.EXPECTED_SCALAR_MESSAGE
import static graphql.schema.idl.errors.DirectiveIllegalArgumentTypeError.MISSING_REQUIRED_FIELD_MESSAGE
import static graphql.schema.idl.errors.DirectiveIllegalArgumentTypeError.MUST_BE_VALID_ENUM_VALUE_MESSAGE
import static graphql.schema.idl.errors.DirectiveIllegalArgumentTypeError.NOT_A_VALID_SCALAR_LITERAL_MESSAGE
import static graphql.schema.idl.errors.DirectiveIllegalArgumentTypeError.UNKNOWN_FIELDS_MESSAGE
import static java.lang.String.format

class SchemaTypeCheckerTest extends Specification {

    def enforceSchemaDirectives = false


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
        String name

        NamedWiringFactory(String name) {
            this.name = name
        }

        @Override
        boolean providesTypeResolver(InterfaceWiringEnvironment environment) {
            return name == environment.getInterfaceTypeDefinition().getName()
        }

        @Override
        TypeResolver getTypeResolver(InterfaceWiringEnvironment environment) {
            resolver
        }

        @Override
        boolean providesTypeResolver(UnionWiringEnvironment environment) {
            return name == environment.getUnionTypeDefinition().getName()
        }

        @Override
        TypeResolver getTypeResolver(UnionWiringEnvironment environment) {
            resolver
        }

        @Override
        boolean providesDataFetcher(FieldWiringEnvironment environment) {
            false
        }

        @Override
        DataFetcher getDataFetcher(FieldWiringEnvironment environment) {
            throw new UnsupportedOperationException("Not implemented")
        }
    }

    List<GraphQLError> check(String spec) {
        check(spec, [])
    }

    List<GraphQLError> check(String spec, List<String> resolvingNames) {
        def types = parse(spec)


        NamedWiringFactory wiringFactory = new NamedWiringFactory("InterfaceType")

        def scalesScalar = new GraphQLScalarType("Scales", "", new Coercing() {
            @Override
            Object serialize(Object dataFetcherResult) {
                return null
            }

            @Override
            Object parseValue(Object input) {
                return null
            }

            @Override
            Object parseLiteral(Object input) {
                return null
            }
        })
        def aCustomDateScalar = new GraphQLScalarType("ACustomDate", "", new Coercing() {
            @Override
            Object serialize(Object dataFetcherResult) {
                return null
            }

            @Override
            Object parseValue(Object input) {
                return null
            }

            @Override
            Object parseLiteral(Object input) {
                if (input instanceof StringValue && "AFailingDate" == input.value) {
                    throw new CoercingParseLiteralException("Failed!")
                }
                return null
            }
        })
        def runtimeBuilder = RuntimeWiring.newRuntimeWiring()
                .wiringFactory(wiringFactory)
                .scalar(scalesScalar)
                .scalar(aCustomDateScalar)
                .type(TypeRuntimeWiring.newTypeWiring("InterfaceType1").typeResolver(resolver))
                .type(TypeRuntimeWiring.newTypeWiring("InterfaceType2").typeResolver(resolver))
                .type(TypeRuntimeWiring.newTypeWiring("FooBar").typeResolver(resolver))

        for (String name : resolvingNames) {
            runtimeBuilder.type(TypeRuntimeWiring.newTypeWiring(name).typeResolver(resolver))
        }
        return new SchemaTypeChecker().checkTypeRegistry(types, runtimeBuilder.build(), enforceSchemaDirectives)
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

        result.get(0).getMessage().contains("is missing its base underlying type")
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

    def "test that field names within types are unique"() {

        def spec = """                        
            
            type Query {
                fieldA : String
                fieldA : Int
            }

            extend type Query {
                fieldB : String
                fieldB : Int
                fieldOK : Int
            }
            
            enum EnumType {
                enumA
                enumA
                enumOK
            }

            input InputType {
                inputFieldA : String
                inputFieldA : String
                inputFieldOK : String
            }
        """

        def result = check(spec)

        expect:

        !result.isEmpty()
        result.size() == 4
    }

    def "test that field args are unique"() {

        def spec = """                        
            
            type Query {
                fieldA(arg1 : Int, arg1 : String) : String
                fieldB(arg1 : Int, argOK : String) : String
            }

            extend type Query {
                fieldC(arg1 : Int, arg1 : String) : String
                fieldD(arg1 : Int, argOK : String) : String
            }
            
            interface InterfaceType1 {
                fieldX(arg1 : Int, arg1 : String) : String
                fieldY(arg1 : Int, argOK : String) : String
            }
            
            type Implementor implements InterfaceType1 {
                fieldX(arg1 : Int, arg1 : String) : String
                fieldY(arg1 : Int, argOK : String) : String
            }
        """

        def result = check(spec)

        expect:

        !result.isEmpty()
        result.size() == 4
    }


    def "test that deprecation directive is valid"() {

        def spec = """                        
            
            interface InterfaceType1 {
                fieldA : String @deprecated(badName : "must be called reason") 
            }

            type Query implements InterfaceType1 {
                fieldA : String
                fieldC : String @deprecated(reason : "it must have", one : "argument value")
            }

            extend type Query {
                fieldB : Int
                fieldD: Int @deprecated(badName : "must be called reason")
                fieldE: Int @deprecated(reason : "it must have", one : "argument value")
            }
            
            enum EnumType {
                
                enumA @deprecated(badName : "must be called reason"),
                enumB @deprecated(reason : "it must have", one : "argument value")
            }

            input InputType {
                inputFieldA : String @deprecated(badName : "must be called reason")
                inputFieldB : String @deprecated(reason : "it must have", one : "argument value")
            }
        """

        def result = check(spec)

        expect:

        !result.isEmpty()
        result.size() == 8
    }

    def "test that directives are valid"() {

        def spec = """                        
            
            interface InterfaceType1 {
                fieldA : String @directiveA @directiveA 
            }

            type Query implements InterfaceType1 {
                fieldA : String
                fieldC : String @directiveA @directiveA
            }

            extend type Query {
                fieldB : Int
                fieldD: Int @directiveA @directiveA
                fieldE: Int @directiveA @directiveOK
            }
            
            enum EnumType {
                
                enumA @directiveA @directiveA
                enumB @directiveA @directiveOK
            }

            input InputType {
                inputFieldA : String @directiveA @directiveA
                inputFieldB : String @directiveA @directiveOK
            }
        """

        def result = check(spec)

        expect:

        !result.isEmpty()
        result.size() == 5
    }

    def "test that directives args are valid"() {

        def spec = """                        
            
            interface InterfaceType1 {
                fieldA : String @directive(arg1 : 1, arg1 : 2) 
            }

            type Query implements InterfaceType1 {
                fieldA : String
                fieldC : String @directive(arg1 : 1, arg1 : 2)
            }

            extend type Query {
                fieldB : Int
                fieldD: Int @directive(arg1 : 1, arg1 : 2)
                fieldE: Int @directive(arg1 : 1, argOK : 2)
            }
            
            enum EnumType {
                
                enumA @directive(arg1 : 1, arg1 : 2)
                enumB @directive(arg1 : 1, argOK : 2)
            }

            input InputType {
                inputFieldA : String @directive(arg1 : 1, arg1 : 2)
                inputFieldB : String @directive(arg1 : 1, argOK : 2)
            }
        """

        def result = check(spec)

        expect:

        !result.isEmpty()
        result.size() == 5
    }


    def errorContaining(List<GraphQLError> errors, String partialMatch) {
        for (GraphQLError e : errors) {
            String message = e.message
            message = message.replaceAll($/\[@[0-9]+:[0-9]+]/$, '[@n:n]')
            if (message.contains(partialMatch)) {
                return true
            }
        }
        return false
    }

    def "object type extensions invariants are enforced"() {

        def spec = """                        

            type Query @directive {
                fieldA : String
            }
            
            extend type Query @directive {
                fieldB : String
            }

            extend type Query {
                fieldB : String
            }

            extend type Query {
                fieldB : Int
            }
            
            extend type Query {
                fieldC : Int
                fieldC : Int
            }

            extend type NonExistent {
                fieldX : String  
            }
            
        """

        def result = check(spec)

        expect:

        errorContaining(result, "The extension 'Query' type [@n:n] has redefined the directive called 'directive'")
        errorContaining(result, "'Query' extension type [@n:n] tried to redefine field 'fieldB' [@n:n]")
        errorContaining(result, "The type 'Query' [@n:n] has declared a field with a non unique name 'fieldC'")
        errorContaining(result, "The extension 'NonExistent' type [@n:n] is missing its base underlying type")
    }

    def "interface type extensions invariants are enforced"() {

        def spec = """                        

            type Query implements InterfaceType1 {
                fieldA : String
                fieldC : String
            }
            
            interface InterfaceType1 @directive {
                fieldA : String  
            }

            extend interface InterfaceType1 @directive {  # directive redefined
                fieldA : Int #redefined  
            }
            
            extend interface NonExistent {
                fieldX : String            
            }
            
        """

        def result = check(spec)

        expect:

        result.size() == 3
        errorContaining(result, "The extension 'NonExistent' type [@n:n] is missing its base underlying type")
        errorContaining(result, "'InterfaceType1' extension type [@n:n] tried to redefine field 'fieldA' [@n:n]")
        errorContaining(result, "The extension 'InterfaceType1' type [@n:n] has redefined the directive called 'directive'")
    }

    def "union type extensions invariants are enforced"() {

        def spec = """                        
            type Query {
                fieldA : String
            }
            
            type Foo {
                foo : String
            }

            type Bar {
                bar : String
            }

            type Baz {
                baz : String
            }

            union FooBar @directive = Foo | Bar

            extend union FooBar @directive
            
            extend union FooBar = Foo | Baz

            extend union FooBar = Foo | Foo
            
            extend union FooBar = Buzz
            
            extend union NonExistent = Foo
            
        """

        def result = check(spec)

        expect:

        result.size() == 4
        errorContaining(result, "The extension 'NonExistent' type [@n:n] is missing its base underlying type")
        errorContaining(result, "The extension 'FooBar' type [@n:n] has redefined the directive called 'directive'")
        errorContaining(result, "The union member type 'Buzz' is not present when resolving type 'FooBar' [@n:n]")
        errorContaining(result, "The type 'FooBar' [@n:n] has declared an union member with a non unique name 'Foo'")
    }

    def "enum type extensions invariants are enforced"() {

        def spec = """                        
            type Query {
                fieldA : String
            }

            enum Numb @directive {
                A
            }

            extend enum Numb @directive {
                B
            }

            extend enum Numb {
                A,C
            }

            extend enum Numb {
                D,D
            }

            extend enum NonExistent {
                E
            }
            
        """

        def result = check(spec)

        expect:

        errorContaining(result, "'Numb' extension type [@n:n] tried to redefine enum value 'A' [@n:n]")
        errorContaining(result, "The extension 'Numb' type [@n:n] has redefined the directive called 'directive'")
        errorContaining(result, "The type 'Numb' [@n:n] has declared an enum value with a non unique name 'D'")
        errorContaining(result, "The extension 'NonExistent' type [@n:n] is missing its base underlying type")
    }


    def "scalar type extensions invariants are enforced"() {

        def spec = """                        
            type Query {
                fieldA : String
            }

            scalar Scales @directive 

            extend scalar Scales @directive 

            
            extend scalar NonExistent {
                E
            }
            
        """

        def result = check(spec)

        expect:

        errorContaining(result, "The extension 'Scales' type [@n:n] has redefined the directive called 'directive'")
        errorContaining(result, "The extension 'NonExistent' type [@n:n] is missing its base underlying type")
    }

    def "input object type extensions invariants are enforced"() {

        def spec = """                        

            type Query  {
                fieldA : String
            }
            
            input Puter @directive {
                fieldA : String  
                fieldB : String  
            }

            extend input Puter @directive {
                fieldC : String  
            }
            
            extend input Puter {
                fieldB : String  
            }

            extend input Puter {
                fieldD : String  
                fieldD : Int  
            }

            extend input Puter {
                fieldE : String  
            }

            extend input Puter {
                fieldE : Int  
            }

            extend input NonExistent {
                fieldX : String  
            }
            
        """

        def result = check(spec)

        expect:

        errorContaining(result, "The extension 'Puter' type [@n:n] has redefined the directive called 'directive'")
        errorContaining(result, "The type 'Puter' [@n:n] has declared an input field with a non unique name 'fieldD'")
        errorContaining(result, "'Puter' extension type [@n:n] tried to redefine field 'fieldE' [@n:n]")
        errorContaining(result, "The extension 'NonExistent' type [@n:n] is missing its base underlying type")
    }

    def "covariant object types are supported"() {

        def spec = '''
            type Query {
              planets: PlanetsConnection
            }
            
            type PlanetsConnection implements UnpaginatedConnection {
              edges: [PlanetEdge]
            }
            
            type PlanetEdge implements Edge {
              vertex: Planet!
            }
            
            type Planet implements Vertex {
              id: String!
              name: String!
            }
            
            interface UnpaginatedConnection {
              edges: [Edge]
            }
            
            interface Edge {
              vertex: Vertex!
            }
            
            interface Vertex {
              id: String!
            }
        '''

        def result = check(spec, ["UnpaginatedConnection", "Edge", "Vertex"])

        expect:

        result.isEmpty()

    }

    def "deviant covariant object types are detected"() {

        def spec = '''
            type Query {
              planets: PlanetsConnection
            }
            
            type PlanetsConnection implements UnpaginatedConnection {
              edges: PlanetEdge
            }
            
            type PlanetEdge implements Edge {
              vertex: Planet
            }
            
            type Planet implements Vertex {
              id: String!
              name: String!
            }
            
            interface UnpaginatedConnection {
              edges: [Edge]!
            }
            
            interface Edge {
              vertex: Vertex!
            }
            
            interface Vertex {
              id: String!
            }
        '''

        def result = check(spec, ["UnpaginatedConnection", "Edge", "Vertex"])

        expect:

        errorContaining(result, "The object type 'PlanetsConnection' [@n:n] has tried to redefine field 'edges' defined via interface 'UnpaginatedConnection' [@n:n] from '[Edge]!' to 'PlanetEdge'")
        errorContaining(result, "The object type 'PlanetsConnection' [@n:n] has tried to redefine field 'edges' defined via interface 'UnpaginatedConnection' [@n:n] from '[Edge]!' to 'PlanetEdge'")
    }

    def "directive definition bad location"() {
        def spec = """
            directive @badDirective on UNKNOWN 
                            
            type Query {
                f : String
            }
        """

        enforceSchemaDirectives = true
        def result = check(spec)

        expect:

        result.get(0) instanceof DirectiveIllegalLocationError
    }

    def "directive definition non unique arg name"() {
        def spec = """
            directive @badDirective(arg1 : String, arg1 : String) on OBJECT 
                            
            type Query {
                f : String
            }
        """

        enforceSchemaDirectives = true
        def result = check(spec)

        expect:

        result.get(0) instanceof NonUniqueNameError
    }

    def "directive definition unknown arg type"() {
        def spec = """
            directive @badDirective(arg1 : UnknownType, arg2 : String) on OBJECT 
                            
            type Query {
                f : String
            }
        """

        enforceSchemaDirectives = true
        def result = check(spec)

        expect:

        result.get(0) instanceof MissingTypeError
    }

    def "undeclared directive definition will be caught"() {
        def spec = """
            directive @testDirective(knownArg : String = "defaultValue") on SCHEMA | SCALAR | 
                            OBJECT | FIELD_DEFINITION |
                            ARGUMENT_DEFINITION | INTERFACE | UNION | 
                            ENUM | ENUM_VALUE | 
                            INPUT_OBJECT | INPUT_FIELD_DEFINITION

            type Query {
                f : String @testDirectiveNotDeclared
            }
        """

        enforceSchemaDirectives = true
        def result = check(spec)

        expect:

        result.get(0) instanceof DirectiveUndeclaredError
    }

    def "directive definition can be valid"() {
        def spec = """
            directive @testDirective(knownArg : String = "defaultValue") on SCHEMA | SCALAR | 
                            OBJECT | FIELD_DEFINITION |
                            ARGUMENT_DEFINITION | INTERFACE | UNION | 
                            ENUM | ENUM_VALUE | 
                            INPUT_OBJECT | INPUT_FIELD_DEFINITION

            type Query {
                f : String @testDirective
            }
        """

        enforceSchemaDirectives = true
        def result = check(spec)

        expect:

        result.isEmpty()
    }

    @Unroll
    def "directive definition allowed argument type '#allowedArgType' does not match argument value '#argValue'"() {
        def spec = """
            directive @testDirective(knownArg : $allowedArgType) on FIELD_DEFINITION

            type Query {
                f : String @testDirective(knownArg: $argValue)
            }
            
            scalar ACustomDate
            
            enum WEEKDAY {
                MONDAY
                TUESDAY
            }
            
            input UserInput {
                field: String
                fieldNonNull: String!
                fieldWithDefault: String = "default"
                # not sure if below makes sense
                fieldNonNullWithDefault: String! = "default"
                fieldArray: [String]
                fieldArrayOfArray: [[String]]
                fieldNestedInput: AddressInput
            }
            
            input AddressInput {
                street: String
            }

        """

        enforceSchemaDirectives = true
        def result = check(spec)

        expect:

        !result.empty
        result.get(0) instanceof DirectiveIllegalArgumentTypeError
        errorContaining(result, "'f' [@n:n] uses an illegal value for the argument 'knownArg' on directive 'testDirective'. $detailedMessage")

        where:

        allowedArgType | argValue                                                                               | detailedMessage
        "String"       | 'MONDAY'                                                                               | format(EXPECTED_SCALAR_MESSAGE, "EnumValue")
        "String"       | '{ an: "object" }'                                                                     | format(EXPECTED_SCALAR_MESSAGE, "ObjectValue")
        "String"       | '["str", "str2"]'                                                                      | format(EXPECTED_SCALAR_MESSAGE, "ArrayValue")
        "ACustomDate"  | '"AFailingDate"'                                                                       | format(NOT_A_VALID_SCALAR_LITERAL_MESSAGE, "ACustomDate")
        "[String]"     | '"str"'                                                                                | format(EXPECTED_LIST_MESSAGE, "StringValue")
        "[String]!"    | '"str"'                                                                                | format(EXPECTED_LIST_MESSAGE, "StringValue")
        "[String!]"    | '["str", null]'                                                                        | format(EXPECTED_NON_NULL_MESSAGE)
        "[[String!]!]" | '[["str"], ["str2", null]]'                                                            | format(EXPECTED_NON_NULL_MESSAGE)
        "WEEKDAY"      | '"somestr"'                                                                            | format(EXPECTED_ENUM_MESSAGE, "StringValue")
        "WEEKDAY"      | 'SATURDAY'                                                                             | format(MUST_BE_VALID_ENUM_VALUE_MESSAGE, "SATURDAY", "MONDAY,TUESDAY")
        "UserInput"    | '{ fieldNonNull: "str", fieldNonNull: "dupeKey" }'                                     | format(DUPLICATED_KEYS_MESSAGE, "fieldNonNull")
        "UserInput"    | '{ fieldNonNull: "str", unknown: "field" }'                                            | format(UNKNOWN_FIELDS_MESSAGE, "unknown", "UserInput")
        "UserInput"    | '{ fieldNonNull: "str", fieldArray: "strInsteadOfArray" }'                             | format(EXPECTED_LIST_MESSAGE, "StringValue")
        "UserInput"    | '{ fieldNonNull: "str", fieldArrayOfArray: ["ArrayInsteadOfArrayOfArray"] }'           | format(EXPECTED_LIST_MESSAGE, "StringValue")
        "UserInput"    | '{ fieldNonNull: "str", fieldNestedInput: "strInsteadOfObject" }'                      | format(EXPECTED_OBJECT_MESSAGE, "StringValue")
        "UserInput"    | '{ fieldNonNull: "str", fieldNestedInput: { street: { s: "objectInsteadOfString" }} }' | format(EXPECTED_SCALAR_MESSAGE, "ObjectValue")
        "UserInput"    | '{ field: "missing the `fieldNonNull` entry"}'                                         | format(MISSING_REQUIRED_FIELD_MESSAGE, "fieldNonNull")
    }

    @Unroll
    def "directive definition allowed argument type '#allowedArgType' matches argument value '#argValue'"() {
        def spec = """
            directive @testDirective(knownArg : $allowedArgType) on FIELD_DEFINITION

            type Query {
                f : String @testDirective(knownArg: $argValue)
            }
            
            scalar ACustomDate
           
            enum WEEKDAY {
                MONDAY
            }
            
            input UserInput {
                fieldString: String
                fieldNonNull: String!
                fieldWithDefault: String = "default"
                fieldArray: [String]
                fieldArrayOfArray: [[String]]
                fieldNestedInput: AddressInput
            }
            
            input AddressInput {
                street: String
            }
        """

        enforceSchemaDirectives = true
        def result = check(spec)

        expect:

        result.empty

        where:

        allowedArgType | argValue
        "String"       | '"str"'
        "Boolean"      | 'false'
        "String"       | 'null'
        "ACustomDate"  | '"TwoThousand-June-Six"'
        "ACustomDate"  | '2002'
        "[String]"     | '["str", null]'
        "[String]"     | 'null'
        "[String!]!"   | '["str"]'
        "[[String!]!]" | '[["str"], ["str2", "str3"]]'
        "WEEKDAY"      | 'MONDAY'
        "UserInput"    | '{ fieldNonNull: "str" }'
        "UserInput"    | '{ fieldNonNull: "str", fieldString: "Hey" }'
        "UserInput"    | '{ fieldNonNull: "str", fieldWithDefault: "notDefault" }'
        "UserInput"    | '{ fieldNonNull: "str", fieldArray: ["Hey", "Low"] }'
        "UserInput"    | '{ fieldNonNull: "str", fieldArrayOfArray: [["Hey"], ["Low"]] }'
        "UserInput"    | '{ fieldNonNull: "str", fieldNestedInput: { street: "nestedStr"} }'
    }

}
