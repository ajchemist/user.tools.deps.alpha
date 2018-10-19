(ns script.extra
  (:require
   [clojure.java.io :as jio]
   [clojure.tools.cli :as cli]
   )
  (:import
   java.io.ByteArrayOutputStream
   java.io.File
   java.io.OutputStream
   java.io.Reader
   java.util.Properties
   org.apache.maven.artifact.repository.metadata.Metadata
   org.apache.maven.artifact.repository.metadata.io.xpp3.MetadataXpp3Reader
   org.apache.maven.artifact.repository.metadata.io.xpp3.MetadataXpp3Writer
   org.apache.maven.model.Build
   org.apache.maven.model.Dependency
   org.apache.maven.model.Exclusion
   org.apache.maven.model.License
   org.apache.maven.model.Model
   org.apache.maven.model.Repository
   org.apache.maven.model.Scm
   org.apache.maven.model.io.xpp3.MavenXpp3Reader
   org.apache.maven.model.io.xpp3.MavenXpp3Writer
   ))


(defn ^Model read-pom
  "Reads a pom file returning a maven Model object."
  [path]
  (with-open [reader (jio/reader path)]
    (.read (MavenXpp3Reader.) reader)))


;; * cli options


(def cli-options
  [["-h" "--help"]])


;; * main


(def cmds
  {"get-version" (fn [] (println (.getVersion (read-pom "pom.xml"))))})


(defn -main
  [& xs]
  (loop [args (drop-while (fn [cmd] (not (find cmds cmd))) xs)]
    (when-not (empty? args)
      (let [[cmd] args
            cmdfn (get cmds cmd)
            args  (rest args)]
        (when (fn? cmdfn)
          (cmdfn))
        (recur (drop-while (fn [cmd] (not (find cmds cmd))) args))))))


(comment
  (cli/parse-opts ["-v" "get-version" "-h"] cli-options)


  (cli/parse-opts ["--" "get-version" "-h"] cli-options :in-order false)


  (-main "get-version")


  (-main "a")
  )
