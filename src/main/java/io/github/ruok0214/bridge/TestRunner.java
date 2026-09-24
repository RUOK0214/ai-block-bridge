package io.github.ruok0214.bridge;

import com.mojang.brigadier.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

/** Drives inputs and checks outputs across server ticks. States are parsed up front so a typo never touches the world. */
public final class TestRunner {
    private record Action(TestScript.Step step, BlockState state, Map<Property<?>, Comparable<?>> properties) {}

    private final Region region;
    private final List<TestScript.Case> cases;
    private final List<List<Action>> actions = new ArrayList<>();
    private final List<TestReport.Result> results = new ArrayList<>();
    private List<TestReport.Failure> failures = new ArrayList<>();
    private int caseIndex, stepIndex, wait;
    private String stoppedReason;

    public TestRunner(ServerLevel level, Region region, List<TestScript.Case> cases) {
        this.region = region;
        this.cases = cases;
        for (TestScript.Case testCase : cases) {
            var parsed = new ArrayList<Action>();
            for (TestScript.Step step : testCase.steps()) {
                if (step.kind().equals(TestScript.WAIT)) { parsed.add(new Action(step, null, Map.of())); continue; }
                try {
                    StringReader reader = new StringReader(step.state());
                    var result = BlockStateParser.parseForBlock(level.registryAccess().lookupOrThrow(Registries.BLOCK), reader, false);
                    reader.skipWhitespace();
                    if (reader.canRead()) throw new IllegalArgumentException(Messages.text("ai_block_bridge.error.trailing_state"));
                    parsed.add(new Action(step, result.blockState(), result.properties()));
                } catch (Exception ex) {
                    throw new IllegalArgumentException(Messages.text("ai_block_bridge.error.line", step.line(), ex.getMessage()));
                }
            }
            actions.add(List.copyOf(parsed));
        }
    }

    public boolean done() { return caseIndex >= cases.size(); }

    public void tick(ServerLevel level) {
        if (wait > 0 && --wait > 0) return;
        while (!done()) {
            List<Action> steps = actions.get(caseIndex);
            if (stepIndex >= steps.size()) { finishCase(); continue; }
            Action action = steps.get(stepIndex++);
            if (action.step().kind().equals(TestScript.WAIT)) { wait = action.step().ticks(); return; }
            BlockPos pos = new BlockPos(region.x() + action.step().x(), region.y() + action.step().y(), region.z() + action.step().z());
            if (action.step().kind().equals(TestScript.SET)) apply(level, pos, action);
            else check(level, pos, action);
        }
    }

    private void apply(ServerLevel level, BlockPos pos, Action action) {
        BlockState actual = level.getBlockState(pos);
        if (!actual.is(action.state().getBlock())) { fail(pos, action, actual); return; }
        BlockState updated = actual;
        for (Map.Entry<Property<?>, Comparable<?>> entry : action.properties().entrySet()) {
            updated = with(updated, entry.getKey(), entry.getValue());
        }
        level.setBlock(pos, updated, Block.UPDATE_ALL);
        level.updateNeighborsAt(pos, updated.getBlock());
    }

    private void check(ServerLevel level, BlockPos pos, Action action) {
        BlockState actual = level.getBlockState(pos);
        if (!actual.is(action.state().getBlock())) { fail(pos, action, actual); return; }
        for (Map.Entry<Property<?>, Comparable<?>> entry : action.properties().entrySet()) {
            Property<?> property = entry.getKey();
            if (actual.hasProperty(property) && actual.getValue(property).equals(entry.getValue())) continue;
            fail(pos, action, actual);
            return;
        }
    }

    @SuppressWarnings("unchecked")
    private static <T extends Comparable<T>> BlockState with(BlockState state, Property<?> property, Comparable<?> value) {
        return state.setValue((Property<T>) property, (T) value);
    }

    private void fail(BlockPos pos, Action action, BlockState actual) {
        failures.add(new TestReport.Failure(action.step().x(), action.step().y(), action.step().z(),
            action.step().state(), BlockStateParser.serialize(actual), action.step().line()));
    }

    private void finishCase() {
        results.add(new TestReport.Result(cases.get(caseIndex).name(), List.copyOf(failures)));
        failures = new ArrayList<>();
        caseIndex++;
        stepIndex = 0;
    }

    public void stop(String reason) {
        if (done()) return;
        stoppedReason = reason;
        while (!done()) {
            failures.add(new TestReport.Failure(0, 0, 0, "completed test", "not completed: " + reason, 0));
            finishCase();
        }
    }

    public String report() { return TestReport.render(region, List.copyOf(results), stoppedReason); }
}
