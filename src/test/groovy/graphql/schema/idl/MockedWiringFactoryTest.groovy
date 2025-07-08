package graphql.schema.idl


import spock.lang.Specification

class MockedWiringFactoryTest extends Specification {

    def "mock wiring factory can be used for any schema"() {
        def sdl = """
            type Query {
                foo : Foo
            }
            
            scalar SomeScalar
            scalar SomeOtherScalar
            
            type Foo {
                bar( 
                    arg1 : SomeScalar! = 666, 
                    arg2 : Int! = 777, 
                    arg3 : SomeOtherScalar = { x : [{ y : 1, z : "s"}] } ) : Bar
            }
            
            interface Bar {
                baz : String
            }
            
            type BarBar implements Bar {
                baz : String
            }

            type BlackSheep implements Bar {
                baz : String
            }
        """

        when:
        def registry = new SchemaParser().parse(sdl)
        def schema = new SchemaGenerator().makeExecutableSchema(registry, RuntimeWiring.MOCKED_WIRING)

        then:
        schema != null
        schema.getType("Query") != null
        schema.getType("Foo") != null
        schema.getType("Bar") != null
        schema.getType("BarBar") != null
        schema.getType("BlackSheep") != null
    }
}
