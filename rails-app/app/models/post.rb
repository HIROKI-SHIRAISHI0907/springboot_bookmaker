class Post < ApplicationRecord
  # DBに登録する前にuuidでpostidを設定
  before_create :set_postid

  private
  def set_postid
    self.postid = SecureRandom.uuid
  end
end