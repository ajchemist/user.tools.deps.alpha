(ns user.tools.deps.script.package
  (:require
   [clojure.java.io :as jio]
   [clojure.tools.cli :as cli]
   [clojure.tools.deps.alpha :as deps]
   [clojure.tools.deps.alpha.reader :as deps.reader]
   [clojure.tools.deps.alpha.extensions :as deps.ext]
   [clojure.tools.deps.alpha.extensions.git :as deps.git]
   [user.java.io.alpha :as u.jio]
   [user.tools.deps.maven.alpha :as maven]
   [user.tools.deps.clean :as clean]
   [user.tools.deps.jar :as jar]
   [user.tools.deps.install :as install]
   [user.java.time.script.print-chrono-version :as chrono-version]
   )
  (:import
   java.nio.file.Path
   ))


(set! *warn-on-reflection* true)


(def ^:dynamic *target-path* "target")
(def ^:dynamic ^String *jar-path* "package.jar")


(def cli-options
  [["-r" "--library LIBRARY" "Library symbol"
    :parse-fn symbol]
   ["-d" "--dir TARGET_DIR" "Package target directory"
    :default *target-path*
    :validate-fn [string? "Target path must be a string"]]
   ["-t" "--version VERSION" "Package version string"
    :default (chrono-version/chrono-version-str)]
   [nil "--jar-path JAR_FILE" "Package jar file path"
    :default *jar-path*]])


(defn package
  [library version]
  (let [^String target-path *target-path*
        ^String jar-path    *jar-path*]
    (time
      (do
        (clean/clean target-path)
        (let [mvn-coords {:mvn/version version}
              pom-path   (maven/sync-pom library {:mvn/version version})
              jarpath    (jar/maven-jar
                           pom-path nil nil
                           {:out-path (. (u.jio/as-path target-path) resolve jar-path)})]
          (println (str (install/install nil nil jarpath pom-path)))
          (println "\n- " version "\n"))))))


(defn -main
  [& xs]
  (let [{:keys [dir library version jar-path]} (cli/parse-opts xs cli-options)]
    (try
      (binding [*target-path* dir
                *jar-path*    jar-path]
        (package library version))
      (catch Throwable e
        (.printStackTrace e)
        (System/exit 127))
      (finally
        (shutdown-agents)
        (System/exit 0)))))


(set! *warn-on-reflection* false)
