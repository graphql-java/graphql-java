package graphql.schema.diffing.ana

import graphql.TestUtil
import graphql.schema.diffing.SchemaDiffing
import spock.lang.Specification

class EditOperationAnalyzerAppliedDirectivesTest extends Specification{

    def "interface field applied directive argument deleted"() {
        given:
        def oldSdl = '''
        directive @d(arg1:String) on FIELD_DEFINITION
        
        type Query implements I{
            foo: String 
        }
        interface I {
            foo: String @d(arg1: "foo")
        }
        '''
        def newSdl = '''
        directive @d(arg1:String) on FIELD_DEFINITION
        
        type Query implements I{
            foo: String 
        }
        interface I {
            foo: String @d
        }
        '''
        when:
        def changes = calcDiff(oldSdl, newSdl)
        then:
        changes.interfaceDifferences["I"] instanceof SchemaDifference.InterfaceModification
        def argumentDeletions = (changes.interfaceDifferences["I"] as SchemaDifference.InterfaceModification).getDetails(SchemaDifference.AppliedDirectiveArgumentDeletion)
        def location = argumentDeletions[0].locationDetail as SchemaDifference.AppliedDirectiveInterfaceFieldLocation
        location.interfaceName == "I"
        location.fieldName == "foo"
        argumentDeletions[0].argumentName == "arg1"
    }

    def "input object field added applied directive"() {
        given:
        def oldSdl = '''
        directive @d(arg:String) on INPUT_FIELD_DEFINITION
        input I {
            a: String
        }
        type Query {
            foo(arg: I): String
        }
        '''
        def newSdl = '''
        directive @d(arg:String) on INPUT_FIELD_DEFINITION
        input I {
            a: String @d(arg: "foo")
        }
        type Query {
            foo(arg: I): String
        }
        '''
        when:
        def changes = calcDiff(oldSdl, newSdl)
        then:
        changes.inputObjectDifferences["I"] instanceof SchemaDifference.InputObjectModification
        def appliedDirective = (changes.inputObjectDifferences["I"] as SchemaDifference.InputObjectModification).getDetails(SchemaDifference.AppliedDirectiveAddition)
        (appliedDirective[0].locationDetail as SchemaDifference.AppliedDirectiveInputObjectFieldLocation).inputObjectName == "I"
        (appliedDirective[0].locationDetail as SchemaDifference.AppliedDirectiveInputObjectFieldLocation).fieldName == "a"
        appliedDirective[0].name == "d"
    }

    def "object field added applied directive"() {
        given:
        def oldSdl = '''
        directive @d(arg:String) on FIELD_DEFINITION
        
        type Query {
            foo: String 
        }
        '''
        def newSdl = '''
        directive @d(arg: String)  on FIELD_DEFINITION
        
        type Query {
            foo: String @d(arg: "foo")
        }
        '''
        when:
        def changes = calcDiff(oldSdl, newSdl)
        then:
        changes.objectDifferences["Query"] instanceof SchemaDifference.ObjectModification
        def appliedDirective = (changes.objectDifferences["Query"] as SchemaDifference.ObjectModification).getDetails(SchemaDifference.AppliedDirectiveAddition)
        (appliedDirective[0].locationDetail as SchemaDifference.AppliedDirectiveObjectFieldLocation).objectName == "Query"
        (appliedDirective[0].locationDetail as SchemaDifference.AppliedDirectiveObjectFieldLocation).fieldName == "foo"
        appliedDirective[0].name == "d"
    }

    def "object field applied directive argument value changed"() {
        given:
        def oldSdl = '''
        directive @d(arg:String) on FIELD_DEFINITION
        
        type Query {
            foo: String @d(arg: "foo1")
        }
        '''
        def newSdl = '''
        directive @d(arg: String)  on FIELD_DEFINITION
        
        type Query {
            foo: String @d(arg: "foo2")
        }
        '''
        when:
        def changes = calcDiff(oldSdl, newSdl)
        then:
        changes.objectDifferences["Query"] instanceof SchemaDifference.ObjectModification
        def argumentValueModifications = (changes.objectDifferences["Query"] as SchemaDifference.ObjectModification).getDetails(SchemaDifference.AppliedDirectiveArgumentValueModification)
        (argumentValueModifications[0].locationDetail as SchemaDifference.AppliedDirectiveObjectFieldLocation).objectName == "Query"
        (argumentValueModifications[0].locationDetail as SchemaDifference.AppliedDirectiveObjectFieldLocation).fieldName == "foo"
        argumentValueModifications[0].argumentName == "arg"
        argumentValueModifications[0].oldValue == '"foo1"'
        argumentValueModifications[0].newValue == '"foo2"'
    }

