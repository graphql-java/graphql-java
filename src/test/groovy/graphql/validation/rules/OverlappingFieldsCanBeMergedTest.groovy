package graphql.validation.rules

import graphql.TypeResolutionEnvironment
import graphql.language.Document
import graphql.language.SourceLocation
import graphql.parser.Parser
import graphql.schema.GraphQLObjectType
import graphql.schema.GraphQLSchema
import graphql.schema.TypeResolver
import graphql.validation.LanguageTraversal
import graphql.validation.RulesVisitor
import graphql.validation.ValidationContext
import graphql.validation.ValidationErrorCollector
import spock.lang.Specification

import static graphql.Scalars.GraphQLInt
import static graphql.Scalars.GraphQLString
import static graphql.schema.GraphQLFieldDefinition.newFieldDefinition
import static graphql.schema.GraphQLList.list
import static graphql.schema.GraphQLNonNull.nonNull
import static graphql.schema.GraphQLObjectType.newObject
import static graphql.schema.GraphQLUnionType.newUnionType

class OverlappingFieldsCanBeMergedTest extends Specification {

    ValidationErrorCollector errorCollector = new ValidationErrorCollector()


    def traverse(String query, GraphQLSchema schema) {
        if (schema == null) {
            def objectType = newObject()
                    .name("Test")
                    .field(newFieldDefinition().name("name").type(GraphQLString))
                    .field(newFieldDefinition().name("nickname").type(GraphQLString))
                    .build()
            schema = GraphQLSchema.newSchema().query(objectType).build()
        }

        Document document = new Parser().parseDocument(query)
        ValidationContext validationContext = new ValidationContext(schema, document)
        OverlappingFieldsCanBeMerged overlappingFieldsCanBeMerged = new OverlappingFieldsCanBeMerged(validationContext, errorCollector)
        LanguageTraversal languageTraversal = new LanguageTraversal()

        languageTraversal.traverse(document, new RulesVisitor(validationContext, [overlappingFieldsCanBeMerged]))
    }

    def "identical fields are ok"() {
        given:
        def query = """
            fragment f on Test{
                name
                name
            }
        """
        when:
        traverse(query, null)

        then:
        errorCollector.errors.isEmpty()
    }

    def "two aliases with different targets"() {
        given:
        def query = """
            fragment f on Test{
                myName : name
                myName : nickname
            }
        """
        when:
        traverse(query, null)

        then:
        errorCollector.getErrors().size() == 1
        errorCollector.getErrors()[0].message == "Validation error of type FieldsConflict: myName: name and nickname are different fields @ 'f'"
        errorCollector.getErrors()[0].locations == [new SourceLocation(3, 17), new SourceLocation(4, 17)]
    }

    GraphQLSchema unionSchema() {
        def StringBox = newObject().name("StringBox")
                .field(newFieldDefinition().name("scalar").type(GraphQLString))
                .build()
        def IntBox = newObject().name("IntBox")
                .field(newFieldDefinition().name("scalar").type(GraphQLInt))
                .build()

        def NonNullStringBox1 = newObject().name("NonNullStringBox1")
                .field(newFieldDefinition().name("scalar").type(nonNull(GraphQLString)))
                .build()

        def NonNullStringBox2 = newObject().name("NonNullStringBox2")
                .field(newFieldDefinition().name("scalar").type(nonNull(GraphQLString)))
                .build()

        def ListStringBox1 = newObject().name("ListStringBox1")
                .field(newFieldDefinition().name("scalar").type(list(GraphQLString)))
                .build()

        def BoxUnion = newUnionType()
                .name("BoxUnion")
                .possibleTypes(StringBox, IntBox, NonNullStringBox1, NonNullStringBox2, ListStringBox1)
                .typeResolver(new TypeResolver() {
            @Override
            GraphQLObjectType getType(TypeResolutionEnvironment env) {
                return null
            }
        })
                .build()
        def QueryRoot = newObject()
                .name("QueryRoot")
                .field(newFieldDefinition().name("boxUnion").type(BoxUnion)).build()
        return GraphQLSchema.newSchema().query(QueryRoot).build()
    }

    def 'conflicting scalar return types'() {
        given:
        def schema = unionSchema()
        def query = """
                {
                    boxUnion {
                        ...on IntBox {
                            scalar
                        }
                        ...on StringBox {
                            scalar
                        }
                    }
                }
        """

        when:
        traverse(query, schema)

        then:
        errorCollector.getErrors().size() == 1
        errorCollector.getErrors()[0].message == "Validation error of type FieldsConflict: scalar: they return differing types Int and String @ 'boxUnion'"
    }


