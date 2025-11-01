package test;

import org.junit.Before;
import org.junit.Test;

import component.Anything;

public class AnythingTest {

	Anything anything = new Anything();

	@Before
	public void setUp() {

	}

	@Test
	public void test1() {
		String str = anything.concatStringToComma("NONE","HIGH","LOW");
		System.out.println("結果:"+str);
	}

}
