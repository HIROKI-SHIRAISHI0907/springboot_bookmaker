class Users < ActiveRecord::Migration[7.0]
  def change
    create_table :users, id: false, comment: 'ユーザー' do |t|
      t.string :userid, null: false, primary_key: true, comment: 'ユーザーID(USERXXXX)'
      t.string :password_digest,     null: false, comment: 'パスワード'
      t.string :email,    null: false, comment: 'E-mailアドレス'
      t.string :birthday,    null: true, comment: '誕生日'
      t.timestamps
    end
  end
end
