# app/controllers/application_controller.rb
require "pundit"

class ApplicationController < ActionController::Base
  include Pundit::Authorization
  protect_from_forgery with: :null_session

  # 現在全てのコントローラが当コントローラーを継承しているためクッキーにJWTがなければログインに強制遷移
  before_action :authenticate!

  rescue_from Pundit::NotAuthorizedError do
    render json: { error: "Forbidden" }, status: :forbidden
  end

  private

  # JWTがあるか
  def authenticate!
    token = bearer_token
    return handle_unauthenticated("Token missing") if token.blank?

    payload = ::JsonWebToken.verify!(token)  # => { "user_id", "exp" }

    # ★ User.primary_key を使って探す（主キーが 'userid' なら USER0002 でヒット）
    begin
      @current_user = User.find(payload[:user_id])   # ← primary_key を尊重して探す
    rescue ActiveRecord::RecordNotFound
      return handle_unauthenticated("User not found")
    end
  rescue ::JsonWebToken::Error => e
    handle_unauthenticated(e.message)
  end

  def handle_unauthenticated(message)
    respond_to do |format|
      format.html { redirect_to login_path, alert: "ログインしてください" }
      format.json { render json: { error: message }, status: :unauthorized }
      format.any  { render plain: message, status: :unauthorized }
    end
  end

  # CookieからJWTが登路されているか
  def bearer_token
    auth = request.headers["Authorization"]
    return auth.split.last if auth&.start_with?("Bearer ")
    cookies[:jwt]  # ← HTML遷移時はこれ
  end

  def current_user
    @current_user
  end
end
