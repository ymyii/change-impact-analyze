package io.github.dependencyanalysis.callgraph.topology;

import io.github.dependencyanalysis.callgraph.model.CodeOrigin;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.callgraph.Context;
import com.ibm.wala.types.Descriptor;
import com.ibm.wala.types.TypeName;
import com.ibm.wala.core.util.strings.Atom;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Tests deterministic CGNode-level Call Graph topology. */
class CallGraphTopologyAnalyzerTest {

    /** Expected full node count including WALA sentinels. */
    private static final int EXPECTED_NODE_COUNT = 13;

    /** Expected full edge count including WALA sentinel edges. */
    private static final int EXPECTED_EDGE_COUNT = 17;

    /** Expected entry caller related CGNode count. */
    private static final int ENTRY_RELATED_NODES = 6;

    /** Expected entry caller distinct callee Method count. */
    private static final int ENTRY_DISTINCT_METHODS = 5;

    /** Expected shared Method related Context count. */
    private static final int SHARED_RELATED_NODES = 2;

    @Test
    void ranksExactNodesAggregatesChildrenAndBuildsNodePathsAndCycles() {
        final GraphFixture fixture = new GraphFixture();
        final CGNode fakeRoot = fixture.node("wala.FakeRoot", "root", "()V");
        final CGNode fakeWorld = fixture.node(
                "wala.FakeWorld", "world", "()V");
        final CGNode entry = fixture.node("app.Entry", "run", "()V");
        final CGNode other = fixture.node("app.Other", "run", "()V");
        final CGNode alpha = fixture.node("app.Alpha", "call", "()V");
        final CGNode beta = fixture.node("app.Beta", "call", "()V");
        final CGNode target = fixture.node("app.Target", "hit", "()V");
        final CGNode sharedOne = fixture.node(
                "app.Shared", "accept", "()V");
        final CGNode sharedTwo = fixture.node(
                "app.Shared", "accept", "()V");
        final CGNode self = fixture.node("app.Self", "loop", "()V");
        final CGNode cycleA = fixture.node("app.CycleA", "a", "()V");
        final CGNode cycleB = fixture.node("app.CycleB", "b", "()V");
        final CGNode sentinelOnly = fixture.node(
                "app.SentinelOnly", "call", "()V");
        fixture.fakeRoot(fakeRoot).fakeWorld(fakeWorld)
                .entrypoint(entry).entrypoint(other)
                .edge(fakeRoot, fakeWorld).edge(fakeRoot, entry)
                .edge(fakeRoot, sentinelOnly).edge(fakeWorld, target)
                .edge(entry, alpha).edge(entry, beta)
                .edge(alpha, target).edge(beta, target)
                .edge(entry, sharedOne).edge(entry, sharedTwo)
                .edge(other, sharedTwo)
                .edge(entry, self).edge(self, self)
                .edge(entry, cycleA).edge(cycleA, cycleB)
                .edge(cycleB, cycleA)
                .edge(sentinelOnly, other);

        final CallGraphTopologySnapshot topology =
                new CallGraphTopologyAnalyzer().analyze(
                        fixture.graph(), ignored -> CodeOrigin.PROJECT);

        assertThat(topology.nodeCount()).isEqualTo(EXPECTED_NODE_COUNT);
        assertThat(topology.edgeCount()).isEqualTo(EXPECTED_EDGE_COUNT);
        assertThat(topology.entrypointCount()).isEqualTo(2);
        assertThat(topology.topCallers()).extracting(value ->
                value.node().method().owner()).contains(
                        "wala.FakeRoot", "wala.FakeWorld");
        assertThat(ranked(topology.topCallers(), fakeRoot).node()
                .sentinelRole()).isEqualTo(
                        CallGraphNodeSentinelRole.FAKE_ROOT);
        assertThat(ranked(topology.topCallers(), fakeWorld).node()
                .sentinelRole()).isEqualTo(
                        CallGraphNodeSentinelRole.FAKE_WORLD_CLINIT);
        final CallGraphRankedNode entryRank = ranked(
                topology.topCallers(), entry);
        assertThat(entryRank.relatedCgNodeCount())
                .isEqualTo(ENTRY_RELATED_NODES);
        assertThat(entryRank.distinctRelatedMethodCount())
                .isEqualTo(ENTRY_DISTINCT_METHODS);
        assertThat(entryRank.rawEdgeCount()).isEqualTo(ENTRY_RELATED_NODES);
        assertThat(entryRank.topRelatedMethods().get(0).method().owner())
                .isEqualTo("app.Shared");
        assertThat(entryRank.topRelatedMethods().get(0)
                .relatedCgNodeCount()).isEqualTo(SHARED_RELATED_NODES);
        assertThat(entryRank.topRelatedMethods().get(0).relatedNodes())
                .extracting(CallGraphNodeIdentity::graphNodeId)
                .containsExactly(sharedOne.getGraphNodeId(),
                        sharedTwo.getGraphNodeId());

        final CallGraphRankedNode sharedTwoRank = ranked(
                topology.topCallees(), sharedTwo);
        assertThat(sharedTwoRank.relatedCgNodeCount()).isEqualTo(2);
        assertThat(sharedTwoRank.reachabilityPaths().stream()
                .filter(path -> path.rootKind()
                        == CallGraphPathRootKind.DECLARED_ENTRYPOINT))
                .extracting(path -> path.root().method().owner())
                .containsExactly(
                        "app.Entry", "app.Other");
        final CallGraphRankedNode targetRank = ranked(
                topology.topCallees(), target);
        final CallGraphNodeReachabilityPath declaredTargetPath = targetRank
                .reachabilityPaths().stream().filter(path -> path.rootKind()
                        == CallGraphPathRootKind.DECLARED_ENTRYPOINT)
                .findFirst().orElseThrow();
        assertThat(declaredTargetPath.steps())
                .extracting(step -> step.node().method().owner())
                .containsExactly("app.Entry", "app.Alpha", "app.Target");
        final CallGraphNodeReachabilityPath fakeRootTargetPath = targetRank
                .reachabilityPaths().stream().filter(path -> path.rootKind()
                        == CallGraphPathRootKind.FAKE_ROOT)
                .findFirst().orElseThrow();
        assertThat(fakeRootTargetPath.steps())
                .extracting(step -> step.node().method().owner())
                .containsExactly("wala.FakeRoot", "wala.FakeWorld",
                        "app.Target");
        assertThat(fakeRootTargetPath.steps())
                .extracting(step -> step.node().sentinelRole())
                .containsExactly(CallGraphNodeSentinelRole.FAKE_ROOT,
                        CallGraphNodeSentinelRole.FAKE_WORLD_CLINIT,
                        CallGraphNodeSentinelRole.NONE);
        assertThat(ranked(topology.topCallers(), self).cycle()).isTrue();
        assertThat(ranked(topology.topCallers(), cycleA).cycle()).isTrue();
        assertThat(ranked(topology.topCallers(), cycleB).cycle()).isTrue();
        final CallGraphNodeReachabilityPath sentinelOnlyPath = ranked(
                topology.topCallers(), sentinelOnly).reachabilityPaths()
                .stream().filter(path -> path.rootKind()
                        == CallGraphPathRootKind.FAKE_ROOT)
                .findFirst().orElseThrow();
        assertThat(sentinelOnlyPath.steps())
                .extracting(step -> step.node().method().owner())
                .containsExactly("wala.FakeRoot", "app.SentinelOnly");
        assertThat(entryRank.ir().isAvailable()).isFalse();
        assertThat(entryRank.ir().reason()).contains("unavailable");
    }

