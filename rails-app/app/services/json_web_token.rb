# lib/json_web_token.rb
require 'jwt'

module JsonWebToken
  SECRET = Rails.application.credentials.dig(:jwt, :secret) || ENV['JWT_SECRET'] || 'dev-secret'
  ALGO   = 'HS256'

  module_function

  def issue_for(user, exp: 1.week.from_now)
    payload = { sub: user.id, exp: exp.to_i }
    JWT.encode(payload, SECRET, ALGO)
  end

  def decode(token)
    JWT.decode(token, SECRET, true, algorithm: ALGO) # => [payload, header]
  end
end
