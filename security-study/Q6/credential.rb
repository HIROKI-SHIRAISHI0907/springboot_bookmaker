# frozen_string_literal: true

require 'yaml'
class Credential
  # プロジェクトから見て現在のパスとフォルダ、ymlファイル名を結合
  config_path = File.join(__dir__, 'config', 'credentials.yml')
  # yamlを読み込む
  credentials = YAML.load_file(config_path)
  # 値を読み取る
  sample_key = credentials['aws']['sample_key']
  puts sample_key # aaabbb
end
