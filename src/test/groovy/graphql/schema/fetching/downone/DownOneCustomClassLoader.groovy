package graphql.schema.fetching.downone

/**
 * Our custom class loader works in a whacky way - its inserts a "downone" directory -
 * so other class loaders wont find this class by name if they tried
 *
 * This is of course only sensible for tests - it makes no sense otherwise
 */
class DownOneCustomClassLoader extends ClassLoader {

    @Override
    Class findClass(String name) throws ClassNotFoundException {
        name = adjustNameDownOne(name)
        byte[] b = loadClassFromFile(name)
        return defineClass(name, b, 0, b.length)
    }

    private byte[] loadClassFromFile(String className) {
        String fileName = className.replace(".", File.separator) + ".class"
        InputStream inputStream = getClass().getClassLoader().getResourceAsStream(fileName)
        if (inputStream == null) {
            throw new ClassNotFoundException(className);
        }
        byte[] buffer
        ByteArrayOutputStream byteStream = new ByteArrayOutputStream()
        int nextValue
        try {
            while ((nextValue = inputStream.read()) != -1) {
                byteStream.write(nextValue)
            }
        } catch (IOException e) {
            throw new ClassNotFoundException(className, e);
        }
        buffer = byteStream.toByteArray()
        return buffer
    }

    static String adjustNameDownOne(String className) {
        String fileName = className.replace(".", File.separator)
        File file = new File(fileName)
        file = new File(file.getParentFile(), "downone/" + file.getName())
        def newName = file.getPath().replace(File.separator, ".")
        return newName
    }
}
