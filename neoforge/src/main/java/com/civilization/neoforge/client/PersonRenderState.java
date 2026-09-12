package com.civilization.neoforge.client;

import com.civilization.sim.culture.Race;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;

/**
 * A settler's render state: everything vanilla's humanoid needs, plus what body
 * this one is in.
 *
 * <p>It exists because {@code getTextureLocation} is handed the render state and
 * nothing else — not the entity, not the level. Anything the skin depends on has
 * to be copied out of the entity during extraction and into here, once per
 * frame, off the render thread's critical path. So the race and the variant ride
 * along.
 *
 * <p>Two plain fields rather than an {@code Identifier} worked out up front,
 * deliberately: the state is reused frame after frame for the same body and
 * resolving the texture is a map lookup, so there is nothing to cache and one
 * less thing to keep in step with the table in {@link PersonRenderer}.
 */
public class PersonRenderState extends HumanoidRenderState {

    /** Never null; a state that has not been extracted yet reads as human. */
    public Race race = Race.HUMAN;

    /** Which of that race's skins, as the server chose it from the person's id. */
    public int skin;
}
