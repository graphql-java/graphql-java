package graphql.parser

import graphql.language.Argument
import graphql.language.ArrayValue
import graphql.language.AstComparator
import graphql.language.BooleanValue
import graphql.language.Comment
import graphql.language.Directive
import graphql.language.DirectiveDefinition
import graphql.language.DirectiveLocation
import graphql.language.Document
import graphql.language.EnumTypeDefinition
import graphql.language.EnumTypeExtensionDefinition
import graphql.language.EnumValueDefinition
import graphql.language.FieldDefinition
import graphql.language.InputObjectTypeDefinition
import graphql.language.InputObjectTypeExtensionDefinition
import graphql.language.InputValueDefinition
import graphql.language.IntValue
import graphql.language.InterfaceTypeDefinition
import graphql.language.InterfaceTypeExtensionDefinition
import graphql.language.ListType
import graphql.language.Node
import graphql.language.NonNullType
import graphql.language.ObjectField
import graphql.language.ObjectTypeDefinition
import graphql.language.ObjectTypeExtensionDefinition
import graphql.language.ObjectValue
import graphql.language.OperationTypeDefinition
import graphql.language.ScalarTypeDefinition
import graphql.language.ScalarTypeExtensionDefinition
import graphql.language.SchemaDefinition
import graphql.language.TypeName
import graphql.language.UnionTypeDefinition
import graphql.language.UnionTypeExtensionDefinition
import graphql.language.VariableReference
import spock.lang.Specification

import java.util.stream.Collectors

class IDLParserTest extends Specification {

    boolean isEqual(Node node1, Node node2) {
        return new AstComparator().isEqual(node1, node2)
    }

    boolean isEqual(List<Node> node1, List<Node> node2) {
        return new AstComparator().isEqual(node1, node2)
    }


    def "StarWars schema"() {
        given:
        def input = """
enum Episode { NEWHOPE, EMPIRE, JEDI }

interface Character {
    id: String!,
    name: String,
    friends: [Character],
    appearsIn: [Episode],
}

type Human implements Character {
    id: String!,
    name: String,
    friends: [Character],
    appearsIn: [Episode],
    homePlanet: String,
}

type Droid implements Character {
    id: String!,
    name: String,
    friends: [Character],
    appearsIn: [Episode],
    primaryFunction: String,
}
"""

        and: "expected schema"
        def episode = EnumTypeDefinition.newEnumTypeDefinition().name("Episode")
        episode.enumValueDefinition(new EnumValueDefinition("NEWHOPE"))
        episode.enumValueDefinition(new EnumValueDefinition("EMPIRE"))
        episode.enumValueDefinition(new EnumValueDefinition("JEDI"))

        def character = InterfaceTypeDefinition.newInterfaceTypeDefinition().name("Character")
        character.definition(new FieldDefinition("id", new NonNullType(new TypeName("String"))))
        character.definition(new FieldDefinition("name", new TypeName("String")))
        character.definition(new FieldDefinition("friends", new ListType(new TypeName("Character"))))
        character.definition(new FieldDefinition("appearsIn", new ListType(new TypeName("Episode"))))

        def human = ObjectTypeDefinition.newObjectTypeDefinition().name("Human")
        human.implementz(new TypeName("Character"))
        human.fieldDefinition(new FieldDefinition("id", new NonNullType(new TypeName("String"))))
        human.fieldDefinition(new FieldDefinition("name", new TypeName("String")))
        human.fieldDefinition(new FieldDefinition("friends", new ListType(new TypeName("Character"))))
        human.fieldDefinition(new FieldDefinition("appearsIn", new ListType(new TypeName("Episode"))))
        human.fieldDefinition(new FieldDefinition("homePlanet", new TypeName("String")))

        def droid = ObjectTypeDefinition.newObjectTypeDefinition().name("Droid")
        droid.implementz(new TypeName("Character"))
        droid.fieldDefinition(new FieldDefinition("id", new NonNullType(new TypeName("String"))))
        droid.fieldDefinition(new FieldDefinition("name", new TypeName("String")))
        droid.fieldDefinition(new FieldDefinition("friends", new ListType(new TypeName("Character"))))
        droid.fieldDefinition(new FieldDefinition("appearsIn", new ListType(new TypeName("Episode"))))
        droid.fieldDefinition(new FieldDefinition("primaryFunction", new TypeName("String")))

        when:
        def document = new Parser().parseDocument(input)

        then:
        document.definitions.size() == 4
        isEqual(document.definitions[0], episode.build())
        isEqual(document.definitions[1], character.build())
        isEqual(document.definitions[2], human.build())
        isEqual(document.definitions[3], droid.build())
    }

