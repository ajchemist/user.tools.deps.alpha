(ns user.tools.deps.alpha-test
  (:require
   [clojure.string :as str]
   [clojure.test :as test :refer [deftest is are testing]]
   [clojure.tools.deps.alpha.util.maven :as util.maven]
   [clojure.tools.deps.alpha :as deps]
   [user.tools.deps.alpha :as u.deps]
   )
  (:import
   java.io.File
   java.util.jar.JarFile
   ))


(deftest main
  ;; aliases :provided deps not included
  (is (nil? (str/index-of (make-classpath) "clojure/tools.deps.alpha")))
  ;; :provided deps included
  (is (int? (str/index-of (make-classpath {:resolve-aliases [:provided]}) "clojure/tools.deps.alpha")))


  )


(comment
  (= (.getName (JarFile. (get-jarpath 'user.java.io {:mvn/version "2018.292.70200"})))
     (get-jarpath 'user.java.io {:mvn/version "2018.292.70200"}))


  (str/split (make-classpath) (re-pattern File/pathSeparator))


  (u.deps/project-deps-edn)


  (update (deps.reader/read-deps ["deps.edn"]) :mvn/repos
    #(merge %2 %)
    util.maven/standard-repos)

  )
