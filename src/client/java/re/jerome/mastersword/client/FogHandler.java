package re.jerome.mastersword.client;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientBlockEntityEvents;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.core.BlockPos;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector4f;
import re.jerome.mastersword.block.PedestalBlockEntity;
import re.jerome.mastersword.config.MasterSwordConfig;
import re.jerome.mastersword.config.MasterSwordConfig.FogConfig;

/**
 * The mist that guards a sword still in its shrine (SPEC 5).
 *
 * Entirely client-side, and with no packet of its own: PedestalBlockEntity sends
 * its whole saved state to the client already, so the two flags the fog needs --
 * shrine and fog_consumed -- are in the block entity the client is holding. All
 * that is left is knowing which pedestals are loaded, and how thick to make the
 * fog.
 */
public final class FogHandler {
	private FogHandler() {
	}

	// Every loaded pedestal, shrine or not.
	//
	// Filtering on isShrine() here does NOT work, and the failure looks like the
	// feature simply never happening. Fabric fires BLOCK_ENTITY_LOAD from
	// LevelChunk.setBlockEntity, at the Map.put; for a pedestal arriving in a chunk
	// packet that is offset 5 of LevelChunk.lambda$replaceWithPacketData$0, while
	// loadWithComponents -- which is what reads shrine out of the NBT -- only runs
	// at offset 52. At event time the block entity is brand new and every flag
	// still reads false. The sorting therefore happens in guardsFog(), per frame,
	// long after the load.
	private static final Map<BlockPos, PedestalBlockEntity> PEDESTALS = new HashMap<>();

	// How fast the fog follows the distance. Same shape as the rain fog in
	// AtmosphericFogEnvironment, which eases at 0.2 per tick; 0.15 is about a
	// second of fade. It earns its keep three times over: the fog does not appear
	// in one frame when a shrine comes into view, it does not snap away when the
	// sword is drawn, and it does not flicker if a chunk reloads.
	private static final float EASING_PER_TICK = 0.15F;

	// Where the fog stops being worth any work. Below this the sky and cloud
	// clamps would be doing nothing visible.
	private static final float NEGLIGIBLE = 0.002F;

	// The near edge of the thickest fog. Not configurable: the far edge
	// (visibility) is what the eye reads, and a start closer than a block would
	// only fog up the player's own hands.
	private static final float THICKEST_START = 1.0F;

	private static float density;

	// Held weakly, and only ever compared: a strong reference would pin a whole
	// world in memory after leaving it.
	private static WeakReference<ClientLevel> lastLevel = new WeakReference<>(null);

	public static void register() {
		ClientBlockEntityEvents.BLOCK_ENTITY_LOAD.register((blockEntity, level) -> {
			if (blockEntity instanceof PedestalBlockEntity pedestal) {
				PEDESTALS.put(pedestal.getBlockPos().immutable(), pedestal);
			}
		});

		// remove(key, value) and not remove(key): LevelChunk.setBlockEntity puts the
		// new block entity in before calling setRemoved on the old one, so the two
		// events arrive in that order. Removing by key alone would let the unload of
		// the old pedestal delete the entry of the new one at the same position.
		ClientBlockEntityEvents.BLOCK_ENTITY_UNLOAD.register((blockEntity, level) -> {
			if (blockEntity instanceof PedestalBlockEntity) {
				PEDESTALS.remove(blockEntity.getBlockPos(), blockEntity);
			}
		});
	}

	/**
	 * Thickens the fog the game has already set up, in place.
	 *
	 * Called at the tail of FogRenderer.setupFog, so every FogEnvironment vanilla
	 * applies has run and what is in {@code fog} is the game's own answer.
	 */
	public static void apply(FogData fog, Camera camera, DeltaTracker deltaTracker,
			ClientLevel level, float darkenWorldAmount) {
		// Changing dimension, or leaving for the title screen and joining another
		// world, must not carry a fog over. Comparing the level rather than
		// listening for an event covers both, and covers a disconnection too --
		// AFTER_CLIENT_LEVEL_CHANGE is not fired when the new level is null.
		if (lastLevel.get() != level) {
			lastLevel = new WeakReference<>(level);
			density = 0.0F;
		}

		FogConfig config = MasterSwordConfig.get().fog();
		// Head underwater or in lava, the fluid's fog is the one that makes sense;
		// ours would be arguing with it about a view that is already blocked.
		boolean suspended = !config.enabled() || camera.getFluidInCamera() != FogType.NONE;
		float target = suspended ? 0.0F : targetDensity(config, camera.position(), level);

		// Eased on game time, not on frames: the fade then takes the same second
		// whether the game runs at 30 or 300 images per second. The factor is capped
		// at 1 so that one very long frame lands on the target instead of
		// overshooting past it.
		density += (target - density) * Math.min(1.0F, deltaTracker.getGameTimeDeltaTicks() * EASING_PER_TICK);
		if (density < NEGLIGIBLE) {
			return;
		}

		// Math.min throughout, never a plain assignment. Underwater, blinded or in
		// darkness another FogEnvironment has already set tighter values, and this
		// fog must only ever add to what the game decided -- never clear it.
		fog.environmentalEnd = Math.min(fog.environmentalEnd, closingDistance(fog.environmentalEnd, config.visibility(), density));
		// The end is computed first, and the start is then kept under it. The
		// shader reads `d <= fogStart` as no fog at all, so a start that overtakes
		// the end does not weaken this fog, it deletes it. Vanilla's own start is 0
		// in the overworld and nothing here can raise it, but the two bounds move on
		// different curves -- one straight, one geometric -- and only the clamp makes
		// that safe for a dimension or a mod that starts its fog further out.
		fog.environmentalStart = Math.min(
				Math.min(fog.environmentalStart, Mth.lerp(density, fog.environmentalStart, THICKEST_START)),
				fog.environmentalEnd);
		// Left alone, the sky and the clouds stay crisp behind an opaque fog.
		// Vanilla pulls them in the same way for the boss bar fog.
		fog.skyEnd = Math.min(fog.skyEnd, fog.environmentalEnd);
		fog.cloudEnd = Math.min(fog.cloudEnd, fog.environmentalEnd);

		if (mayRecolour(camera, darkenWorldAmount)) {
			int tint = config.color();
			fog.color.lerp(new Vector4f(ARGB.redFloat(tint), ARGB.greenFloat(tint), ARGB.blueFloat(tint), 1.0F), density);
		}
	}

