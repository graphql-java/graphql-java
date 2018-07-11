package graphql.schema.idl

import graphql.language.InterfaceTypeDefinition
import graphql.language.ListType
import graphql.language.NonNullType
import graphql.language.ObjectTypeDefinition
import graphql.language.SchemaDefinition
import graphql.language.Type
import graphql.language.TypeName
import graphql.schema.idl.errors.SchemaProblem
import graphql.schema.idl.errors.SchemaRedefinitionError
import spock.lang.Specification

class TypeDefinitionRegistryTest extends Specification {

    TypeDefinitionRegistry parse(String spec) {
        new SchemaParser().parse(spec)
    }

    def "test default scalars are locked in"() {

        def registry = new TypeDefinitionRegistry()

        def scalars = registry.scalars()

        expect:

        scalars.containsKey("Int")
        scalars.containsKey("Float")
        scalars.containsKey("String")
        scalars.containsKey("Boolean")
        scalars.containsKey("ID")

        // graphql-java library extensions
        scalars.containsKey("Long")
        scalars.containsKey("BigInteger")
        scalars.containsKey("BigDecimal")
        scalars.containsKey("Short")
        scalars.containsKey("Char")

    }

    def "adding 2 schemas is not allowed"() {
        def registry = new TypeDefinitionRegistry()
        def result1 = registry.add(SchemaDefinition.newSchemaDefintion().build())
        def result2 = registry.add(SchemaDefinition.newSchemaDefintion().build())

        expect:
        !result1.isPresent()
        result2.get() instanceof SchemaRedefinitionError
    }


    def "merging multiple type registries does not overwrite schema definition"() {

        def spec1 = """ 
            schema {
                query: Query
            }
        """

        def spec2 = """ 
            type Post { id: Int! }
        """

        def result1 = parse(spec1)
        def result2 = parse(spec2)

        def registry = result1.merge(result2)

        expect:
        result1.schemaDefinition().isPresent()
        registry.schemaDefinition().get().isEqualTo(result1.schemaDefinition().get())

    }

    def "test merge of schema types"() {

        def spec1 = """ 
            schema {
                query: Query
                mutation: Mutation
            }
        """

        def spec2 = """ 
            schema {
                query: Query2
                mutation: Mutation2
            }
        """

        def result1 = parse(spec1)
        def result2 = parse(spec2)

        when:
        result1.merge(result2)

        then:
        SchemaProblem e = thrown(SchemaProblem)
        e.getErrors().get(0) instanceof SchemaRedefinitionError
    }

    def "test merge of object types"() {

        def spec1 = """ 
          type Post {
              id: Int!
              title: String
              votes: Int
            }

        """

        def spec2 = """ 
          type Post {
              id: Int!
            }

        """

        def result1 = parse(spec1)
        def result2 = parse(spec2)

        when:
        result1.merge(result2)

        then:

        SchemaProblem e = thrown(SchemaProblem)
        e.getErrors().get(0).getMessage().contains("tried to redefine existing 'Post'")
    }


    def "test merge of scalar types"() {

        def spec1 = """ 
          type Post {
              id: Int!
              title: String
              votes: Int
            }
            
            scalar Url

        """

        def spec2 = """ 
         
         scalar UrlX
         
         scalar Url
         

        """

        def result1 = parse(spec1)
        def result2 = parse(spec2)

        when:
        result1.merge(result2)

        then:

        SchemaProblem e = thrown(SchemaProblem)
        e.getErrors().get(0).getMessage().contains("tried to redefine existing 'Url'")
    }

    def "test merge of directive defs"() {

        def spec1 = """ 

          directive @example on FIELD_DEFINITION | ARGUMENT_DEFINITION

          type Post {
              id: Int!
            }
            
        """

        def spec2 = """ 
         
         type Post2 {
            id : Int
        }
         
          directive @example on FIELD_DEFINITION | ARGUMENT_DEFINITION

        """

        def result1 = parse(spec1)
        def result2 = parse(spec2)

        when:
        result1.merge(result2)

        then:

        SchemaProblem e = thrown(SchemaProblem)
        e.getErrors().get(0).getMessage().contains("tried to redefine existing directive 'example'")
    }

    def "test successful merge of types"() {

        def spec1 = """ 

          directive @example on FIELD_DEFINITION | ARGUMENT_DEFINITION

          type Post {
              id: Int!
              title: String
              votes: Int
            }

            extend type Post {
                placeOfPost : String
            }

        """

        def spec2 = """

          directive @anotherExample on FIELD_DEFINITION | ARGUMENT_DEFINITION
 
          type Author {
              id: Int!
              name: String
              title : String
              posts : [Post]
            }
            
            extend type Post {
                timeOfPost : Int
            }

        """

        def result1 = parse(spec1)
        def result2 = parse(spec2)

        when:

        result1.merge(result2)

        then:

        noExceptionThrown()

        def post = result1.types().get("Post")
        def author = result1.types().get("Author")

        post.name == "Post"
        post.getChildren().size() == 3

        author.name == "Author"
        author.getChildren().size() == 4

        def typeExtensions = result1.objectTypeExtensions().get("Post")
        typeExtensions.size() == 2

        result1.getDirectiveDefinitions().get("example") != null
        result1.getDirectiveDefinitions().get("anotherExample") != null
    }

