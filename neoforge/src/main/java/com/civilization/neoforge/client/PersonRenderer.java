package com.civilization.neoforge.client;

import com.civilization.neoforge.entity.PersonEntity;
import com.civilization.neoforge.entity.PersonSkins;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.Identifier;

/**
 * Renders a simulated person as a player-shaped humanoid, skinned by race.
 *
 * <p>The model is vanilla's — {@link ModelLayers#PLAYER}, the wide (Steve) build
 * — and stays vanilla's for every race: an orc is a big man rather than a
 * different shape, and a goblin who needed his own geometry would need his own
 * animations with it. Only the sheet changes, and which sheet is
 * {@link PersonSkins}'s opinion rather than this class's.
 *
 * <p>All this does with it is the copying: {@code getTextureLocation} is handed a
 * render state and nothing else, so the race has to be lifted off the entity
 * during extraction. See {@link PersonRenderState}.
 */
public final class PersonRenderer
        extends HumanoidMobRenderer<PersonEntity, PersonRenderState, HumanoidModel<PersonRenderState>> {

    public PersonRenderer(EntityRendererProvider.Context context) {
        super(context, new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER)), 0.5F);
    }

    @Override
    public PersonRenderState createRenderState() {
        return new PersonRenderState();
    }

    @Override
    public void extractRenderState(PersonEntity entity, PersonRenderState state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        state.race = entity.race();
        state.skin = entity.skin();
    }

    @Override
    public Identifier getTextureLocation(PersonRenderState state) {
        return PersonSkins.sheetFor(state.race, state.skin);
    }
}
