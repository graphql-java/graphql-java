package graphql.schema.somepackage;

import graphql.schema.DataFetchingEnvironment;

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

    // --- DFE interface: public interface with a method accepting DataFetchingEnvironment ---

    public interface PublicDfeInterface {
        String getDfeValue(DataFetchingEnvironment dfe);
    }

    // Package-private class implementing the public DFE interface.
    // Exercises the dfeInUse path in findMethodOnPublicInterfaces.
    static class PackagePrivateDfeImpl implements PublicDfeInterface {
        @Override
        public String getDfeValue(DataFetchingEnvironment dfe) {
            return "dfeValue";
        }
    }

    // --- Interface with multiple methods: one exists, one doesn't ---
    // Used to exercise the NoSuchMethodException catch path in findMethodOnPublicInterfaces.

    public interface PublicInterfaceWithoutTarget {
        String getUnrelatedValue();
    }

    // Package-private class implementing an interface that does NOT have the fetched property.
    // Also implements PublicBaseInterface which DOES have it.
    // The search hits NoSuchMethodException on PublicInterfaceWithoutTarget, then finds it on PublicBaseInterface.
    static class PackagePrivateMultiInterfaceImpl implements PublicInterfaceWithoutTarget, PublicBaseInterface {
        @Override
        public String getUnrelatedValue() {
            return "unrelated";
        }

        @Override
        public String getBaseValue() {
            return "foundViaSecondInterface";
        }
    }

    // --- Factory methods (public entry points for tests) ---

    public static Object createChainImpl() {
        return new PackagePrivateChainImpl();
    }

    public static Object createDiamondImpl() {
        return new DiamondImpl();
    }

    public static Object createDfeImpl() {
        return new PackagePrivateDfeImpl();
    }

    public static Object createMultiInterfaceImpl() {
        return new PackagePrivateMultiInterfaceImpl();
    }
}
