(ns user.tools.deps.jar-test
  (:require
   [clojure.test :as test :refer [deftest is are testing]]
   [user.tools.deps.alpha :as u.deps]
   [user.tools.deps.jar :as u.jar]
   )
  (:import
   java.util.jar.JarFile
   java.util.jar.JarEntry
   ))


(deftest test-uber
  (def uber-jarpath
    (u.jar/uberjar
      (u.jar/jar 'user.tools.deps.alpha {:mvn/version "0.0.0" :classifier "uber"} nil {})
      (u.deps/make-classpath (u.deps/project-deps) [])))


  (is (instance? JarEntry (.getJarEntry (JarFile. uber-jarpath) "clojure/core__init.class")))
  )
