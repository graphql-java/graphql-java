package graphql.schema.usage

import graphql.TestUtil
import spock.lang.Specification

class SchemaUsageTest extends Specification {

    def sdl = '''
            type Query {
                f : Ref1
            }
            
            type Ref1 {
                f1 : Ref2
                f2 : IRef1 
                f3 : RefUnion1
                f4 : RefEnum1
                f5 : String
                
                f_arg1( arg : RefInput1) : String
                f_arg2( arg : [RefInput2]) : String
                f_arg3( arg : RefInput3!) : String
            }

            type Ref2 { f : ID}
            
            interface IRef1 implements IRef2 {
                f : ID
                f2 :  String
            }
            
            interface IRef2 {
                f: ID
            }


            type Floating1 implements IRef1 & IRef2 {
                f : ID
                f2 :  String
            }

            type Floating2 implements IRef2 {
                f : ID
                circular1 : Floating2
                circular2 : Floating3
            }

            type Floating3 {
                circular1 : Floating2 @deprecated
            }
                         
            union RefUnion1 = Ref1 | Ref2
            
            enum RefEnum1 { A, B }
            
            
            input RefInput1 {
                f : RefInput2
            }

            input RefInput2 {
                f : RefInput1
                f1 : String
                f3 : RefInput1
                f4 : RefInput1
            }

            input RefInput3 {
                f1 : String
                f2 : RefInput4
            }

            input RefInput4 {
                f1 : String
            }
            
            
            interface UnIRef1 { f: ID }

            union UnRefUnion1 = Ref1 | Ref2
            
            type UnRef1 { f : ID }
            
            enum UnRefEnum1 { A, B }
            
            input UnRefInput1 {
                f : ID
            }
            
            
            @directive RefArgDirective on ARGUMENT_DEFINITION
            @directive RefArgDirective on FIELD_DEFINITION
            @directive RefFieldDirective on FIELD_DEFINITION
            
            type RefDirectiveType
            
        '''

    def "can get references"() {

        def schema = TestUtil.schema(sdl)

        when:
        def schemaUsage = SchemaUsageSupport.getSchemaUsage(schema)
        then:
        schemaUsage.isReferenced(schema, "Ref1")
        schemaUsage.isReferenced(schema, "Ref2")
        schemaUsage.isReferenced(schema, "IRef1")
        schemaUsage.isReferenced(schema, "IRef2")
        schemaUsage.isReferenced(schema, "Floating1")
        schemaUsage.isReferenced(schema, "Floating2")
        schemaUsage.isReferenced(schema, "Floating3")

        schemaUsage.isReferenced(schema, "RefUnion1")

        schemaUsage.isReferenced(schema, "RefInput1")
        schemaUsage.isReferenced(schema, "RefInput2")
        schemaUsage.isReferenced(schema, "RefInput3")
        schemaUsage.isReferenced(schema, "RefInput4")

        schemaUsage.isReferenced(schema, "ID")
        schemaUsage.isReferenced(schema, "String")
        schemaUsage.isReferenced(schema, "Int")
        schemaUsage.isReferenced(schema, "__Type")
        schemaUsage.isReferenced(schema, "__Schema")

        !schemaUsage.isReferenced(schema, "UnRef1")
        !schemaUsage.isReferenced(schema, "UnIRef1")
        !schemaUsage.isReferenced(schema, "UnRefEnum1")
        !schemaUsage.isReferenced(schema, "UnRefInput1")
    }

    def "can record counts"() {
        def schema = TestUtil.schema(sdl)

        when:
        def schemaUsage = SchemaUsageSupport.getSchemaUsage(schema)
        then:
        schemaUsage.getInputFieldReferenceCount()["Ref1"] == null
        schemaUsage.getInputFieldReferenceCount()["RefInput1"] == 3
        schemaUsage.getFieldReferenceCount()["RefInput1"] == 3
        schemaUsage.getArgumentReferenceCount()["RefInput1"] == 1

    }
}