    @Test
    void truncatesEqualNodeRanksByStableNodeIdentity() {
        final GraphFixture fixture = new GraphFixture();
        final List<String> expected = new ArrayList<>();
        for (int index = 0; index <= CallGraphTopologyAnalyzer.RANK_LIMIT;
             index++) {
            final String suffix = String.format("%02d", index);
            final CGNode caller = fixture.node(
                    "app.Caller" + suffix, "call", "()V");
            final CGNode callee = fixture.node(
                    "app.Callee" + suffix, "accept", "()V");
            fixture.edge(caller, callee);
            if (index < CallGraphTopologyAnalyzer.RANK_LIMIT) {
                expected.add("app.Caller" + suffix);
            }
        }

        final CallGraphTopologySnapshot topology =
                new CallGraphTopologyAnalyzer().analyze(
                        fixture.graph(), ignored -> CodeOrigin.PROJECT);

        assertThat(topology.topCallers())
                .hasSize(CallGraphTopologyAnalyzer.RANK_LIMIT)
                .extracting(value -> value.node().method().owner())
                .containsExactlyElementsOf(expected);
    }

    @Test
    void preservesWalaSyntheticFlagForSummaryDeclaredOnJdkClass() {
        final GraphFixture fixture = new GraphFixture();
        final CGNode caller = fixture.node("app.Entry", "run", "()V");
        final CGNode summary = fixture.walaSyntheticNode(
                "java.lang.reflect.Method", "invoke",
                "(Ljava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;");
        fixture.entrypoint(caller).edge(caller, summary);

        final CallGraphTopologySnapshot topology =
                new CallGraphTopologyAnalyzer().analyze(
                        fixture.graph(), type -> type.getName().toString()
                        .contains("java/lang")
                        ? CodeOrigin.JDK : CodeOrigin.PROJECT);

        final CallGraphRankedNode rankedSummary = ranked(
                topology.topCallees(), summary);
        assertThat(rankedSummary.node().method().origin())
                .isEqualTo(CodeOrigin.JDK);
        assertThat(rankedSummary.node().walaSynthetic()).isTrue();
    }

