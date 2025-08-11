class Comments < ActiveRecord::Migration[7.0]
  def change
    create_table :comments, id: false, comment: '掲示板投稿コメント' do |t|
      t.string :commentid, null: false, primary_key: true, comment: 'コメントID(COMMENTXXXX)'
      t.string :postid,     null: false, comment: '投稿ID'
      t.string :userid,    null: false, comment: 'ユーザーID'
      t.string :comment,     null: false, comment: '本文'
      t.timestamps
    end
  end
end
