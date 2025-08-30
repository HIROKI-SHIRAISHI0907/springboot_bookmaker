# app/controllers/sessions_controller.rb
class SessionsController < ApplicationController
  skip_before_action :authenticate!, only: [:login, :create], raise: false

  def login   # GET /login
    @login = User.new
  end

  def create
    email    = params.dig(:user, :email)    || params[:email]
    password = params.dig(:user, :password) || params[:password]
    user = User.find_by(email: email)

    if user&.authenticate(password)
      issued = ::JsonWebToken.issue_for(user)  # ← これが Hash の時と String の時に対応
      token  = issued.is_a?(Hash) ? (issued[:token] || issued['token']) : issued

      cookies[:jwt] = {
        value:     token,
        expires:   1.week.from_now,
        httponly:  true,
        secure:    Rails.env.production?,
        same_site: :lax
      }

      redirect_to all_posts_path, notice: 'ログインしました。', status: :see_other
    else
      @login = User.new(email: email)
      flash.now[:alert] = 'メールアドレスまたはパスワードが違います'
      render :login, status: :unprocessable_entity
    end
  end


  def destroy
    cookies.delete(:jwt)
    redirect_to login_path, notice: 'ログアウトしました。', status: :see_other
  end
end
