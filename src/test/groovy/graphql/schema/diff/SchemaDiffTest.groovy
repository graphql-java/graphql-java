package graphql.schema.diff

import graphql.AssertException
import graphql.TestUtil
import graphql.language.Argument
import graphql.language.Directive
import graphql.language.IntValue
import graphql.language.ListType
import graphql.language.NonNullType
import graphql.language.ObjectTypeDefinition
import graphql.language.StringValue
import graphql.language.Type
import graphql.language.TypeDefinition
import graphql.language.TypeKind
import graphql.language.TypeName
import graphql.schema.Coercing
import graphql.schema.DataFetcher
import graphql.schema.GraphQLScalarType
import graphql.schema.GraphQLSchema
import graphql.schema.PropertyDataFetcher
import graphql.schema.TypeResolver
import graphql.schema.diff.reporting.CapturingReporter
import graphql.schema.diff.reporting.ChainedReporter
import graphql.schema.diff.reporting.PrintStreamReporter
import graphql.schema.idl.FieldWiringEnvironment
import graphql.schema.idl.InterfaceWiringEnvironment
import graphql.schema.idl.RuntimeWiring
import graphql.schema.idl.UnionWiringEnvironment
import graphql.schema.idl.WiringFactory
import spock.lang.Specification

import java.util.stream.Collectors

class SchemaDiffTest extends Specification {
    private CapturingReporter introspectionReporter
    private CapturingReporter sdlReporter
    private ChainedReporter introspectionChainedReporter
    private ChainedReporter sdlChainedReporter

    private static final TypeResolver NULL_TYPE_RESOLVER = { env -> null }

    static GraphQLScalarType CUSTOM_SCALAR = GraphQLScalarType
            .newScalar()
            .name("CustomScalar")
            .description("CustomScalar")
            .coercing(new Coercing() {
                @Override
                Object serialize(Object dataFetcherResult) {
                    throw new UnsupportedOperationException("Not implemented")
                }

                @Override
                Object parseValue(Object input) {
                    throw new UnsupportedOperationException("Not implemented")
                }

                @Override
                Object parseLiteral(Object input) {
                    throw new UnsupportedOperationException("Not implemented")
                }
            })
            .build()

    static RuntimeWiring wireWithNoFetching() {

        return RuntimeWiring.newRuntimeWiring()
                .wiringFactory(new WiringFactory() {

                    @Override
                    boolean providesTypeResolver(UnionWiringEnvironment environment) {
                        return true
                    }

                    @Override
                    boolean providesTypeResolver(InterfaceWiringEnvironment environment) {
                        return true
                    }

                    @Override
                    TypeResolver getTypeResolver(InterfaceWiringEnvironment environment) {
                        return NULL_TYPE_RESOLVER
                    }

                    @Override
                    TypeResolver getTypeResolver(UnionWiringEnvironment environment) {
                        return NULL_TYPE_RESOLVER
                    }

                    @Override
                    DataFetcher getDefaultDataFetcher(FieldWiringEnvironment environment) {
                        return new PropertyDataFetcher(environment.getFieldDefinition().getName())
                    }
                })
                .scalar(CUSTOM_SCALAR)
                .build()
    }

    void setup() {
        introspectionReporter = new CapturingReporter()
        sdlReporter = new CapturingReporter()
        introspectionChainedReporter = new ChainedReporter(introspectionReporter, new PrintStreamReporter())
        sdlChainedReporter = new ChainedReporter(sdlReporter, new PrintStreamReporter())
    }

    void compareDiff(String newFile) {
        SchemaDiffSet introspectionSchemaDiffSet = introspectionSchemaDiffSet(newFile)
        SchemaDiffSet sdlSchemaDiffSet = sdlSchemaDiffSet(newFile)

        def diff = new SchemaDiff()
        diff.diffSchema(introspectionSchemaDiffSet, introspectionChainedReporter)
        diff.diffSchema(sdlSchemaDiffSet, sdlChainedReporter)
    }

