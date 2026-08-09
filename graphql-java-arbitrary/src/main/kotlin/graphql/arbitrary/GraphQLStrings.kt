package graphql.arbitrary

import io.kotest.property.Arb
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.bind
import io.kotest.property.arbitrary.char
import io.kotest.property.arbitrary.charArray
import io.kotest.property.arbitrary.filter
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.string

private val nameStartRanges: List<CharRange> =
    listOf(
        'A'..'Z',
        'a'..'z',
        '_'..'_'
    )

private val nameContinueRanges: List<CharRange> =
    nameStartRanges.plusElement('0'..'9')

private val builtinTypeNames = setOf("Boolean", "Float", "ID", "Int", "String")

// Enum values may not be "true", "false", or "null"
//   https://spec.graphql.org/draft/#sec-Enum-Value
private val bannedEnumValues = setOf("true", "false", "null")

/**
 * Return a String that is a valid GraphQL name, defined by
 * https://spec.graphql.org/draft/#sec-Names
 *
 * This Arb will not return introspection names
 */
fun Arb.Companion.graphQLName(length: IntRange = 1..10): Arb<String> {
    val nameStart: Arb<Char> = Arb.char(nameStartRanges)
    val nameContinue = Arb.charArray(
        Arb.int(length).map { it - 1 },
        Arb.char(nameContinueRanges)
    )

    return Arb
        .bind(nameStart, nameContinue) { ns, nc ->
            buildString {
                append(ns.toString())
                append(nc)
            }
        }.filter {
            // filter out introspection schema names which are reserved for spec use
            !it.startsWith("__") &&
                // filter out names of built-in types
                it !in builtinTypeNames
        }
}

private fun Arb.Companion.graphQLFieldName(length: IntRange): Arb<String> =
    graphQLName(length = length)
        .map {
            it.replaceFirstChar(Char::lowercase)
        }

/**
 * Return a String suitable for GraphQL field names.
 * This Arb will not return introspection names.
 *
 * Though not required by the spec, generated names always
 * have lower-case first-letters to improve legibility.
 */
fun Arb.Companion.graphQLFieldName(cfg: Config = Config.default): Arb<String> = graphQLFieldName(cfg[FieldNameLength]).filter(cfg)

/** Generate Strings suitable for use as a GraphQL argument name */
fun Arb.Companion.graphQLArgumentName(cfg: Config = Config.default): Arb<String> = graphQLFieldName(cfg[FieldNameLength]).filter(cfg)

/** Generate Strings suitable for use as GraphQL enum value */
fun Arb.Companion.graphQLEnumValueName(cfg: Config = Config.default): Arb<String> =
    graphQLName(cfg[FieldNameLength])
        .filter(cfg)
        .filter { it !in bannedEnumValues }

/** Generate Strings suitable for use as a GraphQL description. */
fun Arb.Companion.graphQLDescription(cfg: Config = Config.default): Arb<String> = Arb.string(cfg[DescriptionLength])

private fun Arb<String>.filter(cfg: Config): Arb<String> {
    val ban = cfg[BanFieldNames]
    return if (ban.isEmpty()) {
        this
    } else {
        filter { !ban.contains(it) }
    }
}
