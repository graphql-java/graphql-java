package graphql.validation

import graphql.i18n.I18n
import graphql.language.Document
import graphql.language.SourceLocation
import graphql.parser.Parser
import graphql.schema.GraphQLCodeRegistry
import graphql.schema.GraphQLSchema
import graphql.validation.LanguageTraversal
import graphql.validation.OperationValidationRule
import graphql.validation.OperationValidator
import graphql.validation.ValidationContext
import graphql.validation.ValidationErrorCollector
import spock.lang.Specification

import static graphql.Scalars.GraphQLInt
import static graphql.Scalars.GraphQLString
import static graphql.TestUtil.schema
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
        I18n i18n = I18n.i18n(I18n.BundleType.Validation, Locale.ENGLISH)
        ValidationContext validationContext = new ValidationContext(schema, document, i18n)
        OperationValidator operationValidator = new OperationValidator(validationContext, errorCollector,
                { r -> r == OperationValidationRule.OVERLAPPING_FIELDS_CAN_BE_MERGED })
        LanguageTraversal languageTraversal = new LanguageTraversal()

        languageTraversal.traverse(document, operationValidator)
    }

    def "identical fields are ok"() {
        given:
        def query = """
           {...f}
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

    def "identical fields are ok 2"() {
        given:
        def query = """
           { name name name name: name}
        """
        when:
        traverse(query, null)

        then:
        errorCollector.errors.isEmpty()
    }

    def "two aliases with different targets"() {
        given:
        def query = """
            {... f }
            fragment f on Test{
                myName : name
                myName : nickname
            }
        """
        when:
        traverse(query, null)

        then:
        errorCollector.getErrors().size() == 1
        errorCollector.getErrors()[0].message == "Validation error (FieldsConflict) : 'myName' : 'name' and 'nickname' are different fields"
        errorCollector.getErrors()[0].locations == [new SourceLocation(4, 17), new SourceLocation(5, 17)]
    }

    static GraphQLSchema unionSchema() {
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
                .build()
        def codeRegistry = GraphQLCodeRegistry.newCodeRegistry()
                .typeResolver(BoxUnion, { env -> null })
                .build()
        def QueryRoot = newObject()
                .name("QueryRoot")
                .field(newFieldDefinition().name("boxUnion").type(BoxUnion)).build()

        return GraphQLSchema.newSchema()
                .codeRegistry(codeRegistry)
                .query(QueryRoot)
                .build()
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
        errorCollector.getErrors()[0].message == "Validation error (FieldsConflict) : 'boxUnion/scalar' : returns different types 'Int' and 'String'"
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
        errorCollector.getErrors()[0].message == "Validation error (FieldsConflict) : 'boxUnion/scalar' : fields have different nullability shapes"
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
        errorCollector.getErrors()[0].message == "Validation error (FieldsConflict) : 'boxUnion/scalar' : fields have different list shapes"
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
        {dog{...sameAliasesWithDifferentFieldTargets}}
        fragment sameAliasesWithDifferentFieldTargets on Dog {
            fido: name
            fido: nickname
        }
        """
        def schema = schema('''
        type Dog {
            name: String
            nickname: String
        }
        type Query {
            dog: Dog
        }
        ''')
        when:
        traverse(query, schema)

        then:
        errorCollector.getErrors().size() == 1
        errorCollector.getErrors()[0].message == "Validation error (FieldsConflict) : 'dog/fido' : 'name' and 'nickname' are different fields"
        errorCollector.getErrors()[0].locations == [new SourceLocation(4, 13), new SourceLocation(5, 13)]
    }

    def 'Alias masking direct field access'() {
        given:
        def query = """
        {dog{...aliasMaskingDirectFieldAccess}}
        fragment aliasMaskingDirectFieldAccess on Dog {
            name: nickname
            name
        }
         """
        def schema = schema('''
        type Dog {
            nickname: String
            name : String
        }
        type Query { dog: Dog }
        ''')
        when:
        traverse(query, schema)

        then:
        errorCollector.getErrors().size() == 1
        errorCollector.getErrors()[0].message == "Validation error (FieldsConflict) : 'dog/name' : 'nickname' and 'name' are different fields"
        errorCollector.getErrors()[0].locations == [new SourceLocation(4, 13), new SourceLocation(5, 13)]
    }

    def 'issue 3332 - Alias masking direct field access non fragment'() {
        given:
        def query = """
        { dog {
            name: nickname
            name
        }}
         """
        def schema = schema('''
        type Dog {
            name : String
            nickname: String
        }
        type Query { dog: Dog }
        ''')
        when:
        traverse(query, schema)

        then:
        errorCollector.getErrors().size() == 1
        errorCollector.getErrors()[0].message == "Validation error (FieldsConflict) : 'dog/name' : 'nickname' and 'name' are different fields"
        errorCollector.getErrors()[0].locations == [new SourceLocation(3, 13), new SourceLocation(4, 13)]
    }

    def 'issue 3332  -Alias masking direct field access non fragment with non null parent type'() {
        given:
        def query = """
        query GetCat {
              cat {
                foo1
                foo1: foo2
              }
            }
         """
        def schema = schema('''
        type Query {
            cat: Cat! # non null parent type
        }
        type Cat {
            foo1: String!
            foo2: String!
        }
        ''')
        when:
        traverse(query, schema)

        then:
        errorCollector.getErrors().size() == 1
        errorCollector.getErrors()[0].message == "Validation error (FieldsConflict) : 'cat/foo1' : 'foo1' and 'foo2' are different fields"
        errorCollector.getErrors()[0].locations == [new SourceLocation(4, 17), new SourceLocation(5, 17)]
    }

    def 'conflicting args'() {
        given:
        def query = """
        {dog{...conflictingArgs}}
                fragment conflictingArgs on Dog {
            doesKnowCommand(dogCommand: SIT)
            doesKnowCommand(dogCommand: HEEL)
        }
        """
        def schema = schema('''
        type Dog {
            doesKnowCommand(dogCommand: DogCommand): String
        }
        enum DogCommand { SIT, HEEL }
        type Query {
            dog: Dog
        }
        ''')
        when:
        traverse(query, schema)

        then:
        errorCollector.getErrors().size() == 1
        errorCollector.getErrors()[0].message == "Validation error (FieldsConflict) : 'dog/doesKnowCommand' : fields have different arguments"
        errorCollector.getErrors()[0].locations == [new SourceLocation(4, 13), new SourceLocation(5, 13)]
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
        def schema = schema('''
        type Type {
            a: String
            b: String
            c: String
        }
        schema {
           query: Type
        }
        ''')
        when:
        traverse(query, schema)

        then:
        errorCollector.getErrors().size() == 1
        errorCollector.getErrors()[0].message == "Validation error (FieldsConflict) : 'x' : 'a' and 'b' are different fields"
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
        def schema = schema('''
        type Type {
            a: String
            b: String
            c: String
        }
        type Query {
            f1: Type
            f2: Type
            f3: Type
        }
        ''')

        when:
        traverse(query, schema)

        then:
        errorCollector.getErrors().size() == 1

        errorCollector.getErrors()[0].message == "Validation error (FieldsConflict) : 'f1/x' : 'a' and 'b' are different fields"
        errorCollector.getErrors()[0].locations == [new SourceLocation(18, 13), new SourceLocation(21, 13)]
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
        def schema = schema('''
        type Type {
            a: String
            b: String
        }
        type Query {
            field: Type
        }
        ''')
        traverse(query, schema)

        then:
        errorCollector.getErrors().size() == 1
        errorCollector.getErrors()[0].message == "Validation error (FieldsConflict) : 'field/x' : 'a' and 'b' are different fields"
        errorCollector.getErrors()[0].locations.size() == 2
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
        def schema = schema('''
        type Type {
            a: String
            b: String
            c: String
            d: String
        }
        type Query {
            field: Type
        }
        ''')

        when:
        traverse(query, schema)

        then:
        errorCollector.getErrors().size() == 2
        errorCollector.getErrors()[0].message == "Validation error (FieldsConflict) : 'field/x' : 'a' and 'b' are different fields"
        errorCollector.getErrors()[0].locations.size() == 2

        errorCollector.getErrors()[1].message == "Validation error (FieldsConflict) : 'field/y' : 'c' and 'd' are different fields"
        errorCollector.getErrors()[1].locations.size() == 2
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
        def schema = schema('''
        type Type {
            a: String
            b: String
            c: String
            d: String
        }
        type Field {
            deepField: Type
        }
        type Query {
            field: Field
        }
        ''')
        when:
        traverse(query, schema)

        then:
        errorCollector.getErrors().size() == 1
        errorCollector.getErrors()[0].message == "Validation error (FieldsConflict) : 'field/deepField/x' : 'a' and 'b' are different fields"
        errorCollector.getErrors()[0].locations.size() == 2
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
        def schema = schema('''
        type Type {
            a: String
            b: String
            c: String
            d: String
        }
        type Field {
            deepField: Type
        }
        type Query {
            field: Field
        }
        ''')

        when:
        traverse(query, schema)

        then:
        errorCollector.getErrors().size() == 1
        errorCollector.getErrors()[0].message == "Validation error (FieldsConflict) : 'field/deepField/x' : 'a' and 'b' are different fields"
        errorCollector.getErrors()[0].locations.size() == 2
    }


    def "parent type is of List NonNull"() {
        given:
        def query = """
        query (\$id: String!) {
          services(ids: [\$id]) {
            componentInfoLocationUrl
            ...ComponentInformation
          }
        }

        fragment ComponentInformation on Component {
          componentInfoLocationUrl
        }
"""
        def schema = schema("""
    type Query {
      services(ids: [String!]): [Component!]
    }

    type Component {
      componentInfoLocationUrl: String!
    }
""")
        when:
        traverse(query, schema)


        then:
        errorCollector.getErrors().size() == 0

    }

    def "parent type is of List List NonNull"() {
        given:
        def query = """
        query (\$id: String!) {
          services(ids: [\$id]) {
            componentInfoLocationUrl
            ...ComponentInformation
          }
        }

        fragment ComponentInformation on Component {
          componentInfoLocationUrl
        }
"""
        def schema = schema("""
    type Query {
      services(ids: [String!]): [[Component!]]
    }

    type Component {
      componentInfoLocationUrl: String!
    }
""")
        when:
        traverse(query, schema)


        then:
        errorCollector.getErrors().size() == 0

    }

    def "parent type is of List NonNull and field is nullable"() {
        given:
        def query = """
        query (\$id: String!) {
          services(ids: [\$id]) {
            componentInfoLocationUrl
            ...ComponentInformation
          }
        }

        fragment ComponentInformation on Component {
          componentInfoLocationUrl
        }
"""
        def schema = schema("""
    type Query {
      services(ids: [String!]): [Component!]
    }

    type Component {
      componentInfoLocationUrl: String
    }
""")
        when:
        traverse(query, schema)


        then:
        errorCollector.getErrors().size() == 0

    }

    def "valid diverging fields with the same parent type on deeper level"() {
        given:
        def schema = schema('''
        type Query {
         pets: [Pet]
        }
        interface Pet {
         name: String
         breed: String
         friends: [Pet]
        }
        type Dog implements Pet {
          name: String
          dogBreed: String
         breed: String
          friends: [Pet]

        }
        type Cat implements Pet {
          catBreed: String
         breed: String
          name : String
          friends: [Pet]

        }
        ''')
        /**
         * Here F1 and F2 are allowed to diverge (backed by different field definitions) because the parent fields have
         * different concrete parent: P1 has Dog, P2 has Cat.
         */
        def query = '''
{
  pets {
    ... on Dog {
       friends { #P1
         name
         ... on Dog {
            breed: dogBreed #F1
         }
       }
    }
    ... on Cat {
     friends {  #P2
        name
        ... on Dog {
          breed #F2
        }
       }
    }
    ... on Pet {
      friends {
        name
       }
    }
  }
}
        '''
        when:
        traverse(query, schema)


        then:
        errorCollector.getErrors().size() == 0
    }

    def "children of diverging fields still need to have same response shape"() {
        given:
        def schema = schema('''
        type Query {
         pets: [Pet]
        }
        interface Pet {
         name: String
         breed: String
         friends: [Pet]
        }
        type Dog implements Pet {
          name: String
          age: Int
          dogBreed: String
         breed: String
          friends: [Pet]

        }
        type Cat implements Pet {
          catBreed: String
          breed: String
          height: Float
          name : String
          friends: [Pet]

        }
        ''')
        def query = '''
        {
          pets {
            ... on Dog {
               friends {
                 ... on Dog {
                    conflict: age
                 }
               }
            }
            ... on Cat {
             friends {
                ... on Cat {
                  conflict: height
                }
               }
            }
          }
        }
        '''
        when:
        traverse(query, schema)


        then:
        errorCollector.getErrors().size() == 1
        errorCollector.getErrors()[0].message == "Validation error (FieldsConflict) : 'pets/friends/conflict' : returns different types 'Int' and 'Float'"
    }


    def "subselection of fields with different concrete parent can be different "() {
        given:
        def schema = schema('''
        type Query {
         pets: [Pet]
        }
        interface Pet {
         name: String
         breed: String
         friends: [Pet]
        }
        type Dog implements Pet {
          name: String
          age: Int
          dogBreed: String
         breed: String
          friends: [Pet]

        }
        type Cat implements Pet {
          catBreed: String
          breed: String
          height: Float
          name : String
          friends: [Pet]

        }
        ''')
        def query = '''
        {
          pets {
            ... on Dog {
               friends {
                name
               }
            }
            ... on Cat {
             friends {
                  breed
               }
            }
          }
        }
        '''
        when:
        traverse(query, schema)


        then:
        errorCollector.getErrors().size() == 0
    }

    def "overlapping fields on lower level"() {
        given:
        def schema = schema('''
        type Query {
         pets: [Pet]
        }
        interface Pet {
         name: String
         breed: String
         friends: [Pet]
        }
        type Dog implements Pet {
          name: String
          age: Int
          dogBreed: String
         breed: String
          friends: [Pet]

        }
        type Cat implements Pet {
          catBreed: String
          breed: String
          height: Float
          name : String
          friends: [Pet]

        }
        ''')
        def query = '''
        {
          pets {
               friends {
                ... on Dog {
                  x: name
                  }
                ... on Cat {
                 x: height
                }
               }
            }
        }
        '''
        when:
        traverse(query, schema)


        then:
        errorCollector.getErrors().size() == 1
        errorCollector.getErrors()[0].message == "Validation error (FieldsConflict) : 'pets/friends/x' : returns different types 'String' and 'Float'"

    }


}
