package graphql.schema.bytecode;

@SuppressWarnings({"unused", "SpellCheckingInspection"})
public class PrimitivePojo {

    private final byte pbyte;
    private final short pshort;
    private final int pint;
    private final long plong;
    private final float pfloat;
    private final double pdouble;
    private final char pchar;
    private final boolean pbool;

    public PrimitivePojo(byte pbyte, short pshort, int pint, long plong, float pfloat, double pdouble, char pchar, boolean pbool) {
        this.pbyte = pbyte;
        this.pshort = pshort;
        this.pint = pint;
        this.plong = plong;
        this.pfloat = pfloat;
        this.pdouble = pdouble;
        this.pchar = pchar;
        this.pbool = pbool;
    }

    public byte getPbyte() {
        return pbyte;
    }

    public short getPshort() {
        return pshort;
    }

    public int getPint() {
        return pint;
    }

    public long getPlong() {
        return plong;
    }

    public float getPfloat() {
        return pfloat;
    }

    public double getPdouble() {
        return pdouble;
    }

    public char getPchar() {
        return pchar;
    }

    public boolean isPbool() {
        return pbool;
    }
}
