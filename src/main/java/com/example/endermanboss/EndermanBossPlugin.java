package com.example.endermanboss;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Enderman;
import org.bukkit.entity.Endermite;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Silverfish;
import org.bukkit.entity.Wither;
import org.bukkit.entity.WitherSkeleton;
import org.bukkit.entity.WitherSkull;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTeleportEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

public final class EndermanBossPlugin extends JavaPlugin implements Listener, TabExecutor {
    private static final double BASE_HEALTH = 500.0;
    private static final double HEALTH_PER_PARTY_MEMBER = 0.75;
    private static final double PHASE_TWO_START_RATIO = 0.50;
    private static final double PHASE_THREE_START_RATIO = 0.20;
    private static final long DAGGER_COOLDOWN_MS = 5_000L;
    private static final long BOOTS_COOLDOWN_MS = 8_000L;
    private static final long WITHER_DASHER_COOLDOWN_MS = 65_000L;
    private static final double WITHER_FOLLOWER_HEALTH = 800.0;

    private NamespacedKey bossKey;
    private NamespacedKey daggerKey;
    private NamespacedKey bootsKey;
    private NamespacedKey voidProjectileKey;
    private NamespacedKey witherBossKey;
    private NamespacedKey witherDasherKey;
    private NamespacedKey witherLauncherKey;
    private NamespacedKey witherFrameKey;
    private BossFight fight;
    private WitherFollowerFight witherFight;
    private final Map<UUID, Party> partiesByLeader = new HashMap<>();
    private final Map<UUID, UUID> memberToLeader = new HashMap<>();
    private final Map<UUID, UUID> pendingInvites = new HashMap<>();
    private final Map<UUID, Long> daggerCooldowns = new HashMap<>();
    private final Map<UUID, Long> bootsCooldowns = new HashMap<>();
    private final Map<UUID, Long> witherDasherCooldowns = new HashMap<>();
    private final Map<UUID, Integer> witherLauncherUses = new HashMap<>();
    private final Map<UUID, Long> witherLauncherCooldowns = new HashMap<>();
    private boolean pluginTeleport;

    @Override
    public void onEnable() {
        bossKey = new NamespacedKey(this, "enderman_boss");
        daggerKey = new NamespacedKey(this, "void_dagger");
        bootsKey = new NamespacedKey(this, "void_boots");
        voidProjectileKey = new NamespacedKey(this, "void_projectile");
        witherBossKey = new NamespacedKey(this, "wither_follower");
        witherDasherKey = new NamespacedKey(this, "wither_dasher");
        witherLauncherKey = new NamespacedKey(this, "wither_launcher");
        witherFrameKey = new NamespacedKey(this, "wither_frame");
        Bukkit.getPluginManager().registerEvents(this, this);
        getCommand("bossspawn").setExecutor(this);
        getCommand("bossinfo").setExecutor(this);
        getCommand("party").setExecutor(this);
    }

    @Override
    public void onDisable() {
        if (fight != null) {
            fight.cleanup(false);
        }
        if (witherFight != null) {
            witherFight.cleanup(false);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String name = command.getName().toLowerCase(Locale.ROOT);
        if (name.equals("party")) {
            return handleParty(sender, args);
        }
        if (name.equals("bossinfo")) {
            sendBossInfo(sender);
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "플레이어만 사용할 수 있습니다.");
            return true;
        }
        if (args.length == 0) {
            player.sendMessage(ChatColor.RED + "사용법: /bossspawn <enderman|wither>");
            return true;
        }
        Party party = getParty(player.getUniqueId());
        List<Player> challengers = party == null ? List.of(player) : party.onlineMembers();
        if (args[0].equalsIgnoreCase("enderman")) {
            if (fight != null && fight.isActive()) {
                player.sendMessage(ChatColor.RED + "이미 진행 중인 끝의 숨결 보스전이 있습니다.");
                return true;
            }
            startFight(player.getLocation(), challengers);
            return true;
        }
        if (args[0].equalsIgnoreCase("wither") || args[0].equalsIgnoreCase("witherfollower")) {
            if (witherFight != null && witherFight.isActive()) {
                player.sendMessage(ChatColor.RED + "이미 진행 중인 위더 추종자 레이드가 있습니다.");
                return true;
            }
            startWitherFollower(player.getLocation(), challengers);
            return true;
        }
        player.sendMessage(ChatColor.RED + "사용법: /bossspawn <enderman|wither>");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (command.getName().equalsIgnoreCase("party") && args.length == 1) {
            return List.of("create", "invite", "join", "leave", "disband", "info").stream()
                .filter(value -> value.startsWith(args[0].toLowerCase(Locale.ROOT))).collect(Collectors.toList());
        }
        if ((command.getName().equalsIgnoreCase("bossspawn") || command.getName().equalsIgnoreCase("bossinfo")) && args.length == 1) {
            return List.of("enderman", "wither");
        }
        return List.of();
    }

    private void startFight(Location location, List<Player> challengers) {
        Enderman boss = location.getWorld().spawn(location, Enderman.class, entity -> {
            entity.setCustomName(ChatColor.DARK_PURPLE + "끝의 숨결");
            entity.setCustomNameVisible(true);
            entity.setRemoveWhenFarAway(false);
            entity.getPersistentDataContainer().set(bossKey, PersistentDataType.BYTE, (byte) 1);
        });
        double maxHealth = BASE_HEALTH * (1.0 + Math.max(0, challengers.size() - 1) * HEALTH_PER_PARTY_MEMBER);
        boss.getAttribute(Attribute.MAX_HEALTH).setBaseValue(maxHealth);
        boss.setHealth(maxHealth);
        BossBar bar = Bukkit.createBossBar(ChatColor.DARK_PURPLE + "끝의 숨결", BarColor.PURPLE, BarStyle.SEGMENTED_10);
        challengers.forEach(bar::addPlayer);
        fight = new BossFight(boss, bar, challengers, maxHealth);
        fight.start();
        Bukkit.broadcastMessage(ChatColor.LIGHT_PURPLE + "끝의 숨결 보스전 시작! 참여 인원: " + challengers.size() + "명, 체력: " + (int) maxHealth);
        Bukkit.broadcastMessage(ChatColor.GRAY + "페이즈 컷라인: 2페이즈 " + (int) phaseTwoCutoff(maxHealth) + " HP / 3페이즈 " + (int) phaseThreeCutoff(maxHealth) + " HP");
    }

    private void sendBossInfo(CommandSender sender) {
        sender.sendMessage(ChatColor.LIGHT_PURPLE + "끝의 숨결: 파티 인원에 비례해 체력과 페이즈 컷라인이 함께 증가합니다.");
        sender.sendMessage(ChatColor.GRAY + "1페이즈: 뒤 텔레포트 공격, 후퇴 후 추적 공허탄 발사");
        sender.sendMessage(ChatColor.GRAY + "2페이즈: 8개 웅덩이, 회귀 엔드마이트, 돌진, 8연속 텔레포트, 낙하 공허탄, 자폭 엔드마이트, 엔드 기둥");
        sender.sendMessage(ChatColor.GRAY + "3페이즈: 30초 처치 제한, 충격파, 실패 시 보상 없음");
        sender.sendMessage(ChatColor.GRAY + "보스가 텔레포트할 때마다 이전 위치에 공허 웅덩이가 남습니다.");
        sender.sendMessage(ChatColor.GRAY + "처치 시 파티 기여도 순위에 따라 공허 단검, 공허 부츠, 경험치를 지급합니다.");
        sender.sendMessage(ChatColor.DARK_GRAY + "위더 추종자: /bossspawn wither - 4페이즈, 위더 대셔/런쳐/뼈대 보상");
    }

