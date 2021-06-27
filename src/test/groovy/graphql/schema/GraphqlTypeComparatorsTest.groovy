package graphql.schema

import graphql.TestUtil
import spock.lang.Specification

import static graphql.schema.idl.RuntimeWiring.newRuntimeWiring

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
            
            type Foo implements YInterface & XInterface & ZInterface {
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

    def runtimeWiringByName = newRuntimeWiring()
            .comparatorRegistry(GraphqlTypeComparatorRegistry.BY_NAME_REGISTRY)
            .wiringFactory(TestUtil.mockWiringFactory)
    def schemaByName = TestUtil.schema(spec, runtimeWiringByName)


    def runtimeWiringAsIs = newRuntimeWiring()
            .comparatorRegistry(GraphqlTypeComparatorRegistry.AS_IS_REGISTRY)
            .wiringFactory(TestUtil.mockWiringFactory)
    def schemaAsIs = TestUtil.schema(spec, runtimeWiringAsIs)

    def "test that types sorted from the schema"() {
        def expectedNames = ["Bar", "Boolean", "EnumType", "Foo", "InputType", "Query", "String", "XInterface", "XUnion", "YInterface", "ZInterface", "ZType",
                             "__Directive", "__DirectiveLocation", "__EnumValue", "__Field", "__InputValue", "__Schema", "__Type", "__TypeKind"]
        when:
        def names = schemaByName.getAllTypesAsList().collect({ thing -> thing.getName() })

        then:
        names == expectedNames

        when:
        names = schemaByName.getTypeMap().values().collect({ thing -> thing.getName() })

        then:
        names == expectedNames

        when:
        names = schemaByName.getTypeMap().keySet().toList()

        then:
        names == expectedNames

    }

    def "test that fields in a type are sorted"() {
        when:
        def names = schemaByName.getObjectType("Query").getFieldDefinitions().collect({ thing -> thing.getName() })

        then:
        names == ["xField", "yField", "zField"]

        when:
        names = schemaAsIs.getObjectType("Query").getFieldDefinitions().collect({ thing -> thing.getName() })

        then:
        names == ["zField", "yField", "xField"]

        when:
        def interfaceType = schemaByName.getType("YInterface") as GraphQLInterfaceType
        names = interfaceType.getFieldDefinitions().collect({ thing -> thing.getName() })

        then:
        names == ["xField", "yField", "zField"]

        when:
        interfaceType = schemaAsIs.getType("YInterface") as GraphQLInterfaceType
        names = interfaceType.getFieldDefinitions().collect({ thing -> thing.getName() })

        then:
        names == ["zField", "yField", "xField"]
    }

    def "test that members in a union are sorted"() {
        when:
        def unionType = schemaByName.getType("XUnion") as GraphQLUnionType
        def names = unionType.getTypes().collect({ thing -> thing.getName() })

        then:
        names == ["Bar", "Foo"]

        when:
        unionType = schemaAsIs.getType("XUnion") as GraphQLUnionType
        names = unionType.getTypes().collect({ thing -> thing.getName() })

        then:
        names == ["Foo", "Bar"]
    }

    def "test that implementations in a object type are sorted"() {
        when:
        def objectType = schemaByName.getObjectType("Foo")
        def names = objectType.getInterfaces().collect({ thing -> thing.getName() })

        then:
        names == ["XInterface", "YInterface", "ZInterface"]

        when:
        objectType = schemaAsIs.getObjectType("Foo")
        names = objectType.getInterfaces().collect({ thing -> thing.getName() })

        then:
        names == ["YInterface", "XInterface", "ZInterface"]
    }

    def "test that args in a field in a type are sorted"() {
        when:
        def names = schemaByName.getObjectType("Query").getFieldDefinition("zField").getArguments().collect({ thing -> thing.getName() })

        then:
        names == ["xArg", "yArg", "zArg"]

        when:
        names = schemaAsIs.getObjectType("Query").getFieldDefinition("zField").getArguments().collect({ thing -> thing.getName() })

        then:
        names == ["zArg", "yArg", "xArg"]

    }

    def "test that enum values in a enum are sorted"() {
        when:
        def enumType = schemaByName.getType("EnumType") as GraphQLEnumType
        def names = enumType.getValues().collect({ thing -> thing.getName() })

        then:
        names == ["x", "y", "z"]

        when:
        enumType = schemaAsIs.getType("EnumType") as GraphQLEnumType
        names = enumType.getValues().collect({ thing -> thing.getName() })

        then:
        names == ["z", "y", "x"]

    }

    def "test that input fields in a input type are sorted"() {
        when:
        def inputType = schemaByName.getType("InputType") as GraphQLInputObjectType
        def names = inputType.getFieldDefinitions().collect({ thing -> thing.getName() })

        then:
        names == ["xInput", "yInput", "zInput"]

        when:
        inputType = schemaAsIs.getType("InputType") as GraphQLInputObjectType
        names = inputType.getFieldDefinitions().collect({ thing -> thing.getName() })

        then:
        names == ["zInput", "xInput", "yInput"]
    }
}
