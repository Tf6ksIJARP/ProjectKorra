package com.projectkorra.projectkorra.board;

import com.projectkorra.projectkorra.BendingPlayer;
import com.projectkorra.projectkorra.ability.CoreAbility;
import com.projectkorra.projectkorra.configuration.ConfigManager;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Collection;
import java.util.HashSet;

import static com.projectkorra.projectkorra.ability.util.MultiAbilityManager.hasMultiAbilityBound;

public class CooldownTask extends BukkitRunnable {
 public static final Collection<Integer> IDS = new HashSet<>();
 private static final String PREFIX = ChatColor
  .stripColor(ConfigManager.languageConfig.get().getString("Board.Prefix.Text"));
 private final BendingPlayer bendingPlayer;
 private final int slot;
 private final String ability;
 private final BendingBoard.BoardSlot[] slots;
 private final BendingBoard board;

 public CooldownTask(final String abilityName,
                     final BendingPlayer bendingPlayer,
                     final BendingBoard.BoardSlot[] slots,
                     final BendingBoard board,
                     final int slot) {
  this.bendingPlayer = bendingPlayer;
  this.ability = abilityName;
  this.slots = slots.clone();
  this.slot = slot;
  this.board = board;
 }

 @Override
 public void run() {
  final var cooldown = this.bendingPlayer.getCooldown(this.ability) - System.currentTimeMillis();
  if (hasMultiAbilityBound(this.bendingPlayer.getPlayer())) { return; }
  if ((0 > cooldown) || !this.bendingPlayer.getAbilities().containsValue(this.ability)) {
   CooldownTask.IDS.remove(this.getTaskId());
   this.cancel();
   return;
  }
  final var abil = CoreAbility.getAbility(this.ability);
  final var sb = (abil == null ? ability : abil.getMovePreview(bendingPlayer.getPlayer()));
  this.slots[this.slot - 1].update((slot == board.selectedSlot ? board.selectedColor : board.altColor) + PREFIX, sb);
  CooldownTask.IDS.add(this.getTaskId());
 }
}
