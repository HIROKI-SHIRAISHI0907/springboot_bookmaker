# app/controllers/users_controller.rb
class UsersController < ApplicationController
  skip_before_action :authenticate!, only: [:new, :create], raise: false

  def new
    @user = User.new
  end

  def create
      @user = User.new(user_params)
      if @user.save
         issued = ::JsonWebToken.issue_for(@user)
         token  = issued.is_a?(Hash) ? (issued[:token] || issued['token']) : issued

         cookies[:jwt] = {
            value:     token,
            expires:   1.week.from_now,
            httponly:  true,
            secure:    Rails.env.production?,
            same_site: :lax
         }

         redirect_to all_posts_path, notice: 'ユーザーを作成し、ログインしました。', status: :see_other
      else
         render :new, status: :unprocessable_entity
      end
   end

   private
   
   def user_params
      params.require(:user).permit(:email, :password, :password_confirmation)
   end
end
