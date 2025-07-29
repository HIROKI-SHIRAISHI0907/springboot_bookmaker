# config/initializers/cors.rb
Rails.application.config.middleware.insert_before 0, Rack::Cors do
  allow do
    origins "https://www.example.com"
    resource "/GET/*",
      headers: :any,
      methods: [:get]
    resource "/POST/*",
      headers: :any,
      methods: [:post, :options, :head]
  end
end
