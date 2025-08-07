class Posts < ActiveRecord::Migration[7.0]
  def change
    create_table :posts, comment: '掲示板投稿' do |t|
      t.string :postid, null: false, comment: '投稿ID'

      t.timestamps
    end
  end
end