    def commonSpec = '''

            type Type {
                name : String
            }

            type Type2 {
                name : String
            }
            
            type Type3 {
                name : String
            }

            interface Interface {
                name : String
            }
            
            union Union = Foo | Bar
            
            scalar Scalar
                

        '''

    private static TypeName type(String name) {
        new TypeName(name)
    }

    private static Type nonNullType(Type type) {
        new NonNullType(type)
    }

    private static Type nonNullType(String name) {
        new NonNullType(new TypeName(name))
    }

    private static Type listType(String name) {
        new ListType(new TypeName(name))
    }

    private static Type listType(Type type) {
        new ListType(type)
    }

    def "test abstract type detection"() {

        when:
        def registry = parse(commonSpec)

        then:
        registry.isInterfaceOrUnion(type("Interface"))
        registry.isInterfaceOrUnion(type("Union"))
        !registry.isInterfaceOrUnion(type("Type"))
        !registry.isInterfaceOrUnion(type("Scalar"))
    }

    def "test object type detection"() {

        when:
        def registry = parse(commonSpec)

        then:
        registry.isObjectType(type("Type"))
        !registry.isObjectType(type("Interface"))
        !registry.isObjectType(type("Union"))
        !registry.isObjectType(type("Scalar"))
    }

    def "test can get list of type definitions"() {
        when:
        def registry = parse(commonSpec)
        def objectTypeDefinitions = registry.getTypes(ObjectTypeDefinition.class)
        def names = objectTypeDefinitions.collect { it.getName() }
        then:
        names == ["Type", "Type2", "Type3"]
    }

    def "test can get map of type definitions"() {
        when:
        def registry = parse(commonSpec)
        def objectTypeDefinitions = registry.getTypesMap(ObjectTypeDefinition.class)
        then:
        objectTypeDefinitions.size() == 3
        objectTypeDefinitions.containsKey("Type")
        objectTypeDefinitions.containsKey("Type2")
        objectTypeDefinitions.containsKey("Type3")
    }


    def "test can get implements of interface"() {
        def spec = '''
            interface Interface {
                name : String
            }
            
            type Type1 implements Interface {
                name : String
            }

            type Type2 implements Interface {
                name : String
            }

            type Type3 implements Interface {
                name : String
            }

            type Type4 implements NotThatInterface {
                name : String
            }
        '''
        when:
        def registry = parse(spec)
        def interfaceDef = registry.getType("Interface", InterfaceTypeDefinition.class).get()
        def objectTypeDefinitions = registry.getImplementationsOf(interfaceDef)
        def names = objectTypeDefinitions.collect { it.getName() }
        then:
        names == ["Type1", "Type2", "Type3"]
    }

    def animalia = '''

            interface Animal {
              id: String!
            }

            interface Mammal {
              id: String!
            }

            interface Reptile {
              id: String!
            }

            type Dog implements Animal, Mammal {
              id: String!
            }

            type Duck implements Animal, Mammal {
              id: String!
            }
            
            union Platypus = Duck | Turtle

            type Cat implements Animal, Mammal {
              id: String!
            }

            type Turtle implements Animal, Reptile {
              id: String!
            }


        '''

    def "test possible type detection"() {
        when:
        def registry = parse(animalia)

        then:
        registry.isPossibleType(type("Mammal"), type("Dog"))
        registry.isPossibleType(type("Mammal"), type("Cat"))
        !registry.isPossibleType(type("Mammal"), type("Turtle"))

        !registry.isPossibleType(type("Reptile"), type("Dog"))
        !registry.isPossibleType(type("Reptile"), type("Cat"))
        registry.isPossibleType(type("Reptile"), type("Turtle"))

        registry.isPossibleType(type("Animal"), type("Dog"))
        registry.isPossibleType(type("Animal"), type("Cat"))
        registry.isPossibleType(type("Animal"), type("Turtle"))

        registry.isPossibleType(type("Platypus"), type("Duck"))
        registry.isPossibleType(type("Platypus"), type("Turtle"))
        !registry.isPossibleType(type("Platypus"), type("Dog"))
        !registry.isPossibleType(type("Platypus"), type("Cat"))

    }


    def "isSubTypeOf detection"() {
        when:
        def registry = parse(animalia)

        then:
        registry.isSubTypeOf(type("Mammal"), type("Mammal"))
        registry.isSubTypeOf(type("Dog"), type("Mammal"))

        registry.isSubTypeOf(type("Turtle"), type("Animal"))
        !registry.isSubTypeOf(type("Turtle"), type("Mammal"))

        registry.isSubTypeOf(nonNullType("Dog"), type("Mammal"))
        !registry.isSubTypeOf(type("Dog"), nonNullType("Mammal")) // but not the other way around

        registry.isSubTypeOf(listType("Mammal"), listType("Mammal"))
        !registry.isSubTypeOf(listType("Mammal"), type("Mammal")) // but not if they aren't both lists

        // unwraps all the way down
        registry.isSubTypeOf(listType(nonNullType(listType(type("Dog")))), listType(nonNullType(listType(type("Mammal")))))
        !registry.isSubTypeOf(listType(nonNullType(listType(type("Turtle")))), listType(nonNullType(listType(type("Mammal")))))

    }
}
