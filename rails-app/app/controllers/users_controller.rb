class UsersController < ApplicationController
   def new  #routes.rbで設定した posts#newの箇所
      @user = User.new 
   end

   #ユーザー作成画面
   def create #routes.rbで設定した users#createの箇所
      @user = User.new(user_params)
      # 登録したらGETで/users/loginへリダイレクト
      if @user.save
         redirect_to login_users_path, notice: 'ユーザーを作成しました。', status: :see_other
      else
         render :new, status: :unprocessable_entity
      end
   end

   def login
      @login = User.new
   end

   #ログインチェック
   def loginChk
      email = User.find_by!(email: params[:email])
      redirect_to all_posts_path, notice: 'ユーザーを作成しました。', status: :see_other
   end

   private

   def user_params #受け取るべきパラメータのみ記載
      params.require(:user).permit(:email, :password)
   end
end