    def "interface schema"() {
        given:
        def input = """
interface InterfaceName @interfaceDirective(argName1:\$varName argName2:true) {
fieldName(arg1:SomeType={one:1} @argDirective(a1:\$v1)):[Elm] @fieldDirective(cool:true)
}
"""

        and: "expected schema"
        def iface = InterfaceTypeDefinition.newInterfaceTypeDefinition().name("InterfaceName")
        iface.directive(new Directive("interfaceDirective",
                [new Argument("argName1", new VariableReference("varName")),
                 new Argument("argName2", new BooleanValue(true))]))
        def field = FieldDefinition.newFieldDefinition().name("fieldName").type(new ListType(new TypeName("Elm")))
        field.directive(new Directive("fieldDirective", [new Argument("cool", new BooleanValue(true))]))

        def defaultValue = ObjectValue.newObjectValue()
        defaultValue.objectField(new ObjectField("one", new IntValue(1)))

        def arg1 = InputValueDefinition.newInputValueDefinition().name("arg1").type(new TypeName("SomeType")).defaultValue(defaultValue.build())
        arg1.directive(new Directive("argDirective", [new Argument("a1", new VariableReference("v1"))]))
        field.inputValueDefinition(arg1.build())

        iface.definition(field.build())

        when:
        def document = new Parser().parseDocument(input)

        then:
        document.definitions.size() == 1
        isEqual(document.definitions[0], iface.build())
    }

    def "enum schema"() {
        given:
        def input = """
enum EnumName @enumDirective(a1:\$v1) {
ONE @first,
TWO @second,
}
"""

        and: "expected schema"
        def enumSchema = EnumTypeDefinition.newEnumTypeDefinition().name("EnumName")
        enumSchema.directive(new Directive("enumDirective", [new Argument("a1", new VariableReference("v1"))]))
        enumSchema.enumValueDefinition(new EnumValueDefinition("ONE", [new Directive("first")]))
        enumSchema.enumValueDefinition(new EnumValueDefinition("TWO", [new Directive("second")]))

        when:
        def document = new Parser().parseDocument(input)

        then:
        document.definitions.size() == 1
        isEqual(document.definitions[0], enumSchema.build())
    }

    def "object schema"() {
        given:
        def input = """
type TypeName implements Impl1 Impl2 @typeDirective(a1:\$v1) {
one: Number
two: Number @second
cmd(arg1:[Number]=[1] arg2:String @secondArg(cool:true)): Function
}
"""

        and: "expected schema"
        def objSchema = ObjectTypeDefinition.newObjectTypeDefinition().name("TypeName")
        objSchema.implementz(new TypeName("Impl1"))
        objSchema.implementz(new TypeName("Impl2"))
        objSchema.directive(new Directive("typeDirective", [new Argument("a1", new VariableReference("v1"))]))
        objSchema.fieldDefinition(new FieldDefinition("one", new TypeName("Number")))

        def two = FieldDefinition.newFieldDefinition().name("two").type(new TypeName("Number"))
        two.directive(new Directive("second"))
        objSchema.fieldDefinition(two.build())

        def cmdField = FieldDefinition.newFieldDefinition().name("cmd").type(new TypeName("Function"))
        cmdField.inputValueDefinition(new InputValueDefinition("arg1",
                new ListType(new TypeName("Number")),
                new ArrayValue([new IntValue(1)])))
        def arg2 = InputValueDefinition.newInputValueDefinition().name("arg2").type(new TypeName("String"))
        arg2.directive(new Directive("secondArg", [new Argument("cool", new BooleanValue(true))]))
        cmdField.inputValueDefinition(arg2.build())

        objSchema.fieldDefinition(cmdField.build())

        when:
        def document = new Parser().parseDocument(input)

        then:
        document.definitions.size() == 1
        isEqual(document.definitions[0], objSchema.build())
    }

