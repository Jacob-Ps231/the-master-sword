package re.jerome.mastersword.entity;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import re.jerome.mastersword.config.MasterSwordConfig;

// The beam a fully charged swing throws.
//
// Extends Projectile directly rather than ThrowableProjectile or
// AbstractHurtingProjectile: both keep applyInertia() private, so their drag and
// acceleration cannot be cancelled, and -- the deciding point -- their tick()
// snaps the entity onto the first thing it hits, which would glue a wave meant to
// pass through a line of mobs onto the first one.
public class LightWaveEntity extends Projectile {
	/** Entities already hurt, so a wave crossing one over several ticks hits it once. */
	private final IntSet alreadyHit = new IntOpenHashSet();

	private float travelled;
	private float damage;

	public LightWaveEntity(EntityType<? extends LightWaveEntity> type, Level level) {
		super(type, level);
	}

	public void setDamage(float damage) {
		this.damage = damage;
	}

	public float getDamage() {
		return this.damage;
	}

	@Override
	protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
		// Nothing to synchronise: the client only needs position and velocity, and
		// ClientboundAddEntityPacket already carries both.
	}

	@Override
	public void tick() {
		super.tick();

		MasterSwordConfig.LightWaveConfig cfg = MasterSwordConfig.get().lightWave();
		Vec3 from = this.position();
		Vec3 step = this.getDeltaMovement();
		Vec3 to = from.add(step);

		if (this.level() instanceof ServerLevel serverLevel) {
			// COLLIDER is what lets the wave pass through tall grass, flowers and
			// water -- their collision shape is empty -- while stone stops it.
			HitResult blockHit = ProjectileUtil.getHitResultOnMoveVector(
					this, entity -> false, ClipContext.Block.COLLIDER);
			if (blockHit.getType() == HitResult.Type.BLOCK) {
				to = blockHit.getLocation();
				this.onHitBlock((BlockHitResult) blockHit);
			}

			this.hurtEverythingBetween(serverLevel, from, to, cfg);
		}

		this.setPos(to);
		this.travelled += (float) step.length();

		if (this.level() instanceof ServerLevel serverLevel) {
			this.trail(serverLevel, from, to);
			// A hard tick cap on top of the range: the range alone would not end a
			// wave that never moves.
			if (this.travelled >= cfg.range() || this.tickCount > 200 || !this.isAlive()) {
				this.discard();
			}
		}
	}

	private void hurtEverythingBetween(ServerLevel level, Vec3 from, Vec3 to, MasterSwordConfig.LightWaveConfig cfg) {
		float half = cfg.width() / 2.0F;
		AABB sweep = new AABB(from, to).inflate(half);

		// The long overload, not the short one: the short one ignores the box
		// entirely for the actual hit test and falls back to computeMargin, which
		// starts at 0 and never exceeds 0.3 -- the wave would be a line and the
		// width setting would do nothing. Passing the margin explicitly is what
		// makes it a sweep, and the ClipContext argument adds a line-of-sight check
		// so nothing is hit through a wall.
		//
		// 26.3 added a tenth argument: it only chooses which point the hit result
		// carries, the clip on the entity's box or the block hit. We read nothing
		// but getEntity(), so either value gives the same wave.
		for (EntityHitResult hit : ProjectileUtil.getManyEntityHitResult(
				level, this, from, to, sweep, this::canHitEntity, half, ClipContext.Block.COLLIDER,
				false, false)) {
			Entity target = hit.getEntity();
			if (this.alreadyHit.add(target.getId())) {
				this.hurt(level, target);
			}
		}
	}

	private void hurt(ServerLevel level, Entity target) {
		Entity owner = this.getOwner();
		// indirectMagic rather than playerAttack: the player goes in as the causing
		// entity, which is what credits the kill, drops the loot and draws the
		// aggro, while the knockback still comes from the wave rather than from the
		// player standing metres away.
		target.hurtServer(level, this.damageSources().indirectMagic(this, owner), this.damage);
		if (owner instanceof net.minecraft.world.entity.LivingEntity livingOwner) {
			livingOwner.setLastHurtMob(target);
		}

		level.playSound(null, target.getX(), target.getY(), target.getZ(),
				SoundEvents.TRIDENT_HIT, SoundSource.PLAYERS, 0.7F, 1.3F);
	}

	private void trail(ServerLevel level, Vec3 from, Vec3 to) {
		Vec3 mid = from.add(to).scale(0.5);
		level.sendParticles(ParticleTypes.END_ROD, mid.x, mid.y, mid.z, 4, 0.15, 0.15, 0.15, 0.0);
		level.sendParticles(ParticleTypes.GLOW, mid.x, mid.y, mid.z, 1, 0.2, 0.2, 0.2, 0.0);
	}

	@Override
	protected void onHitBlock(BlockHitResult hit) {
		super.onHitBlock(hit);

		// The crystalline register of the shot, shattering: the wave breaks apart
		// rather than thudding into the wall.
		if (this.level() instanceof ServerLevel level) {
			Vec3 at = hit.getLocation();
			level.playSound(null, at.x, at.y, at.z,
					SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.PLAYERS, 0.5F, 1.5F);
			level.sendParticles(ParticleTypes.END_ROD, at.x, at.y, at.z, 12, 0.1, 0.1, 0.1, 0.08);
		}

		this.discard();
	}

	@Override
	protected boolean canHitEntity(Entity entity) {
		// Projectile.canHitEntity stops protecting the owner once leftOwner flips,
		// a few ticks in. The wave has long gone by then, but stating it outright
		// costs a line and closes the question.
		return entity != this.getOwner() && !entity.isSpectator() && super.canHitEntity(entity);
	}

	// Short-lived by nature: a wave that somehow never travels -- zero velocity --
	// would otherwise sit there for good.
	@Override
	public boolean shouldBeSaved() {
		return false;
	}

	// The wave is an effect, not a target: arrows and other waves pass through it.
	@Override
	public boolean hurtServer(ServerLevel level, net.minecraft.world.damagesource.DamageSource source, float amount) {
		return false;
	}

	@Override
	public boolean isPickable() {
		return false;
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putFloat("damage", this.damage);
		output.putFloat("travelled", this.travelled);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		this.damage = input.getFloatOr("damage", 0.0F);
		this.travelled = input.getFloatOr("travelled", 0.0F);
	}
}
