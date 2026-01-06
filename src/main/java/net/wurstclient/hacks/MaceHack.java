/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.AxeItem;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.HandleInputListener;
import net.wurstclient.events.PreMotionListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.AttackSpeedSliderSetting;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.settings.SwingHandSetting;
import net.wurstclient.settings.SwingHandSetting.SwingHand;
import net.wurstclient.settings.filterlists.EntityFilterList;
import net.wurstclient.util.EntityUtils;
import net.wurstclient.util.InventoryUtils;

@SearchTags({"trigger bot", "AutoAttack", "auto attack", "AutoClicker",
	"auto clicker", "mace", "auto mace", "automace"})
public final class MaceHack extends Hack
	implements PreMotionListener, HandleInputListener
{
	private final SliderSetting range =
		new SliderSetting("Range", 4.25, 1, 6, 0.05, ValueDisplay.DECIMAL);
	
	private final AttackSpeedSliderSetting speed =
		new AttackSpeedSliderSetting();
	
	private final SliderSetting speedRandMS =
		new SliderSetting("Speed randomization",
			"Helps you bypass anti-cheat plugins by varying the delay between"
				+ " attacks.\n\n" + "\u00b1100ms is recommended for Vulcan.\n\n"
				+ "0 (off) is fine for NoCheat+, AAC, Grim, Verus, Spartan, and"
				+ " vanilla servers.",
			100, 0, 1000, 50, ValueDisplay.INTEGER.withPrefix("\u00b1")
				.withSuffix("ms").withLabel(0, "off"));
	
	private final SwingHandSetting swingHand =
		new SwingHandSetting(this, SwingHand.CLIENT);
	
	private final CheckboxSetting stunSlamm = new CheckboxSetting("Stun slamm ",
		"Toggle this to perform stunn slamms(hit player with shield).", false);
	
	private final CheckboxSetting attackWhileBlocking = new CheckboxSetting(
		"Attack while blocking",
		"Attacks even while you're blocking with a shield or using"
			+ " items.\n\n"
			+ "This would not be possible in vanilla and won't work if",
		false);
	
	private final EntityFilterList entityFilters =
		EntityFilterList.genericCombat();
	
	private Entity pendingTarget;
	private int pendingSlot = -1;
	private boolean shouldAttack;
	private int previousSlot = -1;
	private int axeSlot = -1;
	
	private enum State
	{
		NONE,
		// stun slam states
		SWITCH_TO_AXE,
		HIT_AXE,
		WAIT,
		SWITCH_TO_MACE,
		HIT_MACE,
		
		// normal states (i could also use the switch_to_mace and hit_mace cases
		// above in normal mace hit but using these for better readability)
		NORMAL_SWITCH,
		NORMAL_HIT,
		
		RESTORE
	}
	
	private State state = State.NONE;
	
	public MaceHack()
	{
		super("Mace");
		setCategory(Category.COMBAT);
		addSetting(stunSlamm);
		addSetting(range);
		addSetting(speed);
		addSetting(speedRandMS);
		addSetting(swingHand);
		addSetting(attackWhileBlocking);
		
		entityFilters.forEach(this::addSetting);
	}
	
	@Override
	protected void onEnable()
	{
		// disable other killauras
		WURST.getHax().triggerBotHack.setEnabled(false);
		WURST.getHax().clickAuraHack.setEnabled(false);
		WURST.getHax().crystalAuraHack.setEnabled(false);
		WURST.getHax().fightBotHack.setEnabled(false);
		WURST.getHax().killauraLegitHack.setEnabled(false);
		WURST.getHax().killauraHack.setEnabled(false);
		WURST.getHax().multiAuraHack.setEnabled(false);
		WURST.getHax().protectHack.setEnabled(false);
		WURST.getHax().tpAuraHack.setEnabled(false);
		
		speed.resetTimer(speedRandMS.getValue());
		EVENTS.add(PreMotionListener.class, this);
		EVENTS.add(HandleInputListener.class, this);
	}
	
	@Override
	protected void onDisable()
	{
		pendingTarget = null;
		pendingSlot = -1;
		previousSlot = -1;
		axeSlot = -1;
		shouldAttack = false;
		
		EVENTS.remove(PreMotionListener.class, this);
		EVENTS.remove(HandleInputListener.class, this);
	}
	
	@Override
	public void onPreMotion()
	{
		
		if(!shouldAttack || pendingTarget == null)
			return;
		
		LocalPlayer player = MC.player;
		
		if(stunSlamm.isChecked() && state == State.NONE && pendingTarget != null
			&& axeSlot != -1 && pendingSlot != -1)
		{
			previousSlot = player.getInventory().getSelectedSlot();
			state = State.SWITCH_TO_AXE; // control transfer to switch case
											// switch_to_axe , line -175
		}
		
		if(!stunSlamm.isChecked() && state == State.NONE
			&& pendingTarget != null && pendingSlot != -1)
		{
			previousSlot = player.getInventory().getSelectedSlot();
			state = State.NORMAL_SWITCH; // control transfer to switch
											// normal_switch , line -207
		}
		
		// switch case to avoid same tick packet spamming (this performs action
		// tick by tick to avoid anticheats, to do : tick randomization )
		
		if(state == State.NONE)
			return;
		
		switch(state)
		{
			// states for stun slamm
			case SWITCH_TO_AXE:
			if(player.getInventory().getSelectedSlot() != axeSlot)
			{
				player.getInventory().setSelectedSlot(axeSlot);
				state = State.HIT_AXE;
			}else
			{
				MC.gameMode.attack(player, pendingTarget);
				swingHand.swing(InteractionHand.MAIN_HAND);
				
				state = State.SWITCH_TO_MACE;// ignoring wait
			}
			break;
			
			case HIT_AXE:
			MC.gameMode.attack(player, pendingTarget);
			swingHand.swing(InteractionHand.MAIN_HAND);
			
			state = State.SWITCH_TO_MACE;// ignoring wait
			break;
			
			case WAIT:
			if(speed.isTimeToAttack())
				state = State.SWITCH_TO_MACE;
			break;
			
			case SWITCH_TO_MACE:
			player.getInventory().setSelectedSlot(pendingSlot);
			state = State.HIT_MACE;
			break;
			
			case HIT_MACE:
			MC.gameMode.attack(player, pendingTarget);
			swingHand.swing(InteractionHand.MAIN_HAND);
			
			state = State.RESTORE;// line 218
			break;
			
			// states for normal mace hit
			
			case NORMAL_SWITCH:
			if(player.getInventory().getSelectedSlot() != pendingSlot)
			{
				player.getInventory().setSelectedSlot(pendingSlot);
				state = State.NORMAL_HIT;
			}else
			{
				MC.gameMode.attack(player, pendingTarget);
				swingHand.swing(InteractionHand.MAIN_HAND);
				state = State.RESTORE;// line 221
				
			}
			break;
			
			case NORMAL_HIT:
			MC.gameMode.attack(player, pendingTarget);
			swingHand.swing(InteractionHand.MAIN_HAND);
			state = State.RESTORE;// line 221
			break;
			
			case RESTORE:
			if(previousSlot != -1)
				player.getInventory().setSelectedSlot(previousSlot);
			// clearing state
			state = State.NONE;
			
			pendingTarget = null;
			pendingSlot = -1;
			shouldAttack = false;
			previousSlot = -1;
			axeSlot = -1;
			break;
			
		}
	}
	
	@Override
	public void onHandleInput()
	{
		speed.updateTimer();
		if(!speed.isTimeToAttack())
			return;
		
		if(MC.screen instanceof AbstractContainerScreen)
			return;
		
		LocalPlayer player = MC.player;
		if(!attackWhileBlocking.isChecked() && player.isUsingItem())
			return;
		
		if(!(MC.hitResult instanceof EntityHitResult eResult))
			return;
		
		Entity target = eResult.getEntity();
		if(target == null || !isCorrectEntity(target))
			return;
		
		int maceSlot = searchForMace();
		if(maceSlot == -1)
			return;
		
		axeSlot = searchForAxe();
		if(axeSlot == -1)
			return;
		
		pendingTarget = target;
		pendingSlot = maceSlot;
		if(previousSlot == -1)
			previousSlot = player.getInventory().getSelectedSlot();
		shouldAttack = true;
		
		speed.resetTimer(speedRandMS.getValue());
	}
	
	private boolean isCorrectEntity(Entity entity)
	{
		if(!EntityUtils.IS_ATTACKABLE.test(entity))
			return false;
		
		if(MC.player.distanceToSqr(entity) > range.getValueSq())
			return false;
		
		return entityFilters.testOne(entity);
	}
	
	private int searchForMace()
	{
		return InventoryUtils.indexOf(this::isMace, 40);
		
	}
	
	private boolean isMace(ItemStack stack)
	{
		return stack.is(Items.MACE);
	}
	
	private int searchForAxe()
	{
		return InventoryUtils
			.indexOf(stack -> stack.getItem() instanceof AxeItem, 9);
	}
	
}
