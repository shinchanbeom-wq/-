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
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTeleportEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
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

    private NamespacedKey bossKey;
    private NamespacedKey daggerKey;
    private NamespacedKey bootsKey;
    private NamespacedKey voidProjectileKey;
    private BossFight fight;
    private final Map<UUID, Party> partiesByLeader = new HashMap<>();
    private final Map<UUID, UUID> memberToLeader = new HashMap<>();
    private final Map<UUID, UUID> pendingInvites = new HashMap<>();
    private final Map<UUID, Long> daggerCooldowns = new HashMap<>();
    private final Map<UUID, Long> bootsCooldowns = new HashMap<>();
    private boolean pluginTeleport;

    @Override
    public void onEnable() {
        bossKey = new NamespacedKey(this, "enderman_boss");
        daggerKey = new NamespacedKey(this, "void_dagger");
        bootsKey = new NamespacedKey(this, "void_boots");
        voidProjectileKey = new NamespacedKey(this, "void_projectile");
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
        if (args.length == 0 || !args[0].equalsIgnoreCase("enderman")) {
            player.sendMessage(ChatColor.RED + "사용법: /bossspawn enderman");
            return true;
        }
        if (fight != null && fight.isActive()) {
            player.sendMessage(ChatColor.RED + "이미 진행 중인 끝의 숨결 보스전이 있습니다.");
            return true;
        }
        Party party = getParty(player.getUniqueId());
        List<Player> challengers = party == null ? List.of(player) : party.onlineMembers();
        startFight(player.getLocation(), challengers);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (command.getName().equalsIgnoreCase("party") && args.length == 1) {
            return List.of("create", "invite", "join", "leave", "disband", "info").stream()
                .filter(value -> value.startsWith(args[0].toLowerCase(Locale.ROOT))).collect(Collectors.toList());
        }
        if ((command.getName().equalsIgnoreCase("bossspawn") || command.getName().equalsIgnoreCase("bossinfo")) && args.length == 1) {
            return List.of("enderman");
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
        if (fight == null || !fight.isBoss(event.getEntity())) {
            return;
        }
        Player player = damagingPlayer(event.getDamager());
        if (player != null) {
            fight.addContribution(player.getUniqueId(), event.getFinalDamage());
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
        }
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        ItemStack boots = player.getInventory().getBoots();
        if (hasKey(boots, bootsKey) && player.isOnGround()) {
            player.setAllowFlight(true);
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
            return boss.getNearbyEntities(range, 24, range).stream()
                .filter(entity -> entity instanceof Player player && challengers.contains(player.getUniqueId()))
                .map(entity -> (LivingEntity) entity)
                .min(Comparator.comparingDouble(entity -> entity.getLocation().distanceSquared(boss.getLocation())))
                .orElse(null);
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

        void spawnHostilePool(Location center, long durationTicks) {
            center.getWorld().playSound(center, Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 0.8f, 0.7f);
            new BukkitRunnable() {
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
            }.runTaskTimer(EndermanBossPlugin.this, 0L, 10L);
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
