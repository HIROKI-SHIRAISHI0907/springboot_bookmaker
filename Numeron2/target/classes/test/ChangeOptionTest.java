package test;

import java.util.ArrayList;
import java.util.Arrays;

import org.junit.Before;
import org.junit.Test;

import consts.Const;
import main.GameMaster;
import usedItem.ChangeOption;

public class ChangeOptionTest {

	GameMaster gameMaster = new GameMaster();

	@Before
	public void setUp() {

	}

	@Test
	public void test1() {
		String diff = Const.EASY;
		gameMaster.setDigits(3);
		gameMaster.setDifficulty(diff);
		ArrayList<String> numList = new ArrayList<String>(Arrays.asList("2","6","7"));
		gameMaster.setCorrectCpuNumberList(numList);
		ChangeOption co = new ChangeOption(Const.CPU,gameMaster);
		co.changeLogic();
		System.out.println(gameMaster.getCorrectCpuNumberList());
		System.out.println("桁:"+co.getDigitInd());
		System.out.println("Low?High?:"+co.getLh());
	}

	@Test
	public void test2() {
		String diff = Const.NORMAL;
		gameMaster.setDigits(3);
		gameMaster.setDifficulty(diff);
		ArrayList<String> numList = new ArrayList<String>(Arrays.asList("8","1","9"));
		gameMaster.setCorrectCpuNumberList(numList);
		ChangeOption co = new ChangeOption(Const.CPU,gameMaster);
		co.changeLogic();
		System.out.println(gameMaster.getCorrectCpuNumberList());
		System.out.println("桁:"+co.getDigitInd());
		System.out.println("Low?High?:"+co.getLh());
	}

	@Test
	public void test3() {
		String diff = Const.HARD;
		gameMaster.setDigits(3);
		gameMaster.setDifficulty(diff);
		ArrayList<String> numList = new ArrayList<String>(Arrays.asList("0","6","4"));
		gameMaster.setCorrectCpuNumberList(numList);
		ChangeOption co = new ChangeOption(Const.CPU,gameMaster);
		co.changeLogic();
		System.out.println(gameMaster.getCorrectCpuNumberList());
		System.out.println("桁:"+co.getDigitInd());
		System.out.println("Low?High?:"+co.getLh());
	}

	@Test
	public void test4() {
		String diff = Const.EXHAUSTED;
		gameMaster.setDigits(3);
		gameMaster.setDifficulty(diff);
		ArrayList<String> numList = new ArrayList<String>(Arrays.asList("1","7","5"));
		gameMaster.setCorrectCpuNumberList(numList);
		ChangeOption co = new ChangeOption(Const.CPU,gameMaster);
		co.changeLogic();
		System.out.println(gameMaster.getCorrectCpuNumberList());
		System.out.println("桁:"+co.getDigitInd());
		System.out.println("Low?High?:"+co.getLh());
	}




}