    def "scalar schema"() {
        given:
        def input = """
scalar ScalarName @scalarDirective(a1:\$v1)
scalar other
"""

        and: "expected schema"
        def schema = ScalarTypeDefinition.newScalarTypeDefinition().name("ScalarName")
        schema.directive(new Directive("scalarDirective", [new Argument("a1", new VariableReference("v1"))]))
        def other = new ScalarTypeDefinition("other")

        when:
        def document = new Parser().parseDocument(input)

        then:
        document.definitions.size() == 2
        isEqual(document.definitions[0], schema.build())
        isEqual(document.definitions[1], other)
    }

    def "union schema"() {
        given:
        def input = """
union UnionName @d1 @d2 = Type1 | Type2
"""

        and: "expected schema"
        def schema = UnionTypeDefinition.newUnionTypeDefinition().name("UnionName")
        schema.directive(new Directive("d1"))
        schema.directive(new Directive("d2"))
        schema.memberType(new TypeName("Type1"))
        schema.memberType(new TypeName("Type2"))

        when:
        def document = new Parser().parseDocument(input)

        then:
        document.definitions.size() == 1
        isEqual(document.definitions[0], schema.build())
    }

    def "input object schema"() {
        given:
        def input = """
input InputName @d1 @d2 {
one: Number
two: Number = 1 @two
three: [Number] @three
}
"""

        and: "expected schema"
        def schema = InputObjectTypeDefinition.newInputObjectDefinition().name("InputName")
        schema.directive(new Directive("d1"))
        schema.directive(new Directive("d2"))
        schema.inputValueDefinition(new InputValueDefinition("one", new TypeName("Number")))

        def two = InputValueDefinition.newInputValueDefinition().name("two").type(new TypeName("Number")).defaultValue(new IntValue(1))
        two.directive(new Directive("two"))
        schema.inputValueDefinition(two.build())

        def three = InputValueDefinition.newInputValueDefinition().name("three").type(new ListType(new TypeName("Number")))
        three.directive(new Directive("three"))
        schema.inputValueDefinition(three.build())

        when:
        def document = new Parser().parseDocument(input)

        then:
        document.definitions.size() == 1
        isEqual(document.definitions[0], schema.build())
    }

    def "toplevel schema"() {
        given:
        def input = """
schema @d1 @d2 {
    query: OpType1
    mutation: OpType2
    subscription: OpType3
}
"""

        and: "expected schema"
        def schema = SchemaDefinition.newSchemaDefintion()
        schema.directive(new Directive("d1"))
        schema.directive(new Directive("d2"))
        schema.operationTypeDefinition(new OperationTypeDefinition("query", new TypeName("OpType1")))
        schema.operationTypeDefinition(new OperationTypeDefinition("mutation", new TypeName("OpType2")))
        schema.operationTypeDefinition(new OperationTypeDefinition("subscription", new TypeName("OpType3")))

        when:
        def document = new Parser().parseDocument(input)

        then:
        document.definitions.size() == 1
        isEqual(document.definitions[0], schema.build())
    }

    def "extend schema"() {
        given:
        def input = """
extend type ExtendType implements Impl3 @extendDirective(a1:\$v1) {
one: Int
two: Int @second
withArgs(arg1:[Number]=[1] arg2:String @secondArg(cool:true)): Function
}
"""

        and: "expected schema"
        def schema = ObjectTypeExtensionDefinition.newObjectTypeExtensionDefinition().name("ExtendType")
        schema.implementz(new TypeName("Impl3"))
        schema.directive(new Directive("extendDirective", [new Argument("a1", new VariableReference("v1"))]))
        schema.fieldDefinition(new FieldDefinition("one", new TypeName("Int")))

        def two = FieldDefinition.newFieldDefinition().name("two").type(new TypeName("Int"))
        two.directive(new Directive("second"))
        schema.fieldDefinition(two.build())

        def withArgs = FieldDefinition.newFieldDefinition().name("withArgs").type(new TypeName("Function"))
        withArgs.inputValueDefinition(new InputValueDefinition("arg1",
                new ListType(new TypeName("Number")),
                new ArrayValue([new IntValue(1)])))

        def arg2 = InputValueDefinition.newInputValueDefinition().name("arg2").type(new TypeName("String"))
        arg2.directive(new Directive("secondArg", [new Argument("cool", new BooleanValue(true))]))
        withArgs.inputValueDefinition(arg2.build())
        schema.fieldDefinition(withArgs.build())

        when:
        def document = new Parser().parseDocument(input)

        then:
        document.definitions.size() == 1
        isEqual(document.definitions[0], schema.build())
    }

