package at.petrak.hexcasting.client.render;


import at.petrak.hexcasting.api.casting.math.HexPattern;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class PatternRenderer {

    public static void renderPattern(HexPattern pattern, PoseStack ps, PatternRenderSettings patSets, PatternColors patColors, double seed, int resPerUnit) {
        renderPattern(pattern, ps, null, patSets, patColors, seed, resPerUnit);
    }

    public static void renderPattern(HexPattern pattern, PoseStack ps, @Nullable WorldlyBits worldlyBits, PatternRenderSettings patSets, PatternColors patColors, double seed, int resPerUnit){
        var oldShader = RenderSystem.getShader();
        HexPatternPoints staticPoints = HexPatternPoints.getStaticPoints(pattern, patSets, seed);

        boolean shouldRenderDynamic = true;

        // only do texture rendering if it's static and has solid colors
        if(patSets.speed == 0 && PatternTextureManager.useTextures && patColors.innerStartColor == patColors.innerEndColor
        && patColors.outerStartColor == patColors.outerEndColor){
            boolean didRender = renderPatternTexture(pattern, ps, worldlyBits, patSets, patColors, seed, resPerUnit);
            if(didRender) shouldRenderDynamic = false;
        }
        if(shouldRenderDynamic){
            List<Vec2> zappyPattern;

            if(patSets.speed == 0) {
                // re-use our static points if we're rendering a static pattern anyway
                zappyPattern = staticPoints.zappyPoints;
            } else {
                List<Vec2> lines1 = pattern.toLines(1, Vec2.ZERO);
                Set<Integer> dupIndices = RenderLib.findDupIndices(pattern.positions());
                zappyPattern = RenderLib.makeZappy(lines1, dupIndices,
                        patSets.hops, patSets.variance, patSets.speed, patSets.flowIrregular, patSets.readabilityOffset, patSets.lastSegmentLenProportion, seed);
            }

            List<Vec2> zappyRenderSpace = staticPoints.scaleVecs(zappyPattern);

            if(FastColor.ARGB32.alpha(patColors.outerEndColor) != 0 && FastColor.ARGB32.alpha(patColors.outerStartColor) != 0){
                RenderLib.drawLineSeq(ps.last().pose(), zappyRenderSpace, patSets.outerWidthProvider.apply((float)(staticPoints.finalScale)),
                        patColors.outerEndColor, patColors.outerStartColor, VCDrawHelper.getHelper(worldlyBits, ps,0.001f, TheCoolerRenderLib.WHITE));
            }
            if(FastColor.ARGB32.alpha(patColors.innerEndColor) != 0 && FastColor.ARGB32.alpha(patColors.innerStartColor) != 0) {
                RenderLib.drawLineSeq(ps.last().pose(), zappyRenderSpace, patSets.innerWidthProvider.apply((float) (staticPoints.finalScale)),
                        patColors.innerEndColor, patColors.innerStartColor, VCDrawHelper.getHelper(worldlyBits, ps,0.0005f, TheCoolerRenderLib.WHITE));
            }
        }

        // render dots and grid dynamically for now

//        if(provider == null) provider.getBuffer(RenderType.solid()); // just to try to refresh it

        float dotZ = 0.0004f;

        if(FastColor.ARGB32.alpha(patColors.startingDotColor) != 0) {
            RenderLib.drawSpot(ps.last().pose(), staticPoints.dotsScaled.get(0), patSets.startingDotRadiusProvider.apply((float) (staticPoints.finalScale)),
                    patColors.startingDotColor, VCDrawHelper.getHelper(worldlyBits, ps, dotZ, TheCoolerRenderLib.WHITE));
        }

        if(FastColor.ARGB32.alpha(patColors.gridDotsColor) != 0) {
            for(int i = 1; i < staticPoints.dotsScaled.size(); i++){
                Vec2 gridDot = staticPoints.dotsScaled.get(i);
                RenderLib.drawSpot(ps.last().pose(), gridDot, patSets.gridDotsRadiusProvider.apply((float) (staticPoints.finalScale)),
                    patColors.gridDotsColor, VCDrawHelper.getHelper(worldlyBits, ps, dotZ, TheCoolerRenderLib.WHITE));
            }
        }

        RenderSystem.setShader(() -> oldShader);
    }

    private static boolean renderPatternTexture(HexPattern pattern, PoseStack ps, @Nullable WorldlyBits worldlyBits, PatternRenderSettings patSets, PatternColors patColors, double seed, int resPerUnit){
        Optional<Map<String, ResourceLocation>> maybeTextures = PatternTextureManager.getTextures(pattern, patSets, seed, resPerUnit);
        if(maybeTextures.isEmpty()){
            return false;
        }

        Vec3 normalVec = worldlyBits.normal();
        int light = worldlyBits.light();
        MultiBufferSource provider = worldlyBits.provider();

        if(normalVec == null) normalVec = new Vec3(1f, 1f, 1f);

        ShaderInstance oldShader = RenderSystem.getShader();

        Map<String, ResourceLocation> textures = maybeTextures.get();

        HexPatternPoints staticPoints = HexPatternPoints.getStaticPoints(pattern, patSets, seed);

        VertexConsumer vc;

        float outerZ = 0.001f;
        float innerZ = 0.0005f;

        if(FastColor.ARGB32.alpha(patColors.outerStartColor) != 0) {
            VCDrawHelper vcHelper = VCDrawHelper.getHelper(worldlyBits, ps, outerZ, textures.get("outer"));
            vc = vcHelper.vcSetupAndSupply(VertexFormat.Mode.QUADS);

            int cl = patColors.outerStartColor;

            vcHelper.vertex(vc, cl, new Vec2(0, 0), new Vec2(0, 0), ps.last().pose());
            vcHelper.vertex(vc, cl, new Vec2(0, (float) staticPoints.fullHeight), new Vec2(0, 1), ps.last().pose());
            vcHelper.vertex(vc, cl, new Vec2((float) staticPoints.fullWidth, (float) staticPoints.fullHeight), new Vec2(1, 1), ps.last().pose());
            vcHelper.vertex(vc, cl, new Vec2((float) staticPoints.fullWidth, 0), new Vec2(1, 0), ps.last().pose());

            vcHelper.vcEndDrawer(vc);
        }

        if(FastColor.ARGB32.alpha(patColors.innerStartColor) != 0) {
            VCDrawHelper vcHelper = VCDrawHelper.getHelper(worldlyBits, ps, innerZ, textures.get("inner"));
            vc = vcHelper.vcSetupAndSupply(VertexFormat.Mode.QUADS);

            int cl = patColors.innerStartColor;

            vcHelper.vertex(vc, cl, new Vec2(0, 0), new Vec2(0, 0), ps.last().pose());
            vcHelper.vertex(vc, cl, new Vec2(0, (float) staticPoints.fullHeight), new Vec2(0, 1), ps.last().pose());
            vcHelper.vertex(vc, cl, new Vec2((float) staticPoints.fullWidth, (float) staticPoints.fullHeight), new Vec2(1, 1), ps.last().pose());
            vcHelper.vertex(vc, cl, new Vec2((float) staticPoints.fullWidth, 0), new Vec2(1, 0), ps.last().pose());

            vcHelper.vcEndDrawer(vc);
        }
        RenderSystem.setShader(() -> oldShader);

        return true;
    }

    public record WorldlyBits(@Nullable MultiBufferSource provider, Integer light, Vec3 normal){};
}
