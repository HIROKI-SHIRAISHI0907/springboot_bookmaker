# frozen_string_literal: true

require 'sidekiq/web'

Rails.application.routes.draw do
  # all_posts POST   /posts/all(.:format)  posts#all
  # detail_posts POST   /posts/detail(.:format)  posts#detail
  # edit_posts POST   /posts/edit(.:format)  posts#edit
  # posts POST   /posts(.:format)   posts#create
  # new_post GET    /posts/new(.:format)   posts#new
  resources :posts, only: %i[new create]

  # 詳細・編集（postidがある画面）URLにpostidを仕込んでいく。deleteしたいときはdestroyと書く
  resources :posts, param: :postid, only: [:new, :create, :update] do
     # 一覧（all）画面
    collection { get :all }
    member     { get :detail, :edit, :destroy }
  end

  post 'posts/comment',   to: 'comments#create',   as: :comment_post

  mount Sidekiq::Web, at: '/sidekiq'
end
