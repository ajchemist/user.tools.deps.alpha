(ns user.tools.deps.compile-test
  (:refer-clojure :exclude [compile])
  (:require
   [clojure.java.io :as jio]
   [clojure.test :as test :refer [deftest is are testing]]
   [clojure.tools.namespace.find :as ns.find]
   [clojure.tools.namespace.file]
   [user.java.io.alpha :as u.jio]
   [user.tools.deps.util.compile :as util.compile]
   [user.tools.deps.clean :as clean]
   [user.tools.deps.alpha :as u.deps]
   [user.tools.deps.compile :as u.compile :refer :all]
   )
  (:import
   java.io.File
   ))


(deftest reset-loaded-libs
  ;;
  (clean/clean "target/classes")
  (u.jio/mkdir "target/classes")
  (binding [*compile-path*     "target/classes"
            *compiler-options* {}]
    (run! clojure.core/compile #{'user.tools.deps.compile-test.a})
    (is (util.compile/lib-already-compiled? 'user.tools.deps.compile-test.a))
    (is (not (util.compile/lib-already-compiled? 'user.java.io.alpha))))


  ;;
  (binding [*compile-path* "target/classes"]
    (clean/clean *compile-path*)
    (compile #{'user.tools.deps.compile-test.a})
    (is (util.compile/lib-already-compiled? 'user.java.io.alpha)))


  ;;
  (clean/clean "target/classes")
  )


(defn- file-length
  [^File f]
  {:pre [(.isFile f)]}
  (.length f))


(deftest assert-1
  (clean/clean "target/compile-test/assert")
  (u.jio/mkdir "target/compile-test/assert")
  (binding [*compile-path* "target/compile-test/assert"]
    (clojure.core/compile 'user.tools.deps.compile-test.assert))


  (clean/clean "target/compile-test/noassert")
  (u.jio/mkdir "target/compile-test/noassert")
  (binding [*assert*       false
            *compile-path* "target/compile-test/noassert"]
    (clojure.core/compile 'user.tools.deps.compile-test.assert))


  (is
    (<
      (file-length (jio/file "target/compile-test/noassert/user/tools/deps/compile_test/assert$f.class"))
      (file-length (jio/file "target/compile-test/assert/user/tools/deps/compile_test/assert$f.class"))))
  )


(deftest assert-2
  (clean/clean "target/compile-test/assert")
  (u.jio/mkdir "target/compile-test/assert")
  (binding [*compile-path* "target/compile-test/assert"]
    (compile '[user.tools.deps.compile-test.assert]))


  (clean/clean "target/compile-test/noassert")
  (u.jio/mkdir "target/compile-test/noassert")
  (binding [*compiler-options* {:assert false}
            *compile-path*     "target/compile-test/noassert"]
    (compile '[user.tools.deps.compile-test.assert]
             nil nil {:assert false}))


  (is
    (<
      (file-length (jio/file "target/compile-test/noassert/user/tools/deps/compile_test/assert$f.class"))
      (file-length (jio/file "target/compile-test/assert/user/tools/deps/compile_test/assert$f.class")))))


(comment


  java.io.File/pathSeparator
  (System/getProperty "path.separator")


  (binding [clojure.core/*loading-verbosely* true]
    (require 'user.java.io.alpha :reload))


  (binding [*compile-path* "target/classes"]
    (clojure.core/compile 'clojure.string))


  (ns.find/find-namespaces [(jio/file "test/user/tools/deps/compile_test")])
  (ns.find/find-ns-decls [(jio/file "test/user/tools/deps/compile_test")])


  (->> (System/getProperty "java.class.path") (classpath->paths) (paths->urls))


  (compile #{'user.tools.deps.compile-test.a} nil (System/getProperty "java.class.path") nil)


  (compile #{'user.tools.deps.compile-test.b} nil (System/getProperty "java.class.path") nil)


  (compile #{'user.java.io.alpha})


  (compile
    #{'datascript.db}
    nil
    (u.deps/make-classpath
      (update (u.deps/project-deps)
        :deps merge
        '{org.clojure/clojure {:mvn/version "1.10.1"}
          datascript          {:mvn/version "0.16.6"}})
      [:test])
    {:direct-linking true})


  (compile #{'user.tools.deps.compile-test.a 'user.java.io.alpha})


  )
