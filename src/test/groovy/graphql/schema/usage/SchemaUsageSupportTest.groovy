package graphql.schema.usage

import graphql.TestUtil
import graphql.schema.GraphQLAppliedDirective
import graphql.schema.GraphQLFieldDefinition
import graphql.schema.SchemaTransformer
import graphql.schema.visitor.GraphQLSchemaTraversalControl
import graphql.schema.visitor.GraphQLSchemaVisitor
import spock.lang.Specification

class SchemaUsageSupportTest extends Specification {

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
                f6 : RefUnion2
                
                f_arg1( arg : RefInput1) : String
                f_arg2( arg : [RefInput2]) : String
                f_arg3( arg : RefInput3!) : String
                
                f_directive1 : RefDirectiveObjectType
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
            
            
            type RefByUnionOnly1 { f : ID}
            type RefByUnionOnly2 { f : ID}
            
            union RefUnion2 = RefByUnionOnly1 | RefByUnionOnly2
            
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
            
            
            directive @RefArgDirective on ARGUMENT_DEFINITION
            directive @RefFieldDirective on FIELD_DEFINITION
            directive @RefInputFieldDirective on INPUT_FIELD_DEFINITION
            directive @RefInputTypeDirective on INPUT_OBJECT
            directive @RefObjectTypeDirective(arg : RefDirectiveInputType) on OBJECT
            
            type RefDirectiveObjectType @RefObjectTypeDirective { 
                f(arg : ID @RefArgDirective) : ID @RefFieldDirective 
            }
            
            input RefDirectiveInputType @RefInputTypeDirective {
                f : ID @RefInputFieldDirective
            }
            

            directive @UnRefFieldDirective(arg : UnRefDirectiveInputType) on FIELD_DEFINITION
            directive @UnRefInputTypeDirective  on INPUT_OBJECT
            
            input UnRefDirectiveInputType @UnRefInputTypeDirective {
                f : ID
            }
            
            ### hanging elements
            
            directive @UnRefHangingArgDirective(arg : UnRefHangingInputType3)  on ARGUMENT_DEFINITION
            
            type UnRefHangingType { 
                f(arg : UnRefHangingInputType @UnRefHangingArgDirective)  : UnRefHangingType2
            }

            type UnRefHangingType2 {
                f : UnRefHangingType2
            }
            
            input UnRefHangingInputType {
                f : UnRefHangingInputType2
            }

            input UnRefHangingInputType2 {
                f : UnRefHangingInputType
            }

