package graphql.parser

import spock.lang.Specification

class MultiSourceReaderTest extends Specification {

    MultiSourceReader multiSourceLineReader

    void cleanup() {
        multiSourceLineReader.close()
    }

    def "can combine files"() {
        when:
        multiSourceLineReader = MultiSourceReader.newMultiSourceLineReader()
                .reader(readerOf("multisource/a.txt"), "PartA")
                .reader(readerOf("multisource/b.txt"), "PartB")
                .reader(readerOf("multisource/c.txt"), "PartC")
                .build()
        then:
        multiSourceLineReader.getLineNumber() == 0
        multiSourceLineReader.getOverallLineNumber() == 0
        multiSourceLineReader.getSourceName() == "PartA"

        when:
        def line = readNLines(1)
        then:
        line == "A0*******X"
        multiSourceLineReader.getLineNumber() == 1
        multiSourceLineReader.getOverallLineNumber() == 1
        multiSourceLineReader.getSourceName() == "PartA"

        when:
        line = readNLines(1)
        then:
        line == "A1*******X"
        multiSourceLineReader.getLineNumber() == 2
        multiSourceLineReader.getOverallLineNumber() == 2
        multiSourceLineReader.getSourceName() == "PartA"

        when:
        line = readNLines(4)
        then:
        line == "A2*******XA3*******XA4*******XA5*******X"
        multiSourceLineReader.getLineNumber() == 6
        multiSourceLineReader.getOverallLineNumber() == 6
        multiSourceLineReader.getSourceName() == "PartA"

        // jumps into file B
        when:
        line = readNLines(1)
        then:
        line == "B0*******X"
        multiSourceLineReader.getLineNumber() == 1
        multiSourceLineReader.getOverallLineNumber() == 7
        multiSourceLineReader.getSourceName() == "PartB"

        // jumps into file C
        when:
        readNLines(6)
        line = readNLines(1)
        then:
        line == "C1*******X"
        multiSourceLineReader.getLineNumber() == 2
        multiSourceLineReader.getOverallLineNumber() == 14
        multiSourceLineReader.getSourceName() == "PartC"

        // reads to the end and stays there
        when:
        line = readNLines(100)
        then:
        line == "C2*******XC3*******XC4*******XC5*******X"
        multiSourceLineReader.getLineNumber() == 6
        multiSourceLineReader.getOverallLineNumber() == 18
        multiSourceLineReader.getSourceName() == "PartC"

        when:
        line = readNLines(100)
        then:
        line == ""
        multiSourceLineReader.getLineNumber() == 6
        multiSourceLineReader.getOverallLineNumber() == 18
        multiSourceLineReader.getSourceName() == "PartC"
    }

    def "can read all the lines as one"() {

        when:
        multiSourceLineReader = MultiSourceReader.newMultiSourceLineReader()
                .reader(readerOf("multisource/a.graphql"), "SdlA")
                .reader(readerOf("multisource/b.graphql"), "SdlB")
                .reader(readerOf("multisource/c.graphql"), "SdlC")
                .build()

        then:

        joinLines(multiSourceLineReader.readLines()) == '''
type A {
    fieldC : String
}
type B {
    fieldC : String
}
type C {
    fieldC : String
}
'''

    }


    def "does not track data if told too"() {
        when:
        multiSourceLineReader = MultiSourceReader.newMultiSourceLineReader()
                .reader(readerOf("multisource/a.txt"), "PartA")
                .reader(readerOf("multisource/b.txt"), "PartB")
                .trackData(false)
                .build()
        then:
        readNLines(100)
        multiSourceLineReader.getData() == []
    }

    def "can track data b y default"() {
        when:
        multiSourceLineReader = MultiSourceReader.newMultiSourceLineReader()
                .reader(readerOf("multisource/a.txt"), "PartA")
                .reader(readerOf("multisource/b.txt"), "PartB")
                .build()
        then:
        readNLines(100)
        joinLines(multiSourceLineReader.getData()) == '''
A0*******X
A1*******X
A2*******X
A3*******X
A4*******X
A5*******X
B0*******X
B1*******X
B2*******X
B3*******X
B4*******X
B5*******X
'''
    }

    def "can handle zero elements"() {
        when:
        multiSourceLineReader = MultiSourceReader.newMultiSourceLineReader()
                .build()
        def lines = multiSourceLineReader.readLines()
        then:
        lines.isEmpty()
        multiSourceLineReader.getSourceName() == null
        multiSourceLineReader.getLineNumber() == 0
        multiSourceLineReader.getOverallLineNumber() == 0
    }

    def "can work out relative lines from overall lines"() {
        when:
        multiSourceLineReader = MultiSourceReader.newMultiSourceLineReader()
                .reader(readerOf("multisource/a.txt"), "PartA")
                .reader(readerOf("multisource/b.txt"), "PartB")
                .reader(readerOf("multisource/b.txt"), "PartC")
                .trackData(true)
                .build()

        readNLines(100)

        def sAndL = multiSourceLineReader.getSourceAndLineFromOverallLine(7)

        then:
        sAndL.line == 1
        sAndL.sourceName == "PartB"

        when:
        sAndL = multiSourceLineReader.getSourceAndLineFromOverallLine(3)

        then:
        sAndL.line == 3
        sAndL.sourceName == "PartA"

        when:
        sAndL = multiSourceLineReader.getSourceAndLineFromOverallLine(13)

        then:
        sAndL.line == 1
        sAndL.sourceName == "PartC"

        when:
        // wont jump past the max lines
        sAndL = multiSourceLineReader.getSourceAndLineFromOverallLine(100)

        then:
        sAndL.line == 18
        sAndL.sourceName == "PartC"
    }

    String readNLines(int count) {
        def line = ""
        for (int i = 0; i < count; i++) {
            def l = multiSourceLineReader.readLine()
            if (l == null) {
                return line
            }
            line += l
        }
        line
    }

    String joinLines(List<String> lines) {
        "\n" + lines.join("\n") + "\n"
    }

    Reader readerOf(String s) {
        def stream = this.class.classLoader.getResourceAsStream(s)
        return new InputStreamReader(stream)
    }
}
