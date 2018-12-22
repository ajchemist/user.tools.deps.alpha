(ns script.package
  (:require
   [clojure.java.io :as jio]
   [clojure.tools.deps.alpha :as deps]
   [clojure.tools.deps.alpha.reader :as deps.reader]
   [clojure.tools.deps.alpha.extensions :as deps.ext]
   [clojure.tools.deps.alpha.extensions.git :as deps.git]
   [user.java.io.alpha :as u.jio]
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
(def library 'user.tools.deps.alpha)


(defn package
  []
  (time
    (do
      (clean/clean target-path)
      (let [version    (script.time/chrono-version-str)
            mvn-coords {:mvn/version version}
            pom-path   (maven/sync-pom library {:mvn/version (script.time/chrono-version-str)})
            jarpath    (jar/maven-jar
                         pom-path nil nil
                         {:out-path (. (u.jio/as-path target-path) resolve "package.jar")})]
        (println (str (install/install nil nil jarpath pom-path)))
        (println "\n- " version "\n")))))


(defn -main
  [& xs]
  (try
    (package)
    (catch Throwable e
      (.printStackTrace e)
      (System/exit 127))
    (finally
      (shutdown-agents)
      (System/exit 0))))


(set! *warn-on-reflection* false)


(comment
  (package)
  )
