import sys, os
p = sys.argv[1]
snip = open(os.path.join(os.path.dirname(__file__), "tc_batch_snippet.txt"), encoding="utf-8").read()
s = open(p, encoding="utf-8").read()
assert "syncFillLinkExact" not in s
k = s.rstrip().rfind("}")
s = s[:k].rstrip() + "\n\n" + snip + "}\n"
if "import org.apache.ibatis.annotations.Update;" not in s:
    s = s.replace("import org.apache.ibatis.annotations.Select;", "import org.apache.ibatis.annotations.Select;\nimport org.apache.ibatis.annotations.Update;")
old = "\t\t\t        team_color_sub_hex,\n\t\t\t        register_id,"
assert s.count(old) == 1, ("ins", s.count(old))
s = s.replace(old, "\t\t\t        team_color_sub_hex,\n\t\t\t        del_flg,\n\t\t\t        register_id,")
old = "\t\t\t        #{teamColorSubHex},\n\t\t\t        'SYSTEM',"
assert s.count(old) == 1, ("val", s.count(old))
s = s.replace(old, "\t\t\t        #{teamColorSubHex},\n\t\t\t        '0',\n\t\t\t        'SYSTEM',")
s = s.replace("team = #{team} AND del_flg = '0';", "team = #{team} AND COALESCE(del_flg, '0') = '0';")
open(p, "w", encoding="utf-8").write(s)
print("OK", p)
