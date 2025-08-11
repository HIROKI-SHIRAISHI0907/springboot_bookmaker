class PostsController < ApplicationController
   def new  #routes.rbで設定した posts#newの箇所
      @post = Post.new #Postクラスのインスタンスを作成
      @comment = Comment.new #Commentクラスのインスタンスを作成
   end

   #掲示板作成画面
   def create #routes.rbで設定した posts#createの箇所
      @post = Post.new(post_params)
      # 登録したらGETでallへリダイレクト
      if @post.save
         redirect_to all_posts_path, notice: '投稿を作成しました。', status: :see_other
      else
         render :new, status: :unprocessable_entity
      end
   end

   #掲示板一覧画面
   def all
      @posts = Post.all
   end

   #掲示板詳細画面(postidに対応するデータを取得)
   def detail
      @detail = Post.find_by!(postid: params[:postid])
      @comment = Comment.new(postid: @detail.postid)
      # コメント一覧
      @comments = Comment.where(postid: @detail.postid).order(:created_at)  
   end

   #掲示板編集画面(postidに対応するデータを取得)
   def edit
      @edit = Post.edit(post_id_param)
   end

   private

   def post_params #受け取るべきパラメータのみ記載
      params.require(:post).permit(:name, :title, :body, :reviewer)
   end

   # IDで取得（/posts/:id, ?postid=, ?get[postid]=どれでもいい）
   def post_id_param
      params[:postid] || params.dig(:get, :postid) ||
      raise(ActionController::ParameterMissing, :postid)
  end
end