package dev.application.analyze.bm_m022;

import lombok.Data;

/**
 * ホームおよびアウェーの試合統計フィールドの差分を格納するエンティティクラスです。
 * 各フィールドに対応する差分を保存し、試合データの比較や分析に使用されます。
 * <p>
 * このクラスは、ホームとアウェーの差分を個別に格納するためのフィールドを持っています。
 * また、差分を取得するためのゲッターと設定するためのセッターが自動生成されます。
 * </p>
 *
 * @author shiraishitoshio
 */
@Data
public class StatsDiffEntity {

	/**
     * ID
     */
    private String id;

	/**
     * 国カテゴリ
     */
    private String dataCategory;

    /**
     * ホームチーム
     */
    private String homeTeamName;

    /**
     * アウェーチーム
     */
    private String awayTeamName;

    // ホームフィールドの差分

	/**
     * ホームチームのスコアの差分
     */
    private String diffHomeScore;

    /**
     * ホームチームのポゼッションの差分
     */
    private String diffHomeDonation;

    /**
     * ホームチームのシュート数の差分
     */
    private String diffHomeShootAll;

    /**
     * ホームチームの枠内シュート数の差分
     */
    private String diffHomeShootIn;

    /**
     * ホームチームの枠外シュート数の差分
     */
    private String diffHomeShootOut;

    /**
     * ホームチームのブロックシュート数の差分
     */
    private String diffHomeBlockShoot;

    /**
     * ホームチームのビッグチャンス数の差分
     */
    private String diffHomeBigChance;

    /**
     * ホームチームのコーナーキック数の差分
     */
    private String diffHomeCorner;

    /**
     * ホームチームのボックス内シュート数の差分
     */
    private String diffHomeBoxShootIn;

    /**
     * ホームチームのボックス外シュート数の差分
     */
    private String diffHomeBoxShootOut;

    /**
     * ホームチームのゴールポストに当たったシュート数の差分
     */
    private String diffHomeGoalPost;

    /**
     * ホームチームのヘディングゴール数の差分
     */
    private String diffHomeGoalHead;

    /**
     * ホームチームのキーパーセーブ数の差分
     */
    private String diffHomeKeeperSave;

    /**
     * ホームチームのフリーキック数の差分
     */
    private String diffHomeFreeKick;

    /**
     * ホームチームのオフサイド数の差分
     */
    private String diffHomeOffside;

    /**
     * ホームチームのファウル数の差分
     */
    private String diffHomeFoul;

    /**
     * ホームチームのイエローカード数の差分
     */
    private String diffHomeYellowCard;

    /**
     * ホームチームのレッドカード数の差分
     */
    private String diffHomeRedCard;

    /**
     * ホームチームのスローイン数の差分
     */
    private String diffHomeSlowIn;

    /**
     * ホームチームのボックスタッチ数の差分
     */
    private String diffHomeBoxTouch;

    /**
     * ホームチームのパス数の差分
     */
    private String diffHomePassCount;

    /**
     * ホームチームのファイナルサードパス数の差分
     */
    private String diffHomeFinalThirdPassCount;

    /**
     * ホームチームのクロス数の差分
     */
    private String diffHomeCrossCount;

    /**
     * ホームチームのタックル数の差分
     */
    private String diffHomeTackleCount;

    /**
     * ホームチームのクリア数の差分
     */
    private String diffHomeClearCount;

    /**
     * ホームチームのインターセプト数の差分
     */
    private String diffHomeInterceptCount;

    // アウェーフィールドの差分

    /**
     * アウェーチームのスコアの差分
     */
    private String diffAwayScore;

    /**
     * アウェーチームのポゼッションの差分
     */
    private String diffAwayDonation;

    /**
     * アウェーチームのシュート数の差分
     */
    private String diffAwayShootAll;

    /**
     * アウェーチームの枠内シュート数の差分
     */
    private String diffAwayShootIn;

    /**
     * アウェーチームの枠外シュート数の差分
     */
    private String diffAwayShootOut;

    /**
     * アウェーチームのブロックシュート数の差分
     */
    private String diffAwayBlockShoot;

    /**
     * アウェーチームのビッグチャンス数の差分
     */
    private String diffAwayBigChance;

    /**
     * アウェーチームのコーナーキック数の差分
     */
    private String diffAwayCorner;

    /**
     * アウェーチームのボックス内シュート数の差分
     */
    private String diffAwayBoxShootIn;

    /**
     * アウェーチームのボックス外シュート数の差分
     */
    private String diffAwayBoxShootOut;

    /**
     * アウェーチームのゴールポストに当たったシュート数の差分
     */
    private String diffAwayGoalPost;

    /**
     * アウェーチームのヘディングゴール数の差分
     */
    private String diffAwayGoalHead;

    /**
     * アウェーチームのキーパーセーブ数の差分
     */
    private String diffAwayKeeperSave;

    /**
     * アウェーチームのフリーキック数の差分
     */
    private String diffAwayFreeKick;

    /**
     * アウェーチームのオフサイド数の差分
     */
    private String diffAwayOffside;

    /**
     * アウェーチームのファウル数の差分
     */
    private String diffAwayFoul;

    /**
     * アウェーチームのイエローカード数の差分
     */
    private String diffAwayYellowCard;

    /**
     * アウェーチームのレッドカード数の差分
     */
    private String diffAwayRedCard;

    /**
     * アウェーチームのスローイン数の差分
     */
    private String diffAwaySlowIn;

    /**
     * アウェーチームのボックスタッチ数の差分
     */
    private String diffAwayBoxTouch;

    /**
     * アウェーチームのパス数の差分
     */
    private String diffAwayPassCount;

    /**
     * アウェーチームのファイナルサードパス数の差分
     */
    private String diffAwayFinalThirdPassCount;

    /**
     * アウェーチームのクロス数の差分
     */
    private String diffAwayCrossCount;

    /**
     * アウェーチームのタックル数の差分
     */
    private String diffAwayTackleCount;

    /**
     * アウェーチームのクリア数の差分
     */
    private String diffAwayClearCount;

    /**
     * アウェーチームのインターセプト数の差分
     */
    private String diffAwayInterceptCount;

    /**
     * ホームプレースタイル
     */
    private String homePlayStyle;

    /**
     * アウェープレースタイル
     */
    private String awayPlayStyle;

    /** 登録ID */
	private String registerId;

	/** 登録時間 */
	private String registerTime;

	/** 更新ID */
	private String updateId;

	/** 更新時間 */
	private String updateTime;

}
