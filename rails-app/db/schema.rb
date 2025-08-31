# This file is auto-generated from the current state of the database. Instead
# of editing this file, please use the migrations feature of Active Record to
# incrementally modify your database, and then regenerate this schema definition.
#
# This file is the source Rails uses to define your schema when running `bin/rails
# db:schema:load`. When creating a new database, `bin/rails db:schema:load` tends to
# be faster and is potentially less error prone than running all of your
# migrations from scratch. Old migrations may fail to apply correctly if those
# migrations use external dependencies or application code.
#
# It's strongly recommended that you check this file into your version control system.

ActiveRecord::Schema[7.0].define(version: 2025_08_27_154354) do
  create_table "comments", primary_key: "commentid", id: :string, force: :cascade do |t|
    t.string "postid", null: false
    t.string "userid", null: false
    t.string "comment", null: false
    t.datetime "created_at", null: false
    t.datetime "updated_at", null: false
  end

  create_table "posts", primary_key: "postid", id: :string, force: :cascade do |t|
    t.string "userid", null: false
    t.string "name", null: false
    t.string "title", null: false
    t.string "body", null: false
    t.string "reviewer", null: false
    t.datetime "created_at", null: false
    t.datetime "updated_at", null: false
  end

  create_table "users", primary_key: "userid", id: :string, force: :cascade do |t|
    t.string "password_digest", null: false
    t.string "email", null: false
    t.string "birthday"
    t.datetime "created_at", null: false
    t.datetime "updated_at", null: false
  end

end
