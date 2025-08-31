class SessionsController < ApplicationController
  skip_before_action :authenticate!, only: %i[login create], raise: false

  def login
    @login = User.new
  end

  def create
    email    = params.dig(:user, :email)    || params[:email]
    password = params.dig(:user, :password) || params[:password]
    user = User.find_by(email:)

    if user&.authenticate(password)
      token = JsonWebToken.issue_for(user)

      cookies.encrypted[:jwt] = {
        value:     token,
        expires:   1.week.from_now,
        httponly:  true,
        secure:    Rails.env.production?,
        same_site: :lax
      }
      session[:jwt] = token

      redirect_to all_posts_path, notice: 'ログインしました。', status: :see_other
    else
      @login = User.new(email:)
      flash.now[:alert] = 'メールアドレスまたはパスワードが違います'
      render :login, status: :unprocessable_entity
    end
  end

  def destroy
    cookies.delete(:jwt)
    session.delete(:jwt)
    redirect_to login_path, notice: 'ログアウトしました。', status: :see_other
  end
end
