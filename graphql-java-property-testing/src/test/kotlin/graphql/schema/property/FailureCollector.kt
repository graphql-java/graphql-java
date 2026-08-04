package graphql.schema.property

import java.io.PrintWriter
import java.io.StringWriter
import java.lang.reflect.InvocationTargetException
import java.text.MessageFormat
import kotlin.reflect.KClass

class FailureCollector : Iterable<Failure> {
    private val context = ArrayList<String>()
    private val failures = ArrayList<Failure>()

    fun toMultilineString(dst: StringBuilder) {
        for (f: Failure in failures) {
            f.toMultilineString(dst)
        }
    }

    val isEmpty: Boolean
        get() = failures.isEmpty()

    fun assertEmpty(separator: String = "\n") {
        if (!isEmpty) {
            throw AssertionError(this.joinToString(separator))
        }
    }

    fun assertEmptyMultiline(message: String) {
        if (isEmpty) return
        val result = StringBuilder()
        result.append(message)
        toMultilineString(result)
        throw AssertionError(result.toString())
    }

    fun toListOfErrors(): List<String> =
        failures.map { f ->
            val stringBuilder = StringBuilder()
            f.toMultilineString(stringBuilder)
            stringBuilder.toString()
        }

    val size: Int
        get() = failures.size

    override fun iterator(): Iterator<Failure> = failures.iterator()

    fun pushContext(moreContext: String) {
        context.add(moreContext)
    }

    fun popContext() {
        if (context.isEmpty()) throw IllegalStateException("No context to pop.")
        context.removeAt(context.size - 1)
    }

    fun withContext(
        moreContext: String,
        body: Runnable
    ) {
        pushContext(moreContext)
        body.run()
        popContext()
    }

    fun addFailure(
        details: String?,
        messageFormat: String,
        messageArgs: Array<String?>
    ) {
        val msg = MessageFormat.format(messageFormat, *messageArgs)
        failures.add(Failure(context.joinToString("."), msg, details))
    }

    @JvmOverloads
    fun addExceptionFailure(
        e: Throwable?,
        messageFormat: String,
        messageArgs: Array<String?> = EMPTY_ARGS
    ) {
        val sw = StringWriter()
        val pw = PrintWriter(sw)
        e?.printStackTrace(pw)
        addFailure(sw.toString(), messageFormat, messageArgs)
    }

    // The actual checkers
    fun isTrue(
        actual: Boolean,
        messageFormat: String
    ): Boolean = isTrue(actual, messageFormat, EMPTY_ARGS)

    fun isTrue(
        actual: Boolean,
        messageFormat: String,
        messageArgs: Array<String?>
    ): Boolean {
        if (actual) return true
        addFailure(null, messageFormat, messageArgs)
        return false
    }

    fun isFalse(
        actual: Boolean,
        messageFormat: String
    ): Boolean = isFalse(actual, messageFormat, EMPTY_ARGS)

    fun isFalse(
        actual: Boolean,
        messageFormat: String,
        messageArgs: Array<String?>
    ): Boolean {
        if (!actual) return true
        addFailure(null, messageFormat, messageArgs)
        return false
    }

    fun isNull(
        actual: Any?,
        messageFormat: String
    ): Boolean = isNull(actual, messageFormat, EMPTY_ARGS)

    fun isNull(
        actual: Any?,
        messageFormat: String,
        messageArgs: Array<String?>
    ): Boolean {
        if (actual == null) return true
        val details = "$actual was not null"
        addFailure(details, messageFormat, messageArgs)
        return false
    }

    fun isNotNull(
        actual: Any?,
        messageFormat: String
    ): Boolean = isNotNull(actual, messageFormat, EMPTY_ARGS)

    fun isNotNull(
        actual: Any?,
        messageFormat: String,
        messageArgs: Array<String?>
    ): Boolean {
        if (actual != null) return true
        val details = "Actual was null"
        addFailure(details, messageFormat, messageArgs)
        return false
    }

    fun isSameInstanceAs(
        expected: Any,
        actual: Any,
        messageFormat: String
    ): Boolean = isSameInstanceAs(expected, actual, messageFormat, EMPTY_ARGS)

    fun isSameInstanceAs(
        expected: Any,
        actual: Any,
        messageFormat: String,
        messageArgs: Array<String?>
    ): Boolean {
        if (expected === actual) return true
        val details = "$expected (expected) is not same instance as $actual (actual)"
        addFailure(details, messageFormat, messageArgs)
        return false
    }

