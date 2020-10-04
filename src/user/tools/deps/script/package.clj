(ns user.tools.deps.script.package
  (:require
   [clojure.java.io :as jio]
   [clojure.tools.cli :as cli]
   [user.java.io.alpha :as u.jio]
   [user.tools.deps.maven.alpha :as maven]
   [user.tools.deps.clean :as clean]
   [user.tools.deps.jar :as jar]
   [user.tools.deps.install :as install]
   [user.java.time.script.print-chrono-version :as chrono-version]
   )
  (:import
   ;; java.nio.file.Path
   ))


(set! *warn-on-reflection* true)


(def ^:dynamic *target-dir*)
(def ^:dynamic *jar-path*)


(def cli-options
  [["-r" "--library LIBRARY" "Library symbol"
    :parse-fn symbol]
   ["-d" "--dir TARGET_DIR" "Package target directory"
    :default "target"
    :validate-fn [string? "Target path must be a string"]]
   ["-t" "--version VERSION" "Package version string"
    :default (chrono-version/chrono-version-str)]
   [nil "--jar-path JAR_FILE" "Package jar file path"
    :default "package.jar"]])


(defn package
  [library version]
  (let [^String target-dir *target-dir*
        ^String jar-path   *jar-path*]
    (time
      (do
        (clean/clean target-dir)
        (let [mvn-coords {:mvn/version version}
              pom-path   (maven/sync-pom library mvn-coords)
              jarpath    (jar/jar
                           library nil nil
                           {:pom-path pom-path
                            :out-path (. (u.jio/as-path target-dir) resolve jar-path)})]
          (println (str (install/install nil nil jarpath pom-path)))
          (println "\n- " version "\n"))))))


(defn -main
  [& xs]
  (let [{{:keys [dir library version jar-path]} :options
         :as                                    parsed-opts} (cli/parse-opts xs cli-options)]
    (try
      (binding [*target-dir* dir
                *jar-path*   jar-path]
        (package library version))
      (catch Throwable e
        (.printStackTrace e)
        (System/exit 127))
      (finally
        (shutdown-agents)
        (System/exit 0)))))


(set! *warn-on-reflection* false)


(comment
  (cli/parse-opts [] cli-options)
  )
