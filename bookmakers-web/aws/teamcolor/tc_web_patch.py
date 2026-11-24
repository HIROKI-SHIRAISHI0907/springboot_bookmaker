import sys, os
p = sys.argv[1]
snip = open(os.path.join(os.path.dirname(__file__), "tc_web_snippet.txt"), encoding="utf-8").read()
s = open(p, encoding="utf-8").read()
a = "\tprivate boolean hasText(String s) {"
assert s.count(a) == 1 and "syncWithTeamMaster" not in s
s = s.replace(a, snip + "\n" + a)
old = "\t\t\t\t      league = :league AND\n\t\t\t\t      team = :team\n"
assert s.count(old) == 1, s.count(old)
s = s.replace(old, "\t\t\t\t      league = :league AND\n\t\t\t\t      team = :team AND\n\t\t\t\t      COALESCE(del_flg, '0') = '0'\n")
open(p, "w", encoding="utf-8").write(s)
print("OK", p)
