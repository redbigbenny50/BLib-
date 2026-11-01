package com.blib.api.client.render.v1;

import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;

import com.blib.api.client.model.v1.AzBone;
import com.blib.api.client.render.v1.layer.AzRenderLayer;

public class AzLayerRenderer<K, T> {

    private final Supplier<Collection<AzRenderLayer<K, T>>> renderLayerSupplier;

    public AzLayerRenderer(Supplier<Collection<AzRenderLayer<K, T>>> renderLayerSupplier) {
        this.renderLayerSupplier = renderLayerSupplier;
    }

    protected void preApplyRenderLayers(AzRendererPipelineContext<K, T> context) {
        for (var renderLayer : renderLayerSupplier.get()) {
            renderLayer.preRender(context);
        }
    }

    /**
     * Called once per bone per frame, so it walks list-backed layer collections by index rather than allocating an
     * iterator each time.
     */
    public void applyRenderLayersForBone(AzRendererPipelineContext<K, T> context, AzBone bone) {
        var layers = renderLayerSupplier.get();

        if (layers instanceof List<AzRenderLayer<K, T>> list) {
            for (int i = 0, size = list.size(); i < size; i++) {
                renderLayerForBone(context, list.get(i), bone);
            }
        } else {
            for (var renderLayer : layers) {
                renderLayerForBone(context, renderLayer, bone);
            }
        }
    }

    protected void applyRenderLayers(AzRendererPipelineContext<K, T> context) {
        for (var renderLayer : renderLayerSupplier.get()) {
            var sorter = unsortedSorter(context, renderLayer);

            if (sorter == null) {
                renderLayer.render(context);
                continue;
            }

            sorter.pushUnsorted();

            try {
                renderLayer.render(context);
            } finally {
                sorter.popUnsorted();
            }
        }
    }

    private void renderLayerForBone(AzRendererPipelineContext<K, T> context, AzRenderLayer<K, T> layer, AzBone bone) {
        var sorter = unsortedSorter(context, layer);

        if (sorter == null) {
            layer.renderForBone(context, bone);
            return;
        }

        sorter.pushUnsorted();

        try {
            layer.renderForBone(context, bone);
        } finally {
            sorter.popUnsorted();
        }
    }

    /**
     * AzureLib 3.2.3 (ported Oct 31) - the active sorter, when this layer opts out of translucency sorting
     * ({@link AzRenderLayer#sortsTranslucency()}); {@code null} otherwise, which is the cheap common case.
     */
    private static AzTranslucencySorter unsortedSorter(AzRendererPipelineContext<?, ?> context, AzRenderLayer<?, ?> layer) {
        if (!layer.sortsTranslucency() && context.multiBufferSource() instanceof AzTranslucencySorter sorter && sorter.isActive()) {
            return sorter;
        }

        return null;
    }
}
