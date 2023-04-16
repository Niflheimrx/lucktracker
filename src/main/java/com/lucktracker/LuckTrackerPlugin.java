/*
 * Copyright (c) 2018, SomeoneWithAnInternetConnection
 * Copyright (c) 2018, oplosthee <https://github.com/oplosthee>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.lucktracker;

import com.google.inject.Provides;
import com.sun.source.doctree.HiddenTree;
import jdk.internal.org.jline.utils.Log;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.events.*;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.NPCManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;
import org.apache.commons.lang3.ArrayUtils;

import javax.inject.Inject;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@PluginDescriptor(
        name = "Luck Tracker",
        description = "Stats on how lucky you are in combat",
        tags = {"combat", "rng"},
        enabledByDefault = false
)

@Slf4j
public class LuckTrackerPlugin extends Plugin {

    /*
    Identify when a player attacks by detecting whenever an attack animation is performed.
    Get information about the player (offensive stats, gear, etc.), and information about the target (defense level, defense stats).
    Calculate the player's attack roll and the opponent's defense roll. Generate a Hit Distribution based on this.
        Account for as many special cases as possible (set bonuses, special attacks, unique weapons, weapon/npc interactions, etc.).
    Keep a "running-total" hit distribution -- that is, a total damage PDF -- and show where the player's total damage lands on it.
     */

    private LuckTrackerPanel panel;
    private NavigationButton navButton;

    private TickCounterUtil tickCounterUtil; // Used for identifying attack animations
    private Actor lastInteracting; // Keep track of targetted NPC; will update every game tick
    private Monsters monsterTable;
    private LuckTrackerUtil UTIL;

    private ItemContainer wornItemsContainer;
    private ArrayList<Pattern> slayerTargetNames = new ArrayList<>();
    private int specialAttackEnergy;
    private boolean usedSpecialAttack;

    private int totalDamage;
    private HitDist runningHitDist;

    private boolean isWearingMeleeVoid;
    private boolean isWearingRangeVoid;
    private boolean isWearingMagicVoid;
    private boolean isWearingMeleeEliteVoid;
    private boolean isWearingRangeEliteVoid;
    private boolean isWearingMagicEliteVoid;

    public final Set<Integer> crystalBodies = new HashSet<>(Arrays.asList(ItemID.CRYSTAL_BODY, ItemID.CRYSTAL_BODY_27721, ItemID.CRYSTAL_BODY_27709, ItemID.CRYSTAL_BODY_27733, ItemID.CRYSTAL_BODY_27757, ItemID.CRYSTAL_BODY_27697, ItemID.CRYSTAL_BODY_27745, ItemID.CRYSTAL_BODY_27769));
    public final Set<Integer> crystalLegs = new HashSet<>(Arrays.asList(ItemID.CRYSTAL_LEGS, ItemID.CRYSTAL_LEGS_27725, ItemID.CRYSTAL_LEGS_27713, ItemID.CRYSTAL_LEGS_27701, ItemID.CRYSTAL_LEGS_27761, ItemID.CRYSTAL_LEGS_27737, ItemID.CRYSTAL_LEGS_27749, ItemID.CRYSTAL_LEGS_27773));
    public final Set<Integer> crystalHelms = new HashSet<>(Arrays.asList(ItemID.CRYSTAL_HELM, ItemID.CRYSTAL_HELM_27729, ItemID.CRYSTAL_HELM_27705, ItemID.CRYSTAL_HELM_27717, ItemID.CRYSTAL_HELM_27729, ItemID.CRYSTAL_HELM_27741, ItemID.CRYSTAL_HELM_27753, ItemID.CRYSTAL_HELM_27777));
    public final Set<Integer> bofas = new HashSet<>(Arrays.asList(ItemID.BOW_OF_FAERDHINEN_INACTIVE, ItemID.BOW_OF_FAERDHINEN, ItemID.BOW_OF_FAERDHINEN_C, ItemID.BOW_OF_FAERDHINEN_C_25869, ItemID.BOW_OF_FAERDHINEN_C_25884, ItemID.BOW_OF_FAERDHINEN_C_25886, ItemID.BOW_OF_FAERDHINEN_C_25888, ItemID.BOW_OF_FAERDHINEN_C_25890, ItemID.BOW_OF_FAERDHINEN_C_25892, ItemID.BOW_OF_FAERDHINEN_C_25894, ItemID.BOW_OF_FAERDHINEN_C_25896));
    public final Set<Integer> crystalBows = new HashSet<>(Arrays.asList(ItemID.NEW_CRYSTAL_BOW, ItemID.NEW_CRYSTAL_BOW_4213, ItemID.CRYSTAL_BOW_FULL, ItemID.CRYSTAL_BOW_910, ItemID.CRYSTAL_BOW_810, ItemID.CRYSTAL_BOW_710, ItemID.CRYSTAL_BOW_610, ItemID.CRYSTAL_BOW_510, ItemID.CRYSTAL_BOW_410, ItemID.CRYSTAL_BOW_310, ItemID.CRYSTAL_BOW_210, ItemID.CRYSTAL_BOW_110, ItemID.NEW_CRYSTAL_BOW_16888, ItemID.CRYSTAL_BOW));
    public final Set<Integer> imbuedCrystalBows = new HashSet<>(Arrays.asList(ItemID.NEW_CRYSTAL_BOW_I, ItemID.CRYSTAL_BOW_FULL_I, ItemID.CRYSTAL_BOW_910_I, ItemID.CRYSTAL_BOW_810_I, ItemID.CRYSTAL_BOW_710_I, ItemID.CRYSTAL_BOW_610_I, ItemID.CRYSTAL_BOW_510_I, ItemID.CRYSTAL_BOW_410_I, ItemID.CRYSTAL_BOW_310_I, ItemID.CRYSTAL_BOW_210_I, ItemID.CRYSTAL_BOW_110_I, ItemID.NEW_CRYSTAL_BOW_I_16889));

    @Inject
    private Client client;
    @Inject
    private ClientThread clientThread;
    @Inject
    private ClientToolbar clientToolbar;
    @Inject
    private LuckTrackerConfig config;
    @Inject
    private ItemManager itemManager;
    @Inject
    private ChatMessageManager chatMessageManager;
    @Inject
    private NPCManager npcManager;

    @Provides
    LuckTrackerConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(LuckTrackerConfig.class);
    }

    @Override
    protected void startUp() throws Exception {
        tickCounterUtil = new TickCounterUtil(); // Utility ripped from PVMTickCounter plugin: dictionary of (animation_ID, tick_duration) pairs. Used for ID'ing player attack animations.
        tickCounterUtil.init();
        monsterTable = new Monsters();
        UTIL = new LuckTrackerUtil(client, itemManager, chatMessageManager, npcManager);

        this.totalDamage = 0;

        // Load the side panel
        final BufferedImage navIcon = ImageUtil.loadImageResource(this.getClass(), "/info_icon.png"); // Load the icon for the nav button (in LuckTrackerPlugin's resources subfolder)
        panel = injector.getInstance(LuckTrackerPanel.class); // Create an instance of the LuckTrackerPanel
        navButton = NavigationButton.builder() // Create a nav button that we can add to the toolbar...
                .tooltip("Luck Tracker")
                .icon(navIcon)
                .priority(10)
                .panel(panel)
                .build();
        clientToolbar.addNavigation(navButton); // Add the nav button to the toolbar

        clientThread.invokeLater(() -> {
            // Get worn items on startup // TODO Logging in with nothing equipped will set this to null. (Unequipping items while already logged in properly sets an empty ItemContainer, but not logging in w/ nothinng equipped!)
            final ItemContainer wornItemsContainer = client.getItemContainer(InventoryID.EQUIPMENT);

            // Get special attack energy, initialize the spec boolean
            this.usedSpecialAttack = false;
            this.specialAttackEnergy = client.getVarpValue(VarPlayer.SPECIAL_ATTACK_PERCENT);

            // Master hit distribution
            this.runningHitDist = new HitDist();

            // Figure out what our slayer task is, and get a handle on all applicable target NPC names
            updateSlayerTargetNames();
        });
    }

    @Override
    protected void shutDown() {
        clientToolbar.removeNavigation(navButton);
        panel = null;
    }

    protected void resetStats() {
        this.runningHitDist = new HitDist();
        this.totalDamage = 0;
        panel.updatePanelStats(this.totalDamage, this.runningHitDist.getAvgDmg(), this.runningHitDist.getCdfAtDmg(this.totalDamage), this.runningHitDist.getDmgAtCdf(0.1D), this.runningHitDist.getDmgAtCdf(0.9D));
    }

    @Subscribe
    public void onGameTick(GameTick gameTick) {

        Player player = client.getLocalPlayer();
        if (player == null) return;

        // Update currently interacting NPC
        Actor interactingActor = player.getInteracting();
        if (interactingActor instanceof NPC) this.lastInteracting = interactingActor;

        // Reset the special attack bool
        clientThread.invokeLater(() -> {
            this.usedSpecialAttack = false;
        });
    }

    @Subscribe
    public void onHitsplatApplied(HitsplatApplied hitsplatApplied) {
        Player player = client.getLocalPlayer();
        Actor actor = hitsplatApplied.getActor();
        if (!(actor instanceof NPC)) // only look for hitsplats applied to NPCs
        {
            return;
        }
        Hitsplat hitsplat = hitsplatApplied.getHitsplat(); // get a handle on the hitsplat
        if (hitsplat.isMine()) { // if it's our own hitsplat...
            this.totalDamage += hitsplat.getAmount();
            panel.updatePanelStats(this.totalDamage, this.runningHitDist.getAvgDmg(), this.runningHitDist.getCdfAtDmg(this.totalDamage), this.runningHitDist.getDmgAtCdf(0.1D), this.runningHitDist.getDmgAtCdf(0.9D));
        }
    }

    @Subscribe
    public void onVarbitChanged(VarbitChanged varbitChanged) {
        if (varbitChanged.getVarpId() == VarPlayer.SPECIAL_ATTACK_PERCENT)
            this.specialAttackEnergy = varbitChanged.getValue();
        if (varbitChanged.getValue() < this.specialAttackEnergy) this.usedSpecialAttack = true;
        if (varbitChanged.getVarpId() == VarPlayer.SLAYER_TASK_CREATURE || varbitChanged.getVarpId() == VarPlayer.SLAYER_TASK_LOCATION) {
            updateSlayerTargetNames();
        }
    }

    @Subscribe
    public void onItemContainerChanged(ItemContainerChanged event) {
        if (event.getItemContainer() != client.getItemContainer(InventoryID.EQUIPMENT)) {
            return;
        }

        this.wornItemsContainer = event.getItemContainer();

        clientThread.invokeLater(this::updateVoidStatus);
        updateSlayerTargetNames();
    }

    @Subscribe
    private void onAnimationChanged(AnimationChanged e) { // Main hook for identifying when we perform an attack: our animation changes. Will use PVMTickCounter's utility to determine
        // TODO most weapon anims are 2 ticks; so 2t weapons won't get tracked properly using just onAnimationChanged
        //		Ian idea: instead of tracking onAnimationChanged, keep track of player's animation and re-set it every 2 ticks
        //		Alternative: PVMTickCounter uses an "IsBPing" flag
        // TODO hook up salamander blaze and flare; no animation on player, but it spawns G = 952.
        //  Also potentially a lot of other attack animations.
        // 	See tickCounterUtil -> aniTM.


        // Do everything AFTER this client thread: need to allow targeted NPC to update first.
        clientThread.invokeLater(() -> {

            if (!(e.getActor() instanceof Player)) return;
            Player p = (Player) e.getActor();
            if (p != client.getLocalPlayer()) return;
            if (!tickCounterUtil.isAttack(p.getAnimation())) return; // If the animation isn't an attack, stop here
            int animationId = p.getAnimation();

            // ************************************************** //
            // Player Attack setup
            // TODO Casting a spell will take whatever stance is currently active... Which is only accurate if autocasting. For casting spells specifically, will probably need to short circuit based on animation?

            int attackStyleId = client.getVarpValue(VarPlayer.ATTACK_STYLE);
            int weaponTypeId = client.getVarbitValue(Varbits.EQUIPPED_WEAPON_TYPE);

            WeaponStance weaponStance = WeaponType.getWeaponStance(weaponTypeId, attackStyleId); // Determine weapon stance (Controlled, Aggressive, Rapid, etc.)
            AttackStyle attackStyle = WeaponType.getAttackStyle(weaponTypeId, attackStyleId); // Determine if we're using slash, crush, stab, range, or magic based on the weapon type and current stance

            // NPC Defense setup

            int npcId = ((NPC) this.lastInteracting).getId();
            MonsterData npcData = this.monsterTable.getMonsterData(npcId);
            if (npcData == null) {
                UTIL.sendChatMessage("UNKNOWN MONSTER");
                return;
            }

            HitDist hitDist = processAttack(attackStyle, weaponStance, npcData);
            UTIL.sendChatMessage("Average: " + hitDist.getAvgDmg() + " || Max: " + hitDist.getMax() + " || >0 Prob: " + hitDist.getNonZeroHitChance());

            this.runningHitDist.convolve(hitDist);
        });
    }

    private void updateVoidStatus() { // gross
        this.isWearingMeleeVoid = UTIL.isWearingVoid(this.wornItemsContainer, Skill.ATTACK, false);
        this.isWearingRangeVoid = UTIL.isWearingVoid(this.wornItemsContainer, Skill.RANGED, false);
        this.isWearingMagicVoid = UTIL.isWearingVoid(this.wornItemsContainer, Skill.MAGIC, false);

        this.isWearingMeleeEliteVoid = UTIL.isWearingVoid(this.wornItemsContainer, Skill.ATTACK, true);
        this.isWearingRangeEliteVoid = UTIL.isWearingVoid(this.wornItemsContainer, Skill.RANGED, true);
        this.isWearingMagicEliteVoid = UTIL.isWearingVoid(this.wornItemsContainer, Skill.MAGIC, true);
    }

    public void updateSlayerTargetNames() {
        this.slayerTargetNames.clear();
        int amount = client.getVarpValue(VarPlayer.SLAYER_TASK_SIZE);
        if (amount > 0) {

            String taskName;
            int taskId = client.getVarpValue(VarPlayer.SLAYER_TASK_CREATURE);

            if (taskId == 98 /* Bosses, from [proc,helper_slayer_current_assignment] */) {
                taskName = client.getEnum(EnumID.SLAYER_TASK_BOSS)
                        .getStringValue(client.getVarbitValue(Varbits.SLAYER_TASK_BOSS));
            } else {
                taskName = client.getEnum(EnumID.SLAYER_TASK_CREATURE)
                        .getStringValue(taskId);
            }

            Task task = Task.getTask(taskName);
            if (task == null) return;
            Arrays.stream(task.getTargetNames())
                    .map(LuckTrackerUtil::targetNamePattern)
                    .forEach(this.slayerTargetNames::add);
            this.slayerTargetNames.add(LuckTrackerUtil.targetNamePattern(taskName.replaceAll("s$", "")));
        }
    }

    private boolean isSlayerTarget(NPC npc) {
        if (this.slayerTargetNames.isEmpty()) {
            return false;
        }

        for (NPC nearbyNpc : client.getNpcs()) {
            NPCComposition composition = nearbyNpc.getTransformedComposition();
            String name;
            Matcher targetMatcher;
            if (composition == null) {
                continue;
            }
            name = composition.getName().replace('\u00A0', ' ').toLowerCase();

            for (Pattern target : this.slayerTargetNames) {
                targetMatcher = target.matcher(name);
                if (targetMatcher.find()
                        && (ArrayUtils.contains(composition.getActions(), "Attack")
                        || ArrayUtils.contains(composition.getActions(), "Pick"))) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isSalveTarget(NPC npc) {
        MonsterData monsterData = monsterTable.getMonsterData(npc.getId());
        List<String> monsterAttributes = Arrays.asList(monsterData.getAttributes());
        if (monsterAttributes.contains("undead")) return true;
        return false;
    }

    // TODO decide if this should be a pure function or not.
    //  Right now there's a nasty mix of argument passing + instance variables. Pick one and stick with it!
    HitDist processAttack(AttackStyle attackStyle, WeaponStance weaponStance, MonsterData npcData) {
        int effAttLvl, effStrLvl, attRoll, maxHit = -1, defRoll, npcCurrentHp;
        double hitChance;

        NPC targetedNpc = (NPC) lastInteracting;
        List<Integer> wornItemIds = Arrays.stream(wornItemsContainer.getItems()).map(Item::getId).collect(Collectors.toList());
        EquipmentStat equipmentStatOffense = attackStyle.getEquipmentStat(); // Using the attackStyle, figure out which worn-equipment stat we should use
        EquipmentStat equipmentStatDefense = LuckTrackerUtil.getDefensiveStat(equipmentStatOffense); // Using the attackStyle, figure out which worn-equipment stat we should use

        boolean slayerTarget = isSlayerTarget(targetedNpc);
        boolean salveTarget = isSalveTarget(targetedNpc);

        // region Base Attack Roll and Max Hit

        switch (attackStyle) {
            case MAGIC: {
                int visibleMagicLvl = client.getBoostedSkillLevel(Skill.MAGIC);
                effAttLvl = LuckTrackerUtil.calcEffectiveMagicLevel(visibleMagicLvl, UTIL.getActivePrayerModifiers(PrayerAttribute.PRAY_MATT), weaponStance.getInvisBonus(Skill.MAGIC), isWearingMagicVoid, isWearingMagicEliteVoid); // TODO Handle Tumeken's Shadow in here (Attack Roll)
                if (weaponStance == WeaponStance.POWERED_STAFF_ACCURATE || weaponStance == WeaponStance.POWERED_STAFF_LONGRANGE) {
                    if (wornItemIds.contains(ItemID.TUMEKENS_SHADOW)) {
                         maxHit = visibleMagicLvl / 3 + 1;
                    }
                    else if (wornItemIds.contains(ItemID.SANGUINESTI_STAFF) || wornItemIds.contains(ItemID.HOLY_SANGUINESTI_STAFF)) {
                        maxHit = visibleMagicLvl / 3 - 1;
                    }
                    else if (wornItemIds.contains(ItemID.TRIDENT_OF_THE_SWAMP) || wornItemIds.contains(ItemID.TRIDENT_OF_THE_SWAMP_E)) {
                        maxHit = visibleMagicLvl / 3 - 2;
                    }
                    else if (wornItemIds.contains(ItemID.TRIDENT_OF_THE_SEAS) || wornItemIds.contains(ItemID.TRIDENT_OF_THE_SEAS_E) || wornItemIds.contains(ItemID.TRIDENT_OF_THE_SEAS_FULL)) {
                        maxHit = visibleMagicLvl / 3 - 5;
                    }
                } else {
                    maxHit = -1; // TODO regular spells
                }
                maxHit = (int) (maxHit * (1.0D + UTIL.getEquipmentStyleBonus(wornItemsContainer, EquipmentStat.MDMG) / 100.0D));
                break;
            }
            case RANGE: {
                effAttLvl = LuckTrackerUtil.calcEffectiveRangeAttack(client.getBoostedSkillLevel(Skill.RANGED), UTIL.getActivePrayerModifiers(PrayerAttribute.PRAY_RATT), weaponStance.getInvisBonus(Skill.RANGED), isWearingRangeVoid, isWearingRangeEliteVoid);
                effStrLvl = LuckTrackerUtil.calcEffectiveRangeStrength(client.getBoostedSkillLevel(Skill.RANGED), UTIL.getActivePrayerModifiers(PrayerAttribute.PRAY_RSTR), weaponStance.getInvisBonus(Skill.RANGED), isWearingRangeVoid, isWearingRangeEliteVoid);
                maxHit = LuckTrackerUtil.calcRangeBasicMaxHit(effStrLvl, UTIL.getEquipmentStyleBonus(wornItemsContainer, equipmentStatOffense));
                break;
            }
            default: {
                effAttLvl = LuckTrackerUtil.calcEffectiveMeleeLevel(client.getBoostedSkillLevel(Skill.ATTACK), UTIL.getActivePrayerModifiers(PrayerAttribute.PRAY_ATT), weaponStance.getInvisBonus(Skill.ATTACK), isWearingMeleeVoid || isWearingMeleeEliteVoid);
                effStrLvl = LuckTrackerUtil.calcEffectiveMeleeLevel(client.getBoostedSkillLevel(Skill.STRENGTH), UTIL.getActivePrayerModifiers(PrayerAttribute.PRAY_STR), weaponStance.getInvisBonus(Skill.STRENGTH), isWearingMeleeVoid || isWearingMeleeEliteVoid);
                maxHit = LuckTrackerUtil.calcBasicMaxHit(effStrLvl, UTIL.getEquipmentStyleBonus(wornItemsContainer, EquipmentStat.STR));
                break;
            }
        }
        attRoll = (effAttLvl * (UTIL.getEquipmentStyleBonus(wornItemsContainer, equipmentStatOffense) + 64));

        log.info(String.format("Base attack roll / max hit: %d / %d", attRoll, maxHit));

        // endregion

        // region NPC HP and basic Defense Roll

        npcCurrentHp = UTIL.getNpcCurrentHp(targetedNpc);
        defRoll = npcData.calcDefenseRoll(equipmentStatDefense);

        // endregion

        // region Attack roll & Max hit modifiers

        // TODO Tumeken's Shadow

        // TODO Mystic Smoke Staff gets +10% acc and dmg on regular spellbook. Right here

        // Crystal armor, if wearing a crystal bow or bofa
        if (attackStyle == AttackStyle.RANGE && (wornItemIds.stream().anyMatch(bofas::contains) || wornItemIds.stream().anyMatch(crystalBows::contains) || wornItemIds.stream().anyMatch(imbuedCrystalBows::contains))) {
            double totalAttBoost = 1.0D;
            double totalStrBoost = 1.0D;
            if (wornItemIds.stream().anyMatch(crystalBodies::contains)) {
                totalAttBoost += 0.15D;
                totalStrBoost += 0.075D;
            }
            if (wornItemIds.stream().anyMatch(crystalLegs::contains)) {
                totalAttBoost *= 0.1D;
                totalStrBoost += 0.05D;
            }
            if (wornItemIds.stream().anyMatch(crystalHelms::contains)) {
                totalAttBoost *= 0.05D;
                totalStrBoost += 0.025D;
            }
            attRoll = (int) (attRoll * totalAttBoost);
            maxHit = (int) (maxHit * totalStrBoost);
            log.info("Wearing Crystal Armor + cbow/bofa -- total bonus is " + totalAttBoost);
        }

        // Slayer Helm and Salve bonus
        double salveSlayerBonus = LuckTrackerUtil.calcSalveSlayerBonus(wornItemsContainer, slayerTarget, salveTarget, attackStyle);
        attRoll = (int) (attRoll * salveSlayerBonus);
        maxHit = (int) (maxHit * salveSlayerBonus);

        // TODO Darklight, Silverlight
        // Arclight
        if (wornItemIds.contains(ItemID.ARCLIGHT) && (attackStyle != AttackStyle.MAGIC) && Arrays.stream(monsterTable.getMonsterData(targetedNpc.getId()).getAttributes()).anyMatch("demon"::contains)) {
            attRoll = (int) (attRoll * 1.7D);
            maxHit = (int) (maxHit * 1.7D);
            log.info("Wielding Arclight and attacking a demon");
        }

        // Dragon Hunter Crossbow
        if ((attackStyle != AttackStyle.MAGIC) &&
                Arrays.stream(monsterTable.getMonsterData(targetedNpc.getId()).getAttributes()).anyMatch("dragon"::contains) &&
                (wornItemIds.contains(ItemID.DRAGON_HUNTER_CROSSBOW) || wornItemIds.contains(ItemID.DRAGON_HUNTER_CROSSBOW_T) || wornItemIds.contains(ItemID.DRAGON_HUNTER_CROSSBOW_B))) {
            attRoll = (int) (attRoll * 1.3);
            maxHit = (int) (maxHit * 1.25);
            log.info("Wielding DHCB and attacking a dragon");
        }

        // Dragon Hunter Lance
        if ((attackStyle != AttackStyle.MAGIC) &&
                Arrays.stream(monsterTable.getMonsterData(targetedNpc.getId()).getAttributes()).anyMatch("dragon"::contains) &&
                (wornItemIds.contains(ItemID.DRAGON_HUNTER_LANCE))) {
            attRoll = (int) (attRoll * 1.2);
            maxHit = (int) (maxHit * 1.2);
            log.info("Wielding DHL and attacking a dragon");
        }

        // TODO Wildy Weapons

        // Twisted Bow
        if ((attackStyle != AttackStyle.MAGIC) &&
                wornItemIds.contains(ItemID.TWISTED_BOW)) {
            int oppMagicLevel = monsterTable.getMonsterData(targetedNpc.getId()).getMagicLvl();
            int oppMagicAcc = monsterTable.getMonsterData(targetedNpc.getId()).getAMagic();
            int oppMagicValue = Math.max(oppMagicAcc, oppMagicLevel);

            // TODO cap oppMagicValue at 350 in cox, 250 outside of cox

            double accuracyBonus = (140.0D + ((((3.0D * oppMagicValue) - 10.0D) / 100.0D) - (Math.pow(((3.0D * oppMagicValue / 10.0D) - 100.0D), 2.0D) / 100.0D))) / 100.0D;
            double damageBonus = (250.0D + ((((3.0D * oppMagicValue) - 14.0D) / 100.0D) - (Math.pow(((3.0D * oppMagicValue / 10.0D) - 140.0D), 2.0D) / 100.0D))) / 100.0D;

            attRoll = (int) (attRoll * accuracyBonus);
            maxHit = (int) (maxHit * damageBonus);
            log.info(String.format("Wielding Twisted Bow -- accuracy bonus = %f, damage bonus = %f", accuracyBonus, damageBonus));
        }

        // Obsidian Armor set bonus (NOT Bers. necklace)
        if (wornItemIds.contains(ItemID.OBSIDIAN_HELMET) && wornItemIds.contains(ItemID.OBSIDIAN_PLATEBODY) && wornItemIds.contains(ItemID.OBSIDIAN_PLATELEGS)) {
            if (wornItemIds.contains(ItemID.TOKTZXILEK) || wornItemIds.contains(ItemID.TOKTZXILAK) || wornItemIds.contains(ItemID.TZHAARKETEM) || wornItemIds.contains(ItemID.TZHAARKETOM)
                    || wornItemIds.contains(ItemID.TOKTZXILAK_20554) || wornItemIds.contains(ItemID.TZHAARKETOM_T)) {
                attRoll = (int) (attRoll * 1.1D);
                maxHit = (int) (maxHit * 1.1D);
                log.info(String.format("Obsidian Armor set bonus granted"));
            }
        }

        // TODO Chinchompa range to target

        // Inquisitor Armor
        if (attackStyle == AttackStyle.CRUSH) {
            double totalBonus = 1.0D;
            for (int itemId : new int[]{ItemID.INQUISITORS_HAUBERK, ItemID.INQUISITORS_HAUBERK_27196, ItemID.INQUISITORS_GREAT_HELM, ItemID.INQUISITORS_GREAT_HELM_27195, ItemID.INQUISITORS_PLATESKIRT, ItemID.INQUISITORS_PLATESKIRT_27197}) {
                if (wornItemIds.contains(itemId)) totalBonus += 0.005D;
            }

            if (totalBonus > 1.014D) { // all 3 pieces gives 1.015 before set effect; use >1.014 due to double precision
                totalBonus = 1.025D;
            }
            attRoll = (int) (attRoll * totalBonus);
            maxHit = (int) (maxHit * totalBonus);

            if (totalBonus > 1.0D) log.info("Wearing Inquisitor armor -- total bonus = " + totalBonus);
        }

        // TODO Tome of Water

        // TODO Keris weaponry

        // TODO Brimstone ring

        // endregion

        hitChance = LuckTrackerUtil.getHitChance(attRoll, defRoll);

        return new HitDist(hitChance, maxHit, npcCurrentHp);
    }
}