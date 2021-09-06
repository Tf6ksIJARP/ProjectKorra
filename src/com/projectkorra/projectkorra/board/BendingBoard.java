package com.projectkorra.projectkorra.board;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.projectkorra.projectkorra.BendingPlayer;
import com.projectkorra.projectkorra.Element;
import com.projectkorra.projectkorra.ProjectKorra;
import com.projectkorra.projectkorra.ability.CoreAbility;
import com.projectkorra.projectkorra.configuration.ConfigManager;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.*;

/**
 * Represents a player's scoreboard for bending purposes
 */
public class BendingBoard {
	public static class BoardSlot {
		
		private final Scoreboard board;
		private final Objective obj;
		private int slot;
		private final Team team;
		private final String entry;
		private Optional<BoardSlot> next = Optional.empty();
		
		@SuppressWarnings("deprecation")
		public BoardSlot(Scoreboard board, Objective obj, int slot) {
			this.board = board;
			this.obj = obj;
			this.slot = slot + 1;
			this.team = board.registerNewTeam("slot" + this.slot);
			this.entry = ChatColor.values()[slot % 10] + "" + ChatColor.values()[slot % 16];
			
			team.addEntry(entry);
		}
		
		private void set() {
			if (board.getObjective(obj.getName()) != null) {
				obj.getScore(entry).setScore(-slot);
			}
		}
		
		public void update(String prefix, String name) {
			team.setPrefix(prefix);
			team.setSuffix(name);
			set();
		}
		
		public void setSlot(int slot) {
			this.slot = slot + 1;
			set();
		}
		
		public void decreaseSlot() {
			setSlot(--slot);
			next.ifPresent(BoardSlot::decreaseSlot);
		}
		
		public void clear() {
			board.resetScores(entry);
			team.unregister();
			next.ifPresent(BoardSlot::decreaseSlot);
		}
		
		private void setNext(BoardSlot slot) {
			this.next = Optional.ofNullable(slot);
		}
	}
	
	private final BoardSlot[] slots = new BoardSlot[9];
	private final Multimap <String, BoardSlot> misc = ArrayListMultimap.create();
	private final Objective bendingSlots;
	private final Scoreboard bendingBoard;
	protected int selectedSlot;
	private final UUID uuid;
	private BoardSlot miscTail = null;
	
	private String prefix, emptySlot, miscSeparator;
	protected ChatColor selectedColor, altColor;

	public BendingBoard(final BendingPlayer bPlayer) {
		uuid = bPlayer.getUUID();
		selectedSlot = bPlayer.getPlayer().getInventory().getHeldItemSlot() + 1;
		bendingBoard = Bukkit.getScoreboardManager().getNewScoreboard();
		String title = ChatColor.translateAlternateColorCodes('&', ConfigManager.languageConfig.get().getString("Board.Title"));
		bendingSlots = bendingBoard.registerNewObjective("Board Slots", "dummy", title);
		bendingSlots.setDisplaySlot(DisplaySlot.SIDEBAR);
		
		for (int i = 0; i < 9; ++i) {
			slots[i] = new BoardSlot(bendingBoard, bendingSlots, i);
		}
		
		prefix = ChatColor.stripColor(ConfigManager.languageConfig.get().getString("Board.Prefix.Text"));
		emptySlot = ChatColor.translateAlternateColorCodes('&', ConfigManager.languageConfig.get().getString("Board.EmptySlot"));
		miscSeparator = ChatColor.translateAlternateColorCodes('&', ConfigManager.languageConfig.get().getString("Board.MiscSeparator"));

		updateAll();
	}

	void destroy() {
		bendingBoard.clearSlot(DisplaySlot.SIDEBAR);
		bendingSlots.unregister();
	}
	
	private ChatColor getElementColor() {
		Player player = Bukkit.getPlayer(uuid);
		if (player == null) {
			return ChatColor.WHITE;
		}
		BendingPlayer bendingPlayer = BendingPlayer.getBendingPlayer(player);
		if (bendingPlayer == null) {
			return ChatColor.WHITE;
		}
		if (bendingPlayer.getElements().size() > 1) {
			return Element.AVATAR.getColor().asBungee();
		} else if (bendingPlayer.getElements().size() == 1) {
			return bendingPlayer.getElements().get(0).getColor().asBungee();
		} else {
			return ChatColor.WHITE;
		}
	}
	
	private ChatColor getColor(String from, ChatColor def) {
		if (from.equalsIgnoreCase("element")) {
			return getElementColor();
		}
		
		try {
			return ChatColor.of(from);
		} catch (Exception e) {
			ProjectKorra.plugin.getLogger().warning("Couldn't parse board color from '" + from + "', using default!");
			return def;
		}
	}

	public void hide() {
		Player player = Bukkit.getPlayer(uuid);
		if (player == null) {
			return;
		}
		player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
	}

