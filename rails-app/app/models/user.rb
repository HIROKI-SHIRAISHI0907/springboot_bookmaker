class User < ApplicationRecord
  has_secure_password  # password / password_confirmation を受け付ける

  validates :email,
    presence: true,
    uniqueness: { case_sensitive: false },
    format: { with: URI::MailTo::EMAIL_REGEXP }

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