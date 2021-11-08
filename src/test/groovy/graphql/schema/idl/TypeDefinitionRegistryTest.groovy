package graphql.schema.idl

import graphql.language.DirectiveDefinition
import graphql.language.EnumTypeDefinition
import graphql.language.EnumTypeExtensionDefinition
import graphql.language.InputObjectTypeDefinition
import graphql.language.InputObjectTypeExtensionDefinition
import graphql.language.InterfaceTypeDefinition
import graphql.language.InterfaceTypeExtensionDefinition
import graphql.language.ListType
import graphql.language.NonNullType
import graphql.language.ObjectTypeDefinition
import graphql.language.ObjectTypeExtensionDefinition
import graphql.language.ScalarTypeDefinition
import graphql.language.ScalarTypeExtensionDefinition
import graphql.language.SchemaDefinition
import graphql.language.Type
import graphql.language.TypeDefinition
import graphql.language.TypeName
import graphql.language.UnionTypeDefinition
import graphql.language.UnionTypeExtensionDefinition
import graphql.parser.MultiSourceReader
import graphql.schema.GraphQLTypeUtil
import graphql.schema.idl.errors.SchemaProblem
import graphql.schema.idl.errors.SchemaRedefinitionError
import spock.lang.Specification
import spock.lang.Unroll

import java.util.stream.Collectors

class TypeDefinitionRegistryTest extends Specification {

    static TypeDefinitionRegistry parse(String spec) {
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

    }

