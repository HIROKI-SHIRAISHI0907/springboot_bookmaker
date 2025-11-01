package test;

import org.junit.Before;
import org.junit.Test;

import consts.Const;
import game.Numer0n;
import main.GameMaster;
import main.Player;

public class Numer0nTest {

	GameMaster gameMaster = new GameMaster();
	Player player = new Player(Const.PLAYER);
	Numer0n numeron = new Numer0n();

	@Before
	public void setUp() {

	}

	@Test
	public void test1() {
		numeron.doStart();
		//System.out.println("結果:"+hl.getHighLowList());
	}

	@Test
	public void test2() {
		numeron.doStart();
		//System.out.println("結果:"+hl.getHighLowList());
	}

	@Test
	public void test3() {
		numeron.doStart();
		//System.out.println("コール結果:"+hl.getHighLowList());
	}

	@Test
	public void test4() {
		numeron.doStart();
		//System.out.println("結果:"+hl.getHighLowList());
	}

	@Test
	public void test5() {
		numeron.doStart();
		//System.out.println("結果:"+hl.getHighLowList());
	}




}
