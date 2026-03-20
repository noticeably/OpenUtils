package org.afterlike.openutils.module.impl.world;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.scoreboard.Score;
import net.minecraft.scoreboard.ScoreObjective;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.util.BlockPos;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import org.afterlike.openutils.event.api.EventPhase;
import org.afterlike.openutils.event.handler.EventHandler;
import org.afterlike.openutils.event.impl.GameTickEvent;
import org.afterlike.openutils.event.impl.WorldLoadEvent;
import org.afterlike.openutils.module.api.Module;
import org.afterlike.openutils.module.api.ModuleCategory;
import org.afterlike.openutils.util.client.ClientUtil;
import org.afterlike.openutils.util.client.TextUtil;

public class AnticheatModule extends Module {
	private Map<Entity, Map<String, Object>> anticheatPlayers = new HashMap<>();
	private Map<String, Boolean> reports = new HashMap<>();
	private HashSet<String> history = new HashSet<>();
	private Map<String, Object> debugData = null;
	private String team = "";
	private String[] checks = {"NoSlowA", "AutoBlockA", "SprintA", "RotationA", "ScaffoldA",
			"ScaffoldC"};
	private long lastReport;
	private JsonObject anticheatConfig, anticheatDataArr;
	public AnticheatModule() {
		super("AntiCheat", ModuleCategory.WORLD);
		this.anticheatConfig = new JsonObject();
		this.anticheatDataArr = new JsonObject();
		this.anticheatConfig.add("anticheat_data", anticheatDataArr);
	}

	@EventHandler
	private void onWorldLoad(final WorldLoadEvent event) {
		ClientUtil.sendDebugMessage("Clearing anticheat data on world load");
		this.anticheatPlayers.clear();
		this.reports.clear();
		this.history.clear();
		this.debugData = null;
	}

