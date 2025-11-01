package test;

import java.util.ArrayList;
import java.util.Arrays;

import org.junit.Before;
import org.junit.Test;

import consts.Const;
import main.GameMaster;
import usedItem.TargetOption;

public class TargetOptionTest {

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
		TargetOption to = new TargetOption(Const.PLAYER, gameMaster);
		to.targetLogic();
		System.out.println("存在したら:"+to.getExistsInd());
		System.out.println("指定した数字:"+to.getExNum());
	}

	@Test
	public void test2() {
		String diff = Const.NORMAL;
		gameMaster.setDigits(4);
		gameMaster.setDifficulty(diff);
		ArrayList<String> numList = new ArrayList<String>(Arrays.asList("6","2","7","1"));
		gameMaster.setCorrectCpuNumberList(numList);
		TargetOption to = new TargetOption(Const.PLAYER, gameMaster);
		to.targetLogic();
		System.out.println("存在したら:"+to.getExistsInd());
		System.out.println("指定した数字:"+to.getExNum());
	}

	@Test
	public void test3() {
		String diff = Const.HARD;
		gameMaster.setDigits(5);
		gameMaster.setDifficulty(diff);
		ArrayList<String> numList = new ArrayList<String>(Arrays.asList("3","1","9","5","4"));
		gameMaster.setCorrectCpuNumberList(numList);
		TargetOption to = new TargetOption(Const.PLAYER, gameMaster);
		to.targetLogic();
		System.out.println("存在したら:"+to.getExistsInd());
		System.out.println("指定した数字:"+to.getExNum());
	}

	@Test
	public void test4() {
		String diff = Const.EXHAUSTED;
		gameMaster.setDigits(5);
		gameMaster.setDifficulty(diff);
		ArrayList<String> numList = new ArrayList<String>(Arrays.asList("7","1","4","8","2"));
		gameMaster.setCorrectCpuNumberList(numList);
		TargetOption to = new TargetOption(Const.PLAYER, gameMaster);
		to.targetLogic();
		System.out.println("存在したら:"+to.getExistsInd());
		System.out.println("指定した数字:"+to.getExNum());
	}

	@Test
	public void test5() {
		String diff = Const.INSANE;
		gameMaster.setDigits(4);
		gameMaster.setDifficulty(diff);
		ArrayList<String> numList = new ArrayList<String>(Arrays.asList("9","6","1","8"));
		gameMaster.setCorrectCpuNumberList(numList);
		TargetOption to = new TargetOption(Const.PLAYER, gameMaster);
		to.targetLogic();
		System.out.println("存在したら:"+to.getExistsInd());
		System.out.println("指定した数字:"+to.getExNum());
	}




}
