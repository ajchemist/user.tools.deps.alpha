(ns user.tools.deps.jar-test
  (:require
   [clojure.test :as test :refer [deftest is are testing]]
   [user.java.io.alpha :as u.jio]
   [user.tools.deps.alpha :as u.deps]
   [user.tools.deps.util.jar :as util.jar]
   [user.tools.deps.jar :refer :all]
   )
  (:import
   java.util.jar.JarFile
   java.util.jar.JarEntry
   ))


(deftest test-uber
  (def uber-jarpath
    (uberjar
      (jar 'user.tools.deps.alpha {:mvn/version "0.0.0" :classifier "uber"} nil {})
      (u.deps/make-classpath)))


  (is (instance? JarEntry (.getJarEntry (JarFile. uber-jarpath) "clojure/core__init.class")))
  )