	@SuppressWarnings("unchecked")
	private void updatePlayerData(EntityPlayer p, Map<String, Object> playerData) {
		ClientUtil.sendDebugMessage("Updating data for player: " + p.getName());
		int currentTick = p.ticksExisted;
		boolean lastCrouching = (Boolean) playerData.getOrDefault("crouching", false);
		boolean lastSprinting = (Boolean) playerData.getOrDefault("sprinting", false);
		boolean lastUsing = (Boolean) playerData.getOrDefault("using", false);
		boolean lastOnEdge = (Boolean) playerData.getOrDefault("onEdge", false);
		float lastSwing = (Float) playerData.getOrDefault("swingProgress", 0f);
		boolean lastOnGround = (Boolean) playerData.getOrDefault("onGround", false);
		ItemStack lastHeldItem = (ItemStack) playerData.getOrDefault("heldItem", null);
		double lastDeltaY = (Double) playerData.getOrDefault("lastDeltaY", 0d);
		boolean isCrouching = p.isSneaking();
		boolean isSprinting = p.isSprinting();
		boolean isUsing = p.isUsingItem();
		float swingProgress = p.swingProgress;
		boolean isCollided = p.isCollidedHorizontally || p.isCollidedVertically;
		boolean isDead = p.isDead;
		boolean isBurning = p.isBurning();
		boolean onEdge = false;
		double x = p.posX;
		double y = p.posY;
		double z = p.posZ;
		BlockPos posBelow = new BlockPos(Math.floor(x), Math.floor(y - 0.1), Math.floor(z));
		IBlockState stateBelow = Minecraft.getMinecraft().theWorld.getBlockState(posBelow);
		if (stateBelow.getBlock().getMaterial().isSolid()) {
			double offsetX = x - Math.floor(x) - 0.5;
			double offsetZ = z - Math.floor(z) - 0.5;
			double threshold = 0.3;
			if (Math.abs(offsetX) > threshold || Math.abs(offsetZ) > threshold) {
				BlockPos edgeCheck = new BlockPos(Math.floor(x + Math.signum(offsetX) * 0.5),
						Math.floor(y - 1), Math.floor(z + Math.signum(offsetZ) * 0.5));
				IBlockState blockBelowEdge = Minecraft.getMinecraft().theWorld
						.getBlockState(edgeCheck);
				if (blockBelowEdge.getBlock().getMaterial() == Material.air) {
					onEdge = true;
				}
			}
		}
		Vec3 currentPosition = new Vec3(p.posX, p.posY, p.posZ);
		Vec3 lastPosition = new Vec3(p.prevPosX, p.prevPosY, p.prevPosZ);
		double deltaX = currentPosition.xCoord - lastPosition.xCoord;
		double deltaY = currentPosition.yCoord - lastPosition.yCoord;
		double deltaZ = currentPosition.zCoord - lastPosition.zCoord;
		float rotationYaw = p.rotationYaw;
		float moveYaw = getMoveYaw(deltaX, deltaZ, rotationYaw);
		float pitch = p.rotationPitch;
		float prevYaw = p.prevRotationYaw;
		float prevPitch = p.prevRotationPitch;
		boolean onGround = p.onGround;
		String name = p.getName();
		String uuid = p.getUniqueID().toString();
		String displayName = p.getDisplayName().getFormattedText();
		int hurtTime = p.hurtTime;
		int maxHurtTime = p.maxHurtTime;
		Entity riding = p.ridingEntity;
		ItemStack heldItem = p.getHeldItem();
		BlockPos pos = new BlockPos(currentPosition.xCoord, currentPosition.yCoord,
				currentPosition.zCoord);
		IBlockState state = Minecraft.getMinecraft().theWorld.getBlockState(pos);
		String blockOn = state.getBlock().getUnlocalizedName();
		playerData.put("lastCrouching", lastCrouching);
		playerData.put("lastSprinting", lastSprinting);
		playerData.put("lastUsing", lastUsing);
		playerData.put("lastOnEdge", lastOnEdge);
		playerData.put("lastSwinging", lastSwing);
		playerData.put("lastBlockOn", playerData.getOrDefault("blockOn", "tile.air"));
		playerData.put("lastOnGround", lastOnGround);
		playerData.put("lastHeldItem", lastHeldItem);
		playerData.put("lastDeltaY", deltaY);
		playerData.put("uuid", uuid);
		playerData.put("name", name);
		playerData.put("displayName", displayName);
		playerData.put("hurtTime", hurtTime);
		playerData.put("maxHurtTime", maxHurtTime);
		playerData.put("ticksExisted", currentTick);
		playerData.put("position", currentPosition);
		playerData.put("lastPosition", lastPosition);
		playerData.put("yaw", rotationYaw);
		playerData.put("pitch", pitch);
		playerData.put("previousYaw", prevYaw);
		playerData.put("previousPitch", prevPitch);
		playerData.put("swingProgress", swingProgress);
		playerData.put("riding", riding);
		playerData.put("dead", isDead);
		playerData.put("onGround", onGround);
		playerData.put("heldItem", heldItem);
		playerData.put("collided", isCollided);
		playerData.put("burning", isBurning);
		playerData.put("moveYaw", moveYaw);
		playerData.put("onEdge", onEdge);
		playerData.put("blockOn", blockOn);
		playerData.put("crouching", isCrouching);
		playerData.put("sprinting", isSprinting);
		playerData.put("using", isUsing);
		List<Vec3> previousPositions = (List<Vec3>) playerData.get("previousPositions");
		if (previousPositions == null) {
			previousPositions = new ArrayList<>(20);
			playerData.put("previousPositions", previousPositions);
		} else if (previousPositions.size() >= 20) {
			previousPositions.remove(previousPositions.size() - 1);
		}
		previousPositions.add(0, currentPosition);
		if (isCrouching && !lastCrouching)
			playerData.put("lastCrouchedTick", currentTick);
		if (!isCrouching && lastCrouching)
			playerData.put("lastStopCrouchingTick", currentTick);
		if (isSprinting && !lastSprinting)
			playerData.put("lastSprintingTick", currentTick);
		if (isUsing && !lastUsing)
			playerData.put("lastUsingTick", currentTick);
		if (!isUsing && lastUsing) {
			playerData.put("lastStopUsingTick", currentTick);
			playerData.put("lastStopUsingItem", lastHeldItem);
		}
		if (lastSwing < 0.1f && swingProgress > 0.9f) {
			playerData.put("lastSwingTick", currentTick);
			playerData.put("lastSwingItem", heldItem);
		}
		if (!onEdge && lastOnEdge)
			playerData.put("lastEdgeTick", currentTick);
		if ((heldItem == null && lastHeldItem != null) || (heldItem != null && lastHeldItem == null)
				|| (heldItem != null && lastHeldItem != null
						&& (heldItem.getMetadata() != lastHeldItem.getMetadata() || !heldItem
								.getDisplayName().equals(lastHeldItem.getDisplayName())))) {
			playerData.put("lastItemChangeTick", currentTick);
		}
		if (blockOn.equals("tile.air"))
			playerData.put("lastOnAir", currentTick);
		if (deltaY < -0.1 && lastDeltaY >= -0.1) {
			playerData.put("lastStartFallingTick", currentTick);
			playerData.put("lastStartFallingPosition", currentPosition);
		}
		if (onGround && !lastOnGround) {
			playerData.put("lastStopFallingTick", currentTick);
			playerData.put("lastStopFallingPosition", currentPosition);
		}
		if (!onGround && lastOnGround)
			playerData.put("lastOnGroundTick", currentTick);
	}

