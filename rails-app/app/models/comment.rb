class Comment < ApplicationRecord
  # DBに登録する前でCOMMENTXXXXの形式でcommentidを設定
  self.primary_key = 'commentid'
  before_create :assign_commentid!

  private

  def assign_commentid!
    return if commentid.present?
    
    last = Comment.where("commentid LIKE 'COMMENT____'")   # アンダースコア4つで4文字
                  .order(Arel.sql('commentid DESC'))
                  .limit(1)
                  .pick(:commentid)
    next_seq = last ? last[-4..].to_i + 1 : 1
    self.commentid = format("COMMENT%04d", next_seq)
  end
end