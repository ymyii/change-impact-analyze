package io.github.dependencyanalysis.callgraph;

import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.CallGraph;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Immutable post-fixed-point ServiceLoader evidence. */
final class ServiceLoaderModelMetadata {

    /** Synthetic model node to stable service label. */
    private final Map<CGNode, String> modeledNodes;

    /** Stable limitations. */
    private final List<ModelLimitation> limitations;

    /** CHA-created provider constructor callsites. */
    private final Map<ChaCallGraphStrategy.ServiceLoaderEdgeKey, String>
            chaEdges;

    private ServiceLoaderModelMetadata(
            final Map<CGNode, String> nodes,
            final List<ModelLimitation> values,
            final Map<ChaCallGraphStrategy.ServiceLoaderEdgeKey, String>
                    providerEdges) {
        modeledNodes = Map.copyOf(Objects.requireNonNull(nodes, "nodes"));
        limitations = Objects.requireNonNull(values, "values").stream()
                .distinct().sorted().toList();
        chaEdges = Map.copyOf(Objects.requireNonNull(
                providerEdges, "providerEdges"));
    }

    static ServiceLoaderModelMetadata snapshot(
            final CallGraph graph,
            final ZeroCfaServiceLoaderModel model,
            final List<ModelLimitation> limitations) {
        final Map<CGNode, String> nodes = new LinkedHashMap<>();
        for (CGNode node : graph) {
            if (model.models(node)) {
                nodes.put(node, model.serviceLabel(node));
            }
        }
        return new ServiceLoaderModelMetadata(nodes, limitations, Map.of());
    }

    static ServiceLoaderModelMetadata snapshot(
            final CallGraph graph,
            final OptimizedServiceLoaderModel model,
            final List<ModelLimitation> limitations) {
        final Map<CGNode, String> nodes = new LinkedHashMap<>();
        for (CGNode node : graph) {
            if (model.models(node)) {
                nodes.put(node, model.serviceLabel(node));
            }
        }
        return new ServiceLoaderModelMetadata(nodes, limitations, Map.of());
    }

    static ServiceLoaderModelMetadata snapshot(
            final CallGraph graph,
            final KObjServiceLoaderModel model,
            final List<ModelLimitation> limitations) {
        final Map<CGNode, String> nodes = new LinkedHashMap<>();
        for (CGNode node : graph) {
            if (model.models(node)) {
                nodes.put(node, model.serviceLabel(node));
            }
        }
        return new ServiceLoaderModelMetadata(nodes, limitations, Map.of());
    }

    static ServiceLoaderModelMetadata snapshot(
            final CallGraph graph,
            final RtaServiceLoaderModel model,
            final List<ModelLimitation> limitations) {
        final Map<CGNode, String> nodes = new LinkedHashMap<>();
        for (CGNode node : graph) {
            if (model.models(node)) {
                nodes.put(node, model.serviceLabel(node));
            }
        }
        return new ServiceLoaderModelMetadata(nodes, limitations, Map.of());
    }

    static ServiceLoaderModelMetadata empty(
            final List<ModelLimitation> limitations) {
        return new ServiceLoaderModelMetadata(
                Map.of(), limitations, Map.of());
    }

    static ServiceLoaderModelMetadata cha(
            final Map<ChaCallGraphStrategy.ServiceLoaderEdgeKey, String>
                    edges) {
        return new ServiceLoaderModelMetadata(Map.of(), List.of(), edges);
    }

    List<ModelLimitation> limitations() {
        return limitations;
    }

    boolean models(final CGNode node) {
        return modeledNodes.containsKey(node);
    }

    String serviceLabel(final CGNode node) {
        return modeledNodes.get(node);
    }

    String chaService(
            final CGNode caller,
            final com.ibm.wala.classLoader.CallSiteReference site,
            final CGNode callee) {
        return chaEdges.get(new ChaCallGraphStrategy.ServiceLoaderEdgeKey(
                caller.getMethod().getReference().toString(),
                site.getProgramCounter(),
                callee.getMethod().getReference().toString()));
    }
}