	public List<String> getScoreboardLines() {
		List<String> lines = new ArrayList<>();
		Minecraft mc = Minecraft.getMinecraft();
		if (mc.theWorld == null)
			return lines;
		Scoreboard scoreboard = mc.theWorld.getScoreboard();
		ScoreObjective sidebarObjective = scoreboard.getObjectiveInDisplaySlot(1); // 1 is sidebar
		if (sidebarObjective != null) {
			// Add Title
			lines.add(sidebarObjective.getDisplayName());
			// Get Scores
			Collection<Score> scores = scoreboard.getSortedScores(sidebarObjective);
			for (Score score : scores) {
				ScorePlayerTeam team = scoreboard.getPlayersTeam(score.getPlayerName());
				// Format line with team prefix/suffix (colors)
				String line = ScorePlayerTeam.formatPlayerName(team, score.getPlayerName());
				lines.add(line);
			}
		}
		return lines;
	}

	@EventHandler
	private void onTick(GameTickEvent event) {
		if (event.getPhase() != EventPhase.POST)
			return;
		for (EntityPlayer player : Minecraft.getMinecraft().theWorld.playerEntities) {
			if (player == Minecraft.getMinecraft().thePlayer || player.isDead)
				continue;
			Map<String, Object> playerData = this.anticheatPlayers.computeIfAbsent(player,
					k -> new HashMap<>());
			updatePlayerData(player, playerData);
			String uuid = (String) playerData.get("uuid");
			if (!this.history.contains(uuid) && (int) playerData.get("ticksExisted") > 20) {
				this.history.add(uuid);
				if (uuid != null && uuid.length() > 14 && uuid.charAt(14) == '4') {
					JsonElement storedElement = this.anticheatDataArr.get(uuid);
					if (storedElement != null && storedElement.isJsonObject()) {
						printPreviousFlags(playerData, storedElement.getAsJsonObject());
					}
				}
			}
			for (String check : this.checks) {
				ClientUtil.sendDebugMessage(
						"Running check: " + check + " for player: " + player.getName());
				int cooldown = 1000;
				int vlThreshold = 3;
				switch (check) {
					case "AutoBlockA" :
						AutoBlockA(playerData, cooldown, vlThreshold, true);
						break;
					case "NoSlowA" :
						NoSlowA(playerData, cooldown, vlThreshold, true);
						break;
					case "SprintA" :
						SprintA(playerData, cooldown, vlThreshold, true);
						break;
					case "VelocityA" :
						VelocityA(playerData, cooldown, vlThreshold, true);
						break;
					case "RotationA" :
						RotationA(playerData, cooldown, vlThreshold, true);
						break;
					case "ScaffoldA" :
						ScaffoldA(playerData, cooldown, vlThreshold, true);
						break;
					case "ScaffoldB" :
						ScaffoldB(playerData, cooldown, vlThreshold, true);
						break;
					case "ScaffoldC" :
						ScaffoldC(playerData, cooldown, vlThreshold, true);
						break;
				}
			}
		}
	}

	private void printFlag(Map<String, Object> anticheatPlayer, String flag, int vl) {
		String displayName = (String) anticheatPlayer.getOrDefault("displayName", "&7Unknown");
		String playerName = TextUtil
				.stripColorCodes((String) anticheatPlayer.getOrDefault("name", "Unknown"));
		String msg = "§c" + playerName + " §7flags §c" + flag + "§7. §8(§cVL: "
				+ vl + "§8)";
		ClientUtil.sendMessage(msg);
		String playerUuid = (String) anticheatPlayer.get("uuid");
		if (playerUuid == null)
			return;
		JsonObject playerNode;
		JsonElement existing = this.anticheatDataArr.get(playerUuid);
		if (existing != null && existing.isJsonObject()) {
			playerNode = existing.getAsJsonObject();
		} else {
			playerNode = new JsonObject();
			this.anticheatDataArr.add(playerUuid, playerNode);
		}
		JsonArray eventsArray;
		JsonElement flagElement = playerNode.get(flag);
		if (flagElement != null && flagElement.isJsonArray()) {
			eventsArray = flagElement.getAsJsonArray();
		} else {
			eventsArray = new JsonArray();
			playerNode.add(flag, eventsArray);
		}
		JsonObject eventObj = new JsonObject();
		eventObj.addProperty("timestamp", System.currentTimeMillis());
		eventObj.addProperty("vl", vl);
		eventsArray.add(eventObj);
	}

