class Posts < ActiveRecord::Migration[7.0]
  def change
    create_table :posts, id: false, comment: '掲示板投稿' do |t|
      t.string :postid, null: false, primary_key: true, comment: '投稿ID(POSTXXXX)'
      t.string :name,     null: false, comment: '名前'
      t.string :title,    null: false, comment: 'タイトル'
      t.string :body,     null: false, comment: '本文'
      t.string :reviewer, null: false, comment: '担当者'
      t.timestamps
    end
  end
end
