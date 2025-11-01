package test;

import java.util.ArrayList;
import java.util.Arrays;

import org.junit.Before;
import org.junit.Test;

import consts.Const;
import judge.Eatbite;
import main.GameMaster;

public class EatBiteTest {

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
		ArrayList<String> judgeList = new ArrayList<String>(Arrays.asList("5","9","1"));
		Eatbite eb = new Eatbite(judgeList, Const.PLAYER, gameMaster);
		eb.judgeEatBite(gameMaster);
		System.out.println("結果:"+eb.getEatBiteResult());
	}

	@Test
	public void test2() {
		String diff = Const.NORMAL;
		gameMaster.setDigits(4);
		gameMaster.setDifficulty(diff);
		ArrayList<String> numList = new ArrayList<String>(Arrays.asList("3","9","0","1"));
		gameMaster.setCorrectCpuNumberList(numList);
		ArrayList<String> judgeList = new ArrayList<String>(Arrays.asList("5","8","4","9"));
		Eatbite eb = new Eatbite(judgeList, Const.PLAYER, gameMaster);
		eb.judgeEatBite(gameMaster);
		System.out.println("結果:"+eb.getEatBiteResult());
	}

	@Test
	public void test3() {
		String diff = Const.HARD;
		gameMaster.setDigits(5);
		gameMaster.setDifficulty(diff);
		ArrayList<String> numList = new ArrayList<String>(Arrays.asList("6","9","4","1","2"));
		gameMaster.setCorrectCpuNumberList(numList);
		ArrayList<String> judgeList = new ArrayList<String>(Arrays.asList("9","3","5","1","2"));
		Eatbite eb = new Eatbite(judgeList, Const.PLAYER, gameMaster);
		eb.judgeEatBite(gameMaster);
		System.out.println("結果:"+eb.getEatBiteResult());
	}

}