	public void show() {
		Player player = Bukkit.getPlayer(uuid);
		if (player == null) {
			return;
		}
		player.setScoreboard(bendingBoard);
		updateAll();
	}

	public boolean isVisible() {
		Player player = Bukkit.getPlayer(uuid);
		if (player == null) {
			return false;
		}
		return player.getScoreboard().equals(bendingBoard);
	}

	public void setVisible(boolean show) {
		if (show) {
			show();
		} else {
			hide();
		}
	}

	public void setSlot(int slot, String ability, boolean cooldown) {
		Player player = Bukkit.getPlayer(uuid);
		if (player == null || slot < 1 || slot > 9 || !player.getScoreboard().equals(bendingBoard)) {
			return;
		}
		
		StringBuilder sb = new StringBuilder();
		
		if (ability == null || ability.isEmpty()) {
			sb.append(emptySlot.replaceAll("\\{slot_number\\}", "" + slot));
		} else {
			BendingPlayer bendingPlayer = BendingPlayer.getBendingPlayer(player);
			if (bendingPlayer == null) {
				return;
			}
			CoreAbility coreAbility = CoreAbility.getAbility(ChatColor.stripColor(ability));
			if (coreAbility == null) { // MultiAbility
				if (cooldown || bendingPlayer.isOnCooldown(ability)) {
					sb.append(ChatColor.STRIKETHROUGH);
				}
				sb.append(ability);
			} else if (cooldown) {
				new CooldownTask(ability, bendingPlayer, this.slots, this, slot).runTaskTimer(ProjectKorra.plugin, 0L, 20L);
				return;
			} else {
				sb.append(coreAbility.getMovePreviewWithoutCooldownTimer(player, cooldown));
			}
		}
		
		slots[slot - 1].update((slot == selectedSlot ? selectedColor : altColor) + prefix, sb.toString());
	}
	
	private int updateSelected(int newSlot) {
		int oldSlot = selectedSlot;
		selectedSlot = newSlot;
		return oldSlot;
	}
	
	public void updateColors() {
		selectedColor = getColor(ConfigManager.languageConfig.get().getString("Board.Prefix.SelectedColor"), ChatColor.WHITE);
		altColor = getColor(ConfigManager.languageConfig.get().getString("Board.Prefix.NonSelectedColor"), ChatColor.DARK_GRAY);
	}

	public void updateAll() {
		Player player = Bukkit.getPlayer(uuid);
		if (player == null) {
			return;
		}
		BendingPlayer bendingPlayer = BendingPlayer.getBendingPlayer(player);
		if (bendingPlayer == null) {
			return;
		}
		updateColors();
		selectedSlot = player.getInventory().getHeldItemSlot() + 1;
		for (int i = 1; i <= 9; i++) {
			setSlot(i, bendingPlayer.getAbilities().get(i), false);
		}
	}

	public void clearSlot(int slot) {
		setSlot(slot, null, false);
	}

	public void setActiveSlot(int newSlot) {
		Player player = Bukkit.getPlayer(uuid);
		if (player == null) {
			return;
		}
		BendingPlayer bendingPlayer = BendingPlayer.getBendingPlayer(player);
		if (bendingPlayer == null) {
			return;
		}
		int oldSlot = updateSelected(newSlot);
		setSlot(oldSlot, bendingPlayer.getAbilities().get(oldSlot), false);
		setSlot(newSlot, bendingPlayer.getAbilities().get(newSlot), false);
	}

	public void setAbilityCooldown(String name, boolean cooldown) {
		Player player = Bukkit.getPlayer(uuid);
		if (player == null) {
			return;
		}
		BendingPlayer bendingPlayer = BendingPlayer.getBendingPlayer(player);
		if (bendingPlayer == null) {
			return;
		}
		bendingPlayer.getAbilities().entrySet().stream().filter(entry -> name.equals(entry.getValue())).forEach(entry -> setSlot(entry.getKey(), name, cooldown));
	}
	
	public void updateMisc(String name, ChatColor color, boolean cooldown) {
		if (!cooldown) {
			for (Iterator<Map.Entry<String, BoardSlot>> it = misc.entries().iterator(); it.hasNext();) {
				Map.Entry<String, BoardSlot> slot = it.next();
				if (miscTail == slot.getValue()) {
					miscTail = null;
				}

				slot.getValue().clear();
				it.remove();
				return;
			}
				
			if (misc.isEmpty()) {
				bendingBoard.resetScores(miscSeparator);
			}
		} else if (!misc.containsKey(name)) {
			BoardSlot slot = new BoardSlot(bendingBoard, bendingSlots, 10 + misc.size());
			misc.put(name, slot);
			slot.update(String.join("", Collections.nCopies(ChatColor.stripColor(prefix).length() + 1, " ")), color + "" + ChatColor.STRIKETHROUGH + name);
			
			if (miscTail != null) {
				miscTail.setNext(slot);
			}
			
			miscTail = slot;
			bendingSlots.getScore(miscSeparator).setScore(-10);
		}	
	}
}