    @Test
    void includesSentinelsInCyclesWithoutCountingThemAsEntrypoints() {
        final GraphFixture fixture = new GraphFixture();
        final CGNode fakeRoot = fixture.node(
                "wala.FakeRoot", "root", "()V");
        final CGNode fakeWorld = fixture.node(
                "wala.FakeWorld", "world", "()V");
        final CGNode ordinary = fixture.node(
                "app.Ordinary", "call", "()V");
        fixture.fakeRoot(fakeRoot).fakeWorld(fakeWorld)
                .entrypoint(fakeRoot).entrypoint(ordinary)
                .edge(fakeRoot, ordinary).edge(ordinary, fakeRoot)
                .edge(fakeWorld, fakeWorld);

        final CallGraphTopologySnapshot topology =
                new CallGraphTopologyAnalyzer().analyze(
                        fixture.graph(), ignored -> CodeOrigin.PROJECT);

        assertThat(topology.entrypointCount()).isEqualTo(1);
        assertThat(ranked(topology.topCallers(), fakeRoot).cycle()).isTrue();
        assertThat(ranked(topology.topCallers(), fakeWorld).cycle()).isTrue();
        assertThat(ranked(topology.topCallers(), ordinary).cycle()).isTrue();
    }

    private CallGraphRankedNode ranked(
            final List<CallGraphRankedNode> values,
            final CGNode node) {
        return values.stream().filter(value ->
                        value.node().graphNodeId() == node.getGraphNodeId())
                .findFirst().orElseThrow(() -> new AssertionError(
                        "Missing ranked CGNode: " + node));
    }

    /** Minimal typed WALA Call Graph fixture. */
    private static final class GraphFixture {

        /** Nodes in insertion order. */
        private final List<CGNode> nodes = new ArrayList<>();

        /** Outgoing edges. */
        private final Map<CGNode, Set<CGNode>> outgoing =
                new IdentityHashMap<>();

        /** Declared entrypoints. */
        private final Set<CGNode> entrypoints = new LinkedHashSet<>();

        /** Shared Methods by stable identity. */
        private final Map<String, IMethod> methods = new LinkedHashMap<>();

        /** Fake root. */
        private CGNode fakeRoot;

