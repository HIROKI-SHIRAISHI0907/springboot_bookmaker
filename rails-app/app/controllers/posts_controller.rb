class PostsController < ApplicationController
   def new  #routes.rbで設定した posts#newの箇所
      @post = Post.new #Postクラスのインスタンスを作成
   end

   #掲示板作成画面
   def create #routes.rbで設定した posts#createの箇所
      @post = Post.new(post_params)
      @post.save
   end

   #掲示板一覧画面
   def all
      @post = Post.all
   end

   #掲示板詳細画面(postidに対応するデータを取得)
   def detail
      @post = Post.detail(post_params)
   end

   #掲示板編集画面(postidに対応するデータを取得)
   def edit
      @post = Post.detail(post_params)
   end

   private

   def post_params #受け取るべきパラメータのみ記載
      params.require(:post).permit(:postid)
   end
end