    void compareDiff(GraphQLSchema oldSchema, GraphQLSchema newSchema) {
        SchemaDiffSet introspectionSchemaDiffSet = SchemaDiffSet.diffSetFromIntrospection(oldSchema, newSchema)
        SchemaDiffSet sdlSchemaDiffSet = SchemaDiffSet.diffSetFromSdl(oldSchema, newSchema)

        def diff = new SchemaDiff()
        diff.diffSchema(introspectionSchemaDiffSet, introspectionChainedReporter)
        diff.diffSchema(sdlSchemaDiffSet, sdlChainedReporter)
    }

    void validateReportersAreEqual() {
        introspectionReporter.events == sdlReporter.events
        introspectionReporter.infos == sdlReporter.infos
        introspectionReporter.dangers == sdlReporter.dangers
        introspectionReporter.breakages == sdlReporter.breakages
        introspectionReporter.breakageCount == sdlReporter.breakageCount
        introspectionReporter.infoCount == sdlReporter.infoCount
        introspectionReporter.dangerCount == sdlReporter.dangerCount
    }

    SchemaDiffSet introspectionSchemaDiffSet(String newFile) {
        def schemaOld = TestUtil.schemaFile("diff/" + "schema_ABaseLine.graphqls", wireWithNoFetching())
        def schemaNew = TestUtil.schemaFile("diff/" + newFile, wireWithNoFetching())

        def diffSet = SchemaDiffSet.diffSetFromIntrospection(schemaOld, schemaNew)
        diffSet
    }

    SchemaDiffSet sdlSchemaDiffSet(String newFile) {
        def schemaOld = TestUtil.schemaFile("diff/" + "schema_ABaseLine.graphqls", wireWithNoFetching())
        def schemaNew = TestUtil.schemaFile("diff/" + newFile, wireWithNoFetching())

        def diffSet = SchemaDiffSet.diffSetFromSdl(schemaOld, schemaNew)
        diffSet
    }

    def "change_in_null_ness_input_or_arg"() {

        given:
        Type baseLine = new NonNullType(new ListType(new TypeName("foo")))
        Type same = new NonNullType(new ListType(new TypeName("foo")))

        Type less = new ListType(new TypeName("foo"))


        def diff = new SchemaDiff()

        def sameType = diff.checkTypeWithNonNullAndListOnInputOrArg(baseLine, same)

        def lessStrict = diff.checkTypeWithNonNullAndListOnInputOrArg(baseLine, less)

        // not allowed as old clients wont work
        def moreStrict = diff.checkTypeWithNonNullAndListOnInputOrArg(less, baseLine)


        expect:
        sameType == null
        lessStrict == null
        moreStrict == DiffCategory.STRICTER
    }

    def "change_in_null_ness_object_or_interface"() {

        given:
        Type nonNull = new NonNullType(new ListType(new TypeName("foo")))
        Type nonNullDuplicate = new NonNullType(new ListType(new TypeName("foo")))

        Type nullable = new ListType(new TypeName("foo"))


        def diff = new SchemaDiff()

        def sameType = diff.checkTypeWithNonNullAndListOnObjectOrInterface(nonNull, nonNullDuplicate)

        def removeGuarantee = diff.checkTypeWithNonNullAndListOnObjectOrInterface(nonNull, nullable)

        def addGuarantee = diff.checkTypeWithNonNullAndListOnObjectOrInterface(nullable, nonNull)


        expect:
        sameType == null
        removeGuarantee == DiffCategory.STRICTER
        addGuarantee == null
    }

    def "change_in_list_ness_input_or_arg"() {

        given:
        Type list = new NonNullType(new ListType(new TypeName("foo")))
        Type notList = new NonNullType(new TypeName("foo"))

        def diff = new SchemaDiff()

        def noLongerList = diff.checkTypeWithNonNullAndListOnInputOrArg(list, notList)
        def nowList = diff.checkTypeWithNonNullAndListOnInputOrArg(notList, list)

        expect:
        noLongerList == DiffCategory.INVALID
        nowList == DiffCategory.INVALID
    }

