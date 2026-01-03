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
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.HandleInputListener;
import net.wurstclient.events.PreMotionListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.mixinterface.IKeyBinding;
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
	
	private final CheckboxSetting attackWhileBlocking =
		new CheckboxSetting("Attack while blocking",
			"Attacks even while you're blocking with a shield or using"
				+ " items.\n\n"
				+ "This would not be possible in vanilla and won't work if"
				+ " \"Simulate mouse click\" is enabled.",
			false);
	
	private final CheckboxSetting simulateMouseClick = new CheckboxSetting(
		"Simulate mouse click",
		"Simulates an actual mouse click (or key press) when attacking. Can be"
			+ " used to trick CPS measuring tools into thinking that you're"
			+ " attacking manually.\n\n"
			+ "\u00a7c\u00a7lWARNING:\u00a7r Simulating mouse clicks can lead"
			+ " to unexpected behavior, like in-game menus clicking themselves."
			+ " Also, the \"Swing hand\" and \"Attack while blocking\" settings"
			+ " will not work while this option is enabled.",
		false);
	
	private final EntityFilterList entityFilters =
		EntityFilterList.genericCombat();
	
	private boolean simulatingMouseClick;
	
	private Entity pendingTarget;
	private int pendingSlot = -1;
	private boolean shouldAttack;
	private int previousSlot = -1;
	
	public MaceHack()
	{
		super("Mace");
		setCategory(Category.COMBAT);
		
		addSetting(range);
		addSetting(speed);
		addSetting(speedRandMS);
		addSetting(swingHand);
		addSetting(attackWhileBlocking);
		addSetting(simulateMouseClick);
		
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
		shouldAttack = false;
		
		if(simulatingMouseClick)
		{
			IKeyBinding.get(MC.options.keyAttack).simulatePress(false);
			simulatingMouseClick = false;
		}
		
		EVENTS.remove(PreMotionListener.class, this);
		EVENTS.remove(HandleInputListener.class, this);
	}
	
	@Override
	public void onPreMotion()
	{
		
		if(simulatingMouseClick)
		{
			IKeyBinding.get(MC.options.keyAttack).simulatePress(false);
			simulatingMouseClick = false;
		}
		
		if(!shouldAttack || pendingTarget == null)
			return;
		
		LocalPlayer player = MC.player;
		
		// switch slot 
		if(player.getInventory().getSelectedSlot() != pendingSlot)
			
			player.getInventory().setSelectedSlot(pendingSlot);
		
		WURST.getHax().autoSwordHack.setSlot(pendingTarget);
		
		if(simulateMouseClick.isChecked())
		{
			IKeyBinding.get(MC.options.keyAttack).simulatePress(true);
			simulatingMouseClick = true;
		}else
		{
			MC.gameMode.attack(player, pendingTarget);
			swingHand.swing(InteractionHand.MAIN_HAND);
		}
		
		if(previousSlot != -1
			&& player.getInventory().getSelectedSlot() != previousSlot)
		{
			player.getInventory().setSelectedSlot(previousSlot);
		}
		
		// clear state
		pendingTarget = null;
		pendingSlot = -1;
		shouldAttack = false;
		previousSlot = -1;
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
	
}
