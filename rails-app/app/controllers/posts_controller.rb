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

      # もしinvalidで失敗したら(失敗→true)renderでnew（新規作成オブジェクト）を返す
      # @post があって、かつ invalid? のときだけ render
      return render :new if defined?(@post) && @post&.invalid?

      # もしinvalidで失敗したら(失敗→true)renderでedit（編集オブジェクト）を返す
      # @edit があって、かつ invalid? のときだけ render
      return render :new if defined?(@edit) && @edit&.invalid?
   end

   #掲示板詳細画面(postidに対応するデータを取得)
   def detail
      @detail = Post.find_by!(postid: params[:postid])
      @comment = Comment.new(postid: @detail.postid)
      # 同一postidに紐づくコメント一覧
      @comments = Comment.where(postid: @detail.postid).order(:created_at)  

      Rails.logger.info "[comments.count] #{@comments.count} for postid=#{@detail.postid}"
   end

   #掲示板編集画面(postidに対応するデータを取得)
   def edit
      @edit = Post.find_by!(postid: params[:postid])
   end

   #掲示板投稿更新処理
   def update
      # クエリURL内のpostidをキーに更新をかける
      @update = Post.find_by!(postid: params[:postid])
      if @update.update(post_params)
         redirect_to all_posts_path, notice: '投稿を更新しました。', status: :see_other
      else
         render :new
      end
   end

   #掲示板投稿削除処理
   def destroy
      ActiveRecord::Base.transaction do
         post = Post.find_by!(postid: params[:postid])
         # 先にコメントを全削除（コールバックが必要なら destroy_all / 不要なら delete_all）
         Comment.where(postid: post.postid).destroy_all
         # 投稿本体を削除
         post.destroy!
      end

      redirect_to all_posts_path, notice: '投稿を削除しました。', status: :see_other
   rescue ActiveRecord::RecordNotFound
      redirect_to all_posts_path, alert: '対象の投稿が見つかりませんでした。'
   rescue => e
      render :new, status: :unprocessable_entity
   end

   private

   def post_params #受け取るべきパラメータのみ記載
      params.require(:post).permit(:name, :title, :body, :reviewer)
   end
end