    def 'same wrapped scalar return types'() {
        given:
        def schema = unionSchema()
        def query = """
                {
                    boxUnion {
                        ...on NonNullStringBox1 {
                            scalar
                        }
                        ...on NonNullStringBox2 {
                            scalar
                        }
                    }
                }
                """

        when:
        traverse(query, schema)

        then:
        errorCollector.errors.isEmpty()
    }

    def 'not the same non null return types'() {
        given:
        def schema = unionSchema()
        def query = """
                {
                    boxUnion {
                        ...on StringBox {
                            scalar
                        }
                        ...on NonNullStringBox1 {
                            scalar
                        }
                    }
                }
                """

        when:
        traverse(query, schema)

        then:
        errorCollector.getErrors().size() == 1
        errorCollector.getErrors()[0].message == "Validation error of type FieldsConflict: scalar: fields have different nullability shapes @ 'boxUnion'"
    }

    def 'not the same list return types'() {
        given:
        def schema = unionSchema()
        def query = """
                {
                    boxUnion {
                        ...on StringBox {
                            scalar
                        }
                        ...on ListStringBox1 {
                            scalar
                        }
                    }
                }
                """

        when:
        traverse(query, schema)

        then:
        errorCollector.getErrors().size() == 1
        errorCollector.getErrors()[0].message == "Validation error of type FieldsConflict: scalar: fields have different list shapes @ 'boxUnion'"
    }


    def 'unique fields'() {
        given:
        def query = """
        fragment uniqueFields on Dog {
            name
            nickname
        }
        """
        when:
        traverse(query, null)

        then:
        errorCollector.getErrors().isEmpty()
    }

    def 'identical fields'() {
        given:
        def query = """
        fragment mergeIdenticalFields on Dog {
            name
            name
        }
        """
        when:
        traverse(query, null)

        then:
        errorCollector.getErrors().isEmpty()
    }

    def 'identical fields with identical args'() {
        given:
        def query = """
        fragment mergeIdenticalFieldsWithIdenticalArgs on Dog {
            doesKnowCommand(dogCommand: SIT)
            doesKnowCommand(dogCommand: SIT)
        }
        """
        when:
        traverse(query, null)

        then:
        errorCollector.getErrors().isEmpty()
    }

    def 'identical fields with identical directives'() {
        given:
        def query = """
        fragment mergeSameFieldsWithSameDirectives on Dog {
            name @include(if: true)
            name @include(if: true)
        }
        """
        when:
        traverse(query, null)

        then:
        errorCollector.getErrors().isEmpty()
    }

    def 'different args with different aliases'() {
        given:
        def query = """
        fragment differentArgsWithDifferentAliases on Dog {
            knowsSit: doesKnowCommand(dogCommand: SIT)
            knowsDown: doesKnowCommand(dogCommand: DOWN)
        }
        """
        when:
        traverse(query, null)

        then:
        errorCollector.getErrors().isEmpty()
    }

    def 'different directives with different aliases'() {
        given:
        def query = """
                fragment differentDirectivesWithDifferentAliases on Dog {
            nameIfTrue: name @include(if: true)
            nameIfFalse: name @include(if: false)
        }
        """
        when:
        traverse(query, null)

        then:
        errorCollector.getErrors().isEmpty()
    }

    def 'Same aliases with different field targets'() {
        given:
        def query = """
        fragment sameAliasesWithDifferentFieldTargets on Dog {
            fido: name
            fido: nickname
        }
        """
        when:
        traverse(query, null)

        then:
        errorCollector.getErrors().size() == 1
        errorCollector.getErrors()[0].message == "Validation error of type FieldsConflict: fido: name and nickname are different fields @ 'sameAliasesWithDifferentFieldTargets'"
        errorCollector.getErrors()[0].locations == [new SourceLocation(3, 13), new SourceLocation(4, 13)]
    }

    def 'Alias masking direct field access'() {
        given:
        def query = """
        fragment aliasMaskingDirectFieldAccess on Dog {
            name: nickname
            name
        }
         """
        when:
        traverse(query, null)

        then:
        errorCollector.getErrors().size() == 1
        errorCollector.getErrors()[0].message == "Validation error of type FieldsConflict: name: nickname and name are different fields @ 'aliasMaskingDirectFieldAccess'"
        errorCollector.getErrors()[0].locations == [new SourceLocation(3, 13), new SourceLocation(4, 13)]
    }

