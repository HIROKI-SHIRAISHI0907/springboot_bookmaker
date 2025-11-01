package test;

import java.util.ArrayList;
import java.util.Arrays;

import org.junit.Before;
import org.junit.Test;

import info.AiSpecifyOptionAndNextAction;
import info.Info;
import main.Computer;
import main.GameMaster;
import main.Player;
import usedItem.ChangeOption;
import usedItem.ShuffleOption;
import usedItem.TargetOption;

public class AiSpecifyOptionAndNextActionTest {

	GameMaster gameMaster = new GameMaster();
	Player player;
	Computer computer;
	ShuffleOption shuffle;
	ChangeOption change;
	TargetOption target;
	Info info = new Info();

	@Before
	public void setUp() {
		// 順番を決める
		gameMaster.setTurnName();
		// 難易度と桁を選択
		gameMaster.selectDifficulty();
		gameMaster.selectDigits();
		player = new Player(gameMaster.getName());
		computer = new Computer(gameMaster.getDifficulty());

		// お互い数字を選択
		player.makeCorrectNumber(gameMaster);
		computer.makeCorrectNumber(gameMaster);

		// ゲームスタート
		// 使用オプションを決める
		player.selectOption();
		computer.selectOption();

		shuffle = new ShuffleOption(gameMaster.getName(),gameMaster);
		change = new ChangeOption(gameMaster.getName(), gameMaster);
		target = new TargetOption(gameMaster.getName(), gameMaster);
	}

	@Test
	public void test1() {
		info.setPlayerInfoList("HIGH&LOW,HIGH,LOW,HIGH");
		player.candidatePlayerNumberList = new ArrayList<String>(Arrays.asList("320","620","420","820"));
		computer.candidateCpuNumberList = new ArrayList<String>(Arrays.asList("320"));
		AiSpecifyOptionAndNextAction aiOp = new AiSpecifyOptionAndNextAction(player, computer, info, gameMaster);
		aiOp.setOptionYuusenFlagLogic(gameMaster, computer, target, shuffle, change);
		System.out.println(aiOp.conditionMakeNumber);
	}

	@Test
	public void test2() {
		info.setPlayerInfoList("NOOPTION,8,5,2,0EAT2BITE");
		player.candidatePlayerNumberList = new ArrayList<String>(Arrays.asList("320","620","420","820"));
		computer.candidateCpuNumberList = new ArrayList<String>(Arrays.asList("320"));
		AiSpecifyOptionAndNextAction aiOp = new AiSpecifyOptionAndNextAction(player, computer, info, gameMaster);
		aiOp.setOptionYuusenFlagLogic(gameMaster, computer, target, shuffle, change);
		System.out.println(aiOp.conditionMakeNumber);
	}

}
