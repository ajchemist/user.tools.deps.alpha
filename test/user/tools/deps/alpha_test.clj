(ns user.tools.deps.alpha-test
  (:require
   [clojure.string :as str]
   [clojure.test :as test :refer [deftest is are testing]]
   [user.tools.deps.alpha :refer :all]
   ))


(deftest main


  (is (nil? (str/index-of (make-classpath) "tools.deps.alpha")))
  (is (int? (str/index-of (make-classpath {:resolve-aliases [:provided]}) "tools.deps.alpha")))


  )