    fun isNotSameInstanceAs(
        expected: Any?,
        actual: Any?,
        messageFormat: String
    ): Boolean = isNotSameInstanceAs(expected, actual, messageFormat, EMPTY_ARGS)

    fun isNotSameInstanceAs(
        expected: Any?,
        actual: Any?,
        messageFormat: String,
        messageArgs: Array<String?>
    ): Boolean {
        if (expected !== actual) return true
        val details = "Expected and actual are same instance ($actual)"
        addFailure(details, messageFormat, messageArgs)
        return false
    }

    fun isEqualTo(
        expected: Int,
        actual: Int,
        messageFormat: String
    ): Boolean = isEqualTo(expected, actual, messageFormat, EMPTY_ARGS)

    fun isEqualTo(
        expected: Int,
        actual: Int,
        messageFormat: String,
        messageArgs: Array<String?>
    ): Boolean {
        if (expected == actual) return true
        val details = "$expected (expected) is not equal to $actual (actual)"
        addFailure(details, messageFormat, messageArgs)
        return false
    }

    fun isEqualTo(
        expected: Any?,
        actual: Any?,
        messageFormat: String
    ): Boolean = isEqualTo(expected, actual, messageFormat, EMPTY_ARGS)

    private fun eq(
        expected: Any?,
        actual: Any?
    ): Boolean = if (expected != null && (expected == actual || expected == actual)) true else actual == null && expected == null

    fun isEqualTo(
        expected: Any?,
        actual: Any?,
        messageFormat: String,
        messageArgs: Array<String?>
    ): Boolean {
        if (eq(expected, actual)) return true

        val details = if (actual == null) {
            "actual was null and expected was $expected"
        } else if (expected == null) {
            "expected was null and actual was $actual"
        } else {
            "$expected (expected) is not equal to $actual (actual)"
        }
        addFailure(details, messageFormat, messageArgs)

        return false
    }

    fun isNotEqualTo(
        expected: Any?,
        actual: Any?,
        messageFormat: String
    ): Boolean = isNotEqualTo(expected, actual, messageFormat, EMPTY_ARGS)

    fun isNotEqualTo(
        expected: Any?,
        actual: Any?,
        messageFormat: String,
        messageArgs: Array<String?>
    ): Boolean {
        if (!eq(expected, actual)) return true
        val details = if (actual == null && expected == null) {
            "actual and expected were both null"
        } else {
            "Expected and actual are the same ($actual)"
        }
        addFailure(details, messageFormat, messageArgs)
        return false
    }

    fun isEmpty(
        actual: Iterable<*>,
        messageFormat: String
    ): Boolean = isEmpty(actual, messageFormat, EMPTY_ARGS)

    fun isEmpty(
        actual: Iterable<*>,
        messageFormat: String,
        messageArgs: Array<String?>
    ): Boolean {
        if (!actual.iterator().hasNext()) return true
        val details = "$actual is not empty"
        addFailure(details, messageFormat, messageArgs)
        return false
    }

    fun isNotEmpty(
        actual: Iterable<*>,
        messageFormat: String
    ): Boolean = isNotEmpty(actual, messageFormat, EMPTY_ARGS)

    fun isNotEmpty(
        actual: Iterable<*>,
        messageFormat: String,
        messageArgs: Array<String?>
    ): Boolean {
        if (actual.iterator().hasNext()) return true
        val details = "$actual is empty"
        addFailure(details, messageFormat, messageArgs)
        return false
    }

    fun isEmpty(
        actual: CharSequence,
        messageFormat: String
    ): Boolean = isEmpty(actual, messageFormat, EMPTY_ARGS)

    fun isEmpty(
        actual: CharSequence,
        messageFormat: String,
        messageArgs: Array<String?>
    ): Boolean {
        if (actual.isEmpty()) return true
        val details = "[$actual] is not empty"
        addFailure(details, messageFormat, messageArgs)
        return false
    }

    fun isNotEmpty(
        actual: CharSequence,
        messageFormat: String
    ): Boolean = isNotEmpty(actual, messageFormat, EMPTY_ARGS)

    fun isNotEmpty(
        actual: CharSequence,
        messageFormat: String,
        messageArgs: Array<String?>
    ): Boolean {
        if (actual.isNotEmpty()) return true
        val details = "[$actual] is empty"
        addFailure(details, messageFormat, messageArgs)
        return false
    }