    def "object field applied directive argument name changed"() {
        given:
        def oldSdl = '''
        directive @d(arg1:String, arg2: String) on FIELD_DEFINITION
        
        type Query {
            foo: String @d(arg1: "foo")
        }
        '''
        def newSdl = '''
        directive @d(arg1: String, arg2: String)  on FIELD_DEFINITION
        
        type Query {
            foo: String @d(arg2: "foo")
        }
        '''
        when:
        def changes = calcDiff(oldSdl, newSdl)
        then:
        changes.objectDifferences["Query"] instanceof SchemaDifference.ObjectModification
        def argumentRenames = (changes.objectDifferences["Query"] as SchemaDifference.ObjectModification).getDetails(SchemaDifference.AppliedDirectiveArgumentRename)
        def location = argumentRenames[0].locationDetail as SchemaDifference.AppliedDirectiveObjectFieldLocation
        location.objectName == "Query"
        location.fieldName == "foo"
        argumentRenames[0].oldName == "arg1"
        argumentRenames[0].newName == "arg2"
    }

    def "object field applied directive argument deleted"() {
        given:
        def oldSdl = '''
        directive @d(arg1:String) on FIELD_DEFINITION
        
        type Query {
            foo: String @d(arg1: "foo")
        }
        '''
        def newSdl = '''
        directive @d(arg1: String)  on FIELD_DEFINITION
        
        type Query {
            foo: String @d
        }
        '''
        when:
        def changes = calcDiff(oldSdl, newSdl)
        then:
        changes.objectDifferences["Query"] instanceof SchemaDifference.ObjectModification
        def argumentDeletions = (changes.objectDifferences["Query"] as SchemaDifference.ObjectModification).getDetails(SchemaDifference.AppliedDirectiveArgumentDeletion)
        def location = argumentDeletions[0].locationDetail as SchemaDifference.AppliedDirectiveObjectFieldLocation
        location.objectName == "Query"
        location.fieldName == "foo"
        argumentDeletions[0].argumentName == "arg1"
    }

    def "input object added applied directive"() {
        given:
        def oldSdl = '''
        directive @d(arg:String) on INPUT_OBJECT
        input I {
            a: String
        }
        type Query {
            foo(arg: I): String 
        }
        '''
        def newSdl = '''
        directive @d(arg:String) on INPUT_OBJECT
        input I @d(arg: "foo") {
            a: String
        }
        type Query {
            foo(arg: I): String 
        }
        '''
        when:
        def changes = calcDiff(oldSdl, newSdl)
        then:
        changes.inputObjectDifferences["I"] instanceof SchemaDifference.InputObjectModification
        def appliedDirective = (changes.inputObjectDifferences["I"] as SchemaDifference.InputObjectModification).getDetails(SchemaDifference.AppliedDirectiveAddition)
        (appliedDirective[0].locationDetail as SchemaDifference.AppliedDirectiveInputObjectLocation).name == "I"
        appliedDirective[0].name == "d"
    }

    def "object added applied directive"() {
        given:
        def oldSdl = '''
        directive @d(arg:String) on OBJECT
        
        type Query {
            foo: String 
        }
        '''
        def newSdl = '''
        directive @d(arg: String)  on OBJECT
        
        type Query @d(arg: "foo") {
            foo: String 
        }
        '''
        when:
        def changes = calcDiff(oldSdl, newSdl)
        then:
        changes.objectDifferences["Query"] instanceof SchemaDifference.ObjectModification
        def appliedDirective = (changes.objectDifferences["Query"] as SchemaDifference.ObjectModification).getDetails(SchemaDifference.AppliedDirectiveAddition)
        (appliedDirective[0].locationDetail as SchemaDifference.AppliedDirectiveObjectLocation).name == "Query"
        appliedDirective[0].name == "d"
    }

    def "interface added applied directive"() {
        given:
        def oldSdl = '''
        directive @d(arg:String) on INTERFACE
        
        type Query implements I{
            foo: String 
        }
        interface I {
            foo: String
        }
        '''
        def newSdl = '''
        directive @d(arg: String) on INTERFACE
        
        type Query implements I {
            foo: String 
        }
        interface I @d(arg: "foo") {
            foo: String
        }
        '''
        when:
        def changes = calcDiff(oldSdl, newSdl)
        then:
        changes.interfaceDifferences["I"] instanceof SchemaDifference.InterfaceModification
        def appliedDirective = (changes.interfaceDifferences["I"] as SchemaDifference.InterfaceModification).getDetails(SchemaDifference.AppliedDirectiveAddition)
        (appliedDirective[0].locationDetail as SchemaDifference.AppliedDirectiveInterfaceLocation).name == "I"
        appliedDirective[0].name == "d"
    }