    def "directive schema"() {
        given:
        def input = """
directive @DirectiveName(arg1:String arg2:Int=23) on FIELD | QUERY
"""

        and: "expected schema"
        def schema = DirectiveDefinition.newDirectiveDefinition().name("DirectiveName")
        schema.inputValueDefinition(new InputValueDefinition("arg1", new TypeName("String")))
        schema.inputValueDefinition(new InputValueDefinition("arg2", new TypeName("Int"), new IntValue(23)))
        schema.directiveLocation(new DirectiveLocation("FIELD"))
        schema.directiveLocation(new DirectiveLocation("QUERY"))

        when:
        def document = new Parser().parseDocument(input)

        then:
        document.definitions.size() == 1
        isEqual(document.definitions[0], schema.build())
    }


    List<String> commentContent(List<Comment> comments) {
        comments.stream().map { c -> c.content }.collect(Collectors.toList())
    }

    def "comment support on definitions"() {


        given:
        def input = """

#schema comment 1
#       schema comment 2 with leading spaces
schema {
    # schema operation comment query
    query: Query
    # schema operation comment mutation
    mutation: Mutation
}

# type query comment 1
# type query comment 2
type Query {
    # query field 'hero' comment
    hero(episode: Episode): Character
    # query field 'droid' comment
    droid(id: ID!): Droid
    
    #comment after fields that doesnt count for anything
}

# enum Episode comment 1
# enum Episode comment 2
enum Episode { NEWHOPE, EMPIRE, JEDI }

# interface Character comment 1
# interface Character comment 2
interface Character {
    id: String!,
    name: String,
    friends: [Character],
    appearsIn: [Episode],
}

# union type Humanoid comment 1
union Humanoid = Human | Droid

type Mutation {
    shoot (
        # arg 'id'
        id: String!
        # arg 'with'
        with : Gun
    ): Query
}

# input type Gun comment 1
input Gun {
    # gun 'name' input value comment
    name : String
    # gun 'caliber' input value comment
    caliber : Int
}
 

# down here just hanging out wont be counted as anything

"""

        when:
        def document = new Parser().parseDocument(input)

        then:
        SchemaDefinition schemaDef = document.definitions[0] as SchemaDefinition
        commentContent(schemaDef.comments) == ["schema comment 1", "       schema comment 2 with leading spaces"]
        commentContent(schemaDef.operationTypeDefinitions[0].comments) == [" schema operation comment query"]
        commentContent(schemaDef.operationTypeDefinitions[1].comments) == [" schema operation comment mutation"]
        ObjectTypeDefinition typeDef = document.definitions[1] as ObjectTypeDefinition
        commentContent(typeDef.comments) == [" type query comment 1", " type query comment 2"]
        commentContent(typeDef.fieldDefinitions[0].comments) == [" query field 'hero' comment"]
        commentContent(typeDef.fieldDefinitions[1].comments) == [" query field 'droid' comment"]

        EnumTypeDefinition enumTypeDef = document.definitions[2] as EnumTypeDefinition
        commentContent(enumTypeDef.comments) == [" enum Episode comment 1", " enum Episode comment 2"]

        InterfaceTypeDefinition interfaceTypeDef = document.definitions[3] as InterfaceTypeDefinition
        commentContent(interfaceTypeDef.comments) == [" interface Character comment 1", " interface Character comment 2"]

        UnionTypeDefinition unionTypeDef = document.definitions[4] as UnionTypeDefinition
        commentContent(unionTypeDef.comments) == [" union type Humanoid comment 1"]

        ObjectTypeDefinition mutationTypeDef = document.definitions[5] as ObjectTypeDefinition
        commentContent(mutationTypeDef.fieldDefinitions[0].inputValueDefinitions[0].comments) == [" arg 'id'"]
        commentContent(mutationTypeDef.fieldDefinitions[0].inputValueDefinitions[1].comments) == [" arg 'with'"]

        InputObjectTypeDefinition inputTypeDef = document.definitions[6] as InputObjectTypeDefinition
        commentContent(inputTypeDef.comments) == [" input type Gun comment 1"]
        commentContent(inputTypeDef.inputValueDefinitions[0].comments) == [" gun 'name' input value comment"]
        commentContent(inputTypeDef.inputValueDefinitions[1].comments) == [" gun 'caliber' input value comment"]

    }