    private void startWitherFollower(Location location, List<Player> challengers) {
        WitherSkeleton boss = location.getWorld().spawn(location, WitherSkeleton.class, entity -> {
            entity.setCustomName(ChatColor.DARK_RED + "위더 추종자");
            entity.setCustomNameVisible(true);
            entity.setRemoveWhenFarAway(false);
            entity.getAttribute(Attribute.MAX_HEALTH).setBaseValue(WITHER_FOLLOWER_HEALTH);
            entity.getAttribute(Attribute.ATTACK_DAMAGE).setBaseValue(3.0);
            if (entity.getAttribute(Attribute.SCALE) != null) {
                entity.getAttribute(Attribute.SCALE).setBaseValue(2.0);
            }
            entity.setHealth(WITHER_FOLLOWER_HEALTH);
            entity.getPersistentDataContainer().set(witherBossKey, PersistentDataType.BYTE, (byte) 1);
            entity.setAI(false);
        });
        BossBar bar = Bukkit.createBossBar(ChatColor.DARK_RED + "위더 추종자", BarColor.RED, BarStyle.SEGMENTED_10);
        challengers.forEach(bar::addPlayer);
        witherFight = new WitherFollowerFight(boss, bar, challengers);
        witherFight.start();
        Bukkit.broadcastMessage(ChatColor.DARK_RED + "위더 추종자가 생성되었습니다. 5초 후 활동을 시작합니다!");
    }

    private double phaseTwoCutoff(double maxHealth) {
        return maxHealth * PHASE_TWO_START_RATIO;
    }

    private double phaseThreeCutoff(double maxHealth) {
        return maxHealth * PHASE_THREE_START_RATIO;
    }

