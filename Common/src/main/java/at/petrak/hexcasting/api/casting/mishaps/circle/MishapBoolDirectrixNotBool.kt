package at.petrak.hexcasting.api.casting.mishaps.circle

import at.petrak.hexcasting.api.casting.eval.CastingEnvironment
import at.petrak.hexcasting.api.casting.iota.Iota
import at.petrak.hexcasting.api.casting.mishaps.Mishap
import at.petrak.hexcasting.api.pigment.FrozenPigment
import net.minecraft.ChatFormatting
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.world.item.DyeColor

// what a mouthful
class MishapBoolDirectrixNotBool(
    val perpetrator: Iota,
    val pos: BlockPos,
) : Mishap() {
    override fun accentColor(ctx: CastingEnvironment, errorCtx: Context): FrozenPigment =
        dyeColor(DyeColor.GRAY)

    override fun execute(env: CastingEnvironment, errorCtx: Context, stack: MutableList<Iota>) {
        env.world.destroyBlock(this.pos, true)
    }

    override fun errorMessage(ctx: CastingEnvironment, errorCtx: Context): Component =
        error("circle.invalid_value", "boolean", 0,
            Component.literal("(").append(pos.toShortString()).append(")").withStyle(ChatFormatting.RED),
            perpetrator.display())
}