    def "comments on field arguments"() {
        def input = """
        type QueryType {
          hero(
              #comment about episode
              episode: Episode
              # second
              foo: String = \"bar\"
          ): Character
        }
"""
        when:
        def document = new Parser().parseDocument(input)

        then:
        ObjectTypeDefinition typeDef = document.definitions[0] as ObjectTypeDefinition
        def inputValueDefinitions = typeDef.fieldDefinitions[0].inputValueDefinitions
        commentContent(inputValueDefinitions[0].comments) == ["comment about episode"]
        commentContent(inputValueDefinitions[1].comments) == [" second"]
    }

    def "empty type definition"() {

        def input = """
        type EmptyType
        
        extend type EmptyType {
            hero : String
        }
"""
        when:
        def document = new Parser().parseDocument(input)

        then:
        ObjectTypeDefinition typeDef = document.definitions[0] as ObjectTypeDefinition
        typeDef.getName() == 'EmptyType'
        typeDef.getFieldDefinitions().isEmpty()

        ObjectTypeExtensionDefinition extTypeDef = document.definitions[1] as ObjectTypeExtensionDefinition
        extTypeDef.getName() == 'EmptyType'
        extTypeDef.getFieldDefinitions().size() == 1
    }

    def "empty type definition with body"() {

        def input = """
        type EmptyType {
        
        }
        
        extend type EmptyType {
            hero : String
        }
"""
        when:
        def document = new Parser().parseDocument(input)

        then:
        ObjectTypeDefinition typeDef = document.definitions[0] as ObjectTypeDefinition
        typeDef.getName() == 'EmptyType'
        typeDef.getFieldDefinitions().isEmpty()

        ObjectTypeExtensionDefinition extTypeDef = document.definitions[1] as ObjectTypeExtensionDefinition
        extTypeDef.getName() == 'EmptyType'
        extTypeDef.getFieldDefinitions().size() == 1
    }

    def "type implements can have & character for extra names"() {

        def input = """
        interface Bar {
            bar : String
        }

        interface Baz {
            baz : String
        }
        
        type Foo implements Bar & Baz {
            bar : String
            baz : String
        }
    
        type Foo2 implements Bar Baz {
            bar : String
            baz : String
        }
        
"""
        when:
        def document = new Parser().parseDocument(input)

        then:
        ObjectTypeDefinition typeDef = document.definitions[2] as ObjectTypeDefinition
        typeDef.getName() == 'Foo'
        typeDef.getImplements().size() == 2
        (typeDef.getImplements()[0] as TypeName).getName() == 'Bar'
        (typeDef.getImplements()[1] as TypeName).getName() == 'Baz'

        then:
        ObjectTypeDefinition typeDef2 = document.definitions[3] as ObjectTypeDefinition
        typeDef2.getName() == 'Foo2'
        typeDef2.getImplements().size() == 2
        (typeDef2.getImplements()[0] as TypeName).getName() == 'Bar'
        (typeDef2.getImplements()[1] as TypeName).getName() == 'Baz'
    }