    def "union added applied directive"() {
        given:
        def oldSdl = '''
        directive @d(arg: String) on UNION
        type Query {
            foo: U 
        }
        union U = A | B
        type A { a: String }
        type B { b: String }
        '''
        def newSdl = '''
        directive @d(arg: String) on UNION
        type Query {
            foo: U 
        }
        union U @d(arg: "foo") = A | B
        type A { a: String }
        type B { b: String }
        '''
        when:
        def changes = calcDiff(oldSdl, newSdl)
        then:
        changes.unionDifferences["U"] instanceof SchemaDifference.UnionModification
        def appliedDirective = (changes.unionDifferences["U"] as SchemaDifference.UnionModification).getDetails(SchemaDifference.AppliedDirectiveAddition)
        (appliedDirective[0].locationDetail as SchemaDifference.AppliedDirectiveUnionLocation).name == "U"
        appliedDirective[0].name == "d"
    }

    def "scalar added applied directive"() {
        given:
        def oldSdl = '''
        directive @d(arg:String) on SCALAR
        scalar DateTime  
        type Query {
            foo: DateTime 
        }
        '''
        def newSdl = '''
        directive @d(arg:String) on SCALAR
        scalar DateTime  @d(arg: "foo")
        type Query {
            foo: DateTime 
        }
        '''
        when:
        def changes = calcDiff(oldSdl, newSdl)
        then:
        changes.scalarDifferences["DateTime"] instanceof SchemaDifference.ScalarModification
        def appliedDirective = (changes.scalarDifferences["DateTime"] as SchemaDifference.ScalarModification).getDetails(SchemaDifference.AppliedDirectiveAddition)
        (appliedDirective[0].locationDetail as SchemaDifference.AppliedDirectiveScalarLocation).name == "DateTime"
        appliedDirective[0].name == "d"
    }

    def "enum added applied directive"() {
        given:
        def oldSdl = '''
        directive @d(arg:String) on ENUM
        enum E { A, B }
        type Query {
            foo: E 
        }
        '''
        def newSdl = '''
        directive @d(arg:String) on ENUM
        enum E @d(arg: "foo") { A, B }
        type Query {
            foo: E 
        }
        '''
        when:
        def changes = calcDiff(oldSdl, newSdl)
        then:
        changes.enumDifferences["E"] instanceof SchemaDifference.EnumModification
        def appliedDirective = (changes.enumDifferences["E"] as SchemaDifference.EnumModification).getDetails(SchemaDifference.AppliedDirectiveAddition)
        (appliedDirective[0].locationDetail as SchemaDifference.AppliedDirectiveEnumLocation).name == "E"
        appliedDirective[0].name == "d"
    }

    def "enum value added applied directive"() {
        given:
        def oldSdl = '''
        directive @d(arg:String) on ENUM_VALUE
        enum E { A, B }
        type Query {
            foo: E 
        }
        '''
        def newSdl = '''
        directive @d(arg:String) on ENUM_VALUE
        enum E  { A, B @d(arg: "foo") }
        type Query {
            foo: E 
        }
        '''
        when:
        def changes = calcDiff(oldSdl, newSdl)
        then:
        changes.enumDifferences["E"] instanceof SchemaDifference.EnumModification
        def appliedDirective = (changes.enumDifferences["E"] as SchemaDifference.EnumModification).getDetails(SchemaDifference.AppliedDirectiveAddition)
        (appliedDirective[0].locationDetail as SchemaDifference.AppliedDirectiveEnumValueLocation).enumName == "E"
        (appliedDirective[0].locationDetail as SchemaDifference.AppliedDirectiveEnumValueLocation).valueName == "B"
        appliedDirective[0].name == "d"
    }






    EditOperationAnalysisResult calcDiff(
            String oldSdl,
            String newSdl
    ) {
        def oldSchema = TestUtil.schema(oldSdl)
        def newSchema = TestUtil.schema(newSdl)
        def changes = new SchemaDiffing().diffAndAnalyze(oldSchema, newSchema)
        return changes
    }

}
