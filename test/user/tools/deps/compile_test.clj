(ns user.tools.deps.compile-test
  (:refer-clojure :exclude [compile])
  (:require
   [clojure.java.io :as jio]
   [clojure.test :as test :refer [deftest is are testing]]
   [clojure.tools.deps.alpha :as deps]
   [clojure.tools.namespace.find :as ns.find]
   [clojure.tools.namespace.file]
   [user.java.io.alpha :as u.jio]
   [user.tools.deps.clean :as clean]
   [user.tools.deps.alpha :as u.deps]
   [user.tools.deps.compile :refer :all]
   ))


(deftest reset-loaded-libs
  ;;
  (clean/clean "target/classes")
  (u.jio/mkdir "target/classes")
  (binding [*compile-path*     "target/classes"
            *compiler-options* {}]
    (run! clojure.core/compile #{'user.tools.deps.compile-test.a}))
  (is (u.jio/file? "target/classes/user/tools/deps/compile_test/a__init.class"))
  (is (not (u.jio/file? "target/classes/user/java/io/alpha__init.class")))


  ;;
  (clean/clean "target/classes")
  (compile #{'user.tools.deps.compile-test.a} "target/classes" nil nil)
  (is (u.jio/file? "target/classes/user/java/io/alpha__init.class"))
  )


(comment

  (binding [clojure.core/*loading-verbosely* true]
    (require 'user.java.io.alpha :reload))


  (binding [*compile-path* "target/classes"]
    (clojure.core/compile 'clojure.string))


  (ns.find/find-namespaces [(jio/file "test/user/tools/deps/compile_test")])
  (ns.find/find-ns-decls [(jio/file "test/user/tools/deps/compile_test")])


  (compile #{'user.tools.deps.compile-test.a} nil (System/getProperty "java.class.path") nil)


  (compile #{'user.tools.deps.compile-test.b} nil (System/getProperty "java.class.path") nil)


  (compile #{'user.java.io.alpha})


  (compile
    #{'datascript.db}
    nil
    (u.deps/make-classpath
      (update (u.deps/deps-map) :deps merge '{datascript {:mvn/version "0.16.6"}})
      {:resolve-aliases [:test]})
    {:direct-linking true})


  (compile #{'user.tools.deps.compile-test.a 'user.java.io.alpha})


  )
