class ApplicationController < ActionController::Base
  before_action :set_current_user_id

  helper_method :logged_in?, :current_user_id

  def logged_in?      = @current_user_id.present?
  def current_user_id = @current_user_id

  private

  def set_current_user_id
    token = cookies.encrypted[:jwt] || cookies[:jwt] || session[:jwt] || request.headers['Authorization']
    @current_user_id = nil
    return if token.blank?

    token = token.split.last if token.to_s.start_with?('Bearer ')
    begin
      payload, _ = JsonWebToken.decode(token)
      uid = payload['sub'] || payload['user_id'] || payload['uid']
      @current_user_id = (uid.is_a?(String) && uid.match?(/\A\d+\z/)) ? uid.to_i : uid

      # デバッグ（必要なら）
      Rails.logger.debug "[JWT] ok uid=#{@current_user_id.inspect} path=#{request.path}"
    rescue JWT::DecodeError, JWT::ExpiredSignature => e
      Rails.logger.warn "[JWT] decode failed: #{e.class}: #{e.message} path=#{request.path}"
      @current_user_id = nil
    end
  end

  def authenticate!
    return if logged_in?
    redirect_to login_path, alert: 'サインインしてください。'
  end
end
