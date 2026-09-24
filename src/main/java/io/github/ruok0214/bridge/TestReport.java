package io.github.ruok0214.bridge;

import java.util.List;

/** Pass or fail per case, in the same coordinate syntax as scripts. Text only. */
public final class TestReport {
    public record Failure(int x, int y, int z, String expected, String actual, int line) {}
    public record Result(String name, List<Failure> failures) {
        public boolean passed() { return failures.isEmpty(); }
    }
    private TestReport() {}

    public static String render(Region region, List<Result> results, String stoppedReason) {
        int passed = 0;
        for (Result result : results) if (result.passed()) passed++;
        StringBuilder text = new StringBuilder("# AI Block Bridge Test Report v1\n# size: "
            + region.sizeX() + " " + region.sizeY() + " " + region.sizeZ()
            + "\n# origin: " + region.x() + " " + region.y() + " " + region.z()
            + "\n# cases " + results.size() + " | passed " + passed + " | failed " + (results.size() - passed)
            + "\n# Observation only; these lines are not paste instructions.\n"
            + "# 'expect' compares the block and only the properties written in the test.\n");
        if (stoppedReason != null) text.append("# stopped early: ").append(stoppedReason).append('\n');
        for (Result result : results) {
            text.append("\n@case ").append(result.name()).append(" : ").append(result.passed() ? "PASS" : "FAIL").append('\n');
            for (Failure failure : result.failures()) {
                text.append("  line ").append(failure.line()).append(" | ")
                    .append(failure.x()).append(' ').append(failure.y()).append(' ').append(failure.z())
                    .append("\n    expected ").append(failure.expected())
                    .append("\n    actual   ").append(failure.actual()).append('\n');
            }
        }
        return text.toString();
    }
}
