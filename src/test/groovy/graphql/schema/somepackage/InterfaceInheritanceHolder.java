package graphql.schema.somepackage;

/**
 * Test fixtures for interface-extends-interface method resolution.
 * <p>
 * Tests the recursive interface search in findMethodOnPublicInterfaces:
 * a package-private class implements a package-private interface that extends
 * a public interface. The method is only declared on the public grandparent,
 * so the algorithm must recursively traverse through the package-private
 * interface to find it.
 * <p>
 * Linear chain:
 * <pre>
 *   PublicBaseInterface (public)              — defines getBaseValue()
 *        |
 *   PackagePrivateMiddleInterface            — extends PublicBaseInterface (adds nothing new)
 *        |
 *   PackagePrivateChainImpl (package-private) — implements PackagePrivateMiddleInterface
 * </pre>
 * <p>
 * Diamond pattern:
 * <pre>
 *        PublicBaseInterface (public)          — defines getBaseValue()
 *             /          \
 *  PackagePrivateBranchA   PackagePrivateBranchB  — each adds own method
 *             \          /
 *        DiamondImpl (package-private)
 * </pre>
 */
public class InterfaceInheritanceHolder {

    // --- Linear interface chain ---

    public interface PublicBaseInterface {
        String getBaseValue();
    }

    // Package-private interface extending a public interface — adds no new methods
    interface PackagePrivateMiddleInterface extends PublicBaseInterface {
    }

    // Package-private class: only implements the package-private interface.
    // getBaseValue() is declared on PublicBaseInterface — the recursive search
    // must traverse PackagePrivateMiddleInterface -> PublicBaseInterface to find it.
    static class PackagePrivateChainImpl implements PackagePrivateMiddleInterface {
        @Override
        public String getBaseValue() {
            return "baseValue";
        }
    }

    // --- Diamond pattern ---

    // Two package-private interfaces both extending PublicBaseInterface
    interface PackagePrivateBranchA extends PublicBaseInterface {
        String getBranchAValue();
    }

    interface PackagePrivateBranchB extends PublicBaseInterface {
        String getBranchBValue();
    }

    // Package-private class implementing both branches (diamond).
    // getBaseValue() is only on PublicBaseInterface — must be found through either branch.
    static class DiamondImpl implements PackagePrivateBranchA, PackagePrivateBranchB {
        @Override
        public String getBaseValue() {
            return "diamondBaseValue";
        }

        @Override
        public String getBranchAValue() {
            return "branchAValue";
        }

        @Override
        public String getBranchBValue() {
            return "branchBValue";
        }
    }

    // --- Factory methods (public entry points for tests) ---

    public static Object createChainImpl() {
        return new PackagePrivateChainImpl();
    }

    public static Object createDiamondImpl() {
        return new DiamondImpl();
    }
}
