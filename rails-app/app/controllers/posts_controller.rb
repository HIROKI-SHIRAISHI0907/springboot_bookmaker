class PostsController < ApplicationController
   skip_before_action :authenticate!, only: %i[new all detail], raise: false
   before_action :authenticate!, only: %i[create edit update destroy]

   def new
      @post = Post.new
      @comment = Comment.new
   end

   def create
      @post = Post.new(post_params.merge(userid: current_user_id))
      if @post.save
         redirect_to all_posts_path, notice: '投稿を作成しました。', status: :see_other
      else
         render :new, status: :unprocessable_entity
      end
   end

   def all
      @posts = Post.order(updated_at: :desc)
      return render :new if defined?(@post) && @post&.invalid?
      return render :new if defined?(@edit) && @edit&.invalid?
   end

   def detail
      @detail   = Post.find_by!(postid: params[:postid])
      @comment  = Comment.new(postid: @detail.postid)
      @comments = Comment.where(postid: @detail.postid).order(created_at: :desc)
   end

   def edit
      @post = Post.find_by!(postid: params[:postid])
   end

   def update
      @post = Post.find_by!(postid: params[:postid])
      @post.userid = current_user_id if logged_in?
      if @post.update(post_params)
         redirect_to all_posts_path, notice: '投稿を更新しました。', status: :see_other
      else
         render :edit, status: :unprocessable_entity
      end
   end

   def destroy
      ActiveRecord::Base.transaction do
         post = Post.find_by!(postid: params[:postid])
         Comment.where(postid: post.postid).destroy_all
         post.destroy!
      end
      redirect_to all_posts_path, notice: '投稿を削除しました。', status: :see_other
   rescue ActiveRecord::RecordNotFound
      redirect_to all_posts_path, alert: '対象の投稿が見つかりませんでした。'
   rescue => _e
      render :new, status: :unprocessable_entity
   end

   private
   
   def post_params
      params.require(:post).permit(:name, :title, :body, :reviewer)
   end
end