    def "change_in_list_ness_object_or_interface"() {

        given:
        Type list = new NonNullType(new ListType(new TypeName("foo")))
        Type notList = new NonNullType(new TypeName("foo"))

        def diff = new SchemaDiff()

        def noLongerList = diff.checkTypeWithNonNullAndListOnObjectOrInterface(list, notList)
        def nowList = diff.checkTypeWithNonNullAndListOnObjectOrInterface(list, notList)

        expect:
        noLongerList == DiffCategory.INVALID
        nowList == DiffCategory.INVALID
    }

    DiffEvent lastBreakage(CapturingReporter capturingReporter) {
        def breakages = capturingReporter.getBreakages()
        breakages.size() == 0 ? null : breakages.get(breakages.size() - 1)
    }

    def "directives_controlled_via_options"() {

        given:
        DiffCtx ctx = new DiffCtx(introspectionReporter, null, null)

        TypeDefinition left = new ObjectTypeDefinition("fooType")

        def oneDirective = [new Directive("bar")]
        def twoDirectives = [new Directive("foo"), new Directive("bar")]

        def diff = new SchemaDiff()
        diff.checkDirectives(ctx, left, twoDirectives, oneDirective)
        def notChecked = lastBreakage(introspectionReporter)

        diff = new SchemaDiff(SchemaDiff.Options.defaultOptions().enforceDirectives())
        diff.checkDirectives(ctx, left, twoDirectives, oneDirective)
        def missingDirective = lastBreakage(introspectionReporter)

        expect:
        notChecked == null
        missingDirective.category == DiffCategory.MISSING
    }


    def "directives enforced to be the same"() {

        given:
        DiffCtx ctx = new DiffCtx(introspectionReporter, null, null)

        TypeDefinition left = new ObjectTypeDefinition("fooType")


        def oneDirective = [new Directive("bar")]
        def twoDirectives = [new Directive("foo"), new Directive("bar")]

        def diff = new SchemaDiff(SchemaDiff.Options.defaultOptions().enforceDirectives())

        diff.checkDirectives(ctx, left, twoDirectives, oneDirective)
        def missingDirective = lastBreakage(introspectionReporter)

        def oldDirective = new Directive("foo", [
                new Argument("arg1", new StringValue("p1")),
                new Argument("arg2", new StringValue("p1")),
        ])

        def newDirective = new Directive("foo", [
                new Argument("arg1", new StringValue("p1")),
                new Argument("arg3", new StringValue("p1")),
        ])

        diff.checkDirectives(ctx, left, [oldDirective], [newDirective])
        def missingArgs = lastBreakage(introspectionReporter)


        def newDirectiveDiffDefaultType = new Directive("foo", [
                new Argument("arg1", new StringValue("p1")),
                new Argument("arg2", new IntValue(new BigInteger("123"))),
        ])

        diff.checkDirectives(ctx, left, [oldDirective], [newDirectiveDiffDefaultType])
        def changedType = lastBreakage(introspectionReporter)

        expect:
        missingDirective.category == DiffCategory.MISSING
        missingArgs.category == DiffCategory.MISSING
        changedType.category == DiffCategory.INVALID
        introspectionReporter.getBreakageCount() == 3
    }

    def "same schema diff"() {
        compareDiff("schema_ABaseLine.graphqls")

        expect:
        validateReportersAreEqual()
        introspectionReporter.breakageCount == 0
    }

    def "additional field"() {
        compareDiff("schema_with_additional_field.graphqls")

        expect:
        validateReportersAreEqual()
        introspectionReporter.breakageCount == 0

        List<DiffEvent> newFieldEvents = introspectionReporter.infos.stream()
                .filter { de -> de.typeName == "Ainur" && de.fieldName == "surname" }
                .collect(Collectors.toList())

        newFieldEvents.size() == 2

        newFieldEvents[0].level == DiffLevel.INFO
        newFieldEvents[1].level == DiffLevel.INFO
        newFieldEvents[1].category == DiffCategory.ADDITION
    }