	private void printPreviousFlags(Map<String, Object> playerData, JsonObject stored) {
		Map<String, long[]> data = new HashMap<>();
		for (Map.Entry<String, JsonElement> entry : stored.entrySet()) {
			String flag = entry.getKey();
			JsonElement arrElement = entry.getValue();
			if (!arrElement.isJsonArray())
				continue;
			int threshold = 10;
			int bestVl = 0;
			long bestTs = 0;
			JsonArray events = arrElement.getAsJsonArray();
			for (JsonElement element : events) {
				if (!element.isJsonObject())
					continue;
				JsonObject ev = element.getAsJsonObject();
				int vl = ev.get("vl").getAsInt();
				if (vl >= threshold && vl > bestVl) {
					bestVl = vl;
					bestTs = ev.get("timestamp").getAsLong();
				}
			}
			if (bestVl >= threshold) {
				data.put(flag, new long[]{bestVl, bestTs});
			}
		}
		if (data.isEmpty())
			return;
		List<String> flags = new ArrayList<>(data.keySet());
		flags.sort(String.CASE_INSENSITIVE_ORDER);
		String displayName = (String) playerData.getOrDefault("displayName", "&7Unknown");
		String playerName = TextUtil
				.stripColorCodes((String) playerData.getOrDefault("name", "Unknown"));
		String prev = "§c" + playerName + " §7previously flagged: ";
		for (String flag : flags) {
			prev += "§c " + flag + "§7";
		}
		ClientUtil.sendMessage(prev);
	}

	private float getMoveYaw(double deltaX, double deltaZ, float playerYaw) {
		if (Math.abs(deltaX) < 1e-8 && Math.abs(deltaZ) < 1e-8)
			return 0f;
		float moveAngle = (float) Math.toDegrees(Math.atan2(deltaZ, deltaX)) - 90;
		float relativeAngle = (moveAngle - playerYaw) % 360;
		if (relativeAngle > 180)
			relativeAngle -= 360;
		if (relativeAngle < -180)
			relativeAngle += 360;
		return relativeAngle;
	}

	private boolean checkSurroundingBlocks(Vec3 position) {
		double[][] offsets = {{0.5, 0}, {-0.5, 0}, {0, 0.5}, {0, -0.5}};
		int bottom = (int) Math.floor(position.yCoord);
		int middle = bottom + 1;
		World world = Minecraft.getMinecraft().theWorld;
		for (double[] offset : offsets) {
			int checkX = (int) Math.floor(position.xCoord + offset[0]);
			int checkZ = (int) Math.floor(position.zCoord + offset[1]);
			BlockPos posLeg = new BlockPos(checkX, bottom, checkZ);
			BlockPos posTorso = new BlockPos(checkX, middle, checkZ);
			String blockLeg = world.getBlockState(posLeg).getBlock().getUnlocalizedName();
			String blockTorso = world.getBlockState(posTorso).getBlock().getUnlocalizedName();
			if (!blockLeg.equals("tile.air") || !blockTorso.equals("tile.air"))
				return true;
		}
		return false;
	}

	private String getBlockAtPos(World world, double x, double y, double z) {
		BlockPos pos = new BlockPos(x, y, z);
		Block blocka = world.getBlockState(pos).getBlock();
		// hacky AF
		if (blocka == net.minecraft.init.Blocks.air) {
			return "air";
		}
		return blocka.getLocalizedName().toLowerCase();
	}

