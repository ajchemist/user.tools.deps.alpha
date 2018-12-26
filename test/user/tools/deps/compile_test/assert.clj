(ns user.tools.deps.compile-test.assert)


(defn f
  [x]
  {:pre  [(number? x)]
   :post (number? %)}
  x)
