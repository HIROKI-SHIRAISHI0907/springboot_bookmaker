# app/services/json_web_token.rb
require "jwt"

module JsonWebToken
  class Error < StandardError; end
  ALG = "HS256"

  def self.secret
    ENV["JWT_SECRET_KEY"].presence || Rails.application.credentials.jwt_secret_key
  end

  # JWT発行(user_idと1週間期限を設けた期限日)
  def self.issue_for(user)
    raise Error, "Missing secret" if secret.blank?
    exp = 1.week.from_now.to_i
    payload = { user_id: user.id, exp: exp }
    token = ::JWT.encode(payload, secret, ALG)
    #{ token:, exp: Time.at(exp).iso8601 } ログ
  end

  def self.verify!(token)
    raise Error, "Token missing" if token.blank?
    payload, = ::JWT.decode(token, secret, true, { algorithm: ALG })
    payload.with_indifferent_access
  rescue ::JWT::ExpiredSignature
    raise Error, "Token expired"
  rescue ::JWT::DecodeError
    raise Error, "Token invalid"
  end
end