	void NoSlowA(Map<String, Object> anticheatPlayer, int cooldown, int vlThreshold,
			boolean alerts) {
		int vl = (int) anticheatPlayer.getOrDefault("NoSlowA_VL", 0);
		long lastAlert = (long) anticheatPlayer.getOrDefault("NoSlowA_LastAlert", 0L);
		boolean isSprinting = Boolean.TRUE.equals(anticheatPlayer.get("sprinting"));
		boolean isUsingItem = Boolean.TRUE.equals(anticheatPlayer.get("using"));
		boolean isRiding = anticheatPlayer.get("riding") != null;
		int ticks = (int) anticheatPlayer.get("ticksExisted");
		int lastStartUsing = (int) anticheatPlayer.getOrDefault("lastUsingTick", 0);
		int lastItemSwap = (int) anticheatPlayer.getOrDefault("lastItemChangeTick", 0);
		if (isUsingItem && isSprinting && !isRiding && lastStartUsing - lastItemSwap > 1) {
			boolean isSameItem = true;
			int ticksNotUsing = (int) anticheatPlayer.getOrDefault("lastUsingTick", 0)
					- (int) anticheatPlayer.getOrDefault("lastStopUsingTick", 0);
			if (ticksNotUsing <= 7) {
				ItemStack heldItem = (ItemStack) anticheatPlayer.get("heldItem");
				ItemStack lastStopUsingItem = (ItemStack) anticheatPlayer.get("lastStopUsingItem");
				String heldItemKey = heldItem != null
						? heldItem.getUnlocalizedName() + ":" + heldItem.getMetadata()
						: null;
				String lastStopUsingItemKey = lastStopUsingItem != null
						? lastStopUsingItem.getUnlocalizedName() + ":"
								+ lastStopUsingItem.getMetadata()
						: null;
				isSameItem = heldItemKey != null && heldItemKey.equals(lastStopUsingItemKey);
			}
			if (ticksNotUsing > 7 || !isSameItem) {
				vl++;
				if (vl >= vlThreshold && System.currentTimeMillis() - lastAlert > cooldown) {
					anticheatPlayer.put("NoSlowA_LastAlert", System.currentTimeMillis());
					printFlag(anticheatPlayer, "NoSlowA", vl);
				}
			}
		} else {
			vl = Math.max(vl - 1, 0);
		}
		anticheatPlayer.put("NoSlowA_VL", vl);
	}

	void AutoBlockA(Map<String, Object> anticheatPlayer, int cooldown, int vlThreshold,
			boolean alerts) {
		int vl = (int) anticheatPlayer.getOrDefault("AutoBlockA_VL", 0);
		long lastAlert = (long) anticheatPlayer.getOrDefault("AutoBlockA_LastAlert", 0L);
		int swingProgress = (int) anticheatPlayer.get("swingProgress");
		boolean isUsingItem = Boolean.TRUE.equals(anticheatPlayer.get("using"));
		int ticksUsing = (int) anticheatPlayer.get("ticksExisted")
				- (int) anticheatPlayer.getOrDefault("lastUsingTick", 0);
		ItemStack heldItem = (ItemStack) anticheatPlayer.get("heldItem");
		if (isUsingItem && heldItem != null
				&& heldItem.getItem() instanceof net.minecraft.item.ItemSword) {
			if (swingProgress != 0) {
				vl++;
				if (vl >= vlThreshold && System.currentTimeMillis() - lastAlert > cooldown) {
					anticheatPlayer.put("AutoBlockA_LastAlert", System.currentTimeMillis());
					printFlag(anticheatPlayer, "AutoBlockA", vl);
				}
			}
		} else {
			vl = Math.max(vl - 5, 0);
		}
		anticheatPlayer.put("AutoBlockA_VL", vl);
	}

	void SprintA(Map<String, Object> anticheatPlayer, int cooldown, int vlThreshold,
			boolean alerts) {
		int vl = (int) anticheatPlayer.getOrDefault("SprintA_VL", 0);
		long lastAlert = (long) anticheatPlayer.getOrDefault("SprintA_LastAlert", 0L);
		boolean isSprinting = Boolean.TRUE.equals(anticheatPlayer.get("sprinting"));
		boolean isGroundCollision = Boolean.TRUE.equals(anticheatPlayer.get("onGround"));
		boolean isRiding = anticheatPlayer.get("riding") != null;
		float moveYaw = (float) anticheatPlayer.get("moveYaw");
		float rotationYaw = (float) anticheatPlayer.get("yaw");
		Vec3 current = (Vec3) anticheatPlayer.get("position");
		Vec3 previous = (Vec3) anticheatPlayer.get("lastPosition");
		double speed = Math.max(Math.abs(current.xCoord - previous.xCoord),
				Math.abs(current.zCoord - previous.zCoord));
		if (!isRiding && isSprinting && isGroundCollision && Math.abs(moveYaw) > 90
				&& speed >= 0.2) {
			vl++;
			if (vl >= vlThreshold && System.currentTimeMillis() - lastAlert > cooldown) {
				anticheatPlayer.put("SprintA_LastAlert", System.currentTimeMillis());
				printFlag(anticheatPlayer, "SprintA", vl);
			}
		} else {
			vl = Math.max(vl - 1, 0);
		}
		anticheatPlayer.put("SprintA_VL", vl);
	}

