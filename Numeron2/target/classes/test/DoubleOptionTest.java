package test;

import java.util.ArrayList;
import java.util.Arrays;

import org.junit.Before;
import org.junit.Test;

import consts.Const;
import info.Info;
import main.Computer;
import main.GameMaster;
import main.Player;
import usedItem.DoubleOption;

public class DoubleOptionTest {

	GameMaster gameMaster = new GameMaster();
	Player player = new Player(Const.PLAYER);
	Info info = new Info();


	@Before
	public void setUp() {

	}

	@Test
	public void test1() {
		String diff = Const.EASY;
		Computer computer = new Computer(diff);
		Player player = new Player(diff);
		Info info = new Info();
		gameMaster.setDigits(3);
		gameMaster.setDifficulty(diff);
		ArrayList<String> numList = new ArrayList<String>(Arrays.asList("6","2","7"));
		gameMaster.setCorrectPlayerNumberList(numList);
		gameMaster.setCorrectCpuNumberList(numList);
		DoubleOption doO = new DoubleOption(Const.CPU,gameMaster,player, computer, info);
		doO.doubleLogic();
		System.out.println("教える桁:"+doO.getDoubleDigit());
		System.out.println("教える数字:"+doO.getDoubleNum());
	}

	@Test
	public void test2() {
		String diff = Const.NORMAL;
		Computer computer = new Computer(diff);
		Player player = new Player(diff);
		Info info = new Info();
		gameMaster.setDigits(4);
		gameMaster.setDifficulty(diff);
		ArrayList<String> numList = new ArrayList<String>(Arrays.asList("6","2","7","1"));
		gameMaster.setCorrectPlayerNumberList(numList);
		gameMaster.setCorrectCpuNumberList(numList);
		DoubleOption doO = new DoubleOption(Const.CPU,gameMaster,player, computer, info);
		doO.doubleLogic();
		System.out.println("教える桁:"+doO.getDoubleDigit());
		System.out.println("教える数字:"+doO.getDoubleNum());
	}

	@Test
	public void test3() {
		String diff = Const.HARD;
		Computer computer = new Computer(diff);
		Player player = new Player(diff);
		Info info = new Info();
		gameMaster.setDigits(5);
		gameMaster.setDifficulty(diff);
		ArrayList<String> numList = new ArrayList<String>(Arrays.asList("6","2","7","1","9"));
		gameMaster.setCorrectPlayerNumberList(numList);
		gameMaster.setCorrectCpuNumberList(numList);
		DoubleOption doO = new DoubleOption(Const.CPU,gameMaster,player, computer, info);
		doO.doubleLogic();
		System.out.println("教える桁:"+doO.getDoubleDigit());
		System.out.println("教える数字:"+doO.getDoubleNum());
	}




}
