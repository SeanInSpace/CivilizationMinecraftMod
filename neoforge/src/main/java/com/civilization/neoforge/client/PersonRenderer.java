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

    /**
     * How near you have to be for a name to be worth drawing: four blocks.
     *
     * <p>Squared, because that is the form the renderer is handed and squaring a
     * constant once is cheaper than rooting a distance every frame for every
     * settler in view.
     */
    private static final double NAME_RANGE_SQR = 4.0 * 4.0;

    /**
     * One name at a time, and only the one you are looking at.
     *
     * <p>The plate used to be a pure distance rule — every citizen inside eight
     * blocks carried one — and three settlers standing together therefore drew
     * three plates through each other. The playtest photographed the result and
     * it read {@code BrihtwaCuthberRingwJoaderTrader}: not three names, one
     * ruined one. A label nobody can read is worse than no label, because it is
     * also in the way of the faces behind it.
     *
     * <p>So the rule is vanilla's own, which this class had been overriding
     * without meaning to. {@code EntityRenderer.shouldShowName} draws a custom
     * name when the entity is {@code crosshairPickEntity} — the thing under your
     * crosshair — and that is exactly the question "which of these people do you
     * mean". {@code LivingEntityRenderer} then replaces that rule with a
     * team-visibility one that answers yes for any teamless mob you can see, so
     * a humanoid renderer never inherits the crosshair behaviour at all. Saying
     * it here is the only way to have it.
     *
     * <p>The range is four blocks rather than the old eight and is the second
     * half of the same thought: the crosshair alone would put a name on somebody
     * across a field, which is a label on a speck. Four is close enough to speak
     * to, which is the only distance a name is an answer to anything.
     *
     * <p>Sneaking still hides it, by the same courtesy vanilla extends to
     * players — a settler who has crouched out of the way is not asking to be
     * identified.
     *
     * @param distanceSqr squared distance from the camera, as the dispatcher
     *                    measures it
     */
    @Override
    protected boolean shouldShowName(PersonEntity person, double distanceSqr) {
        if (person.isDiscrete() || !person.hasCustomName()) {
            return false;
        }
        return distanceSqr <= NAME_RANGE_SQR
                && person == this.entityRenderDispatcher.crosshairPickEntity;
    }
}