            input UnRefHangingInputType3 {
                f : ID
            }
        '''

    def "can get references"() {

        def schema = TestUtil.schema(sdl)

        when:
        def schemaUsage = SchemaUsageSupport.getSchemaUsage(schema)
        then:
        schemaUsage.isStronglyReferenced(schema, "Ref1")
        schemaUsage.isStronglyReferenced(schema, "Ref2")
        schemaUsage.isStronglyReferenced(schema, "IRef1")
        schemaUsage.isStronglyReferenced(schema, "IRef2")
        schemaUsage.isStronglyReferenced(schema, "Floating1")
        schemaUsage.isStronglyReferenced(schema, "Floating2")
        schemaUsage.isStronglyReferenced(schema, "Floating3")

        schemaUsage.isStronglyReferenced(schema, "RefUnion1")

        schemaUsage.isStronglyReferenced(schema, "RefUnion2")
        schemaUsage.isStronglyReferenced(schema, "RefByUnionOnly1")
        schemaUsage.isStronglyReferenced(schema, "RefByUnionOnly2")

        schemaUsage.isStronglyReferenced(schema, "RefInput1")
        schemaUsage.isStronglyReferenced(schema, "RefInput2")
        schemaUsage.isStronglyReferenced(schema, "RefInput3")
        schemaUsage.isStronglyReferenced(schema, "RefInput4")

        schemaUsage.isStronglyReferenced(schema, "ID")
        schemaUsage.isStronglyReferenced(schema, "String")
        schemaUsage.isStronglyReferenced(schema, "Int")
        schemaUsage.isStronglyReferenced(schema, "__Type")
        schemaUsage.isStronglyReferenced(schema, "__Schema")

        schemaUsage.isStronglyReferenced(schema, "RefDirectiveObjectType")
        schemaUsage.isStronglyReferenced(schema, "RefDirectiveInputType")
        schemaUsage.isStronglyReferenced(schema, "RefInputTypeDirective")
        schemaUsage.isStronglyReferenced(schema, "RefFieldDirective")
        schemaUsage.isStronglyReferenced(schema, "RefArgDirective")
        schemaUsage.isStronglyReferenced(schema, "RefInputFieldDirective")


        !schemaUsage.isStronglyReferenced(schema, "UnRef1")
        !schemaUsage.isStronglyReferenced(schema, "UnIRef1")
        !schemaUsage.isStronglyReferenced(schema, "UnRefEnum1")
        !schemaUsage.isStronglyReferenced(schema, "UnRefInput1")

        !schemaUsage.isStronglyReferenced(schema, "UnRefFieldDirective")
        !schemaUsage.isStronglyReferenced(schema, "UnRefInputTypeDirective")
        !schemaUsage.isStronglyReferenced(schema, "UnRefDirectiveInputType")

        schemaUsage.getUnReferencedElements(schema).collect { it.name }.sort() ==
                ["UnIRef1", "UnRef1", "UnRefDirectiveInputType", "UnRefEnum1",
                 "UnRefHangingInputType", "UnRefHangingInputType2", "UnRefHangingInputType3",
                 "UnRefHangingType", "UnRefHangingType2", "UnRefInput1",
                 "UnRefFieldDirective", "UnRefInputTypeDirective", "UnRefHangingArgDirective"].sort()
    }

    def "can record counts"() {
        def schema = TestUtil.schema(sdl)

        when:
        def schemaUsage = SchemaUsageSupport.getSchemaUsage(schema)

        then:
        schemaUsage.getOutputFieldReferenceCounts()["Ref1"] == 1
        schemaUsage.getFieldReferenceCounts()["Ref1"] == 1
        schemaUsage.getUnionReferenceCounts()["Ref1"] == 2

        schemaUsage.getInputFieldReferenceCounts()["Ref1"] == null
        schemaUsage.getInputFieldReferenceCounts()["RefInput1"] == 3

        schemaUsage.getFieldReferenceCounts()["RefInput1"] == 3
        schemaUsage.getArgumentReferenceCounts()["RefInput1"] == 1

        schemaUsage.getFieldReferenceCounts()["UnRef1"] == null
        schemaUsage.getFieldReferenceCounts()["UnRefInput1"] == null
        schemaUsage.getDirectiveReferenceCounts()["UnRefFieldDirective"] == null

    }

    def "can handle hanging elements"() {
        // hanging elements are elements that reference other elements
        // but ultimately dont lead to the root types

        def schema = TestUtil.schema(sdl)

        when:
        def schemaUsage = SchemaUsageSupport.getSchemaUsage(schema)

        then:
        !schemaUsage.isStronglyReferenced(schema, "UnRefHangingType")
        !schemaUsage.isStronglyReferenced(schema, "UnRefHangingType2")
        !schemaUsage.isStronglyReferenced(schema, "UnRefHangingType3")
        !schemaUsage.isStronglyReferenced(schema, "UnRefHangingInputType")
        !schemaUsage.isStronglyReferenced(schema, "UnRefHangingInputType2")
        !schemaUsage.isStronglyReferenced(schema, "UnRefHangingInputType3")
        !schemaUsage.isStronglyReferenced(schema, "UnRefHangingArgDirective")

        // 2 because of the dual nature of directives and applied directives
        schemaUsage.getDirectiveReferenceCounts()["UnRefHangingArgDirective"] == 2
        schemaUsage.getArgumentReferenceCounts()["UnRefHangingInputType"] == 1
        schemaUsage.getFieldReferenceCounts()["UnRefHangingType2"] == 2
        schemaUsage.getArgumentReferenceCounts()["UnRefHangingInputType3"] == 3
    }

    def "can handle cleared directives"() {
        // https://github.com/graphql-java/graphql-java/issues/3267


        def schema = TestUtil.schema(sdl)
        schema = new SchemaTransformer().transform(schema, new GraphQLSchemaVisitor() {

            @Override
            GraphQLSchemaTraversalControl visitFieldDefinition(GraphQLFieldDefinition fieldDef, GraphQLSchemaVisitor.FieldDefinitionVisitorEnvironment env) {
                if (fieldDef.getAppliedDirective("RefFieldDirective") != null) {
                    List<GraphQLAppliedDirective> directives = fieldDef.getAppliedDirectives();
                    fieldDef = fieldDef.transform(
                            f -> f.clearDirectives().replaceAppliedDirectives(directives)
                    )
                }
                return env.changeNode(fieldDef)
            }
        }.toTypeVisitor())

        when:
        def schemaUsage = SchemaUsageSupport.getSchemaUsage(schema)
        then:
        schemaUsage.isStronglyReferenced(schema, "RefFieldDirective")
    }
}
