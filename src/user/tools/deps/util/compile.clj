(ns user.tools.deps.util.compile
  (:require
   [clojure.java.io :as jio]
   ))


(def root-resource (var-get #'clojure.core/root-resource))


(defn path-already-compiled?
  [path]
  (let [clj-path      (str path ".clj")
        class-path    (str path "__init.class")
        loader        (clojure.lang.RT/baseLoader)
        compiled-file (jio/file (str *compile-path* "/" class-path))
        clj-url       (jio/resource clj-path loader)]
    (or (and (.exists compiled-file)
             (or (nil? clj-url)
                 (> (clojure.lang.RT/lastModified (jio/as-url compiled-file) class-path)
                    (clojure.lang.RT/lastModified clj-url clj-path))))
        (jio/resource class-path loader))))


(defn lib-already-compiled?
  [lib]
  (path-already-compiled? (subs (root-resource lib) 1)))
