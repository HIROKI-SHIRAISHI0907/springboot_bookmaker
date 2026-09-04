package dev.common.constant;

/**
 * S3バケット定数
 * @author shiraishitoshio
 *
 */
public class S3BucketConstant {

	/** コンストラクタ生成禁止 */
	private S3BucketConstant() {}

	/** S3_ALL_LEAGUE */
	public static final String S3_ALL_LEAGUE = "aws-s3-all-league-csv";

	/** S3_DELAY_POSTPONE */
	public static final String S3_DELAY_POSTPONE = "aws-s3-delay-postpone-csv";

	/** S3_FUTURE */
	public static final String S3_FUTURE = "aws-s3-future-csv";

	/** S3_GEOGRAFIC */
	public static final String S3_GEOGRAFIC = "aws-s3-geografic-csv";

	/** S3_MAIL */
	public static final String S3_MAIL = "aws-s3-mail";

	/** S3_NEXT_SEASON_INFO */
	public static final String S3_NEXT_SEASON_INFO = "aws-s3-next-season-info-csv";

	/** S3_NO_ECS */
	public static final String S3_NO_ECS = "aws-s3-no-ecs-task-time-csv";

	/** S3_OUTPUT */
	public static final String S3_OUTPUT = "aws-s3-outputs-csv";

	/** S3_OUTPUT_BK */
	public static final String S3_OUTPUT_BK = "aws-s3-outputs-csv-bk";

	/** S3_OUTPUT_FIN */
	public static final String S3_OUTPUT_FIN = "aws-s3-outputs-fin-csv";

	/** S3_RECORD */
	public static final String S3_RECORD = "aws-s3-record-csv";

	/** S3_SEASON */
	public static final String S3_SEASON = "aws-s3-season-csv";

	/** S3_STAT */
	public static final String S3_STAT = "aws-s3-stat-csv";

	/** S3_TEAM */
	public static final String S3_TEAM = "aws-s3-team-csv";

	/** S3_TEAM_MEMBER */
	public static final String S3_TEAM_MEMBER = "aws-s3-team-member-csv";

	/** S3_PASSWORD_RESET(バケットではないが、体裁を合わせるため。) */
	public static final String S3_PASSWORD_RESET = "aws-s3-password-reset";

	/** S3_DELETE_INFO(バケットではないが、体裁を合わせるため。) */
	public static final String S3_DELETE_INFO = "aws-s3-delete-info";

}
