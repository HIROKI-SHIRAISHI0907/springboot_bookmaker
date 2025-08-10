# frozen_string_literal: true

require 'sidekiq/web'

Rails.application.routes.draw do
  # all_posts POST   /posts/all(.:format)  posts#all
  # detail_posts POST   /posts/detail(.:format)  posts#detail
  # edit_posts POST   /posts/edit(.:format)  posts#edit
  # posts POST   /posts(.:format)   posts#create
  # new_post GET    /posts/new(.:format)   posts#new
  resources :posts, only: %i[new create]

  # 一覧（all）画面
  get 'posts/all', to: 'posts#all', as: :all_posts

  # 詳細・編集（idがある画面にしたい場合）
  # get 'posts/:id/detail', to: 'posts#detail', as: :detail_post
  # get 'posts/:id/edit',   to: 'posts#edit',   as: :edit_post

  mount Sidekiq::Web, at: '/sidekiq'
end
