package test;

import java.util.ArrayList;
import java.util.Arrays;

import org.junit.Before;
import org.junit.Test;

import consts.Const;
import main.GameMaster;
import usedItem.SlashOption;

public class SlashOptionTest {

	GameMaster gameMaster = new GameMaster();

	@Before
	public void setUp() {

	}

	@Test
	public void test1() {
		String diff = Const.EASY;
		gameMaster.setDigits(3);
		gameMaster.setDifficulty(diff);
		ArrayList<String> numList = new ArrayList<String>(Arrays.asList("9","0","1"));
		gameMaster.setCorrectCpuNumberList(numList);
		SlashOption slo = new SlashOption(Const.PLAYER, gameMaster);
		System.out.println("結果:"+slo.getSlashNum());
	}

	@Test
	public void test2() {
		String diff = Const.NORMAL;
		gameMaster.setDigits(4);
		gameMaster.setDifficulty(diff);
		ArrayList<String> numList = new ArrayList<String>(Arrays.asList("6","2","7","1"));
		gameMaster.setCorrectCpuNumberList(numList);
		SlashOption slo = new SlashOption(Const.PLAYER, gameMaster);
		System.out.println("結果:"+slo.getSlashNum());
	}

	@Test
	public void test3() {
		String diff = Const.HARD;
		gameMaster.setDigits(5);
		gameMaster.setDifficulty(diff);
		ArrayList<String> numList = new ArrayList<String>(Arrays.asList("3","1","9","5","4"));
		gameMaster.setCorrectCpuNumberList(numList);
		SlashOption slo = new SlashOption(Const.PLAYER, gameMaster);
		System.out.println("結果:"+slo.getSlashNum());
	}




}
