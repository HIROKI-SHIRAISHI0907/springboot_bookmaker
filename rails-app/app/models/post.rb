class Post < ApplicationRecord
  # DBに登録する前でPOSTXXXXの形式でpostidを設定
  self.primary_key = 'postid'
  before_create :assign_postid!

  private

  def assign_postid!
    return if postid.present?

    last_id = Post.where("postid LIKE 'POST%'")
                  .order(Arel.sql("CAST(SUBSTR(postid, 5) AS INTEGER) DESC"))
                  .limit(1)
                  .pluck(:postid)
                  .first

    last_no = last_id ? last_id.delete_prefix("POST").to_i : 0
    self.postid = format("POST%04d", last_no + 1)
  end
end