    def "object type extensions"() {

        def input = '''

        type Query {
            bar : String
        }
        
        extend type Query @directiveOnly

        extend type Query @directive {
            field : String
        }

        '''

        when:
        def doc = new Parser().parseDocument(input)

        then:

        // object type extension
        fromDoc(doc, 0, ObjectTypeDefinition).name == "Query"

        fromDoc(doc, 1, ObjectTypeExtensionDefinition).name == "Query"
        fromDoc(doc, 1, ObjectTypeExtensionDefinition).getDirectivesByName().size() == 1
        fromDoc(doc, 1, ObjectTypeExtensionDefinition).getDirectivesByName().containsKey("directiveOnly")
        fromDoc(doc, 1, ObjectTypeExtensionDefinition).getFieldDefinitions().size() == 0

        fromDoc(doc, 2, ObjectTypeExtensionDefinition).name == "Query"
        fromDoc(doc, 2, ObjectTypeExtensionDefinition).getDirectivesByName().size() == 1
        fromDoc(doc, 2, ObjectTypeExtensionDefinition).getDirectivesByName().containsKey("directive")
        fromDoc(doc, 2, ObjectTypeExtensionDefinition).getFieldDefinitions().size() == 1
        fromDoc(doc, 2, ObjectTypeExtensionDefinition).getFieldDefinitions()[0].name == 'field'

    }

    def "interface type extensions"() {

        def input = '''
        
        interface Bar {
            bar : String
        }

        extend interface Bar @directiveOnly
        
        extend interface Bar @directive {
          iField : String
        }
        
        '''

        when:
        def doc = new Parser().parseDocument(input)

        then:

        fromDoc(doc, 0, InterfaceTypeDefinition).name == 'Bar'

        fromDoc(doc, 1, InterfaceTypeExtensionDefinition).name == 'Bar'
        fromDoc(doc, 1, InterfaceTypeExtensionDefinition).getDirectivesByName().size() == 1
        fromDoc(doc, 1, InterfaceTypeExtensionDefinition).getDirectivesByName().containsKey("directiveOnly")
        fromDoc(doc, 1, InterfaceTypeExtensionDefinition).getFieldDefinitions().size() == 0


        fromDoc(doc, 2, InterfaceTypeExtensionDefinition).name == 'Bar'
        fromDoc(doc, 1, InterfaceTypeExtensionDefinition).getDirectivesByName().size() == 1
        fromDoc(doc, 2, InterfaceTypeExtensionDefinition).getDirectivesByName().containsKey("directive")
        fromDoc(doc, 2, InterfaceTypeExtensionDefinition).getFieldDefinitions().size() == 1
        fromDoc(doc, 2, InterfaceTypeExtensionDefinition).getFieldDefinitions()[0].name == 'iField'
    }

    def "union type extensions"() {

        def input = '''

        union FooBar = Foo | Bar

        extend union FooBar @directiveOnly

        extend union FooBar @directive =
            | Baz 
            | Buzz
        
        
        '''

        when:
        def doc = new Parser().parseDocument(input)

        then:

        // union type extension
        fromDoc(doc, 0, UnionTypeDefinition).name == 'FooBar'

        fromDoc(doc, 1, UnionTypeExtensionDefinition).name == 'FooBar'
        fromDoc(doc, 1, UnionTypeExtensionDefinition).getDirectives().size() == 1
        fromDoc(doc, 1, UnionTypeExtensionDefinition).getDirectivesByName().containsKey("directiveOnly")

        fromDoc(doc, 2, UnionTypeExtensionDefinition).name == 'FooBar'
        fromDoc(doc, 2, UnionTypeExtensionDefinition).getDirectives().size() == 1
        fromDoc(doc, 2, UnionTypeExtensionDefinition).getDirectivesByName().containsKey("directive")
        (fromDoc(doc, 2, UnionTypeExtensionDefinition).memberTypes[0] as TypeName).name == 'Baz'
        (fromDoc(doc, 2, UnionTypeExtensionDefinition).memberTypes[1] as TypeName).name == 'Buzz'
    }