    fun <T> contains(
        expected: T,
        actual: Iterable<T>,
        messageFormat: String
    ): Boolean = contains(expected, actual, messageFormat, EMPTY_ARGS)

    fun <T> contains(
        expected: T,
        actual: Iterable<T>,
        messageFormat: String,
        messageArgs: Array<String?>
    ): Boolean {
        var result = false
        for (v: T in actual) {
            if (eq(expected, v)) {
                result = true
                break
            }
        }
        if (result) return true
        val details = "$actual does not contains $expected"
        addFailure(details, messageFormat, messageArgs)
        return false
    }

    fun <T> containedBy(
        expected: Iterable<T>,
        actual: T,
        messageFormat: String
    ): Boolean = containedBy(expected, actual, messageFormat, EMPTY_ARGS)

    fun <T> containedBy(
        expected: Iterable<T>,
        actual: T,
        messageFormat: String,
        messageArgs: Array<String?>
    ): Boolean {
        var result = false
        for (v: T in expected) {
            if (eq(v, actual)) {
                result = true
                break
            }
        }
        if (result) return true
        val details = "$actual is not contained by $expected"
        addFailure(details, messageFormat, messageArgs)
        return false
    }

    fun <T : Comparable<T>?> isInOrder(
        actual: Iterable<T>,
        messageFormat: String
    ): Boolean = isInOrder(actual, messageFormat, EMPTY_ARGS)

    fun <T : Comparable<T>?> isInOrder(
        actual: Iterable<T>,
        messageFormat: String,
        messageArgs: Array<String?>
    ): Boolean {
        val iter = actual.iterator()
        if (!iter.hasNext()) return true
        var right = iter.next()
        if (!iter.hasNext()) return true
        var left: T? = null
        var inOrder = true
        while (iter.hasNext()) {
            left = right
            right = iter.next()
            if (left!! > right) {
                inOrder = false
                break
            }
        }
        if (inOrder) return true
        val details = "$left is not less than $right"
        addFailure(details, messageFormat, messageArgs)
        return false
    }

    fun <T> containsNoDuplicates(
        actual: Iterable<T>,
        messageFormat: String
    ): Boolean = containsNoDuplicates(actual, messageFormat, EMPTY_ARGS)

    fun <T> containsNoDuplicates(
        actual: Iterable<T>,
        messageFormat: String,
        messageArgs: Array<String?>
    ): Boolean {
        var dups: ArrayList<T>? = null
        val found = HashSet<T>()
        var nodups = true
        for (e: T in actual) {
            if (!found.add(e)) {
                if (dups == null) dups = ArrayList()
                dups.add(e)
                nodups = false
            }
        }
        if (nodups) return true
        val details = StringBuilder("Duplicate elements: ")
        joinToString(details, dups!!.iterator())
        addFailure(details.toString(), messageFormat, messageArgs)
        return false
    }

    fun <T> containsAtLeastElementsIn(
        expected: Iterable<T>,
        actual: Iterable<T>,
        messageFormat: String
    ): Boolean = containsAtLeastElementsIn(expected, actual, messageFormat, EMPTY_ARGS)

    fun <T> containsAtLeastElementsIn(
        expected: Iterable<T>,
        actual: Iterable<T>,
        messageFormat: String,
        messageArgs: Array<String?>
    ): Boolean {
        val missing = ArrayList<T>()
        for (t: T in expected) missing.add(t)
        val actualValues = actual.iterator()
        while (actualValues.hasNext() && !missing.isEmpty()) {
            val actualVal = actualValues.next()
            missing.remove(actualVal)
        }
        if (missing.isEmpty()) return true
        val details = StringBuilder("Missing elements: ")
        joinToString(details, missing.iterator())
        addFailure(details.toString(), messageFormat, messageArgs)
        return false
    }

    fun <T> containsAtMostElementsIn(
        expected: Iterable<T>,
        actual: Iterable<T>,
        messageFormat: String
    ): Boolean = containsAtMostElementsIn(expected, actual, messageFormat, EMPTY_ARGS)

    fun <T> containsAtMostElementsIn(
        expected: Iterable<T>,
        actual: Iterable<T>,
        messageFormat: String,
        messageArgs: Array<String?>
    ): Boolean {
        val extra = ArrayList<T>()
        for (t: T in actual) extra.add(t)
        val expectedValues = expected.iterator()
        while (expectedValues.hasNext() && !extra.isEmpty()) {
            val expectedVal = expectedValues.next()
            extra.remove(expectedVal)
        }
        if (extra.isEmpty()) return true
        val details = StringBuilder("Extra elements: ")
        joinToString(details, extra.iterator())
        addFailure(details.toString(), messageFormat, messageArgs)
        return false
    }