	/**
	 * How far one can see, closing in **geometrically** from what the game was
	 * already showing down to {@code visibility}.
	 *
	 * Two things had to be measured rather than guessed here, and both were wrong
	 * on the first try.
	 *
	 * **Fog is read as a ratio of distances, not as a difference.** Interpolating
	 * straight towards `visibility` put the fog end at 91 blocks when the player
	 * stood 20 blocks from the shrine -- halfway down the curve, and invisible
	 * under a canopy where nothing is in sight past 30 blocks. Dividing the sight
	 * distance instead of subtracting from it spreads the effect over the whole
	 * approach.
	 *
	 * **And the value being divided is 1024, not the render distance.**
	 * `EnvironmentAttributes.FOG_END_DISTANCE` defaults to 1024 blocks and only
	 * two vanilla biomes override it (swamp and mangrove swamp); the render
	 * distance lives in the *other* pair of fog bounds, which the shader combines
	 * with a `max`. Dividing 1024 needs a faster ramp than dividing 192, hence the
	 * square root: without it the fog still ended at 36 blocks with the player 20
	 * blocks out, which reads as a light haze, not as a guarded place.
	 *
	 * The square root is the exact inverse of the quadratic this started as. The
	 * resulting sight distance, with the defaults: 111 blocks at 40, 44 at 30, 22
	 * at 20, 16 at 15, 12 at the shrine.
	 *
	 * Starting from vanilla's own value rather than from a constant is what keeps
	 * this continuous: at density 0 it returns exactly what it was handed, so
	 * nothing snaps when the shrine comes into range.
	 */
	private static float closingDistance(float current, float visibility, float density) {
		if (current <= visibility) {
			return current;
		}

		return current * (float) Math.pow(visibility / current, Math.sqrt(density));
	}

	/**
	 * The one place this fog could contradict vanilla rather than add to it.
	 *
	 * The distances above are clamped, so they can only ever darken. The colour is
	 * an interpolation, and washing a pale grey-green over a screen the game has
	 * deliberately blackened -- Blindness, Darkness, or the world darkening of a
	 * boss bar -- would hand back some of the sight those effects take away. In
	 * those three cases the fog still thickens, it simply keeps vanilla's colour.
	 *
	 * The void darkening near the bottom of the world is knowingly left out: a
	 * shrine is a surface structure, and the two cannot meet.
	 */
	private static boolean mayRecolour(Camera camera, float darkenWorldAmount) {
		if (darkenWorldAmount > 0.0F) {
			return false;
		}

		return !(camera.entity() instanceof LivingEntity living)
				|| (!living.hasEffect(MobEffects.BLINDNESS) && !living.hasEffect(MobEffects.DARKNESS));
	}

	/**
	 * How far along the approach the player is: zero beyond outer_radius, one at
	 * inner_radius and closer.
	 *
	 * Straight, and not the squared curve this once used. SPEC 5 asks for
	 * something more dramatic than linear, and it now comes from closingDistance,
	 * which is exponential in this value -- squaring on top of that pushed the
	 * whole effect into the last few blocks, which is exactly the fault the first
	 * test in game turned up.
	 */
	private static float targetDensity(FogConfig config, Vec3 eye, ClientLevel level) {
		float distance = nearestShrine(eye, level);
		if (distance >= config.outerRadius()) {
			return 0.0F;
		}

		return Mth.clamp((config.outerRadius() - distance) / config.fadeSpan(), 0.0F, 1.0F);
	}

	/** Distance to the nearest shrine still owed a fog, or +inf if there is none. */
	private static float nearestShrine(Vec3 eye, ClientLevel level) {
		double nearestSqr = Double.MAX_VALUE;

		var iterator = PEDESTALS.values().iterator();
		while (iterator.hasNext()) {
			PedestalBlockEntity pedestal = iterator.next();
			// The safety net: a pedestal that left without its unload event firing,
			// and one belonging to a world we are no longer in. Either would raise a
			// fog where there is nothing, or worse, at the same coordinates in
			// another dimension.
			if (pedestal.isRemoved() || pedestal.getLevel() != level) {
				iterator.remove();
				continue;
			}

			if (!pedestal.guardsFog()) {
				continue;
			}

			nearestSqr = Math.min(nearestSqr, eye.distanceToSqr(Vec3.atCenterOf(pedestal.getBlockPos())));
		}

		return nearestSqr == Double.MAX_VALUE ? Float.MAX_VALUE : (float) Math.sqrt(nearestSqr);
	}
}
