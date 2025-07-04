package graphql.schema.idl

import graphql.language.InterfaceTypeDefinition
import graphql.language.TypeDefinition
import spock.lang.Specification

class ImmutableTypeDefinitionRegistryTest extends Specification {

    def serializableSchema = '''
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
            
            scalar MyScalar
            
            input InputType {
                in : String
            }
        '''

    def "immutable registry can be serialized and hence cacheable"() {
        def registryOut = new SchemaParser().parse(serializableSchema).readOnly()

        when:

        TypeDefinitionRegistry registryIn = serialise(registryOut).readOnly()

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

    def "immutable registry is a perfect copy of the starting registry"() {
        when:
        def mutableRegistry = new SchemaParser().parse(serializableSchema)
        def immutableRegistry = mutableRegistry.readOnly()

        then:

        containsSameObjects(mutableRegistry.types(), immutableRegistry.types())

        TypeDefinition typeIn = mutableRegistry.getType(typeName).get()
        TypeDefinition typeOut = immutableRegistry.getType(typeName).get()
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

    def "extensions are also present in immutable copy"() {
        def sdl = serializableSchema + """
            extend type FieldTypeImpl {
                extra : String
            }
            
            extend input InputType {
                out : String
            }
 
            extend scalar MyScalar @specifiedBy(url: "myUrl.example")
            
            extend union UnionType @deprecated

            extend interface FieldType @deprecated

        """

        when:
        def mutableRegistry = new SchemaParser().parse(sdl)
        def immutableRegistry = mutableRegistry.readOnly()

        then:

        containsSameObjects(mutableRegistry.types(), immutableRegistry.types())
        containsSameObjects(mutableRegistry.objectTypeExtensions(), immutableRegistry.objectTypeExtensions())
        containsSameObjects(mutableRegistry.inputObjectTypeExtensions(), immutableRegistry.inputObjectTypeExtensions())
        containsSameObjects(mutableRegistry.interfaceTypeExtensions(), immutableRegistry.interfaceTypeExtensions())
        containsSameObjects(mutableRegistry.unionTypeExtensions(), immutableRegistry.unionTypeExtensions())
        containsSameObjects(mutableRegistry.scalarTypeExtensions(), immutableRegistry.scalarTypeExtensions())

    }

    def "readonly is aware if itself"() {
        when:
        def mutableRegistry = new SchemaParser().parse(serializableSchema)
        def immutableRegistry1 = mutableRegistry.readOnly()

        then:
        mutableRegistry !== immutableRegistry1

        when:
        def immutableRegistry2 = immutableRegistry1.readOnly()

        then:
        immutableRegistry2 === immutableRegistry1


    }

    def "is in read only mode"() {
        when:
        def mutableRegistry = new SchemaParser().parse(serializableSchema)
        def immutableRegistry = mutableRegistry.readOnly()

        immutableRegistry.merge(mutableRegistry)

        then:
        thrown(UnsupportedOperationException)

        when:
        immutableRegistry.addAll([])
        then:
        thrown(UnsupportedOperationException)


        def someDef = mutableRegistry.getTypes(TypeDefinition.class)[0]

        when:
        immutableRegistry.add(someDef)
        then:
        thrown(UnsupportedOperationException)

        when:
        immutableRegistry.remove(someDef)
        then:
        thrown(UnsupportedOperationException)

        when:
        immutableRegistry.remove("key", someDef)
        then:
        thrown(UnsupportedOperationException)
    }

    def "get implementations of"() {
        def sdl = serializableSchema + """
            interface IType {
                i : String
            }
            
            interface DerivedIType implements IType {
                i : String
                d : String                
            } 
            
        """
        for (int i = 0; i < 10; i++) {
            sdl += """
                type OT$i implements IType {
                    i : String
                }
                """

        }
        for (int i = 0; i < 5; i++) {
            sdl += """
            type DT$i implements DerivedIType {
                i : String
                d : String                
            } 
                """

        }
        def immutableRegistry = new SchemaParser().parse(sdl).readOnly()

        Map<String, InterfaceTypeDefinition> interfaces = immutableRegistry.getTypesMap(InterfaceTypeDefinition.class)

        when:
        def iFieldType = interfaces.get("IType")
        def allImplementationsOf = immutableRegistry.getAllImplementationsOf(iFieldType)
        def implementationsOf = immutableRegistry.getImplementationsOf(iFieldType)

        then:
        allImplementationsOf.size() == 11
        allImplementationsOf.collect({ it.getName() }).every { it.startsWith("OT") || it == "DerivedIType" }

        implementationsOf.size() == 10
        implementationsOf.collect({ it.getName() }).every { it.startsWith("OT") }

        when:
        def iDerivedType = interfaces.get("DerivedIType")
        allImplementationsOf = immutableRegistry.getAllImplementationsOf(iDerivedType)
        implementationsOf = immutableRegistry.getImplementationsOf(iDerivedType)

        then:
        allImplementationsOf.size() == 5
        allImplementationsOf.collect({ it.getName() }).every { it.startsWith("DT") }

        implementationsOf.size() == 5
        implementationsOf.collect({ it.getName() }).every { it.startsWith("DT") }

    }

    void containsSameObjects(Map<String, Object> leftMap, Map<String, Object> rightMap) {
        assert leftMap.size() > 0, "The map must have some entries"
        assert leftMap.size() == rightMap.size(), "The maps are not the same size"
        for (String leftKey : leftMap.keySet()) {
            def leftVal = leftMap.get(leftKey)
            def rightVal = rightMap.get(leftKey)
            assert leftVal === rightVal, "$leftKey :  $leftVal dont not strictly equal $rightVal"
        }
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
