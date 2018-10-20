(ns script.package
  (:require
   [clojure.java.io :as jio]
   [clojure.tools.deps.alpha :as deps]
   [clojure.tools.deps.alpha.reader :as deps.reader]
   [clojure.tools.deps.alpha.extensions :as deps.ext]
   [clojure.tools.deps.alpha.extensions.git :as deps.git]
   [user.java.io.alpha :as io]
   [user.tools.deps.maven.alpha :as maven]
   [user.tools.deps.clean :as clean]
   [user.tools.deps.jar :as jar]
   [user.tools.deps.install :as install]
   [script.time]
   )
  (:import
   java.nio.file.Path
   ))


(set! *warn-on-reflection* true)


(def target-path "target")
(def classes-path "target/classes")
(def ^Path library 'user.tools.deps.alpha)


(defn package
  []
  (time
    (do
      (clean/clean target-path)
      (let [version    (script.time/chrono-version-str)
            mvn-coords {:mvn/version version}
            pom-file   (maven/sync-pom library mvn-coords)
            jarpath    (jar/jar library mvn-coords nil {:out-path (. (io/as-path target-path) resolve "package.jar")})]
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
