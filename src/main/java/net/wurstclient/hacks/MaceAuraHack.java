/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import java.util.stream.Stream;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.AxeItem;
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

@SearchTags({"mace aura", "auto mace", "kill aura mace"})
public final class MaceAuraHack extends Hack
	implements PreMotionListener, HandleInputListener
{
	private final SliderSetting range =
		new SliderSetting("Range", 4.25, 1, 6, 0.05, ValueDisplay.DECIMAL);

	private final AttackSpeedSliderSetting speed =
		new AttackSpeedSliderSetting();

	private final SliderSetting speedRandMS =
		new SliderSetting("Speed randomization",
			"Random delay between attacks",
			100, 0, 1000, 50,
			ValueDisplay.INTEGER.withPrefix("±").withSuffix("ms"));

	private final SwingHandSetting swingHand =
		new SwingHandSetting(this, SwingHand.CLIENT);

	private final CheckboxSetting stunSlam =
		new CheckboxSetting("Stun slam",
			"Break shields using axe before mace hit.", false);

	private final EntityFilterList entityFilters =
		EntityFilterList.genericCombat();

	private Entity target;
	private int maceSlot = -1;
	private int axeSlot = -1;
	private int previousSlot = -1;
	private boolean attackNextTick;
	private boolean simulatingClick;

	public MaceAuraHack()
	{
		super("MaceAura");
		setCategory(Category.COMBAT);

		addSetting(range);
		addSetting(speed);
		addSetting(speedRandMS);
		addSetting(swingHand);
		addSetting(stunSlam);

		entityFilters.forEach(this::addSetting);
	}

	@Override
	protected void onEnable()
	{
		WURST.getHax().killauraHack.setEnabled(false);
		WURST.getHax().triggerBotHack.setEnabled(false);

		speed.resetTimer(speedRandMS.getValue());
		EVENTS.add(PreMotionListener.class, this);
		EVENTS.add(HandleInputListener.class, this);
	}

	@Override
	protected void onDisable()
	{
		target = null;
		attackNextTick = false;

		if(simulatingClick)
		{
			IKeyBinding.get(MC.options.keyAttack).simulatePress(false);
			simulatingClick = false;
		}

		EVENTS.remove(PreMotionListener.class, this);
		EVENTS.remove(HandleInputListener.class, this);
	}

	@Override
	public void onHandleInput()
	{
		speed.updateTimer();
		if(!speed.isTimeToAttack())
			return;

		if(MC.screen instanceof AbstractContainerScreen)
			return;

		Stream<Entity> stream = EntityUtils.getAttackableEntities();
		double rangeSq = range.getValueSq();

		stream = stream
			.filter(e -> MC.player.distanceToSqr(e) <= rangeSq)
			.filter(entityFilters);

		target = stream.findFirst().orElse(null);
		if(target == null)
			return;

		maceSlot = searchForMace();
		if(maceSlot == -1)
			return;

		if(stunSlam.isChecked())
		{
			axeSlot = searchForAxe();
			if(axeSlot == -1)
				return;
		}

		LocalPlayer player = MC.player;
		previousSlot = player.getInventory().getSelectedSlot();
		attackNextTick = true;

		speed.resetTimer(speedRandMS.getValue());
	}

	@Override
	public void onPreMotion()
	{
		if(!attackNextTick || target == null)
			return;

		LocalPlayer player = MC.player;

		if(stunSlam.isChecked())
		{
			player.getInventory().setSelectedSlot(axeSlot);
			MC.gameMode.attack(player, target);
			swingHand.swing(InteractionHand.MAIN_HAND);
		}

		player.getInventory().setSelectedSlot(maceSlot);
		MC.gameMode.attack(player, target);
		swingHand.swing(InteractionHand.MAIN_HAND);

		if(previousSlot != -1)
			player.getInventory().setSelectedSlot(previousSlot);

		target = null;
		attackNextTick = false;
	}

	private int searchForMace()
	{
		return InventoryUtils.indexOf(stack -> stack.is(Items.MACE), 40);
	}

	private int searchForAxe()
	{
		return InventoryUtils.indexOf(
			stack -> stack.getItem() instanceof AxeItem, 9);
	}
}