    def "missing fields on interface"() {
        compareDiff("schema_interface_fields_missing.graphqls")

        expect:
        validateReportersAreEqual()
        introspectionReporter.breakageCount == 8 // 2 fields removed from interface, affecting 3 types
        introspectionReporter.breakages[0].category == DiffCategory.MISSING
        introspectionReporter.breakages[0].typeKind == TypeKind.Interface

        introspectionReporter.breakages[1].category == DiffCategory.MISSING
        introspectionReporter.breakages[1].typeKind == TypeKind.Interface
    }

    def "missing members on union"() {
        compareDiff("schema_missing_union_members.graphqls")

        expect:
        validateReportersAreEqual()
        introspectionReporter.breakageCount == 1 // 1 member removed
        introspectionReporter.breakages[0].category == DiffCategory.MISSING
        introspectionReporter.breakages[0].typeKind == TypeKind.Union
    }

    def "missing fields on object"() {
        compareDiff("schema_missing_object_fields.graphqls")

        expect:
        validateReportersAreEqual()
        introspectionReporter.breakageCount == 2 // 2 fields removed
        introspectionReporter.breakages[0].category == DiffCategory.MISSING
        introspectionReporter.breakages[0].typeKind == TypeKind.Object
        introspectionReporter.breakages[0].fieldName == 'colour'

        introspectionReporter.breakages[1].category == DiffCategory.MISSING
        introspectionReporter.breakages[1].typeKind == TypeKind.Object
        introspectionReporter.breakages[1].fieldName == 'temperament'

    }

    def "missing operation"() {
        compareDiff("schema_missing_operation.graphqls")

        expect:
        validateReportersAreEqual()
        introspectionReporter.breakageCount == 1
        introspectionReporter.breakages[0].category == DiffCategory.MISSING
        introspectionReporter.breakages[0].typeKind == TypeKind.Operation
        introspectionReporter.breakages[0].typeName == 'Mutation'
    }

    def "missing input object fields"() {
        compareDiff("schema_missing_input_object_fields.graphqls")

        expect:
        validateReportersAreEqual()
        introspectionReporter.breakageCount == 1
        introspectionReporter.breakages[0].category == DiffCategory.MISSING
        introspectionReporter.breakages[0].typeKind == TypeKind.InputObject
        introspectionReporter.breakages[0].fieldName == 'queryTarget'

    }

    def "changed nested input object field types"() {
        compareDiff("schema_changed_nested_input_object_fields.graphqls")

        expect:
        validateReportersAreEqual()
        introspectionReporter.breakageCount == 1
        introspectionReporter.breakages[0].category == DiffCategory.INVALID
        introspectionReporter.breakages[0].typeName == 'NestedInput'
        introspectionReporter.breakages[0].typeKind == TypeKind.InputObject
        introspectionReporter.breakages[0].fieldName == 'nestedInput'
    }

    def "changed input object field types"() {
        compareDiff("schema_changed_input_object_fields.graphqls")

        expect:
        validateReportersAreEqual()
        introspectionReporter.breakageCount == 4
        introspectionReporter.breakages[0].category == DiffCategory.STRICTER
        introspectionReporter.breakages[0].typeName == 'Query'
        introspectionReporter.breakages[0].typeKind == TypeKind.Object
        introspectionReporter.breakages[0].fieldName == 'being'

        introspectionReporter.breakages[1].category == DiffCategory.STRICTER
        introspectionReporter.breakages[1].typeName == 'Questor'
        introspectionReporter.breakages[1].typeKind == TypeKind.InputObject
        introspectionReporter.breakages[1].fieldName == 'nestedInput'

        introspectionReporter.breakages[2].category == DiffCategory.INVALID
        introspectionReporter.breakages[2].typeName == 'Questor'
        introspectionReporter.breakages[2].typeKind == TypeKind.InputObject
        introspectionReporter.breakages[2].fieldName == 'queryTarget'

        introspectionReporter.breakages[3].category == DiffCategory.STRICTER
        introspectionReporter.breakages[3].typeName == 'Questor'
        introspectionReporter.breakages[3].typeKind == TypeKind.InputObject
        introspectionReporter.breakages[3].fieldName == 'newMandatoryField'

    }