	void VelocityA(Map<String, Object> anticheatPlayer, int cooldown, int vlThreshold,
			boolean alerts) {
		int vl = (int) anticheatPlayer.getOrDefault("VelocityA_VL", 0);
		long lastAlert = (long) anticheatPlayer.getOrDefault("VelocityA_LastAlert", 0L);
		int hurtTime = (int) anticheatPlayer.get("hurtTime");
		int maxHurtTime = (int) anticheatPlayer.get("maxHurtTime");
		int ticksExisted = (int) anticheatPlayer.get("ticksExisted");
		int startFall = (int) anticheatPlayer.getOrDefault("lastStartFallingTick", 0);
		int stopFall = (int) anticheatPlayer.getOrDefault("lastStopFallingTick", 0);
		int ticksSinceFall = ticksExisted - stopFall;
		int fallTicks = Math.max(0, stopFall - startFall);
		boolean burning = Boolean.TRUE.equals(anticheatPlayer.get("burning"));
		Vec3 position = (Vec3) anticheatPlayer.get("position");
		Vec3 lastPosition = (Vec3) anticheatPlayer.get("lastPosition");
		boolean recentFall = fallTicks >= 6 && ticksSinceFall <= 6;
		double deltaXZ = Math.sqrt(Math.pow(position.zCoord - lastPosition.zCoord, 2)
				+ Math.pow(position.zCoord - lastPosition.zCoord, 2));
		if (!burning && hurtTime > 0 && hurtTime < maxHurtTime && !recentFall
				&& Math.abs(deltaXZ) < 1e-8) {
			boolean isCollided = checkSurroundingBlocks(position);
			if (!isCollided) {
				vl++;
				if (vl >= vlThreshold && System.currentTimeMillis() - lastAlert > cooldown) {
					anticheatPlayer.put("VelocityA_LastAlert", System.currentTimeMillis());
					printFlag(anticheatPlayer, "VelocityA", vl);
				}
			}
		} else {
			vl = Math.max(vl - 1, 0);
		}
		anticheatPlayer.put("VelocityA_VL", vl);
	}

	void RotationA(Map<String, Object> anticheatPlayer, int cooldown, int vlThreshold,
			boolean alerts) {
		World world = Minecraft.getMinecraft().theWorld;
		int vl = (int) anticheatPlayer.getOrDefault("RotationA_VL", 0);
		long lastAlert = (long) anticheatPlayer.getOrDefault("RotationA_LastAlert", 0L);
		float pitch = (float) anticheatPlayer.get("pitch");
		if (Math.abs(pitch) > 90) {
			List<String> scoreboard = getScoreboardLines();
			if (scoreboard != null) {
				String header = TextUtil.stripColorCodes(scoreboard.get(0));
				if ("ATLAS".equals(header) || "REPLAY".equals(header))
					return;
			}
			vl++;
			if (vl >= vlThreshold && System.currentTimeMillis() - lastAlert > cooldown) {
				anticheatPlayer.put("RotationA_LastAlert", System.currentTimeMillis());
				printFlag(anticheatPlayer, "RotationA", vl);
			}
		} else {
			vl = Math.max(vl - 1, 0);
		}
		anticheatPlayer.put("RotationA_VL", vl);
	}

