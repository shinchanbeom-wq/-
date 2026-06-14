package com.example.endermanboss;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Enderman;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
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
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class EndermanBossPlugin extends JavaPlugin implements Listener {
    private static final String BOSS_NAME = ChatColor.DARK_PURPLE + "엔더맨 보스";
    private static final String WEAPON_NAME = ChatColor.LIGHT_PURPLE + "공허의 단검";
    private static final double MAX_HEALTH = 500.0;
    private static final int STASIS_SECONDS = 30;

    private org.bukkit.NamespacedKey bossKey;
    private org.bukkit.NamespacedKey weaponKey;
    private UUID bossId;
    private int patternTask = -1;
    private int stasisTask = -1;
    private boolean stasisStarted;
    private boolean rewardAllowed = true;

    @Override
    public void onEnable() {
        bossKey = new org.bukkit.NamespacedKey(this, "enderman_boss");
        weaponKey = new org.bukkit.NamespacedKey(this, "void_dagger");
        Bukkit.getPluginManager().registerEvents(this, this);
        Objects.requireNonNull(getCommand("endermanboss")).setExecutor(this);
    }

    @Override
    public void onDisable() {
        cancelTasks();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("플레이어만 사용할 수 있습니다.");
            return true;
        }
        if (args.length == 0 || !args[0].equalsIgnoreCase("spawn")) {
            player.sendMessage(ChatColor.YELLOW + "사용법: /endermanboss spawn");
            return true;
        }
        spawnBoss(player.getLocation());
        player.sendMessage(ChatColor.GREEN + "엔더맨 보스를 소환했습니다!");
        return true;
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
        bossId = boss.getUniqueId();
        stasisStarted = false;
        rewardAllowed = true;
        patternTask = Bukkit.getScheduler().scheduleSyncRepeatingTask(this, () -> tickBoss(boss), 40L, 60L);
    }

    private void tickBoss(Enderman boss) {
        if (!boss.isValid() || boss.isDead()) {
            cancelTasks();
            return;
        }
        double health = boss.getHealth();
        if (health <= 100.0) {
            startStasis(boss);
        } else if (health <= 250.0) {
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
            boss.teleport(behind);
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
            boss.teleport(retreat);
            boss.getWorld().playSound(retreat, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.2f);
            Arrow arrow = boss.launchProjectile(Arrow.class);
            arrow.setVelocity(target.getEyeLocation().toVector().subtract(boss.getEyeLocation().toVector()).normalize().multiply(2.1));
            arrow.setDamage(9.0);
            arrow.setPickupStatus(Arrow.PickupStatus.DISALLOWED);
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

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (isBoss(event.getEntity()) && stasisStarted) {
            event.setDamage(event.getDamage() * 0.8);
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

    private boolean isBoss(Entity entity) {
        if (!(entity instanceof Enderman)) {
            return false;
        }
        PersistentDataContainer data = entity.getPersistentDataContainer();
        return data.has(bossKey, PersistentDataType.BYTE) || entity.getUniqueId().equals(bossId);
    }

    private void cancelTasks() {
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