    def "adding 2 schemas is not allowed"() {
        def registry = new TypeDefinitionRegistry()
        def result1 = registry.add(SchemaDefinition.newSchemaDefinition().build())
        def result2 = registry.add(SchemaDefinition.newSchemaDefinition().build())

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

    def "test object type or interface detection"() {

        when:
        def registry = parse(commonSpec)

        then:
        registry.isObjectTypeOrInterface(type("Type"))
        registry.isObjectTypeOrInterface(type("Interface"))
        !registry.isObjectTypeOrInterface(type("Union"))
        !registry.isObjectTypeOrInterface(type("Scalar"))
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

            interface Type5 implements Interface {
                name : String
            }

            interface Type6 implements NotThatInterface {
                name : String
            }
        '''
        when:
        def registry = parse(spec)
        def interfaceDef = registry.getType("Interface", InterfaceTypeDefinition.class).get()
        def implementingTypeDefinitions = registry.getAllImplementationsOf(interfaceDef)
        def names = implementingTypeDefinitions.collect { it.getName() }
        then:
        names == ["Type1", "Type2", "Type3", "Type5"]
    }

    def animalia = '''

            interface Animal {
              id: String!
            }

            interface Mammal {
              id: String!
              mother: Mammal!
              offspring: [Mammal!]!
            }

            interface Reptile {
              id: String!
            }

            interface Canine implements Animal & Mammal {
              id: String!
              mother: Canine!
              offspring: [Canine!]!
            }

            type Dog implements Animal & Mammal & Canine {
              id: String!
              mother: Dog!
              offspring: [Dog!]!
            }

            type Duck implements Animal {
              id: String!
            }

            union Platypus = Duck | Turtle

            type Cat implements Animal & Mammal {
              id: String!
              mother: Cat!
              offspring: [Cat!]!
            }

            type Turtle implements Animal & Reptile {
              id: String!
            }


        '''

    def "test possible type detection"() {
        when:
        def registry = parse(animalia)

        then:
        registry.isPossibleType(type("Mammal"), type("Canine"))
        registry.isPossibleType(type("Mammal"), type("Dog"))
        registry.isPossibleType(type("Mammal"), type("Cat"))
        !registry.isPossibleType(type("Mammal"), type("Turtle"))

        !registry.isPossibleType(type("Reptile"), type("Dog"))
        !registry.isPossibleType(type("Reptile"), type("Cat"))
        registry.isPossibleType(type("Reptile"), type("Turtle"))

        registry.isPossibleType(type("Animal"), type("Canine"))
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
        registry.isSubTypeOf(type("Canine"), type("Animal"))
        registry.isSubTypeOf(type("Canine"), type("Mammal"))
        registry.isSubTypeOf(type("Canine"), type("Canine"))
        registry.isSubTypeOf(type("Dog"), type("Animal"))
        registry.isSubTypeOf(type("Dog"), type("Mammal"))
        registry.isSubTypeOf(type("Dog"), type("Canine"))

        registry.isSubTypeOf(type("Turtle"), type("Animal"))
        !registry.isSubTypeOf(type("Turtle"), type("Mammal"))

        registry.isSubTypeOf(nonNullType("Dog"), type("Mammal"))
        !registry.isSubTypeOf(type("Dog"), nonNullType("Mammal")) // but not the other way around

        registry.isSubTypeOf(listType("Mammal"), listType("Mammal"))
        !registry.isSubTypeOf(listType("Mammal"), type("Mammal")) // but not if they aren't both lists
        registry.isSubTypeOf(listType("Canine"), listType("Mammal"))
        registry.isSubTypeOf(listType("Canine"), listType("Animal"))

        // unwraps all the way down
        registry.isSubTypeOf(listType(nonNullType(listType(type("Dog")))), listType(nonNullType(listType(type("Mammal")))))
        registry.isSubTypeOf(listType(nonNullType(listType(type("Canine")))), listType(nonNullType(listType(type("Mammal")))))
        !registry.isSubTypeOf(listType(nonNullType(listType(type("Turtle")))), listType(nonNullType(listType(type("Mammal")))))

    }

    @Unroll
    def "remove a definition"() {
        given:
        def registry = new TypeDefinitionRegistry()
        registry.add(definition)
        when:
        registry.remove(definition)
        then:
        !registry.getType(definition.getName()).isPresent()

        where:
        definition                                                               | _
        ObjectTypeDefinition.newObjectTypeDefinition().name("foo").build()       | _
        InterfaceTypeDefinition.newInterfaceTypeDefinition().name("foo").build() | _
        UnionTypeDefinition.newUnionTypeDefinition().name("foo").build()         | _
        EnumTypeDefinition.newEnumTypeDefinition().name("foo").build()           | _
        ScalarTypeDefinition.newScalarTypeDefinition().name("foo").build()       | _
        InputObjectTypeDefinition.newInputObjectDefinition().name("foo").build() | _
    }

    def "remove single directive definition from list"() {
        given:
        DirectiveDefinition definition = DirectiveDefinition.newDirectiveDefinition().name("foo").build()
        def registry = new TypeDefinitionRegistry()
        registry.add(definition)
        when:
        registry.remove(definition)
        then:
        !registry.getDirectiveDefinition(definition.getName()).isPresent()
    }

    def "remove multiple directive definition from list"() {
        given:
        DirectiveDefinition definition1 = DirectiveDefinition.newDirectiveDefinition().name("foo").build()
        DirectiveDefinition definition2 = DirectiveDefinition.newDirectiveDefinition().name("bar").build()
        def registry = new TypeDefinitionRegistry()
        registry.add(definition1)
        registry.add(definition2)
        when:
        registry.remove(definition1)
        then:
        !registry.getDirectiveDefinition(definition1.getName()).isPresent()
        registry.getDirectiveDefinition(definition2.getName()).isPresent()
    }

    def "remove single directive definition from map"() {
        given:
        DirectiveDefinition definition = DirectiveDefinition.newDirectiveDefinition().name("foo").build()
        def registry = new TypeDefinitionRegistry()
        registry.add(definition)
        when:
        registry.remove(definition.getName(), definition)
        then:
        !registry.getDirectiveDefinition(definition.getName()).isPresent()
    }

    def "remove multiple directive definition from map"() {
        given:
        DirectiveDefinition definition1 = DirectiveDefinition.newDirectiveDefinition().name("foo").build()
        DirectiveDefinition definition2 = DirectiveDefinition.newDirectiveDefinition().name("bar").build()
        def registry = new TypeDefinitionRegistry()
        registry.add(definition1)
        registry.add(definition2)
        when:
        registry.remove(definition1.getName(), definition1)
        then:
        !registry.getDirectiveDefinition(definition1.getName()).isPresent()
        registry.getDirectiveDefinition(definition2.getName()).isPresent()
    }


    def "remove single object type extension from list"() {
        given:
        def extension = ObjectTypeExtensionDefinition.newObjectTypeExtensionDefinition().name("foo").build()
        def registry = new TypeDefinitionRegistry()
        registry.add(extension)
        when:
        registry.remove(extension)
        then:
        !registry.objectTypeExtensions().containsKey(extension.getName())
    }

    def "remove multiple object type extension from list"() {
        given:
        def extension1 = ObjectTypeExtensionDefinition.newObjectTypeExtensionDefinition().name("foo").build()
        def extension2 = ObjectTypeExtensionDefinition.newObjectTypeExtensionDefinition().name("bar").build()
        def registry = new TypeDefinitionRegistry()
        registry.add(extension1)
        registry.add(extension2)
        when:
        registry.remove(extension1)
        then:
        !registry.objectTypeExtensions().containsKey(extension1.getName())
        registry.objectTypeExtensions().containsKey(extension2.getName())
        registry.objectTypeExtensions().get(extension2.getName()).contains(extension2)
    }

    def "remove single object type extension from map"() {
        given:
        def extension = ObjectTypeExtensionDefinition.newObjectTypeExtensionDefinition().name("foo").build()
        def registry = new TypeDefinitionRegistry()
        registry.add(extension)
        when:
        registry.remove(extension.getName(), extension)
        then:
        !registry.objectTypeExtensions().containsKey(extension.getName())
    }

    def "remove multiple object type extension from map"() {
        given:
        def extension1 = ObjectTypeExtensionDefinition.newObjectTypeExtensionDefinition().name("foo").build()
        def extension2 = ObjectTypeExtensionDefinition.newObjectTypeExtensionDefinition().name("bar").build()
        def registry = new TypeDefinitionRegistry()
        registry.add(extension1)
        registry.add(extension2)
        when:
        registry.remove(extension1.getName(), extension1)
        then:
        !registry.objectTypeExtensions().containsKey(extension1.getName())
        registry.objectTypeExtensions().containsKey(extension2.getName())
        registry.objectTypeExtensions().get(extension2.getName()).contains(extension2)
    }

    def "remove single interface type extension from list"() {
        given:
        def extension = InterfaceTypeExtensionDefinition.newInterfaceTypeExtensionDefinition().name("foo").build()
        def registry = new TypeDefinitionRegistry()
        registry.add(extension)
        when:
        registry.remove(extension)
        then:
        !registry.interfaceTypeExtensions().containsKey(extension.getName())
    }

    def "remove multiple interface type extension from list"() {
        given:
        def extension1 = InterfaceTypeExtensionDefinition.newInterfaceTypeExtensionDefinition().name("foo").build()
        def extension2 = InterfaceTypeExtensionDefinition.newInterfaceTypeExtensionDefinition().name("bar").build()
        def registry = new TypeDefinitionRegistry()
        registry.add(extension1)
        registry.add(extension2)
        when:
        registry.remove(extension1)
        then:
        !registry.interfaceTypeExtensions().containsKey(extension1.getName())
        registry.interfaceTypeExtensions().containsKey(extension2.getName())
        registry.interfaceTypeExtensions().get(extension2.getName()).contains(extension2)
    }

    def "remove single interface type extension from map"() {
        given:
        def extension = InterfaceTypeExtensionDefinition.newInterfaceTypeExtensionDefinition().name("foo").build()
        def registry = new TypeDefinitionRegistry()
        registry.add(extension)
        when:
        registry.remove(extension.getName(), extension)
        then:
        !registry.interfaceTypeExtensions().containsKey(extension.getName())
    }

    def "remove multiple interface type extension from map"() {
        given:
        def extension1 = InterfaceTypeExtensionDefinition.newInterfaceTypeExtensionDefinition().name("foo").build()
        def extension2 = InterfaceTypeExtensionDefinition.newInterfaceTypeExtensionDefinition().name("bar").build()
        def registry = new TypeDefinitionRegistry()
        registry.add(extension1)
        registry.add(extension2)
        when:
        registry.remove(extension1.getName(), extension1)
        then:
        !registry.interfaceTypeExtensions().containsKey(extension1.getName())
        registry.interfaceTypeExtensions().containsKey(extension2.getName())
        registry.interfaceTypeExtensions().get(extension2.getName()).contains(extension2)
    }

    def "remove single union type extension from list"() {
        given:
        def extension = UnionTypeExtensionDefinition.newUnionTypeExtensionDefinition().name("foo").build()
        def registry = new TypeDefinitionRegistry()
        registry.add(extension)
        when:
        registry.remove(extension)
        then:
        !registry.unionTypeExtensions().containsKey(extension.getName())
    }

    def "remove multiple union type extension from list"() {
        given:
        def extension1 = UnionTypeExtensionDefinition.newUnionTypeExtensionDefinition().name("foo").build()
        def extension2 = UnionTypeExtensionDefinition.newUnionTypeExtensionDefinition().name("bar").build()
        def registry = new TypeDefinitionRegistry()
        registry.add(extension1)
        registry.add(extension2)
        when:
        registry.remove(extension1)
        then:
        !registry.unionTypeExtensions().containsKey(extension1.getName())
        registry.unionTypeExtensions().containsKey(extension2.getName())
        registry.unionTypeExtensions().get(extension2.getName()).contains(extension2)
    }

    def "remove single union type extension from map"() {
        given:
        def extension = UnionTypeExtensionDefinition.newUnionTypeExtensionDefinition().name("foo").build()
        def registry = new TypeDefinitionRegistry()
        registry.add(extension)
        when:
        registry.remove(extension.getName(), extension)
        then:
        !registry.unionTypeExtensions().containsKey(extension.getName())
    }

    def "remove multiple union type extension from map"() {
        given:
        def extension1 = UnionTypeExtensionDefinition.newUnionTypeExtensionDefinition().name("foo").build()
        def extension2 = UnionTypeExtensionDefinition.newUnionTypeExtensionDefinition().name("bar").build()
        def registry = new TypeDefinitionRegistry()
        registry.add(extension1)
        registry.add(extension2)
        when:
        registry.remove(extension1.getName(), extension1)
        then:
        !registry.unionTypeExtensions().containsKey(extension1.getName())
        registry.unionTypeExtensions().containsKey(extension2.getName())
        registry.unionTypeExtensions().get(extension2.getName()).contains(extension2)
    }

    def "remove single enum type extension from list"() {
        given:
        def extension = EnumTypeExtensionDefinition.newEnumTypeExtensionDefinition().name("foo").build()
        def registry = new TypeDefinitionRegistry()
        registry.add(extension)
        when:
        registry.remove(extension)
        then:
        !registry.enumTypeExtensions().containsKey(extension.getName())
    }

    def "remove multiple enum type extension from list"() {
        given:
        def extension1 = EnumTypeExtensionDefinition.newEnumTypeExtensionDefinition().name("foo").build()
        def extension2 = EnumTypeExtensionDefinition.newEnumTypeExtensionDefinition().name("bar").build()
        def registry = new TypeDefinitionRegistry()
        registry.add(extension1)
        registry.add(extension2)
        when:
        registry.remove(extension1)
        then:
        !registry.enumTypeExtensions().containsKey(extension1.getName())
        registry.enumTypeExtensions().containsKey(extension2.getName())
        registry.enumTypeExtensions().get(extension2.getName()).contains(extension2)
    }

    def "remove single enum type extension from map"() {
        given:
        def extension = EnumTypeExtensionDefinition.newEnumTypeExtensionDefinition().name("foo").build()
        def registry = new TypeDefinitionRegistry()
        registry.add(extension)
        when:
        registry.remove(extension.getName(), extension)
        then:
        !registry.enumTypeExtensions().containsKey(extension.getName())
    }

    def "remove multiple enum type extension from map"() {
        given:
        def extension1 = EnumTypeExtensionDefinition.newEnumTypeExtensionDefinition().name("foo").build()
        def extension2 = EnumTypeExtensionDefinition.newEnumTypeExtensionDefinition().name("bar").build()
        def registry = new TypeDefinitionRegistry()
        registry.add(extension1)
        registry.add(extension2)
        when:
        registry.remove(extension1.getName(), extension1)
        then:
        !registry.enumTypeExtensions().containsKey(extension1.getName())
        registry.enumTypeExtensions().containsKey(extension2.getName())
        registry.enumTypeExtensions().get(extension2.getName()).contains(extension2)
    }

    def "remove single scalar type extension from list"() {
        given:
        def extension = ScalarTypeExtensionDefinition.newScalarTypeExtensionDefinition().name("foo").build()
        def registry = new TypeDefinitionRegistry()
        registry.add(extension)
        when:
        registry.remove(extension)
        then:
        !registry.scalarTypeExtensions().containsKey(extension.getName())
    }

    def "remove multiple scalar type extension from list"() {
        given:
        def extension1 = ScalarTypeExtensionDefinition.newScalarTypeExtensionDefinition().name("foo").build()
        def extension2 = ScalarTypeExtensionDefinition.newScalarTypeExtensionDefinition().name("bar").build()
        def registry = new TypeDefinitionRegistry()
        registry.add(extension1)
        registry.add(extension2)
        when:
        registry.remove(extension1)
        then:
        !registry.scalarTypeExtensions().containsKey(extension1.getName())
        registry.scalarTypeExtensions().containsKey(extension2.getName())
        registry.scalarTypeExtensions().get(extension2.getName()).contains(extension2)
    }

    def "remove single scalar type extension from map"() {
        given:
        def extension = ScalarTypeExtensionDefinition.newScalarTypeExtensionDefinition().name("foo").build()
        def registry = new TypeDefinitionRegistry()
        registry.add(extension)
        when:
        registry.remove(extension.getName(), extension)
        then:
        !registry.scalarTypeExtensions().containsKey(extension.getName())
    }

    def "remove multiple scalar type extension from map"() {
        given:
        def extension1 = ScalarTypeExtensionDefinition.newScalarTypeExtensionDefinition().name("foo").build()
        def extension2 = ScalarTypeExtensionDefinition.newScalarTypeExtensionDefinition().name("bar").build()
        def registry = new TypeDefinitionRegistry()
        registry.add(extension1)
        registry.add(extension2)
        when:
        registry.remove(extension1.getName(), extension1)
        then:
        !registry.scalarTypeExtensions().containsKey(extension1.getName())
        registry.scalarTypeExtensions().containsKey(extension2.getName())
        registry.scalarTypeExtensions().get(extension2.getName()).contains(extension2)
    }

    def "remove single input object type extension from list"() {
        given:
        def extension = InputObjectTypeExtensionDefinition.newInputObjectTypeExtensionDefinition().name("foo").build()
        def registry = new TypeDefinitionRegistry()
        registry.add(extension)
        when:
        registry.remove(extension)
        then:
        !registry.inputObjectTypeExtensions().containsKey(extension.getName())
    }

    def "remove multiple input object type extension from list"() {
        given:
        def extension1 = InputObjectTypeExtensionDefinition.newInputObjectTypeExtensionDefinition().name("foo").build()
        def extension2 = InputObjectTypeExtensionDefinition.newInputObjectTypeExtensionDefinition().name("bar").build()
        def registry = new TypeDefinitionRegistry()
        registry.add(extension1)
        registry.add(extension2)
        when:
        registry.remove(extension1)
        then:
        !registry.inputObjectTypeExtensions().containsKey(extension1.getName())
        registry.inputObjectTypeExtensions().containsKey(extension2.getName())
        registry.inputObjectTypeExtensions().get(extension2.getName()).contains(extension2)
    }

    def "remove single input object type extension from map"() {
        given:
        def extension = InputObjectTypeExtensionDefinition.newInputObjectTypeExtensionDefinition().name("foo").build()
        def registry = new TypeDefinitionRegistry()
        registry.add(extension)
        when:
        registry.remove(extension.getName(), extension)
        then:
        !registry.inputObjectTypeExtensions().containsKey(extension.getName())
    }

    def "remove multiple input object type extension from map"() {
        given:
        def extension1 = InputObjectTypeExtensionDefinition.newInputObjectTypeExtensionDefinition().name("foo").build()
        def extension2 = InputObjectTypeExtensionDefinition.newInputObjectTypeExtensionDefinition().name("bar").build()
        def registry = new TypeDefinitionRegistry()
        registry.add(extension1)
        registry.add(extension2)
        when:
        registry.remove(extension1.getName(), extension1)
        then:
        !registry.inputObjectTypeExtensions().containsKey(extension1.getName())
        registry.inputObjectTypeExtensions().containsKey(extension2.getName())
        registry.inputObjectTypeExtensions().get(extension2.getName()).contains(extension2)
    }

    def "remove schema definition"() {
        given:
        def registry = new TypeDefinitionRegistry()
        def definition = SchemaDefinition.newSchemaDefinition().build()
        registry.add(definition)
        when:
        registry.remove(definition)
        then:
        !registry.schemaDefinition().isPresent()
    }

    def "addAll can add multiple things successfully"() {
        def obj1 = ObjectTypeDefinition.newObjectTypeDefinition().name("foo").build()
        def obj2 = ObjectTypeDefinition.newObjectTypeDefinition().name("bar").build()
        def registry = new TypeDefinitionRegistry()
        when:
        registry.addAll(Arrays.asList(obj1, obj2))
        then:
        registry.getType("foo").isPresent()
        registry.getType("bar").isPresent()
    }

    def "addAll will return an error on the first abd thing"() {
        def obj1 = ObjectTypeDefinition.newObjectTypeDefinition().name("foo").build()
        def obj2 = ObjectTypeDefinition.newObjectTypeDefinition().name("bar").build()
        def obj3 = ObjectTypeDefinition.newObjectTypeDefinition().name("bar").build()
        def obj4 = ObjectTypeDefinition.newObjectTypeDefinition().name("foo").build()
        def registry = new TypeDefinitionRegistry()
        when:
        def error = registry.addAll(Arrays.asList(obj1, obj2, obj3, obj4))
        then:
        error.isPresent()
        error.get().getMessage().contains("tried to redefine existing 'bar' type")
    }

    def "can be serialized and hence cacheable"() {
        def sdl = '''
            "the schema"
            schema {
                query : Q
            }
            
            "the query type"
            type Q {
                field( arg : String! = "default") : FieldType @deprecated(reason : "no good")
            }
            
            interface FieldType {
                f : UnionType
            }
            
            type FieldTypeImpl implements FieldType {
                f : UnionType
            }
            
            union UnionType = Foo | Bar
            
            type Foo {
                foo : String
            }

            type Bar {
                bar : String
            }
        '''
        def registryOut = new SchemaParser().parse(sdl)

        when:

        TypeDefinitionRegistry registryIn = serialise(registryOut)

        then:
        TypeDefinition typeIn = registryIn.getType(typeName).get()
        TypeDefinition typeOut = registryOut.getType(typeName).get()
        typeIn.isEqualTo(typeOut)

        where:
        typeName        | _
        "Q"             | _
        "FieldType"     | _
        "FieldTypeImpl" | _
        "UnionType"     | _
        "Foo"           | _
        "Bar"           | _
    }

    def "can maintain parsed order"() {
        def sdl = '''
            "the schema"
            schema {
                query : Q
            }
            
            "the query type"
            type Q {
                field( arg : String! = "default") : FieldType @deprecated(reason : "no good")
            }
            
            interface FieldType {
                f : UnionType
            }
            
            type FieldTypeImpl implements FieldType {
                f : UnionType
            }
            
            union UnionType = Foo | Bar
            
            type Foo {
                foo : String
            }

            type Bar {
                bar : String
            }
            
            scalar FooScalar
            
            directive @FooDirective on FIELD_DEFINITION
        '''

        when:
        def registry = new SchemaParser().parse(sdl)
        def parseOrder = registry.getParseOrder()
        def defListInOrder = parseOrder.getInOrder().get("")
        def defListInNameOrder = parseOrder.getInNameOrder().get("")
        def listOfNames = defListInNameOrder.collect({ d -> d.getName() })

        then:

        defListInOrder.size() == 9

        defListInOrder[0] instanceof SchemaDefinition
        defListInOrder[1] instanceof ObjectTypeDefinition
        defListInOrder[2] instanceof InterfaceTypeDefinition

        defListInOrder[8] instanceof DirectiveDefinition

        listOfNames == ["Q", "FieldType", "FieldTypeImpl", "UnionType",
                        "Foo", "Bar", "FooScalar", "FooDirective"]

        when: "We merge in a new type registry, they are added to the end"
        def extraSDL = '''
            type Extra {
                s : String
            }

            interface ExtraInterface {
                s : String
            }
        '''

        def extraRegistry = new SchemaParser().parse(extraSDL)
        registry.merge(extraRegistry)

        parseOrder = registry.getParseOrder()
        defListInOrder = parseOrder.getInOrder().get("")
        defListInNameOrder = parseOrder.getInNameOrder().get("")
        listOfNames = defListInNameOrder.collect({ d -> d.getName() })

        then:

        defListInOrder.size() == 11

        defListInOrder[0] instanceof SchemaDefinition
        defListInOrder[1] instanceof ObjectTypeDefinition
        defListInOrder[2] instanceof InterfaceTypeDefinition

        defListInOrder[9] instanceof ObjectTypeDefinition
        defListInOrder[10] instanceof InterfaceTypeDefinition

        listOfNames == ["Q", "FieldType", "FieldTypeImpl", "UnionType",
                        "Foo", "Bar", "FooScalar", "FooDirective", "Extra", "ExtraInterface"]
    }

    def "multi source reader works"() {

        def sdl1 = '''
            type Query {
                f : IType
            }
            
            scalar FooScalar
        '''

        def sdl2 = '''
            interface IType {
                q : FooScalar
            }
            
            type QType {
                q : FooScalar
            }
        '''

        def sdl3 = '''
            directive @FooDirective on FIELD_DEFINITION
        '''

        def multiSourceReader = MultiSourceReader.newMultiSourceReader()
                .string(sdl1, "source1")
                .string(sdl2, "source2")
                .string(sdl3, null)
                .build()

        when:
        def registry = new SchemaParser().parse(multiSourceReader)
        def parseOrder = registry.getParseOrder()
        def inNameOrderMap = parseOrder.getInNameOrder()

        then:
        inNameOrderMap.size() == 3
        inNameOrderMap["source1"].collect({ it.getName() })
                == ["Query", "FooScalar"]
        inNameOrderMap["source2"].collect({ it.getName() })
                == ["IType", "QType"]
        inNameOrderMap[""].collect({ it.getName() })
                == ["FooDirective"]

    }

    def "can created a runtime type comparator"() {
        def sdl1 = '''
            type Query {
                f : QType @FooDirective
            }

            input BInput {
                b : String
            }
            
            input AInput {
                a : String
            }
            
            scalar FooScalar
        '''

        def sdl2 = '''
            type QType {
                c : String
                b : ID
                a : FooScalar
            }

            type XType {
                x : String
                z : ID
                y : FooScalar
            }

        '''

        def sdl3 = '''
            type AType {
                y : FooScalar
                x : String
                z : ID
            }

            directive @FooDirective on FIELD_DEFINITION
        '''

        def multiSourceReader = MultiSourceReader.newMultiSourceReader()
                .string(sdl1, "source1")
                .string(sdl2, "source2")
                .string(sdl3, null)
                .build()

        when:
        def registry = new SchemaParser().parse(multiSourceReader)
        def parseOrder = registry.getParseOrder()
        def schema = new SchemaGenerator().makeExecutableSchema(registry, RuntimeWiring.MOCKED_WIRING)


        def schemaElements = schema.getAllElementsAsList().stream()
                .filter(GraphQLTypeUtil.isSystemElement().negate())
                .collect(Collectors.toList())

        schemaElements.sort(parseOrder.getElementComparator())

        then:
        schemaElements.collect({ it.name }) ==
                ["Query",
                 "BInput",
                 "AInput",
                 "FooScalar",
                 "QType",
                 "XType",
                 "AType",
                 "FooDirective",
                ]

    }

    static TypeDefinitionRegistry serialise(TypeDefinitionRegistry registryOut) {
        ByteArrayOutputStream baOS = new ByteArrayOutputStream()
        ObjectOutputStream oos = new ObjectOutputStream(baOS)

        oos.writeObject(registryOut)

        ByteArrayInputStream baIS = new ByteArrayInputStream(baOS.toByteArray())
        ObjectInputStream ois = new ObjectInputStream(baIS)

        ois.readObject() as TypeDefinitionRegistry
    }
}