    def 'conflicting args'() {
        given:
        def query = """
                fragment conflictingArgs on Dog {
            doesKnowCommand(dogCommand: SIT)
            doesKnowCommand(dogCommand: HEEL)
        }
        """
        when:
        traverse(query, null)

        then:
        errorCollector.getErrors().size() == 1
        errorCollector.getErrors()[0].message == "Validation error of type FieldsConflict: doesKnowCommand: they have differing arguments @ 'conflictingArgs'"
        errorCollector.getErrors()[0].locations == [new SourceLocation(3, 13), new SourceLocation(4, 13)]
    }

    //
    // The rules have been relaxed regarding fragment uniqueness.
    //
    // see https://github.com/facebook/graphql/pull/120/files
    // and https://github.com/graphql/graphql-js/pull/230/files
    //
    def "different skip/include directives accepted"() {
        given:
        def query = """
            fragment differentDirectivesWithDifferentAliases on Dog {
                name @include(if: true)
                name @include(if: false)
            }
        """
        when:
        traverse(query, null)

        then:
        errorCollector.getErrors().isEmpty()
    }

    def 'encounters conflict in fragments'() {
        def query = """
        {
            ...A
            ...B
        }
        fragment A on Type {
            x: a
        }
        fragment B on Type {
            x: b
        }
        """
        when:
        traverse(query, null)

        then:
        errorCollector.getErrors().size() == 1
        errorCollector.getErrors()[0].message == "Validation error of type FieldsConflict: x: a and b are different fields"
        errorCollector.getErrors()[0].locations == [new SourceLocation(7, 13), new SourceLocation(10, 13)]
    }

    def 'reports each conflict once'() {
        def query = """
        {
            f1 {
                ...A
                ...B
            }
            f2 {
                ...B
                ...A
            }
            f3 {
                ...A
                ...B
                x: c
            }
        }
        fragment A on Type {
            x: a
        }
        fragment B on Type {
            x: b
        }
        """
        when:
        traverse(query, null)

        then:
        errorCollector.getErrors().size() == 3

        errorCollector.getErrors()[0].message == "Validation error of type FieldsConflict: x: a and b are different fields @ 'f1'"
        errorCollector.getErrors()[0].locations == [new SourceLocation(18, 13), new SourceLocation(21, 13)]

        errorCollector.getErrors()[1].message == "Validation error of type FieldsConflict: x: a and c are different fields @ 'f3'"
        errorCollector.getErrors()[1].locations == [new SourceLocation(18, 13), new SourceLocation(14, 17)]

        errorCollector.getErrors()[2].message == "Validation error of type FieldsConflict: x: b and c are different fields @ 'f3'"
        errorCollector.getErrors()[2].locations == [new SourceLocation(21, 13), new SourceLocation(14, 17)]
    }


    def 'deep conflict'() {
        def query = """
        {
            field {
                x: a
            },
            field {
                x: b
            }
        }
        """
        when:
        traverse(query, null)

        then:
        errorCollector.getErrors().size() == 1
        errorCollector.getErrors()[0].message == "Validation error of type FieldsConflict: field: (x: a and b are different fields)"
        errorCollector.getErrors()[0].locations.size() == 4
    }

    def 'deep conflict with multiple issues'() {
        def query = """
                {
                    field {
                        x: a
                        y: c
                    },
                    field {
                        x: b
                        y: d
                    }
                }
                """
        when:
        traverse(query, null)

        then:
        errorCollector.getErrors().size() == 1
        errorCollector.getErrors()[0].message == "Validation error of type FieldsConflict: field: (x: a and b are different fields, y: c and d are different fields)"
        errorCollector.getErrors()[0].locations.size() == 6
    }

    def 'very deep conflict'() {
        given:
        def query = """
                {
                    field {
                        deepField {
                            x: a
                        }
                    },
                    field {
                        deepField {
                            x: b
                        }
                    }
                }
                """
        when:
        traverse(query, null)

        then:
        errorCollector.getErrors().size() == 1
        errorCollector.getErrors()[0].message == "Validation error of type FieldsConflict: field: (deepField: (x: a and b are different fields))"
        errorCollector.getErrors()[0].locations.size() == 6
    }

    def 'reports deep conflict to nearest common ancestor'() {
        def query = """
                {
                    field {
                        deepField {
                            x: a
                        }
                        deepField {
                            x: b
                        }
                    },
                    field {
                        deepField {
                            y
                        }
                    }
                }
                """
        when:
        traverse(query, null)

        then:
        errorCollector.getErrors().size() == 1
        errorCollector.getErrors()[0].message == "Validation error of type FieldsConflict: deepField: (x: a and b are different fields) @ 'field'"
        errorCollector.getErrors()[0].locations.size() == 4
    }

}