    def "enum type extensions"() {

        def input = '''

        enum Numb {
            A, B, C
        }
        
        extend enum Numb @directiveOnly
        
        extend enum Numb @directive {
            E,F
        }
        
        
        '''

        when:
        def doc = new Parser().parseDocument(input)

        then:


        // enum type extension
        fromDoc(doc, 0, EnumTypeDefinition).name == 'Numb'

        fromDoc(doc, 1, EnumTypeExtensionDefinition).name == 'Numb'
        fromDoc(doc, 1, EnumTypeExtensionDefinition).getDirectives().size() == 1
        fromDoc(doc, 1, EnumTypeExtensionDefinition).getDirectivesByName().containsKey("directiveOnly")

        fromDoc(doc, 2, EnumTypeExtensionDefinition).name == 'Numb'
        fromDoc(doc, 2, EnumTypeExtensionDefinition).getDirectives().size() == 1
        fromDoc(doc, 2, EnumTypeExtensionDefinition).getDirectivesByName().containsKey("directive")
        fromDoc(doc, 2, EnumTypeExtensionDefinition).getEnumValueDefinitions()[0].name == 'E'
        fromDoc(doc, 2, EnumTypeExtensionDefinition).getEnumValueDefinitions()[1].name == 'F'
    }

    def "scalar type extensions"() {

        def input = '''

        scalar Scales
        
        extend scalar Scales @directiveOnly 
        
        extend scalar Scales @directive
        
        '''

        when:
        def doc = new Parser().parseDocument(input)

        then:


        // scalar type extension
        fromDoc(doc, 0, ScalarTypeDefinition).name == 'Scales'

        fromDoc(doc, 1, ScalarTypeExtensionDefinition).name == 'Scales'
        fromDoc(doc, 1, ScalarTypeExtensionDefinition).getDirectives().size() == 1
        fromDoc(doc, 1, ScalarTypeExtensionDefinition).getDirectivesByName().containsKey("directiveOnly")

        fromDoc(doc, 2, ScalarTypeExtensionDefinition).name == 'Scales'
        fromDoc(doc, 2, ScalarTypeExtensionDefinition).getDirectives().size() == 1
        fromDoc(doc, 2, ScalarTypeExtensionDefinition).getDirectivesByName().containsKey("directive")
    }

    def "input object type extensions"() {

        def input = '''

        input Puter {
            field : String
        }
        
        extend input Puter @directiveOnly
        
        extend input Puter @directive {
            inputField : String
        }
            
        '''

        when:
        def doc = new Parser().parseDocument(input)

        then:

        fromDoc(doc, 0, InputObjectTypeDefinition).name == 'Puter'

        fromDoc(doc, 1, InputObjectTypeExtensionDefinition).name == 'Puter'
        fromDoc(doc, 1, InputObjectTypeExtensionDefinition).getDirectives().size() == 1
        fromDoc(doc, 1, InputObjectTypeExtensionDefinition).getDirectivesByName().containsKey("directiveOnly")
        fromDoc(doc, 1, InputObjectTypeExtensionDefinition).inputValueDefinitions.isEmpty()

        fromDoc(doc, 2, InputObjectTypeExtensionDefinition).name == 'Puter'
        fromDoc(doc, 2, InputObjectTypeExtensionDefinition).getDirectives().size() == 1
        fromDoc(doc, 2, InputObjectTypeExtensionDefinition).getDirectivesByName().containsKey("directive")
        fromDoc(doc, 2, InputObjectTypeExtensionDefinition).inputValueDefinitions[0].name == 'inputField'
    }

    def "source name is available when specified"() {

        def input = 'type Query { hello: String }'
        def sourceName = 'named.graphql'

        when:
        def defaultDoc = new Parser().parseDocument(input)
        def namedDocNull = new Parser().parseDocument(input, null)
        def namedDoc = new Parser().parseDocument(input, sourceName)

        then:

        defaultDoc.definitions[0].sourceLocation.sourceName == null
        namedDocNull.definitions[0].sourceLocation.sourceName == null
        namedDoc.definitions[0].sourceLocation.sourceName == sourceName

    }

    static <T> T fromDoc(Document document, int index, Class<T> asClass) {
        def definition = document.definitions[index]
        assert asClass == definition.getClass(), "Could not find expected definition of type " + asClass.getName() + " but was " + definition.getClass().getName()
        return asClass.cast(definition)
    }
}

