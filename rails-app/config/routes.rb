# frozen_string_literal: true

require 'sidekiq/web'

Rails.application.routes.draw do
  # all_posts POST   /posts/all(.:format)  posts#all
  # detail_posts POST   /posts/detail(.:format)  posts#detail
  # edit_posts POST   /posts/edit(.:format)  posts#edit
  # posts POST   /posts(.:format)   posts#create
  # new_post GET    /posts/new(.:format)   posts#new
  resources :posts, only: %i[new create] do
    collection do
      post :all
      post :detail
      post :edit
    end
  end
  
  mount Sidekiq::Web, at: '/sidekiq'
end
