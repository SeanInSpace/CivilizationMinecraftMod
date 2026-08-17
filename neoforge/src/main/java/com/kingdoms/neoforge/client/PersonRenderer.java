package com.kingdoms.neoforge.client;

import com.kingdoms.neoforge.entity.PersonEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.resources.Identifier;

/**
 * Renders a simulated person as a generic player-shaped humanoid with the
 * default Steve skin — vanilla model, vanilla texture, no custom assets.
 */
public final class PersonRenderer
        extends HumanoidMobRenderer<PersonEntity, HumanoidRenderState, HumanoidModel<HumanoidRenderState>> {

    private static final Identifier STEVE =
            Identifier.withDefaultNamespace("textures/entity/player/wide/steve.png");

    public PersonRenderer(EntityRendererProvider.Context context) {
        super(context, new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER)), 0.5F);
    }

    @Override
    public HumanoidRenderState createRenderState() {
        return new HumanoidRenderState();
    }

    @Override
    public Identifier getTextureLocation(HumanoidRenderState state) {
        return STEVE;
    }
}