    fun <T> containsExactlyElementsIn(
        expected: Iterable<T>,
        actual: Iterable<T>,
        messageFormat: String
    ): Boolean = containsExactlyElementsIn(expected, actual, messageFormat, EMPTY_ARGS)

    fun <T> containsExactlyElementsIn(
        expected: Iterable<T>,
        actual: Iterable<T>,
        messageFormat: String,
        messageArgs: Array<String?>
    ): Boolean {
        val expectedValues = expected.iterator()
        val actualValues = actual.iterator()

        // Deal with startup corner-cases
        if (!expectedValues.hasNext() && !actualValues.hasNext()) return true
        var expectedVal: T? = null
        var actualVal: T? = null
        var differenceFound = false
        while (!differenceFound && actualValues.hasNext() && expectedValues.hasNext()) {
            expectedVal = expectedValues.next()
            actualVal = actualValues.next()
            if (!eq(expectedVal, actualVal)) differenceFound = true
        }
        if (!differenceFound && !actualValues.hasNext() && !expectedValues.hasNext()) {
            return true
        }
        if (!differenceFound && !expectedValues.hasNext()) {
            // Error - have extras (only) to report
            val extra = StringBuilder("Extra elements: ")
            joinToString(extra, actualValues)
            addFailure(extra.toString(), messageFormat, messageArgs)
            return false
        }
        val missing = ArrayList<T?>()
        missing.add(expectedVal)
        while (expectedValues.hasNext()) missing.add(expectedValues.next())
        val extra = ArrayList<T?>()
        if (!missing.remove(actualVal)) extra.add(actualVal)
        while (actualValues.hasNext()) {
            actualVal = actualValues.next()
            if (!missing.remove(actualVal)) extra.add(actualVal)
        }
        if (extra.isEmpty() && missing.isEmpty()) return true
        val details = StringBuilder("Missing elements: ")
        joinToString(details, missing.iterator()).append(".  Extra elements: ")
        joinToString(details, extra.iterator())
        addFailure(details.toString(), messageFormat, messageArgs)
        return false
    }

    inline fun <reified T : Throwable> doesThrow(
        message: String,
        body: () -> Unit
    ): Boolean = doesThrow<T>(message, EMPTY_ARGS, body)

    inline fun <reified T : Throwable> doesThrow(
        messageFormat: String,
        messageArgs: Array<String?>,
        body: () -> Unit
    ): Boolean {
        val expected = T::class.java
        @Suppress("Detekt.TooGenericExceptionCaught")
        try {
            body()
            addFailure(
                "Expected exception of type ${expected.name} but no exception was thrown.",
                messageFormat,
                messageArgs
            )
            return false
        } catch (t: Throwable) {
            var underlying = t
            // InvocationTargetException is thrown by Method.invoke when the method invoked
            // throws an exception - we unwrap the exception thrown because that's the one
            // we're likely wanting to test against
            if (t is InvocationTargetException) {
                underlying = t.cause!!
            }
            if (expected.isInstance(underlying)) return true
            addFailure(
                "Expected exception of type " +
                    expected.name +
                    " but got " +
                    t.javaClass.name +
                    ".",
                messageFormat,
                messageArgs
            )
            return false
        }
    }

    interface ContingentResult<T> {
        fun ifNoThrow(body: (T) -> Unit)

        companion object {
            fun <T> of(result: T): ContingentResult<T> =
                object : ContingentResult<T> {
                    override fun ifNoThrow(body: (T) -> Unit) = body(result)
                }

            fun <T> empty(): ContingentResult<T> =
                object : ContingentResult<T> {
                    override fun ifNoThrow(body: (T) -> Unit) {}
                }
        }
    }

    fun <T> doesNotThrow(
        message: String,
        body: () -> T
    ): ContingentResult<T> = doesNotThrow(message, EMPTY_ARGS, body)

    fun <T> doesNotThrow(
        messageFormat: String,
        messageArgs: Array<String?>,
        body: () -> T
    ): ContingentResult<T> =
        @Suppress("Detekt.TooGenericExceptionCaught")
        try {
            ContingentResult.of(body())
        } catch (e: Throwable) {
            addExceptionFailure(e, messageFormat, messageArgs)
            ContingentResult.empty()
        }

