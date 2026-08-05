package graphql.validation

import graphql.ExperimentalApi
import graphql.TestUtil
import graphql.i18n.I18n
import graphql.language.Document
import graphql.language.Field
import graphql.language.InlineFragment
import graphql.language.OperationDefinition
import graphql.language.SelectionSet
import graphql.language.TypeName
import graphql.parser.Parser
import graphql.schema.GraphQLSchema
import spock.lang.Specification

class OperationValidatorTraversalTest extends Specification {

    GraphQLSchema schema = TestUtil.schema("""
        directive @flag(if: Boolean!) on FIELD

        type Query {
            node: Node
            search(
                filter: Filter
                values: [String!]!
                defaulted: String! = "default"
            ): String
        }

        type Mutation {
            update: Node
        }

        type Subscription {
            event: Node
        }

        type Node {
            child: Node
            name: String
        }

        input Filter {
            nested: Nested
            values: [String!]!
        }

        input Nested {
            required: String!
            defaulted: String! = "default"
        }
    """)

    def "resolves selection set parents for fields and fragments"() {
        when:
        def errors = validate("""
            {
                node {
                    missingDirect
                    ... on Node {
                        missingInline
                    }
                    ...NodeFields
                }
            }

            fragment NodeFields on Node {
                child {
                    missingNested
                }
            }
        """)

        then:
        errors*.validationErrorType == [
                ValidationErrorType.FieldUndefined,
                ValidationErrorType.FieldUndefined,
                ValidationErrorType.FieldUndefined,
        ]
        errors*.queryPath == [
                ["node", "missingDirect"],
                ["node", "missingInline"],
                ["NodeFields", "child", "missingNested"],
        ]
        errors.every { it.message.contains("in type 'Node'") }
    }

    def "resolves a programmatically constructed inline fragment"() {
        given:
        def inlineFragment = new InlineFragment(new TypeName("Node")).transform {
            it.selectionSet(new SelectionSet([new Field("missing")]))
        }
        def nodeField = Field.newField("node")
                .selectionSet(new SelectionSet([inlineFragment]))
                .build()
        def operation = OperationDefinition.newOperationDefinition()
                .selectionSet(new SelectionSet([nodeField]))
                .build()

        when:
        def errors = validate(new Document([operation]))

        then:
        errors.size() == 1
        errors[0].validationErrorType == ValidationErrorType.FieldUndefined
        errors[0].queryPath == ["node", "missing"]
        errors[0].message.contains("in type 'Node'")
    }

    def "resolves the #operation operation root"() {
        when:
        def errors = validate("""
            $operation {
                $rootField {
                    missing
                }
            }
        """)

        then:
        errors.size() == 1
        errors[0].validationErrorType == ValidationErrorType.FieldUndefined
        errors[0].queryPath == [rootField, "missing"]
        errors[0].message.contains("in type 'Node'")

        where:
        operation      | rootField
        "mutation"     | "update"
        "subscription" | "event"
    }

    def "tracks nested input positions and location defaults"() {
        when:
        def errors = validate("""
            query Test(\$string: String, \$flag: Boolean) {
                search(
                    filter: {
                        nested: {
                            required: \$string
                            defaulted: \$string
                        }
                        values: [\$string]
                    }
                    values: [\$string]
                    defaulted: \$string
                ) @flag(if: \$flag)
            }
        """)

        then:
        errors*.validationErrorType == [
                ValidationErrorType.VariableTypeMismatch,
                ValidationErrorType.VariableTypeMismatch,
                ValidationErrorType.VariableTypeMismatch,
                ValidationErrorType.VariableTypeMismatch,
        ]
        errors.every { it.queryPath == ["search"] }
    }

    def "clears directive state before resolving the next field arguments"() {
        when:
        def errors = validate("""
            {
                first: search(values: ["one"]) @flag(if: true)
                second: search(values: ["two"], unknown: true)
            }
        """)

        then:
        errors.size() == 1
        errors[0].validationErrorType == ValidationErrorType.UnknownArgument
        errors[0].queryPath == ["search"]
    }

    def "handles object fields under an unknown argument"() {
        when:
        def errors = validate("""
            {
                search(
                    values: ["one"]
                    unknown: { anything: true }
                )
            }
        """)

        then:
        errors.size() == 1
        errors[0].validationErrorType == ValidationErrorType.UnknownArgument
        errors[0].queryPath == ["search"]
    }

    def "handles a directive before the operation selection set"() {
        when:
        def errors = validateWithIncrementalSupport("""
            query @defer {
                node {
                    name
                }
            }
        """)

        then:
        errors.size() == 1
        errors[0].validationErrorType == ValidationErrorType.MisplacedDirective
        errors[0].queryPath == []
    }

    private List<ValidationError> validate(String query) {
        def document = Parser.parse(query)
        return validate(document)
    }

    private List<ValidationError> validate(Document document) {
        return new Validator().validateDocument(schema, document, Locale.ENGLISH)
    }

    private List<ValidationError> validateWithIncrementalSupport(String query) {
        def document = Parser.parse(query)
        def i18n = I18n.i18n(I18n.BundleType.Validation, Locale.ENGLISH)
        def validationContext = new ValidationContext(schema, document, i18n)
        validationContext.graphQLContext.put(ExperimentalApi.ENABLE_INCREMENTAL_SUPPORT, true)
        def errorCollector = new ValidationErrorCollector()
        new LanguageTraversal().traverse(
                document,
                new OperationValidator(validationContext, errorCollector, { true }))
        return errorCollector.errors
    }
}