        /** Fake world-clinit. */
        private CGNode fakeWorld;

        CGNode node(
                final String owner,
                final String name,
                final String descriptor) {
            return node(owner, name, descriptor, false);
        }

        CGNode walaSyntheticNode(
                final String owner,
                final String name,
                final String descriptor) {
            return node(owner, name, descriptor, true);
        }

        private CGNode node(
                final String owner,
                final String name,
                final String descriptor,
                final boolean walaSynthetic) {
            final String key = owner + "#" + name + descriptor
                    + "|walaSynthetic=" + walaSynthetic;
            final IMethod method = methods.computeIfAbsent(
                    key, ignored -> method(
                            owner, name, descriptor, walaSynthetic));
            final int nodeId = nodes.size();
            final Context context = proxy(Context.class,
                    (proxy, called, args) -> objectMethod(
                            proxy, called, args, "context-" + nodeId));
            final CGNode node = proxy(CGNode.class, (proxy, called, args) ->
                    switch (called.getName()) {
                        case "getMethod" -> method;
                        case "getGraphNodeId" -> nodeId;
                        case "getContext" -> context;
                        case "getIR" -> null;
                        default -> objectMethod(proxy, called, args,
                                key + "@context-" + nodeId);
                    });
            nodes.add(node);
            outgoing.put(node, new LinkedHashSet<>());
            return node;
        }

        GraphFixture edge(final CGNode caller, final CGNode callee) {
            outgoing.get(caller).add(callee);
            return this;
        }

        GraphFixture entrypoint(final CGNode node) {
            entrypoints.add(node);
            return this;
        }

        GraphFixture fakeRoot(final CGNode node) {
            fakeRoot = node;
            return this;
        }

        GraphFixture fakeWorld(final CGNode node) {
            fakeWorld = node;
            return this;
        }

        CallGraph graph() {
            return proxy(CallGraph.class, (proxy, called, args) -> switch (
                    called.getName()) {
                case "iterator" -> nodes.iterator();
                case "getNumberOfNodes" -> nodes.size();
                case "getSuccNodes" -> outgoing.getOrDefault(
                        (CGNode) args[0], Set.of()).iterator();
                case "getEntrypointNodes" -> entrypoints;
                case "getFakeRootNode" -> fakeRoot;
                case "getFakeWorldClinitNode" -> fakeWorld;
                default -> objectMethod(proxy, called, args, "test-graph");
            });
        }

        private IMethod method(
                final String owner,
                final String name,
                final String descriptor,
                final boolean walaSynthetic) {
            final IClass declaringClass = proxy(IClass.class,
                    (proxy, called, args) -> called.getName().equals("getName")
                            ? TypeName.findOrCreate("L"
                            + owner.replace('.', '/'))
                            : objectMethod(proxy, called, args, owner));
            return proxy(IMethod.class, (proxy, called, args) -> switch (
                    called.getName()) {
                case "getDeclaringClass" -> declaringClass;
                case "getName" -> Atom.findOrCreateUnicodeAtom(name);
                case "getDescriptor" -> Descriptor.findOrCreateUTF8(
                        descriptor);
                case "isWalaSynthetic" -> walaSynthetic;
                default -> objectMethod(proxy, called, args,
                        owner + "#" + name + descriptor);
            });
        }
    }

    private static Object objectMethod(
            final Object proxy,
            final Method method,
            final Object[] arguments,
            final String label) {
        return switch (method.getName()) {
            case "toString" -> label;
            case "hashCode" -> System.identityHashCode(proxy);
            case "equals" -> proxy == arguments[0];
            default -> defaultValue(method.getReturnType());
        };
    }

    private static Object defaultValue(final Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == char.class) {
            return '\0';
        }
        return 0;
    }

    private static <T> T proxy(
            final Class<T> type,
            final java.lang.reflect.InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(
                CallGraphTopologyAnalyzerTest.class.getClassLoader(),
                new Class<?>[]{type}, handler));
    }
}
