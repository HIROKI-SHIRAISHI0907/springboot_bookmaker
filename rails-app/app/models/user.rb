class User < ApplicationRecord
  # validate
  validates :email,
    format: { with: URI::MailTo::EMAIL_REGEXP },
    presence: true,
    uniqueness: { case_sensitive: false }
  validates :password, presence: true, length: { maximum: 30 }

  # 安全なパスワードハッシュ機能
  has_secure_password

  # DBに登録する前でUSERXXXXの形式でuseridを設定
  self.primary_key = 'userid'
  before_create :assign_userid!

  private

  def assign_userid!
    return if userid.present?

    last_id = User.where("userid LIKE 'USER%'")
                  .order(Arel.sql("CAST(SUBSTR(userid, 5) AS INTEGER) DESC"))
                  .limit(1)
                  .pluck(:userid)
                  .first

    last_no = last_id ? last_id.delete_prefix("USER").to_i : 0
    self.userid = format("USER%04d", last_no + 1)
  end
end