    def "changed type kind"() {
        compareDiff("schema_changed_type_kind.graphqls")

        expect:
        validateReportersAreEqual()
        introspectionReporter.breakageCount == 1
        introspectionReporter.breakages[0].category == DiffCategory.INVALID
        introspectionReporter.breakages[0].typeName == 'Character'
        introspectionReporter.breakages[0].typeKind == TypeKind.Union
    }

    def "missing object field args"() {
        compareDiff("schema_missing_field_arguments.graphqls")

        expect:
        validateReportersAreEqual()
        introspectionReporter.breakageCount == 2

        introspectionReporter.breakages[0].category == DiffCategory.MISSING
        introspectionReporter.breakages[0].typeKind == TypeKind.Object
        introspectionReporter.breakages[0].typeName == "Mutation"
        introspectionReporter.breakages[0].fieldName == 'being'
        introspectionReporter.breakages[0].components.contains("questor")

        introspectionReporter.breakages[1].category == DiffCategory.MISSING
        introspectionReporter.breakages[1].typeKind == TypeKind.Object
        introspectionReporter.breakages[0].typeName == "Mutation"
        introspectionReporter.breakages[1].fieldName == 'sword'

    }

    def "missing enum value"() {
        compareDiff("schema_missing_enum_value.graphqls")

        expect:
        validateReportersAreEqual()
        introspectionReporter.breakageCount == 1
        introspectionReporter.breakages[0].category == DiffCategory.MISSING
        introspectionReporter.breakages[0].typeKind == TypeKind.Enum
        introspectionReporter.breakages[0].typeName == 'Temperament'
        introspectionReporter.breakages[0].components.contains("Duplicitous")

    }

    def "changed object field args"() {
        compareDiff("schema_changed_field_arguments.graphqls")

        expect:
        validateReportersAreEqual()
        introspectionReporter.breakageCount == 2
        introspectionReporter.breakages[0].category == DiffCategory.INVALID
        introspectionReporter.breakages[0].typeKind == TypeKind.Object
        introspectionReporter.breakages[0].fieldName == 'sword'

        introspectionReporter.breakages[1].category == DiffCategory.INVALID
        introspectionReporter.breakages[1].typeKind == TypeKind.Object
        introspectionReporter.breakages[1].fieldName == 'sword'

    }

    def "changed type on object"() {
        compareDiff("schema_changed_object_fields.graphqls")

        expect:
        validateReportersAreEqual()
        introspectionReporter.breakageCount == 3
        introspectionReporter.breakages[0].category == DiffCategory.STRICTER
        introspectionReporter.breakages[0].typeName == 'Istari'
        introspectionReporter.breakages[0].typeKind == TypeKind.Object
        introspectionReporter.breakages[0].fieldName == 'temperament'

        introspectionReporter.breakages[1].category == DiffCategory.INVALID
        introspectionReporter.breakages[1].typeName == 'Query'
        introspectionReporter.breakages[1].typeKind == TypeKind.Object
        introspectionReporter.breakages[1].fieldName == 'beings'

        introspectionReporter.breakages[2].category == DiffCategory.INVALID
        introspectionReporter.breakages[2].typeName == 'Query'
        introspectionReporter.breakages[2].typeKind == TypeKind.Object
        introspectionReporter.breakages[2].fieldName == 'customScalar'
    }