    private boolean handleParty(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "플레이어만 사용할 수 있습니다.");
            return true;
        }
        if (args.length == 0) {
            player.sendMessage(ChatColor.YELLOW + "/party <create|invite|join|leave|disband|info>");
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "create" -> createParty(player);
            case "invite" -> inviteParty(player, args);
            case "join" -> joinParty(player, args);
            case "leave" -> leaveParty(player);
            case "disband" -> disbandParty(player);
            case "info" -> showParty(player);
            default -> player.sendMessage(ChatColor.YELLOW + "/party <create|invite|join|leave|disband|info>");
        }
        return true;
    }

    private void createParty(Player player) {
        if (getParty(player.getUniqueId()) != null) {
            player.sendMessage(ChatColor.RED + "이미 파티에 속해 있습니다.");
            return;
        }
        Party party = new Party(player.getUniqueId());
        party.members.add(player.getUniqueId());
        partiesByLeader.put(player.getUniqueId(), party);
        memberToLeader.put(player.getUniqueId(), player.getUniqueId());
        player.sendMessage(ChatColor.GREEN + "파티를 생성했습니다. /party invite <플레이어>");
    }

    private void inviteParty(Player player, String[] args) {
        Party party = partiesByLeader.get(player.getUniqueId());
        if (party == null) {
            player.sendMessage(ChatColor.RED + "파티장만 초대할 수 있습니다.");
            return;
        }
        if (args.length < 2 || Bukkit.getPlayerExact(args[1]) == null) {
            player.sendMessage(ChatColor.RED + "온라인 플레이어 이름을 입력하세요.");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        pendingInvites.put(target.getUniqueId(), player.getUniqueId());
        player.sendMessage(ChatColor.GREEN + target.getName() + "님을 초대했습니다.");
        target.sendMessage(ChatColor.AQUA + player.getName() + "님의 파티 초대: /party join " + player.getName());
    }

    private void joinParty(Player player, String[] args) {
        UUID leaderId = args.length >= 2 && Bukkit.getPlayerExact(args[1]) != null
            ? Bukkit.getPlayerExact(args[1]).getUniqueId() : pendingInvites.get(player.getUniqueId());
        Party party = leaderId == null ? null : partiesByLeader.get(leaderId);
        if (party == null || !leaderId.equals(pendingInvites.get(player.getUniqueId()))) {
            player.sendMessage(ChatColor.RED + "유효한 파티 초대가 없습니다.");
            return;
        }
        if (getParty(player.getUniqueId()) != null) {
            player.sendMessage(ChatColor.RED + "이미 파티에 속해 있습니다.");
            return;
        }
        party.members.add(player.getUniqueId());
        memberToLeader.put(player.getUniqueId(), leaderId);
        pendingInvites.remove(player.getUniqueId());
        party.broadcast(ChatColor.GREEN + player.getName() + "님이 파티에 참가했습니다.");
    }

    private void leaveParty(Player player) {
        Party party = getParty(player.getUniqueId());
        if (party == null) {
            player.sendMessage(ChatColor.RED + "파티에 속해 있지 않습니다.");
            return;
        }
        if (party.leader.equals(player.getUniqueId())) {
            disbandParty(player);
            return;
        }
        party.members.remove(player.getUniqueId());
        memberToLeader.remove(player.getUniqueId());
        party.broadcast(ChatColor.YELLOW + player.getName() + "님이 파티를 떠났습니다.");
    }

    private void disbandParty(Player player) {
        Party party = partiesByLeader.remove(player.getUniqueId());
        if (party == null) {
            player.sendMessage(ChatColor.RED + "파티장만 해산할 수 있습니다.");
            return;
        }
        party.members.forEach(memberToLeader::remove);
        party.broadcast(ChatColor.RED + "파티가 해산되었습니다.");
    }

    private void showParty(Player player) {
        Party party = getParty(player.getUniqueId());
        if (party == null) {
            player.sendMessage(ChatColor.RED + "파티에 속해 있지 않습니다.");
            return;
        }
        player.sendMessage(ChatColor.AQUA + "파티원: " + party.onlineMembers().stream().map(Player::getName).collect(Collectors.joining(", ")));
    }

    private Party getParty(UUID playerId) {
        UUID leader = memberToLeader.get(playerId);
        return leader == null ? null : partiesByLeader.get(leader);
    }

    @EventHandler
    public void onBossDamage(EntityDamageByEntityEvent event) {
        Player player = damagingPlayer(event.getDamager());
        if (fight != null && fight.isBoss(event.getEntity())) {
            if (player != null) {
                fight.addContribution(player.getUniqueId(), event.getFinalDamage());
            }
            return;
        }
        if (witherFight != null && witherFight.isBoss(event.getEntity())) {
            if (witherFight.finalPhase && !(event.getDamager() instanceof WitherSkull)) {
                event.setCancelled(true);
                return;
            }
            if (player != null) {
                witherFight.addContribution(player.getUniqueId(), event.getFinalDamage());
            }
            witherFight.recordIncomingDamage(event.getFinalDamage());
        }
    }

    @EventHandler
    public void onBossDeath(EntityDeathEvent event) {
        if (fight != null && fight.isBoss(event.getEntity())) {
            event.getDrops().clear();
            event.setDroppedExp(0);
            fight.rewardAndCleanup();
            fight = null;
            return;
        }
        if (fight != null && event.getEntity().getPersistentDataContainer().has(voidProjectileKey, PersistentDataType.BYTE)) {
            event.getDrops().clear();
            event.setDroppedExp(0);
            if (fight.isActive()) {
                fight.boss.damage(14.0);
            }
            return;
        }
        if (witherFight != null && witherFight.isBoss(event.getEntity())) {
            event.getDrops().clear();
            event.setDroppedExp(0);
            witherFight.rewardAndCleanup();
            witherFight = null;
        }
    }

    @EventHandler
    public void onTeleport(EntityTeleportEvent event) {
        if (fight == null || !fight.isBoss(event.getEntity())) {
            return;
        }
        if (!pluginTeleport) {
            event.setCancelled(true);
            return;
        }
        fight.spawnHostilePool(event.getFrom(), 80L);
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        if (hasKey(item, daggerKey)) {
            event.setCancelled(true);
            useDagger(player);
        } else if (hasKey(item, witherDasherKey)) {
            event.setCancelled(true);
            useWitherDasher(player);
        } else if (hasKey(item, witherLauncherKey)) {
            event.setCancelled(true);
            useWitherLauncher(player);
        }
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        ItemStack boots = player.getInventory().getBoots();
        if (hasKey(boots, bootsKey) && player.isOnGround()) {
            player.setAllowFlight(true);
        }
        ItemStack leggings = player.getInventory().getLeggings();
        if (hasKey(leggings, witherFrameKey)) {
            applyWitherFrameAura(player);
        }
    }


    @EventHandler
    public void onToggleFlight(PlayerToggleFlightEvent event) {
        Player player = event.getPlayer();
        if (!hasKey(player.getInventory().getBoots(), bootsKey)) {
            return;
        }
        event.setCancelled(true);
        player.setFlying(false);
        player.setAllowFlight(false);
        useBoots(player);
    }


    private void useWitherDasher(Player player) {
        if (isCooling(player, witherDasherCooldowns, WITHER_DASHER_COOLDOWN_MS, "위더 대셔")) {
            return;
        }
        LivingEntity target = nearestTarget(player, 28.0, true);
        if (target == null) {
            player.sendMessage(ChatColor.RED + "위더 대셔 대상이 없습니다.");
            return;
        }
        witherDasherCooldowns.put(player.getUniqueId(), System.currentTimeMillis());
        player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 80, 2));
        taskDasherSequence(player, target, 0, 5);
    }

    private void taskDasherSequence(Player player, LivingEntity target, int step, int maxSteps) {
        if (step >= maxSteps || !player.isOnline() || target.isDead()) {
            return;
        }
        player.setVelocity(target.getLocation().toVector().subtract(player.getLocation().toVector()).normalize().multiply(1.7));
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (target.isValid() && player.getLocation().distanceSquared(target.getLocation()) < 16) {
                target.damage(8.0, player);
            }
            LivingEntity next = nearestTarget(player, 24.0, true);
            if (next != null) {
                taskDasherSequence(player, next, step + 1, maxSteps);
            }
        }, 16L);
    }

    private void useWitherLauncher(Player player) {
        if (isCooling(player, witherLauncherCooldowns, 3_000L, "위더 런쳐")) {
            return;
        }
        int uses = witherLauncherUses.merge(player.getUniqueId(), 1, Integer::sum);
        if (uses >= 3) {
            witherLauncherUses.put(player.getUniqueId(), 0);
            witherLauncherCooldowns.put(player.getUniqueId(), System.currentTimeMillis());
        }
        for (int i = 0; i < 5; i++) {
            Bukkit.getScheduler().runTaskLater(this, () -> shootSkull(player.getEyeLocation(), player.getLocation().getDirection(), player, true), i * 4L);
        }
    }

    private void applyWitherFrameAura(Player player) {
        for (Entity entity : player.getNearbyEntities(5, 3, 5)) {
            if (entity instanceof LivingEntity living && !living.equals(player)) {
                Party party = getParty(player.getUniqueId());
                if (living instanceof Player other && party != null && party.members.contains(other.getUniqueId())) {
                    continue;
                }
                living.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 60, 0));
            }
        }
    }

    private WitherSkull shootSkull(Location start, Vector direction, LivingEntity shooter, boolean canBeParried) {
        WitherSkull skull = start.getWorld().spawn(start, WitherSkull.class, entity -> {
            entity.setShooter(shooter);
            entity.setDirection(direction.normalize());
            entity.setVelocity(direction.normalize().multiply(1.3));
            entity.setYield(canBeParried ? 1.0f : 2.0f);
            entity.setCharged(!canBeParried);
        });
        return skull;
    }

    private void useDagger(Player player) {
        if (isCooling(player, daggerCooldowns, DAGGER_COOLDOWN_MS, "공허 단검")) {
            return;
        }
        LivingEntity target = nearestTarget(player, 30.0, true);
        if (target == null) {
            player.sendMessage(ChatColor.RED + "근처에 순간이동할 대상이 없습니다.");
            return;
        }
        Location safe = safeBehind(target);
        player.teleport(safe);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.2f);
        daggerCooldowns.put(player.getUniqueId(), System.currentTimeMillis());
    }

    private void useBoots(Player player) {
        if (isCooling(player, bootsCooldowns, BOOTS_COOLDOWN_MS, "공허 부츠")) {
            return;
        }
        Vector direction = player.getLocation().getDirection().normalize().multiply(1.8).setY(0.35);
        player.setVelocity(direction);
        bootsCooldowns.put(player.getUniqueId(), System.currentTimeMillis());
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline()) {
                    cancel();
                    return;
                }
                if (player.isOnGround()) {
                    spawnFriendlyPool(player.getLocation(), player);
                    cancel();
                }
            }
        }.runTaskTimer(this, 4L, 2L);
    }

    private boolean isCooling(Player player, Map<UUID, Long> map, long cooldown, String itemName) {
        long remain = cooldown - (System.currentTimeMillis() - map.getOrDefault(player.getUniqueId(), 0L));
        if (remain > 0) {
            player.sendMessage(ChatColor.RED + itemName + " 쿨타임: " + String.format(Locale.ROOT, "%.1f", remain / 1000.0) + "초");
            return true;
        }
        return false;
    }

    private LivingEntity nearestTarget(Player player, double range, boolean excludeParty) {
        Party party = getParty(player.getUniqueId());
        return player.getNearbyEntities(range, range, range).stream()
            .filter(entity -> entity instanceof LivingEntity && !entity.equals(player))
            .map(entity -> (LivingEntity) entity)
            .filter(entity -> !(entity instanceof Player p) || !excludeParty || party == null || !party.members.contains(p.getUniqueId()))
            .min(Comparator.comparingDouble(entity -> entity.getLocation().distanceSquared(player.getLocation())))
            .orElse(null);
    }

    private Location safeBehind(LivingEntity target) {
        Location base = target.getLocation().clone().subtract(target.getLocation().getDirection().normalize().multiply(1.6));
        World world = base.getWorld();
        for (int y = 0; y <= 3; y++) {
            Location candidate = new Location(world, base.getX(), base.getBlockY() + y, base.getZ(), target.getLocation().getYaw(), 0);
            if (candidate.getBlock().isPassable() && candidate.clone().add(0, 1, 0).getBlock().isPassable() && !candidate.clone().subtract(0, 1, 0).getBlock().isPassable()) {
                return candidate.add(0.5, 0, 0.5);
            }
        }
        return target.getLocation();
    }

    private boolean hasKey(ItemStack item, NamespacedKey key) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        return item.getItemMeta().getPersistentDataContainer().has(key, PersistentDataType.BYTE);
    }

    private Player damagingPlayer(Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player player) {
            return player;
        }
        return null;
    }

    private void spawnFriendlyPool(Location center, Player owner) {
        center.getWorld().playSound(center, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.8f, 0.7f);
        new BukkitRunnable() {
            int ticks;
            @Override
            public void run() {
                ticks += 10;
                drawPool(center, Color.fromRGB(90, 40, 180));
                Party party = getParty(owner.getUniqueId());
                for (Entity entity : center.getWorld().getNearbyEntities(center, 3.0, 1.5, 3.0)) {
                    if (entity instanceof LivingEntity living && !living.equals(owner)) {
                        if (living instanceof Player player && (player.equals(owner) || party != null && party.members.contains(player.getUniqueId()))) {
                            continue;
                        }
                        living.damage(4.0, owner);
                    }
                }
                if (ticks >= 100) {
                    cancel();
                }
            }
        }.runTaskTimer(this, 0L, 10L);
    }

    private void drawPool(Location center, Color color) {
        center.getWorld().spawnParticle(Particle.DUST, center, 80, 2.6, 0.08, 2.6, new Particle.DustOptions(color, 1.5f));
        center.getWorld().spawnParticle(Particle.DRAGON_BREATH, center, 25, 2.2, 0.05, 2.2, 0.01);
    }

    private ItemStack createDagger() {
        ItemStack item = new ItemStack(Material.NETHERITE_SWORD);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.DARK_PURPLE + "공허의 단검");
        meta.setLore(List.of(ChatColor.GRAY + "우클릭: 근처 생명체 뒤 안전한 위치로 순간이동", ChatColor.GRAY + "쿨타임 5초"));
        meta.addEnchant(Enchantment.SHARPNESS, 5, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        meta.getPersistentDataContainer().set(daggerKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createBoots() {
        ItemStack item = new ItemStack(Material.NETHERITE_BOOTS);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.DARK_PURPLE + "공허 부츠");
        meta.setLore(List.of(ChatColor.GRAY + "공중에서 점프: 앞으로 돌진", ChatColor.GRAY + "착지 지점에 아군 피해 없는 공허 웅덩이 생성"));
        meta.addEnchant(Enchantment.PROTECTION, 4, true);
        meta.getPersistentDataContainer().set(bootsKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }


    private ItemStack createWitherDasher() {
        ItemStack item = new ItemStack(Material.NETHERITE_SWORD);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.DARK_RED + "위더 대셔");
        meta.setLore(List.of(ChatColor.GRAY + "우클릭: 위더 추종자의 연계 돌진을 즉시 사용", ChatColor.GRAY + "사용 중 저항, 쿨타임 65초"));
        meta.addEnchant(Enchantment.SHARPNESS, 5, true);
        meta.getPersistentDataContainer().set(witherDasherKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createWitherLauncher() {
        ItemStack item = new ItemStack(Material.BOW);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.DARK_RED + "위더 런쳐");
        meta.setLore(List.of(ChatColor.GRAY + "우클릭: 보는 방향으로 위더 해골 5연사", ChatColor.GRAY + "3회 사용 후 3초 쿨타임"));
        meta.addEnchant(Enchantment.POWER, 5, true);
        meta.getPersistentDataContainer().set(witherLauncherKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createWitherFrame() {
        ItemStack item = new ItemStack(Material.NETHERITE_LEGGINGS);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.DARK_RED + "위더의 뼈대");
        meta.setLore(List.of(ChatColor.GRAY + "착용: 근처 적에게 위더 오라 부여"));
        meta.addEnchant(Enchantment.PROTECTION, 4, true);
        meta.getPersistentDataContainer().set(witherFrameKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }


    private final class WitherFollowerFight {
        private final WitherSkeleton boss;
        private final BossBar bar;
        private final Set<UUID> challengers;
        private final Map<UUID, Double> contribution = new HashMap<>();
        private final List<Integer> taskIds = new ArrayList<>();
        private int patternCount;
        private boolean ultimateRunning;
        private boolean finalPhase;
        private boolean weakWithersAlive;
        private double incomingWindowDamage;

        WitherFollowerFight(WitherSkeleton boss, BossBar bar, List<Player> challengers) {
            this.boss = boss;
            this.bar = bar;
            this.challengers = challengers.stream().map(Player::getUniqueId).collect(Collectors.toCollection(HashSet::new));
        }

        void start() {
            taskIds.add(Bukkit.getScheduler().runTaskLater(EndermanBossPlugin.this, () -> boss.setAI(true), 100L).getTaskId());
            long patternInterval = Math.max(45L, 100L - Math.max(0, challengers.size() - 1) * 10L);
            taskIds.add(Bukkit.getScheduler().runTaskTimer(EndermanBossPlugin.this, this::tick, 20L, 20L).getTaskId());
            taskIds.add(Bukkit.getScheduler().runTaskTimer(EndermanBossPlugin.this, this::usePattern, 140L, patternInterval).getTaskId());
        }

        boolean isActive() {
            return boss.isValid() && !boss.isDead();
        }

        boolean isBoss(Entity entity) {
            return entity.getUniqueId().equals(boss.getUniqueId());
        }

        void addContribution(UUID id, double damage) {
            if (challengers.contains(id)) {
                contribution.merge(id, damage, Double::sum);
            }
        }

        void recordIncomingDamage(double damage) {
            incomingWindowDamage += damage;
        }

        Player randomTarget() {
            List<Player> online = challengers.stream()
                .map(Bukkit::getPlayer)
                .filter(player -> player != null && player.isOnline() && !player.isDead() && player.getWorld().equals(boss.getWorld()))
                .collect(Collectors.toList());
            if (online.isEmpty()) {
                return null;
            }
            return online.get(ThreadLocalRandom.current().nextInt(online.size()));
        }

        void tick() {
            if (!isActive()) {
                cleanup(false);
                return;
            }
            if (randomTarget() == null) {
                Bukkit.broadcastMessage(ChatColor.RED + "모든 파티원이 사망하여 위더 추종자 레이드에 실패했습니다.");
                cleanup(false);
                return;
            }
            double hp = boss.getHealth();
            bar.setProgress(Math.max(0.0, Math.min(1.0, hp / WITHER_FOLLOWER_HEALTH)));
            if (hp <= 100) {
                bar.setColor(BarColor.PURPLE);
                if (!finalPhase) {
                    enterFinalPhase();
                }
            } else if (hp <= 200) {
                bar.setColor(BarColor.BLUE);
            } else if (hp <= 500) {
                bar.setColor(BarColor.BLACK);
            } else {
                bar.setColor(BarColor.RED);
            }
        }

        void usePattern() {
            if (!isActive() || finalPhase || ultimateRunning) {
                return;
            }
            double hp = boss.getHealth();
            if (hp > 500) {
                switch (ThreadLocalRandom.current().nextInt(5)) {
                    case 0 -> leapQuake();
                    case 1 -> chainDash(0, new HashSet<>());
                    case 2 -> explosiveShot();
                    case 3 -> damageCheckStance(40, 12, false);
                    default -> summonBurrowingWorms();
                }
            } else if (hp > 200) {
                patternCount++;
                if (patternCount >= 5 && ThreadLocalRandom.current().nextInt(4) == 0) {
                    phaseTwoUltimate();
                    patternCount = 0;
                    return;
                }
                switch (ThreadLocalRandom.current().nextInt(3)) {
                    case 0 -> skullBarrage(15, true);
                    case 1 -> summonWeakWithers();
                    default -> radialSkulls();
                }
            } else {
                switch (ThreadLocalRandom.current().nextInt(4)) {
                    case 0 -> focusedSkulls();
                    case 1 -> phaseTwoUltimate();
                    case 2 -> leapQuake();
                    default -> explosiveShot();
                }
            }
        }

        void leapQuake() {
            Player target = randomTarget();
            if (target == null) return;
            boss.setVelocity(new Vector(0, 1.5, 0));
            taskIds.add(Bukkit.getScheduler().runTaskLater(EndermanBossPlugin.this, () -> {
                if (!isActive()) return;
                boss.teleport(target.getLocation());
                boss.getWorld().spawnParticle(Particle.EXPLOSION, boss.getLocation(), 2);
                for (Entity entity : boss.getNearbyEntities(2, 2, 2)) {
                    if (entity instanceof LivingEntity living && !living.equals(boss)) {
                        living.damage(8, boss);
                        living.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 100, 0));
                    }
                }
            }, 30L).getTaskId());
        }

        void chainDash(int count, Set<UUID> hit) {
            if (count >= 3) return;
            Player target = randomTarget();
            if (target == null || hit.contains(target.getUniqueId())) return;
            hit.add(target.getUniqueId());
            boss.setVelocity(target.getLocation().toVector().subtract(boss.getLocation().toVector()).normalize().multiply(1.8));
            taskIds.add(Bukkit.getScheduler().runTaskLater(EndermanBossPlugin.this, () -> {
                if (target.isOnline() && boss.getLocation().distanceSquared(target.getLocation()) < 16) {
                    target.damage(7, boss);
                }
                chainDash(count + 1, hit);
            }, 18L).getTaskId());
        }

        void explosiveShot() {
            Player target = randomTarget();
            if (target == null) return;
            Vector dir = target.getLocation().toVector().subtract(boss.getEyeLocation().toVector()).normalize();
            WitherSkull skull = shootSkull(boss.getEyeLocation(), dir, boss, true);
            taskIds.add(Bukkit.getScheduler().runTaskLater(EndermanBossPlugin.this, skull::remove, 30L).getTaskId());
        }

        void damageCheckStance(double requiredDamage, int seconds, boolean selfDamageOnSuccess) {
            ultimateRunning = true;
            incomingWindowDamage = 0;
            boss.setAI(false);
            Bukkit.broadcastMessage(ChatColor.DARK_RED + "위더 추종자가 힘을 모읍니다! " + seconds + "초 안에 " + (int) requiredDamage + " 피해를 주세요.");
            taskIds.add(Bukkit.getScheduler().runTaskLater(EndermanBossPlugin.this, () -> {
                if (!isActive()) return;
                if (incomingWindowDamage < requiredDamage) {
                    Bukkit.broadcastMessage(ChatColor.RED + "저지 실패! 위더 추종자가 폭발합니다.");
                    boss.getWorld().createExplosion(boss.getLocation(), 3.0f, false, false, boss);
                    for (Entity entity : boss.getNearbyEntities(8, 4, 8)) {
                        if (entity instanceof LivingEntity living && !living.equals(boss)) {
                            living.damage(8, boss);
                            living.setVelocity(living.getLocation().toVector().subtract(boss.getLocation().toVector()).normalize().multiply(1.8));
                        }
                    }
                } else {
                    Bukkit.broadcastMessage(ChatColor.GREEN + "저지 성공! 위더 추종자가 그로기 상태에 빠집니다.");
                    if (selfDamageOnSuccess) boss.damage(30.0);
                    boss.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 60, 0));
                }
                boss.setAI(true);
                ultimateRunning = false;
            }, seconds * 20L).getTaskId());
        }

        void summonBurrowingWorms() {
            for (int i = 0; i < 18; i++) {
                Location spawn = boss.getLocation().clone().add(ThreadLocalRandom.current().nextDouble(-10, 10), 0, ThreadLocalRandom.current().nextDouble(-10, 10));
                spawn.getWorld().spawnParticle(Particle.BLOCK, spawn, 20, 0.3, 0.1, 0.3, Material.SOUL_SAND.createBlockData());
                Silverfish worm = spawn.getWorld().spawn(spawn, Silverfish.class, entity -> {
                    entity.setCustomName(ChatColor.DARK_GRAY + "침식하는 벌레");
                    entity.getAttribute(Attribute.MAX_HEALTH).setBaseValue(10.0);
                    entity.setHealth(10.0);
                });
                taskIds.add(Bukkit.getScheduler().runTaskTimer(EndermanBossPlugin.this, () -> {
                    Player target = randomTarget();
                    if (!worm.isValid() || target == null) { worm.remove(); return; }
                    worm.setTarget(target);
                    if (worm.getLocation().distanceSquared(target.getLocation()) < 3) {
                        target.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 100, 0));
                    }
                }, 0L, 20L).getTaskId());
            }
        }

        void skullBarrage(int seconds, boolean parryable) {
            boss.setAI(false);
            boss.setVelocity(new Vector(0, 0.4, 0));
            for (int i = 0; i < seconds * 2; i++) {
                taskIds.add(Bukkit.getScheduler().runTaskLater(EndermanBossPlugin.this, () -> {
                    Player target = randomTarget();
                    if (target != null && isActive()) shootSkull(boss.getEyeLocation(), target.getLocation().toVector().subtract(boss.getEyeLocation().toVector()), boss, parryable);
                }, i * 10L).getTaskId());
            }
            taskIds.add(Bukkit.getScheduler().runTaskLater(EndermanBossPlugin.this, () -> boss.setAI(true), seconds * 20L).getTaskId());
        }

        void summonWeakWithers() {
            if (weakWithersAlive) return;
            weakWithersAlive = true;
            List<Wither> withers = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                Wither wither = boss.getWorld().spawn(boss.getLocation().clone().add(i * 2 - 1, 1, 0), Wither.class, entity -> {
                    entity.setCustomName(ChatColor.GRAY + "약화된 위더");
                    entity.getAttribute(Attribute.MAX_HEALTH).setBaseValue(20.0);
                    entity.setHealth(20.0);
                    if (entity.getAttribute(Attribute.ATTACK_DAMAGE) != null) entity.getAttribute(Attribute.ATTACK_DAMAGE).setBaseValue(2.0);
                });
                withers.add(wither);
            }
            taskIds.add(Bukkit.getScheduler().runTaskTimer(EndermanBossPlugin.this, () -> weakWithersAlive = withers.stream().anyMatch(Entity::isValid), 20L, 20L).getTaskId());
        }

        void radialSkulls() {
            for (int i = 0; i < 12; i++) {
                double angle = Math.PI * 2 * i / 12.0;
                shootSkull(boss.getEyeLocation(), new Vector(Math.cos(angle), 0.05, Math.sin(angle)), boss, true);
            }
        }

        void focusedSkulls() {
            Player target = randomTarget();
            if (target == null) return;
            for (int i = 0; i < 3; i++) {
                taskIds.add(Bukkit.getScheduler().runTaskLater(EndermanBossPlugin.this, () -> shootSkull(boss.getEyeLocation(), target.getLocation().toVector().subtract(boss.getEyeLocation().toVector()), boss, false), i * 8L).getTaskId());
            }
        }

        void phaseTwoUltimate() {
            Player target = randomTarget();
            if (target == null) return;
            ultimateRunning = true;
            damageCheckStance(50, 15, true);
            taskIds.add(Bukkit.getScheduler().runTaskLater(EndermanBossPlugin.this, () -> {
                if (incomingWindowDamage >= 50 || !isActive()) return;
                chainDash(0, new HashSet<>());
                for (int i = 0; i < 5; i++) {
                    taskIds.add(Bukkit.getScheduler().runTaskLater(EndermanBossPlugin.this, this::focusedSkulls, 40L + i * 20L).getTaskId());
                }
                taskIds.add(Bukkit.getScheduler().runTaskLater(EndermanBossPlugin.this, () -> {
                    for (Entity entity : boss.getNearbyEntities(10, 5, 10)) {
                        if (entity instanceof LivingEntity living && !living.equals(boss)) {
                            living.setVelocity(boss.getLocation().toVector().subtract(living.getLocation().toVector()).normalize().multiply(1.2));
                            living.damage(10, boss);
                        }
                    }
                    ultimateRunning = false;
                }, 160L).getTaskId());
            }, 300L).getTaskId());
        }

        void enterFinalPhase() {
            finalPhase = true;
            boss.setAI(false);
            boss.setVelocity(new Vector(0, 1.2, 0));
            Bukkit.broadcastMessage(ChatColor.DARK_GRAY + "위더 추종자가 공중에서 최후의 난사를 시작합니다!");
            taskIds.add(Bukkit.getScheduler().runTaskTimer(EndermanBossPlugin.this, () -> {
                if (!isActive()) return;
                Player target = randomTarget();
                if (target != null) shootSkull(boss.getEyeLocation(), target.getLocation().toVector().subtract(boss.getEyeLocation().toVector()), boss, ThreadLocalRandom.current().nextBoolean());
            }, 0L, 8L).getTaskId());
        }

        void rewardAndCleanup() {
            List<Map.Entry<UUID, Double>> ranking = contribution.entrySet().stream()
                .filter(entry -> challengers.contains(entry.getKey()))
                .sorted(Map.Entry.<UUID, Double>comparingByValue().reversed())
                .collect(Collectors.toList());
            for (UUID id : challengers) contribution.putIfAbsent(id, 0.0);
            ranking = contribution.entrySet().stream().filter(entry -> challengers.contains(entry.getKey())).sorted(Map.Entry.<UUID, Double>comparingByValue().reversed()).collect(Collectors.toList());
            Bukkit.broadcastMessage(ChatColor.DARK_RED + "===== 위더 추종자 기여도 =====");
            for (int i = 0; i < ranking.size(); i++) {
                Player player = Bukkit.getPlayer(ranking.get(i).getKey());
                if (player == null) continue;
                Bukkit.broadcastMessage(ChatColor.RED + String.valueOf(i + 1) + "위 " + player.getName() + " - " + String.format(Locale.ROOT, "%.1f", ranking.get(i).getValue()) + " 피해");
            }
            if (ranking.size() == 1) {
                Player solo = Bukkit.getPlayer(ranking.get(0).getKey());
                if (solo != null) {
                    give(solo, createWitherDasher()); give(solo, createWitherLauncher()); give(solo, createWitherFrame()); solo.giveExp(2500);
                }
            } else {
                for (int i = 0; i < ranking.size(); i++) {
                    Player player = Bukkit.getPlayer(ranking.get(i).getKey());
                    if (player == null) continue;
                    if (i == 0) give(player, createWitherDasher());
                    else if (i == 1) give(player, createWitherLauncher());
                    else if (i == 2) give(player, createWitherFrame());
                    else player.giveExp(1800);
                }
            }
            cleanup(true);
        }

        void give(Player player, ItemStack item) {
            Map<Integer, ItemStack> leftovers = player.getInventory().addItem(item);
            leftovers.values().forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
        }

        void cleanup(boolean keepBossDeath) {
            taskIds.forEach(Bukkit.getScheduler()::cancelTask);
            bar.removeAll();
            if (!keepBossDeath && boss.isValid()) boss.remove();
        }
    }

    private final class BossFight {
        private final Enderman boss;
        private final BossBar bar;
        private final Set<UUID> challengers;
        private final Map<UUID, Double> contribution = new HashMap<>();
        private final List<Integer> taskIds = new ArrayList<>();
        private final double maxHealth;
        private boolean finalPhaseStarted;
        private boolean rewardAllowed = true;

        BossFight(Enderman boss, BossBar bar, List<Player> challengers, double maxHealth) {
            this.boss = boss;
            this.bar = bar;
            this.challengers = challengers.stream().map(Player::getUniqueId).collect(Collectors.toCollection(HashSet::new));
            this.maxHealth = maxHealth;
        }

        void start() {
            taskIds.add(Bukkit.getScheduler().runTaskTimer(EndermanBossPlugin.this, this::tick, 20L, 20L).getTaskId());
            taskIds.add(Bukkit.getScheduler().runTaskTimer(EndermanBossPlugin.this, this::usePattern, 80L, 120L).getTaskId());
        }

        boolean isActive() {
            return boss.isValid() && !boss.isDead();
        }

        boolean isBoss(Entity entity) {
            return entity.getUniqueId().equals(boss.getUniqueId());
        }

        void addContribution(UUID playerId, double damage) {
            if (challengers.contains(playerId)) {
                contribution.merge(playerId, damage, Double::sum);
            }
        }

        void tick() {
            if (!isActive()) {
                cleanup(false);
                return;
            }
            if (allChallengersDown()) {
                Bukkit.broadcastMessage(ChatColor.RED + "모든 파티원이 사망하여 끝의 숨결 레이드에 실패했습니다.");
                cleanup(false);
                return;
            }
            double progress = Math.max(0.0, boss.getHealth() / maxHealth);
            bar.setProgress(Math.min(1.0, progress));
            if (boss.getHealth() <= phaseThreeCutoff(maxHealth)) {
                bar.setColor(BarColor.BLUE);
                if (!finalPhaseStarted) {
                    enterFinalPhase();
                }
            } else if (boss.getHealth() <= phaseTwoCutoff(maxHealth)) {
                bar.setColor(BarColor.RED);
            }
        }

        void usePattern() {
            if (!isActive() || finalPhaseStarted) {
                return;
            }
            double health = boss.getHealth();
            ThreadLocalRandom random = ThreadLocalRandom.current();
            if (health > phaseTwoCutoff(maxHealth)) {
                if (random.nextBoolean()) {
                    teleportBehindNearest();
                } else {
                    retreatAndShootVoidProjectiles();
                }
                return;
            }
            if (health > phaseThreeCutoff(maxHealth)) {
                switch (random.nextInt(7)) {
                    case 0 -> spawnEightPools();
                    case 1 -> spawnReturnMites();
                    case 2 -> chargeNearest();
                    case 3 -> teleportCombo();
                    case 4 -> fallingVoidProjectiles();
                    case 5 -> spawnExplosiveMites();
                    default -> endPillarAttack();
                }
            }
        }


        LivingEntity nearestChallenger(double range) {
            List<Player> candidates = challengers.stream()
                .map(Bukkit::getPlayer)
                .filter(player -> player != null && player.isOnline() && !player.isDead())
                .filter(player -> player.getWorld().equals(boss.getWorld()))
                .filter(player -> player.getLocation().distanceSquared(boss.getLocation()) <= range * range)
                .collect(Collectors.toList());
            if (candidates.isEmpty()) {
                return null;
            }
            return candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
        }

        void retreatAndShootVoidProjectiles() {
            LivingEntity target = nearestChallenger(32);
            if (target == null) {
                return;
            }
            Vector away = boss.getLocation().toVector().subtract(target.getLocation().toVector()).normalize();
            pluginTeleport = true;
            try {
                boss.teleport(boss.getLocation().add(away.multiply(8)));
            } finally {
                pluginTeleport = false;
            }
            for (int i = 0; i < 4; i++) {
                spawnVoidProjectile(boss.getEyeLocation().add(0, i * 0.25, 0), target);
            }
        }

        void spawnVoidProjectile(Location start, LivingEntity target) {
            Endermite mite = start.getWorld().spawn(start, Endermite.class, entity -> {
                entity.setCustomName(ChatColor.DARK_PURPLE + "공허탄");
                entity.setCustomNameVisible(true);
                entity.setAI(false);
                entity.getAttribute(Attribute.MAX_HEALTH).setBaseValue(10.0);
                entity.setHealth(10.0);
                entity.getPersistentDataContainer().set(voidProjectileKey, PersistentDataType.BYTE, (byte) 1);
            });
            taskIds.add(Bukkit.getScheduler().runTaskTimer(EndermanBossPlugin.this, () -> {
                if (!mite.isValid() || !isActive() || target.isDead()) {
                    mite.remove();
                    return;
                }
                Vector velocity = target.getLocation().add(0, 1, 0).toVector().subtract(mite.getLocation().toVector()).normalize().multiply(0.55);
                mite.teleport(mite.getLocation().add(velocity));
                mite.getWorld().spawnParticle(Particle.PORTAL, mite.getLocation(), 8, 0.15, 0.15, 0.15);
                if (mite.getLocation().distanceSquared(target.getLocation()) < 2.25) {
                    target.damage(8.0, boss);
                    mite.remove();
                }
            }, 0L, 2L).getTaskId());
        }

        void spawnReturnMites() {
            boss.damage(Math.min(30.0, Math.max(0.0, boss.getHealth() - phaseThreeCutoff(maxHealth) - 1.0)));
            for (int i = 0; i < 15; i++) {
                double angle = Math.PI * 2 * i / 15.0;
                Location spawn = boss.getLocation().clone().add(Math.cos(angle) * 8, 0, Math.sin(angle) * 8);
                Endermite mite = spawn.getWorld().spawn(spawn, Endermite.class, entity -> entity.setCustomName(ChatColor.LIGHT_PURPLE + "회귀 엔드마이트"));
                taskIds.add(Bukkit.getScheduler().runTaskTimer(EndermanBossPlugin.this, () -> {
                    if (!mite.isValid() || !isActive()) {
                        mite.remove();
                        return;
                    }
                    Vector step = boss.getLocation().toVector().subtract(mite.getLocation().toVector()).normalize().multiply(0.35);
                    mite.setVelocity(step);
                    if (mite.getLocation().distanceSquared(boss.getLocation()) < 3.0) {
                        boss.setHealth(Math.min(maxHealth, boss.getHealth() + 5.0));
                        mite.remove();
                    }
                }, 0L, 5L).getTaskId());
            }
        }

        void chargeNearest() {
            LivingEntity target = nearestChallenger(32);
            if (target == null) {
                return;
            }
            Vector velocity = target.getLocation().toVector().subtract(boss.getLocation().toVector()).normalize().multiply(1.8);
            boss.setVelocity(velocity);
            boss.getWorld().playSound(boss.getLocation(), Sound.ENTITY_ENDER_DRAGON_FLAP, 1.0f, 0.8f);
        }

        void teleportCombo() {
            LivingEntity target = nearestChallenger(32);
            if (target == null) {
                return;
            }
            for (int i = 0; i < 8; i++) {
                int delay = i * 8;
                taskIds.add(Bukkit.getScheduler().runTaskLater(EndermanBossPlugin.this, () -> {
                    if (!isActive() || target.isDead()) {
                        return;
                    }
                    teleportBehindNearest();
                    if (boss.getLocation().distanceSquared(target.getLocation()) < 9.0) {
                        target.damage(7.0, boss);
                    }
                }, delay).getTaskId());
            }
        }

        void fallingVoidProjectiles() {
            LivingEntity target = nearestChallenger(32);
            if (target == null) {
                return;
            }
            for (int i = 0; i < 6; i++) {
                Location start = target.getLocation().clone().add(ThreadLocalRandom.current().nextDouble(-3, 3), 8 + i, ThreadLocalRandom.current().nextDouble(-3, 3));
                spawnVoidProjectile(start, target);
            }
        }

        void spawnExplosiveMites() {
            LivingEntity target = nearestChallenger(32);
            if (target == null) {
                return;
            }
            for (int i = 0; i < 5; i++) {
                Endermite mite = boss.getWorld().spawn(boss.getLocation().clone().add(i - 2, 0, 0), Endermite.class, entity -> entity.setCustomName(ChatColor.RED + "자폭 엔드마이트"));
                taskIds.add(Bukkit.getScheduler().runTaskTimer(EndermanBossPlugin.this, () -> {
                    if (!mite.isValid() || !isActive() || target.isDead()) {
                        mite.remove();
                        return;
                    }
                    mite.setVelocity(target.getLocation().toVector().subtract(mite.getLocation().toVector()).normalize().multiply(0.45));
                    if (mite.getLocation().distanceSquared(target.getLocation()) < 4.0) {
                        mite.getWorld().spawnParticle(Particle.EXPLOSION, mite.getLocation(), 2);
                        for (Entity nearby : mite.getNearbyEntities(3, 3, 3)) {
                            if (nearby instanceof LivingEntity living && !living.equals(boss)) {
                                living.damage(10.0, boss);
                            }
                        }
                        mite.remove();
                    }
                }, 0L, 5L).getTaskId());
            }
        }

        void endPillarAttack() {
            LivingEntity target = nearestChallenger(32);
            if (target == null) {
                return;
            }
            Location center = target.getLocation();
            center.getWorld().spawnParticle(Particle.DUST, center, 90, 2.0, 0.1, 2.0, new Particle.DustOptions(Color.RED, 1.5f));
            taskIds.add(Bukkit.getScheduler().runTaskLater(EndermanBossPlugin.this, () -> {
                center.getWorld().spawnParticle(Particle.DRAGON_BREATH, center.clone().add(0, 3, 0), 160, 1.6, 3.0, 1.6, 0.02);
                for (Entity nearby : center.getWorld().getNearbyEntities(center, 2.5, 6, 2.5)) {
                    if (nearby instanceof LivingEntity living && !living.equals(boss)) {
                        living.damage(8.0, boss);
                    }
                }
            }, 30L).getTaskId());
        }

        void enterFinalPhase() {
            finalPhaseStarted = true;
            boss.setAI(false);
            boss.setVelocity(new Vector(0, 0, 0));
            Bukkit.broadcastMessage(ChatColor.DARK_PURPLE + "끝의 숨결이 힘을 모읍니다! 30초 안에 처치하세요.");
            taskIds.add(Bukkit.getScheduler().runTaskTimer(EndermanBossPlugin.this, () -> {
                if (isActive()) {
                    boss.getWorld().spawnParticle(Particle.SONIC_BOOM, boss.getLocation(), 2, 1.5, 0.5, 1.5);
                    for (Entity nearby : boss.getNearbyEntities(8, 4, 8)) {
                        if (nearby instanceof LivingEntity living && !living.equals(boss)) {
                            living.setVelocity(living.getLocation().toVector().subtract(boss.getLocation().toVector()).normalize().multiply(1.2));
                            living.damage(4.0, boss);
                        }
                    }
                }
            }, 0L, 160L).getTaskId());
            taskIds.add(Bukkit.getScheduler().runTaskLater(EndermanBossPlugin.this, () -> {
                if (!isActive()) {
                    return;
                }
                rewardAllowed = false;
                Location location = boss.getLocation();
                location.getWorld().spawnParticle(Particle.EXPLOSION, location, 8, 2, 2, 2);
                location.getWorld().createExplosion(location, 4.0f, false, false, boss);
                boss.remove();
                cleanup(true);
                Bukkit.broadcastMessage(ChatColor.RED + "끝의 숨결 처치에 실패했습니다. 보상은 지급되지 않습니다.");
            }, 600L).getTaskId());
        }

        void spawnEightPools() {
            Location origin = boss.getLocation();
            for (int i = 0; i < 8; i++) {
                double angle = Math.PI * 2 * i / 8.0;
                Location pool = origin.clone().add(Math.cos(angle) * 5.0, 0, Math.sin(angle) * 5.0);
                spawnHostilePool(pool, 100L);
            }
            origin.getWorld().playSound(origin, Sound.ENTITY_ENDERMAN_SCREAM, 1.0f, 0.8f);
        }

        void teleportBehindNearest() {
            LivingEntity target = boss.getNearbyEntities(30, 20, 30).stream()
                .filter(entity -> entity instanceof Player player && challengers.contains(player.getUniqueId()))
                .map(entity -> (LivingEntity) entity)
                .min(Comparator.comparingDouble(entity -> entity.getLocation().distanceSquared(boss.getLocation())))
                .orElse(null);
            if (target == null) {
                return;
            }
            Location destination = safeBehind(target);
            pluginTeleport = true;
            try {
                boss.teleport(destination);
            } finally {
                pluginTeleport = false;
            }
            boss.getWorld().playSound(destination, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 0.7f);
        }

        boolean allChallengersDown() {
            return challengers.stream()
                .map(Bukkit::getPlayer)
                .noneMatch(player -> player != null && player.isOnline() && !player.isDead() && player.getWorld().equals(boss.getWorld()));
        }

        void spawnHostilePool(Location center, long durationTicks) {
            center.getWorld().playSound(center, Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 0.8f, 0.7f);
            int poolTaskId = new BukkitRunnable() {
                int ticks;
                @Override
                public void run() {
                    ticks += 10;
                    drawPool(center, Color.fromRGB(45, 0, 90));
                    for (Entity entity : center.getWorld().getNearbyEntities(center, 3.0, 1.5, 3.0)) {
                        if (entity instanceof LivingEntity living && !living.equals(boss)) {
                            living.damage(5.0, boss);
                        }
                    }
                    if (ticks >= durationTicks || !isActive()) {
                        cancel();
                    }
                }
            }.runTaskTimer(EndermanBossPlugin.this, 0L, 10L).getTaskId();
            taskIds.add(poolTaskId);
        }

        void rewardAndCleanup() {
            List<Map.Entry<UUID, Double>> ranking = contribution.entrySet().stream()
                .filter(entry -> challengers.contains(entry.getKey()))
                .sorted(Map.Entry.<UUID, Double>comparingByValue().reversed())
                .collect(Collectors.toList());
            for (UUID id : challengers) {
                contribution.putIfAbsent(id, 0.0);
            }
            ranking = contribution.entrySet().stream()
                .filter(entry -> challengers.contains(entry.getKey()))
                .sorted(Map.Entry.<UUID, Double>comparingByValue().reversed())
                .collect(Collectors.toList());
            announceContribution(ranking);
            if (!rewardAllowed) {
                cleanup(true);
                return;
            }
            if (ranking.size() == 1) {
                Player solo = Bukkit.getPlayer(ranking.get(0).getKey());
                if (solo != null) {
                    giveReward(solo, createDagger());
                    giveReward(solo, createBoots());
                    solo.giveExp(2500);
                }
            } else {
                for (int i = 0; i < ranking.size(); i++) {
                    Player player = Bukkit.getPlayer(ranking.get(i).getKey());
                    if (player == null) {
                        continue;
                    }
                    if (i == 0) {
                        giveReward(player, createDagger());
                    } else if (i == 1) {
                        giveReward(player, createBoots());
                    } else {
                        player.giveExp(1800);
                    }
                }
            }
            cleanup(true);
        }

        void announceContribution(List<Map.Entry<UUID, Double>> ranking) {
            Bukkit.broadcastMessage(ChatColor.GOLD + "===== 끝의 숨결 기여도 =====");
            int rank = 1;
            for (Map.Entry<UUID, Double> entry : ranking) {
                Player player = Bukkit.getPlayer(entry.getKey());
                String name = player == null ? entry.getKey().toString().substring(0, 8) : player.getName();
                Bukkit.broadcastMessage(ChatColor.YELLOW + String.valueOf(rank++) + "위 " + name + " - " + String.format(Locale.ROOT, "%.1f", entry.getValue()) + " 피해");
            }
        }

        void giveReward(Player player, ItemStack item) {
            Map<Integer, ItemStack> leftovers = player.getInventory().addItem(item);
            leftovers.values().forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
            player.sendMessage(ChatColor.LIGHT_PURPLE + "보상 획득: " + item.getItemMeta().getDisplayName());
        }

        void cleanup(boolean keepBossDeath) {
            taskIds.forEach(Bukkit.getScheduler()::cancelTask);
            bar.removeAll();
            if (!keepBossDeath && boss.isValid()) {
                boss.remove();
            }
        }
    }

    private final class Party {
        private final UUID leader;
        private final Set<UUID> members = new HashSet<>();

        Party(UUID leader) {
            this.leader = leader;
        }

        List<Player> onlineMembers() {
            return members.stream().map(Bukkit::getPlayer).filter(player -> player != null && player.isOnline()).collect(Collectors.toList());
        }

        void broadcast(String message) {
            onlineMembers().forEach(player -> player.sendMessage(message));
        }
    }
}
