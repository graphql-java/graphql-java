package graphql.validation.rules

import graphql.language.Document
import graphql.language.SourceLocation
import graphql.parser.Parser
import graphql.schema.GraphQLNonNull
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
import static graphql.schema.GraphQLObjectType.newObject
import static graphql.schema.GraphQLUnionType.newUnionType

class OverlappingFieldsCanBeMergedTest extends Specification {

    ValidationErrorCollector errorCollector = new ValidationErrorCollector()


    def traverse(String query, GraphQLSchema schema) {
        if (schema == null) {
            def objectType = newObject()
                    .name("Test")
                    .field(newFieldDefinition().name("name").type(GraphQLString).build())
                    .field(newFieldDefinition().name("nickname").type(GraphQLString).build())
                    .build();
            schema = GraphQLSchema.newSchema().query(objectType).build()
        }

        Document document = new Parser().parseDocument(query)
        ValidationContext validationContext = new ValidationContext(schema, document)
        OverlappingFieldsCanBeMerged overlappingFieldsCanBeMerged = new OverlappingFieldsCanBeMerged(validationContext, errorCollector)
        LanguageTraversal languageTraversal = new LanguageTraversal();

        languageTraversal.traverse(document, new RulesVisitor(validationContext, [overlappingFieldsCanBeMerged]));
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
        errorCollector.getErrors()[0].message == "Validation error of type FieldsConflict: myName: name and nickname are different fields"
        errorCollector.getErrors()[0].locations == [new SourceLocation(3, 17), new SourceLocation(4, 17)]
    }

    def 'conflicting scalar return types'() {

        def StringBox = newObject().name("StringBox")
                .field(newFieldDefinition().name("scalar").type(GraphQLString).build())
                .build()
        def IntBox = newObject().name("IntBox")
                .field(newFieldDefinition().name("scalar").type(GraphQLInt).build())
                .build()

        def NonNullStringBox1 = newObject().name("NonNullStringBox1")
                .field(newFieldDefinition().name("scalar").type(new GraphQLNonNull(GraphQLString)).build())
                .build()

        def NonNullStringBox2 = newObject().name("NonNullStringBox2")
                .field(newFieldDefinition().name("scalar").type(new GraphQLNonNull(GraphQLString)).build())
                .build()

        def BoxUnion = newUnionType()
                .name("BoxUnion")
                .possibleTypes(StringBox, IntBox, NonNullStringBox1, NonNullStringBox2)
                .typeResolver(new TypeResolver() {
            @Override
            GraphQLObjectType getType(Object object) {
                return null
            }
        })
                .build()
        def QueryRoot = newObject()
                .name("QueryRoot")
                .field(newFieldDefinition().name("boxUnion").type(BoxUnion).build()).build()
        def schema = GraphQLSchema.newSchema().query(QueryRoot).build()

        given:
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
        errorCollector.getErrors()[0].message == "Validation error of type FieldsConflict: scalar: they return differing types Int and String"
//        [
//                { message: fieldsConflictMessage(
//                        'scalar',
//                        'they return differing types Int and String'
//                ),
//                    locations: [ { line: 5, column: 15 }, { line: 8, column: 15 } ] }
//        ]);
    }

}