    def "dangerous changes"() {
        compareDiff("schema_dangerous_changes.graphqls")

        expect:
        validateReportersAreEqual()
        introspectionReporter.breakageCount == 0
        introspectionReporter.dangerCount == 3

        introspectionReporter.dangers[0].category == DiffCategory.ADDITION
        introspectionReporter.dangers[0].typeName == "Temperament"
        introspectionReporter.dangers[0].typeKind == TypeKind.Enum
        introspectionReporter.dangers[0].components.contains("Nonplussed")

        introspectionReporter.dangers[1].category == DiffCategory.ADDITION
        introspectionReporter.dangers[1].typeName == "Character"
        introspectionReporter.dangers[1].typeKind == TypeKind.Union
        introspectionReporter.dangers[1].components.contains("BenignFigure")

        introspectionReporter.dangers[2].category == DiffCategory.DIFFERENT
        introspectionReporter.dangers[2].typeName == "Query"
        introspectionReporter.dangers[2].typeKind == TypeKind.Object
        introspectionReporter.dangers[2].fieldName == "being"
        introspectionReporter.dangers[2].components.contains("type")
    }

    def "deprecated fields are unchanged"() {
        def schema = TestUtil.schemaFile("diff/" + "schema_deprecated_fields_new.graphqls", wireWithNoFetching())
        SchemaDiffSet introspectionSchemaDiffSet = SchemaDiffSet.diffSetFromIntrospection(schema, schema)
        SchemaDiffSet sdlSchemaDiffSet = SchemaDiffSet.diffSetFromSdl(schema, schema)

        def diff = new SchemaDiff()
        diff.diffSchema(introspectionSchemaDiffSet, introspectionChainedReporter)
        diff.diffSchema(sdlSchemaDiffSet, sdlChainedReporter)

        expect:
        validateReportersAreEqual()
        introspectionReporter.dangerCount == 0
        introspectionReporter.breakageCount == 0
    }

    def "field was deprecated"() {
        compareDiff("schema_deprecated_fields_new.graphqls")

        expect:
        validateReportersAreEqual()
        introspectionReporter.dangerCount == 14
        introspectionReporter.breakageCount == 0
        introspectionReporter.dangers.every {
            it.getCategory() == DiffCategory.DEPRECATION_ADDED
        }
    }

    def "deprecated field was removed"() {
        def schemaOld = TestUtil.schemaFile("diff/" + "schema_deprecated_fields_new.graphqls", wireWithNoFetching())
        def schemaNew = TestUtil.schemaFile("diff/" + "schema_deprecated_fields_removed.graphqls", wireWithNoFetching())

        SchemaDiffSet introspectionSchemaDiffSet = SchemaDiffSet.diffSetFromIntrospection(schemaOld, schemaNew)
        SchemaDiffSet sdlSchemaDiffSet = SchemaDiffSet.diffSetFromSdl(schemaOld, schemaNew)

        def diff = new SchemaDiff()
        diff.diffSchema(introspectionSchemaDiffSet, introspectionChainedReporter)
        diff.diffSchema(sdlSchemaDiffSet, sdlChainedReporter)

        expect:
        validateReportersAreEqual()
        introspectionReporter.dangerCount == 0
        introspectionReporter.breakageCount == 12
        introspectionReporter.breakages.every {
            it.getCategory() == DiffCategory.DEPRECATION_REMOVED
        }
    }

    def "union members are checked"() {
        def oldSchema = TestUtil.schema('''
        type Query {
            foo: Foo
        }
        union Foo = A | B 
        type A {
            a: String
            toRemove: String 
        }
        type B {
            b: String
        }
       ''')
        def newSchema = TestUtil.schema('''
        type Query {
            foo: Foo
        }
        union Foo = A | B 
        type A {
            a: String
        }
        type B {
            b: String
        }
       ''')

        when:
        compareDiff(oldSchema, newSchema)

        then:
        validateReportersAreEqual()
        introspectionReporter.dangerCount == 0
        introspectionReporter.breakageCount == 1
        introspectionReporter.breakages.every {
            it.getCategory() == DiffCategory.MISSING
        }

    }

