(ns calip.tools)

(defn unvar-state
  "#'foo/bar to foo/bar"
  [s]
  (->> s (drop 2) (apply str)))  ;; magic 2 is removing "#'" in state name

(defn str->var
  "converts strings to vars
   usually useful if values are coming from config / text data"
  [s]
  (-> s unvar-state symbol resolve))

