I have already asked IntelliJ to infer nullity on this class. Can you help me make this more accurate.

Note that JSpecify is already used in this repository so it's already imported.

If you see a builder static class, you can label it `@NullUnmarked` and not need to do anymore for this static class in terms of annotations.

Analyze this Java class and add JSpecify annotations based on:
1. Set the class to be `@NullMarked`
2. Remove all the redundant `@NonNull` annotations that IntelliJ added
3. Check Javadoc @param tags mentioning "null", "nullable", "may be null"
4. Check Javadoc @return tags mentioning "null", "optional", "if available"
5. GraphQL specification semantics (nullable fields, non-null by default)
6. Method implementations that return null or check for null

IntelliJ's infer nullity code analysis isn't comprehensive so feel free to make corrections.

Finally, please check all of this works, by running the NullAway compile check.

If you find NullAway errors, try and make the smallest possible change to fix them. If you must, you can use assertNotNull. Make sure to include a message as well.

Finally, can you remove this class from the JSpecifyAnnotationsCheck as an exemption. Thanks

You do not need to run the JSpecifyAnnotationsCheck. Removing the completed class is enough.

Remember to delete all unused imports when you're done from the class you've just annotated.