    def "field renamed"() {
        def oldSchema = TestUtil.schema('''
        type Query {
            hello: String 
        }
       ''')
        def newSchema = TestUtil.schema('''
        type Query {
            hello2: String
        }
       ''')
        when:
        compareDiff(oldSchema, newSchema)

        then:
        // the old hello field is missing
        validateReportersAreEqual()
        introspectionReporter.breakageCount == 1
        introspectionReporter.breakages.every {
            it.getCategory() == DiffCategory.MISSING
        }
    }
    def "interface renamed"() {
        def oldSchema = TestUtil.schema('''
        type Query implements Hello{
            hello: String 
            world: World
        }
        type World implements Hello {
            hello: String
        }
        interface Hello {
            hello: String
        }
       ''')
        def newSchema = TestUtil.schema('''
        type Query implements Hello2{
            hello: String 
            world: World
        }
        type World implements Hello2 {
            hello: String
        }
        interface Hello2 {
            hello: String
        }
       ''')
        when:

        compareDiff(oldSchema, newSchema)

        then:
        // two breakages for World and Query not implementing Hello anymore
        validateReportersAreEqual()
        introspectionReporter.breakageCount == 2

    }

    def "SchemaDiff and CapturingReporter have the same diff counts"() {
        def schema1 = TestUtil.schema("type Query { f : String }")
        def schema2 = TestUtil.schema("type Query { f : Int }")

        when:
        def capturingReporter = new CapturingReporter()
        def schemaDiff = new SchemaDiff()
        def breakingCount = schemaDiff.diffSchema(SchemaDiffSet.diffSetFromIntrospection(schema1, schema1), capturingReporter)
        then:
        breakingCount == capturingReporter.getBreakageCount()
        breakingCount == 0

        when:
        capturingReporter = new CapturingReporter()
        schemaDiff = new SchemaDiff()
        breakingCount = schemaDiff.diffSchema(SchemaDiffSet.diffSetFromIntrospection(schema1, schema2), capturingReporter)

        then:
        breakingCount == capturingReporter.getBreakageCount()
        breakingCount == 1
    }

    def "directives are removed should be breaking when enforceDirectives is enabled"() {
        def oldSchema = TestUtil.schema('''
      directive @someDirective on FIELD_DEFINITION

      type test {
        version: String! @someDirective
      }
      
      type Query {
        getTests: [test]!
      }
     ''')
        def newSchema = TestUtil.schema('''
      type test {
        version: String!
      }
      
      type Query {
        getTests: [test]!
      }
     ''')
        def reporter = new CapturingReporter()
        SchemaDiffSet diffSet = SchemaDiffSet.diffSetFromSdl(oldSchema, newSchema)
        def diff = new SchemaDiff(SchemaDiff.Options.defaultOptions().enforceDirectives())
        when:
        diff.diffSchema(diffSet, reporter)

        then:
        reporter.dangerCount == 0
        reporter.breakageCount == 1
        reporter.breakages.every {
            it.getCategory() == DiffCategory.MISSING
        }
    }

    def "When enforceDirectives is enabled, IntrospectionSchemaDiffSet should assert"() {
        def oldSchema = TestUtil.schema('''
      directive @someDirective on FIELD_DEFINITION

      type test {
        version: String! @someDirective
      }
      
      type Query {
        getTests: [test]!
      }
     ''')
        def newSchema = TestUtil.schema('''
      type test {
        version: String!
      }
      
      type Query {
        getTests: [test]!
      }
     ''')
        def reporter = new CapturingReporter()
        SchemaDiffSet diffSet = SchemaDiffSet.diffSetFromIntrospection(oldSchema, newSchema)
        def diff = new SchemaDiff(SchemaDiff.Options.defaultOptions().enforceDirectives())
        when:
        diff.diffSchema(diffSet, reporter)

        then:
        thrown(AssertException)
    }
}
