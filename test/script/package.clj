(ns script.package
  (:require
   [clojure.java.io :as jio]
   [clojure.tools.deps.alpha :as deps]
   [clojure.tools.deps.alpha.reader :as deps.reader]
   [clojure.tools.deps.alpha.extensions :as deps.ext]
   [clojure.tools.deps.alpha.util.maven :as maven]
   [clojure.tools.deps.alpha.extensions.git :as deps.git]
   [badigeon.maven.alpha :as badigeon.maven]
   [badigeon.clean :as clean]
   [badigeon.javac :as javac]
   [badigeon.jar.maven.alpha :as jar]
   [badigeon.install :as install]
   [badigeon.sign :as sign]
   [badigeon.deploy :as deploy]
   [script.time]
   [user.java.io.alpha :as io]
   )
  (:import
   java.nio.file.Path
   ))


(set! *warn-on-reflection* true)


(def ^Path target-path (io/path "target"))
(def ^Path classes-path (io/path-resolve target-path "classes"))
(def ^Path library 'user.tools.deps.alpha)


(defn package
  []
  (time
    (do
      (clean/clean target-path)
      (let [version    (script.time/chrono-version-str)
            mvn-coords {:mvn/version version}
            pom-file   (badigeon.maven/sync-pom library mvn-coords)
            jarpath    (jar/jar library mvn-coords nil {:out-path (. target-path resolve "package.jar")})]
        (println (str (install/install library mvn-coords jarpath pom-file)))
        (println (str "\n- " version "\n"))
        jarpath))))


(defn -main
  [& xs]
  (try
    (package)
    (System/exit 0)
    (finally
      (shutdown-agents))))


(set! *warn-on-reflection* false)


(comment
  (package)
  )
