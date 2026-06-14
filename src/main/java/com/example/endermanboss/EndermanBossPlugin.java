package com.example.endermanboss;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Enderman;
import org.bukkit.entity.Endermite;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTeleportEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.Comparator;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class EndermanBossPlugin extends JavaPlugin implements Listener {
    private static final String BOSS_NAME = ChatColor.DARK_PURPLE + "엔더맨 보스";
    private static final String WEAPON_NAME = ChatColor.LIGHT_PURPLE + "공허의 단검";
    private static final double MAX_HEALTH = 500.0;
    private static final int STASIS_SECONDS = 30;
    private static final double VOID_PROJECTILE_HEALTH = 10.0;

    private org.bukkit.NamespacedKey bossKey;
    private org.bukkit.NamespacedKey weaponKey;
    private org.bukkit.NamespacedKey projectileKey;
    private UUID bossId;
    private int patternTask = -1;
    private int stasisTask = -1;
    private boolean stasisStarted;
    private boolean rewardAllowed = true;
    private boolean allowBossTeleport;
    private int currentPhase = 0;
    private BossBar bossBar;
    private final List<Integer> projectileTasks = new ArrayList<>();

    @Override
    public void onEnable() {
        bossKey = new org.bukkit.NamespacedKey(this, "enderman_boss");
        weaponKey = new org.bukkit.NamespacedKey(this, "void_dagger");
        projectileKey = new org.bukkit.NamespacedKey(this, "void_projectile");
        Bukkit.getPluginManager().registerEvents(this, this);
        Objects.requireNonNull(getCommand("bossspawn")).setExecutor(this);
        Objects.requireNonNull(getCommand("bossinfo")).setExecutor(this);
    }

    @Override
    public void onDisable() {
        cancelTasks();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("bossinfo")) {
            if (args.length == 0 || !args[0].equalsIgnoreCase("enderman")) {
                sender.sendMessage(ChatColor.YELLOW + "사용법: /bossinfo enderman");
                return true;
            }
            sendBossInfo(sender);
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage("플레이어만 사용할 수 있습니다.");
            return true;
        }
        if (args.length == 0 || !args[0].equalsIgnoreCase("enderman")) {
            player.sendMessage(ChatColor.YELLOW + "사용법: /bossspawn enderman");
            return true;
        }
        spawnBoss(player.getLocation());
        player.sendMessage(ChatColor.GREEN + "엔더맨 보스를 소환했습니다!");
        return true;
    }

    private void sendBossInfo(CommandSender sender) {
        sender.sendMessage(ChatColor.DARK_PURPLE + "[엔더맨 보스]");
        sender.sendMessage(ChatColor.GRAY + "체력 500 / 보스바 페이즈별 색상 변경");
        sender.sendMessage(ChatColor.LIGHT_PURPLE + "1페이즈(500~250): 뒤 텔레포트 공격, 도주 후 추적 공허탄 다중 발사");
        sender.sendMessage(ChatColor.RED + "2페이즈(250~100): 매우 빠른 돌격 공격");
        sender.sendMessage(ChatColor.BLUE + "3페이즈(100~0): 30초 정지, 피해 0.8배, 실패 시 보라색 폭발과 보상 없음");
        sender.sendMessage(ChatColor.YELLOW + "공허탄 HP 10, 처치 시 엔더맨 보스에게 피해를 줍니다. 철퇴 피해는 0.6배입니다.");
    }

    private void spawnBoss(Location location) {
        cancelTasks();
        Enderman boss = (Enderman) location.getWorld().spawnEntity(location, EntityType.ENDERMAN);
        boss.setCustomName(BOSS_NAME);
        boss.setCustomNameVisible(true);
        boss.setRemoveWhenFarAway(false);
        boss.getPersistentDataContainer().set(bossKey, PersistentDataType.BYTE, (byte) 1);
        AttributeInstance maxHealth = boss.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth != null) {
            maxHealth.setBaseValue(MAX_HEALTH);
        }
        boss.setHealth(MAX_HEALTH);
        boss.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, Integer.MAX_VALUE, 0, true, false));
        boss.setTarget(null);
        createBossBar(boss);
        bossId = boss.getUniqueId();
        stasisStarted = false;
        rewardAllowed = true;
        allowBossTeleport = false;
        currentPhase = 0;
        setPhase(boss, 1);
        patternTask = Bukkit.getScheduler().scheduleSyncRepeatingTask(this, () -> tickBoss(boss), 40L, 60L);
    }

    private void tickBoss(Enderman boss) {
        if (!boss.isValid() || boss.isDead()) {
            cancelTasks();
            return;
        }
        double health = boss.getHealth();
        updateBossBar(boss);
        if (health <= 100.0) {
            setPhase(boss, 3);
            startStasis(boss);
        } else if (health <= 250.0) {
            setPhase(boss, 2);
            phaseTwoCharge(boss);
        } else if (Math.random() < 0.5) {
            phaseOneBackstab(boss);
        } else {
            phaseOneRetreatAndShoot(boss);
        }
    }

    private void phaseOneBackstab(Enderman boss) {
        nearestTarget(boss, 32).ifPresent(target -> {
            Location behind = behind(target, 1.5);
            teleportBoss(boss, behind);
            boss.getWorld().spawnParticle(Particle.PORTAL, behind, 80, 0.7, 1.0, 0.7, 0.2);
            boss.getWorld().playSound(behind, Sound.ENTITY_ENDERMAN_TELEPORT, 1.2f, 0.7f);
            target.damage(12.0, boss);
        });
    }

    private void phaseOneRetreatAndShoot(Enderman boss) {
        nearestTarget(boss, 32).ifPresent(target -> {
            Vector away = boss.getLocation().toVector().subtract(target.getLocation().toVector()).normalize().multiply(8);
            Location retreat = boss.getLocation().add(away);
            retreat.setY(Math.max(retreat.getWorld().getMinHeight() + 2, retreat.getY()));
            teleportBoss(boss, retreat);
            boss.getWorld().playSound(retreat, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.2f);
            for (int i = 0; i < 4; i++) {
                spawnVoidProjectile(boss, target, i);
            }
        });
    }

    private void phaseTwoCharge(Enderman boss) {
        nearestTarget(boss, 40).ifPresent(target -> {
            Vector velocity = target.getLocation().toVector().subtract(boss.getLocation().toVector()).normalize().multiply(2.8).setY(0.25);
            boss.setVelocity(velocity);
            boss.getWorld().spawnParticle(Particle.REVERSE_PORTAL, boss.getLocation(), 120, 0.8, 0.8, 0.8, 0.1);
            boss.getWorld().playSound(boss.getLocation(), Sound.ENTITY_ENDER_DRAGON_FLAP, 1.3f, 0.6f);
        });
    }

    private void startStasis(Enderman boss) {
        if (stasisStarted) {
            return;
        }
        stasisStarted = true;
        boss.setAI(false);
        boss.setVelocity(new Vector(0, 0, 0));
        boss.getWorld().playSound(boss.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 2.0f, 0.5f);
        Bukkit.broadcastMessage(ChatColor.DARK_PURPLE + "엔더맨 보스가 힘을 모읍니다! 30초 안에 처치하세요!");
        stasisTask = new BukkitRunnable() {
            int ticks = STASIS_SECONDS * 20;

            @Override
            public void run() {
                if (!boss.isValid() || boss.isDead()) {
                    cancel();
                    return;
                }
                boss.setVelocity(new Vector(0, 0, 0));
                boss.getWorld().spawnParticle(Particle.DRAGON_BREATH, boss.getLocation().add(0, 1, 0), 40, 1.5, 1.5, 1.5, 0.03);
                ticks -= 20;
                if (ticks <= 0) {
                    rewardAllowed = false;
                    purpleExplosion(boss.getLocation());
                    boss.remove();
                    cancelTasks();
                    cancel();
                }
            }
        }.runTaskTimer(this, 0L, 20L).getTaskId();
    }

    private void purpleExplosion(Location location) {
        World world = location.getWorld();
        world.spawnParticle(Particle.DRAGON_BREATH, location, 900, 6, 4, 6, 0.25);
        world.spawnParticle(Particle.EXPLOSION, location, 80, 5, 3, 5, 0.1);
        world.playSound(location, Sound.ENTITY_GENERIC_EXPLODE, 4.0f, 0.45f);
        world.createExplosion(location, 8.0f, false, false);
        for (Entity entity : world.getNearbyEntities(location, 10, 10, 10)) {
            if (entity instanceof LivingEntity living) {
                living.damage(30.0);
            }
        }
    }

    private void createBossBar(Enderman boss) {
        if (bossBar != null) {
            bossBar.removeAll();
        }
        bossBar = Bukkit.createBossBar(BOSS_NAME, BarColor.PURPLE, BarStyle.SEGMENTED_10);
        bossBar.setVisible(true);
        for (Player player : Bukkit.getOnlinePlayers()) {
            bossBar.addPlayer(player);
        }
        updateBossBar(boss);
    }

    private void updateBossBar(Enderman boss) {
        if (bossBar == null || !boss.isValid()) {
            return;
        }
        bossBar.setProgress(Math.max(0.0, Math.min(1.0, boss.getHealth() / MAX_HEALTH)));
        bossBar.setTitle(BOSS_NAME + ChatColor.WHITE + " " + (int) Math.ceil(boss.getHealth()) + "/" + (int) MAX_HEALTH + " HP");
    }

    private void setPhase(Enderman boss, int phase) {
        if (currentPhase == phase) {
            return;
        }
        currentPhase = phase;
        if (bossBar != null) {
            bossBar.setColor(phase == 1 ? BarColor.PURPLE : phase == 2 ? BarColor.RED : BarColor.BLUE);
        }
        Location location = boss.getLocation().add(0, 1, 0);
        boss.getWorld().spawnParticle(phase == 1 ? Particle.PORTAL : phase == 2 ? Particle.FLAME : Particle.DRAGON_BREATH, location, 180, 1.8, 1.8, 1.8, 0.15);
        boss.getWorld().playSound(location, phase == 1 ? Sound.ENTITY_ENDERMAN_SCREAM : phase == 2 ? Sound.ENTITY_WITHER_SPAWN : Sound.ENTITY_ENDER_DRAGON_GROWL, 1.5f, 0.7f);
    }

    private void teleportBoss(Enderman boss, Location location) {
        allowBossTeleport = true;
        boss.teleport(location);
        allowBossTeleport = false;
    }

    private void spawnVoidProjectile(Enderman boss, LivingEntity target, int offset) {
        Location spawn = boss.getEyeLocation().add(boss.getLocation().getDirection().normalize().multiply(0.8));
        Endermite projectile = (Endermite) boss.getWorld().spawnEntity(spawn, EntityType.ENDERMITE);
        projectile.setCustomName(ChatColor.DARK_PURPLE + "추적 공허탄");
        projectile.setCustomNameVisible(true);
        projectile.setRemoveWhenFarAway(false);
        projectile.setAI(false);
        projectile.getPersistentDataContainer().set(projectileKey, PersistentDataType.BYTE, (byte) 1);
        AttributeInstance maxHealth = projectile.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth != null) {
            maxHealth.setBaseValue(VOID_PROJECTILE_HEALTH);
        }
        projectile.setHealth(VOID_PROJECTILE_HEALTH);
        projectileTasks.add(Bukkit.getScheduler().scheduleSyncRepeatingTask(this, () -> tickVoidProjectile(projectile, boss, target), offset * 5L, 2L));
    }

    private void tickVoidProjectile(Endermite projectile, Enderman boss, LivingEntity target) {
        if (!projectile.isValid() || projectile.isDead() || !boss.isValid() || boss.isDead()) {
            projectile.remove();
            return;
        }
        LivingEntity currentTarget = target.isValid() && !target.isDead() ? target : nearestTarget(boss, 40).orElse(null);
        if (currentTarget == null) {
            projectile.remove();
            return;
        }
        Vector direction = currentTarget.getEyeLocation().toVector().subtract(projectile.getLocation().toVector()).normalize();
        projectile.setVelocity(direction.multiply(0.75));
        projectile.getWorld().spawnParticle(Particle.REVERSE_PORTAL, projectile.getLocation(), 8, 0.15, 0.15, 0.15, 0.03);
        if (projectile.getLocation().distanceSquared(currentTarget.getLocation()) < 1.6) {
            currentTarget.damage(8.0, boss);
            projectile.getWorld().spawnParticle(Particle.DRAGON_BREATH, projectile.getLocation(), 25, 0.4, 0.4, 0.4, 0.06);
            projectile.remove();
        }
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (!isBoss(event.getEntity())) {
            return;
        }
        if (stasisStarted) {
            event.setDamage(event.getDamage() * 0.8);
        }
        if (event instanceof EntityDamageByEntityEvent byEntity
                && byEntity.getDamager() instanceof Player player
                && player.getInventory().getItemInMainHand().getType() == Material.MACE) {
            event.setDamage(event.getDamage() * 0.6);
        }
    }

    @EventHandler
    public void onBossTouch(EntityDamageByEntityEvent event) {
        if (isBoss(event.getDamager()) && event.getEntity() instanceof LivingEntity) {
            Enderman boss = (Enderman) event.getDamager();
            if (boss.getHealth() <= 250.0 && boss.getHealth() > 100.0) {
                event.setDamage(18.0);
                event.getEntity().setVelocity(event.getEntity().getLocation().toVector().subtract(boss.getLocation().toVector()).normalize().multiply(1.4).setY(0.5));
            }
        }
    }

    @EventHandler
    public void onDeath(EntityDeathEvent event) {
        if (isVoidProjectile(event.getEntity())) {
            event.getDrops().clear();
            event.setDroppedExp(0);
            getBoss().ifPresent(boss -> boss.damage(14.0));
            return;
        }
        if (!isBoss(event.getEntity())) {
            return;
        }
        cancelTasks();
        event.getDrops().clear();
        event.setDroppedExp(250);
        if (rewardAllowed) {
            event.getDrops().add(createWeapon());
            Bukkit.broadcastMessage(ChatColor.LIGHT_PURPLE + "엔더맨 보스 처치 성공! 공허의 단검이 드랍되었습니다.");
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (bossBar != null) {
            bossBar.addPlayer(event.getPlayer());
        }
    }

    @EventHandler
    public void onUse(PlayerInteractEvent event) {
        ItemStack item = event.getItem();
        if (item == null || !item.hasItemMeta() || !item.getItemMeta().getPersistentDataContainer().has(weaponKey)) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        nearestEntityForWeapon(player, 30).ifPresentOrElse(target -> {
            Location behind = behind(target, 1.2);
            player.teleport(behind);
            player.getWorld().spawnParticle(Particle.PORTAL, behind, 90, 0.6, 1.0, 0.6, 0.15);
            player.getWorld().playSound(behind, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
        }, () -> player.sendMessage(ChatColor.GRAY + "30블록 안에 대상이 없습니다."));
    }

    private ItemStack createWeapon() {
        ItemStack item = new ItemStack(Material.NETHERITE_SWORD);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(WEAPON_NAME);
        meta.setLore(List.of(ChatColor.GRAY + "우클릭: 가장 가까운 대상 뒤로 순간이동", ChatColor.DARK_GRAY + "발사체는 대상에서 제외됩니다."));
        meta.addEnchant(Enchantment.SHARPNESS, 5, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        meta.getPersistentDataContainer().set(weaponKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    private Optional<LivingEntity> nearestTarget(Enderman boss, double radius) {
        return boss.getNearbyEntities(radius, radius, radius).stream()
                .filter(entity -> entity instanceof LivingEntity && !entity.equals(boss))
                .map(entity -> (LivingEntity) entity)
                .filter(LivingEntity::isValid)
                .min(Comparator.comparingDouble(entity -> entity.getLocation().distanceSquared(boss.getLocation())));
    }

    private Optional<LivingEntity> nearestEntityForWeapon(Player player, double radius) {
        return player.getNearbyEntities(radius, radius, radius).stream()
                .filter(entity -> entity instanceof LivingEntity && !(entity instanceof Player && entity.equals(player)))
                .map(entity -> (LivingEntity) entity)
                .min(Comparator.comparingDouble(entity -> entity.getLocation().distanceSquared(player.getLocation())));
    }

    private Location behind(LivingEntity target, double distance) {
        Vector direction = target.getLocation().getDirection().setY(0).normalize();
        if (direction.lengthSquared() == 0) {
            direction = new Vector(0, 0, 1);
        }
        Location behind = target.getLocation().subtract(direction.multiply(distance));
        behind.setYaw(target.getLocation().getYaw());
        behind.setPitch(target.getLocation().getPitch());
        return behind;
    }

    @EventHandler
    public void onTeleport(EntityTeleportEvent event) {
        if (isBoss(event.getEntity()) && !allowBossTeleport) {
            event.setCancelled(true);
        }
    }

    private Optional<Enderman> getBoss() {
        if (bossId == null) {
            return Optional.empty();
        }
        Entity entity = Bukkit.getEntity(bossId);
        if (entity instanceof Enderman enderman && enderman.isValid() && !enderman.isDead()) {
            return Optional.of(enderman);
        }
        return Optional.empty();
    }

    private boolean isVoidProjectile(Entity entity) {
        return entity instanceof Endermite && entity.getPersistentDataContainer().has(projectileKey, PersistentDataType.BYTE);
    }

    private boolean isBoss(Entity entity) {
        if (!(entity instanceof Enderman)) {
            return false;
        }
        PersistentDataContainer data = entity.getPersistentDataContainer();
        return data.has(bossKey, PersistentDataType.BYTE) || entity.getUniqueId().equals(bossId);
    }

    private void cancelTasks() {
        if (bossBar != null) {
            bossBar.removeAll();
            bossBar = null;
        }
        for (int taskId : projectileTasks) {
            Bukkit.getScheduler().cancelTask(taskId);
        }
        projectileTasks.clear();
        if (patternTask != -1) {
            Bukkit.getScheduler().cancelTask(patternTask);
            patternTask = -1;
        }
        if (stasisTask != -1) {
            Bukkit.getScheduler().cancelTask(stasisTask);
            stasisTask = -1;
        }
    }
}
