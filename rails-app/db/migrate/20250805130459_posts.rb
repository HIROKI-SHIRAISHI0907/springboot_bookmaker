class Posts < ActiveRecord::Migration[7.0]
  def change
    create_table :posts, comment: '掲示板投稿' do |t|
      t.string :postid, null: false, comment: '投稿ID'
      t.string :name, null: false, comment: '名前'
      t.string :title, null: false, comment: 'タイトル'
      t.string :body, null: false, comment: '本文'
      t.string :reviewer, null: false, comment: '担当者'
      t.timestamps
    end
  end
end