    inline fun <reified T> isInstanceOf(
        actualObject: Any?,
        message: String = "Expecting a ${T::class.simpleName}, found a ${actualObject?.let { it::class.simpleName } ?: "null"}",
        block: (T) -> Unit
    ) {
        if (isInstanceOf(T::class, actualObject, message)) block(actualObject as T)
    }

    inline fun <reified T> isInstanceOf(
        actualObject: Any?,
        message: String
    ): Boolean = isInstanceOf(T::class, actualObject, message, EMPTY_ARGS)

    fun isInstanceOf(
        expectedClass: KClass<*>,
        actualObject: Any?,
        message: String
    ): Boolean = isInstanceOf(expectedClass, actualObject, message, EMPTY_ARGS)

    /**
     * Checks that an object is an instance of a provided class. When `expectedClass` is a class
     * representing a primitive type (e.g., `int.class`), checks to ensure `actualObject` isn't null,
     * and if not ensures that it's an instance of the corresponding boxed type for that primitive
     * (e.g., `Integer` for ints).
     */
    fun isInstanceOf(
        expectedClass: KClass<*>,
        actualObject: Any?,
        messageFormat: String,
        messageArgs: Array<String?>
    ): Boolean {
        /**
         * Interactions of KClass, Class, and autoboxing for primitives can be surprising.
         * Here are some important things to know:
         *
         * - Kotlin KClasses for primitive types are the same as their boxed java.lang
         *   counterparts
         *     kotlin.Boolean::class        ==   java.lang.Boolean::class
         *
         * - However, their java Classes are not the same
         *     kotlin.Boolean::class.java   !=   java.lang.Boolean::class.java
         *
         * - Kotlin uses unboxed values for non-nullable kotlin primitives, but will autobox
         *   when that value is represented as nullable
         *     val b: Boolean = true      // b::class.java     is primitive 'boolean'
         *     val b2: Boolean? = true    // b2!!::class.java  is java.lang.Boolean
         *
         * - Similar to the autoboxing above, boxed values will be automatically unboxed
         *   when represented as non-nullable
         *     val b2: Boolean? = true    // b2!!::class.java   is java.lang.Boolean
         *     b2?.let { it }             // it::class.java     is primitive 'boolean'
         *
         * - Because this method accepts values as a nullable Any?, primitive values will be
         *   automatically boxed, regardless of the value that this function was actually called with
         *     val b: Boolean = true             // b::class.java                is primitive 'boolean'
         *     isInstanceOf(actualObject = b)    // actualObject!!::class.java   is java.lang.Boolean
         */
        if (actualObject == null && expectedClass.java.isPrimitive) {
            addFailure(
                "Null passed for primitive type ${expectedClass.simpleName}",
                messageFormat,
                messageArgs
            )
            return false
        }
        if (actualObject != null && !expectedClass.isInstance(actualObject)) {
            val msg =
                "Expected instance of ${expectedClass.qualifiedName} but got " +
                    actualObject::class.qualifiedName
            addFailure(msg, messageFormat, messageArgs)
            return false
        }
        return true
    }

    companion object {
        private fun <T> joinToString(
            dst: StringBuilder,
            list: Iterator<T>
        ): StringBuilder {
            while (list.hasNext()) {
                dst.append(list.next())
                if (list.hasNext()) dst.append(", ")
            }
            return dst
        }

        val EMPTY_ARGS: Array<String?> = emptyArray()
    }
}

class Failure internal constructor(
    val context: String,
    val message: String,
    val details: String?
) {
    val label: String
        get() = message.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()[0]

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Failure) return false
        val o = other
        if (context != o.context) return false
        if (message != o.message) return false
        if (details == null && o.details != null) return false
        return if (details != null && o.details == null) false else details == null || (details == o.details)
    }

    override fun hashCode(): Int = 31 * 31 * context.hashCode() + 31 * message.hashCode() + (details?.hashCode() ?: 4507321)

    override fun toString(): String {
        val ctx = if (!context.isEmpty()) "$context: " else ""
        val deets = if (details == null) "" else " ($details)"
        return "$ctx$message$deets"
    }

    fun toMultilineString(dst: StringBuilder) {
        dst.append(message)
        if (!context.isEmpty()) dst.append("\n    Context: $context")
        if (details != null) {
            dst
                .append("\n    ")
                .append(details.replace(".  Extra", "\nExtra").replace("\n", "\n    "))
        }
        dst.append("\n")
    }
}
