package dev.kingdoms.client;

import dev.kingdoms.entity.CitizenEntity;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;

import java.util.List;

/**
 * Citizens look like players with the game's default wide-arm skins; the UUID picks the skin, so it
 * never changes. The skin goes into the render state too, since player-model code reads it from there.
 */
public final class CitizenRenderer extends HumanoidMobRenderer<CitizenEntity, AvatarRenderState, PlayerModel> {
    private static final List<PlayerSkin> SKINS = List.of("alex", "ari", "efe", "kai", "makena", "noor", "steve", "sunny", "zuri").stream()
        .map(name -> new PlayerSkin(new ClientAsset.ResourceTexture(Identifier.withDefaultNamespace("entity/player/wide/" + name)),
            null, null, PlayerModelType.WIDE, true))
        .toList();

    public CitizenRenderer(EntityRendererProvider.Context context) {
        super(context, new PlayerModel(context.bakeLayer(ModelLayers.PLAYER), false), 0.5f);
    }

    @Override
    public AvatarRenderState createRenderState() {
        return new AvatarRenderState();
    }

    @Override
    public void extractRenderState(CitizenEntity entity, AvatarRenderState state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        state.skin = SKINS.get(Math.floorMod(entity.getUUID().hashCode(), SKINS.size()));
    }

    @Override
    public Identifier getTextureLocation(AvatarRenderState state) {
        return state.skin.body().texturePath();
    }
}
