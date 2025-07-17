package dev.application.analyze.bm_m004;

class TimeSegmentAggregator {
	/** シュート合計 */
	private double shootSum = 0.0;

	/** 枠内シュート合計 */
	private double shootInSum = 0.0;

	/** ビッグチャンス合計 */
	private double bigChanceSum = 0.0;

	/** フリーキック合計 */
	private double freeKickSum = 0.0;

	/** オフサイド合計 */
	private double offsideSum = 0.0;

	/** ファウル合計 */
	private double foulSum = 0.0;

	/** イエローカード合計 */
	private double yellowSum = 0.0;

	/** レッドカード合計 */
	private double redSum = 0.0;

	/** 件数 */
	private int[] count = {0,0,0,0,0,0,0,0};

	/** 加算メソッド */
	synchronized void addShoot(String val) { if (val != null) shootSum += Double.parseDouble(val); count[0]++;}
	synchronized void addShootIn(String val) { if (val != null) shootInSum += Double.parseDouble(val); count[1]++;}
	synchronized void addBigChance(String val) { if (val != null) bigChanceSum += Double.parseDouble(val); count[2]++;}
	synchronized void addFreeKick(String val) { if (val != null) freeKickSum += Double.parseDouble(val); count[3]++;}
	synchronized void addOffside(String val) { if (val != null) offsideSum += Double.parseDouble(val); count[4]++;}
	synchronized void addFoul(String val) { if (val != null) foulSum += Double.parseDouble(val); count[5]++;}
	synchronized void addYellow(String val) { if (val != null) yellowSum += Double.parseDouble(val); count[6]++;}
	synchronized void addRed(String val) { if (val != null) redSum += Double.parseDouble(val); count[7]++;}

	/** 平均取得メソッド */
	synchronized double getShootAvg() { return count[0] == 0 ? 0 : shootSum / count[0]; }
	synchronized double getShootInAvg() { return count[1] == 0 ? 0 : shootInSum / count[1]; }
	synchronized double getBigChanceAvg() { return count[2] == 0 ? 0 : bigChanceSum / count[2]; }
	synchronized double getFreeKickAvg() { return count[3] == 0 ? 0 : freeKickSum / count[3]; }
	synchronized double getOffsideAvg() { return count[4] == 0 ? 0 : offsideSum / count[4]; }
	synchronized double getFoulAvg() { return count[5] == 0 ? 0 : foulSum / count[5]; }
	synchronized double getYellowAvg() { return count[6] == 0 ? 0 : yellowSum / count[6]; }
	synchronized double getRedAvg() { return count[7] == 0 ? 0 : redSum / count[7]; }
}
