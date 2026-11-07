# large-schema-6 — Schema and query fixtures

`large-schema-6-query.graphql` contains the `mainIssueAggQuery` operation and its
663 fragments. The matching SDL is split into four parts at definition boundaries
to keep each file below the repository's 10 MB limit. Concatenating the parts
reproduces the supplied SDL byte for byte; the query is also unchanged.

## Reassembly

```bash
cat large-schema-6.graphqls.part{1,2,3,4} > large-schema-6.graphqls
```

Tests can concatenate the four resources in that order without creating another
file. `LargeSchemaQueryComplexityTest` demonstrates loading both fixtures.

| File | Bytes | SHA-256 |
| --- | ---: | --- |
| Reassembled SDL | 32,655,122 | `0c3d189c550fc73f26f618c7dc0ddf3a0dee9e68c309a6ef7386bb9b4682cca7` |
| Query | 194,881 | `7bdd27d0fafd04fa7b27615a4510fe413432e6a1db3f38152f6e76f48b8e4393` |

## Parser measurements

These counts use the GraphQL Java lexer and parser, including their channel and
grammar-rule definitions. The SDL column describes the reassembled file.

| Measurement / parser option | Query | Default operation limit | SDL | Default SDL limit |
| --- | ---: | ---: | ---: | ---: |
| UTF-8 bytes | 194,881 | Not a parser byte limit | 32,655,122 | Not a parser byte limit |
| UTF-16 code units (`maxCharacters`) | 194,881 | 1,048,576 | 32,501,785 | Unlimited |
| Unicode code points | 194,881 | Not a separate limit | 32,501,785 | Not a separate limit |
| Grammar-channel tokens, including EOF (`maxTokens`) | 22,480 | 15,000 | 1,591,375 | Unlimited |
| Whitespace-channel tokens (`maxWhitespaceTokens`) | 7,414 | 200,000 | 1,806,512 | Unlimited |
| Comment-channel tokens (separate `maxTokens` budget) | 0 | 15,000 | 0 | Unlimited |
| All tokens, including EOF | 29,894 | Not a combined limit | 3,397,887 | Not a combined limit |
| Maximum grammar-rule nesting (`maxRuleDepth`) | 47 | 500 | 24 | Unlimited |
| Longest numeric literal (`maxNumericLiteralCharacters`) | 5 | 100 | 6 | 100 |

“Unlimited” here means `Integer.MAX_VALUE` (2,147,483,647).

`maxCharacters` counts Java `Reader` characters, which are UTF-16 code units;
it does not count UTF-8 bytes. Both fixtures happen to have equal code-point and
UTF-16-unit counts. The query is ASCII; the SDL contains non-ASCII text.

Grammar tokens exclude ignored commas and whitespace. The query's 7,414 whitespace
tokens comprise 4,548 commas, 2,783 spaces and 83 line feeds. Its grammar token
count is 22,479 without EOF, but `SafeTokenSource` also charges the EOF token.
SDL descriptions are string tokens, not comment tokens.

Only the query's grammar-token count exceeds an operation parser default.
The test raises its local token limit to 100,000 to exercise validation; an exact
limit of 22,480 also accepts it. Regression tests verify that the exact measured
query limits pass and values one below each measurement fail.

Parser nesting is syntactic: it does not expand fragment spreads. Its depth of
47 is therefore a different measure from the operation's field depth of 15.

## Counting assumptions

The unmodified query validates against the SDL. It contains `__typename` but no
`__schema` or `__type` selections, so Good Faith introspection limits do not apply.
Its structural counts include all selections regardless of `@skip` and `@include`.

No request variables accompany the fixture. For the ENF comparison, the test
changes conditional directive arguments in memory to `@skip(if: false)` and
`@include(if: true)`, and supplies stand-in coerced argument values. This retains
both sides of mutually exclusive conditions; it does not describe one possible
runtime request. The stored query and the document used for validation are unchanged.

| Measurement | Count |
| --- | ---: |
| Field nodes in the parsed document | 5,687 |
| Expanded field occurrences | 91,515 |
| Merged validation fields | 4,311 |
| ENFs created (`getOperationFieldCount()`, before final ENF merging) | 5,648 |
| Fields reachable in the final ENF tree | 3,882 |
| Maximum field depth | 15 |

These measure document or selection structure, not list lengths, resolver calls,
response bytes, or execution time.
