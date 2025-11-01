package test;

import java.util.ArrayList;
import java.util.Arrays;

import org.junit.Test;

import consts.Const;
import info.AiSpecifyNumber;
import main.Computer;
import main.GameMaster;
import main.Player;

public class AiSpecifyNumberTest {

	@Test
	public void test1() {
		GameMaster gameMaster = new GameMaster();
		Player player = new Player(Const.PLAYER);
		gameMaster.setDigits(3);
		Computer computer = new Computer(Const.EXHAUSTED);
		computer.setCorrectPlayerNumberList(new ArrayList<String>(Arrays.asList("4","1","5")));
		AiSpecifyNumber ai = new AiSpecifyNumber(null, player, computer, null);
		String item = "NONEOPTION";
		String info = "NONEOPTION,8,5,2,0EAT1BITE";
		ai.arrangeCandidateNumberLogic(item, info, gameMaster, player, computer);
		System.out.println(computer.getCandidateCpuNumberList());
		System.out.println(computer.getNotCandidateCpuNumberList());
	}

}