	void ScaffoldA(Map<String, Object> anticheatPlayer, int cooldown, int vlThreshold,
			boolean alerts) {
		int vl = (int) anticheatPlayer.getOrDefault("ScaffoldA_VL", 0);
		long lastAlert = (long) anticheatPlayer.getOrDefault("ScaffoldA_LastAlert", 0L);
		World world = Minecraft.getMinecraft().theWorld;
		int lastStopCrouch = (int) anticheatPlayer.getOrDefault("lastStopCrouchingTick", 0);
		int ticksExisted = (int) anticheatPlayer.get("ticksExisted");
		int lastSwing = (int) anticheatPlayer.getOrDefault("lastSwingTick", 0);
		int lastStartCrouch = (int) anticheatPlayer.getOrDefault("lastCrouchedTick", 0);
		Vec3 current = (Vec3) anticheatPlayer.get("position");
		boolean onGround = Boolean.TRUE.equals(anticheatPlayer.get("onGround")); // Does not flag in
																					// replays due
																					// to ground
																					// always being
																					// false for
																					// entities!
		float pitch = (float) anticheatPlayer.get("pitch");
		ItemStack lastSwingItem = (ItemStack) anticheatPlayer.get("lastSwingItem");
		// boolean holdingBlocks = lastSwingItem == null ? false :
		// lastSwingItem.type.startsWith("Block");
		boolean holdingBlocks = false;
		if (lastSwingItem != null) {
			Block block = Block.getBlockFromItem(lastSwingItem.getItem());
			if (block != Blocks.air) {
				holdingBlocks = true;
			}
		}
		boolean lookingDown = pitch >= 70f;
		if (lookingDown && holdingBlocks && lastSwing == ticksExisted
				&& lastStopCrouch >= ticksExisted - 1 && lastStopCrouch - lastStartCrouch <= 2) {
			boolean grounded = onGround;
			List<String> scoreboard = getScoreboardLines();
			if (!grounded && scoreboard != null && !scoreboard.isEmpty()) { // Fixes not flagging
																			// players in replays
				String header = TextUtil.stripColorCodes(scoreboard.get(0));
				if ("ATLAS".equals(header) || "REPLAY".equals(header)) {
					@SuppressWarnings("unchecked")
					List<Vec3> ps = (List<Vec3>) anticheatPlayer.get("previousPositions");
					if (ps != null && ps.size() > 1) {
						int sz = ps.size(), n = Math.min(sz - 1, 10);
						double sum = 0;
						for (int i = sz - n; i < sz; i++)
							sum += Math.abs(ps.get(i).yCoord - ps.get(i - 1).yCoord);
						grounded = (sum / n) <= 0.2;
					}
				}
			}
			if (grounded) {
				vl++;
				if (vl >= vlThreshold && System.currentTimeMillis() - lastAlert > cooldown) {
					anticheatPlayer.put("ScaffoldA_LastAlert", System.currentTimeMillis());
					printFlag(anticheatPlayer, "ScaffoldA", vl);
				}
			}
		} else if (!lookingDown || !holdingBlocks || ticksExisted - lastStopCrouch > 20
				|| ticksExisted - lastSwing > 20
				|| (onGround && lastSwing == ticksExisted && lastStopCrouch < ticksExisted - 1)) {
			vl = Math.max(vl - 1, 0);
		}
		anticheatPlayer.put("ScaffoldA_VL", vl);
	}

	// Thought this would work well, but it does not. If anybody ends up improving,
	// please lmk I cba
	void ScaffoldB(Map<String, Object> anticheatPlayer, int cooldown, int vlThreshold,
			boolean alerts) {
		int vl = (int) anticheatPlayer.getOrDefault("ScaffoldB_VL", 0);
		long lastAlert = (long) anticheatPlayer.getOrDefault("ScaffoldB_LastAlert", 0L);
		int lastStopCrouch = (int) anticheatPlayer.getOrDefault("lastStopCrouchingTick", 0);
		int ticksExisted = (int) anticheatPlayer.get("ticksExisted");
		int lastSwing = (int) anticheatPlayer.getOrDefault("lastSwingTick", 0);
		int lastOnGround = (int) anticheatPlayer.getOrDefault("lastOnGroundTick", 0);
		float pitch = (float) anticheatPlayer.get("pitch");
		ItemStack lastSwingItem = (ItemStack) anticheatPlayer.get("lastSwingItem");
		// boolean holdingBlocks = lastSwingItem != null &&
		// lastSwingItem.type.startsWith("Block");
		boolean holdingBlocks = false;
		if (lastSwingItem != null) {
			Block block = Block.getBlockFromItem(lastSwingItem.getItem());
			if (block != Blocks.air) {
				holdingBlocks = true;
			}
		}
		boolean lookingDown = pitch >= 70;
		if (lookingDown && holdingBlocks && ticksExisted - lastSwing <= 10
				&& lastStopCrouch == ticksExisted && ticksExisted == lastOnGround) {
			vl++;
			if (vl >= vlThreshold && System.currentTimeMillis() - lastAlert > cooldown) {
				anticheatPlayer.put("ScaffoldB_LastAlert", System.currentTimeMillis());
				printFlag(anticheatPlayer, "ScaffoldB", vl);
			}
		} else if (!lookingDown || !holdingBlocks || ticksExisted - lastStopCrouch > 50
				|| ticksExisted - lastSwing > 50) {
			vl = Math.max(vl - 1, 0);
		}
		anticheatPlayer.put("ScaffoldB_VL", vl);
	}

