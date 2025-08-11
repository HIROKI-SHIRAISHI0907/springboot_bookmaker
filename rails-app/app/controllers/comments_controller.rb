class CommentsController < ApplicationController
   def new  #routes.rbで設定した comments#newの箇所
      @comment = Comment.new #Commentクラスのインスタンスを作成
   end

   #コメント作成要素
   def create #routes.rbで設定した comments#createの箇所
      # hidden から postid を取得
      postid = params.dig(:comment, :postid)
      # postsからpostidに紐づくデータ取得 Post.findだとidを探しに行ってしまう
      @post  = Post.find_by(postid: postid)
      # 該当する Post が無ければ 404
      unless @post
         return render plain: "404 not found", status: :not_found
      end

      # postidをcomments登録オブジェクトに対してマージ
      @comment = Comment.new(comment_params.merge(postid: postid))
      @comment.userid = 'UNKNOWN' if @comment.userid.blank?
      # 登録したらGETで掲示板詳細(detail)へリダイレクト
      if @comment.save
         commentid = @comment.commentid
         redirect_to detail_post_path(@post), status: :see_other
      else
         # 失敗時に詳細画面を再表示
         @get = @post
         render 'posts/detail', status: :unprocessable_entity
      end
   end

   #コメント一覧画面
   def all
      @comments = Comment.all
   end

   def destroy
      comment = Comment.find_by!(commentid: params[:commentid])
      postid  = comment.postid
      comment.destroy!
         redirect_to detail_post_path(postid: postid), notice: "コメントを削除しました。", status: :see_other
   rescue ActiveRecord::RecordNotFound
      redirect_to all_posts_path, alert: "対象のコメントが見つかりませんでした。"
   end

   private

   def comment_params
      # @commentが一番外側
      params.require(:comment).permit(:postid, :userid, :comment)
   end
end