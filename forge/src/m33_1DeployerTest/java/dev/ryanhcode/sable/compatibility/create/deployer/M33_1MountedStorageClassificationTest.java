package dev.ryanhcode.sable.compatibility.create.deployer;

public final class M33_1MountedStorageClassificationTest {
    private M33_1MountedStorageClassificationTest() {
    }

    public static void run() {
        expect("CHEST_NOT_CAPTURED_BY_INNER_CONTRAPTION", 0, 0, 0, false);
        expect("CHEST_CAPTURED_BUT_NOT_MOUNTED", 1, 0, 0, false);
        expect("NO_MATCHING_MOUNTED_ITEM", 1, 1, 0, false);
        expect("NATIVE_EXTRACTION_RETURNED_EMPTY", 1, 1, 16, false);
        expect("NATIVE_EXTRACTION_SUCCEEDED", 1, 1, 16, true);
        expect("NATIVE_EXTRACTION_SUCCEEDED", 2, 2, 16, true);
    }

    private static void expect(final String expected, final int chests, final int mounted,
                               final int matching, final boolean extracted) {
        final String actual = DeployerRefillDecision.classify(chests, mounted, matching, extracted);
        if (!expected.equals(actual)) {
            throw new AssertionError("Expected " + expected + ", got " + actual);
        }
    }
}