	@SuppressWarnings("unchecked")
	void ScaffoldC(Map<String, Object> anticheatPlayer, int cooldown, int vlThreshold,
			boolean alerts) {
		int vl = (int) anticheatPlayer.getOrDefault("ScaffoldC_VL", 0);
		long lastAlert = (long) anticheatPlayer.getOrDefault("ScaffoldC_LastAlert", 0L);
		World world = Minecraft.getMinecraft().theWorld;
		int lastStartCrouch = (int) anticheatPlayer.getOrDefault("lastCrouchedTick", 0);
		int lastStopCrouch = (int) anticheatPlayer.getOrDefault("lastStopCrouchingTick", 0);
		int ticksExisted = (int) anticheatPlayer.get("ticksExisted");
		int lastSwing = (int) anticheatPlayer.getOrDefault("lastSwingTick", 0);
		float moveYaw = (float) anticheatPlayer.get("moveYaw");
		float pitch = (float) anticheatPlayer.get("pitch");
		ItemStack lastSwingItem = (ItemStack) anticheatPlayer.get("lastSwingItem");
		List<Vec3> previousPositions = (List<Vec3>) anticheatPlayer.get("previousPositions");
		// boolean holdingBlocks = lastSwingItem != null &&
		// lastSwingItem.type.startsWith("Block");
		boolean holdingBlocks = false;
		if (lastSwingItem != null) {
			Block block = Block.getBlockFromItem(lastSwingItem.getItem());
			if (block != Blocks.air) {
				holdingBlocks = true;
			}
		}
		boolean lookingDown = pitch >= 70; // Sometimes does not flag in replays due to inaccurate
											// pitch!
		if (lookingDown && holdingBlocks && ticksExisted - lastSwing <= 10
				&& lastStopCrouch >= lastStartCrouch && ticksExisted - lastStopCrouch > 30
				&& Math.abs(moveYaw) >= 90 && previousPositions.size() >= 20) {
			double speed = 0;
			for (int i = 0; i < previousPositions.size() - 1; i++) {
				Vec3 current = previousPositions.get(i);
				Vec3 previous = previousPositions.get(i + 1);
				speed += Math.max(Math.abs(current.xCoord - previous.xCoord),
						Math.abs(current.zCoord - previous.zCoord));
			}
			double avgSpeed = speed / (previousPositions.size() - 1);
			Vec3 position = (Vec3) anticheatPlayer.get("position");
			Vec3 lastPosition = (Vec3) anticheatPlayer.get("lastPosition");
			double direction = Math
					.toRadians(Math.toDegrees(Math.atan2(position.zCoord - lastPosition.zCoord,
							position.xCoord - lastPosition.xCoord)) - 90);
			int baseY = (int) Math.floor(position.yCoord);
			for (int i = 0; i < 3; i++) {
				String blockBelow = getBlockAtPos(world, lastPosition.xCoord, baseY - i,
						lastPosition.zCoord);
				if (!blockBelow.equals("air")) {
					baseY -= i;
					break;
				}
			}
			boolean onGround = Boolean.TRUE.equals(anticheatPlayer.get("onGround"));
			double totalDistance = previousPositions.get(previousPositions.size() - 1)
					.distanceTo(previousPositions.get(0));
			String blockAhead = getBlockAtPos(world, position.xCoord + (2 * Math.cos(direction)),
					baseY, position.zCoord + (2 * Math.sin(direction)));
			boolean overAir = blockAhead.equals("air");
			String standingBlock = getBlockAtPos(world, lastPosition.xCoord, baseY,
					lastPosition.zCoord);
			boolean matchingBlock = standingBlock
					.equals(lastSwingItem.getDisplayName().toLowerCase());
			boolean unsupported = true;
			if (onGround) {
				String block1Below = getBlockAtPos(world, lastPosition.xCoord, baseY - 1,
						lastPosition.zCoord);
				String block2Below = getBlockAtPos(world, lastPosition.xCoord, baseY - 2,
						lastPosition.zCoord);
				unsupported = block1Below.equals("air") && block2Below.equals("air");
			}
			if (avgSpeed >= 0.14 && totalDistance > 3.4 && overAir && matchingBlock
					&& unsupported) {
				vl++;
				if (vl >= vlThreshold && System.currentTimeMillis() - lastAlert > cooldown) {
					anticheatPlayer.put("ScaffoldC_LastAlert", System.currentTimeMillis());
					printFlag(anticheatPlayer, "ScaffoldC", vl);
				}
			} else {
				vl = Math.max(vl - 1, 0);
			}
		} else {
			vl = Math.max(vl - 1, 0);
		}
		anticheatPlayer.put("ScaffoldC_VL", vl);
	}
}
