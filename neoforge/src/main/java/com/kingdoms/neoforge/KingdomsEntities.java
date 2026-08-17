package com.kingdoms.neoforge;

import com.kingdoms.neoforge.entity.PersonEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

/** Entity registration. */
public final class KingdomsEntities {

    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, KingdomsMod.MOD_ID);

    public static final Supplier<EntityType<PersonEntity>> PERSON = ENTITY_TYPES.register(
            "person",
            () -> EntityType.Builder.of(PersonEntity::new, MobCategory.CREATURE)
                    .sized(0.6F, 1.8F)
                    .build(ResourceKey.create(Registries.ENTITY_TYPE,
                            Identifier.fromNamespaceAndPath(KingdomsMod.MOD_ID, "person"))));

    private KingdomsEntities() {
    }

    public static void createAttributes(EntityAttributeCreationEvent event) {
        event.put(PERSON.get(), PersonEntity.createAttributes().build());
    }
}
