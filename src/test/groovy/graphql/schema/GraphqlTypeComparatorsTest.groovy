package graphql.schema

import graphql.TestUtil
import spock.lang.Specification

class GraphqlTypeComparatorsTest extends Specification {

    def spec = '''
            type Query {
                zField(zArg : String, yArg : String, xArg : InputType) : ZType
                yField : YInterface
                xField : XUnion
            }
            
            type ZType {
                enumField : EnumType
            }
            
            interface YInterface {
                zField : String
                yField : String
                xField : String
            } 
            
            interface XInterface {
                zField : String
                yField : String
                xField : String
            }

            interface ZInterface {
                zField : String
                yField : String
                xField : String
            }
            
            union XUnion = Foo | Bar
            
            type Foo implements YInterface, XInterface, ZInterface {
                zField : String
                yField : String
                xField : String
            }
            
            type Bar implements YInterface {
                zField : String
                yField : String
                xField : String
            }
            
            enum EnumType {
                z
                y
                x
            }
            
            input InputType {
                zInput : String
                xInput : String
                yInput : String
            }
        '''

    def schema = TestUtil.schema(spec)

    def "test that types sorted from the schema"() {
        def expectedNames = ["Bar", "Boolean", "EnumType", "Foo", "InputType", "Query", "String", "XInterface", "XUnion", "YInterface", "ZInterface", "ZType",
                             "__Directive", "__DirectiveLocation", "__EnumValue", "__Field", "__InputValue", "__Schema", "__Type", "__TypeKind"]
        when:
        def names = schema.getAllTypesAsList().collect({ thing -> thing.getName() })

        then:
        names == expectedNames

        when:
        names = schema.getTypeMap().values().collect({ thing -> thing.getName() })

        then:
        names == expectedNames

        when:
        names = schema.getTypeMap().keySet().toList()

        then:
        names == expectedNames

    }

    def "test that fields in a type are sorted"() {
        when:
        def names = schema.getObjectType("Query").getFieldDefinitions().collect({ thing -> thing.getName() })

        then:
        names == ["xField", "yField", "zField"]

        when:
        def interfaceType = schema.getType("YInterface") as GraphQLInterfaceType
        names = interfaceType.getFieldDefinitions().collect({ thing -> thing.getName() })

        then:
        names == ["xField", "yField", "zField"]
    }

    def "test that members in a union are sorted"() {
        when:
        def unionType = schema.getType("XUnion") as GraphQLUnionType
        def names = unionType.getTypes().collect({ thing -> thing.getName() })

        then:
        names == ["Bar", "Foo"]
    }

    def "test that implementations in a object type are sorted"() {
        when:
        def objectType = schema.getObjectType("Foo")
        def names = objectType.getInterfaces().collect({ thing -> thing.getName() })

        then:
        names == ["XInterface", "YInterface", "ZInterface"]
    }

    def "test that args in a field in a type are sorted"() {
        when:
        def names = schema.getObjectType("Query").getFieldDefinition("zField").getArguments().collect({ thing -> thing.getName() })

        then:
        names == ["xArg", "yArg", "zArg"]

    }

    def "test that enum values in a enum are sorted"() {
        def enumType = schema.getType("EnumType") as GraphQLEnumType
        when:
        def names = enumType.getValues().collect({ thing -> thing.getName() })

        then:
        names == ["x", "y", "z"]

    }

    def "test that input fields in a input type are sorted"() {
        def inputType = schema.getType("InputType") as GraphQLInputObjectType
        when:
        def names = inputType.getFieldDefinitions().collect({ thing -> thing.getName() })

        then:
        names == ["xInput", "yInput", "zInput"]
    }
}
