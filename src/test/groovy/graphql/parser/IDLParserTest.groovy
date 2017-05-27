package graphql.parser

import graphql.language.*
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
        def episode = new EnumTypeDefinition("Episode")
        episode.getEnumValueDefinitions().add(new EnumValueDefinition("NEWHOPE"))
        episode.getEnumValueDefinitions().add(new EnumValueDefinition("EMPIRE"))
        episode.getEnumValueDefinitions().add(new EnumValueDefinition("JEDI"))
        def character = new InterfaceTypeDefinition("Character")
        character.getFieldDefinitions()
                .add(new FieldDefinition("id", new NonNullType(new TypeName("String"))))
        character.getFieldDefinitions()
                .add(new FieldDefinition("name", new TypeName("String")))
        character.getFieldDefinitions()
                .add(new FieldDefinition("friends", new ListType(new TypeName("Character"))))
        character.getFieldDefinitions()
                .add(new FieldDefinition("appearsIn", new ListType(new TypeName("Episode"))))
        def human = new ObjectTypeDefinition("Human")
        human.getImplements().add(new TypeName("Character"))
        human.getFieldDefinitions()
                .add(new FieldDefinition("id", new NonNullType(new TypeName("String"))))
        human.getFieldDefinitions()
                .add(new FieldDefinition("name", new TypeName("String")))
        human.getFieldDefinitions()
                .add(new FieldDefinition("friends", new ListType(new TypeName("Character"))))
        human.getFieldDefinitions()
                .add(new FieldDefinition("appearsIn", new ListType(new TypeName("Episode"))))
        human.getFieldDefinitions()
                .add(new FieldDefinition("homePlanet", new TypeName("String")))

        def droid = new ObjectTypeDefinition("Droid")
        droid.getImplements().add(new TypeName("Character"))
        droid.getFieldDefinitions()
                .add(new FieldDefinition("id", new NonNullType(new TypeName("String"))))
        droid.getFieldDefinitions()
                .add(new FieldDefinition("name", new TypeName("String")))
        droid.getFieldDefinitions()
                .add(new FieldDefinition("friends", new ListType(new TypeName("Character"))))
        droid.getFieldDefinitions()
                .add(new FieldDefinition("appearsIn", new ListType(new TypeName("Episode"))))
        droid.getFieldDefinitions()
                .add(new FieldDefinition("primaryFunction", new TypeName("String")))

        when:
        def document = new Parser().parseDocument(input)

        then:
        document.definitions.size() == 4
        isEqual(document.definitions[0], episode)
        isEqual(document.definitions[1], character)
        isEqual(document.definitions[2], human)
        isEqual(document.definitions[3], droid)
    }

    def "interface schema"() {
        given:
        def input = """
interface InterfaceName @interfaceDirective(argName1:\$varName argName2:true) {
fieldName(arg1:SomeType={one:1} @argDirective(a1:\$v1)):[Elm] @fieldDirective(cool:true)
}
"""

        and: "expected schema"
        def iface = new InterfaceTypeDefinition("InterfaceName")
        iface.getDirectives()
                .add(new Directive("interfaceDirective",
                [new Argument("argName1", new VariableReference("varName")),
                 new Argument("argName2", new BooleanValue(true))]))
        def field = new FieldDefinition("fieldName", new ListType(new TypeName("Elm")))
        field.getDirectives()
                .add(new Directive("fieldDirective", [new Argument("cool", new BooleanValue(true))]))

        def defaultValue = new ObjectValue()
        defaultValue.getObjectFields().add(new ObjectField("one", new IntValue(1)))
        def arg1 = new InputValueDefinition("arg1",
                new TypeName("SomeType"),
                defaultValue)
        arg1.getDirectives()
                .add(new Directive("argDirective", [new Argument("a1", new VariableReference("v1"))]))
        field.getInputValueDefinitions().add(arg1)

        iface.getFieldDefinitions().add(field)

        when:
        def document = new Parser().parseDocument(input)

        then:
        document.definitions.size() == 1
        isEqual(document.definitions[0], iface)
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
        def enumSchema = new EnumTypeDefinition("EnumName")
        enumSchema.getDirectives()
                .add(new Directive("enumDirective", [new Argument("a1", new VariableReference("v1"))]))
        enumSchema.getEnumValueDefinitions()
                .add(new EnumValueDefinition("ONE", [new Directive("first")]))
        enumSchema.getEnumValueDefinitions()
                .add(new EnumValueDefinition("TWO", [new Directive("second")]))

        when:
        def document = new Parser().parseDocument(input)

        then:
        document.definitions.size() == 1
        isEqual(document.definitions[0], enumSchema)
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
        def objSchema = new ObjectTypeDefinition("TypeName")
        objSchema.getImplements().add(new TypeName("Impl1"))
        objSchema.getImplements().add(new TypeName("Impl2"))
        objSchema.getDirectives()
                .add(new Directive("typeDirective", [new Argument("a1", new VariableReference("v1"))]))
        objSchema.getFieldDefinitions()
                .add(new FieldDefinition("one", new TypeName("Number")))
        def two = new FieldDefinition("two", new TypeName("Number"))
        two.getDirectives().add(new Directive("second"))
        objSchema.getFieldDefinitions().add(two)

        def cmdField = new FieldDefinition("cmd", new TypeName("Function"))
        cmdField.getInputValueDefinitions()
                .add(new InputValueDefinition("arg1",
                new ListType(new TypeName("Number")),
                new ArrayValue([new IntValue(1)])))
        def arg2 = new InputValueDefinition("arg2", new TypeName("String"))
        arg2.getDirectives()
                .add(new Directive("secondArg", [new Argument("cool", new BooleanValue(true))]))
        cmdField.getInputValueDefinitions().add(arg2)
        objSchema.getFieldDefinitions().add(cmdField)

        when:
        def document = new Parser().parseDocument(input)

        then:
        document.definitions.size() == 1
        isEqual(document.definitions[0], objSchema)
    }

    def "scalar schema"() {
        given:
        def input = """
scalar ScalarName @scalarDirective(a1:\$v1)
scalar other
"""

        and: "expected schema"
        def schema = new ScalarTypeDefinition("ScalarName")
        schema.getDirectives()
                .add(new Directive("scalarDirective", [new Argument("a1", new VariableReference("v1"))]))
        def other = new ScalarTypeDefinition("other")

        when:
        def document = new Parser().parseDocument(input)

        then:
        document.definitions.size() == 2
        isEqual(document.definitions[0], schema)
        isEqual(document.definitions[1], other)
    }

    def "union schema"() {
        given:
        def input = """
union UnionName @d1 @d2 = Type1 | Type2
"""

        and: "expected schema"
        def schema = new UnionTypeDefinition("UnionName")
        schema.getDirectives().add(new Directive("d1"))
        schema.getDirectives().add(new Directive("d2"))
        schema.getMemberTypes().add(new TypeName("Type1"))
        schema.getMemberTypes().add(new TypeName("Type2"))

        when:
        def document = new Parser().parseDocument(input)

        then:
        document.definitions.size() == 1
        isEqual(document.definitions[0], schema)
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
        def schema = new InputObjectTypeDefinition("InputName")
        schema.getDirectives().add(new Directive("d1"))
        schema.getDirectives().add(new Directive("d2"))
        schema.getInputValueDefinitions()
                .add(new InputValueDefinition("one", new TypeName("Number")))

        def two = new InputValueDefinition("two", new TypeName("Number"), new IntValue(1))
        two.getDirectives().add(new Directive("two"))
        schema.getInputValueDefinitions().add(two)

        def three = new InputValueDefinition("three", new ListType(new TypeName("Number")))
        three.getDirectives().add(new Directive("three"))
        schema.getInputValueDefinitions().add(three)

        when:
        def document = new Parser().parseDocument(input)

        then:
        document.definitions.size() == 1
        isEqual(document.definitions[0], schema)
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
        def schema = new SchemaDefinition()
        schema.getDirectives().add(new Directive("d1"))
        schema.getDirectives().add(new Directive("d2"))
        schema.getOperationTypeDefinitions()
                .add(new OperationTypeDefinition("query", new TypeName("OpType1")))
        schema.getOperationTypeDefinitions()
                .add(new OperationTypeDefinition("mutation", new TypeName("OpType2")))
        schema.getOperationTypeDefinitions()
                .add(new OperationTypeDefinition("subscription", new TypeName("OpType3")))

        when:
        def document = new Parser().parseDocument(input)

        then:
        document.definitions.size() == 1
        isEqual(document.definitions[0], schema)
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
        def schema = new TypeExtensionDefinition("ExtendType")
        schema.getImplements().add(new TypeName("Impl3"))
        schema.getDirectives()
                .add(new Directive("extendDirective", [new Argument("a1", new VariableReference("v1"))]))
        schema.getFieldDefinitions()
                .add(new FieldDefinition("one", new TypeName("Int")))
        def two = new FieldDefinition("two", new TypeName("Int"))
        two.getDirectives().add(new Directive("second"))
        schema.getFieldDefinitions().add(two)

        def withArgs = new FieldDefinition("withArgs", new TypeName("Function"))
        withArgs.getInputValueDefinitions()
                .add(new InputValueDefinition("arg1",
                new ListType(new TypeName("Number")),
                new ArrayValue([new IntValue(1)])))
        def arg2 = new InputValueDefinition("arg2", new TypeName("String"))
        arg2.getDirectives()
                .add(new Directive("secondArg", [new Argument("cool", new BooleanValue(true))]))
        withArgs.getInputValueDefinitions().add(arg2)
        schema.getFieldDefinitions().add(withArgs)

        when:
        def document = new Parser().parseDocument(input)

        then:
        document.definitions.size() == 1
        isEqual(document.definitions[0], schema)
    }

    def "directive schema"() {
        given:
        def input = """
directive @DirectiveName(arg1:String arg2:Int=23) on FIELD | QUERY
"""

        and: "expected schema"
        def schema = new DirectiveDefinition("DirectiveName")
        schema.getInputValueDefinitions()
                .add(new InputValueDefinition("arg1", new TypeName("String")))
        schema.getInputValueDefinitions()
                .add(new InputValueDefinition("arg2", new TypeName("Int"), new IntValue(23)))
        schema.getDirectiveLocations()
                .add(new DirectiveLocation("FIELD"))
        schema.getDirectiveLocations()
                .add(new DirectiveLocation("QUERY"))

        when:
        def document = new Parser().parseDocument(input)

        then:
        document.definitions.size() == 1
        isEqual(document.definitions[0], schema)
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

}

